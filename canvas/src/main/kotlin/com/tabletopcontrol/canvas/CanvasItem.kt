package com.tabletopcontrol.canvas

import java.util.UUID

/**
 * Represents a single picture placed on the Canvas.
 *
 * Positions and sizes are normalised to the table-view viewport so that items
 * render correctly regardless of the actual screen resolution:
 * - `0.0` = left / top edge of the viewport
 * - `1.0` = right / bottom edge of the viewport
 *
 * @property id        Unique identifier (auto-generated if not supplied).
 * @property filePath  Absolute file-system path to the source image.
 * @property x         Normalised X of the top-left corner (0..1).
 * @property y         Normalised Y of the top-left corner (0..1).
 * @property width     Normalised width (0..1).
 * @property height    Normalised height (0..1).
 * @property rotation  Clockwise rotation in degrees.
 * @property isShared  When `true` the item is projected onto the player table view.
 */
data class CanvasItem(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val x: Double = 0.1,
    val y: Double = 0.1,
    val width: Double = 0.3,
    val height: Double = 0.2,
    val rotation: Double = 0.0,
    val isShared: Boolean = false,
)
