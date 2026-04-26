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
    fun `fine movement step uses one screen pixel capped to a small tile step`() {
        assertEquals(0.025, fineMovementStepInTiles(cellSize = 20.0, scale = 2.0), 0.0000001)
        assertEquals(0.05, fineMovementStepInTiles(cellSize = 8.0, scale = 1.0), 0.0000001)
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
    fun `move selections translates selected walls and lights together`() {
        val wall = DynamicMapWall(
            id = "wall-1",
            start = DynamicMapPoint(1.0, 2.0),
            end = DynamicMapPoint(3.0, 2.0),
        )
        val otherWall = DynamicMapWall(
            id = "wall-2",
            start = DynamicMapPoint(5.0, 5.0),
            end = DynamicMapPoint(6.0, 5.0),
        )
        val light = DynamicMapLight(
            id = "light-1",
            label = "Torch",
            position = DynamicMapPoint(4.0, 4.0),
            brightRadius = 4.0,
            dimRadius = 8.0,
            colorHex = "#ffb347",
        )
        val document = DynamicMapDocument(
            walls = listOf(wall, otherWall),
            lights = listOf(light),
        )

        val moved = document.moveSelections(
            selections = setOf(
                DynamicMapElementSelection(DynamicMapElementKind.WALL, wall.id),
                DynamicMapElementSelection(DynamicMapElementKind.LIGHT, light.id),
            ),
            delta = DynamicMapPoint(0.5, -1.0),
        )

        assertEquals(DynamicMapPoint(1.5, 1.0), moved.walls[0].start)
        assertEquals(DynamicMapPoint(3.5, 1.0), moved.walls[0].end)
        assertEquals(otherWall, moved.walls[1])
        assertEquals(DynamicMapPoint(4.5, 3.0), moved.lights[0].position)
    }

    @Test
    fun `movement delta is clamped to keep selected geometry inside map bounds`() {
        val document = DynamicMapDocument(
            cols = 10,
            rows = 8,
            walls = listOf(
                DynamicMapWall(
                    id = "wall-1",
                    start = DynamicMapPoint(1.0, 2.0),
                    end = DynamicMapPoint(3.0, 4.0),
                ),
            ),
            lights = listOf(
                DynamicMapLight(
                    id = "light-1",
                    label = "Torch",
                    position = DynamicMapPoint(8.0, 7.0),
                    brightRadius = 4.0,
                    dimRadius = 8.0,
                    colorHex = "#ffb347",
                ),
            ),
        )

        val delta = document.clampMovementDelta(
            selections = setOf(
                DynamicMapElementSelection(DynamicMapElementKind.WALL, "wall-1"),
                DynamicMapElementSelection(DynamicMapElementKind.LIGHT, "light-1"),
            ),
            requestedDelta = DynamicMapPoint(5.0, -3.0),
        )

        assertEquals(DynamicMapPoint(2.0, -2.0), delta)
    }

    @Test
    fun `wall topology optimization merges touching collinear walls and updates groups`() {
        val document = DynamicMapDocument(
            walls = listOf(
                DynamicMapWall(
                    id = "wall-a",
                    label = "North Wall",
                    start = DynamicMapPoint(0.0, 0.0),
                    end = DynamicMapPoint(1.0, 0.0),
                ),
                DynamicMapWall(
                    id = "wall-b",
                    label = "North Wall",
                    start = DynamicMapPoint(2.0, 0.0),
                    end = DynamicMapPoint(1.0, 0.0),
                ),
                DynamicMapWall(
                    id = "wall-c",
                    label = "North Wall",
                    start = DynamicMapPoint(1.5, 0.0),
                    end = DynamicMapPoint(3.0, 0.0),
                ),
                DynamicMapWall(
                    id = "wall-vertical",
                    label = "Vertical",
                    start = DynamicMapPoint(3.0, 0.0),
                    end = DynamicMapPoint(3.0, 2.0),
                ),
                DynamicMapWall(
                    id = "wall-separated",
                    label = "Separated",
                    start = DynamicMapPoint(5.0, 0.0),
                    end = DynamicMapPoint(6.0, 0.0),
                ),
                DynamicMapWall(
                    id = "wall-empty",
                    label = "Empty",
                    start = DynamicMapPoint(8.0, 8.0),
                    end = DynamicMapPoint(8.0, 8.0),
                ),
            ),
            lights = listOf(
                DynamicMapLight(
                    id = "light-1",
                    label = "Torch",
                    position = DynamicMapPoint(4.0, 4.0),
                    brightRadius = 4.0,
                    dimRadius = 8.0,
                    colorHex = "#ffb347",
                ),
            ),
            groups = listOf(
                DynamicMapElementGroup(
                    id = "group-1",
                    label = "Group 1",
                    elements = setOf(
                        DynamicMapElementSelection(DynamicMapElementKind.WALL, "wall-a"),
                        DynamicMapElementSelection(DynamicMapElementKind.WALL, "wall-b"),
                        DynamicMapElementSelection(DynamicMapElementKind.WALL, "wall-empty"),
                        DynamicMapElementSelection(DynamicMapElementKind.WALL, "wall-vertical"),
                        DynamicMapElementSelection(DynamicMapElementKind.LIGHT, "light-1"),
                    ),
                ),
            ),
        )

        val optimized = document.optimizeWallTopology()

        assertEquals(3, optimized.walls.size)
        assertEquals(
            DynamicMapWall(
                id = "wall-a",
                label = "North Wall",
                start = DynamicMapPoint(0.0, 0.0),
                end = DynamicMapPoint(3.0, 0.0),
            ),
            optimized.walls[0],
        )
        assertEquals("wall-vertical", optimized.walls[1].id)
        assertEquals("wall-separated", optimized.walls[2].id)
        assertEquals(
            setOf(
                DynamicMapElementSelection(DynamicMapElementKind.WALL, "wall-a"),
                DynamicMapElementSelection(DynamicMapElementKind.WALL, "wall-vertical"),
                DynamicMapElementSelection(DynamicMapElementKind.LIGHT, "light-1"),
            ),
            optimized.groups.single().elements,
        )
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
