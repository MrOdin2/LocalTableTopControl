package com.tabletopcontrol.map.logic

import kotlin.math.abs

enum class GuidedCalibrationAxis {
    HORIZONTAL,
    VERTICAL,
}

/**
 * Computes the new [MapCalibration] for Step 1 of guided map calibration.
 *
 * Translates the map so that the chosen reference point ([clickX], [clickY]) in
 * unviewported canvas space moves to ([targetX], [targetY]), usually the centre
 * point used as the calibration pivot.
 */
internal fun guidedCalibrationStep1(
    current: MapCalibration,
    clickX: Double,
    clickY: Double,
    targetX: Double,
    targetY: Double,
): MapCalibration = MapCalibration(
    scale = current.scale,
    offsetX = current.offsetX + (targetX - clickX),
    offsetY = current.offsetY + (targetY - clickY),
)

/**
 * Computes the new [MapCalibration] for Step 2 of guided map calibration.
 *
 * The second point is intentionally axis-aligned with the centre point chosen in
 * Step 1. The scale factor is therefore derived from the absolute distance on the
 * chosen [axis] only, multiplied by [targetTileSpan] for wide mode.
 */
internal fun guidedCalibrationStep2(
    currentCalibration: MapCalibration,
    cornerX: Double,
    cornerY: Double,
    targetX: Double,
    targetY: Double,
    cellSizeInPixels: Double,
    axis: GuidedCalibrationAxis = GuidedCalibrationAxis.HORIZONTAL,
    targetTileSpan: Int = 1,
): MapCalibration? {
    if (targetTileSpan <= 0) return null

    val distance = when (axis) {
        GuidedCalibrationAxis.HORIZONTAL -> abs(cornerX - targetX)
        GuidedCalibrationAxis.VERTICAL -> abs(cornerY - targetY)
    }
    if (distance < 1.0) return null

    val scaleFactor = (cellSizeInPixels * targetTileSpan) / distance
    return MapCalibration(
        scale = currentCalibration.scale * scaleFactor,
        offsetX = currentCalibration.offsetX * scaleFactor,
        offsetY = currentCalibration.offsetY * scaleFactor,
    )
}
