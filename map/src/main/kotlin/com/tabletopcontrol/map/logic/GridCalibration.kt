package com.tabletopcontrol.map.logic

/**
 * Calibration parameters for the grid overlay.
 *
 * The grid origin — the intersection of two adjacent grid lines — is placed at the
 * centre of the canvas displaced by [offsetX] and [offsetY].  All scaling operations
 * use that canvas centre as their fixed point, so zooming in or out never moves the
 * origin off-screen.
 *
 * During a calibration session the renderer draws a crosshair at the canvas centre
 * (see [com.tabletopcontrol.map.GridCalibrationModeEvent]) so the DM can align the grid precisely.
 *
 * @property cellSizeInPixels  width/height of one grid cell in canvas pixels at scale 1.0;
 *                             must be positive.
 * @property scale             zoom factor applied from the canvas centre; must be positive.
 * @property offsetX           horizontal displacement of the grid origin from the canvas
 *                             centre, in canvas pixels.
 * @property offsetY           vertical displacement of the grid origin from the canvas
 *                             centre, in canvas pixels.
 */
data class GridCalibration(
    val cellSizeInPixels: Double = 50.0,
    val scale: Double = 1.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
) {
    init {
        require(cellSizeInPixels > 0) { "cellSizeInPixels must be positive, was $cellSizeInPixels" }
        require(scale > 0) { "scale must be positive, was $scale" }
    }

    /**
     * Returns the effective grid cell size in canvas pixels after applying [scale].
     */
    fun effectiveCellSizeInPixels(): Double = cellSizeInPixels * scale
}