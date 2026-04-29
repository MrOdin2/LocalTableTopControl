package com.tabletopcontrol.core.scene

import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.ConfigFiles
import com.tabletopcontrol.core.persistence.SafeConfigIO
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.util.Base64
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Shared on-disk storage for reusable encounter scenes.
 */
object SceneLibrary {
    private const val XML_SCENE_VERSION = 2
    private const val LEGACY_SCENE_VERSION = 1
    private const val SCENE_EXTENSION = "scene"

    internal var scenesDirForTest: File? = null

    private val scenesDir: File
        get() = scenesDirForTest ?: AppConfigPaths.configSubDir("scenes")

    fun save(scene: SavedScene) {
        writeSceneFile(scene, fileFor(scene.name))
    }

    private fun writeSceneFile(scene: SavedScene, target: File) {
        ConfigFiles.writeTextAtomically(target, serialize(scene))
    }

    fun hasScene(name: String): Boolean = existingFileFor(name) != null

    fun loadAll(): List<SavedScene> =
        SafeConfigIO.readOrElse(emptyList()) {
            scenesDir.listFiles { file -> file.isFile && file.extension == SCENE_EXTENSION }
                ?.mapNotNull { file ->
                    loadSceneFile(file)
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
        ConfigFiles.openDirectory(scenesDir)
    }

    internal fun serialize(scene: SavedScene): String {
        val normalizedName = scene.name.replace('\n', ' ').replace('\r', ' ')
        return buildString {
            append("<scene version=\"")
            append(XML_SCENE_VERSION)
            append("\" name=\"")
            append(escapeXmlAttribute(normalizedName))
            appendLine("\">")
            scene.sections.sortedBy { it.key }.forEach { section ->
                append("  <section key=\"")
                append(escapeXmlAttribute(section.key))
                append("\">")
                if (section.payload.isNotEmpty()) {
                    append(cdata(section.payload))
                }
                appendLine("</section>")
            }
            appendLine("</scene>")
        }
    }

    internal fun serializeLegacy(scene: SavedScene): String {
        val props = Properties().apply {
            setProperty("version", LEGACY_SCENE_VERSION.toString())
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

    internal fun deserialize(text: String): SavedScene? = parseScene(text)?.scene

    private fun parseScene(text: String): ParsedScene? {
        val trimmed = text.trimStart()
        return if (trimmed.startsWith("<")) {
            deserializeXml(text)
        } else {
            deserializeLegacy(text)
        }
    }

    private fun deserializeXml(text: String): ParsedScene? {
        val documentBuilderFactory = runCatching {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isXIncludeAware = false
                setExpandEntityReferences(false)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
        }.getOrNull() ?: return null
        val document = runCatching {
            documentBuilderFactory.newDocumentBuilder().parse(InputSource(StringReader(text)))
        }.getOrNull() ?: return null

        val root = document.documentElement ?: return null
        if (root.tagName != "scene") return null
        val version = root.getAttribute("version")?.toIntOrNull() ?: return null
        if (version != XML_SCENE_VERSION) return null

        val name = root.getAttribute("name")?.takeIf { it.isNotBlank() } ?: return null
        val sections = buildList {
            val nodes = root.getElementsByTagName("section")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                if (element.parentNode != root) continue
                val key = element.getAttribute("key")?.takeIf { it.isNotBlank() } ?: continue
                add(SceneSection(key = key, payload = element.textContent ?: ""))
            }
        }

        return ParsedScene(
            scene = SavedScene(name = name, sections = sections),
            format = SceneStorageFormat.XML,
        )
    }

    private fun deserializeLegacy(text: String): ParsedScene? {
        val props = Properties().apply { load(StringReader(text)) }
        val version = props.getProperty("version")?.toIntOrNull() ?: return null
        if (version != LEGACY_SCENE_VERSION) return null

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

        return ParsedScene(
            scene = SavedScene(name = name, sections = sections),
            format = SceneStorageFormat.LEGACY,
        )
    }

    internal fun sanitizeFilename(name: String): String = ConfigFiles.sanitizeFilename(name)

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
            deserialize(file.readText())?.name
        }.getOrNull()

    private fun loadSceneFile(file: File): SavedScene? {
        val parsed = runCatching { parseScene(file.readText()) }.getOrNull() ?: return null
        if (parsed.format == SceneStorageFormat.LEGACY) {
            SafeConfigIO.run {
                writeSceneFile(parsed.scene, file)
            }
        }
        return parsed.scene
    }

    private fun escapeXmlAttribute(value: String): String =
        buildString(value.length) {
            value.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(char)
                }
            }
        }

    private fun cdata(value: String): String = "<![CDATA[" + value.replace("]]>", "]]]]><![CDATA[>") + "]]>"

    private data class ParsedScene(
        val scene: SavedScene,
        val format: SceneStorageFormat,
    )

    private enum class SceneStorageFormat {
        XML,
        LEGACY,
    }
}
