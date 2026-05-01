package com.tabletopcontrol.canvas

import com.tabletopcontrol.core.EventBus
import javafx.application.Platform
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import javafx.scene.transform.Rotate

/**
 * Player-facing overlay pane for the Canvas plugin.
 *
 * This pane is placed on top of the primary table view (Map / DynamicMap) inside
 * the table-screen [javafx.scene.layout.StackPane]. It is fully transparent by
 * default, rendering only [CanvasItem]s whose [CanvasItem.isShared] flag is `true`.
 *
 * Mouse events pass through the overlay (it is mouse-transparent) so normal
 * interaction with the underlying table view is not blocked.
 */
class CanvasTableOverlay {

    private val itemViews = mutableMapOf<String, CanvasTableItemView>()
    private var subscription: EventBus.Subscription? = null

    private val pane: Pane = object : Pane() {
        override fun layoutChildren() {
            super.layoutChildren()
            if (width > 0 && height > 0) {
                itemViews.values.forEach { it.layout(width, height) }
            }
        }
    }.apply {
        isMouseTransparent = true
        style = "-fx-background-color: transparent;"
    }

    /** Returns the overlay [Node] to be inserted into the table scene. */
    fun createView(): Node {
        subscription = EventBus.subscribe<CanvasItemsChangedEvent> { event ->
            Platform.runLater { syncItems(event.items) }
        }
        return pane
    }

    /** Cancels the EventBus subscription. Call when the plugin shuts down. */
    fun dispose() {
        subscription?.unsubscribe()
        subscription = null
    }

    // ─── Synchronisation ────────────────────────────────────────────────────

    private fun syncItems(items: List<CanvasItem>) {
        val sharedItems = items.filter { it.isShared }
        val currentIds = sharedItems.map { it.id }.toSet()

        // Remove views for items that are no longer shared or have been deleted.
        val idsToRemove = itemViews.keys.filter { it !in currentIds }
        idsToRemove.forEach { id ->
            itemViews.remove(id)?.let { view -> pane.children.remove(view) }
        }

        // Add or update views for shared items.
        for (item in sharedItems) {
            val existing = itemViews[item.id]
            if (existing != null) {
                existing.item = item
                if (pane.width > 0 && pane.height > 0) {
                    existing.layout(pane.width, pane.height)
                }
            } else {
                val view = CanvasTableItemView(item)
                itemViews[item.id] = view
                pane.children.add(view)
                if (pane.width > 0 && pane.height > 0) {
                    view.layout(pane.width, pane.height)
                }
            }
        }
    }
}

// ─── Table item view ─────────────────────────────────────────────────────────

/**
 * A single [Group] rendering one [CanvasItem] on the table overlay.
 *
 * The [Group] is mouse-transparent so it does not absorb pointer events that
 * should reach the underlying map layer.
 */
private class CanvasTableItemView(item: CanvasItem) : Group() {

    var item: CanvasItem = item
        set(value) {
            field = value
            loadImageIfNeeded()
        }

    private val imageView = ImageView().apply {
        isPreserveRatio = false
        isSmooth = true
        isMouseTransparent = true
    }

    private val rotateTransform = Rotate()
    private var loadedPath: String? = null

    init {
        isMouseTransparent = true
        transforms.add(rotateTransform)
        children.add(imageView)
        loadImageIfNeeded()
    }

    fun layout(paneW: Double, paneH: Double) {
        val x = item.x * paneW
        val y = item.y * paneH
        val w = item.width * paneW
        val h = item.height * paneH

        imageView.fitWidth = w
        imageView.fitHeight = h

        layoutX = x
        layoutY = y
        rotateTransform.angle = item.rotation
        rotateTransform.pivotX = w / 2
        rotateTransform.pivotY = h / 2

        loadImageIfNeeded()
    }

    private fun loadImageIfNeeded() {
        val path = item.filePath
        if (path == loadedPath) return
        loadedPath = path
        try {
            imageView.image = Image("file:$path", true)
        } catch (_: Exception) {
            imageView.image = null
        }
    }
}
