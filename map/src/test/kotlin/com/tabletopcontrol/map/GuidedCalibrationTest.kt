package com.tabletopcontrol.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GuidedCalibrationTest {

    // -------------------------------------------------------------------------
    // Step 1
    // -------------------------------------------------------------------------

    @Test
    fun `step1 translates map so clicked point aligns with target`() {
        val initial = MapCalibration(scale = 1.0, offsetX = 0.0, offsetY = 0.0)
        // Canvas centre is (200, 150). User clicks at (250, 200).
        val result = guidedCalibrationStep1(
            current = initial,
            clickX = 250.0, clickY = 200.0,
            targetX = 200.0, targetY = 150.0,
        )
        assertEquals(1.0, result.scale, 1e-9)
        assertEquals(-50.0, result.offsetX, 1e-9)   // 200 − 250
        assertEquals(-50.0, result.offsetY, 1e-9)   // 150 − 200
    }

    @Test
    fun `step1 preserves existing scale`() {
        val initial = MapCalibration(scale = 2.0, offsetX = 0.0, offsetY = 0.0)
        val result = guidedCalibrationStep1(
            current = initial,
            clickX = 100.0, clickY = 100.0,
            targetX = 200.0, targetY = 200.0,
        )
        assertEquals(2.0, result.scale, 1e-9)
        assertEquals(100.0, result.offsetX, 1e-9)   // 200 − 100
        assertEquals(100.0, result.offsetY, 1e-9)   // 200 − 100
    }

    @Test
    fun `step1 accumulates onto existing offset`() {
        val initial = MapCalibration(scale = 1.0, offsetX = 30.0, offsetY = -10.0)
        val result = guidedCalibrationStep1(
            current = initial,
            clickX = 150.0, clickY = 160.0,
            targetX = 200.0, targetY = 200.0,
        )
        assertEquals(30.0 + (200.0 - 150.0), result.offsetX, 1e-9)   // 80
        assertEquals(-10.0 + (200.0 - 160.0), result.offsetY, 1e-9)  // 30
    }

    @Test
    fun `step1 with click exactly at target leaves offset unchanged`() {
        val initial = MapCalibration(scale = 1.5, offsetX = 20.0, offsetY = 30.0)
        val result = guidedCalibrationStep1(
            current = initial,
            clickX = 200.0, clickY = 150.0,
            targetX = 200.0, targetY = 150.0,
        )
        assertEquals(20.0, result.offsetX, 1e-9)
        assertEquals(30.0, result.offsetY, 1e-9)
    }

    // -------------------------------------------------------------------------
    // Step 2
    // -------------------------------------------------------------------------

    @Test
    fun `step2 scales map so corner ends up cellPx from target`() {
        // Corner is 50 px to the right of the canvas centre (200, 150).
        // Cell size = 100 px → scale factor = 100 / 50 = 2.
        val step1Cal = MapCalibration(scale = 1.0, offsetX = 0.0, offsetY = 0.0)
        val result = guidedCalibrationStep2(
            step1Cal = step1Cal,
            cornerX = 250.0, cornerY = 150.0,
            targetX = 200.0, targetY = 150.0,
            cellSizeInPixels = 100.0,
        )
        assertNotNull(result)
        assertEquals(2.0, result!!.scale, 1e-9)
    }

    @Test
    fun `step2 scales offsets proportionally to keep midpoint fixed`() {
        // scale factor = 50 / 100 = 0.5 (corner 100 px above centre).
        val step1Cal = MapCalibration(scale = 1.0, offsetX = 60.0, offsetY = -40.0)
        val result = guidedCalibrationStep2(
            step1Cal = step1Cal,
            cornerX = 200.0, cornerY = 50.0,
            targetX = 200.0, targetY = 150.0,
            cellSizeInPixels = 50.0,
        )
        assertNotNull(result)
        assertEquals(0.5, result!!.scale, 1e-9)
        assertEquals(30.0, result.offsetX, 1e-9)    // 60 × 0.5
        assertEquals(-20.0, result.offsetY, 1e-9)   // −40 × 0.5
    }

    @Test
    fun `step2 combines with existing scale from step1`() {
        // existing scale = 2.0; corner 25 px right → d = 25; cellPx = 50 → factor = 2.
        // result scale = 2.0 × 2.0 = 4.0.
        val step1Cal = MapCalibration(scale = 2.0, offsetX = 0.0, offsetY = 0.0)
        val result = guidedCalibrationStep2(
            step1Cal = step1Cal,
            cornerX = 225.0, cornerY = 150.0,
            targetX = 200.0, targetY = 150.0,
            cellSizeInPixels = 50.0,
        )
        assertNotNull(result)
        assertEquals(4.0, result!!.scale, 1e-9)
    }

    @Test
    fun `step2 works with diagonal corner click`() {
        // Corner at (230, 190): dx=30, dy=40, d=50. cellPx = 100 → factor = 2.
        val step1Cal = MapCalibration(scale = 1.0, offsetX = 0.0, offsetY = 0.0)
        val result = guidedCalibrationStep2(
            step1Cal = step1Cal,
            cornerX = 230.0, cornerY = 190.0,
            targetX = 200.0, targetY = 150.0,
            cellSizeInPixels = 100.0,
        )
        assertNotNull(result)
        assertEquals(2.0, result!!.scale, 1e-9)
    }

    @Test
    fun `step2 returns null when corner click is at target`() {
        val step1Cal = MapCalibration(scale = 1.0, offsetX = 0.0, offsetY = 0.0)
        val result = guidedCalibrationStep2(
            step1Cal = step1Cal,
            cornerX = 200.0, cornerY = 150.0,
            targetX = 200.0, targetY = 150.0,
            cellSizeInPixels = 50.0,
        )
        assertNull(result)
    }

    @Test
    fun `step2 returns null when corner click is less than 1 px from target`() {
        val step1Cal = MapCalibration(scale = 1.0, offsetX = 0.0, offsetY = 0.0)
        val result = guidedCalibrationStep2(
            step1Cal = step1Cal,
            cornerX = 200.5, cornerY = 150.0,
            targetX = 200.0, targetY = 150.0,
            cellSizeInPixels = 50.0,
        )
        assertNull(result)
    }
}
