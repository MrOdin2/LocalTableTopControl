package com.tabletopcontrol.map

import javafx.scene.paint.Color

/**
 * Represents a visual token for a combatant on the map.
 *
 * Tokens are circles sized to fill one grid cell.  The [col]/[row] fields are
 * zero-based grid indices aligned to the same origin used by [GridCalibration].
 *
 * @property name  the combatant's display name; also the token's unique identifier.
 * @property col   grid column index of the cell the token occupies.
 * @property row   grid row index of the cell the token occupies.
 * @property color fill colour of the token circle.
 */
data class Token(val name: String, val col: Int, val row: Int, val color: Color)
