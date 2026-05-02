package com.tabletopcontrol.canvas

import com.tabletopcontrol.core.EventBus

/**
 * Canonical state for the Canvas plugin.
 *
 * All mutations are funnelled through this class. After every mutation a
 * [CanvasItemsChangedEvent] is published on [EventBus] so that the DM view and
 * the table overlay stay in sync without holding direct references to each other.
 */
class CanvasModel {

    private val _items = mutableListOf<CanvasItem>()

    /** Immutable snapshot of the current item list. */
    val items: List<CanvasItem> get() = _items.toList()

    /** Appends [item] to the canvas and publishes a change event. */
    fun addItem(item: CanvasItem) {
        _items.add(item)
        publish()
    }

    /** Removes the item with the given [id] and publishes a change event if found. */
    fun removeItem(id: String) {
        val removed = _items.removeIf { it.id == id }
        if (removed) publish()
    }

    /**
     * Replaces the item whose [CanvasItem.id] matches [updated] and publishes a change event.
     *
     * No-op when no matching item is found.
     */
    fun updateItem(updated: CanvasItem) {
        val idx = _items.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            _items[idx] = updated
            publish()
        }
    }

    /**
     * Sets the [CanvasItem.isShared] flag on **all** items to [share] and publishes a
     * change event.
     */
    fun setShareAll(share: Boolean) {
        _items.replaceAll { it.copy(isShared = share) }
        publish()
    }

    /**
     * Moves the item with [id] to the end of the list, so it is rendered on top of all
     * others. Publishes a change event.
     *
     * No-op when no matching item is found.
     */
    fun bringToFront(id: String) {
        val idx = _items.indexOfFirst { it.id == id }
        if (idx >= 0 && idx < _items.lastIndex) {
            _items.add(_items.removeAt(idx))
            publish()
        }
    }

    /**
     * Moves the item with [id] to the beginning of the list, so it is rendered behind all
     * others. Publishes a change event.
     *
     * No-op when no matching item is found.
     */
    fun sendToBack(id: String) {
        val idx = _items.indexOfFirst { it.id == id }
        if (idx > 0) {
            _items.add(0, _items.removeAt(idx))
            publish()
        }
    }

    private fun publish() {
        EventBus.publish(CanvasItemsChangedEvent(items))
    }
}
