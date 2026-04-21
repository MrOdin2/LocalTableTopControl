package com.tabletopcontrol.light.ui

import com.tabletopcontrol.light.LightController
import com.tabletopcontrol.light.LightOperationResult
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

internal class LightPresetSection(
    private val controller: LightController,
    private val feedback: LightOperatorFeedbackPresenter,
) {
    fun createSection(): LightSection {
        val activeLabel = Label().apply {
            showPresetState(this)
        }

        val presetField = TextField().apply {
            promptText = "Preset ID (1-250)"
            prefColumnCount = 10
            tooltip = Tooltip("Enter a WLED preset ID (1-250) to activate it on the device")
            maxWidth = Double.MAX_VALUE
        }

        fun applyPreset() {
            when (val result = controller.applyPresetInput(presetField.text)) {
                LightOperationResult.Applied ->
                    feedback.showSuccess(
                        activeLabel,
                        "Active preset: ${controller.preset}",
                    )
                is LightOperationResult.Failure ->
                    feedback.showError(activeLabel, result.operatorMessage)
            }
        }

        val applyBtn = Button("Apply Preset").apply {
            tooltip = Tooltip("Activate the entered preset on the WLED device")
            maxWidth = Double.MAX_VALUE
            setOnAction { applyPreset() }
        }

        val clearBtn = Button("Clear").apply {
            tooltip = Tooltip("Clear the active preset and return to manual control")
            setOnAction {
                controller.setPreset(null)
                presetField.clear()
                feedback.showInfo(activeLabel, "No preset active")
            }
        }

        presetField.setOnAction { applyPreset() }

        val inputRow = HBox(6.0, presetField, applyBtn, clearBtn).apply {
            HBox.setHgrow(presetField, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }

        return LightSection(
            VBox(
                4.0,
                Label("WLED Preset"),
                inputRow,
                activeLabel,
            ),
        )
    }

    private fun showPresetState(label: Label) {
        val preset = controller.preset
        if (preset == null) {
            feedback.showInfo(label, "No preset active")
        } else {
            feedback.showSuccess(label, "Active preset: $preset")
        }
    }
}
