package com.tabletopcontrol.map

import javafx.scene.paint.Color

/**
 * Represents a visual token for a combatant on the map.
 *
 * Tokens are circles sized to fill one grid cell.  The [col]/[row] fields are
 * zero-based grid indices aligned to the same origin used by [GridCalibration].
 *
 * @property id       stable unique identifier for this token (matches the combatant UUID
 *                    from the initiative tracker).  Used for all move/remove lookups so
 *                    that tokens remain correctly targeted even after the combatant is renamed.
 * @property name     the combatant's display name; not guaranteed to be unique.
 * @property col      grid column index of the cell the token occupies.
 * @property row      grid row index of the cell the token occupies.
 * @property color    fill colour of the token circle; used as a fallback when no image is set.
 * @property imageUri URI of the picture to use for this token, or `null` to render a plain
 *                    filled circle using [color].  Typically a `file:` URI obtained via a
 *                    [javafx.stage.FileChooser].
 */
data class Token(
    val id: String,
    val name: String,
    val col: Int,
    val row: Int,
    val color: Color,
    val imageUri: String? = null,
)
