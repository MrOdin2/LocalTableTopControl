package com.tabletopcontrol.dynamicmap.runtime

import javafx.scene.paint.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Supported map measurement shapes. */
enum class MeasurementType { LINE, CONE, RECTANGLE, CIRCLE }

/**
 * A single DM-created measurement overlay anchored to snapped grid cells.
 *
 * @property mirroredToTable whether this measurement is also visible on the table screen.
 * @property unitLabel optional user-visible label prefix (e.g. "Fireball").
 */
data class MeasurementOverlay(
    val id: String,
    val type: MeasurementType,
    val startCol: Int,
    val startRow: Int,
    val endCol: Int,
    val endRow: Int,
    val coneAngleDegrees: Double = DEFAULT_MEASUREMENT_CONE_ANGLE_DEGREES,
    val mirroredToTable: Boolean = false,
    val unitLabel: String = "",
    val unitsSuffix: String = "ft",
    val color: Color = DEFAULT_MEASUREMENT_COLOR,
) {
    init {
        require(coneAngleDegrees in 1.0..360.0) { "coneAngleDegrees must be in [1, 360], was $coneAngleDegrees" }
    }

    /** Euclidean distance between start and end in grid cells. */
    fun distanceInCells(): Double = hypot((endCol - startCol).toDouble(), (endRow - startRow).toDouble())

    /** Rectangle width in grid cells, inclusive of both boundary cells. */
    fun rectangleWidthInCells(): Int = abs(endCol - startCol) + 1

    /** Rectangle height in grid cells, inclusive of both boundary cells. */
    fun rectangleHeightInCells(): Int = abs(endRow - startRow) + 1

    /**
     * Human-readable dimension text in map units.
     *
     * @param cellSizeInUnits map units per grid cell.
     */
    fun dimensionText(cellSizeInUnits: Double): String {
        val units = unitsSuffix.trim()
        val suffix = if (units.isBlank()) "" else " $units"
        return when (type) {
            MeasurementType.LINE -> "${formatMeasure(distanceInCells() * cellSizeInUnits)}$suffix"
            MeasurementType.CIRCLE -> "r ${formatMeasure(distanceInCells() * cellSizeInUnits)}$suffix"
            MeasurementType.CONE ->
                "${formatMeasure(coneAngleDegrees)}° / ${formatMeasure(distanceInCells() * cellSizeInUnits)}$suffix"
            MeasurementType.RECTANGLE -> {
                val w = rectangleWidthInCells() * cellSizeInUnits
                val h = rectangleHeightInCells() * cellSizeInUnits
                "${formatMeasure(w)} × ${formatMeasure(h)}$suffix"
            }
        }
    }

    /**
     * Returns `true` when [col],[row] is close enough to this shape for DM selection.
     *
     * Tolerance is expressed in grid-cell units.
     */
    fun isNearCell(col: Int, row: Int, toleranceCells: Double = 0.75): Boolean {
        val px = col.toDouble()
        val py = row.toDouble()
        val x1 = startCol.toDouble()
        val y1 = startRow.toDouble()
        val x2 = endCol.toDouble()
        val y2 = endRow.toDouble()
        return when (type) {
            MeasurementType.LINE -> distancePointToSegment(px, py, x1, y1, x2, y2) <= toleranceCells
            MeasurementType.RECTANGLE -> {
                val minX = minOf(x1, x2) - toleranceCells
                val maxX = maxOf(x1, x2) + toleranceCells
                val minY = minOf(y1, y2) - toleranceCells
                val maxY = maxOf(y1, y2) + toleranceCells
                px in minX..maxX && py in minY..maxY
            }
            MeasurementType.CIRCLE -> abs(hypot(px - x1, py - y1) - distanceInCells()) <= toleranceCells
            MeasurementType.CONE -> {
                val dx = px - x1
                val dy = py - y1
                val radius = distanceInCells()
                val distance = hypot(dx, dy)
                if (distance > radius + toleranceCells) return false
                if (distance <= toleranceCells) return true
                val direction = Math.toDegrees(atan2((endRow - startRow).toDouble(), (endCol - startCol).toDouble()))
                val pointAngle = Math.toDegrees(atan2(dy, dx))
                abs(normalizeAngle(pointAngle - direction)) <= coneAngleDegrees / 2.0 + CONE_SELECTION_TOLERANCE_DEGREES
            }
        }
    }

    companion object {
        internal const val DEFAULT_MEASUREMENT_CONE_ANGLE_DEGREES = 60.0
        private const val CONE_SELECTION_TOLERANCE_DEGREES = 6.0
        private val DEFAULT_MEASUREMENT_COLOR = Color.color(0.98, 0.86, 0.12, 0.95)
    }
}

private fun distancePointToSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
    val dx = x2 - x1
    val dy = y2 - y1
    if (dx == 0.0 && dy == 0.0) return hypot(px - x1, py - y1)
    val t = (((px - x1) * dx) + ((py - y1) * dy)) / (dx * dx + dy * dy)
    val clamped = t.coerceIn(0.0, 1.0)
    val projX = x1 + clamped * dx
    val projY = y1 + clamped * dy
    return hypot(px - projX, py - projY)
}

private fun normalizeAngle(degrees: Double): Double {
    var angle = degrees % 360.0
    if (angle > 180.0) angle -= 360.0
    if (angle < -180.0) angle += 360.0
    return angle
}

internal fun formatMeasure(value: Double): String =
    if (!value.isFinite()) {
        "0"
    } else {
        val rounded = kotlin.math.round(value * 10.0) / 10.0
        if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
