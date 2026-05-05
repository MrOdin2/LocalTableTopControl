package com.tabletopcontrol.dynamicmap.runtime.logic

import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapBundle
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRuntimeBackgroundCalibration
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRuntimePoint
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRuntimeWall
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRuntimeWallKind
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicMovementReachabilityTest {

    @Test
    fun `reachable cells stop at closed walls`() {
        val bundle = bundle(
            walls = listOf(
                wall(
                    startX = 1.0,
                    startY = 0.0,
                    endX = 1.0,
                    endY = 3.0,
                ),
            ),
        )
        val token = token(col = 0, row = 1)

        val reachable = reachableMovementCells(bundle, token, remainingCells = 2, openDoorIds = emptySet())

        assertTrue(Pair(0, 1) in reachable)
        assertTrue(Pair(0, 0) in reachable)
        assertTrue(Pair(0, 2) in reachable)
        assertFalse(Pair(1, 1) in reachable)
        assertFalse(Pair(2, 1) in reachable)
    }

    @Test
    fun `open doors allow movement through their wall segment`() {
        val bundle = bundle(
            walls = listOf(
                wall(
                    id = "door-1",
                    kind = DynamicMapRuntimeWallKind.DOOR,
                    startX = 1.0,
                    startY = 0.0,
                    endX = 1.0,
                    endY = 3.0,
                ),
            ),
        )
        val token = token(col = 0, row = 1)

        val closedReachable = reachableMovementCells(bundle, token, remainingCells = 1, openDoorIds = emptySet())
        val openReachable = reachableMovementCells(bundle, token, remainingCells = 1, openDoorIds = setOf("door-1"))

        assertFalse(Pair(1, 1) in closedReachable)
        assertTrue(Pair(1, 1) in openReachable)
    }

    @Test
    fun `large tokens only include destinations that fit inside the map`() {
        val bundle = bundle(cols = 3, rows = 3)
        val token = token(col = 1, row = 1, size = TokenSize.LARGE)

        val reachable = reachableMovementCells(bundle, token, remainingCells = 1, openDoorIds = emptySet())

        assertEquals(setOf(Pair(1, 1), Pair(0, 1), Pair(1, 0)), reachable)
    }

    private fun token(
        col: Int,
        row: Int,
        size: TokenSize = TokenSize.MEDIUM,
    ): Token =
        Token(
            id = "pc-1",
            name = "Hero",
            col = col,
            row = row,
            size = size,
            color = Color.RED,
            isPlayerCharacter = true,
        )

    private fun bundle(
        cols: Int = 4,
        rows: Int = 4,
        walls: List<DynamicMapRuntimeWall> = emptyList(),
    ): DynamicMapBundle =
        DynamicMapBundle(
            sourcePath = "",
            displayPath = "",
            cols = cols,
            rows = rows,
            backgroundEntry = null,
            backgroundBytes = null,
            backgroundCalibration = DynamicMapRuntimeBackgroundCalibration(),
            walls = walls,
            lights = emptyList(),
            sunlightAreas = emptyList(),
        )

    private fun wall(
        id: String = "wall-1",
        kind: DynamicMapRuntimeWallKind = DynamicMapRuntimeWallKind.HARD,
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
    ): DynamicMapRuntimeWall =
        DynamicMapRuntimeWall(
            id = id,
            start = DynamicMapRuntimePoint(startX, startY),
            end = DynamicMapRuntimePoint(endX, endY),
            kind = kind,
        )
}
