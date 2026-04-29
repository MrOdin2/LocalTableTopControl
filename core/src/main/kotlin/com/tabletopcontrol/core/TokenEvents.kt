package com.tabletopcontrol.core

import javafx.scene.paint.Color

/**
 * Event published when a new combatant is added to the initiative tracker,
 * requesting a matching token to be created on the map.
 *
 * Subscribers may also treat a repeated [id] as a token metadata refresh for
 * an existing token, updating its visible name, colour, or size in place.
 *
 * @property id    stable unique identifier for the combatant/token (for example, a UUID string).
 * @property name  the combatant's display name; not guaranteed to be unique and may change over time.
 * @property color fill colour for the token circle.
 * @property size  rendered footprint size of the token; defaults to [TokenSize.MEDIUM]
 *                 for legacy saves and older publishers.
 * @property isPlayerCharacter whether this token belongs to an actor marked as a player character.
 * @property darkvisionRangeCells optional darkvision radius measured in grid cells.
 */
data class TokenAddedEvent(
    val id: String,
    val name: String,
    val color: Color,
    val size: TokenSize = TokenSize.MEDIUM,
    val isPlayerCharacter: Boolean = false,
    val darkvisionRangeCells: Double? = null,
)

/**
 * Event published when a combatant is removed from the initiative tracker,
 * requesting its map token to be removed.
 *
 * @property id   stable unique identifier of the combatant/token to remove.
 * @property name optional combatant display name, useful for logging or debugging only.
 */
data class TokenRemovedEvent(val id: String, val name: String)

/**
 * Event published when a token is moved to a new grid cell on the map.
 *
 * This is typically published by the DM when dragging a token on the minimap.
 *
 * @property id   stable unique identifier of the combatant/token to move.
 * @property name optional combatant display name, useful for logging or debugging only.
 * @property col  new zero-based grid column index.
 * @property row  new zero-based grid row index.
 */
data class TokenMovedEvent(val id: String, val name: String, val col: Int, val row: Int)

/**
 * Event published when the active combatant changes in the initiative tracker,
 * so the map can update the active-token highlight.
 *
 * @property id   stable unique identifier of the now-active combatant,
 *                or `null` when the tracker is empty.
 * @property name optional display name of the now-active combatant, for UI purposes only.
 */
data class ActiveTokenChangedEvent(val id: String?, val name: String?)

/**
 * Event published when the initiative tracker is fully reset, requesting all
 * tokens to be cleared from the map.
 */
class TokensResetEvent

/**
 * Event published when the DM assigns or removes a picture for an existing token.
 *
 * The [MapRenderer] updates its image cache in response and triggers a redraw.
 * When [imageUri] is `null` the token reverts to its plain filled-circle appearance.
 *
 * @property id          stable unique identifier of the token whose image is changing.
 * @property imageUri    file URI of the new picture (e.g. `"file:/path/to/image.png"`),
 *                       or `null` to remove the current picture.
 * @property imageScaleX horizontal scale factor for the image (defaults to `1.0`).
 * @property imageScaleY vertical scale factor for the image (defaults to `1.0`).
 * @property imageOffsetX horizontal pixel offset relative to the token centre (defaults to `0.0`).
 * @property imageOffsetY vertical pixel offset relative to the token centre (defaults to `0.0`).
 */
data class TokenImageChangedEvent(
    val id: String,
    val imageUri: String?,
    val imageScaleX: Double = 1.0,
    val imageScaleY: Double = 1.0,
    val imageOffsetX: Double = 0.0,
    val imageOffsetY: Double = 0.0,
)
