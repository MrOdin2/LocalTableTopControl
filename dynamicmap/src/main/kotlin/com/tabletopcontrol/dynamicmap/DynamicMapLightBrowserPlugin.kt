package com.tabletopcontrol.dynamicmap

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.DmWorkspaceId
import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.ui.color.ColorContrast
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

class DynamicMapLightBrowserPlugin : DmPlugin {
    override val displayName: String = "Light Browser"

    override val workspaceIds: Set<DmWorkspaceId> = setOf(DmWorkspaceId.DYNAMIC_MAP_BUILDER)

    override fun createView(): Node {
        var activeTool: DynamicMapTool? = null
        val selectedLabel = Label("Selected preset: ${DynamicMapLightPresets.defaultPreset.displayName}")
        val modeLabel = Label("Placement mode: none").apply {
            style = "-fx-text-fill: -tc-text-muted;"
        }
        val stopPlacementButton = Button("Stop Placement")
        val clearLightsButton = Button("Clear All Lights")

        fun refreshModeLabel() {
            modeLabel.text = when (activeTool) {
                DynamicMapTool.LIGHT -> "Placement mode: light placement"
                DynamicMapTool.WALL_LINE -> "Placement mode: wall line"
                DynamicMapTool.WALL_RECT -> "Placement mode: wall rectangle"
                null -> "Placement mode: none"
            }
        }

        val content = VBox(8.0).apply {
            padding = Insets(8.0)
            style = "-fx-background-color: -tc-bg;"
        }
        content.children += Label("Point light presets")
        content.children += selectedLabel
        content.children += modeLabel
        content.children += Separator()

        DynamicMapLightPresets.presets.forEach { preset ->
            val swatchText = ColorContrast.textColorHexForBackground(ColorHexCodec.hexToColor(preset.colorHex))
            val useButton = Button("Use").apply {
                style = "-fx-background-color: ${preset.colorHex}; -fx-text-fill: $swatchText;"
                setOnAction {
                    EventBus.publish(DynamicMapLightPresetSelectedEvent(preset.id))
                    EventBus.publish(DynamicMapToolSelectedEvent(DynamicMapTool.LIGHT))
                    selectedLabel.text = "Selected preset: ${preset.displayName}"
                }
            }
            val description = VBox(
                2.0,
                Label("${preset.displayName} (${preset.brightRadius.toInt()} / ${preset.dimRadius.toInt()} tiles)"),
                Label(preset.description).apply { style = "-fx-text-fill: -tc-text-muted;" },
            )
            val row = HBox(8.0, useButton, description, Region().also { HBox.setHgrow(it, Priority.ALWAYS) }).apply {
                style = "-fx-background-color: -tc-surface; -fx-border-color: -tc-border; -fx-padding: 8;"
            }
            content.children += row
        }

        content.children += Separator()
        stopPlacementButton.setOnAction {
            EventBus.publish(DynamicMapToolSelectedEvent(tool = null))
        }
        clearLightsButton.setOnAction {
            EventBus.publish(DynamicMapClearLightsRequestedEvent)
        }
        content.children += stopPlacementButton
        content.children += clearLightsButton
        content.children += Separator()
        content.children += Label(
            "Selecting a preset immediately arms light placement in the main builder pane. " +
                "Static point lights are supported in this first version; moving lights with tokens and browser-managed asset libraries come next.",
        ).apply {
            isWrapText = true
            style = "-fx-text-fill: -tc-text-muted;"
        }

        val presetSubscription = EventBus.subscribe<DynamicMapLightPresetSelectedEvent> { event ->
            val preset = DynamicMapLightPresets.byId(event.presetId)
            selectedLabel.text = "Selected preset: ${preset.displayName}"
        }
        val toolSubscription = EventBus.subscribe<DynamicMapToolSelectedEvent> { event ->
            activeTool = event.tool
            refreshModeLabel()
        }

        refreshModeLabel()

        return ScrollPane(content).apply {
            isFitToWidth = true
            isPannable = true
            style = "-fx-background: -tc-bg;"
            sceneProperty().addListener { _, _, newScene ->
                if (newScene == null) {
                    presetSubscription.unsubscribe()
                    toolSubscription.unsubscribe()
                }
            }
        }
    }
}
