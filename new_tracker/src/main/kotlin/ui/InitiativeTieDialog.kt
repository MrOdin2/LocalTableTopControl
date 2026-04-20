package com.tabletopcontrol.new_tracker.ui

import com.tabletopcontrol.core.ui.DragDropContext
import com.tabletopcontrol.core.ui.DragDropSupport
import com.tabletopcontrol.core.ui.DropIndicator
import com.tabletopcontrol.core.ui.GrabHandle
import com.tabletopcontrol.core.ui.dialog.DialogFlows
import com.tabletopcontrol.core.ui.reorder.ReorderSupport
import com.tabletopcontrol.new_tracker.model.Actor
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Window

class InitiativeTieDialog {

    fun show(
        owner: Window?,
        actorsAtInitiative: List<Actor>,
        initiative: Int,
        movedActorId: String,
    ): List<Actor>? {
        require(actorsAtInitiative.any { it.id == movedActorId }) {
            "Initiative tie dialog requires the moved actor to be part of the tie group."
        }

        val orderedActors = actorsAtInitiative.toMutableList()
        val listBox = VBox(6.0).apply {
            isFillWidth = true
            prefWidth = DIALOG_CONTENT_WIDTH
            minWidth = DIALOG_CONTENT_WIDTH
        }
        val listScrollPane = ScrollPane(listBox).apply {
            isFitToWidth = true
            prefViewportWidth = DIALOG_CONTENT_WIDTH
            prefViewportHeight = (orderedActors.size * 72.0).coerceIn(260.0, 420.0)
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            prefWidth = DIALOG_CONTENT_WIDTH
            maxWidth = DIALOG_CONTENT_WIDTH
        }
        val indicator = DropIndicator()

        fun resolveGhostNode(dragSource: Node): Node {
            var current: Node? = dragSource
            while (current != null && current.parent != listBox) {
                current = current.parent
            }
            return current ?: dragSource
        }

        lateinit var rebuildRows: () -> Unit
        val dragContext = DragDropContext(
            dataFormat = "tabletopcontrol/new-tracker-initiative-tie-$movedActorId",
            autoScrollPane = listScrollPane,
            ghostFactory = { dragSource -> resolveGhostNode(dragSource) },
            onReorder = { fromIndex, toIndex ->
                if (!ReorderSupport.reorderMutableListFromDrop(orderedActors, fromIndex, toIndex)) {
                    return@DragDropContext
                }
                rebuildRows()
            },
        )

        rebuildRows = {
            val rows = orderedActors.mapIndexed { index, actor ->
                val handle = GrabHandle()
                val nameLabel = Label(actor.name).apply {
                    isWrapText = true
                    minWidth = 0.0
                    maxWidth = Double.MAX_VALUE
                    HBox.setHgrow(this, Priority.ALWAYS)
                }
                val row = HBox(8.0, handle, nameLabel).apply {
                    alignment = Pos.TOP_LEFT
                    maxWidth = Double.MAX_VALUE
                    padding = Insets(10.0, 12.0, 10.0, 12.0)
                    style = """
                        -fx-background-color: -tc-surface;
                        -fx-border-color: -tc-card-border;
                        -fx-border-radius: 6;
                        -fx-background-radius: 6;
                    """.trimIndent().replace("\n", " ")
                }
                DragDropSupport.installDragSource(handle, index, dragContext)
                DragDropSupport.installDropTarget(row, index, dragContext, indicator)
                row
            }

            val endDropTarget = Region().apply {
                minHeight = 18.0
                prefHeight = 18.0
                maxWidth = Double.MAX_VALUE
                isPickOnBounds = true
            }
            DragDropSupport.installDropTarget(endDropTarget, orderedActors.size, dragContext, indicator)

            listBox.children.setAll(listOf(indicator) + rows + endDropTarget)
        }

        rebuildRows()

        val content = VBox(
            8.0,
            Label("Multiple actors have initiative $initiative. Drag the names into the order you want.").apply {
                isWrapText = true
            },
            Label("Use the grab handle to drag. Drop onto a row to place before it, or use the space at the bottom to move an actor to the end.").apply {
                isWrapText = true
                style = "-fx-text-fill: -tc-text-muted;"
            },
            listScrollPane,
        ).apply {
            padding = Insets(4.0, 0.0, 0.0, 0.0)
            prefWidth = DIALOG_CONTENT_WIDTH
            maxWidth = DIALOG_CONTENT_WIDTH
        }

        val dialog = createTallDialog(owner, content)
        dialog.setResultConverter { button ->
            DialogFlows.resultForButton(button) { orderedActors.toList() }
        }
        return dialog.showAndWait().orElse(null)
    }

    private fun createTallDialog(
        owner: Window?,
        content: VBox,
    ): Dialog<List<Actor>> =
        DialogFlows.createDialog<List<Actor>>(
            owner = owner,
            title = "Resolve Initiative Tie",
            headerText = "Arrange actors with the same initiative",
            content = content,
            buttonTypes = listOf(ButtonType.OK, ButtonType.CANCEL),
        ).apply {
            dialogPane.prefWidth = DIALOG_WIDTH
            dialogPane.minWidth = DIALOG_WIDTH
            dialogPane.maxWidth = DIALOG_WIDTH
        }

    private companion object {
        const val DIALOG_WIDTH = 360.0
        const val DIALOG_CONTENT_WIDTH = 320.0
    }
}
