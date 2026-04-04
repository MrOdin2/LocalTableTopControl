package com.tabletopcontrol.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MeasurementOverlayTest {

    @Test
    fun `line dimension text uses configured units`() {
        val line = MeasurementOverlay(
            id = "m1",
            type = MeasurementType.LINE,
            startCol = 0,
            startRow = 0,
            endCol = 3,
            endRow = 4,
            unitsSuffix = "ft",
        )

        assertEquals("25 ft", line.dimensionText(cellSizeInUnits = 5.0))
    }

    @Test
    fun `rectangle dimension text is inclusive in both axes`() {
        val rect = MeasurementOverlay(
            id = "m2",
            type = MeasurementType.RECTANGLE,
            startCol = 1,
            startRow = 1,
            endCol = 3,
            endRow = 4,
            unitsSuffix = "m",
        )

        assertEquals("6 × 8 m", rect.dimensionText(cellSizeInUnits = 2.0))
    }

    @Test
    fun `circle dimension text shows radius`() {
        val circle = MeasurementOverlay(
            id = "m3",
            type = MeasurementType.CIRCLE,
            startCol = 0,
            startRow = 0,
            endCol = 0,
            endRow = 2,
            unitsSuffix = "ft",
        )

        assertEquals("r 10 ft", circle.dimensionText(cellSizeInUnits = 5.0))
    }

    @Test
    fun `isNearCell detects points near line segment`() {
        val line = MeasurementOverlay(
            id = "m4",
            type = MeasurementType.LINE,
            startCol = 0,
            startRow = 0,
            endCol = 4,
            endRow = 0,
        )

        assertTrue(line.isNearCell(2, 0))
        assertTrue(line.isNearCell(2, 1, toleranceCells = 1.1))
    }

    @Test
    fun `isNearCell supports rectangle selection`() {
        val rect = MeasurementOverlay(
            id = "r1",
            type = MeasurementType.RECTANGLE,
            startCol = 1,
            startRow = 1,
            endCol = 3,
            endRow = 3,
        )
        assertTrue(rect.isNearCell(2, 2))
    }

    @Test
    fun `isNearCell supports circle edge selection`() {
        val circle = MeasurementOverlay(
            id = "c1",
            type = MeasurementType.CIRCLE,
            startCol = 0,
            startRow = 0,
            endCol = 0,
            endRow = 3,
        )
        assertTrue(circle.isNearCell(0, 3))
    }

    @Test
    fun `isNearCell supports cone selection`() {
        val cone = MeasurementOverlay(
            id = "k1",
            type = MeasurementType.CONE,
            startCol = 0,
            startRow = 0,
            endCol = 3,
            endRow = 0,
            coneAngleDegrees = 90.0,
        )
        assertTrue(cone.isNearCell(2, 1))
    }
}
