package com.tabletopcontrol.new_tracker.preset

import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorTracker
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ActorPresetServiceTest {

    private lateinit var tempDir: java.nio.file.Path
    private lateinit var presetService: ActorPresetService

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("new-tracker-preset-service")
        PresetLibrary.presetsDirForTest = tempDir.toFile()
        presetService = ActorPresetService(ActorTracker())
    }

    @AfterEach
    fun tearDown() {
        PresetLibrary.presetsDirForTest = null
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `saveActor does not ask for confirmation when the preset name is new`() {
        var confirmationRequested = false
        val actor = Actor(name = "Goblin", hp = 7, ac = 15)

        val saved = presetService.saveActor(actor) {
            confirmationRequested = true
            false
        }

        assertTrue(saved)
        assertFalse(confirmationRequested)
        assertEquals(listOf(actor.toPreset()), PresetLibrary.loadAll())
    }

    @Test
    fun `saveActor keeps the existing preset when overwrite is cancelled`() {
        val original = Actor(name = "Goblin", hp = 7, ac = 15)
        val updated = Actor(name = "Goblin", hp = 9, ac = 16)
        presetService.saveActor(original) { false }

        var confirmationRequested = false
        val saved = presetService.saveActor(updated) {
            confirmationRequested = true
            false
        }

        assertFalse(saved)
        assertTrue(confirmationRequested)
        assertEquals(listOf(original.toPreset()), PresetLibrary.loadAll())
    }

    @Test
    fun `saveActor overwrites the existing preset when overwrite is confirmed`() {
        val original = Actor(name = "Goblin", hp = 7, ac = 15)
        val updated = Actor(name = "Goblin", hp = 9, ac = 16)
        presetService.saveActor(original) { false }

        val saved = presetService.saveActor(updated) { true }

        assertTrue(saved)
        assertEquals(listOf(updated.toPreset()), PresetLibrary.loadAll())
    }
}
