package com.tabletopcontrol.light.ui

import com.tabletopcontrol.light.LightSerialCoordinator
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox

internal class LightDebugConsoleSection(
    private val serialCoordinator: LightSerialCoordinator,
) {
    fun createSection(): LightSection {
        val enabledCheck = CheckBox("Show sent serial commands (debug)").apply {
            isSelected = serialCoordinator.isDebugLoggingEnabled()
            tooltip = Tooltip("When enabled, logs each JSON command sent to WLED")
            selectedProperty().addListener { _, _, enabled ->
                serialCoordinator.setDebugLoggingEnabled(enabled)
            }
        }
        val clearBtn = Button("Clear").apply {
            tooltip = Tooltip("Clear the debug command console")
        }
        val console = TextArea().apply {
            isEditable = false
            isWrapText = false
            prefRowCount = 6
            promptText = "Sent serial commands appear here when debug logging is enabled."
        }

        clearBtn.setOnAction { console.clear() }

        val removeDebugListener = serialCoordinator.addDebugListener { line ->
            console.appendText("$line\n")
        }

        val topRow = HBox(6.0, enabledCheck, clearBtn).apply {
            alignment = Pos.CENTER_LEFT
        }

        return LightSection(
            node = VBox(
                4.0,
                Label("Serial Debug Console"),
                topRow,
                console,
            ),
            dispose = removeDebugListener,
        )
    }
}
