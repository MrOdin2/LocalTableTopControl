package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.dynamicmap.runtime.logic.GuidedCalibrationAxis
import com.tabletopcontrol.dynamicmap.runtime.logic.MapCalibration
import com.tabletopcontrol.dynamicmap.runtime.logic.guidedCalibrationStep1
import com.tabletopcontrol.dynamicmap.runtime.logic.guidedCalibrationStep2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GuidedCalibrationTest {

    @Test
    fun `step1 translates map so clicked point aligns with target`() {
        val initial = MapCalibration(scale = 1.0, offsetX = 0.0, offsetY = 0.0)
        val result = guidedCalibrationStep1(
            current = initial,
            clickX = 250.0,
            clickY = 200.0,
            targetX = 200.0,
            targetY = 150.0,
        )

        assertEquals(1.0, result.scale, 1e-9)
        assertEquals(-50.0, result.offsetX, 1e-9)
        assertEquals(-50.0, result.offsetY, 1e-9)
    }

    @Test
    fun `step1 preserves existing scale and accumulates offsets`() {
        val initial = MapCalibration(scale = 2.0, offsetX = 30.0, offsetY = -10.0)
        val result = guidedCalibrationStep1(
            current = initial,
            clickX = 150.0,
            clickY = 160.0,
            targetX = 200.0,
            targetY = 200.0,
        )

        assertEquals(2.0, result.scale, 1e-9)
        assertEquals(80.0, result.offsetX, 1e-9)
        assertEquals(30.0, result.offsetY, 1e-9)
    }

    @Test
    fun `step2 horizontal point scales map from horizontal distance only`() {
        val current = MapCalibration(scale = 1.0, offsetX = 0.0, offsetY = 0.0)
        val result = guidedCalibrationStep2(
            currentCalibration = current,
            cornerX = 250.0,
            cornerY = 150.0,
            targetX = 200.0,
            targetY = 150.0,
            cellSizeInPixels = 100.0,
            axis = GuidedCalibrationAxis.HORIZONTAL,
        )

        assertNotNull(result)
        assertEquals(2.0, result!!.scale, 1e-9)
    }

    @Test
    fun `step2 vertical point scales offsets proportionally`() {
        val current = MapCalibration(scale = 1.0, offsetX = 60.0, offsetY = -40.0)
        val result = guidedCalibrationStep2(
            currentCalibration = current,
            cornerX = 200.0,
            cornerY = 50.0,
            targetX = 200.0,
            targetY = 150.0,
            cellSizeInPixels = 50.0,
            axis = GuidedCalibrationAxis.VERTICAL,
        )

        assertNotNull(result)
        assertEquals(0.5, result!!.scale, 1e-9)
        assertEquals(30.0, result.offsetX, 1e-9)
        assertEquals(-20.0, result.offsetY, 1e-9)
    }

    @Test
    fun `step2 ignores the orthogonal axis when horizontal is selected`() {
        val current = MapCalibration(scale = 1.0, offsetX = 0.0, offsetY = 0.0)
        val result = guidedCalibrationStep2(
            currentCalibration = current,
            cornerX = 250.0,
            cornerY = 220.0,
            targetX = 200.0,
            targetY = 150.0,
            cellSizeInPixels = 50.0,
            axis = GuidedCalibrationAxis.HORIZONTAL,
        )

        assertNotNull(result)
        assertEquals(1.0, result!!.scale, 1e-9)
    }

    @Test
    fun `step2 wide mode scales to multi-tile span on selected axis`() {
        val current = MapCalibration(scale = 1.0, offsetX = 12.0, offsetY = -8.0)
        val result = guidedCalibrationStep2(
            currentCalibration = current,
            cornerX = 250.0,
            cornerY = 150.0,
            targetX = 200.0,
            targetY = 150.0,
            cellSizeInPixels = 50.0,
            axis = GuidedCalibrationAxis.HORIZONTAL,
            targetTileSpan = 4,
        )

        assertNotNull(result)
        assertEquals(4.0, result!!.scale, 1e-9)
        assertEquals(48.0, result.offsetX, 1e-9)
        assertEquals(-32.0, result.offsetY, 1e-9)
    }

    @Test
    fun `step2 returns null when horizontal point is at centre`() {
        val current = MapCalibration(scale = 1.0, offsetX = 0.0, offsetY = 0.0)
        val result = guidedCalibrationStep2(
            currentCalibration = current,
            cornerX = 200.0,
            cornerY = 150.0,
            targetX = 200.0,
            targetY = 150.0,
            cellSizeInPixels = 50.0,
            axis = GuidedCalibrationAxis.HORIZONTAL,
        )

        assertNull(result)
    }
}
