package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.light.LightEffect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AdvancedLightPreferencesStoreTest {
    @TempDir
    lateinit var tempDir: File

    private var originalUserHome: String? = null

    @BeforeEach
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.absolutePath)
    }

    @AfterEach
    fun tearDown() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome!!)
        } else {
            System.clearProperty("user.home")
        }
    }

    @Test
    fun `save and load preserve per segment preferences`() {
        AdvancedLightPreferencesStore.save(
            listOf(
                AdvancedLightSegmentState(
                    id = 7,
                    name = "Left",
                    color = "#112233",
                    brightness = 0.4,
                    brightnessScale = 0.55,
                    effect = LightEffect.LIGHTNING,
                    effectSpeed = 44,
                    effectIntensity = 199,
                    assignedTokenIds = setOf("hero-uuid", "ranger-uuid"),
                    turnCue = AdvancedLightTurnCue(
                        effect = LightEffect.FADE,
                        color = "#FFAA33",
                        brightness = 0.8,
                        effectSpeed = 77,
                        effectIntensity = 188,
                        duration = AdvancedLightTurnCueDuration.TIMED,
                        durationMillis = 2_500,
                    ),
                ),
                AdvancedLightSegmentState(
                    id = 2,
                    name = "Right",
                    color = "#AABBCC",
                    brightness = 1.0,
                    effect = LightEffect.CANDLE,
                    effectSpeed = 128,
                    effectIntensity = 64,
                ),
            ),
        )

        val loaded = AdvancedLightPreferencesStore.load()

        assertEquals(listOf(7, 2), loaded.order)
        assertEquals(
            AdvancedLightSegmentPreference(
                name = "Left",
                color = "#112233",
                effect = LightEffect.LIGHTNING,
                brightness = 0.4,
                brightnessScale = 0.55,
                effectSpeed = 44,
                effectIntensity = 199,
                assignedTokenIds = setOf("hero-uuid", "ranger-uuid"),
                turnCue = AdvancedLightTurnCue(
                    effect = LightEffect.FADE,
                    color = "#FFAA33",
                    brightness = 0.8,
                    effectSpeed = 77,
                    effectIntensity = 188,
                    duration = AdvancedLightTurnCueDuration.TIMED,
                    durationMillis = 2_500,
                ),
            ),
            loaded.segments[7],
        )
        assertEquals("Right", loaded.segments[2]?.name)
    }

    @Test
    fun `global tracker turn cue opt in is persisted`() {
        AdvancedLightPreferencesStore.save(
            AdvancedLightPreferences(trackerTurnCuesEnabled = true),
        )

        assertEquals(true, AdvancedLightPreferencesStore.load().trackerTurnCuesEnabled)
    }
}
