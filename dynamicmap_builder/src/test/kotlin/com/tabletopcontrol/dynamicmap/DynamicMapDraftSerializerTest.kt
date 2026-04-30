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
                    label = "Outer Gate",
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
            sunlightAreas = listOf(
                DynamicMapSunlightArea(
                    id = "sunlight-1",
                    label = "Courtyard",
                    points = listOf(
                        DynamicMapPoint(2.0, 3.0),
                        DynamicMapPoint(10.0, 3.0),
                        DynamicMapPoint(9.0, 8.0),
                        DynamicMapPoint(2.0, 7.0),
                    ),
                ),
            ),
            groups = listOf(
                DynamicMapElementGroup(
                    id = "group-1",
                    label = "Entry Ambience",
                    elements = setOf(
                        DynamicMapElementSelection(DynamicMapElementKind.WALL, "wall-1"),
                        DynamicMapElementSelection(DynamicMapElementKind.LIGHT, "light-1"),
                        DynamicMapElementSelection(DynamicMapElementKind.SUNLIGHT_AREA, "sunlight-1"),
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

    @Test
    fun `deserialize supports legacy walls without labels`() {
        val restored = DynamicMapDraftSerializer.deserialize(
            """
            map.cols=30
            map.rows=20
            walls.count=1
            wall.0.id=wall-1
            wall.0.startX=1.0
            wall.0.startY=2.0
            wall.0.endX=3.0
            wall.0.endY=4.0
            lights.count=0
            groups.count=0
            """.trimIndent(),
        )

        assertNotNull(restored)
        assertEquals("Wall 1", restored?.walls?.single()?.label)
    }
}
