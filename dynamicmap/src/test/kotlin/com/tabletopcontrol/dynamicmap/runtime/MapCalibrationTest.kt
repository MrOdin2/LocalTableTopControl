package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.dynamicmap.runtime.logic.MapCalibration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MapCalibrationTest {

    @Test
    fun `default calibration has expected values`() {
        val cal = MapCalibration()
        assertEquals(1.0, cal.scale)
        assertEquals(0.0, cal.offsetX)
        assertEquals(0.0, cal.offsetY)
    }

    @Test
    fun `custom calibration stores provided values`() {
        val cal = MapCalibration(scale = 2.5, offsetX = 10.0, offsetY = -20.0)
        assertEquals(2.5, cal.scale, 1e-9)
        assertEquals(10.0, cal.offsetX, 1e-9)
        assertEquals(-20.0, cal.offsetY, 1e-9)
    }

    @Test
    fun `zero scale throws`() {
        assertThrows<IllegalArgumentException> {
            MapCalibration(scale = 0.0)
        }
    }

    @Test
    fun `negative scale throws`() {
        assertThrows<IllegalArgumentException> {
            MapCalibration(scale = -2.0)
        }
    }

    @Test
    fun `positive scale is accepted`() {
        val cal = MapCalibration(scale = 0.01)
        assertEquals(0.01, cal.scale, 1e-9)
    }
}
