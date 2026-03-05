package com.tabletopcontrol.core

import javafx.scene.paint.Color

/**
 * Event published when a new combatant is added to the initiative tracker,
 * requesting a matching token to be created on the map.
 *
 * @property id    stable unique identifier for the combatant/token (for example, a UUID string).
 * @property name  the combatant's display name; not guaranteed to be unique and may change over time.
 * @property color fill colour for the token circle.
 */
data class TokenAddedEvent(val id: String, val name: String, val color: Color)

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
 * Event published when a combatant's display name is changed in the initiative tracker,
 * so any views displaying token labels can update accordingly.
 *
 * @property id      stable unique identifier of the combatant/token being renamed.
 * @property newName new display name for the combatant/token.
 */
data class TokenRenamedEvent(val id: String, val newName: String)

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
