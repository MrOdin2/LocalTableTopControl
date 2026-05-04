package com.tabletopcontrol.light.advanced.ui

import com.tabletopcontrol.light.LightOperationResult
import com.tabletopcontrol.light.advanced.AdvancedLightCoordinator
import com.tabletopcontrol.light.advanced.AdvancedWledSerial
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

/**
 * Collapsible connection settings panel for the Advanced Lighting plugin.
 *
 * The panel is collapsed by default so it takes minimal vertical space after
 * the initial connect.  It contains port picker, baud-rate field, and a
 * Connect / Disconnect button — identical in behaviour to the basic light plugin
 * but wrapped in a [TitledPane].
 */
internal class AdvConnectSection(
    private val coordinator: AdvancedLightCoordinator,
    private val statusLabel: Label,
    private val onConnected: () -> Unit,
) {

    fun create(): TitledPane {
        val portCombo = ComboBox<String>().apply {
            tooltip = Tooltip("Select the serial port for the WLED device")
            maxWidth = Double.MAX_VALUE
            isEditable = true
            items.setAll(coordinator.availablePorts())
        }
        val baudField = TextField(AdvancedWledSerial.DEFAULT_BAUD_RATE.toString()).apply {
            prefColumnCount = 8
            tooltip = Tooltip("Serial baud rate (WLED default: 115200)")
        }
        val connectBtn = Button().apply {
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip("Open or close the selected serial port")
        }
        val refreshBtn = Button("↻").apply {
            tooltip = Tooltip("Refresh the list of available serial ports")
        }

        fun syncState() {
            if (coordinator.isConnected) {
                connectBtn.text = "Disconnect"
                val pn = coordinator.connectedPortName
                statusLabel.text = pn?.let { "Connected: $it" } ?: "Connected"
                statusLabel.style = "-fx-text-fill: -tc-success;"
            } else {
                connectBtn.text = "Connect"
                statusLabel.text = "Not connected"
                statusLabel.style = "-fx-text-fill: -tc-text-muted;"
            }
        }

        connectBtn.setOnAction {
            if (coordinator.isConnected) {
                connectBtn.isDisable = true
                coordinator.disconnectAsync { result ->
                    connectBtn.isDisable = false
                    syncState()
                    if (result is LightOperationResult.Failure) {
                        statusLabel.text = (result as LightOperationResult.Failure).operatorMessage
                        statusLabel.style = "-fx-text-fill: -tc-error;"
                    }
                }
            } else {
                connectBtn.isDisable = true
                statusLabel.text = "Connecting…"
                statusLabel.style = "-fx-text-fill: -tc-text-muted;"
                val portName = if (portCombo.isEditable) portCombo.editor.text else portCombo.value
                coordinator.connectAsync(portName, baudField.text) { result ->
                    connectBtn.isDisable = false
                    when (result) {
                        LightOperationResult.Applied -> {
                            syncState()
                            onConnected()
                        }
                        is LightOperationResult.Failure -> {
                            syncState()
                            statusLabel.text = result.operatorMessage
                            statusLabel.style = "-fx-text-fill: -tc-error;"
                        }
                    }
                }
            }
        }

        refreshBtn.setOnAction {
            portCombo.items.setAll(coordinator.availablePorts())
        }

        syncState()

        val portRow = HBox(6.0, portCombo, refreshBtn).apply {
            HBox.setHgrow(portCombo, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }
        val baudRow = HBox(6.0, Label("Baud:"), baudField).apply {
            alignment = Pos.CENTER_LEFT
        }
        val connectRow = HBox(6.0, connectBtn).apply {
            HBox.setHgrow(connectBtn, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }

        val content = VBox(6.0, portRow, baudRow, connectRow).apply {
            padding = Insets(8.0)
        }

        return TitledPane("⚡ Serial Connection", content).apply {
            isExpanded = !coordinator.isConnected
            isAnimated = false
        }
    }
}
