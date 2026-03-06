package com.tabletopcontrol.map

import kotlin.math.hypot

/**
 * Computes the new [MapCalibration] for Step 1 of guided map calibration.
 *
 * Translates the map so that the user-clicked point ([clickX], [clickY]) in
 * canvas space is moved to the target position ([targetX], [targetY]) — typically
 * the canvas centre, which is where the red dot and yellow crosshair are drawn
 * during calibration.  The scale is preserved unchanged.
 *
 * @param current  the calibration active at the start of Step 1.
 * @param clickX   canvas-space X coordinate of the map's midpoint as clicked by the user.
 * @param clickY   canvas-space Y coordinate of the map's midpoint as clicked by the user.
 * @param targetX  canvas-space X coordinate the midpoint should move to (canvas centre).
 * @param targetY  canvas-space Y coordinate the midpoint should move to (canvas centre).
 * @return updated [MapCalibration] with adjusted offsets.
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
 * Scales the map so that the tile corner at ([cornerX], [cornerY]) in canvas space
 * ends up exactly [cellSizeInPixels] away from [targetX]/[targetY] (the canvas
 * centre where the midpoint was placed in Step 1).
 *
 * The midpoint stays fixed at [targetX]/[targetY] after scaling, because both
 * [MapCalibration.offsetX] and [MapCalibration.offsetY] are scaled by the same
 * factor as [MapCalibration.scale]:
 * ```
 *   midpointCanvasPos = canvas_centre + offset + (imgMidPx − imgHalfWidth) × scale
 * ```
 * From Step 1 we know the parenthesised sum equals zero, so scaling offset by
 * the same factor that scale changes keeps the sum zero — the midpoint does not move.
 *
 * Returns `null` when the corner click is too close to the target (distance < 1 px),
 * which would produce an undefined or extreme scale factor.
 *
 * @param step1Cal          the calibration produced by [guidedCalibrationStep1].
 * @param cornerX           canvas-space X of the tile corner chosen by the user.
 * @param cornerY           canvas-space Y of the tile corner chosen by the user.
 * @param targetX           canvas-space X of the canvas centre / midpoint.
 * @param targetY           canvas-space Y of the canvas centre / midpoint.
 * @param cellSizeInPixels  effective grid cell size in canvas pixels
 *                          ([GridCalibration.effectiveCellSizeInPixels]).
 * @return updated [MapCalibration] with adjusted scale and offsets, or `null` if
 *         the corner click coincides with the target.
 */
internal fun guidedCalibrationStep2(
    step1Cal: MapCalibration,
    cornerX: Double,
    cornerY: Double,
    targetX: Double,
    targetY: Double,
    cellSizeInPixels: Double,
): MapCalibration? {
    val d = hypot(cornerX - targetX, cornerY - targetY)
    if (d < 1.0) return null
    val scaleFactor = cellSizeInPixels / d
    return MapCalibration(
        scale = step1Cal.scale * scaleFactor,
        offsetX = step1Cal.offsetX * scaleFactor,
        offsetY = step1Cal.offsetY * scaleFactor,
    )
}
