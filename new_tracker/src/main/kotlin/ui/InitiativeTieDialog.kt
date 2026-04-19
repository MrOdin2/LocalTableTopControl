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
        val listBox = VBox(6.0)
        val listScrollPane = ScrollPane(listBox).apply {
            isFitToWidth = true
            prefHeight = (orderedActors.size * 48.0).coerceAtMost(300.0)
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
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
                    maxWidth = Double.MAX_VALUE
                    HBox.setHgrow(this, Priority.ALWAYS)
                }
                val row = HBox(8.0, handle, nameLabel).apply {
                    alignment = Pos.CENTER_LEFT
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
        }

        return DialogFlows.showResultDialog(
            owner = owner,
            title = "Resolve Initiative Tie",
            headerText = "Arrange actors with the same initiative",
            content = content,
            buttonTypes = listOf(ButtonType.OK, ButtonType.CANCEL),
        ) { button ->
            DialogFlows.resultForButton(button) { orderedActors.toList() }
        }
    }
}
