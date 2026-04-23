package com.tabletopcontrol.dynamicmap

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.DmWorkspaceId
import com.tabletopcontrol.core.EventBus
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TreeCell
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.util.Callback
import java.util.Locale

class DynamicMapOutlineBrowserPlugin : DmPlugin {
    override val displayName: String = "Outline Browser"

    override val workspaceIds: Set<DmWorkspaceId> = setOf(DmWorkspaceId.DYNAMIC_MAP_BUILDER)

    override fun createView(): Node {
        var document = DynamicMapDocument()
        var selection: DynamicMapElementSelection? = null
        var isApplyingSelection = false
        var itemBySelection = emptyMap<DynamicMapElementSelection, TreeItem<DynamicMapOutlineNode>>()

        val summaryLabel = Label().apply {
            style = "-fx-text-fill: -tc-text-muted;"
        }
        val tree = TreeView<DynamicMapOutlineNode>().apply {
            isShowRoot = false
            style = "-fx-background-color: -tc-surface;"
            cellFactory = Callback {
                object : TreeCell<DynamicMapOutlineNode>() {
                    override fun updateItem(item: DynamicMapOutlineNode?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (empty || item == null) {
                            text = null
                            style = ""
                            return
                        }
                        text = item.label
                        style = when (item) {
                            is DynamicMapOutlineGroupNode -> "-fx-font-weight: bold;"
                            is DynamicMapOutlineHintNode -> "-fx-text-fill: -tc-text-muted;"
                            is DynamicMapOutlineElementNode -> ""
                        }
                    }
                }
            }
        }
        val removeSelectedButton = Button("Remove Selected")
        val toggleLightButton = Button("Disable Light")
        val clearSelectionButton = Button("Clear Selection")
        val footerLabel = Label(
            "The outline shows every wall and light in the current draft. Future builder element types should join this pane as they are added.",
        ).apply {
            isWrapText = true
            style = "-fx-text-fill: -tc-text-muted;"
        }

        fun selectedLight(): DynamicMapLight? =
            selection
                ?.takeIf { it.kind == DynamicMapElementKind.LIGHT }
                ?.let { document.lightById(it.elementId) }

        fun refreshActionButtons() {
            removeSelectedButton.isDisable = selection == null
            val light = selectedLight()
            toggleLightButton.isDisable = light == null
            toggleLightButton.text = if (light?.enabled == false) "Enable Light" else "Disable Light"
        }

        fun syncTreeSelection() {
            isApplyingSelection = true
            val treeSelection = selection?.let(itemBySelection::get)
            if (treeSelection != null) {
                tree.selectionModel.select(treeSelection)
                val row = tree.getRow(treeSelection)
                if (row >= 0) {
                    tree.scrollTo(row)
                }
            } else {
                tree.selectionModel.clearSelection()
            }
            isApplyingSelection = false
            refreshActionButtons()
        }

        fun rebuildTree() {
            val root = TreeItem<DynamicMapOutlineNode>(DynamicMapOutlineGroupNode("Dynamic Map Draft")).apply {
                isExpanded = true
            }
            val index = linkedMapOf<DynamicMapElementSelection, TreeItem<DynamicMapOutlineNode>>()

            val lightsNode = TreeItem<DynamicMapOutlineNode>(
                DynamicMapOutlineGroupNode("Lights (${document.lights.size})"),
            ).apply { isExpanded = true }
            if (document.lights.isEmpty()) {
                lightsNode.children += TreeItem<DynamicMapOutlineNode>(
                    DynamicMapOutlineHintNode("No lights placed yet"),
                )
            } else {
                document.lights.forEachIndexed { itemIndex, light ->
                    val itemSelection = DynamicMapElementSelection(
                        kind = DynamicMapElementKind.LIGHT,
                        elementId = light.id,
                    )
                    val item = TreeItem<DynamicMapOutlineNode>(
                        DynamicMapOutlineElementNode(
                            selection = itemSelection,
                            label = buildLightOutlineLabel(itemIndex, light),
                        ),
                    )
                    lightsNode.children += item
                    index[itemSelection] = item
                }
            }

            val wallsNode = TreeItem<DynamicMapOutlineNode>(
                DynamicMapOutlineGroupNode("Walls (${document.walls.size})"),
            ).apply { isExpanded = true }
            if (document.walls.isEmpty()) {
                wallsNode.children += TreeItem<DynamicMapOutlineNode>(
                    DynamicMapOutlineHintNode("No walls placed yet"),
                )
            } else {
                document.walls.forEachIndexed { itemIndex, wall ->
                    val itemSelection = DynamicMapElementSelection(
                        kind = DynamicMapElementKind.WALL,
                        elementId = wall.id,
                    )
                    val item = TreeItem<DynamicMapOutlineNode>(
                        DynamicMapOutlineElementNode(
                            selection = itemSelection,
                            label = buildWallOutlineLabel(itemIndex, wall),
                        ),
                    )
                    wallsNode.children += item
                    index[itemSelection] = item
                }
            }

            root.children.setAll(lightsNode, wallsNode)
            itemBySelection = index
            tree.root = root
            summaryLabel.text =
                "Lights: ${document.lights.count { it.enabled }}/${document.lights.size} enabled | Walls: ${document.walls.size}"
            if (selection?.let { !document.containsSelection(it) } == true) {
                EventBus.publish(DynamicMapSelectionChangedEvent(null))
            } else {
                syncTreeSelection()
            }
        }

        tree.selectionModel.selectedItemProperty().addListener { _, _, selectedItem ->
            if (isApplyingSelection) return@addListener
            val selectedNode = selectedItem?.value as? DynamicMapOutlineElementNode
            EventBus.publish(DynamicMapSelectionChangedEvent(selectedNode?.selection))
        }

        removeSelectedButton.setOnAction {
            val currentSelection = selection ?: return@setOnAction
            EventBus.publish(DynamicMapElementRemovalRequestedEvent(currentSelection))
        }
        toggleLightButton.setOnAction {
            val light = selectedLight() ?: return@setOnAction
            EventBus.publish(
                DynamicMapLightEnabledRequestedEvent(
                    lightId = light.id,
                    enabled = !light.enabled,
                ),
            )
        }
        clearSelectionButton.setOnAction {
            EventBus.publish(DynamicMapSelectionChangedEvent(null))
        }

        val documentSubscription = EventBus.subscribe<DynamicMapDocumentChangedEvent> { event ->
            document = event.document
            rebuildTree()
        }
        val selectionSubscription = EventBus.subscribe<DynamicMapSelectionChangedEvent> { event ->
            selection = event.selection?.takeIf { document.containsSelection(it) }
            syncTreeSelection()
        }

        refreshActionButtons()
        EventBus.publish(DynamicMapDocumentSnapshotRequestedEvent)

        return VBox(
            8.0,
            Label("Builder outline"),
            summaryLabel,
            tree,
            removeSelectedButton,
            toggleLightButton,
            clearSelectionButton,
            footerLabel,
        ).apply {
            padding = Insets(8.0)
            style = "-fx-background-color: -tc-bg;"
            VBox.setVgrow(tree, Priority.ALWAYS)
            removeSelectedButton.maxWidth = Double.MAX_VALUE
            toggleLightButton.maxWidth = Double.MAX_VALUE
            clearSelectionButton.maxWidth = Double.MAX_VALUE
            sceneProperty().addListener { _, _, newScene ->
                if (newScene == null) {
                    documentSubscription.unsubscribe()
                    selectionSubscription.unsubscribe()
                }
            }
        }
    }
}

private sealed interface DynamicMapOutlineNode {
    val label: String
}

private data class DynamicMapOutlineGroupNode(
    override val label: String,
) : DynamicMapOutlineNode

private data class DynamicMapOutlineHintNode(
    override val label: String,
) : DynamicMapOutlineNode

private data class DynamicMapOutlineElementNode(
    val selection: DynamicMapElementSelection,
    override val label: String,
) : DynamicMapOutlineNode

private fun buildLightOutlineLabel(index: Int, light: DynamicMapLight): String {
    val state = if (light.enabled) "" else "[Off] "
    return "${index + 1}. ${state}${light.label} @ ${formatOutlineCoordinate(light.position.x)}, " +
        formatOutlineCoordinate(light.position.y)
}

private fun buildWallOutlineLabel(index: Int, wall: DynamicMapWall): String =
    "${index + 1}. ${formatOutlineCoordinate(wall.start.x)}, ${formatOutlineCoordinate(wall.start.y)} " +
        "-> ${formatOutlineCoordinate(wall.end.x)}, ${formatOutlineCoordinate(wall.end.y)}"

private fun formatOutlineCoordinate(value: Double): String =
    String.format(Locale.US, "%.2f", value)
