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
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

class DynamicMapWallToolsPlugin : DmPlugin {
    override val displayName: String = "Wall Tools"

    override val workspaceIds: Set<DmWorkspaceId> = setOf(DmWorkspaceId.DYNAMIC_MAP_BUILDER)

    override fun createView(): Node {
        var activeTool: DynamicMapTool? = null

        val modeLabel = Label("Active tool: none").apply {
            style = "-fx-text-fill: -tc-text-muted;"
        }
        val lineButton = ToggleButton("Wall Line")
        val rectButton = ToggleButton("Wall Rect")
        val stopButton = Button("Stop Editing")
        val optimizeWallsButton = Button("Optimize Walls")
        val clearWallsButton = Button("Clear All Walls")

        fun refreshToolUi() {
            lineButton.isSelected = activeTool == DynamicMapTool.WALL_LINE
            rectButton.isSelected = activeTool == DynamicMapTool.WALL_RECT
            modeLabel.text = when (activeTool) {
                DynamicMapTool.WALL_LINE -> "Active tool: wall line"
                DynamicMapTool.WALL_RECT -> "Active tool: wall rectangle"
                DynamicMapTool.LIGHT -> "Active tool: light placement"
                DynamicMapTool.SUNLIGHT_AREA -> "Active tool: sunlight area"
                null -> "Active tool: none"
            }
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

        refreshToolUi()

        return VBox(
            8.0,
            Label("Wall drawing"),
            modeLabel,
            Separator(),
            lineButton,
            rectButton,
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
                }
            }
        }
    }
}
