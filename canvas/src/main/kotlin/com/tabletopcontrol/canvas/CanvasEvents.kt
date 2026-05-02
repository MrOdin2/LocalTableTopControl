package com.tabletopcontrol.canvas

/**
 * Published on [com.tabletopcontrol.core.EventBus] whenever the canvas item list changes.
 *
 * Both the DM view and the table overlay subscribe to this event to keep
 * their visual representations in sync with the canonical [CanvasModel].
 *
 * @property items Immutable snapshot of the current item list at the time of publication.
 */
data class CanvasItemsChangedEvent(val items: List<CanvasItem>)
