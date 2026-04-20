package com.tabletopcontrol.new_tracker.preset

import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorImageSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PresetLibraryTest {

    private lateinit var tempDir: java.nio.file.Path

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("new-tracker-presets")
        PresetLibrary.presetsDirForTest = tempDir.toFile()
    }

    @AfterEach
    fun tearDown() {
        PresetLibrary.presetsDirForTest = null
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `deserialize legacy tracker preset keeps initiative enabled`() {
        val preset = requireNotNull(PresetLibrary.deserialize(
            """
            name=Goblin
            hp=7
            ac=15
            initiative=0
            """.trimIndent(),
        )) 

        assertTrue(preset.initiativeEnabled)
        assertEquals(0, preset.initiative)
        assertEquals(TokenSize.MEDIUM, preset.tokenSize)
    }

    @Test
    fun `serialize and deserialize preserve nullable initiative flag`() {
        val preset = PresetLibrary.Preset(
            name = "Scout",
            hp = 11,
            ac = 14,
            initiative = 0,
            initiativeEnabled = false,
            tokenSize = TokenSize.LARGE,
        )

        val roundTrip = PresetLibrary.deserialize(PresetLibrary.serialize(preset))

        assertEquals(preset, roundTrip)
    }

    @Test
    fun `savePreset and loadAll preserve folder image and cleared initiative fields`() {
        val preset = PresetLibrary.Preset(
            name = "Dragon",
            hp = 200,
            ac = 22,
            initiative = 0,
            initiativeEnabled = false,
            folder = "Bosses",
            tokenSize = TokenSize.HUGE,
            imageUri = "file:///tokens/dragon.png",
            imageBase64 = "abc123==",
            imageScaleX = 1.5,
            imageScaleY = 1.25,
            imageOffsetX = 10.0,
            imageOffsetY = -4.0,
        )

        PresetLibrary.savePreset(preset)

        assertEquals(listOf(preset), PresetLibrary.loadAll())
    }

    @Test
    fun `actor preset mapping always clears initiative and preserves image settings`() {
        val actor = Actor(
            name = "Specter",
            hp = 22,
            ac = 12,
            initiative = 18,
            tokenSize = TokenSize.LARGE,
            imageSettings = ActorImageSettings(
                uri = "file:///ghost.png",
                scaleX = 1.3,
                scaleY = 1.1,
                offsetX = 6.0,
                offsetY = -2.0,
            ),
        )

        val preset = actor.toPreset()
        val restoredActor = preset.toActor()

        assertEquals(0, preset.initiative)
        assertEquals(false, preset.initiativeEnabled)
        assertEquals(TokenSize.LARGE, preset.tokenSize)
        assertNull(restoredActor.initiative)
        assertEquals(actor.name, restoredActor.name)
        assertEquals(actor.hp, restoredActor.hp)
        assertEquals(actor.ac, restoredActor.ac)
        assertEquals(actor.tokenSize, restoredActor.tokenSize)
        assertEquals(actor.imageSettings, restoredActor.imageSettings)
    }

    @Test
    fun `loading a legacy preset still clears initiative for new tracker actors`() {
        val preset = PresetLibrary.Preset(
            name = "Bandit",
            hp = 11,
            ac = 12,
            initiative = 14,
            initiativeEnabled = true,
        )

        val restoredActor = preset.toActor()

        assertEquals(14, preset.initiative)
        assertEquals(true, preset.initiativeEnabled)
        assertNull(restoredActor.initiative)
    }
}
