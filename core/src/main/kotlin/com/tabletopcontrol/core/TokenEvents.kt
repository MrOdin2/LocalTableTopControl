package com.tabletopcontrol.core

import javafx.scene.paint.Color

/**
 * Event published when a new combatant is added to the initiative tracker,
 * requesting a matching token to be created on the map.
 *
 * @property name  the combatant's display name; also used as the token's unique identifier.
 * @property color fill colour for the token circle.
 */
data class TokenAddedEvent(val name: String, val color: Color)

/**
 * Event published when a combatant is removed from the initiative tracker,
 * requesting its map token to be removed.
 *
 * @property name the combatant's display name identifying the token to remove.
 */
data class TokenRemovedEvent(val name: String)

/**
 * Event published when a token is moved to a new grid cell on the map.
 *
 * This is typically published by the DM when dragging a token on the minimap.
 *
 * @property name the combatant's display name identifying the token to move.
 * @property col  new zero-based grid column index.
 * @property row  new zero-based grid row index.
 */
data class TokenMovedEvent(val name: String, val col: Int, val row: Int)

/**
 * Event published when the active combatant changes in the initiative tracker,
 * so the map can update the active-token highlight.
 *
 * @property name the display name of the now-active combatant,
 *               or `null` when the tracker is empty.
 */
data class ActiveTokenChangedEvent(val name: String?)

/**
 * Event published when the initiative tracker is fully reset, requesting all
 * tokens to be cleared from the map.
 */
class TokensResetEvent
