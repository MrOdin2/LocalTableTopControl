package com.tabletopcontrol.core.ui

import javafx.scene.Node
import javafx.scene.control.ScrollPane

/**
 * Configuration record for a drag-and-drop reorder operation managed by [DragDropSupport].
 *
 * Create one instance per draggable list / grid container and pass it to
 * [DragDropSupport.installDragSource] and [DragDropSupport.installDropTarget] for every
 * item node.
 *
 * @property dataFormat     A unique MIME-type-like string identifying the drag format,
 *                          e.g. `"tabletopcontrol/tracker-item"`.  Only drops that carry
 *                          content in this format are accepted, preventing accidental
 *                          cross-list drops.
 * @property ghostFactory   Optional factory that receives the dragged [Node] and returns
 *                          a custom ghost-preview node rendered under the cursor during the
 *                          drag.  When `null` a snapshot of the source node is used.
 * @property onReorder      Callback invoked when a drop is accepted, receiving the
 *                          `fromIndex` of the dragged item and the `toIndex` of the drop
 *                          target.  Implementations should move the item in their backing
 *                          data model and refresh the UI. For shared index validation and
 *                          drop-target adjustment, use
 *                          [com.tabletopcontrol.core.ui.reorder.ReorderSupport].
 * @property canAcceptDrop  Optional predicate that can veto a drag-over event.  Receives
 *                          the raw source-data string from the dragboard.  Return `false`
 *                          to reject the drop silently.  Default: always accept.
 * @property autoScrollPane Optional [ScrollPane] whose scroll position is adjusted
 *                          automatically when the cursor approaches the relevant edge
 *                          during a drag. Vertical drop targets scroll near the top or
 *                          bottom edge, and horizontal drop targets scroll near the left
 *                          or right edge. The active axis is selected by the
 *                          `orientation` argument passed to
 *                          [DragDropSupport.installDropTarget]. `null` disables
 *                          auto-scroll.
 */
data class DragDropContext(
    val dataFormat: String,
    val ghostFactory: ((draggedNode: Node) -> Node)? = null,
    val onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    val canAcceptDrop: (sourceData: String) -> Boolean = { true },
    val autoScrollPane: ScrollPane? = null,
)
