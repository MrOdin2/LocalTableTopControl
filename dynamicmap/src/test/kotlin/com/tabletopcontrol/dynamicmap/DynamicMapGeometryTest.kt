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
}
