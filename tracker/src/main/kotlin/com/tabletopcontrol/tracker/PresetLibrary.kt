package com.tabletopcontrol.tracker

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Manages a persistent library of combatant presets for the initiative tracker.
 *
 * Each preset is stored as its own **key=value** text file inside
 * `~/.tabletopcontrol/presets/`.  Files use the extension `.preset` and are
 * named after a filesystem-safe version of the combatant's name, making the
 * library easy to browse, back up, and share: just copy individual `.preset`
 * files between machines.
 *
 * **File format** — one `key=value` pair per line, unknown keys are ignored:
 * ```
 * name=Dragon
 * hp=200
 * ac=22
 * initiative=5
 * imageUri=file:///home/dm/tokens/dragon.png
 * imageScaleX=1.50
 * imageScaleY=1.50
 * imageOffsetX=0.0
 * imageOffsetY=0.0
 * imageBase64=iVBORw0KGgo...
 * ```
 *
 * `imageBase64` contains a PNG of the token image scaled down to at most
 * [MAX_EMBEDDED_IMAGE_SIZE] × [MAX_EMBEDDED_IMAGE_SIZE] pixels, encoded as
 * standard Base64.  When a preset is loaded on a different machine the
 * embedded image is decoded to a temporary file so the rest of the
 * application can use it without any changes to the event system.
 *
 * I/O failures in [savePreset] and [delete] are silently swallowed —
 * consistent with the rest of the persistence layer — so that a missing or
 * read-only config directory never crashes the application.
 */
object PresetLibrary {

    /** Maximum width/height of the embedded image thumbnail (pixels). */
    const val MAX_EMBEDDED_IMAGE_SIZE: Int = 256

    /**
     * A saved combatant template.
     *
     * @property name         the combatant's display name
     * @property hp           the combatant's hit points
     * @property ac           the combatant's armour class
     * @property initiative   the combatant's default initiative value (defaults to 0)
     * @property imageUri     original local-file URI of the token image (may not
     *                        exist on another machine)
     * @property imageBase64  PNG thumbnail of the token image encoded as Base64;
     *                        enables sharing without needing the original file
     * @property imageScaleX  horizontal display scale applied to the token image
     * @property imageScaleY  vertical display scale applied to the token image
     * @property imageOffsetX horizontal pixel offset of the token image
     * @property imageOffsetY vertical pixel offset of the token image
     */
    data class Preset(
        val name: String,
        val hp: Int,
        val ac: Int,
        val initiative: Int = 0,
        val imageUri: String? = null,
        val imageBase64: String? = null,
        val imageScaleX: Double = 1.0,
        val imageScaleY: Double = 1.0,
        val imageOffsetX: Double = 0.0,
        val imageOffsetY: Double = 0.0,
    )

    /**
     * Overrideable in tests; when non-null, replaces the default presets
     * directory so tests operate on an isolated temporary directory instead of
     * `~/.tabletopcontrol/presets/`.
     */
    internal var presetsDirForTest: File? = null

    private val presetsDir: File
        get() = presetsDirForTest ?: run {
            val dir = File(File(System.getProperty("user.home"), ".tabletopcontrol"), "presets")
            dir.mkdirs()
            dir
        }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Saves [preset] to the library.
     *
     * If a `.preset` file whose `name` field matches [preset.name] already
     * exists it is overwritten in-place; otherwise a new file is created
     * using a filesystem-safe version of the name.
     */
    fun savePreset(preset: Preset) {
        try {
            presetsDir.mkdirs()
            fileFor(preset.name).writeText(serialize(preset))
        } catch (_: Exception) {
            // non-fatal — proceed without persistence
        }
    }

    /**
     * Returns all presets stored in the library, sorted alphabetically by name.
     *
     * @return a list of saved presets, or an empty list when the directory is
     *         absent or contains no parseable `.preset` files.
     */
    fun loadAll(): List<Preset> =
        try {
            presetsDir.listFiles { f -> f.extension == "preset" }
                ?.mapNotNull { runCatching { deserialize(it.readText()) }.getOrNull() }
                ?.sortedBy { it.name }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    /**
     * Removes every `.preset` file whose `name` field equals [name].
     *
     * Does nothing when no such preset exists.
     */
    fun delete(name: String) {
        try {
            presetsDir.listFiles { f -> f.extension == "preset" }
                ?.forEach { f ->
                    if (runCatching { deserialize(f.readText())?.name == name }.getOrDefault(false)) {
                        f.delete()
                    }
                }
        } catch (_: Exception) {
            // non-fatal
        }
    }

    // ── File naming ───────────────────────────────────────────────────────────

    /**
     * Converts [name] to a safe filename component.
     *
     * Letters, digits, spaces, hyphens, dots, and underscores are kept as-is;
     * everything else is replaced with `_`.  Leading/trailing whitespace is
     * trimmed, an empty result is replaced with `_`, and the result is
     * truncated to 200 characters to stay well within path-length limits.
     */
    internal fun sanitizeFilename(name: String): String =
        name.map { c -> if (c.isLetterOrDigit() || c in " .-_") c else '_' }
            .joinToString("")
            .trim()
            .ifEmpty { "_" }
            .take(200)

    /**
     * Returns the [File] that should be used to store the preset with [name].
     *
     * If a `.preset` file in [presetsDir] already stores a preset with this
     * exact name it is reused (enabling in-place updates).  Otherwise a new
     * file is chosen using the sanitized name, appending `_2`, `_3`, … to
     * avoid collisions with files that have a different actual name.
     */
    internal fun fileFor(name: String): File {
        val dir = presetsDir
        // Reuse an existing file that already stores this name.
        dir.listFiles { f -> f.extension == "preset" }
            ?.forEach { f ->
                if (runCatching { deserialize(f.readText())?.name == name }.getOrDefault(false)) {
                    return f
                }
            }
        // No existing file — pick a fresh filename.
        val base = sanitizeFilename(name)
        val primary = File(dir, "$base.preset")
        if (!primary.exists()) return primary
        var n = 2
        while (true) {
            val candidate = File(dir, "${base}_$n.preset")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    /**
     * Serialises [preset] to the key=value text format written to a `.preset` file.
     *
     * Newline and carriage-return characters in [Preset.name] are replaced
     * with a space so the line-oriented format stays unambiguous.  Values for
     * `imageBase64` are Base64 strings which never contain newlines.
     */
    internal fun serialize(preset: Preset): String = buildString {
        appendLine("name=${preset.name.replace('\n', ' ').replace('\r', ' ')}")
        appendLine("hp=${preset.hp}")
        appendLine("ac=${preset.ac}")
        appendLine("initiative=${preset.initiative}")
        preset.imageUri?.let { appendLine("imageUri=$it") }
        appendLine("imageScaleX=${preset.imageScaleX}")
        appendLine("imageScaleY=${preset.imageScaleY}")
        appendLine("imageOffsetX=${preset.imageOffsetX}")
        appendLine("imageOffsetY=${preset.imageOffsetY}")
        preset.imageBase64?.let { appendLine("imageBase64=$it") }
    }.trimEnd()

    /**
     * Parses the key=value [text] of a `.preset` file into a [Preset].
     *
     * Each line is split on the **first** `=` only, so values (such as file
     * URIs or Base64 data) may themselves contain `=` characters.
     * Unknown keys are silently ignored to allow forward-compatible additions.
     *
     * @return the parsed [Preset], or `null` when `name` or `hp` or `ac` is
     *         missing or unparseable.
     */
    internal fun deserialize(text: String): Preset? {
        val props = mutableMapOf<String, String>()
        for (line in text.lineSequence()) {
            val idx = line.indexOf('=')
            if (idx < 0) continue
            props[line.substring(0, idx)] = line.substring(idx + 1)
        }
        val name = props["name"] ?: return null
        val hp = props["hp"]?.toIntOrNull() ?: return null
        val ac = props["ac"]?.toIntOrNull() ?: return null
        val initiative = props["initiative"]?.toIntOrNull() ?: 0
        return Preset(
            name = name,
            hp = hp,
            ac = ac,
            initiative = initiative,
            imageUri = props["imageUri"]?.takeIf { it.isNotBlank() },
            imageBase64 = props["imageBase64"]?.takeIf { it.isNotBlank() },
            imageScaleX = props["imageScaleX"]?.toDoubleOrNull() ?: 1.0,
            imageScaleY = props["imageScaleY"]?.toDoubleOrNull() ?: 1.0,
            imageOffsetX = props["imageOffsetX"]?.toDoubleOrNull() ?: 0.0,
            imageOffsetY = props["imageOffsetY"]?.toDoubleOrNull() ?: 0.0,
        )
    }

    // ── Image embedding ───────────────────────────────────────────────────────

    /**
     * Reads the image at the local-file [uri], scales it so that neither
     * dimension exceeds [maxSize] pixels (never upscaling), and returns the
     * result as a standard Base64-encoded PNG string.
     *
     * This string can be stored in [Preset.imageBase64] to make the preset
     * fully self-contained and shareable across machines.
     *
     * @param uri     a `file:` URI or an absolute path to the image file
     * @param maxSize maximum width/height of the thumbnail (defaults to
     *                [MAX_EMBEDDED_IMAGE_SIZE])
     * @return the Base64-encoded PNG thumbnail, or `null` if the image cannot
     *         be read or [uri] is not a supported local-file URI
     */
    internal fun loadAndScaleImage(uri: String, maxSize: Int = MAX_EMBEDDED_IMAGE_SIZE): String? =
        try {
            val file = File(URI(uri))
            val original: BufferedImage = ImageIO.read(file) ?: return null
            val w = original.width
            val h = original.height
            val scale = minOf(maxSize.toDouble() / w, maxSize.toDouble() / h, 1.0)
            val newW = maxOf(1, (w * scale).toInt())
            val newH = maxOf(1, (h * scale).toInt())
            val scaled = BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB)
            val g = scaled.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(original, 0, 0, newW, newH, null)
            g.dispose()
            val baos = ByteArrayOutputStream()
            ImageIO.write(scaled, "png", baos)
            Base64.getEncoder().encodeToString(baos.toByteArray())
        } catch (_: Exception) {
            null
        }

    /**
     * Decodes [base64] to raw PNG bytes, writes them to a temporary file that
     * is deleted on JVM exit, and returns the file's `file:` URI string.
     *
     * Use this when loading a preset that has [Preset.imageBase64] set but
     * the original [Preset.imageUri] is inaccessible (e.g. on another machine).
     *
     * @return a `file:` URI string for the temporary image file, or `null` if
     *         the Base64 data cannot be decoded or the file cannot be written.
     */
    internal fun base64ToTempUri(base64: String): String? =
        try {
            val bytes = Base64.getDecoder().decode(base64)
            val tmp = File.createTempFile("tc-preset-", ".png")
            tmp.deleteOnExit()
            tmp.writeBytes(bytes)
            tmp.toURI().toString()
        } catch (_: Exception) {
            null
        }
}
