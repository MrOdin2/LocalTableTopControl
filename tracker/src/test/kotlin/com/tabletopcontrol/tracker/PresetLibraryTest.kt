package com.tabletopcontrol.tracker

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO

class PresetLibraryTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        PresetLibrary.presetsDirForTest = tempDir.toFile()
    }

    @AfterEach
    fun tearDown() {
        PresetLibrary.presetsDirForTest = null
    }

    // ── sanitizeFilename ──────────────────────────────────────────────────────

    @Test
    fun `sanitizeFilename keeps letters digits spaces hyphens dots and underscores`() {
        assertEquals("Dragon_2 - Elite.boss", PresetLibrary.sanitizeFilename("Dragon_2 - Elite.boss"))
    }

    @Test
    fun `sanitizeFilename replaces slashes and special chars with underscore`() {
        assertEquals("Go_blin", PresetLibrary.sanitizeFilename("Go/blin"))
    }

    @Test
    fun `sanitizeFilename returns underscore for a blank name`() {
        assertEquals("_", PresetLibrary.sanitizeFilename(""))
    }

    @Test
    fun `sanitizeFilename truncates to 200 characters`() {
        val long = "A".repeat(300)
        assertEquals(200, PresetLibrary.sanitizeFilename(long).length)
    }

    // ── serialize ─────────────────────────────────────────────────────────────

    @Test
    fun `serialize produces key=value lines for required fields`() {
        val preset = PresetLibrary.Preset("Dragon", 200, 22, 5)
        val text = PresetLibrary.serialize(preset)
        assertTrue(text.contains("name=Dragon"))
        assertTrue(text.contains("hp=200"))
        assertTrue(text.contains("ac=22"))
        assertTrue(text.contains("initiative=5"))
    }

    @Test
    fun `serialize replaces newline characters in name with spaces`() {
        val preset = PresetLibrary.Preset("Line\nBreak", 7, 15, 0)
        assertTrue(PresetLibrary.serialize(preset).contains("name=Line Break"))
    }

    @Test
    fun `serialize includes imageUri when present`() {
        val preset = PresetLibrary.Preset("Orc", 15, 13, 0, imageUri = "file:///tokens/orc.png")
        assertTrue(PresetLibrary.serialize(preset).contains("imageUri=file:///tokens/orc.png"))
    }

    @Test
    fun `serialize omits imageUri line when absent`() {
        val preset = PresetLibrary.Preset("Orc", 15, 13, 0)
        assertTrue(!PresetLibrary.serialize(preset).contains("imageUri="))
    }

    @Test
    fun `serialize includes imageBase64 when present`() {
        val preset = PresetLibrary.Preset("Orc", 15, 13, 0, imageBase64 = "abc123==")
        assertTrue(PresetLibrary.serialize(preset).contains("imageBase64=abc123=="))
    }

    @Test
    fun `serialize includes image scale and offset fields`() {
        val preset = PresetLibrary.Preset("Orc", 15, 13, 0, imageScaleX = 1.5, imageOffsetY = -10.0)
        val text = PresetLibrary.serialize(preset)
        assertTrue(text.contains("imageScaleX=1.5"))
        assertTrue(text.contains("imageOffsetY=-10.0"))
    }

    // ── deserialize ───────────────────────────────────────────────────────────

    @Test
    fun `deserialize parses required fields`() {
        val text = "name=Orc Guard\nhp=25\nac=14\ninitiative=1"
        assertEquals(PresetLibrary.Preset("Orc Guard", 25, 14, 1), PresetLibrary.deserialize(text))
    }

    @Test
    fun `deserialize returns null when name is missing`() {
        assertNull(PresetLibrary.deserialize("hp=25\nac=14\ninitiative=1"))
    }

    @Test
    fun `deserialize returns null when hp is not an integer`() {
        assertNull(PresetLibrary.deserialize("name=Goblin\nhp=not_a_number\nac=15\ninitiative=0"))
    }

    @Test
    fun `deserialize returns null when ac is not an integer`() {
        assertNull(PresetLibrary.deserialize("name=Goblin\nhp=7\nac=not_a_number\ninitiative=0"))
    }

    @Test
    fun `deserialize defaults initiative to zero when absent`() {
        val preset = PresetLibrary.deserialize("name=Goblin\nhp=7\nac=15")
        assertEquals(0, preset?.initiative)
    }

    @Test
    fun `deserialize parses imageUri including equals signs in the value`() {
        val text = "name=Orc\nhp=15\nac=13\ninitiative=0\nimageUri=file:///path/to/orc.png"
        assertEquals("file:///path/to/orc.png", PresetLibrary.deserialize(text)?.imageUri)
    }

    @Test
    fun `deserialize parses imageBase64 including trailing equals padding`() {
        val text = "name=Orc\nhp=15\nac=13\ninitiative=0\nimageBase64=abc123=="
        assertEquals("abc123==", PresetLibrary.deserialize(text)?.imageBase64)
    }

    @Test
    fun `deserialize parses image scale and offset fields`() {
        val text = "name=Orc\nhp=15\nac=13\ninitiative=0\nimageScaleX=1.5\nimageOffsetY=-10.0"
        val preset = PresetLibrary.deserialize(text)
        assertEquals(1.5, preset?.imageScaleX)
        assertEquals(-10.0, preset?.imageOffsetY)
    }

    @Test
    fun `deserialize ignores unknown keys`() {
        val text = "name=Orc\nhp=15\nac=13\ninitiative=0\nunknownField=someValue"
        assertNotNull(PresetLibrary.deserialize(text))
    }

    // ── loadAll ───────────────────────────────────────────────────────────────

    @Test
    fun `loadAll returns empty list when directory is empty`() {
        assertTrue(PresetLibrary.loadAll().isEmpty())
    }

    // ── savePreset and loadAll round-trip ─────────────────────────────────────

    @Test
    fun `savePreset persists a preset that loadAll can retrieve`() {
        val preset = PresetLibrary.Preset("Goblin", 7, 15, 2)
        PresetLibrary.savePreset(preset)

        assertEquals(listOf(preset), PresetLibrary.loadAll())
    }

    @Test
    fun `savePreset creates one file per preset in the presets directory`() {
        PresetLibrary.savePreset(PresetLibrary.Preset("Goblin", 7, 15, 0))
        PresetLibrary.savePreset(PresetLibrary.Preset("Orc", 15, 13, 0))

        val files = tempDir.toFile().listFiles { f -> f.extension == "preset" }
        assertEquals(2, files?.size)
    }

    @Test
    fun `savePreset appends multiple distinct presets`() {
        val goblin = PresetLibrary.Preset("Goblin", 7, 15, 0)
        val orc = PresetLibrary.Preset("Orc", 15, 13, 0)
        PresetLibrary.savePreset(goblin)
        PresetLibrary.savePreset(orc)

        assertEquals(listOf(goblin, orc), PresetLibrary.loadAll())
    }

    @Test
    fun `savePreset replaces an existing preset with the same name`() {
        PresetLibrary.savePreset(PresetLibrary.Preset("Orc", 15, 13, 1))
        PresetLibrary.savePreset(PresetLibrary.Preset("Orc", 30, 14, 0))

        val presets = PresetLibrary.loadAll()
        assertEquals(1, presets.size)
        assertEquals(PresetLibrary.Preset("Orc", 30, 14, 0), presets[0])
    }

    @Test
    fun `savePreset replaces preset without creating a second file`() {
        PresetLibrary.savePreset(PresetLibrary.Preset("Orc", 15, 13, 1))
        PresetLibrary.savePreset(PresetLibrary.Preset("Orc", 30, 14, 0))

        val files = tempDir.toFile().listFiles { f -> f.extension == "preset" }
        assertEquals(1, files?.size)
    }

    @Test
    fun `savePreset round-trips imageUri and imageBase64`() {
        val preset = PresetLibrary.Preset(
            "Dragon", 200, 22, 5,
            imageUri = "file:///tokens/dragon.png",
            imageBase64 = "abc123==",
            imageScaleX = 1.5,
            imageScaleY = 1.5,
            imageOffsetX = 5.0,
            imageOffsetY = -3.0,
        )
        PresetLibrary.savePreset(preset)

        assertEquals(listOf(preset), PresetLibrary.loadAll())
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun `delete removes the named preset`() {
        PresetLibrary.savePreset(PresetLibrary.Preset("Goblin", 7, 15, 0))
        PresetLibrary.savePreset(PresetLibrary.Preset("Orc", 15, 13, 0))

        PresetLibrary.delete("Goblin")

        assertEquals(listOf(PresetLibrary.Preset("Orc", 15, 13, 0)), PresetLibrary.loadAll())
    }

    @Test
    fun `delete removes the preset file from disk`() {
        PresetLibrary.savePreset(PresetLibrary.Preset("Goblin", 7, 15, 0))
        PresetLibrary.delete("Goblin")

        val files = tempDir.toFile().listFiles { f -> f.extension == "preset" }
        assertEquals(0, files?.size)
    }

    @Test
    fun `delete does nothing when the named preset does not exist`() {
        val goblin = PresetLibrary.Preset("Goblin", 7, 15, 0)
        PresetLibrary.savePreset(goblin)

        PresetLibrary.delete("NonExistent")

        assertEquals(listOf(goblin), PresetLibrary.loadAll())
    }

    @Test
    fun `delete on an empty library does not throw`() {
        PresetLibrary.delete("Ghost")
        assertTrue(PresetLibrary.loadAll().isEmpty())
    }

    // ── loadAndScaleImage ─────────────────────────────────────────────────────

    @Test
    fun `loadAndScaleImage returns non-null base64 string for a valid image`() {
        // Create a small test PNG on disk.
        val img = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = java.awt.Color.RED
        g.fillRect(0, 0, 64, 64)
        g.dispose()
        val imgFile = tempDir.resolve("test.png").toFile()
        ImageIO.write(img, "png", imgFile)

        val result = PresetLibrary.loadAndScaleImage(imgFile.toURI().toString(), maxSize = 32)

        assertNotNull(result)
        // The result must be valid Base64 that decodes to a readable image.
        val bytes = Base64.getDecoder().decode(result)
        val decoded = ImageIO.read(bytes.inputStream())
        assertNotNull(decoded)
        assertTrue(decoded.width <= 32)
        assertTrue(decoded.height <= 32)
    }

    @Test
    fun `loadAndScaleImage returns null for a non-existent file`() {
        val result = PresetLibrary.loadAndScaleImage("file:///nonexistent/image.png")
        assertNull(result)
    }

    // ── base64ToTempUri ───────────────────────────────────────────────────────

    @Test
    fun `base64ToTempUri decodes base64 PNG to a readable temp file`() {
        // Create a small PNG and encode it.
        val img = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)
        val base64 = Base64.getEncoder().encodeToString(baos.toByteArray())

        val uri = PresetLibrary.base64ToTempUri(base64)

        assertNotNull(uri)
        val decoded = ImageIO.read(java.io.File(java.net.URI(uri!!)))
        assertNotNull(decoded)
        assertEquals(8, decoded.width)
        assertEquals(8, decoded.height)
    }

    @Test
    fun `base64ToTempUri returns null for invalid base64`() {
        assertNull(PresetLibrary.base64ToTempUri("!!!not-base64!!!"))
    }
}
