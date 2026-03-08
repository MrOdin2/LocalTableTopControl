package com.tabletopcontrol.map

import javafx.scene.paint.Color

/**
 * Returns a grid line colour that contrasts with [background].
 *
 * Computes the perceived luminance of [background] using the standard
 * ITU-R BT.709 coefficients and returns a semi-transparent black when
 * the background is light (luminance > 0.5) or a semi-transparent white
 * when the background is dark.  The 50 % alpha matches the default
 * [GridConfig.color] opacity so the auto-suggested colour integrates
 * naturally with the rest of the rendering.
 *
 * @param background the background colour to contrast against.
 * @return a semi-transparent black or white [Color].
 */
fun contrastingGridColor(background: Color): Color {
    val luminance = 0.2126 * background.red + 0.7152 * background.green + 0.0722 * background.blue
    return if (luminance > 0.5) Color.color(0.0, 0.0, 0.0, 0.5) else Color.color(1.0, 1.0, 1.0, 0.5)
}

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
