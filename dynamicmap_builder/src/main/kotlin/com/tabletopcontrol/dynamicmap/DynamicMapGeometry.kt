package com.tabletopcontrol.dynamicmap

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

private const val MAX_FINE_NUDGE_TILES = 0.05
private const val WALL_TOPOLOGY_EPSILON = 0.0000001
private const val WALL_GAP_CLOSE_MAX_TILES = 0.1
private const val WALL_GAP_EXTENSION_MIN_ALIGNMENT = 0.7071067811865476
private const val POLYGON_EPSILON = 0.0000001

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
    kind: DynamicMapWallKind = DynamicMapWallKind.SOFT,
    doorVisible: Boolean = true,
    frontBehavior: DynamicMapWallSideBehavior = DynamicMapWallSideBehavior.OPEN,
    backBehavior: DynamicMapWallSideBehavior = DynamicMapWallSideBehavior.HARD,
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
    val label = kind.defaultLabel

    return listOf(
        DynamicMapWall(
            label = label,
            start = topLeft,
            end = topRight,
            kind = kind,
            doorVisible = doorVisible,
            frontBehavior = frontBehavior,
            backBehavior = backBehavior,
        ),
        DynamicMapWall(
            label = label,
            start = topRight,
            end = bottomRight,
            kind = kind,
            doorVisible = doorVisible,
            frontBehavior = frontBehavior,
            backBehavior = backBehavior,
        ),
        DynamicMapWall(
            label = label,
            start = bottomRight,
            end = bottomLeft,
            kind = kind,
            doorVisible = doorVisible,
            frontBehavior = frontBehavior,
            backBehavior = backBehavior,
        ),
        DynamicMapWall(
            label = label,
            start = bottomLeft,
            end = topLeft,
            kind = kind,
            doorVisible = doorVisible,
            frontBehavior = frontBehavior,
            backBehavior = backBehavior,
        ),
    )
}

fun distanceToWall(point: DynamicMapPoint, wall: DynamicMapWall): Double =
    distanceToSegment(point, wall.start, wall.end)

fun sanitizedSunlightPolygon(points: List<DynamicMapPoint>): List<DynamicMapPoint> {
    val cleaned = points.fold(mutableListOf<DynamicMapPoint>()) { acc, point ->
        if (acc.lastOrNull() != point) {
            acc += point
        }
        acc
    }
    if (cleaned.size > 1 && cleaned.first() == cleaned.last()) {
        cleaned.removeAt(cleaned.lastIndex)
    }
    return cleaned
}

fun isValidSunlightPolygon(points: List<DynamicMapPoint>): Boolean {
    val cleaned = sanitizedSunlightPolygon(points)
    return cleaned.size >= 3 && polygonArea(cleaned) > POLYGON_EPSILON
}

fun distanceToSunlightArea(point: DynamicMapPoint, area: DynamicMapSunlightArea): Double {
    val points = sanitizedSunlightPolygon(area.points)
    if (points.isEmpty()) return Double.POSITIVE_INFINITY
    if (isPointInSunlightPolygon(point, points)) return 0.0

    return points.indices.minOf { index ->
        val start = points[index]
        val end = points[(index + 1) % points.size]
        distanceToSegment(point, start, end)
    }
}

fun isPointInSunlightPolygon(
    point: DynamicMapPoint,
    polygon: List<DynamicMapPoint>,
): Boolean {
    val points = sanitizedSunlightPolygon(polygon)
    if (points.size < 3) return false
    if (points.indices.any { index ->
            val start = points[index]
            val end = points[(index + 1) % points.size]
            distanceToSegment(point, start, end) <= POLYGON_EPSILON
        }
    ) {
        return true
    }

    var inside = false
    var previousIndex = points.lastIndex
    for (index in points.indices) {
        val current = points[index]
        val previous = points[previousIndex]
        val crossesHorizontalRay = (current.y > point.y) != (previous.y > point.y)
        if (crossesHorizontalRay) {
            val intersectionX =
                (previous.x - current.x) * (point.y - current.y) / (previous.y - current.y) + current.x
            if (point.x < intersectionX) {
                inside = !inside
            }
        }
        previousIndex = index
    }
    return inside
}

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
    val selectedSunlightAreaIds = selections
        .filter { it.kind == DynamicMapElementKind.SUNLIGHT_AREA }
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
        sunlightAreas = sunlightAreas.map { area ->
            if (area.id in selectedSunlightAreaIds) {
                area.copy(points = area.points.map { point -> point + delta })
            } else {
                area
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

fun DynamicMapDocument.optimizeWallTopology(): DynamicMapDocument {
    val optimizedCandidates = optimizeWallCandidates(walls)
    val optimizedWalls = optimizedCandidates.map { it.toWall() }
    val wallIdMap = optimizedCandidates
        .flatMap { candidate -> candidate.sourceIds.map { sourceId -> sourceId to candidate.id } }
        .toMap()

    val optimizedGroups = groups.mapNotNull { group ->
        val optimizedElements = group.elements.mapNotNullTo(linkedSetOf<DynamicMapElementSelection>()) { selection ->
            when (selection.kind) {
                DynamicMapElementKind.WALL -> wallIdMap[selection.elementId]?.let { wallId ->
                    DynamicMapElementSelection(DynamicMapElementKind.WALL, wallId)
                }
                DynamicMapElementKind.LIGHT -> selection
                DynamicMapElementKind.SUNLIGHT_AREA -> selection
            }
        }
        if (optimizedElements.isEmpty()) {
            null
        } else {
            group.copy(elements = optimizedElements)
        }
    }

    return copy(
        walls = optimizedWalls,
        groups = optimizedGroups,
    ).pruneInvalidGroups()
}

fun DynamicMapDocument.boundsForSelections(selections: Set<DynamicMapElementSelection>): DynamicMapBounds? {
    val selectedWallIds = selections
        .filter { it.kind == DynamicMapElementKind.WALL }
        .mapTo(mutableSetOf()) { it.elementId }
    val selectedLightIds = selections
        .filter { it.kind == DynamicMapElementKind.LIGHT }
        .mapTo(mutableSetOf()) { it.elementId }
    val selectedSunlightAreaIds = selections
        .filter { it.kind == DynamicMapElementKind.SUNLIGHT_AREA }
        .mapTo(mutableSetOf()) { it.elementId }

    val points = buildList {
        walls.filter { it.id in selectedWallIds }.forEach { wall ->
            add(wall.start)
            add(wall.end)
        }
        lights.filter { it.id in selectedLightIds }.forEach { light ->
            add(light.position)
        }
        sunlightAreas.filter { it.id in selectedSunlightAreaIds }.forEach { area ->
            addAll(area.points)
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

private fun polygonArea(points: List<DynamicMapPoint>): Double {
    if (points.size < 3) return 0.0
    val signedArea = points.indices.sumOf { index ->
        val current = points[index]
        val next = points[(index + 1) % points.size]
        current.x * next.y - next.x * current.y
    } / 2.0
    return abs(signedArea)
}

private data class DynamicMapWallTopologyCandidate(
    val id: String,
    val label: String,
    val start: DynamicMapPoint,
    val end: DynamicMapPoint,
    val kind: DynamicMapWallKind,
    val doorVisible: Boolean,
    val frontBehavior: DynamicMapWallSideBehavior,
    val backBehavior: DynamicMapWallSideBehavior,
    val sourceIds: Set<String>,
    val sourceLabels: Set<String>,
) {
    fun toWall(): DynamicMapWall =
        DynamicMapWall(
            id = id,
            label = label,
            start = start,
            end = end,
            kind = kind,
            doorVisible = doorVisible,
            frontBehavior = frontBehavior,
            backBehavior = backBehavior,
        )
}

private fun optimizeWallCandidates(walls: List<DynamicMapWall>): List<DynamicMapWallTopologyCandidate> {
    val candidates = walls
        .mapNotNull { wall ->
            if (wall.length() <= WALL_TOPOLOGY_EPSILON) {
                null
            } else {
                DynamicMapWallTopologyCandidate(
                    id = wall.id,
                    label = wall.label,
                    start = wall.start,
                    end = wall.end,
                    kind = wall.kind,
                    doorVisible = wall.doorVisible,
                    frontBehavior = wall.frontBehavior,
                    backBehavior = wall.backBehavior,
                    sourceIds = linkedSetOf(wall.id),
                    sourceLabels = linkedSetOf(wall.label),
                )
            }
        }
        .let(::closeSmallWallGaps)
        .toMutableList()

    var changed: Boolean
    do {
        changed = false
        mergeLoop@ for (index in candidates.indices) {
            for (otherIndex in index + 1 until candidates.size) {
                val merged = mergeWallCandidatesOrNull(candidates[index], candidates[otherIndex]) ?: continue
                candidates[index] = merged
                candidates.removeAt(otherIndex)
                changed = true
                break@mergeLoop
            }
        }
    } while (changed)

    return candidates
}

private data class WallGapConnector(
    val start: DynamicMapPoint,
    val end: DynamicMapPoint,
    val kind: DynamicMapWallKind,
)

private data class WallEndpoint(
    val point: DynamicMapPoint,
    val role: WallEndpointRole,
)

private data class SegmentProjection(
    val point: DynamicMapPoint,
    val t: Double,
)

private enum class WallEndpointRole {
    START,
    END,
}

private fun closeSmallWallGaps(
    candidates: List<DynamicMapWallTopologyCandidate>,
): List<DynamicMapWallTopologyCandidate> {
    if (candidates.size < 2) return candidates

    val connectors = mutableListOf<WallGapConnector>()
    for (index in candidates.indices) {
        for (otherIndex in index + 1 until candidates.size) {
            val first = candidates[index]
            val second = candidates[otherIndex]
            addEndpointGapConnectors(connectors, first, second)
            addEndpointToSegmentGapConnectors(connectors, first, second)
            addEndpointToSegmentGapConnectors(connectors, second, first)
        }
    }

    if (connectors.isEmpty()) return candidates

    val usedIds = candidates.mapTo(mutableSetOf()) { it.id }
    val gapCandidates = connectors.mapIndexed { index, connector ->
        val id = uniqueGapClosureId(index, usedIds)
        usedIds += id
        DynamicMapWallTopologyCandidate(
            id = id,
            label = connector.kind.defaultLabel,
            start = connector.start,
            end = connector.end,
            kind = connector.kind,
            doorVisible = true,
            frontBehavior = DynamicMapWallSideBehavior.OPEN,
            backBehavior = DynamicMapWallSideBehavior.HARD,
            sourceIds = linkedSetOf(id),
            sourceLabels = linkedSetOf(),
        )
    }
    return candidates + gapCandidates
}

private fun addEndpointGapConnectors(
    connectors: MutableList<WallGapConnector>,
    first: DynamicMapWallTopologyCandidate,
    second: DynamicMapWallTopologyCandidate,
) {
    first.endpoints().forEach { firstEndpoint ->
        second.endpoints().forEach { secondEndpoint ->
            connectors.addUniqueGapConnector(
                WallGapConnector(
                    start = firstEndpoint.point,
                    end = secondEndpoint.point,
                    kind = gapClosureKind(first, second),
                ),
            )
        }
    }
}

private fun addEndpointToSegmentGapConnectors(
    connectors: MutableList<WallGapConnector>,
    endpointOwner: DynamicMapWallTopologyCandidate,
    target: DynamicMapWallTopologyCandidate,
) {
    endpointOwner.endpoints().forEach { endpoint ->
        val projection = projectPointOntoSegment(endpoint.point, target.start, target.end) ?: return@forEach
        if (projection.t <= WALL_TOPOLOGY_EPSILON || projection.t >= 1.0 - WALL_TOPOLOGY_EPSILON) {
            return@forEach
        }
        if (!isGapExtension(endpointOwner, endpoint, projection.point)) return@forEach

        connectors.addUniqueGapConnector(
            WallGapConnector(
                start = endpoint.point,
                end = projection.point,
                kind = gapClosureKind(endpointOwner, target),
            ),
        )
    }
}

private fun MutableList<WallGapConnector>.addUniqueGapConnector(connector: WallGapConnector) {
    val distance = connector.start.distanceTo(connector.end)
    if (distance <= WALL_TOPOLOGY_EPSILON || distance >= WALL_GAP_CLOSE_MAX_TILES) return
    if (any { it.sameUndirectedSegment(connector) }) return
    add(connector)
}

private fun gapClosureKind(
    first: DynamicMapWallTopologyCandidate,
    second: DynamicMapWallTopologyCandidate,
): DynamicMapWallKind =
    if (first.kind == DynamicMapWallKind.SOFT && second.kind == DynamicMapWallKind.SOFT) {
        DynamicMapWallKind.SOFT
    } else {
        DynamicMapWallKind.HARD
    }

private fun uniqueGapClosureId(
    index: Int,
    usedIds: Set<String>,
): String {
    val baseId = "wall-gap-${index + 1}"
    if (baseId !in usedIds) return baseId

    var suffix = 2
    while ("$baseId-$suffix" in usedIds) {
        suffix += 1
    }
    return "$baseId-$suffix"
}

private fun mergeWallCandidatesOrNull(
    first: DynamicMapWallTopologyCandidate,
    second: DynamicMapWallTopologyCandidate,
): DynamicMapWallTopologyCandidate? {
    if (!canMergeWallCandidates(first, second)) return null

    val direction = first.directionUnit()
    val endpoints = listOf(first.start, first.end, second.start, second.end)
    val minPoint = endpoints.minBy { it.projectOnto(direction) }
    val maxPoint = endpoints.maxBy { it.projectOnto(direction) }
    val (start, end) = orderedWallEndpoints(minPoint, maxPoint)
    val labels = (first.sourceLabels + second.sourceLabels).toCollection(linkedSetOf())

    return DynamicMapWallTopologyCandidate(
        id = first.id,
        label = mergedWallLabel(labels, first.kind),
        start = start,
        end = end,
        kind = first.kind,
        doorVisible = first.doorVisible,
        frontBehavior = first.frontBehavior,
        backBehavior = first.backBehavior,
        sourceIds = (first.sourceIds + second.sourceIds).toCollection(linkedSetOf()),
        sourceLabels = labels,
    )
}

private fun mergedWallLabel(
    labels: Set<String>,
    kind: DynamicMapWallKind,
): String =
    when (labels.size) {
        0 -> kind.defaultLabel
        1 -> labels.first()
        else -> "Merged Wall"
    }

private fun canMergeWallCandidates(
    first: DynamicMapWallTopologyCandidate,
    second: DynamicMapWallTopologyCandidate,
): Boolean {
    val firstVector = first.vector()
    val secondVector = second.vector()
    val firstLength = first.length()
    val secondLength = second.length()
    if (firstLength <= WALL_TOPOLOGY_EPSILON || secondLength <= WALL_TOPOLOGY_EPSILON) return false
    if (first.kind != second.kind) return false
    if (first.kind == DynamicMapWallKind.DOOR || first.kind == DynamicMapWallKind.FEATURE) return false

    val directionCross = cross(firstVector, secondVector)
    if (abs(directionCross) > WALL_TOPOLOGY_EPSILON * firstLength * secondLength) return false

    val startOffset = DynamicMapPoint(
        x = second.start.x - first.start.x,
        y = second.start.y - first.start.y,
    )
    val endOffset = DynamicMapPoint(
        x = second.end.x - first.start.x,
        y = second.end.y - first.start.y,
    )
    if (abs(cross(firstVector, startOffset)) > WALL_TOPOLOGY_EPSILON * firstLength) return false
    if (abs(cross(firstVector, endOffset)) > WALL_TOPOLOGY_EPSILON * firstLength) return false

    val direction = first.directionUnit()
    val firstRange = projectionRange(first.start, first.end, direction)
    val secondRange = projectionRange(second.start, second.end, direction)
    return max(firstRange.first, secondRange.first) <=
        min(firstRange.second, secondRange.second) + WALL_TOPOLOGY_EPSILON
}

private fun DynamicMapWallTopologyCandidate.vector(): DynamicMapPoint =
    DynamicMapPoint(
        x = end.x - start.x,
        y = end.y - start.y,
    )

private fun DynamicMapWallTopologyCandidate.length(): Double = hypot(end.x - start.x, end.y - start.y)

private fun DynamicMapWall.length(): Double = hypot(end.x - start.x, end.y - start.y)

private fun DynamicMapWallTopologyCandidate.endpoints(): List<WallEndpoint> =
    listOf(
        WallEndpoint(start, WallEndpointRole.START),
        WallEndpoint(end, WallEndpointRole.END),
    )

private fun DynamicMapWallTopologyCandidate.outwardVector(endpoint: WallEndpoint): DynamicMapPoint =
    when (endpoint.role) {
        WallEndpointRole.START -> start - end
        WallEndpointRole.END -> end - start
    }

private fun DynamicMapWallTopologyCandidate.directionUnit(): DynamicMapPoint {
    val length = length()
    return DynamicMapPoint(
        x = (end.x - start.x) / length,
        y = (end.y - start.y) / length,
    )
}

private fun projectPointOntoSegment(
    point: DynamicMapPoint,
    start: DynamicMapPoint,
    end: DynamicMapPoint,
): SegmentProjection? {
    val segment = end - start
    val lengthSquared = segment.x * segment.x + segment.y * segment.y
    if (lengthSquared <= WALL_TOPOLOGY_EPSILON * WALL_TOPOLOGY_EPSILON) return null

    val rawT = dot(point - start, segment) / lengthSquared
    val t = rawT.coerceIn(0.0, 1.0)
    return SegmentProjection(
        point = DynamicMapPoint(
            x = start.x + segment.x * t,
            y = start.y + segment.y * t,
        ),
        t = t,
    )
}

private fun isGapExtension(
    endpointOwner: DynamicMapWallTopologyCandidate,
    endpoint: WallEndpoint,
    targetPoint: DynamicMapPoint,
): Boolean {
    val outward = endpointOwner.outwardVector(endpoint)
    val connector = targetPoint - endpoint.point
    val outwardLength = outward.length()
    val connectorLength = connector.length()
    if (outwardLength <= WALL_TOPOLOGY_EPSILON || connectorLength <= WALL_TOPOLOGY_EPSILON) return false

    val alignment = dot(outward, connector) / (outwardLength * connectorLength)
    return alignment >= WALL_GAP_EXTENSION_MIN_ALIGNMENT
}

private fun projectionRange(
    start: DynamicMapPoint,
    end: DynamicMapPoint,
    direction: DynamicMapPoint,
): Pair<Double, Double> {
    val first = start.projectOnto(direction)
    val second = end.projectOnto(direction)
    return min(first, second) to max(first, second)
}

private fun DynamicMapPoint.projectOnto(direction: DynamicMapPoint): Double =
    x * direction.x + y * direction.y

private operator fun DynamicMapPoint.minus(other: DynamicMapPoint): DynamicMapPoint =
    DynamicMapPoint(
        x = x - other.x,
        y = y - other.y,
    )

private fun DynamicMapPoint.length(): Double = hypot(x, y)

private fun DynamicMapPoint.distanceTo(other: DynamicMapPoint): Double = (this - other).length()

private fun WallGapConnector.sameUndirectedSegment(other: WallGapConnector): Boolean =
    (start.samePoint(other.start) && end.samePoint(other.end)) ||
        (start.samePoint(other.end) && end.samePoint(other.start))

private fun DynamicMapPoint.samePoint(other: DynamicMapPoint): Boolean =
    distanceTo(other) <= WALL_TOPOLOGY_EPSILON

private fun dot(first: DynamicMapPoint, second: DynamicMapPoint): Double =
    first.x * second.x + first.y * second.y

private fun cross(first: DynamicMapPoint, second: DynamicMapPoint): Double =
    first.x * second.y - first.y * second.x

private fun orderedWallEndpoints(
    first: DynamicMapPoint,
    second: DynamicMapPoint,
): Pair<DynamicMapPoint, DynamicMapPoint> =
    if (compareWallEndpoints(first, second) <= 0) {
        first to second
    } else {
        second to first
    }

private fun compareWallEndpoints(
    first: DynamicMapPoint,
    second: DynamicMapPoint,
): Int =
    when {
        abs(first.x - second.x) > WALL_TOPOLOGY_EPSILON -> first.x.compareTo(second.x)
        abs(first.y - second.y) > WALL_TOPOLOGY_EPSILON -> first.y.compareTo(second.y)
        else -> 0
    }
