package com.tabletopcontrol.new_tracker.preset

import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.SafeConfigIO
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
 * Shared on-disk preset library for tracker actors.
 *
 * The file format intentionally matches the existing initiative tracker preset
 * files so both plugins can read the same `.preset` files from
 * `~/.tabletopcontrol/presets/`.
 */
object PresetLibrary {

    const val MAX_EMBEDDED_IMAGE_SIZE: Int = 256

    private const val MAX_BASE64_DECODED_BYTES: Int = 5 * 1024 * 1024
    private val MAX_PRESET_FILE_BYTES: Long = MAX_BASE64_DECODED_BYTES.toLong() * 2L

    private val tempUriCache: ConcurrentHashMap<String, File> = ConcurrentHashMap()
    private const val TEMP_URI_CACHE_MAX: Int = 50

    data class Preset(
        val name: String,
        val hp: Int,
        val ac: Int,
        val initiative: Int? = null,
        val initiativeEnabled: Boolean = true,
        val tokenSize: TokenSize = TokenSize.MEDIUM,
        val folder: String = "",
        val imageUri: String? = null,
        val imageBase64: String? = null,
        val imageScaleX: Double = 1.0,
        val imageScaleY: Double = 1.0,
        val imageOffsetX: Double = 0.0,
        val imageOffsetY: Double = 0.0,
    )

    internal var presetsDirForTest: File? = null

    private val presetsDir: File
        get() = presetsDirForTest ?: AppConfigPaths.configSubDir("presets")

    fun savePreset(preset: Preset) {
        var tmp: File? = null
        try {
            presetsDir.mkdirs()
            val target = fileFor(preset.name, preset.folder)
            tmp = Files.createTempFile(target.parentFile.toPath(), target.name, ".tmp").toFile()
            tmp.writeText(serialize(preset))
            try {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: Exception) {
            SafeConfigIO.run { tmp?.delete() }
        }
    }

    fun loadAll(): List<Preset> =
        SafeConfigIO.readOrElse(emptyList()) {
            val baseDir = presetsDir.canonicalFile
            val results = mutableListOf<Preset>()

            presetsDir.listFiles { file -> file.isFile && file.extension == "preset" }
                ?.mapNotNull { file ->
                    if (file.length() > MAX_PRESET_FILE_BYTES) {
                        return@mapNotNull null
                    }
                    runCatching { deserialize(file.readText()) }.getOrNull()
                }
                ?.let(results::addAll)

            presetsDir.listFiles { file ->
                file.isDirectory && !Files.isSymbolicLink(file.toPath())
            }
                ?.filter { subDir ->
                    runCatching {
                        subDir.canonicalFile.toPath().startsWith(baseDir.toPath())
                    }.getOrDefault(false)
                }
                ?.forEach { subDir ->
                    val folderName = subDir.name
                    subDir.listFiles { file -> file.isFile && file.extension == "preset" }
                        ?.mapNotNull { file ->
                            if (file.length() > MAX_PRESET_FILE_BYTES) {
                                return@mapNotNull null
                            }
                            runCatching {
                                deserialize(file.readText())?.copy(folder = folderName)
                            }.getOrNull()
                        }
                        ?.let(results::addAll)
                }

            results.sortedWith(compareBy({ it.folder }, { it.name }))
        }

    fun delete(name: String) {
        SafeConfigIO.run {
            val baseDir = presetsDir.canonicalFile

            presetsDir.listFiles { file ->
                file.isFile &&
                    file.extension == "preset" &&
                    !Files.isSymbolicLink(file.toPath())
            }
                ?.forEach { file ->
                    if (readNameFromFile(file) == name) {
                        file.delete()
                    }
                }

            presetsDir.listFiles { file ->
                file.isDirectory && !Files.isSymbolicLink(file.toPath())
            }
                ?.filter { subDir ->
                    runCatching {
                        subDir.canonicalFile.toPath().startsWith(baseDir.toPath())
                    }.getOrDefault(false)
                }
                ?.forEach { subDir ->
                    subDir.listFiles { file ->
                        file.isFile &&
                            file.extension == "preset" &&
                            !Files.isSymbolicLink(file.toPath())
                    }
                        ?.forEach { file ->
                            if (readNameFromFile(file) == name) {
                                file.delete()
                            }
                        }
                }
        }
    }

    internal fun sanitizeFilename(name: String): String =
        name.map { char ->
            if (char.isLetterOrDigit() || char in " .-_") {
                char
            } else {
                '_'
            }
        }
            .joinToString("")
            .trim()
            .ifEmpty { "_" }
            .take(200)

    internal fun fileFor(name: String, folder: String = ""): File {
        val baseDir = presetsDir.also { it.mkdirs() }.canonicalFile

        val targetDir = if (folder.isNotEmpty()) {
            val sanitizedFolder = sanitizeFilename(folder)
            require(sanitizedFolder != "." && sanitizedFolder != "..") {
                "Folder name '$folder' is not allowed"
            }
            val candidate = File(baseDir, sanitizedFolder)
            val canonicalDir = candidate.canonicalFile
            require(canonicalDir.toPath().startsWith(baseDir.toPath())) {
                "Resolved folder '$folder' is outside the presets directory"
            }
            canonicalDir.also { it.mkdirs() }
        } else {
            baseDir
        }

        targetDir.listFiles { file -> file.isFile && file.extension == "preset" }
            ?.forEach { file ->
                if (readNameFromFile(file) == name) {
                    return file
                }
            }

        val baseName = sanitizeFilename(name)
        val primary = File(targetDir, "$baseName.preset")
        if (!primary.exists()) {
            return primary
        }

        var suffix = 2
        while (true) {
            val candidate = File(targetDir, "${baseName}_$suffix.preset")
            if (!candidate.exists()) {
                return candidate
            }
            suffix++
        }
    }

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
                // Best effort only.
            }
        }
    }

    internal fun serialize(preset: Preset): String = buildString {
        appendLine("name=${preset.name.replace('\n', ' ').replace('\r', ' ')}")
        appendLine("hp=${preset.hp}")
        appendLine("ac=${preset.ac}")
        appendLine("initiative=${null}")
        if (!preset.initiativeEnabled) {
            appendLine("initiativeEnabled=false")
        }
        appendLine("tokenSize=${preset.tokenSize.name}")
        preset.imageUri?.let { appendLine("imageUri=$it") }
        appendLine("imageScaleX=${preset.imageScaleX}")
        appendLine("imageScaleY=${preset.imageScaleY}")
        appendLine("imageOffsetX=${preset.imageOffsetX}")
        appendLine("imageOffsetY=${preset.imageOffsetY}")
        preset.imageBase64?.let { appendLine("imageBase64=$it") }
    }.trimEnd()

    internal fun deserialize(text: String): Preset? {
        val props = mutableMapOf<String, String>()
        for (line in text.lineSequence()) {
            val separator = line.indexOf('=')
            if (separator < 0) {
                continue
            }
            props[line.substring(0, separator)] = line.substring(separator + 1)
        }

        val name = props["name"] ?: return null
        val hp = props["hp"]?.toIntOrNull() ?: return null
        val ac = props["ac"]?.toIntOrNull() ?: return null
        val initiative = props["initiative"]?.toIntOrNull() ?: 0
        val initiativeEnabled = props["initiativeEnabled"]?.toBooleanStrictOrNull() ?: true

        return Preset(
            name = name,
            hp = hp,
            ac = ac,
            initiative = initiative,
            initiativeEnabled = initiativeEnabled,
            tokenSize = TokenSize.fromPersistence(props["tokenSize"]),
            imageUri = props["imageUri"]?.takeIf { it.isNotBlank() },
            imageBase64 = props["imageBase64"]?.takeIf { it.isNotBlank() },
            imageScaleX = props["imageScaleX"]?.toDoubleOrNull() ?: 1.0,
            imageScaleY = props["imageScaleY"]?.toDoubleOrNull() ?: 1.0,
            imageOffsetX = props["imageOffsetX"]?.toDoubleOrNull() ?: 0.0,
            imageOffsetY = props["imageOffsetY"]?.toDoubleOrNull() ?: 0.0,
        )
    }

    internal fun loadAndScaleImage(
        uri: String,
        maxSize: Int = MAX_EMBEDDED_IMAGE_SIZE,
    ): String? = try {
        val file = run {
            val parsedUri = runCatching { URI(uri) }.getOrNull()
            when {
                parsedUri == null || parsedUri.scheme.isNullOrEmpty() -> File(uri)
                parsedUri.scheme.equals("file", ignoreCase = true) -> File(parsedUri)
                else -> return null
            }
        }
        val original: BufferedImage = ImageIO.read(file) ?: return null
        val width = original.width
        val height = original.height
        val scale = minOf(
            maxSize.toDouble() / width,
            maxSize.toDouble() / height,
            1.0,
        )
        val newWidth = maxOf(1, (width * scale).toInt())
        val newHeight = maxOf(1, (height * scale).toInt())
        val scaled = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = scaled.createGraphics()
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        graphics.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY,
        )
        graphics.drawImage(original, 0, 0, newWidth, newHeight, null)
        graphics.dispose()

        val bytes = ByteArrayOutputStream()
        ImageIO.write(scaled, "png", bytes)
        Base64.getEncoder().encodeToString(bytes.toByteArray())
    } catch (_: Exception) {
        null
    }

    internal fun base64ToTempUri(base64: String): String? {
        val paddingCount = base64.takeLast(2).count { it == '=' }
        if ((base64.length.toLong() * 3L) / 4L - paddingCount > MAX_BASE64_DECODED_BYTES) {
            return null
        }

        val key = base64ContentKey(base64)
        tempUriCache[key]?.takeIf { it.exists() }?.let { return it.toURI().toString() }

        val tmp = runCatching { File.createTempFile("tc-preset-", ".png") }.getOrNull()
            ?: return null
        tmp.deleteOnExit()

        return try {
            val charStream = object : java.io.InputStream() {
                private var position = 0

                override fun read(): Int =
                    if (position < base64.length) {
                        base64[position++].code and 0xFF
                    } else {
                        -1
                    }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (offset < 0 || length < 0 || length > buffer.size - offset) {
                        throw IndexOutOfBoundsException(
                            "offset=$offset, length=$length, buffer size=${buffer.size}",
                        )
                    }
                    if (length == 0) {
                        return 0
                    }
                    if (position >= base64.length) {
                        return -1
                    }

                    val count = minOf(length, base64.length - position)
                    for (index in 0 until count) {
                        buffer[offset + index] = (base64[position++].code and 0xFF).toByte()
                    }
                    return count
                }
            }

            Base64.getDecoder().wrap(charStream).use { decoded ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var totalBytes = 0L
                    var readCount: Int
                    while (decoded.read(buffer).also { readCount = it } != -1) {
                        totalBytes += readCount
                        if (totalBytes > MAX_BASE64_DECODED_BYTES) {
                            tmp.delete()
                            return null
                        }
                        output.write(buffer, 0, readCount)
                    }
                }
            }

            tempUriCache[key] = tmp
            tempUriCache.entries.removeIf { !it.value.exists() }
            while (tempUriCache.size > TEMP_URI_CACHE_MAX) {
                val iterator = tempUriCache.entries.iterator()
                if (!iterator.hasNext()) {
                    break
                }
                val entry = iterator.next()
                tempUriCache.remove(entry.key, entry.value)
            }

            tmp.toURI().toString()
        } catch (_: Exception) {
            tmp.delete()
            null
        }
    }

    private fun readNameFromFile(file: File): String? {
        if (file.length() > MAX_PRESET_FILE_BYTES) {
            return null
        }
        return runCatching {
            file.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    when {
                        line.startsWith("name=") -> return line.substring(5)
                        line.startsWith("imageBase64=") -> return null
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun base64ContentKey(base64: String): String {
        val length = base64.length
        val prefix = base64.substring(0, minOf(256, length))
        val suffix = base64.substring(maxOf(0, length - 256))
        return "$length:$prefix:$suffix"
    }
}
