package com.tabletopcontrol.tracker

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
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
     * Maximum number of bytes a decoded `imageBase64` thumbnail may occupy.
     *
     * A 256×256 RGBA PNG is typically well under 100 KB; 5 MB is a generous
     * safety cap that still protects low-power devices (Raspberry Pi etc.)
     * from OOM or unexpectedly large temp-file writes caused by hand-edited or
     * corrupt `.preset` files.  The limit is enforced both as a cheap upfront
     * estimate (from the Base64 string length) **and** as a hard streaming cap
     * during decode so that no more than one small read-buffer (8 KB) of decoded
     * data is ever held in memory at once.
     */
    private const val MAX_BASE64_DECODED_BYTES: Int = 5 * 1024 * 1024 // 5 MB

    /**
     * Maximum on-disk size of a `.preset` file that will be read into memory.
     *
     * Base64-encodes a [MAX_BASE64_DECODED_BYTES] payload to roughly
     * `MAX_BASE64_DECODED_BYTES × 4/3` bytes; doubling gives a comfortable
     * margin that still protects against pathological hand-edited files.
     * Files larger than this limit are silently skipped by [loadAll] and
     * treated as "name not found" by [delete] and [fileFor].
     */
    private val MAX_PRESET_FILE_BYTES: Long = MAX_BASE64_DECODED_BYTES.toLong() * 2L

    /**
     * Cache of decoded Base64 thumbnail temp files, keyed by a fingerprint of
     * the Base64 content (string length + first 256 chars + last 256 chars).
     *
     * Reusing temp files for repeated loads of the same preset avoids
     * accumulating many `tc-preset-*.png` files in the system temp directory
     * during long gaming sessions.  Entries whose [File.exists] check returns
     * `false` (e.g., after an OS temp cleanup) are ignored and re-decoded.
     *
     * The cache is deliberately small and bounded by [TEMP_URI_CACHE_MAX].
     */
    private val tempUriCache: ConcurrentHashMap<String, File> = ConcurrentHashMap()
    private const val TEMP_URI_CACHE_MAX: Int = 50

    /**
     * A saved combatant template.
     *
     * @property name         the combatant's display name
     * @property hp           the combatant's hit points
     * @property ac           the combatant's armour class
     * @property initiative   the combatant's default initiative value (defaults to 0)
     * @property folder       the name of the immediate subdirectory inside the presets
     *                        directory where this preset lives; empty string means the
     *                        preset is at the root of the presets directory.  This field
     *                        is **not** written to the `.preset` file — it is inferred
     *                        from the file's location on disk, so moving files between
     *                        folders in the system file browser is all that is needed to
     *                        reorganise the library.
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
        val folder: String = "",
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
     * exists (in the same folder) it is overwritten in-place; otherwise a new
     * file is created using a filesystem-safe version of the name.  When
     * [Preset.folder] is non-empty the file is placed inside a matching
     * subdirectory of [presetsDir]; the subdirectory is created if absent.
     */
    fun savePreset(preset: Preset) {
        // Declared before the try so the catch block can clean it up on failure.
        var tmp: File? = null
        try {
            presetsDir.mkdirs()
            val target = fileFor(preset.name, preset.folder)
            // Write to a unique temp file in the same directory, then rename atomically.
            // Using Files.createTempFile (rather than a deterministic ".tmp" name) avoids
            // a race where two concurrent saves of the same preset collide on the same
            // temp path and corrupt each other's content.
            tmp = Files.createTempFile(target.parentFile.toPath(), target.name, ".tmp").toFile()
            tmp.writeText(serialize(preset))
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                // ATOMIC_MOVE can fail when the underlying file system does not support
                // atomic renames (e.g., some network or virtual file systems), or due to
                // platform or permission limitations. Fall back to a plain replace, which
                // is still safer than writing in-place.
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: Exception) {
            // non-fatal — proceed without persistence; best-effort cleanup of any
            // orphaned temp file (on a successful move the file no longer exists at
            // tmp's path, so delete() returns false without effect).
            runCatching { tmp?.delete() }
        }
    }

    /**
     * Returns all presets stored in the library, sorted first by folder name
     * (root presets — those with an empty folder — sort before any named folder)
     * and then alphabetically by name within each folder.
     *
     * Presets at the root of [presetsDir] have [Preset.folder] set to `""`.
     * Presets inside an immediate subdirectory have [Preset.folder] set to
     * that directory's name.  Deeper nesting is not scanned.
     *
     * @return a list of saved presets, or an empty list when the directory is
     *         absent or contains no parseable `.preset` files.
     */
    fun loadAll(): List<Preset> =
        try {
            val baseDir = presetsDir.canonicalFile
            val results = mutableListOf<Preset>()
            // Root-level .preset files — folder = "".
            presetsDir.listFiles { f -> f.isFile && f.extension == "preset" }
                ?.mapNotNull { f ->
                    if (f.length() > MAX_PRESET_FILE_BYTES) return@mapNotNull null
                    runCatching { deserialize(f.readText()) }.getOrNull()
                }
                ?.let { results.addAll(it) }
            // One level of immediate subdirectories — folder = directory name.
            // Symlinks are excluded so a crafted symlink cannot expose files outside presetsDir.
            presetsDir.listFiles { f -> f.isDirectory && !java.nio.file.Files.isSymbolicLink(f.toPath()) }
                ?.filter { subDir ->
                    // Extra canonical-path guard: the resolved directory must still be inside baseDir.
                    runCatching { subDir.canonicalFile.toPath().startsWith(baseDir.toPath()) }.getOrDefault(false)
                }
                ?.forEach { subDir ->
                    val folderName = subDir.name
                    subDir.listFiles { f -> f.isFile && f.extension == "preset" }
                        ?.mapNotNull { f ->
                            if (f.length() > MAX_PRESET_FILE_BYTES) return@mapNotNull null
                            runCatching { deserialize(f.readText())?.copy(folder = folderName) }.getOrNull()
                        }
                        ?.let { results.addAll(it) }
                }
            results.sortedWith(compareBy({ it.folder }, { it.name }))
        } catch (_: Exception) {
            emptyList()
        }

    /**
     * Removes every `.preset` file whose `name` field equals [name], searching
     * both the root of [presetsDir] and any immediate subdirectories.
     *
     * Does nothing when no such preset exists.
     */
    fun delete(name: String) {
        try {
            val baseDir = presetsDir.canonicalFile
            // Remove from root.
            presetsDir.listFiles { f -> f.isFile && f.extension == "preset" }
                ?.forEach { f ->
                    if (readNameFromFile(f) == name) f.delete()
                }
            // Remove from immediate subdirectories.
            // Symlinks are excluded so a crafted symlink cannot delete files outside presetsDir.
            presetsDir.listFiles { f -> f.isDirectory && !java.nio.file.Files.isSymbolicLink(f.toPath()) }
                ?.filter { subDir ->
                    runCatching { subDir.canonicalFile.toPath().startsWith(baseDir.toPath()) }.getOrDefault(false)
                }
                ?.forEach { subDir ->
                    subDir.listFiles { f -> f.isFile && f.extension == "preset" }
                        ?.forEach { f ->
                            if (readNameFromFile(f) == name) f.delete()
                        }
                }
        } catch (_: Exception) {
            // non-fatal
        }
    }

    // ── File naming ───────────────────────────────────────────────────────────

    /**
     * Returns the `name` value stored in [file] by scanning only as far as
     * needed, without reading the entire file into memory.
     *
     * The parser stops as soon as the `name=` line is found.  It also stops
     * (returning `null`) when it reaches an `imageBase64=` line, because that
     * line can be megabytes long and `name=` is always serialised before it.
     * Files that exceed [MAX_PRESET_FILE_BYTES] are skipped outright to protect
     * low-power devices from OOM caused by corrupt or hand-edited presets.
     *
     * @return the preset name, or `null` if not found or the file is unreadable.
     */
    private fun readNameFromFile(file: File): String? {
        if (file.length() > MAX_PRESET_FILE_BYTES) return null
        return runCatching {
            file.bufferedReader().use { reader ->
                reader.lineSequence().forEach { l ->
                    when {
                        l.startsWith("name=") -> return l.substring(5)
                        // imageBase64 is the last field and can be huge; stop scanning here.
                        l.startsWith("imageBase64=") -> return null
                    }
                }
                null
            }
        }.getOrNull()
    }

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
     * When [folder] is non-empty the file lives inside
     * `presetsDir/sanitizeFilename(folder)/`; otherwise it lives directly
     * in [presetsDir].  The target directory is created if it does not
     * already exist.
     *
     * If a `.preset` file in the target directory already stores a preset
     * with this exact name it is reused (enabling in-place updates).
     * Otherwise a new file is chosen using the sanitized name, appending
     * `_2`, `_3`, … to avoid collisions with files that have a different
     * actual name.
     */
    internal fun fileFor(name: String, folder: String = ""): File {
        // Always work with the canonical presets directory as the base.
        val baseDir = presetsDir.also { it.mkdirs() }.canonicalFile

        val dir = if (folder.isNotEmpty()) {
            val sanitizedFolder = sanitizeFilename(folder)
            // Disallow special directory names that could escape the base dir.
            require(sanitizedFolder != "." && sanitizedFolder != "..") {
                "Folder name '$folder' is not allowed"
            }
            val candidate = File(baseDir, sanitizedFolder)
            val canonicalDir = candidate.canonicalFile
            // Ensure the resolved directory is still inside presetsDir.
            require(canonicalDir.toPath().startsWith(baseDir.toPath())) {
                "Resolved folder '$folder' is outside the presets directory"
            }
            canonicalDir.also { it.mkdirs() }
        } else {
            baseDir
        }
        // Reuse an existing file that already stores this name.
        dir.listFiles { f -> f.isFile && f.extension == "preset" }
            ?.forEach { f ->
                if (readNameFromFile(f) == name) return f
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

    // ── Folder management ─────────────────────────────────────────────────────

    /**
     * Opens [presetsDir] in the operating system's default file manager.
     *
     * The directory is created first if it does not yet exist.  On systems
     * where [java.awt.Desktop] is unavailable (some Linux SBCs), a fallback
     * via `xdg-open` is attempted instead.  All errors are swallowed so that
     * the application is never crashed by a missing file manager.
     */
    fun openPresetsFolder() {
        val dir = presetsDir.also { it.mkdirs() }
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir)
            } else {
                ProcessBuilder("xdg-open", dir.absolutePath).start()
            }
        } catch (_: Exception) {
            try {
                ProcessBuilder("xdg-open", dir.absolutePath).start()
            } catch (_: Exception) {
                // silently ignore — the file manager is a convenience, not a critical feature
            }
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
            // Accept both `file:` URIs and plain filesystem paths.
            val file = run {
                val parsedUri = runCatching { URI(uri) }.getOrNull()
                when {
                    // No (or unparsable) URI scheme: treat as a local filesystem path.
                    parsedUri == null || parsedUri.scheme.isNullOrEmpty() -> File(uri)
                    // Explicit file: URI: use it directly.
                    parsedUri.scheme.equals("file", ignoreCase = true) -> File(parsedUri)
                    // Any other scheme is not supported for local image loading.
                    else -> return null
                }
            }
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
     * The decoded data is streamed directly to disk with an 8 KB buffer so
     * that no full copy of the payload ever lives in heap memory.  An upfront
     * estimate based on the Base64 string length rejects obviously oversized
     * payloads before any decode work begins; the streaming loop enforces the
     * same [MAX_BASE64_DECODED_BYTES] (5 MB) hard cap byte-by-byte.  Any
     * partial temp file created before the limit is hit is deleted immediately.
     *
     * @return a `file:` URI string for the temporary image file, or `null` if
     *         the Base64 data cannot be decoded, exceeds the size limit, or the
     *         file cannot be written.
     */
    internal fun base64ToTempUri(base64: String): String? {
        // Cheap upfront estimate: Base64 encodes ~3 decoded bytes per 4 chars.
        // This never under-estimates, so it safely rejects large strings before
        // any memory allocation for the actual decode.
        if ((base64.length.toLong() * 3L) / 4L > MAX_BASE64_DECODED_BYTES) return null

        // Return a cached temp file if we already decoded this payload this session.
        val key = base64ContentKey(base64)
        tempUriCache[key]?.takeIf { it.exists() }?.let { return it.toURI().toString() }

        val tmp = runCatching { File.createTempFile("tc-preset-", ".png") }.getOrNull()
            ?: return null
        tmp.deleteOnExit()

        return try {
            // Stream-decode directly to the temp file with a small buffer so we
            // never materialise more than the buffer + limit bytes in memory.
            //
            // We feed the Base64 decoder via a lightweight InputStream that reads
            // ASCII bytes directly from the String's char array without first
            // copying the whole string into a byte[] (as String.byteInputStream()
            // would do).  Base64 only ever uses chars < 128, so casting each char
            // code to a byte is exact.
            val charStream = object : java.io.InputStream() {
                private var pos = 0
                override fun read(): Int =
                    if (pos < base64.length) base64[pos++].code and 0xFF else -1
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    // Enforce the standard InputStream contract for bounds checking.
                    if (off < 0 || len < 0 || len > b.size - off) {
                        throw IndexOutOfBoundsException("off=$off, len=$len, buffer size=${b.size}")
                    }
                    if (len == 0) return 0
                    if (pos >= base64.length) return -1
                    val count = minOf(len, base64.length - pos)
                    for (i in 0 until count) {
                        b[off + i] = (base64[pos++].code and 0xFF).toByte()
                    }
                    return count
                }
            }
            Base64.getDecoder().wrap(charStream).use { decoded ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(8 * 1024) // 8 KB read buffer
                    var totalBytes = 0L
                    var n: Int
                    while (decoded.read(buf).also { n = it } != -1) {
                        totalBytes += n
                        if (totalBytes > MAX_BASE64_DECODED_BYTES) {
                            tmp.delete()
                            return null
                        }
                        out.write(buf, 0, n)
                    }
                }
            }
            // Store in cache and enforce a hard size cap.
            tempUriCache[key] = tmp

            // First, drop any entries whose underlying temp file has been deleted.
            tempUriCache.entries.removeIf { !it.value.exists() }

            // If we are still over the cap, evict arbitrary entries until the limit is met.
            while (tempUriCache.size > TEMP_URI_CACHE_MAX) {
                val iterator = tempUriCache.entries.iterator()
                if (!iterator.hasNext()) {
                    break
                }
                val entry = iterator.next()
                // Evict from the in-memory cache only; rely on deleteOnExit() for file cleanup
                // to avoid deleting files while their URIs may still be in use.
                // Remove via the map API; ConcurrentHashMap iterators do not support remove().
                tempUriCache.remove(entry.key, entry.value)
            }
            tmp.toURI().toString()
        } catch (_: Exception) {
            tmp.delete()
            null
        }
    }

    /**
     * Returns a fast, statistically-unique fingerprint of [base64] suitable for
     * use as a cache key.
     *
     * Uses the string length plus up to the first and last 256 characters —
     * enough to distinguish any two different embedded thumbnails in practice
     * without iterating over the whole (potentially multi-megabyte) string.
     */
    private fun base64ContentKey(base64: String): String {
        val len = base64.length
        val prefix = base64.substring(0, minOf(256, len))
        val suffix = base64.substring(maxOf(0, len - 256))
        return "$len:$prefix:$suffix"
    }
}
