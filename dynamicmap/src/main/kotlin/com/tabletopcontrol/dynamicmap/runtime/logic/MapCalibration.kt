package com.tabletopcontrol.dynamicmap.runtime.logic

/**
 * Calibration parameters for the map image.
 *
 * The image is rendered centred on the canvas.  [offsetX] and [offsetY] displace the
 * image centre from the canvas centre, and [scale] zooms the image using the canvas
 * centre as the fixed point.  This means zooming in or out keeps the image centred
 * on-screen, making alignment straightforward.
 *
 * During a calibration session the renderer draws a red dot at the canvas centre
 * (see [com.tabletopcontrol.dynamicmap.runtime.MapCalibrationModeEvent]) so the DM can align a known reference point on
 * the map image with the physical table centre.
 *
 * @property scale    uniform zoom factor; `1.0` means no zoom; must be positive.
 * @property offsetX  horizontal displacement of the image centre from the canvas
 *                    centre, in canvas pixels.
 * @property offsetY  vertical displacement of the image centre from the canvas
 *                    centre, in canvas pixels.
 */
data class MapCalibration(
    val scale: Double = 1.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
) {
    init {
        require(scale > 0) { "scale must be positive, was $scale" }
    }
}