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
import java.io.File
import java.io.IOException
import java.nio.file.Files
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
        val text = "name=Orc\nhp=15\nac=13\ninitiative=0\nimageUri=file:///path/with=equals/orc.png"
        assertEquals("file:///path/with=equals/orc.png", PresetLibrary.deserialize(text)?.imageUri)
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

    // ── Folder support ────────────────────────────────────────────────────────

    @Test
    fun `savePreset with folder places file in subdirectory`() {
        val preset = PresetLibrary.Preset("Goblin", 7, 15, 0, folder = "Monsters")
        PresetLibrary.savePreset(preset)

        val subDir = tempDir.resolve("Monsters").toFile()
        assertTrue(subDir.isDirectory)
        val files = subDir.listFiles { f -> f.extension == "preset" }
        assertEquals(1, files?.size)
    }

    @Test
    fun `loadAll returns subfolder presets with correct folder field`() {
        // Place a preset file manually in a subdirectory.
        val subDir = tempDir.resolve("Bosses").toFile().also { it.mkdirs() }
        val text = "name=Dragon Lord\nhp=300\nac=24\ninitiative=10"
        File(subDir, "Dragon Lord.preset").writeText(text)

        val presets = PresetLibrary.loadAll()
        assertEquals(1, presets.size)
        assertEquals("Bosses", presets[0].folder)
        assertEquals("Dragon Lord", presets[0].name)
    }

    @Test
    fun `loadAll returns root presets with empty folder field`() {
        PresetLibrary.savePreset(PresetLibrary.Preset("Goblin", 7, 15, 0))

        val presets = PresetLibrary.loadAll()
        assertEquals(1, presets.size)
        assertEquals("", presets[0].folder)
    }

    @Test
    fun `loadAll returns presets from both root and subdirectories`() {
        // Root preset
        PresetLibrary.savePreset(PresetLibrary.Preset("Goblin", 7, 15, 0))
        // Subfolder preset
        PresetLibrary.savePreset(PresetLibrary.Preset("Dragon Lord", 300, 24, 10, folder = "Bosses"))

        val presets = PresetLibrary.loadAll()
        assertEquals(2, presets.size)
        // Root first (folder = ""), then "Bosses"
        assertEquals("", presets[0].folder)
        assertEquals("Bosses", presets[1].folder)
    }

    @Test
    fun `loadAll sorts root presets before subfolder presets`() {
        PresetLibrary.savePreset(PresetLibrary.Preset("Zombie", 22, 8, 0, folder = "Undead"))
        PresetLibrary.savePreset(PresetLibrary.Preset("Goblin", 7, 15, 0))

        val presets = PresetLibrary.loadAll()
        assertEquals("", presets[0].folder)   // root first
        assertEquals("Undead", presets[1].folder)
    }

    @Test
    fun `loadAll sorts subfolder presets alphabetically by folder then name`() {
        PresetLibrary.savePreset(PresetLibrary.Preset("Wyvern", 110, 19, 3, folder = "Beasts"))
        PresetLibrary.savePreset(PresetLibrary.Preset("Zombie", 22, 8, 0, folder = "Undead"))
        PresetLibrary.savePreset(PresetLibrary.Preset("Ghoul", 36, 12, 1, folder = "Undead"))

        val presets = PresetLibrary.loadAll()
        assertEquals("Beasts", presets[0].folder)
        assertEquals("Ghoul", presets[1].name)
        assertEquals("Zombie", presets[2].name)
    }

    @Test
    fun `savePreset round-trip preserves folder field`() {
        val preset = PresetLibrary.Preset("Orc", 15, 13, 0, folder = "Monsters")
        PresetLibrary.savePreset(preset)

        val loaded = PresetLibrary.loadAll()
        assertEquals(1, loaded.size)
        assertEquals("Monsters", loaded[0].folder)
        assertEquals("Orc", loaded[0].name)
    }

    @Test
    fun `delete removes preset from subdirectory`() {
        PresetLibrary.savePreset(PresetLibrary.Preset("Dragon Lord", 300, 24, 10, folder = "Bosses"))
        PresetLibrary.delete("Dragon Lord")

        assertTrue(PresetLibrary.loadAll().isEmpty())
    }

    @Test
    fun `delete removes all matching presets across root and subfolders`() {
        PresetLibrary.savePreset(PresetLibrary.Preset("Orc", 15, 13, 0))
        // Manually place a same-named preset in a subfolder.
        val subDir = tempDir.resolve("Elite").toFile().also { it.mkdirs() }
        File(subDir, "Orc.preset").writeText("name=Orc\nhp=30\nac=16\ninitiative=2")

        // delete() removes ALL matching presets from root and subfolders.
        PresetLibrary.delete("Orc")

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

    @Test
    fun `base64ToTempUri returns null when decoded size exceeds 5 MB limit`() {
        // Encode a payload just over 5 MB — the upfront string-length estimate
        // should reject this before any streaming decode begins.
        val oversized = ByteArray(5 * 1024 * 1024 + 1)
        val base64 = Base64.getEncoder().encodeToString(oversized)
        assertNull(PresetLibrary.base64ToTempUri(base64))
    }

    @Test
    fun `base64ToTempUri accepts payload that decodes to exactly 5 MB`() {
        // A payload of exactly 5 MiB must be accepted and written to a temp file.
        // This exercises the actual boundary behavior of base64ToTempUri().
        val exactly5MB = ByteArray(5 * 1024 * 1024)
        val base64 = Base64.getEncoder().encodeToString(exactly5MB)

        val uri = PresetLibrary.base64ToTempUri(base64)
        assertNotNull(uri, "Exactly 5 MiB payload should be accepted")

        val tempFile = File(uri!!)
        try {
            assertTrue(tempFile.exists(), "Temp file should exist")
            assertEquals(5L * 1024 * 1024, tempFile.length(), "Temp file should contain exactly 5 MiB of decoded data")
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `loadAll ignores symlinked subdirectories`() {
        // Create a directory outside presetsDir containing a .preset file.
        val outside = Files.createTempDirectory("outside-presets")
        try {
            outside.resolve("external.preset").toFile()
                .writeText("name=External\nhp=10\nac=10\ninitiative=5")
            // Attempt to create a symlink inside presetsDir pointing to the outside directory.
            val link = tempDir.resolve("linked")
            try {
                Files.createSymbolicLink(link, outside)
            } catch (_: UnsupportedOperationException) {
                return // Symlinks not supported on this platform — skip test.
            } catch (_: IOException) {
                return // Permission denied or other OS restriction — skip test.
            }
            // loadAll() must not expose files found through the symlinked directory.
            val presets = PresetLibrary.loadAll()
            assertTrue(presets.none { it.name == "External" }, "Symlinked subdirectory should be ignored by loadAll()")
        } finally {
            outside.toFile().deleteRecursively()
        }
    }
}
