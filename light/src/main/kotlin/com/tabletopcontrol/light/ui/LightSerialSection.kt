package com.tabletopcontrol.light.ui

import com.tabletopcontrol.light.LightOperationResult
import com.tabletopcontrol.light.LightSerialCoordinator
import com.tabletopcontrol.light.WledSerialSender
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

internal class LightSerialSection(
    private val serialCoordinator: LightSerialCoordinator,
    private val feedback: LightOperatorFeedbackPresenter,
) {
    fun createSection(): LightSection {
        val statusLabel = Label()
        val portCombo = ComboBox<String>().apply {
            tooltip = Tooltip("Select the serial port for the WLED device")
            maxWidth = Double.MAX_VALUE
            isEditable = true
            items.setAll(serialCoordinator.availablePorts())
        }
        val baudField = TextField(WledSerialSender.DEFAULT_BAUD_RATE.toString()).apply {
            prefColumnCount = 8
            tooltip = Tooltip("Serial baud rate (WLED default: 115200)")
        }
        val connectBtn = Button().apply {
            tooltip = Tooltip("Open the selected serial port")
            maxWidth = Double.MAX_VALUE
        }
        val refreshBtn = Button("Refresh").apply {
            tooltip = Tooltip("Refresh the list of available serial ports")
        }

        fun syncConnectedState() {
            if (serialCoordinator.isConnected) {
                connectBtn.text = "Disconnect"
                val portName = serialCoordinator.connectedPortName
                feedback.showSuccess(
                    statusLabel,
                    portName?.let { "Connected: $it" } ?: "Connected",
                )
            } else {
                connectBtn.text = "Connect"
                feedback.showInfo(statusLabel, "Not connected")
            }
        }

        connectBtn.setOnAction {
            if (serialCoordinator.isConnected) {
                connectBtn.isDisable = true
                feedback.showInfo(statusLabel, "Disconnecting...")
                serialCoordinator.disconnectAsync { result ->
                    connectBtn.isDisable = false
                    when (result) {
                        LightOperationResult.Applied -> {
                            connectBtn.text = "Connect"
                            feedback.showInfo(statusLabel, "Disconnected")
                        }
                        is LightOperationResult.Failure -> {
                            syncConnectedState()
                            feedback.showError(statusLabel, result.operatorMessage)
                            feedback.showFailureDialog(
                                owner = statusLabel.scene?.window,
                                title = "Light Serial Error",
                                failure = result,
                            )
                        }
                    }
                }
            } else {
                connectBtn.isDisable = true
                feedback.showInfo(statusLabel, "Connecting...")
                val portName = if (portCombo.isEditable) portCombo.editor.text else portCombo.value
                serialCoordinator.connectAsync(portName, baudField.text) { result ->
                    connectBtn.isDisable = false
                    when (result) {
                        LightOperationResult.Applied -> syncConnectedState()
                        is LightOperationResult.Failure -> {
                            connectBtn.text = "Connect"
                            feedback.showError(statusLabel, result.operatorMessage)
                            feedback.showFailureDialog(
                                owner = statusLabel.scene?.window,
                                title = "Light Serial Error",
                                failure = result,
                            )
                        }
                    }
                }
            }
        }

        refreshBtn.setOnAction {
            portCombo.items.setAll(serialCoordinator.availablePorts())
        }

        val removeFailureListener = serialCoordinator.addFailureListener { failure ->
            feedback.showError(statusLabel, failure.operatorMessage)
            feedback.showFailureDialog(
                owner = statusLabel.scene?.window,
                title = "Light Serial Error",
                failure = failure,
            )
        }

        syncConnectedState()

        val portRow = HBox(6.0, portCombo, refreshBtn).apply {
            HBox.setHgrow(portCombo, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }
        val baudRow = HBox(6.0, Label("Baud:"), baudField).apply {
            alignment = Pos.CENTER_LEFT
        }
        val buttonRow = HBox(6.0, connectBtn).apply {
            HBox.setHgrow(connectBtn, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }

        return LightSection(
            node = VBox(
                4.0,
                Label("WLED Serial Connection"),
                portRow,
                baudRow,
                buttonRow,
                statusLabel,
            ),
            dispose = removeFailureListener,
        )
    }
}
