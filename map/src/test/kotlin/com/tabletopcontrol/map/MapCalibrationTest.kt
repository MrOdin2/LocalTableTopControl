package com.tabletopcontrol.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MapCalibrationTest {

    @Test
    fun `default calibration has expected values`() {
        val cal = MapCalibration()
        assertEquals(50.0, cal.pixelsPerUnit)
        assertEquals(0.0, cal.offsetX)
        assertEquals(0.0, cal.offsetY)
        assertEquals(1.0, cal.scale)
    }

    @Test
    fun `toCanvasX applies scale and offsetX`() {
        val cal = MapCalibration(pixelsPerUnit = 50.0, offsetX = 10.0, scale = 2.0)
        // imageX=5 → 5 * 2.0 + 10.0 = 20.0
        assertEquals(20.0, cal.toCanvasX(5.0), 1e-9)
    }

    @Test
    fun `toCanvasY applies scale and offsetY`() {
        val cal = MapCalibration(pixelsPerUnit = 50.0, offsetY = 5.0, scale = 3.0)
        // imageY=4 → 4 * 3.0 + 5.0 = 17.0
        assertEquals(17.0, cal.toCanvasY(4.0), 1e-9)
    }

    @Test
    fun `cellSizeInPixels uses pixelsPerUnit and scale`() {
        val cal = MapCalibration(pixelsPerUnit = 40.0, scale = 2.0)
        // 1 unit * 40 px/unit * 2.0 scale = 80
        assertEquals(80.0, cal.cellSizeInPixels(1.0), 1e-9)
    }

    @Test
    fun `cellSizeInPixels accepts fractional cell sizes`() {
        val cal = MapCalibration(pixelsPerUnit = 50.0, scale = 1.0)
        // 0.5 units * 50 px/unit * 1.0 = 25
        assertEquals(25.0, cal.cellSizeInPixels(0.5), 1e-9)
    }

    @Test
    fun `zero pixelsPerUnit throws`() {
        assertThrows<IllegalArgumentException> {
            MapCalibration(pixelsPerUnit = 0.0)
        }
    }

    @Test
    fun `negative pixelsPerUnit throws`() {
        assertThrows<IllegalArgumentException> {
            MapCalibration(pixelsPerUnit = -1.0)
        }
    }

    @Test
    fun `negative scale throws`() {
        assertThrows<IllegalArgumentException> {
            MapCalibration(scale = -2.0)
        }
    }
}
