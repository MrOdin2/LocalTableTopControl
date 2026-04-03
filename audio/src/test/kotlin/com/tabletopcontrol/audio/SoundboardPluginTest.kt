package com.tabletopcontrol.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SoundboardPluginTest {

    @Test
    fun `columnsForWidth returns 2 for zero width`() {
        assertEquals(2, SoundboardPlugin.columnsForWidth(0.0))
    }

    @Test
    fun `columnsForWidth returns 2 for width just below threshold`() {
        assertEquals(2, SoundboardPlugin.columnsForWidth(SoundboardPlugin.WIDE_THRESHOLD - 1.0))
    }

    @Test
    fun `columnsForWidth returns 8 at the threshold`() {
        assertEquals(8, SoundboardPlugin.columnsForWidth(SoundboardPlugin.WIDE_THRESHOLD))
    }

    @Test
    fun `columnsForWidth returns 8 for wide layout`() {
        assertEquals(8, SoundboardPlugin.columnsForWidth(1200.0))
    }

    @Test
    fun `BUTTON_COUNT is 16`() {
        assertEquals(16, SoundboardPlugin.BUTTON_COUNT)
    }

    @Test
    fun `clampButtonCount clamps to max`() {
        assertEquals(SoundboardPlugin.MAX_BUTTON_COUNT, SoundboardPlugin.clampButtonCount(100))
    }

    @Test
    fun `clampButtonCount clamps negative to zero`() {
        assertEquals(0, SoundboardPlugin.clampButtonCount(-1))
    }

    @Test
    fun `serialize and parse config round trip`() {
        val input = listOf(
            SoundboardPlugin.SlotConfig(label = "Door Slam", uri = "file:///tmp/door.mp3", colorHex = "#FF0000"),
            SoundboardPlugin.SlotConfig(),
        )

        val text = SoundboardPlugin.serializeConfig(input)
        val parsed = SoundboardPlugin.parseConfig(text)

        assertNotNull(parsed)
        assertEquals(input, parsed)
    }

    @Test
    fun `parseConfig returns null for empty payload`() {
        assertNull(SoundboardPlugin.parseConfig("   \n  "))
    }
}
