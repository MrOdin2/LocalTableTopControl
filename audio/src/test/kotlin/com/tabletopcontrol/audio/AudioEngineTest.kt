package com.tabletopcontrol.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AudioEngineTest {

    private lateinit var engine: AudioEngine

    @BeforeEach
    fun setUp() {
        engine = AudioEngine()
    }

    @Test
    fun `default volume for every layer is 1_0`() {
        for (layer in AudioEngine.Layer.entries) {
            assertEquals(1.0, engine.getVolume(layer))
        }
    }

    @Test
    fun `setVolume updates the volume for the given layer`() {
        engine.setVolume(AudioEngine.Layer.MUSIC, 0.5)

        assertEquals(0.5, engine.getVolume(AudioEngine.Layer.MUSIC))
    }

    @Test
    fun `setVolume does not affect other layers`() {
        engine.setVolume(AudioEngine.Layer.MUSIC, 0.3)

        assertEquals(1.0, engine.getVolume(AudioEngine.Layer.AMBIENT))
        assertEquals(1.0, engine.getVolume(AudioEngine.Layer.SFX))
    }

    @Test
    fun `setVolume throws on value below 0`() {
        assertThrows<IllegalArgumentException> {
            engine.setVolume(AudioEngine.Layer.SFX, -0.1)
        }
    }

    @Test
    fun `setVolume throws on value above 1`() {
        assertThrows<IllegalArgumentException> {
            engine.setVolume(AudioEngine.Layer.SFX, 1.1)
        }
    }
}
