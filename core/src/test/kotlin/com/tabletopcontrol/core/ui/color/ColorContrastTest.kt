package com.tabletopcontrol.core.ui.color

import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Tests for [ColorContrast]. */
class ColorContrastTest {
    @Test
    fun `textColorHexForBackground picks black for bright color`() {
        assertEquals("#000000", ColorContrast.textColorHexForBackground(Color.web("#F0E68C")))
    }

    @Test
    fun `textColorHexForBackground picks white for dark color`() {
        assertEquals("#FFFFFF", ColorContrast.textColorHexForBackground(Color.web("#222222")))
    }

    @Test
    fun `textColorHexForBackgroundHex handles invalid input`() {
        assertEquals("#FFFFFF", ColorContrast.textColorHexForBackgroundHex("invalid"))
    }
}
