package com.tabletopcontrol.map

import com.tabletopcontrol.core.ui.dialog.DialogFlows
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import javafx.stage.Window

object MapMeasurementDialogs {
    fun showUnitsDialog(
        owner: Window?,
        currentUnits: String,
        supportedUnits: List<String>,
    ): String? {
        val unitsBox = ComboBox<String>().apply {
            items.addAll(supportedUnits)
            selectionModel.select(currentUnits)
        }
        val content = VBox(
            8.0,
            Label("Units:"),
            unitsBox,
        )
        return DialogFlows.showResultDialog(
            owner = owner,
            title = "Measurement Units",
            headerText = "Choose the default units for new measurements.",
            content = content,
        ) { button ->
            DialogFlows.resultForButton(button) {
                unitsBox.selectionModel.selectedItem ?: currentUnits
            }
        }
    }

    fun showLabelDialog(owner: Window?, currentLabel: String): String? {
        val labelField = TextField(currentLabel).apply {
            promptText = "Optional label"
        }
        val content = VBox(
            8.0,
            Label("Label:"),
            labelField,
        )
        return DialogFlows.showResultDialog(
            owner = owner,
            title = "Measurement Label",
            headerText = "Set an optional label prefix for the measurement.",
            content = content,
        ) { button ->
            DialogFlows.resultForButton(button) {
                labelField.text.trim()
            }
        }
    }
}
