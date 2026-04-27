package com.tabletopcontrol.dynamicmap.runtime.logic

import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRuntimeWall
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class DynamicSightlineMask private constructor(
    val cols: Int,
    val rows: Int,
    private val visible: Array<BooleanArray>,
) {
    init {
        require(cols > 0) { "cols must be positive, was $cols" }
        require(rows > 0) { "rows must be positive, was $rows" }
        require(visible.size == cols) { "visible column count must match cols" }
        require(visible.all { it.size == rows }) { "visible row count must match rows" }
    }

    fun isVisible(col: Int, row: Int): Boolean =
        col in 0 until cols && row in 0 until rows && visible[col][row]

    fun visibleCount(): Int = visible.sumOf { col -> col.count { it } }

    companion object {
        fun compute(
            cols: Int,
            rows: Int,
            walls: List<DynamicMapRuntimeWall>,
            tokens: Iterable<Token>,
        ): DynamicSightlineMask {
            val visible = Array(cols) { BooleanArray(rows) }
            val blockingWalls = walls.filterNot { it.isZeroLength() }
            val pcOrigins = tokens
                .filter { it.isPlayerCharacter }
                .map { it.sightOrigin() }
                .filter { it.x in 0.0..cols.toDouble() && it.y in 0.0..rows.toDouble() }

            pcOrigins.forEach { origin ->
                for (col in 0 until cols) {
                    for (row in 0 until rows) {
                        if (visible[col][row]) continue
                        val target = SightPoint(col + 0.5, row + 0.5)
                        if (hasLineOfSight(origin, target, blockingWalls)) {
                            visible[col][row] = true
                        }
                    }
                }
            }

            return DynamicSightlineMask(cols = cols, rows = rows, visible = visible)
        }
    }
}

private data class SightPoint(val x: Double, val y: Double)

private fun Token.sightOrigin(): SightPoint {
    val span = size.gridSpanCells.toDouble()
    return SightPoint(
        x = col + span / 2.0,
        y = row + span / 2.0,
    )
}

private fun DynamicMapRuntimeWall.isZeroLength(): Boolean =
    abs(start.x - end.x) < EPSILON && abs(start.y - end.y) < EPSILON

private fun hasLineOfSight(
    origin: SightPoint,
    target: SightPoint,
    walls: List<DynamicMapRuntimeWall>,
): Boolean {
    if (!origin.x.isFinite() || !origin.y.isFinite() || !target.x.isFinite() || !target.y.isFinite()) {
        return false
    }
    if (abs(origin.x - target.x) < EPSILON && abs(origin.y - target.y) < EPSILON) {
        return true
    }
    return walls.none { wall -> wallBlocksSegment(origin, target, wall) }
}

private fun wallBlocksSegment(
    origin: SightPoint,
    target: SightPoint,
    wall: DynamicMapRuntimeWall,
): Boolean {
    val rayX = target.x - origin.x
    val rayY = target.y - origin.y
    val wallX = wall.end.x - wall.start.x
    val wallY = wall.end.y - wall.start.y
    val startX = wall.start.x - origin.x
    val startY = wall.start.y - origin.y
    val denom = cross(rayX, rayY, wallX, wallY)

    if (abs(denom) < EPSILON) {
        if (abs(cross(startX, startY, rayX, rayY)) >= EPSILON) {
            return false
        }
        val rayLengthSquared = rayX * rayX + rayY * rayY
        if (rayLengthSquared < EPSILON) return false
        val t0 = dot(wall.start.x - origin.x, wall.start.y - origin.y, rayX, rayY) / rayLengthSquared
        val t1 = dot(wall.end.x - origin.x, wall.end.y - origin.y, rayX, rayY) / rayLengthSquared
        val overlapStart = max(min(t0, t1), EPSILON)
        val overlapEnd = min(max(t0, t1), 1.0 - EPSILON)
        return overlapStart <= overlapEnd
    }

    val t = cross(startX, startY, wallX, wallY) / denom
    val u = cross(startX, startY, rayX, rayY) / denom
    return t > EPSILON && t < 1.0 - EPSILON && u >= -EPSILON && u <= 1.0 + EPSILON
}

private fun cross(ax: Double, ay: Double, bx: Double, by: Double): Double = ax * by - ay * bx

private fun dot(ax: Double, ay: Double, bx: Double, by: Double): Double = ax * bx + ay * by

private const val EPSILON = 1e-9
