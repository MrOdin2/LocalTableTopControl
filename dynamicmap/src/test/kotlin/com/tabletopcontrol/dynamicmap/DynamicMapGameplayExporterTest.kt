package com.tabletopcontrol.dynamicmap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipInputStream

class DynamicMapGameplayExporterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `gameplay export bundles background and strips editor metadata`() {
        val backgroundFile = tempDir.resolve("Secret Dungeon Name.png").toFile().apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val document = DynamicMapDocument(
            cols = 18,
            rows = 12,
            backgroundImageUri = backgroundFile.toURI().toString(),
            backgroundDisplayPath = "Secret Dungeon Name.png",
            backgroundCalibration = DynamicMapBackgroundCalibration(
                scale = 1.25,
                offsetX = 2.0,
                offsetY = -1.0,
            ),
            visibility = DynamicMapLayerVisibility(
                background = false,
                walls = false,
                lights = false,
                grid = false,
            ),
            walls = listOf(
                DynamicMapWall(
                    id = "secret-wall-id",
                    label = "Boss Gate",
                    start = DynamicMapPoint(1.0, 2.0),
                    end = DynamicMapPoint(3.0, 2.0),
                ),
                DynamicMapWall(
                    id = "secret-wall-id-2",
                    label = "Boss Gate",
                    start = DynamicMapPoint(3.0, 2.0),
                    end = DynamicMapPoint(5.0, 2.0),
                ),
            ),
            lights = listOf(
                DynamicMapLight(
                    id = "secret-light-id",
                    label = "Ambush Torch",
                    position = DynamicMapPoint(4.0, 5.0),
                    brightRadius = 3.0,
                    dimRadius = 6.0,
                    colorHex = "#ffb347",
                    enabled = false,
                ),
            ),
            groups = listOf(
                DynamicMapElementGroup(
                    id = "secret-group-id",
                    label = "Ambush Group",
                    elements = setOf(DynamicMapElementSelection(DynamicMapElementKind.WALL, "secret-wall-id")),
                ),
            ),
        )
        val targetFile = tempDir.resolve("export.dynamicmap").toFile()

        val summary = DynamicMapGameplayExporter.export(document, targetFile).getOrThrow()

        assertTrue(summary.includedBackground)
        assertFalse(summary.missingBackground)

        val entries = readZipEntries(targetFile)
        assertEquals(backgroundFile.readBytes().toList(), entries.getValue("background/background.png").toList())

        val manifest = entries.getValue(DynamicMapGameplayExporter.MANIFEST_ENTRY).toString(StandardCharsets.UTF_8)
        assertFalse(manifest.contains("Boss Gate"))
        assertFalse(manifest.contains("Ambush Torch"))
        assertFalse(manifest.contains("Ambush Group"))
        assertFalse(manifest.contains("secret-wall-id"))
        assertFalse(manifest.contains("secret-wall-id-2"))
        assertFalse(manifest.contains("secret-light-id"))
        assertFalse(manifest.contains("secret-group-id"))
        assertFalse(manifest.contains("visibility."))
        assertFalse(manifest.contains("label"))
        assertFalse(manifest.contains("groups"))

        val props = Properties().apply { load(StringReader(manifest)) }
        assertEquals("tabletopcontrol.dynamic-map", props.getProperty("format"))
        assertEquals("1", props.getProperty("format.version"))
        assertEquals("18", props.getProperty("map.cols"))
        assertEquals("12", props.getProperty("map.rows"))
        assertEquals("background/background.png", props.getProperty("background.image"))
        assertEquals("1.25", props.getProperty("background.scale"))
        assertEquals("1", props.getProperty("walls.count"))
        assertEquals("1.0", props.getProperty("wall.0.startX"))
        assertEquals("5.0", props.getProperty("wall.0.endX"))
        assertEquals("1", props.getProperty("lights.count"))
        assertEquals("4.0", props.getProperty("light.0.posX"))
        assertEquals("#ffb347", props.getProperty("light.0.colorHex"))
        assertEquals("false", props.getProperty("light.0.enabled"))
    }

    @Test
    fun `gameplay export succeeds when background file is missing`() {
        val document = DynamicMapDocument(
            backgroundImageUri = tempDir.resolve("missing.png").toUri().toString(),
        )
        val targetFile = tempDir.resolve("missing-background.dynamicmap").toFile()

        val summary = DynamicMapGameplayExporter.export(document, targetFile).getOrThrow()

        assertFalse(summary.includedBackground)
        assertTrue(summary.missingBackground)

        val entries = readZipEntries(targetFile)
        val manifest = entries.getValue(DynamicMapGameplayExporter.MANIFEST_ENTRY).toString(StandardCharsets.UTF_8)
        val props = Properties().apply { load(StringReader(manifest)) }

        assertFalse(props.containsKey("background.image"))
        assertEquals(setOf(DynamicMapGameplayExporter.MANIFEST_ENTRY), entries.keys)
    }

    private fun readZipEntries(file: java.io.File): Map<String, ByteArray> =
        ZipInputStream(file.inputStream()).use { zip ->
            buildMap {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val bytes = ByteArrayOutputStream()
                    zip.copyTo(bytes)
                    put(entry.name, bytes.toByteArray())
                    zip.closeEntry()
                }
            }
        }
}
