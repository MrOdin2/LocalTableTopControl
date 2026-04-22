package com.tabletopcontrol.dynamicmap

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

fun clampPointToMap(
    point: DynamicMapPoint,
    cols: Int,
    rows: Int,
): DynamicMapPoint = DynamicMapPoint(
    x = point.x.coerceIn(0.0, cols.toDouble()),
    y = point.y.coerceIn(0.0, rows.toDouble()),
)

fun snapPoint(point: DynamicMapPoint, step: Double): DynamicMapPoint =
    DynamicMapPoint(
        x = snapCoordinate(point.x, step),
        y = snapCoordinate(point.y, step),
    )

fun snapCoordinate(value: Double, step: Double): Double {
    if (!value.isFinite() || step <= 0.0) return value
    return round(value / step) * step
}

fun buildRectangleWalls(
    start: DynamicMapPoint,
    end: DynamicMapPoint,
): List<DynamicMapWall> {
    val minX = min(start.x, end.x)
    val minY = min(start.y, end.y)
    val maxX = max(start.x, end.x)
    val maxY = max(start.y, end.y)
    if (maxX <= minX || maxY <= minY) return emptyList()

    val topLeft = DynamicMapPoint(minX, minY)
    val topRight = DynamicMapPoint(maxX, minY)
    val bottomLeft = DynamicMapPoint(minX, maxY)
    val bottomRight = DynamicMapPoint(maxX, maxY)

    return listOf(
        DynamicMapWall(start = topLeft, end = topRight),
        DynamicMapWall(start = topRight, end = bottomRight),
        DynamicMapWall(start = bottomRight, end = bottomLeft),
        DynamicMapWall(start = bottomLeft, end = topLeft),
    )
}

fun distanceToWall(point: DynamicMapPoint, wall: DynamicMapWall): Double =
    distanceToSegment(point, wall.start, wall.end)

fun distanceToSegment(
    point: DynamicMapPoint,
    start: DynamicMapPoint,
    end: DynamicMapPoint,
): Double {
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (dx == 0.0 && dy == 0.0) {
        return hypot(point.x - start.x, point.y - start.y)
    }

    val rawT = ((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy)
    val t = rawT.coerceIn(0.0, 1.0)
    val projectionX = start.x + dx * t
    val projectionY = start.y + dy * t
    return hypot(point.x - projectionX, point.y - projectionY)
}
