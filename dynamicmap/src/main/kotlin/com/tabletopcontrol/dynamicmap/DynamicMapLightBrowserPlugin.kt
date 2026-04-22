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
        val selectedLabel = Label("Selected preset: ${DynamicMapLightPresets.defaultPreset.displayName}")

        val content = VBox(8.0).apply {
            padding = Insets(8.0)
            style = "-fx-background-color: -tc-bg;"
        }
        content.children += Label("Point light presets")
        content.children += selectedLabel
        content.children += Separator()

        DynamicMapLightPresets.presets.forEach { preset ->
            val swatchText = ColorContrast.textColorHexForBackground(ColorHexCodec.hexToColor(preset.colorHex))
            val useButton = Button("Use").apply {
                style = "-fx-background-color: ${preset.colorHex}; -fx-text-fill: $swatchText;"
                setOnAction {
                    EventBus.publish(DynamicMapLightPresetSelectedEvent(preset.id))
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
        content.children += Label(
            "Builder notes: this first version places static point lights. " +
                "Moving lights with tokens and browser-managed asset libraries come next.",
        ).apply {
            isWrapText = true
            style = "-fx-text-fill: -tc-text-muted;"
        }

        val presetSubscription = EventBus.subscribe<DynamicMapLightPresetSelectedEvent> { event ->
            val preset = DynamicMapLightPresets.byId(event.presetId)
            selectedLabel.text = "Selected preset: ${preset.displayName}"
        }

        return ScrollPane(content).apply {
            isFitToWidth = true
            isPannable = true
            style = "-fx-background: -tc-bg;"
            sceneProperty().addListener { _, _, newScene ->
                if (newScene == null) {
                    presetSubscription.unsubscribe()
                }
            }
        }
    }
}
