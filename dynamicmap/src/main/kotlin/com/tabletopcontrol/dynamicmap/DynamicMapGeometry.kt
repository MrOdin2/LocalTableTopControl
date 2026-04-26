package com.tabletopcontrol.dynamicmap

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

private const val MAX_FINE_NUDGE_TILES = 0.05

data class DynamicMapBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
)

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

fun fineMovementStepInTiles(
    cellSize: Double,
    scale: Double,
): Double {
    val pixelsPerTile = cellSize * scale
    if (!pixelsPerTile.isFinite() || pixelsPerTile <= 0.0) return MAX_FINE_NUDGE_TILES
    return (1.0 / pixelsPerTile).coerceAtMost(MAX_FINE_NUDGE_TILES)
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

fun DynamicMapDocument.moveSelections(
    selections: Set<DynamicMapElementSelection>,
    delta: DynamicMapPoint,
): DynamicMapDocument {
    if (selections.isEmpty() || (delta.x == 0.0 && delta.y == 0.0)) return this

    val selectedWallIds = selections
        .filter { it.kind == DynamicMapElementKind.WALL }
        .mapTo(mutableSetOf()) { it.elementId }
    val selectedLightIds = selections
        .filter { it.kind == DynamicMapElementKind.LIGHT }
        .mapTo(mutableSetOf()) { it.elementId }

    return copy(
        walls = walls.map { wall ->
            if (wall.id in selectedWallIds) {
                wall.copy(
                    start = wall.start + delta,
                    end = wall.end + delta,
                )
            } else {
                wall
            }
        },
        lights = lights.map { light ->
            if (light.id in selectedLightIds) {
                light.copy(position = light.position + delta)
            } else {
                light
            }
        },
    )
}

fun DynamicMapDocument.clampMovementDelta(
    selections: Set<DynamicMapElementSelection>,
    requestedDelta: DynamicMapPoint,
): DynamicMapPoint {
    val bounds = boundsForSelections(selections) ?: return DynamicMapPoint(0.0, 0.0)
    return DynamicMapPoint(
        x = requestedDelta.x.coerceIn(-bounds.minX, cols.toDouble() - bounds.maxX),
        y = requestedDelta.y.coerceIn(-bounds.minY, rows.toDouble() - bounds.maxY),
    )
}

fun DynamicMapDocument.boundsForSelections(selections: Set<DynamicMapElementSelection>): DynamicMapBounds? {
    val selectedWallIds = selections
        .filter { it.kind == DynamicMapElementKind.WALL }
        .mapTo(mutableSetOf()) { it.elementId }
    val selectedLightIds = selections
        .filter { it.kind == DynamicMapElementKind.LIGHT }
        .mapTo(mutableSetOf()) { it.elementId }

    val points = buildList {
        walls.filter { it.id in selectedWallIds }.forEach { wall ->
            add(wall.start)
            add(wall.end)
        }
        lights.filter { it.id in selectedLightIds }.forEach { light ->
            add(light.position)
        }
    }
    if (points.isEmpty()) return null

    return DynamicMapBounds(
        minX = points.minOf { it.x },
        minY = points.minOf { it.y },
        maxX = points.maxOf { it.x },
        maxY = points.maxOf { it.y },
    )
}

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

private operator fun DynamicMapPoint.plus(delta: DynamicMapPoint): DynamicMapPoint =
    DynamicMapPoint(
        x = x + delta.x,
        y = y + delta.y,
    )
