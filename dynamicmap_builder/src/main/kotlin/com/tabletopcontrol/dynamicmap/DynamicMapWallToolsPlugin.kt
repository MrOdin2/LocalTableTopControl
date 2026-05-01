package com.tabletopcontrol.dynamicmap

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.DmWorkspaceId
import com.tabletopcontrol.core.EventBus
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

class DynamicMapWallToolsPlugin : DmPlugin {
    override val displayName: String = "Wall Tools"

    override val workspaceIds: Set<DmWorkspaceId> = setOf(DmWorkspaceId.DYNAMIC_MAP_BUILDER)

    override fun createView(): Node {
        var activeTool: DynamicMapTool? = null
        var selectedWallKind: DynamicMapWallKind = DynamicMapWallKind.SOFT
        var selectedDoorVisible: Boolean = true

        val modeLabel = Label("Active tool: none").apply {
            style = "-fx-text-fill: -tc-text-muted;"
        }
        val softWallButton = ToggleButton("Soft")
        val hardWallButton = ToggleButton("Hard")
        val doorButton = ToggleButton("Door")
        val visibleDoorButton = ToggleButton("Visible")
        val hiddenDoorButton = ToggleButton("Hidden")
        val lineButton = ToggleButton("Line")
        val rectButton = ToggleButton("Rect")
        val stopButton = Button("Stop Editing")
        val optimizeWallsButton = Button("Optimize Walls")
        val clearWallsButton = Button("Clear All Walls")
        val wallKindGroup = ToggleGroup()
        val doorVisibilityGroup = ToggleGroup()
        val drawModeGroup = ToggleGroup()

        softWallButton.toggleGroup = wallKindGroup
        hardWallButton.toggleGroup = wallKindGroup
        doorButton.toggleGroup = wallKindGroup
        visibleDoorButton.toggleGroup = doorVisibilityGroup
        hiddenDoorButton.toggleGroup = doorVisibilityGroup
        lineButton.toggleGroup = drawModeGroup
        rectButton.toggleGroup = drawModeGroup

        fun refreshToolUi() {
            softWallButton.isSelected = selectedWallKind == DynamicMapWallKind.SOFT
            hardWallButton.isSelected = selectedWallKind == DynamicMapWallKind.HARD
            doorButton.isSelected = selectedWallKind == DynamicMapWallKind.DOOR
            visibleDoorButton.isSelected = selectedDoorVisible
            hiddenDoorButton.isSelected = !selectedDoorVisible
            visibleDoorButton.isDisable = selectedWallKind != DynamicMapWallKind.DOOR
            hiddenDoorButton.isDisable = selectedWallKind != DynamicMapWallKind.DOOR
            lineButton.isSelected = activeTool == DynamicMapTool.WALL_LINE
            rectButton.isSelected = activeTool == DynamicMapTool.WALL_RECT
            modeLabel.text = when (activeTool) {
                DynamicMapTool.WALL_LINE -> "Active tool: ${selectedWallKind.toolText(selectedDoorVisible)} line"
                DynamicMapTool.WALL_RECT -> "Active tool: ${selectedWallKind.toolText(selectedDoorVisible)} rectangle"
                DynamicMapTool.LIGHT -> "Active tool: light placement"
                DynamicMapTool.SUNLIGHT_AREA -> "Active tool: sunlight area"
                null -> "Active tool: none"
            }
        }

        softWallButton.setOnAction {
            selectedWallKind = DynamicMapWallKind.SOFT
            EventBus.publish(DynamicMapWallKindSelectedEvent(selectedWallKind))
            refreshToolUi()
        }
        hardWallButton.setOnAction {
            selectedWallKind = DynamicMapWallKind.HARD
            EventBus.publish(DynamicMapWallKindSelectedEvent(selectedWallKind))
            refreshToolUi()
        }
        doorButton.setOnAction {
            selectedWallKind = DynamicMapWallKind.DOOR
            EventBus.publish(DynamicMapWallKindSelectedEvent(selectedWallKind))
            refreshToolUi()
        }
        visibleDoorButton.setOnAction {
            selectedDoorVisible = true
            EventBus.publish(DynamicMapDoorVisibilitySelectedEvent(visible = selectedDoorVisible))
            refreshToolUi()
        }
        hiddenDoorButton.setOnAction {
            selectedDoorVisible = false
            EventBus.publish(DynamicMapDoorVisibilitySelectedEvent(visible = selectedDoorVisible))
            refreshToolUi()
        }
        lineButton.setOnAction {
            EventBus.publish(
                DynamicMapToolSelectedEvent(
                    tool = if (lineButton.isSelected) DynamicMapTool.WALL_LINE else null,
                ),
            )
        }
        rectButton.setOnAction {
            EventBus.publish(
                DynamicMapToolSelectedEvent(
                    tool = if (rectButton.isSelected) DynamicMapTool.WALL_RECT else null,
                ),
            )
        }
        stopButton.setOnAction {
            EventBus.publish(DynamicMapToolSelectedEvent(tool = null))
        }
        optimizeWallsButton.setOnAction {
            EventBus.publish(DynamicMapOptimizeWallsRequestedEvent)
        }
        clearWallsButton.setOnAction {
            EventBus.publish(DynamicMapClearWallsRequestedEvent)
        }

        val toolSubscription = EventBus.subscribe<DynamicMapToolSelectedEvent> { event ->
            activeTool = event.tool
            refreshToolUi()
        }
        val wallKindSubscription = EventBus.subscribe<DynamicMapWallKindSelectedEvent> { event ->
            selectedWallKind = event.kind
            refreshToolUi()
        }
        val doorVisibilitySubscription = EventBus.subscribe<DynamicMapDoorVisibilitySelectedEvent> { event ->
            selectedDoorVisible = event.visible
            refreshToolUi()
        }

        refreshToolUi()

        return VBox(
            8.0,
            Label("Wall drawing"),
            modeLabel,
            Separator(),
            HBox(6.0, Label("Type:"), softWallButton, hardWallButton, doorButton),
            HBox(6.0, Label("Door:"), visibleDoorButton, hiddenDoorButton),
            HBox(6.0, Label("Draw:"), lineButton, rectButton),
            stopButton,
            optimizeWallsButton,
            Region().also { VBox.setVgrow(it, Priority.ALWAYS) },
            Separator(),
            clearWallsButton,
        ).apply {
            padding = Insets(8.0)
            style = "-fx-background-color: -tc-bg;"
            sceneProperty().addListener { _, _, newScene ->
                if (newScene == null) {
                    toolSubscription.unsubscribe()
                    wallKindSubscription.unsubscribe()
                    doorVisibilitySubscription.unsubscribe()
                }
            }
        }
    }
}

private fun DynamicMapWallKind.toolText(doorVisible: Boolean): String =
    when (this) {
        DynamicMapWallKind.DOOR -> if (doorVisible) "visible door" else "hidden door"
        else -> displayName.lowercase()
    }
