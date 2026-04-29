package com.tabletopcontrol.dynamicmap.runtime.logic

import com.tabletopcontrol.core.TokenSize
import javafx.scene.paint.Color

/**
 * Represents a visual token for a combatant on the map.
 *
 * Tokens are circles sized to fill one grid cell.  The [col]/[row] fields are
 * zero-based grid indices aligned to the same origin used by [GridCalibration].
 *
 * @property id          stable unique identifier for this token (matches the combatant UUID
 *                       from the initiative tracker).  Used for all move/remove lookups so
 *                       that tokens remain correctly targeted even after the combatant is renamed.
 * @property name        the combatant's display name; not guaranteed to be unique.
 * @property col         grid column index of the cell the token occupies.
 * @property row         grid row index of the cell the token occupies.
 * @property size        rendered footprint size on the grid; defaults to [TokenSize.MEDIUM]
 *                       for legacy saves and event publishers.
 * @property color       fill colour of the token circle; used as a fallback when no image is set.
 * @property isPlayerCharacter whether this token belongs to an actor marked as a player character.
 * @property darkvisionRangeCells optional darkvision radius measured in grid cells.
 * @property imageUri    URI of the picture to use for this token, or `null` to render a plain
 *                       filled circle using [color].  Typically a `file:` URI obtained via a
 *                       [javafx.stage.FileChooser].
 * @property imageScaleX horizontal scale factor applied to the token image. Defaults to `1.0`.
 * @property imageScaleY vertical scale factor applied to the token image. Defaults to `1.0`.
 * @property imageOffsetX horizontal pixel offset applied to the token image relative to the
 *                        token centre. Positive shifts the image right.
 * @property imageOffsetY vertical pixel offset applied to the token image relative to the
 *                        token centre. Positive shifts the image down.
 */
data class Token(
    val id: String,
    val name: String,
    val col: Int,
    val row: Int,
    val size: TokenSize = TokenSize.MEDIUM,
    val color: Color,
    val isPlayerCharacter: Boolean = false,
    val darkvisionRangeCells: Double? = null,
    val imageUri: String? = null,
    val imageScaleX: Double = 1.0,
    val imageScaleY: Double = 1.0,
    val imageOffsetX: Double = 0.0,
    val imageOffsetY: Double = 0.0,
)
