package com.tabletopcontrol.dynamicmap

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.DmWorkspaceId
import com.tabletopcontrol.core.EventBus
import javafx.collections.ListChangeListener
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.SelectionMode
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
        var selections = emptySet<DynamicMapElementSelection>()
        var selectedGroupIds = emptySet<String>()
        var isApplyingSelection = false
        var itemBySelection = emptyMap<DynamicMapElementSelection, TreeItem<DynamicMapOutlineNode>>()
        var itemByGroupId = emptyMap<String, TreeItem<DynamicMapOutlineNode>>()

        val summaryLabel = Label().apply {
            style = "-fx-text-fill: -tc-text-muted;"
        }
        val tree = TreeView<DynamicMapOutlineNode>().apply {
            isShowRoot = false
            style = "-fx-background-color: -tc-surface;"
            selectionModel.selectionMode = SelectionMode.MULTIPLE
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
                            is DynamicMapOutlineUserGroupNode -> "-fx-font-weight: bold; -fx-text-fill: -tc-accent;"
                            is DynamicMapOutlineHintNode -> "-fx-text-fill: -tc-text-muted;"
                            is DynamicMapOutlineElementNode -> ""
                        }
                    }
                }
            }
        }
        val createGroupButton = Button("Group Selected")
        val removeGroupButton = Button("Remove Group")
        val removeSelectedButton = Button("Remove Selected")
        val toggleLightButton = Button("Disable Light")
        val clearSelectionButton = Button("Clear Selection")
        val footerLabel = Label(
            "The outline shows every group, wall, and light in the current draft. Selecting a group selects all elements linked to it.",
        ).apply {
            isWrapText = true
            style = "-fx-text-fill: -tc-text-muted;"
        }

        fun selectedLight(): DynamicMapLight? =
            selections
                .singleOrNull()
                ?.takeIf { it.kind == DynamicMapElementKind.LIGHT }
                ?.let { document.lightById(it.elementId) }

        fun refreshActionButtons() {
            createGroupButton.isDisable = selections.isEmpty()
            removeGroupButton.isDisable = selectedGroupIds.isEmpty()
            removeGroupButton.text = if (selectedGroupIds.size > 1) "Remove Groups" else "Remove Group"
            removeSelectedButton.isDisable = selections.isEmpty()
            val light = selectedLight()
            toggleLightButton.isDisable = light == null
            toggleLightButton.text = if (light?.enabled == false) "Enable Light" else "Disable Light"
        }

        fun syncTreeSelection() {
            isApplyingSelection = true
            tree.selectionModel.clearSelection()
            selectedGroupIds = selectedGroupIds.filterTo(linkedSetOf()) { document.groupById(it) != null }
            val groupedSelections = selectedGroupIds
                .flatMap { document.groupById(it)?.elements.orEmpty() }
                .toSet()
            val individualSelections = selections.filterTo(linkedSetOf()) { it !in groupedSelections }
            val treeSelections = selectedGroupIds.mapNotNull(itemByGroupId::get) +
                individualSelections.mapNotNull(itemBySelection::get)
            treeSelections.forEach { tree.selectionModel.select(it) }
            treeSelections.lastOrNull()?.let { lastSelection ->
                val row = tree.getRow(lastSelection)
                if (row >= 0) {
                    tree.scrollTo(row)
                }
            }
            isApplyingSelection = false
            refreshActionButtons()
        }

        fun updateSelectionFromTree() {
            val selectedNodes = tree.selectionModel.selectedItems.map { it.value }
            val treeSelectedGroupIds = selectedNodes
                .mapNotNull { it as? DynamicMapOutlineUserGroupNode }
                .mapTo(linkedSetOf()) { it.groupId }
            val groupSelections = treeSelectedGroupIds.flatMap { groupId ->
                document.groupById(groupId)?.elements.orEmpty()
            }
            val elementSelections = selectedNodes
                .mapNotNull { it as? DynamicMapOutlineElementNode }
                .map { it.selection }
            val selectedOutlineItems = (groupSelections + elementSelections)
                .filterTo(linkedSetOf()) { document.containsSelection(it) }
            val selectionChanged = selectedOutlineItems != selections
            val groupSelectionChanged = treeSelectedGroupIds != selectedGroupIds
            if (!selectionChanged && !groupSelectionChanged) return

            selections = selectedOutlineItems
            selectedGroupIds = treeSelectedGroupIds
            refreshActionButtons()
            if (selectionChanged) {
                EventBus.publish(DynamicMapSelectionChangedEvent(selectedOutlineItems))
            }
        }

        fun rebuildTree() {
            val root = TreeItem<DynamicMapOutlineNode>(DynamicMapOutlineGroupNode("Dynamic Map Draft")).apply {
                isExpanded = true
            }
            val index = linkedMapOf<DynamicMapElementSelection, TreeItem<DynamicMapOutlineNode>>()
            val groupIndex = linkedMapOf<String, TreeItem<DynamicMapOutlineNode>>()

            val groupsNode = TreeItem<DynamicMapOutlineNode>(
                DynamicMapOutlineGroupNode("Groups (${document.groups.size})"),
            ).apply { isExpanded = true }
            if (document.groups.isEmpty()) {
                groupsNode.children += TreeItem<DynamicMapOutlineNode>(
                    DynamicMapOutlineHintNode("No groups created yet"),
                )
            } else {
                document.groups.forEach { group ->
                    val groupSelections = document.filterExistingSelections(group.elements)
                    val groupItem = TreeItem<DynamicMapOutlineNode>(
                        DynamicMapOutlineUserGroupNode(
                            groupId = group.id,
                            label = "${group.label} (${groupSelections.size})",
                        ),
                    ).apply {
                        isExpanded = true
                    }
                    groupSelections.forEach { selection ->
                        buildGroupMemberOutlineNode(selection, document)?.let { memberNode ->
                            groupItem.children += TreeItem<DynamicMapOutlineNode>(memberNode)
                        }
                    }
                    groupsNode.children += groupItem
                    groupIndex[group.id] = groupItem
                }
            }

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

            root.children.setAll(groupsNode, lightsNode, wallsNode)
            itemBySelection = index
            itemByGroupId = groupIndex
            selectedGroupIds = selectedGroupIds.filterTo(linkedSetOf()) { document.groupById(it) != null }
            isApplyingSelection = true
            tree.root = root
            isApplyingSelection = false
            summaryLabel.text =
                "Groups: ${document.groups.size} | Lights: ${document.lights.count { it.enabled }}/${document.lights.size} enabled | Walls: ${document.walls.size}"
            val existingSelections = document.filterExistingSelections(selections)
            if (existingSelections != selections) {
                EventBus.publish(DynamicMapSelectionChangedEvent(existingSelections))
            } else {
                syncTreeSelection()
            }
        }

        tree.selectionModel.selectedItems.addListener(
            ListChangeListener<TreeItem<DynamicMapOutlineNode>> { change ->
                if (!isApplyingSelection) {
                    while (change.next()) {
                        // Advance the JavaFX change object so selectedItems reflects the final state.
                    }
                    updateSelectionFromTree()
                }
            },
        )

        createGroupButton.setOnAction {
            val currentSelections = selections
            if (currentSelections.isEmpty()) return@setOnAction
            EventBus.publish(DynamicMapGroupCreationRequestedEvent(currentSelections))
        }
        removeGroupButton.setOnAction {
            val currentGroupIds = selectedGroupIds
            if (currentGroupIds.isEmpty()) return@setOnAction
            selectedGroupIds = emptySet()
            refreshActionButtons()
            EventBus.publish(DynamicMapGroupRemovalRequestedEvent(currentGroupIds))
        }
        removeSelectedButton.setOnAction {
            val currentSelections = selections
            if (currentSelections.isEmpty()) return@setOnAction
            currentSelections.forEach { selection ->
                EventBus.publish(DynamicMapElementRemovalRequestedEvent(selection))
            }
            EventBus.publish(DynamicMapSelectionChangedEvent(emptySet()))
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
            EventBus.publish(DynamicMapSelectionChangedEvent(emptySet()))
        }

        val documentSubscription = EventBus.subscribe<DynamicMapDocumentChangedEvent> { event ->
            document = event.document
            rebuildTree()
        }
        val selectionSubscription = EventBus.subscribe<DynamicMapSelectionChangedEvent> { event ->
            val existingSelections = document.filterExistingSelections(event.selections)
            if (existingSelections != selections) {
                selections = existingSelections
                selectedGroupIds = emptySet()
                syncTreeSelection()
            } else {
                refreshActionButtons()
            }
        }

        refreshActionButtons()
        EventBus.publish(DynamicMapDocumentSnapshotRequestedEvent)

        return VBox(
            8.0,
            Label("Builder outline"),
            summaryLabel,
            tree,
            createGroupButton,
            removeGroupButton,
            removeSelectedButton,
            toggleLightButton,
            clearSelectionButton,
            footerLabel,
        ).apply {
            padding = Insets(8.0)
            style = "-fx-background-color: -tc-bg;"
            VBox.setVgrow(tree, Priority.ALWAYS)
            createGroupButton.maxWidth = Double.MAX_VALUE
            removeGroupButton.maxWidth = Double.MAX_VALUE
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

private data class DynamicMapOutlineUserGroupNode(
    val groupId: String,
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

private fun buildGroupMemberOutlineNode(
    selection: DynamicMapElementSelection,
    document: DynamicMapDocument,
): DynamicMapOutlineElementNode? {
    val label = when (selection.kind) {
        DynamicMapElementKind.LIGHT -> document.lightById(selection.elementId)?.let { light ->
            val state = if (light.enabled) "" else "[Off] "
            "Light: ${state}${light.label} @ ${formatOutlineCoordinate(light.position.x)}, " +
                formatOutlineCoordinate(light.position.y)
        }
        DynamicMapElementKind.WALL -> document.wallById(selection.elementId)?.let { wall ->
            "Wall: ${formatOutlineCoordinate(wall.start.x)}, ${formatOutlineCoordinate(wall.start.y)} " +
                "-> ${formatOutlineCoordinate(wall.end.x)}, ${formatOutlineCoordinate(wall.end.y)}"
        }
    } ?: return null

    return DynamicMapOutlineElementNode(selection = selection, label = label)
}

private fun formatOutlineCoordinate(value: Double): String =
    String.format(Locale.US, "%.2f", value)
