package com.tabletopcontrol.map

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
}
