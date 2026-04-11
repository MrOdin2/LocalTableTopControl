package com.tabletopcontrol.core.ui

import javafx.scene.layout.Region

/**
 * A thin bar rendered as a visual drop indicator during drag-and-drop reordering
 * operations.
 *
 * Add this node once to the same layout container as the draggable items (e.g. a
 * [javafx.scene.layout.VBox]).  The indicator is **always kept out of the layout
 * flow** (`isManaged` is permanently `false`), so it never displaces other children.
 * [DragDropSupport] positions it with absolute coordinates and calls [show]/[hide]
 * as the user drags over candidate positions. In vertical lists it is drawn as a
 * horizontal bar; in horizontal lists it is drawn as a vertical bar.
 *
 * The bar respects the application theme: it uses the `-tc-accent` CSS variable when
 * the node is inside a themed scene.  The CSS class `tc-drop-indicator` may also be
 * used to style it from an external stylesheet.
 *
 * **Usage:**
 * ```kotlin
 * val indicator = DropIndicator()
 * listContainer.children.add(indicator)
 * DragDropSupport.installDropTarget(card, index, context, indicator)
 * ```
 */
class DropIndicator : Region() {

    init {
        styleClass.add("tc-drop-indicator")
        prefWidth = THICKNESS
        prefHeight = THICKNESS
        maxWidth = Double.MAX_VALUE
        maxHeight = Double.MAX_VALUE
        style = "-fx-background-color: -tc-accent; -fx-background-radius: 2;"
        isVisible = false
        isManaged = false
        isMouseTransparent = true
        isPickOnBounds = false
    }

    /** Shows the indicator without affecting layout flow. */
    fun show() {
        isVisible = true
    }

    /** Hides the indicator. */
    fun hide() {
        isVisible = false
    }

    companion object {
        /** Thickness of the indicator bar in pixels. */
        const val THICKNESS = 3.0
    }
}
