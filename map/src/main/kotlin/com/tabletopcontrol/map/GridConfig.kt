package com.tabletopcontrol.map

import javafx.scene.paint.Color

/**
 * Configuration for the grid overlay rendered on top of the map.
 *
 * @property cellSizeInUnits size of one grid cell expressed in game units.
 *                           The actual pixel size is derived from
 *                           [MapCalibration.cellSizeInPixels]. Must be positive.
 * @property color           stroke colour used to draw the grid lines.
 * @property lineWidth       stroke width of the grid lines in canvas pixels; must be positive.
 * @property visible         whether the grid overlay is currently shown.
 */
data class GridConfig(
    val cellSizeInUnits: Double = 1.0,
    val color: Color = Color.color(0.0, 0.0, 0.0, 0.5),
    val lineWidth: Double = 1.0,
    val visible: Boolean = true,
) {
    init {
        require(cellSizeInUnits > 0) { "cellSizeInUnits must be positive, was $cellSizeInUnits" }
        require(lineWidth > 0) { "lineWidth must be positive, was $lineWidth" }
    }
}
