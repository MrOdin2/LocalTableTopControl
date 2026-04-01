package com.tabletopcontrol.tracker

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PresetLibraryTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        PresetLibrary.configFileForTest = tempDir.resolve("tracker-presets.conf").toFile()
    }

    @AfterEach
    fun tearDown() {
        PresetLibrary.configFileForTest = null
    }

    // ── serialize ─────────────────────────────────────────────────────────────

    @Test
    fun `serialize produces tab-separated fields`() {
        val preset = PresetLibrary.Preset("Dragon", 200, 22, 5)
        assertEquals("Dragon\t200\t22\t5", PresetLibrary.serialize(preset))
    }

    @Test
    fun `serialize replaces tab characters in name with spaces`() {
        val preset = PresetLibrary.Preset("Go\tblin", 7, 15, 0)
        assertEquals("Go blin\t7\t15\t0", PresetLibrary.serialize(preset))
    }

    @Test
    fun `serialize replaces newline characters in name with spaces`() {
        val preset = PresetLibrary.Preset("Line\nBreak", 7, 15, 0)
        assertEquals("Line Break\t7\t15\t0", PresetLibrary.serialize(preset))
    }

    // ── deserialize ───────────────────────────────────────────────────────────

    @Test
    fun `deserialize parses a valid line`() {
        val preset = PresetLibrary.deserialize("Orc Guard\t25\t14\t1")
        assertEquals(PresetLibrary.Preset("Orc Guard", 25, 14, 1), preset)
    }

    @Test
    fun `deserialize returns null for a line with too few fields`() {
        assertNull(PresetLibrary.deserialize("Name\t7\t15"))
    }

    @Test
    fun `deserialize returns null for a line with too many fields`() {
        assertNull(PresetLibrary.deserialize("Name\t7\t15\t0\textra"))
    }

    @Test
    fun `deserialize returns null when hp is not an integer`() {
        assertNull(PresetLibrary.deserialize("Goblin\tnot_a_number\t15\t0"))
    }

    @Test
    fun `deserialize returns null when ac is not an integer`() {
        assertNull(PresetLibrary.deserialize("Goblin\t7\tnot_a_number\t0"))
    }

    @Test
    fun `deserialize returns null when initiative is not an integer`() {
        assertNull(PresetLibrary.deserialize("Goblin\t7\t15\tnot_a_number"))
    }

    // ── loadAll ───────────────────────────────────────────────────────────────

    @Test
    fun `loadAll returns empty list when the file does not exist`() {
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

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun `delete removes the named preset`() {
        PresetLibrary.savePreset(PresetLibrary.Preset("Goblin", 7, 15, 0))
        PresetLibrary.savePreset(PresetLibrary.Preset("Orc", 15, 13, 0))

        PresetLibrary.delete("Goblin")

        assertEquals(listOf(PresetLibrary.Preset("Orc", 15, 13, 0)), PresetLibrary.loadAll())
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
}
