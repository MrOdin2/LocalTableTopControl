package com.tabletopcontrol.dynamicmap.runtime.logic

import com.tabletopcontrol.dynamicmap.runtime.DynamicMapBundle
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRuntimeWall
import com.tabletopcontrol.dynamicmap.runtime.blocksSightAndBrightFrom
import com.tabletopcontrol.dynamicmap.runtime.isDoor
import java.util.ArrayDeque
import kotlin.math.abs

internal fun reachableMovementCells(
    bundle: DynamicMapBundle,
    token: Token,
    remainingCells: Int,
    openDoorIds: Set<String>,
): Set<Pair<Int, Int>> {
    if (remainingCells < 0 || !isReachableDestination(bundle, token.col, token.row, token)) {
        return emptySet()
    }

    val closedWalls = bundle.walls.filterNot { wall -> wall.isDoor() && wall.id in openDoorIds }
    val start = Pair(token.col, token.row)
    val distances = linkedMapOf(start to 0)
    val queue = ArrayDeque<Pair<Int, Int>>()
    queue.add(start)

    while (!queue.isEmpty()) {
        val current = queue.removeFirst()
        val distance = distances.getValue(current)
        if (distance >= remainingCells) continue

        DIRECTIONS.forEach { (colDelta, rowDelta) ->
            val next = Pair(current.first + colDelta, current.second + rowDelta)
            if (next in distances) return@forEach
            if (!isReachableDestination(bundle, next.first, next.second, token)) return@forEach
            if (movementBlocked(current, next, closedWalls)) return@forEach

            distances[next] = distance + 1
            queue.add(next)
        }
    }

    return distances.keys
}

private fun isReachableDestination(
    bundle: DynamicMapBundle,
    col: Int,
    row: Int,
    token: Token,
): Boolean {
    val footprint = tokenGridFootprint(col, row, token.size)
    return footprint.startCol >= 0 &&
        footprint.startRow >= 0 &&
        footprint.endCol < bundle.cols &&
        footprint.endRow < bundle.rows
}

private fun movementBlocked(
    from: Pair<Int, Int>,
    to: Pair<Int, Int>,
    walls: List<DynamicMapRuntimeWall>,
): Boolean {
    val fromX = from.first + 0.5
    val fromY = from.second + 0.5
    val toX = to.first + 0.5
    val toY = to.second + 0.5
    return walls.any { wall ->
        wall.blocksSightAndBrightFrom(fromX, fromY) &&
            segmentsIntersect(
                fromX,
                fromY,
                toX,
                toY,
                wall.start.x,
                wall.start.y,
                wall.end.x,
                wall.end.y,
            )
    }
}

private fun segmentsIntersect(
    ax: Double,
    ay: Double,
    bx: Double,
    by: Double,
    cx: Double,
    cy: Double,
    dx: Double,
    dy: Double,
): Boolean {
    val abC = orientation(ax, ay, bx, by, cx, cy)
    val abD = orientation(ax, ay, bx, by, dx, dy)
    val cdA = orientation(cx, cy, dx, dy, ax, ay)
    val cdB = orientation(cx, cy, dx, dy, bx, by)

    if (abs(abC) < EPSILON && onSegment(ax, ay, cx, cy, bx, by)) return true
    if (abs(abD) < EPSILON && onSegment(ax, ay, dx, dy, bx, by)) return true
    if (abs(cdA) < EPSILON && onSegment(cx, cy, ax, ay, dx, dy)) return true
    if (abs(cdB) < EPSILON && onSegment(cx, cy, bx, by, dx, dy)) return true

    return (abC > 0.0) != (abD > 0.0) && (cdA > 0.0) != (cdB > 0.0)
}

private fun orientation(
    ax: Double,
    ay: Double,
    bx: Double,
    by: Double,
    cx: Double,
    cy: Double,
): Double =
    (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)

private fun onSegment(
    ax: Double,
    ay: Double,
    px: Double,
    py: Double,
    bx: Double,
    by: Double,
): Boolean =
    px >= minOf(ax, bx) - EPSILON &&
        px <= maxOf(ax, bx) + EPSILON &&
        py >= minOf(ay, by) - EPSILON &&
        py <= maxOf(ay, by) + EPSILON

private val DIRECTIONS = listOf(
    Pair(0, -1),
    Pair(0, 1),
    Pair(-1, 0),
    Pair(1, 0),
)

private const val EPSILON = 1e-9
