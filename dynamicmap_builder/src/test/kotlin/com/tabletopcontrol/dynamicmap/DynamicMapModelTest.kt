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
        val missingWallSelection = DynamicMapElementSelection(DynamicMapElementKind.WALL, "missing")

        assertTrue(document.containsSelection(wallSelection))
        assertTrue(document.containsSelection(lightSelection))
        assertEquals(wall, document.wallById(wall.id))
        assertEquals(light, document.lightById(light.id))
        assertFalse(document.containsSelection(missingWallSelection))
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

        val mixedGroup = DynamicMapElementGroup(
            id = "group-1",
            label = "Mixed Group",
            elements = setOf(wallSelection, missingWallSelection),
        )
        val emptyGroup = DynamicMapElementGroup(
            id = "group-2",
            label = "Empty Group",
            elements = setOf(missingWallSelection),
        )
        val prunedDocument = document.copy(groups = listOf(mixedGroup, emptyGroup)).pruneInvalidGroups()

        assertEquals(
            listOf(mixedGroup.copy(elements = setOf(wallSelection))),
            prunedDocument.groups,
        )
        assertEquals(mixedGroup.copy(elements = setOf(wallSelection)), prunedDocument.groupById("group-1"))
    }
}
