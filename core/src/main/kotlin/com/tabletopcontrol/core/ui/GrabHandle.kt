package com.tabletopcontrol.core.ui

import javafx.scene.control.Label

/**
 * A small visual grab handle that signals to users they can drag a list item.
 *
 * The handle displays the [HANDLE_GLYPH] (⠿) and applies an `open-hand` cursor.
 * Add it to the left edge of a draggable card or row as an optional visual
 * affordance. Install drag handling on the draggable node itself (for example,
 * the row or card) using [DragDropSupport.installDragSource].
 *
 * Appearance is controlled by the `.tc-grab-handle` CSS class (if an external
 * stylesheet is loaded) and by the inline style fallback applied in [init].
 *
 * **Usage:**
 * ```kotlin
 * val handle = GrabHandle()
 * val card = HBox(handle, nameLabel)
 * DragDropSupport.installDragSource(card, index, dragContext)
 * ```
 */
class GrabHandle : Label(HANDLE_GLYPH) {

    init {
        styleClass.add("tc-grab-handle")
        style = "-fx-cursor: open-hand; -fx-text-fill: -tc-text-muted; " +
            "-fx-font-size: 14px; -fx-padding: 0 6 0 4;"
        isPickOnBounds = true
    }

    companion object {
        /**
         * Unicode Braille Pattern Dots-123456 (U+28BF) used as the drag-handle glyph.
         * Visually resembles a grip of dots common in DnD-reorderable lists.
         */
        const val HANDLE_GLYPH = "⠿"
    }
}
