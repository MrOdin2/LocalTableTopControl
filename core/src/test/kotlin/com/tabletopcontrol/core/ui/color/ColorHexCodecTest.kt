package com.tabletopcontrol.core.ui.color

import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ColorHexCodecTest {
    @Test
    fun `toHex returns uppercase by default`() {
        assertEquals("#A1B2C3", ColorHexCodec.toHex(Color.web("#a1b2c3")))
    }

    @Test
    fun `toHex supports lowercase output`() {
        assertEquals("#a1b2c3", ColorHexCodec.toHex(Color.web("#A1B2C3"), uppercase = false))
    }

    @Test
    fun `parse returns success for valid hex`() {
        assertTrue(ColorHexCodec.parse("#abc").isSuccess)
        assertTrue(ColorHexCodec.parse("#AABBCC").isSuccess)
    }

    @Test
    fun `parse returns failure for invalid color text`() {
        assertFalse(ColorHexCodec.parse("not-a-color").isSuccess)
    }
}

