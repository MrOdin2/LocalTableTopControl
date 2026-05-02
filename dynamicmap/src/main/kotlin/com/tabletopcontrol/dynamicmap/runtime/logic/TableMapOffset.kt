package com.tabletopcontrol.dynamicmap.runtime.logic

/**
 * Shared displacement applied to the rendered map scene on the table.
 *
 * Unlike [MapCalibration] and [GridCalibration], this offset moves the entire
 * rendered scene together: map image, grid, fog-of-war, tokens, and
 * measurements. Values are expressed in canvas pixels after calibration.
 */
data class TableMapOffset(
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
)
