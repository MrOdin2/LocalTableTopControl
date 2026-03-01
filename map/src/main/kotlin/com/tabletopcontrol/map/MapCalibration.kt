package com.tabletopcontrol.map

/**
 * Defines the calibration parameters used to map image pixels to the canvas.
 *
 * Calibration allows the rendered map to be aligned with physical table
 * dimensions by specifying how many image pixels correspond to one game unit
 * (e.g., 5 feet / 1 metre) and applying a translation offset.
 *
 * @property pixelsPerUnit number of source-image pixels that equal one game unit.
 *                         Must be positive.
 * @property offsetX       horizontal translation (in canvas pixels) applied
 *                         before rendering.
 * @property offsetY       vertical translation (in canvas pixels) applied
 *                         before rendering.
 * @property scale         uniform zoom factor applied on top of [pixelsPerUnit].
 *                         `1.0` means no extra zoom; must be positive.
 */
data class MapCalibration(
    val pixelsPerUnit: Double = 50.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val scale: Double = 1.0,
) {
    init {
        require(pixelsPerUnit > 0) { "pixelsPerUnit must be positive, was $pixelsPerUnit" }
        require(scale > 0) { "scale must be positive, was $scale" }
    }

    /**
     * Converts a source-image x-coordinate to a canvas x-coordinate.
     *
     * @param imageX the x-coordinate in source-image pixels
     * @return the corresponding x-coordinate on the canvas
     */
    fun toCanvasX(imageX: Double): Double = imageX * scale + offsetX

    /**
     * Converts a source-image y-coordinate to a canvas y-coordinate.
     *
     * @param imageY the y-coordinate in source-image pixels
     * @return the corresponding y-coordinate on the canvas
     */
    fun toCanvasY(imageY: Double): Double = imageY * scale + offsetY

    /**
     * Returns the effective grid cell size in canvas pixels, given a
     * [cellSizeInUnits] expressed in game units.
     *
     * @param cellSizeInUnits size of one grid cell measured in game units
     * @return cell width/height in canvas pixels
     */
    fun cellSizeInPixels(cellSizeInUnits: Double = 1.0): Double =
        cellSizeInUnits * pixelsPerUnit * scale
}
