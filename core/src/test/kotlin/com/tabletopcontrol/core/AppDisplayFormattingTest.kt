package com.tabletopcontrol.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppDisplayFormattingTest {

    @Test
    fun `screen label uses effective output pixels when Windows scaling is active`() {
        assertEquals(
            "Screen 2: 1920 x 1080 (Windows scale 150%)",
            formatScreenLabel(
                index = 1,
                logicalWidth = 1280.0,
                logicalHeight = 720.0,
                outputScaleX = 1.5,
                outputScaleY = 1.5,
            ),
        )
    }

    @Test
    fun `screen label omits scale suffix at 100 percent`() {
        assertEquals(
            "Screen 1: 1920 x 1080",
            formatScreenLabel(
                index = 0,
                logicalWidth = 1920.0,
                logicalHeight = 1080.0,
                outputScaleX = 1.0,
                outputScaleY = 1.0,
            ),
        )
    }

    @Test
    fun `screen label shows separate scale percentages when axes differ`() {
        assertEquals(
            "Screen 3: 2000 x 1125 (Windows scale 125%/150%)",
            formatScreenLabel(
                index = 2,
                logicalWidth = 1600.0,
                logicalHeight = 750.0,
                outputScaleX = 1.25,
                outputScaleY = 1.5,
            ),
        )
    }

    @Test
    fun `effective pixel span falls back to logical size for invalid scale`() {
        assertEquals(1920, effectivePixelSpan(logicalSpan = 1920.0, outputScale = Double.NaN))
        assertEquals(1920, effectivePixelSpan(logicalSpan = 1920.0, outputScale = 0.0))
        assertEquals(0, effectivePixelSpan(logicalSpan = 0.0, outputScale = 1.5))
    }
}
