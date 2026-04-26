package com.tabletopcontrol.new_tracker.preset

import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorImageSettings
import com.tabletopcontrol.new_tracker.model.ActorTracker
import com.tabletopcontrol.new_tracker.scene.TrackerSceneState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI
import java.nio.file.Files

class ActorPresetServiceTest {

    private lateinit var tempDir: java.nio.file.Path
    private lateinit var cacheDir: java.nio.file.Path
    private lateinit var presetService: ActorPresetService

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("new-tracker-preset-service")
        cacheDir = Files.createTempDirectory("new-tracker-preset-service-cache")
        PresetLibrary.presetsDirForTest = tempDir.toFile()
        PresetLibrary.presetImageCacheDirForTest = cacheDir.toFile()
        presetService = ActorPresetService(ActorTracker())
    }

    @AfterEach
    fun tearDown() {
        PresetLibrary.presetsDirForTest = null
        PresetLibrary.presetImageCacheDirForTest = null
        tempDir.toFile().deleteRecursively()
        cacheDir.toFile().deleteRecursively()
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

    @Test
    fun `recoverSceneActors rebuilds missing preset token images into cache`() {
        PresetLibrary.savePreset(
            PresetLibrary.Preset(
                name = "Goblin",
                hp = 7,
                ac = 15,
                tokenSize = TokenSize.MEDIUM,
                imageUri = "file:///missing/tc-preset.png",
                imageBase64 = SINGLE_PIXEL_PNG_BASE64,
            ),
        )

        val missingImage = Files.createTempFile("missing-scene-token", ".png")
        Files.deleteIfExists(missingImage)
        val sceneState = TrackerSceneState(
            actors = listOf(
                Actor(
                    name = "Goblin",
                    hp = 7,
                    ac = 15,
                    tokenSize = TokenSize.MEDIUM,
                    imageSettings = ActorImageSettings(uri = missingImage.toUri().toString()),
                ),
            ),
            activeActorId = null,
            roundCount = 0,
        )

        val recovered = presetService.recoverSceneActors(sceneState)
        val recoveredUri = requireNotNull(recovered.actors.single().imageSettings.uri)

        assertNotEquals(missingImage.toUri().toString(), recoveredUri)
        assertTrue(File(URI(recoveredUri)).exists())
        assertTrue(File(URI(recoveredUri)).parentFile.canonicalPath.startsWith(cacheDir.toFile().canonicalPath))
    }

    private companion object {
        const val SINGLE_PIXEL_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+iM3cAAAAASUVORK5CYII="
    }
}
