package com.tabletopcontrol.core.ui.color

import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ColorHexCodecTest {
    @Test
    fun `colorToHex uses uppercase RGB format`() {
        assertEquals("#A1B2C3", ColorHexCodec.colorToHex(Color.web("#a1b2c3")))
    }

    @Test
    fun `parseOrNull returns null for invalid color`() {
        assertNull(ColorHexCodec.parseOrNull("not-a-color"))
    }

    @Test
    fun `parseOrDefault falls back for invalid color`() {
        val fallback = Color.web("#112233")
        assertEquals(fallback, ColorHexCodec.parseOrDefault("invalid", fallback))
    }
}
