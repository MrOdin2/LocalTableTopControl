package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.dynamicmap.runtime.logic.GridConfig
import com.tabletopcontrol.dynamicmap.runtime.logic.contrastingGridColor
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GridConfigTest {

    @Test
    fun `default grid config has expected values`() {
        val cfg = GridConfig()
        assertEquals(1.0, cfg.cellSizeInUnits)
        assertEquals(1.0, cfg.lineWidth)
        assertEquals(true, cfg.visible)
    }

    @Test
    fun `zero cellSizeInUnits throws`() {
        assertThrows<IllegalArgumentException> {
            GridConfig(cellSizeInUnits = 0.0)
        }
    }

    @Test
    fun `negative cellSizeInUnits throws`() {
        assertThrows<IllegalArgumentException> {
            GridConfig(cellSizeInUnits = -5.0)
        }
    }

    @Test
    fun `zero lineWidth throws`() {
        assertThrows<IllegalArgumentException> {
            GridConfig(lineWidth = 0.0)
        }
    }

    @Test
    fun `negative lineWidth throws`() {
        assertThrows<IllegalArgumentException> {
            GridConfig(lineWidth = -1.0)
        }
    }

    @Test
    fun `can create visible false grid`() {
        val cfg = GridConfig(visible = false)
        assertEquals(false, cfg.visible)
    }

    // ── contrastingGridColor ─────────────────────────────────────────────────

    @Test
    fun `contrastingGridColor returns semi-transparent black for white background`() {
        val result = contrastingGridColor(Color.WHITE)
        assertEquals(0.0, result.red, 1e-9)
        assertEquals(0.0, result.green, 1e-9)
        assertEquals(0.0, result.blue, 1e-9)
        assertEquals(0.5, result.opacity, 1e-9)
    }

    @Test
    fun `contrastingGridColor returns semi-transparent white for black background`() {
        val result = contrastingGridColor(Color.BLACK)
        assertEquals(1.0, result.red, 1e-9)
        assertEquals(1.0, result.green, 1e-9)
        assertEquals(1.0, result.blue, 1e-9)
        assertEquals(0.5, result.opacity, 1e-9)
    }

    @Test
    fun `contrastingGridColor returns semi-transparent black for light background`() {
        // Light yellow — luminance above 0.5
        val light = Color.color(0.9, 0.9, 0.5, 1.0)
        val result = contrastingGridColor(light)
        assertEquals(0.0, result.red, 1e-9)
        assertEquals(0.5, result.opacity, 1e-9)
    }

    @Test
    fun `contrastingGridColor returns semi-transparent white for dark background`() {
        // Dark blue — luminance below 0.5
        val dark = Color.color(0.0, 0.0, 0.5, 1.0)
        val result = contrastingGridColor(dark)
        assertEquals(1.0, result.red, 1e-9)
        assertEquals(0.5, result.opacity, 1e-9)
    }

    @Test
    fun `contrastingGridColor result has 50 percent opacity matching default GridConfig color`() {
        val defaultOpacity = GridConfig().color.opacity
        assertEquals(0.5, defaultOpacity, 1e-9)
        assertEquals(defaultOpacity, contrastingGridColor(Color.WHITE).opacity, 1e-9)
        assertEquals(defaultOpacity, contrastingGridColor(Color.BLACK).opacity, 1e-9)
    }
}
