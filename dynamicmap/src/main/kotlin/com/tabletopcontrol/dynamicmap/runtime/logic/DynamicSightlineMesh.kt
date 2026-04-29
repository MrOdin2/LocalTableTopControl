package com.tabletopcontrol.dynamicmap.runtime.logic

import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRuntimePoint
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRuntimeWall
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import java.awt.geom.Rectangle2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal data class DynamicSightPoint(
    val x: Double,
    val y: Double,
)

internal data class DynamicSightTriangle(
    val origin: DynamicSightPoint,
    val first: DynamicSightPoint,
    val second: DynamicSightPoint,
) {
    fun contains(point: DynamicSightPoint): Boolean {
        val d1 = signedArea(point, origin, first)
        val d2 = signedArea(point, first, second)
        val d3 = signedArea(point, second, origin)
        val hasNegative = d1 < -EPSILON || d2 < -EPSILON || d3 < -EPSILON
        val hasPositive = d1 > EPSILON || d2 > EPSILON || d3 > EPSILON
        return !(hasNegative && hasPositive)
    }

    fun intersectsCircle(center: DynamicSightPoint, radius: Double): Boolean =
        contains(center) ||
            distance(origin, center) <= radius + EPSILON ||
            distance(first, center) <= radius + EPSILON ||
            distance(second, center) <= radius + EPSILON ||
            distanceFromPointToSegment(center, origin, first) <= radius + EPSILON ||
            distanceFromPointToSegment(center, first, second) <= radius + EPSILON ||
            distanceFromPointToSegment(center, second, origin) <= radius + EPSILON

    private fun signedArea(a: DynamicSightPoint, b: DynamicSightPoint, c: DynamicSightPoint): Double =
        (a.x - c.x) * (b.y - c.y) - (b.x - c.x) * (a.y - c.y)
}

internal class DynamicSightlineContribution(
    val triangles: List<DynamicSightTriangle>,
    private val visibleArea: Area,
) {
    fun copyVisibleArea(): Area = Area(visibleArea)

    companion object {
        fun empty(): DynamicSightlineContribution =
            DynamicSightlineContribution(
                triangles = emptyList(),
                visibleArea = Area(),
            )
    }
}

internal class DynamicSightlineMesh(
    val cols: Int,
    val rows: Int,
    val triangles: List<DynamicSightTriangle>,
    private val visibleArea: Area,
    private val hiddenArea: Area,
) {
    init {
        require(cols > 0) { "cols must be positive, was $cols" }
        require(rows > 0) { "rows must be positive, was $rows" }
    }

    fun containsPoint(x: Double, y: Double): Boolean =
        x in 0.0..cols.toDouble() &&
            y in 0.0..rows.toDouble() &&
            triangles.any { it.contains(DynamicSightPoint(x, y)) }

    fun intersectsToken(token: Token): Boolean {
        val bounds = tokenDrawBounds(token, originX = 0.0, originY = 0.0, cellPx = 1.0)
        val center = DynamicSightPoint(bounds.centerX, bounds.centerY)
        val radius = bounds.size / 2.0
        return triangles.any { it.intersectsCircle(center, radius) }
    }

    fun copyVisibleArea(): Area = Area(visibleArea)

    fun drawHiddenArea(
        moveTo: (DynamicSightPoint) -> Unit,
        lineTo: (DynamicSightPoint) -> Unit,
        closePath: () -> Unit,
    ) {
        val pathIterator = hiddenArea.getPathIterator(null, PATH_FLATNESS)
        val coords = DoubleArray(6)
        while (!pathIterator.isDone) {
            when (pathIterator.currentSegment(coords)) {
                PathIterator.SEG_MOVETO -> moveTo(DynamicSightPoint(coords[0], coords[1]))
                PathIterator.SEG_LINETO -> lineTo(DynamicSightPoint(coords[0], coords[1]))
                PathIterator.SEG_CLOSE -> closePath()
            }
            pathIterator.next()
        }
    }

    companion object {
        fun hidden(cols: Int, rows: Int): DynamicSightlineMesh =
            DynamicSightlineMesh(
                cols = cols,
                rows = rows,
                triangles = emptyList(),
                visibleArea = Area(),
                hiddenArea = Area(Rectangle2D.Double(0.0, 0.0, cols.toDouble(), rows.toDouble())),
            )

        fun fromVisibleArea(
            cols: Int,
            rows: Int,
            visibleArea: Area,
            triangles: List<DynamicSightTriangle> = emptyList(),
        ): DynamicSightlineMesh {
            val visibleAreaCopy = Area(visibleArea)
            val hiddenArea = Area(Rectangle2D.Double(0.0, 0.0, cols.toDouble(), rows.toDouble())).apply {
                subtract(visibleAreaCopy)
            }
            return DynamicSightlineMesh(
                cols = cols,
                rows = rows,
                triangles = triangles,
                visibleArea = visibleAreaCopy,
                hiddenArea = hiddenArea,
            )
        }

        fun compute(
            cols: Int,
            rows: Int,
            walls: List<DynamicMapRuntimeWall>,
            tokens: Iterable<Token>,
        ): DynamicSightlineMesh =
            DynamicSightlineGeometry.forMap(cols, rows, walls).compute(tokens)
    }
}

internal class DynamicSightlineGeometry private constructor(
    val cols: Int,
    val rows: Int,
    private val segments: List<SightSegment>,
) {
    init {
        require(cols > 0) { "cols must be positive, was $cols" }
        require(rows > 0) { "rows must be positive, was $rows" }
    }

    fun compute(tokens: Iterable<Token>): DynamicSightlineMesh {
        val contributions = tokens.mapNotNull(::computeContribution)
        return combine(contributions)
    }

    fun computeContribution(token: Token): DynamicSightlineContribution? {
        if (!token.isPlayerCharacter) return null
        val origin = token.sightOrigin()
        if (origin.x !in 0.0..cols.toDouble() || origin.y !in 0.0..rows.toDouble()) {
            return DynamicSightlineContribution.empty()
        }
        val triangles = triangulateVisibleArea(origin, segments)
        return DynamicSightlineContribution(
            triangles = triangles,
            visibleArea = visibleAreaFor(triangles),
        )
    }

    fun combine(contributions: Iterable<DynamicSightlineContribution>): DynamicSightlineMesh {
        val contributionList = contributions.toList()
        val triangles = contributionList.flatMap { it.triangles }
        val visibleArea = Area().apply {
            contributionList.forEach { contribution -> add(contribution.copyVisibleArea()) }
        }
        return DynamicSightlineMesh.fromVisibleArea(
            cols = cols,
            rows = rows,
            visibleArea = visibleArea,
            triangles = triangles,
        )
    }

    fun meshFromVisibleArea(visibleArea: Area): DynamicSightlineMesh =
        DynamicSightlineMesh.fromVisibleArea(
            cols = cols,
            rows = rows,
            visibleArea = visibleArea,
        )

    companion object {
        fun forMap(
            cols: Int,
            rows: Int,
            walls: List<DynamicMapRuntimeWall>,
        ): DynamicSightlineGeometry =
            DynamicSightlineGeometry(
                cols = cols,
                rows = rows,
                segments = buildSightSegments(cols, rows, walls),
            )
    }
}

private data class SightSegment(
    val start: DynamicSightPoint,
    val end: DynamicSightPoint,
) {
    val isZeroLength: Boolean
        get() = distance(start, end) < EPSILON
}

private data class RayHit(
    val angle: Double,
    val point: DynamicSightPoint,
    val distance: Double,
)

private fun Token.sightOrigin(): DynamicSightPoint {
    val span = size.gridSpanCells.toDouble()
    return DynamicSightPoint(
        x = col + span / 2.0,
        y = row + span / 2.0,
    )
}

private fun buildSightSegments(
    cols: Int,
    rows: Int,
    walls: List<DynamicMapRuntimeWall>,
): List<SightSegment> {
    val leftTop = DynamicSightPoint(0.0, 0.0)
    val rightTop = DynamicSightPoint(cols.toDouble(), 0.0)
    val rightBottom = DynamicSightPoint(cols.toDouble(), rows.toDouble())
    val leftBottom = DynamicSightPoint(0.0, rows.toDouble())

    return buildList {
        add(SightSegment(leftTop, rightTop))
        add(SightSegment(rightTop, rightBottom))
        add(SightSegment(rightBottom, leftBottom))
        add(SightSegment(leftBottom, leftTop))
        walls.forEach { wall ->
            val segment = SightSegment(wall.start.toSightPoint(), wall.end.toSightPoint())
            if (!segment.isZeroLength) {
                add(segment)
            }
        }
    }
}

private fun DynamicMapRuntimePoint.toSightPoint(): DynamicSightPoint = DynamicSightPoint(x, y)

private fun visibleAreaFor(triangles: List<DynamicSightTriangle>): Area =
    Area().apply {
        triangles.forEach { triangle -> add(triangle.toArea()) }
    }

private fun DynamicSightTriangle.toArea(): Area {
    val path = Path2D.Double()
    path.moveTo(origin.x, origin.y)
    path.lineTo(first.x, first.y)
    path.lineTo(second.x, second.y)
    path.closePath()
    return Area(path)
}

private fun triangulateVisibleArea(
    origin: DynamicSightPoint,
    segments: List<SightSegment>,
): List<DynamicSightTriangle> {
    val rayAngles = segments
        .flatMap { segment -> listOf(segment.start, segment.end) }
        .flatMap { point ->
            val angle = atan2(point.y - origin.y, point.x - origin.x)
            listOf(angle - RAY_ANGLE_EPSILON, angle, angle + RAY_ANGLE_EPSILON)
        }
    val hits = rayAngles
        .mapNotNull { angle -> castRay(origin, normalizeAngle(angle), segments) }
        .sortedBy { it.angle }
        .dedupeAdjacentHits()

    if (hits.size < 2) return emptyList()

    return buildList {
        for (index in hits.indices) {
            val current = hits[index].point
            val next = hits[(index + 1) % hits.size].point
            if (distance(current, next) > EPSILON && triangleArea(origin, current, next) > EPSILON) {
                add(DynamicSightTriangle(origin, current, next))
            }
        }
    }
}

private fun castRay(
    origin: DynamicSightPoint,
    angle: Double,
    segments: List<SightSegment>,
): RayHit? {
    val rayX = cos(angle)
    val rayY = sin(angle)
    return segments
        .mapNotNull { segment -> intersectRayWithSegment(origin, rayX, rayY, segment) }
        .minByOrNull { it.distance }
        ?.copy(angle = angle)
}

private fun intersectRayWithSegment(
    origin: DynamicSightPoint,
    rayX: Double,
    rayY: Double,
    segment: SightSegment,
): RayHit? {
    val segmentX = segment.end.x - segment.start.x
    val segmentY = segment.end.y - segment.start.y
    val startX = segment.start.x - origin.x
    val startY = segment.start.y - origin.y
    val denom = cross(rayX, rayY, segmentX, segmentY)
    if (abs(denom) < EPSILON) return null

    val rayDistance = cross(startX, startY, segmentX, segmentY) / denom
    val segmentDistance = cross(startX, startY, rayX, rayY) / denom
    if (rayDistance < -EPSILON || segmentDistance < -EPSILON || segmentDistance > 1.0 + EPSILON) {
        return null
    }

    val point = DynamicSightPoint(
        x = origin.x + rayX * rayDistance,
        y = origin.y + rayY * rayDistance,
    )
    return RayHit(angle = 0.0, point = point, distance = rayDistance.coerceAtLeast(0.0))
}

private fun List<RayHit>.dedupeAdjacentHits(): List<RayHit> {
    val deduped = mutableListOf<RayHit>()
    for (hit in this) {
        val previous = deduped.lastOrNull()
        if (previous == null || distance(previous.point, hit.point) > HIT_DEDUPE_DISTANCE) {
            deduped += hit
        }
    }
    if (deduped.size > 1 && distance(deduped.first().point, deduped.last().point) <= HIT_DEDUPE_DISTANCE) {
        deduped[0] = deduped.first().copy(point = deduped.last().point)
        deduped.removeAt(deduped.lastIndex)
    }
    return deduped
}

private fun normalizeAngle(angle: Double): Double {
    var normalized = angle % FULL_CIRCLE_RADIANS
    if (normalized < 0.0) normalized += FULL_CIRCLE_RADIANS
    return normalized
}

private fun cross(ax: Double, ay: Double, bx: Double, by: Double): Double = ax * by - ay * bx

private fun distance(first: DynamicSightPoint, second: DynamicSightPoint): Double =
    hypot(first.x - second.x, first.y - second.y)

private fun distanceFromPointToSegment(
    point: DynamicSightPoint,
    start: DynamicSightPoint,
    end: DynamicSightPoint,
): Double {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared < EPSILON) return distance(point, start)

    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared)
        .coerceIn(0.0, 1.0)
    val projected = DynamicSightPoint(
        x = start.x + t * dx,
        y = start.y + t * dy,
    )
    return distance(point, projected)
}

private fun triangleArea(a: DynamicSightPoint, b: DynamicSightPoint, c: DynamicSightPoint): Double =
    abs(cross(b.x - a.x, b.y - a.y, c.x - a.x, c.y - a.y)) / 2.0

private const val FULL_CIRCLE_RADIANS = PI * 2.0
private const val RAY_ANGLE_EPSILON = 0.0001
private const val HIT_DEDUPE_DISTANCE = 1e-6
private const val PATH_FLATNESS = 0.001
private const val EPSILON = 1e-9
