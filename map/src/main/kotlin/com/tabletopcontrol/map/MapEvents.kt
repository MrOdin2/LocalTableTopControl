package com.tabletopcontrol.map

/**
 * Event fired when the DM loads a new map image.
 *
 * @property resourcePath file-system path or classpath URI of the image to load.
 */
data class MapLoadEvent(val resourcePath: String)

/**
 * Event fired when the DM changes the grid visibility or configuration.
 *
 * @property config the new [GridConfig] to apply; `null` to hide the grid.
 */
data class GridUpdateEvent(val config: GridConfig?)

/**
 * Event fired when a single fog-of-war cell is toggled.
 *
 * @property col      zero-based column index of the cell.
 * @property row      zero-based row index of the cell.
 * @property revealed `true` to reveal the cell; `false` to hide it.
 */
data class FogOfWarCellEvent(val col: Int, val row: Int, val revealed: Boolean)

/**
 * Event fired when the DM resets the entire fog-of-war layer at once.
 *
 * @property revealAll `true` to reveal every cell; `false` to hide every cell.
 */
data class FogOfWarResetEvent(val revealAll: Boolean)

/**
 * Event fired when the DM applies new map-image calibration.
 *
 * @property calibration the new [MapCalibration] to apply.
 */
data class MapCalibrationEvent(val calibration: MapCalibration)

/**
 * Event fired when the DM applies new grid calibration.
 *
 * @property calibration the new [GridCalibration] to apply.
 */
data class GridCalibrationEvent(val calibration: GridCalibration)

/**
 * Event fired to enable or disable the grid calibration overlay.
 *
 * When [active] is `true` the renderer draws a crosshair at the canvas centre
 * so the DM can align the grid origin.  Set to `false` when the calibration
 * dialog is closed.
 *
 * @property active `true` to show the crosshair; `false` to hide it.
 */
data class GridCalibrationModeEvent(val active: Boolean)

/**
 * Event fired to enable or disable the map calibration overlay.
 *
 * When [active] is `true` the renderer draws a red dot at the canvas centre
 * so the DM can align a reference point on the map image.  Set to `false`
 * when the calibration dialog is closed.
 *
 * @property active `true` to show the centre dot; `false` to hide it.
 */
data class MapCalibrationModeEvent(val active: Boolean)
