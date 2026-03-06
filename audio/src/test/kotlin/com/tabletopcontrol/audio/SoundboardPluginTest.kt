package com.tabletopcontrol.audio

import org.junit.jupiter.api.Assertions.assertEquals
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
}
