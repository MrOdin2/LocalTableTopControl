package com.tabletopcontrol.dynamicmap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicMapGeometryTest {
    @Test
    fun `rectangle helper creates four boundary walls in clockwise order`() {
        val walls = buildRectangleWalls(
            start = DynamicMapPoint(1.0, 2.0),
            end = DynamicMapPoint(4.0, 6.0),
        )

        assertEquals(4, walls.size)
        assertEquals(DynamicMapPoint(1.0, 2.0), walls[0].start)
        assertEquals(DynamicMapPoint(4.0, 2.0), walls[0].end)
        assertEquals(DynamicMapPoint(4.0, 6.0), walls[1].end)
        assertEquals(DynamicMapPoint(1.0, 6.0), walls[2].end)
        assertEquals(DynamicMapPoint(1.0, 2.0), walls[3].end)
    }

    @Test
    fun `snap point rounds both axes to quarter grid by default`() {
        val point = snapPoint(DynamicMapPoint(1.13, 2.87), 0.25)
        assertEquals(DynamicMapPoint(1.25, 2.75), point)
    }

    @Test
    fun `distance to segment is zero for points on the segment`() {
        val distance = distanceToSegment(
            point = DynamicMapPoint(2.0, 2.0),
            start = DynamicMapPoint(1.0, 1.0),
            end = DynamicMapPoint(3.0, 3.0),
        )
        assertTrue(distance < 0.0001)
    }

    @Test
    fun `fitted background calibration uses map cell counts instead of preview pixels`() {
        val calibration = fittedBackgroundCalibration(
            imageWidth = 2000.0,
            imageHeight = 1000.0,
            cols = 20,
            rows = 10,
        )

        assertEquals(0.01, calibration.scale, 0.0000001)
        assertEquals(0.0, calibration.offsetX)
        assertEquals(0.0, calibration.offsetY)
    }

    @Test
    fun `guided step 1 stores translation in cell units`() {
        val calibration = guidedBackgroundCalibrationStep1(
            current = DynamicMapBackgroundCalibration(scale = 0.01),
            clickX = 90.0,
            clickY = 110.0,
            targetX = 150.0,
            targetY = 170.0,
            cellSizeInPixels = 30.0,
        )

        assertEquals(2.0, calibration.offsetX, 0.0000001)
        assertEquals(2.0, calibration.offsetY, 0.0000001)
    }

    @Test
    fun `guided step 2 scales offsets together with the background`() {
        val calibration = guidedBackgroundCalibrationStep2(
            currentCalibration = DynamicMapBackgroundCalibration(
                scale = 0.01,
                offsetX = 2.0,
                offsetY = -1.0,
            ),
            cornerX = 260.0,
            cornerY = 200.0,
            targetX = 200.0,
            targetY = 200.0,
            cellSizeInPixels = 30.0,
            axis = DynamicMapGuidedCalibrationAxis.HORIZONTAL,
            targetTileSpan = 1,
        )

        require(calibration != null)
        assertEquals(0.005, calibration.scale, 0.0000001)
        assertEquals(1.0, calibration.offsetX, 0.0000001)
        assertEquals(-0.5, calibration.offsetY, 0.0000001)
    }

    @Test
    fun `workspace viewport round trips canvas and world coordinates`() {
        val viewport = DynamicMapWorkspaceViewport().apply {
            zoomIn()
            setPan(x = 80.0, y = -40.0)
        }

        val canvasPoint = viewport.worldToCanvas(
            canvasWidth = 800.0,
            canvasHeight = 600.0,
            worldX = 450.0,
            worldY = 325.0,
        )
        val worldPoint = viewport.canvasToWorld(
            canvasWidth = 800.0,
            canvasHeight = 600.0,
            canvasX = canvasPoint.first,
            canvasY = canvasPoint.second,
        )

        assertEquals(450.0, worldPoint.first, 0.0000001)
        assertEquals(325.0, worldPoint.second, 0.0000001)
    }
}
