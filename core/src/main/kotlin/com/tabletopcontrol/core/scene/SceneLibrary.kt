package com.tabletopcontrol.core.scene

import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.SafeConfigIO
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties

/**
 * Shared on-disk storage for reusable encounter scenes.
 */
object SceneLibrary {
    private const val SCENE_VERSION = 1
    private const val SCENE_EXTENSION = "scene"

    internal var scenesDirForTest: File? = null

    private val scenesDir: File
        get() = scenesDirForTest ?: AppConfigPaths.configSubDir("scenes")

    fun save(scene: SavedScene) {
        var tmp: File? = null
        try {
            scenesDir.mkdirs()
            val target = fileFor(scene.name)
            tmp = Files.createTempFile(target.parentFile.toPath(), target.nameWithoutExtension, ".tmp").toFile()
            tmp.writeText(serialize(scene))
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

    fun hasScene(name: String): Boolean = existingFileFor(name) != null

    fun loadAll(): List<SavedScene> =
        SafeConfigIO.readOrElse(emptyList()) {
            scenesDir.listFiles { file -> file.isFile && file.extension == SCENE_EXTENSION }
                ?.mapNotNull { file ->
                    runCatching { deserialize(file.readText()) }.getOrNull()
                }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        }

    fun delete(name: String) {
        SafeConfigIO.run {
            scenesDir.listFiles { file -> file.isFile && file.extension == SCENE_EXTENSION }
                ?.forEach { file ->
                    if (readNameFromFile(file) == name) {
                        file.delete()
                    }
                }
        }
    }

    fun openScenesFolder() {
        val dir = scenesDir.also { it.mkdirs() }
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

    internal fun serialize(scene: SavedScene): String {
        val props = Properties().apply {
            setProperty("version", SCENE_VERSION.toString())
            setProperty("name", scene.name.replace('\n', ' ').replace('\r', ' '))
            setProperty("section.count", scene.sections.size.toString())
            scene.sections.sortedBy { it.key }.forEachIndexed { index, section ->
                setProperty("section.$index.key", section.key)
                setProperty(
                    "section.$index.payload",
                    Base64.getEncoder().encodeToString(section.payload.toByteArray(Charsets.UTF_8)),
                )
            }
        }

        return StringWriter().use { writer ->
            props.store(writer, "TabletopControl scene")
            writer.toString()
        }
    }

    internal fun deserialize(text: String): SavedScene? {
        val props = Properties().apply { load(StringReader(text)) }
        val version = props.getProperty("version")?.toIntOrNull() ?: return null
        if (version != SCENE_VERSION) return null

        val name = props.getProperty("name")?.takeIf { it.isNotBlank() } ?: return null
        val sectionCount = props.getProperty("section.count")?.toIntOrNull()?.coerceAtLeast(0) ?: 0

        val decoder = Base64.getDecoder()
        val sections = buildList {
            for (index in 0 until sectionCount) {
                val key = props.getProperty("section.$index.key")?.takeIf { it.isNotBlank() } ?: continue
                val payloadBase64 = props.getProperty("section.$index.payload") ?: continue
                val payload = runCatching {
                    String(decoder.decode(payloadBase64), Charsets.UTF_8)
                }.getOrNull() ?: continue
                add(SceneSection(key = key, payload = payload))
            }
        }

        return SavedScene(name = name, sections = sections)
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

    internal fun fileFor(name: String): File {
        val targetDir = scenesDir.also { it.mkdirs() }
        existingFileFor(name)?.let { return it }

        val baseName = sanitizeFilename(name)
        val primary = File(targetDir, "$baseName.$SCENE_EXTENSION")
        if (!primary.exists()) {
            return primary
        }

        var suffix = 2
        while (true) {
            val candidate = File(targetDir, "${baseName}_$suffix.$SCENE_EXTENSION")
            if (!candidate.exists()) {
                return candidate
            }
            suffix++
        }
    }

    private fun existingFileFor(name: String): File? {
        scenesDir.listFiles { file -> file.isFile && file.extension == SCENE_EXTENSION }
            ?.forEach { file ->
                if (readNameFromFile(file) == name) {
                    return file
                }
            }
        return null
    }

    private fun readNameFromFile(file: File): String? =
        runCatching {
            Properties().apply { file.reader().use(::load) }.getProperty("name")
        }.getOrNull()
}
