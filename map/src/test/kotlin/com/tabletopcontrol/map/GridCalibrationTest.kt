package com.tabletopcontrol.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GridCalibrationTest {

    @Test
    fun `default calibration has expected values`() {
        val cal = GridCalibration()
        assertEquals(50.0, cal.cellSizeInPixels)
        assertEquals(1.0, cal.scale)
        assertEquals(0.0, cal.offsetX)
        assertEquals(0.0, cal.offsetY)
    }

    @Test
    fun `effectiveCellSizeInPixels multiplies cellSizeInPixels by scale`() {
        val cal = GridCalibration(cellSizeInPixels = 40.0, scale = 2.0)
        assertEquals(80.0, cal.effectiveCellSizeInPixels(), 1e-9)
    }

    @Test
    fun `effectiveCellSizeInPixels with scale 1 returns cellSizeInPixels unchanged`() {
        val cal = GridCalibration(cellSizeInPixels = 60.0, scale = 1.0)
        assertEquals(60.0, cal.effectiveCellSizeInPixels(), 1e-9)
    }

    @Test
    fun `effectiveCellSizeInPixels supports fractional scale`() {
        val cal = GridCalibration(cellSizeInPixels = 100.0, scale = 0.5)
        assertEquals(50.0, cal.effectiveCellSizeInPixels(), 1e-9)
    }

    @Test
    fun `custom offsets are stored correctly`() {
        val cal = GridCalibration(offsetX = 15.0, offsetY = -10.0)
        assertEquals(15.0, cal.offsetX, 1e-9)
        assertEquals(-10.0, cal.offsetY, 1e-9)
    }

    @Test
    fun `zero cellSizeInPixels throws`() {
        assertThrows<IllegalArgumentException> {
            GridCalibration(cellSizeInPixels = 0.0)
        }
    }

    @Test
    fun `negative cellSizeInPixels throws`() {
        assertThrows<IllegalArgumentException> {
            GridCalibration(cellSizeInPixels = -10.0)
        }
    }

    @Test
    fun `zero scale throws`() {
        assertThrows<IllegalArgumentException> {
            GridCalibration(scale = 0.0)
        }
    }

    @Test
    fun `negative scale throws`() {
        assertThrows<IllegalArgumentException> {
            GridCalibration(scale = -1.0)
        }
    }

    @Test
    fun `small positive scale is accepted`() {
        val cal = GridCalibration(scale = 0.01)
        assertEquals(0.01, cal.scale, 1e-9)
    }
}
