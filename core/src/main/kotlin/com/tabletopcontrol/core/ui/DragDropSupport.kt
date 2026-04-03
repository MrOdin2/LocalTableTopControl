package com.tabletopcontrol.core.ui

import javafx.scene.Node
import javafx.scene.SnapshotParameters
import javafx.scene.input.ClipboardContent
import javafx.scene.input.DataFormat
import javafx.scene.input.TransferMode
import javafx.scene.paint.Color

/**
 * Central helper for attaching consistent drag-and-drop reorder behaviour to item nodes.
 *
 * All visual interactions — ghost preview, drop indicator, and auto-scroll — are managed
 * here.  Plugin code provides only a [DragDropContext] describing the format and callbacks.
 *
 * **Typical usage:**
 * ```kotlin
 * val ctx = DragDropContext(
 *     dataFormat = "tabletopcontrol/my-list-item",
 *     onReorder  = { from, to -> model.move(from, to); refresh() },
 * )
 * val indicator = DropIndicator()
 * listContainer.children.add(indicator)
 *
 * items.forEachIndexed { index, card ->
 *     DragDropSupport.installDragSource(card, index, ctx)
 *     DragDropSupport.installDropTarget(card, index, ctx, indicator)
 * }
 * ```
 *
 * The [DragDropContext.dataFormat] string is converted to a JavaFX [DataFormat] (MIME
 * type) internally.  It must be unique per list type to avoid cross-container drops.
 */
object DragDropSupport {

    /**
     * Installs drag-source behaviour on [node].
     *
     * When the user initiates a drag gesture on [node], the item's [index] is written to
     * the dragboard using the format key from [context].  If [DragDropContext.ghostFactory]
     * is set, that factory is called to produce the ghost image; otherwise a transparent
     * snapshot of [node] itself is used.
     *
     * @param node    The draggable node.
     * @param index   The item's current position within its container.
     * @param context Configuration and callbacks.
     */
    fun installDragSource(node: Node, index: Int, context: DragDropContext) {
        node.setOnDragDetected { e ->
            val db = node.startDragAndDrop(TransferMode.MOVE)
            val content = ClipboardContent()
            content[resolveFormat(context.dataFormat)] = index.toString()
            db.setContent(content)

            val ghostSource = context.ghostFactory?.invoke(node) ?: node
            val params = SnapshotParameters().apply { fill = Color.TRANSPARENT }
            val image = ghostSource.snapshot(params, null)
            db.setDragView(image, image.width / 2, image.height / 2)

            e.consume()
        }
    }

    /**
     * Installs drop-target behaviour on [node].
     *
     * When a compatible drag hovers over [node]:
     * - The transfer mode is accepted.
     * - [dropIndicator] (if provided) is made visible.
     * - The [DragDropContext.autoScrollPane] (if provided) is scrolled when the cursor
     *   approaches its edges.
     *
     * On a successful drop, [DragDropContext.onReorder] is called with the source and
     * target indices.  The indicator is hidden on both exit and drop.
     *
     * @param node          The target node.
     * @param index         The target item's current position within its container.
     * @param context       Configuration and callbacks.
     * @param dropIndicator Optional [DropIndicator] to show while dragging over this node.
     */
    fun installDropTarget(
        node: Node,
        index: Int,
        context: DragDropContext,
        dropIndicator: DropIndicator? = null,
    ) {
        val format = resolveFormat(context.dataFormat)

        node.setOnDragOver { e ->
            val board = e.dragboard
            if (board.hasContent(format)) {
                val sourceData = board.getContent(format) as? String ?: ""
                val fromIdx = sourceData.toIntOrNull()
                val isSameItem = fromIdx == index
                if (fromIdx != null && !isSameItem && e.gestureSource !== node && context.canAcceptDrop(sourceData)) {
                    e.acceptTransferModes(TransferMode.MOVE)
                    if (dropIndicator != null) {
                        repositionIndicator(dropIndicator, node)
                        dropIndicator.show()
                    }
                    autoScroll(context.autoScrollPane, e.sceneY)
                }
            }
            e.consume()
        }

        node.setOnDragExited { e ->
            dropIndicator?.hide()
            e.consume()
        }

        node.setOnDragDropped { e ->
            val board = e.dragboard
            val fromIdx = (board.getContent(format) as? String)?.toIntOrNull()
            val success = fromIdx != null && fromIdx != index
            if (success) {
                context.onReorder(fromIdx!!, index)
            }
            dropIndicator?.hide()
            e.isDropCompleted = success
            e.consume()
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Positions [indicator] as an absolutely-placed overlay at the top edge of [sibling]
     * within their shared [javafx.scene.layout.Pane] parent.
     *
     * The indicator is permanently kept out of the layout flow (`isManaged = false`) so it
     * never displaces [sibling] or any other child.  Because an unmanaged node is not sized
     * by its parent, [resizeRelocate] is used to explicitly set its width and position in a
     * single call.  The indicator is then brought to front so it is not occluded by items.
     *
     * Does nothing if [sibling] has no [javafx.scene.layout.Pane] parent.
     */
    private fun repositionIndicator(indicator: DropIndicator, sibling: Node) {
        val parent = sibling.parent as? javafx.scene.layout.Pane ?: return
        // Use parent.width for the already-laid-out size; fall back to layoutBounds during
        // very early layout passes.  If both are 0 the indicator will be invisible on that
        // single frame — acceptable since a drag-over can only fire once the scene is shown.
        val indicatorWidth = parent.width.takeIf { it > 0.0 } ?: parent.layoutBounds.width
        val indicatorY = (sibling.boundsInParent.minY - DropIndicator.HEIGHT / 2).coerceAtLeast(0.0)
        // resizeRelocate explicitly sizes the unmanaged node and sets its layout position.
        indicator.resizeRelocate(0.0, indicatorY, indicatorWidth, DropIndicator.HEIGHT)
        indicator.toFront()
    }

    /**
     * Returns the [DataFormat] for [mimeType], reusing an existing registration if one
     * already exists (JavaFX throws if you register the same mime type twice).
     */
    internal fun resolveFormat(mimeType: String): DataFormat =
        DataFormat.lookupMimeType(mimeType) ?: DataFormat(mimeType)

    private fun autoScroll(scrollPane: javafx.scene.control.ScrollPane?, sceneY: Double) {
        if (scrollPane == null) return
        val bounds = scrollPane.localToScene(scrollPane.boundsInLocal)
        val contentHeight = scrollPane.content?.boundsInLocal?.height ?: 0.0
        val viewportHeight = scrollPane.viewportBounds?.height ?: 0.0
        val scrollRange = (contentHeight - viewportHeight).takeIf { it > 0.0 } ?: return
        val step = AUTO_SCROLL_STEP / scrollRange
        when {
            sceneY < bounds.minY + AUTO_SCROLL_ZONE ->
                scrollPane.vvalue = (scrollPane.vvalue - step).coerceAtLeast(0.0)
            sceneY > bounds.maxY - AUTO_SCROLL_ZONE ->
                scrollPane.vvalue = (scrollPane.vvalue + step).coerceAtMost(1.0)
        }
    }

    private const val AUTO_SCROLL_ZONE = 40.0
    private const val AUTO_SCROLL_STEP = 20.0
}
