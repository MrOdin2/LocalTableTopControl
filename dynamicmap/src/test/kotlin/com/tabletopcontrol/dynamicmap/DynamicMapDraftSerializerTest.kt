package com.tabletopcontrol.dynamicmap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DynamicMapDraftSerializerTest {
    @Test
    fun `serialize and deserialize round trips the draft document`() {
        val original = DynamicMapDocument(
            cols = 42,
            rows = 27,
            backgroundImageUri = "file:///maps/castle.png",
            backgroundDisplayPath = "C:\\maps\\castle.png",
            backgroundCalibration = DynamicMapBackgroundCalibration(
                scale = 0.015,
                offsetX = 1.5,
                offsetY = -0.75,
            ),
            visibility = DynamicMapLayerVisibility(
                background = true,
                walls = true,
                lights = false,
                grid = true,
            ),
            walls = listOf(
                DynamicMapWall(
                    id = "wall-1",
                    start = DynamicMapPoint(1.0, 2.0),
                    end = DynamicMapPoint(5.0, 2.0),
                ),
            ),
            lights = listOf(
                DynamicMapLight(
                    id = "light-1",
                    label = "Torch",
                    position = DynamicMapPoint(6.0, 7.5),
                    brightRadius = 4.0,
                    dimRadius = 8.0,
                    colorHex = "#ffb347",
                ),
            ),
            groups = listOf(
                DynamicMapElementGroup(
                    id = "group-1",
                    label = "Entry Ambience",
                    elements = setOf(
                        DynamicMapElementSelection(DynamicMapElementKind.WALL, "wall-1"),
                        DynamicMapElementSelection(DynamicMapElementKind.LIGHT, "light-1"),
                    ),
                ),
            ),
        )

        val restored = DynamicMapDraftSerializer.deserialize(
            DynamicMapDraftSerializer.serialize(original),
        )

        assertNotNull(restored)
        assertEquals(original, restored)
    }
}
