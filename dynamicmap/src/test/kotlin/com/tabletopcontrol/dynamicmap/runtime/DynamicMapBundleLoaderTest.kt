package com.tabletopcontrol.dynamicmap.runtime

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicMapBundleLoaderTest {
    @Test
    fun `loads gameplay bundle manifest and background bytes`() {
        val dir = Files.createTempDirectory("dynamicmap-loader-test")
        val bundleFile = dir.resolve("cave.dynamicmap").toFile()
        val backgroundBytes = byteArrayOf(1, 2, 3, 4)
        ZipOutputStream(bundleFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(DynamicMapBundleLoader.MANIFEST_ENTRY))
            zip.write(
                """
                format=tabletopcontrol.dynamic-map
                format.version=1
                map.cols=30
                map.rows=20
                background.image=background/background.png
                background.scale=0.5
                background.offsetX=-1.25
                background.offsetY=2.0
                walls.count=1
                wall.0.startX=1.0
                wall.0.startY=2.0
                wall.0.endX=3.0
                wall.0.endY=4.0
                lights.count=1
                light.0.posX=5.0
                light.0.posY=6.0
                light.0.brightRadius=7.0
                light.0.dimRadius=8.0
                light.0.colorHex=\#ff8a65
                light.0.enabled=true
                """.trimIndent().toByteArray(StandardCharsets.UTF_8),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("background/background.png"))
            zip.write(backgroundBytes)
            zip.closeEntry()
        }

        val loaded = DynamicMapBundleLoader.load(bundleFile)

        assertTrue(loaded is MapResult.Success)
        val bundle = (loaded as MapResult.Success).value
        assertEquals(30, bundle.cols)
        assertEquals(20, bundle.rows)
        assertEquals("background/background.png", bundle.backgroundEntry)
        assertArrayEquals(backgroundBytes, bundle.backgroundBytes)
        assertEquals(1, bundle.walls.size)
        assertEquals(1, bundle.lights.size)
        assertEquals("#ff8a65", bundle.lights.first().colorHex)
    }

    @Test
    fun `rejects bundles without gameplay manifest`() {
        val dir = Files.createTempDirectory("dynamicmap-loader-missing-manifest")
        val bundleFile = dir.resolve("broken.dynamicmap").toFile()
        ZipOutputStream(bundleFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("other.properties"))
            zip.write("format=wrong".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        val loaded = DynamicMapBundleLoader.load(bundleFile)

        assertTrue(loaded is MapResult.Failure)
    }
}
