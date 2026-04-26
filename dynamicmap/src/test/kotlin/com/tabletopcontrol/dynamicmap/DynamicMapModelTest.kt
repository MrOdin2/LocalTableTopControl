package com.tabletopcontrol.dynamicmap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicMapModelTest {
    @Test
    fun `document selection helpers resolve walls and lights`() {
        val wall = DynamicMapWall(
            id = "wall-1",
            start = DynamicMapPoint(1.0, 2.0),
            end = DynamicMapPoint(3.0, 4.0),
        )
        val light = DynamicMapLight(
            id = "light-1",
            label = "Torch",
            position = DynamicMapPoint(5.0, 6.0),
            brightRadius = 4.0,
            dimRadius = 8.0,
            colorHex = "#ffb347",
            enabled = false,
        )
        val document = DynamicMapDocument(
            walls = listOf(wall),
            lights = listOf(light),
        )

        val wallSelection = DynamicMapElementSelection(DynamicMapElementKind.WALL, wall.id)
        val lightSelection = DynamicMapElementSelection(DynamicMapElementKind.LIGHT, light.id)

        assertTrue(document.containsSelection(wallSelection))
        assertTrue(document.containsSelection(lightSelection))
        assertEquals(wall, document.wallById(wall.id))
        assertEquals(light, document.lightById(light.id))
        assertFalse(document.containsSelection(DynamicMapElementSelection(DynamicMapElementKind.WALL, "missing")))
        assertFalse(document.containsSelection(DynamicMapElementSelection(DynamicMapElementKind.LIGHT, "missing")))
        assertEquals(
            setOf(wallSelection, lightSelection),
            document.filterExistingSelections(
                setOf(
                    wallSelection,
                    DynamicMapElementSelection(DynamicMapElementKind.WALL, "missing"),
                    lightSelection,
                ),
            ),
        )
    }
}
