package com.tabletopcontrol.new_tracker.ui

import com.tabletopcontrol.core.ui.InputHelpers.Companion.allowOnlyNonNegativeIntegers
import com.tabletopcontrol.core.ui.dialog.DialogFlows
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorFeatures
import com.tabletopcontrol.new_tracker.model.DistanceRange
import com.tabletopcontrol.new_tracker.model.DistanceUnit
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Window

class ActorFeaturesDialog {

    fun show(
        owner: Window?,
        actor: Actor,
    ): ActorFeatures? {
        val initial = actor.features

        val darkvisionEnabled = CheckBox("Darkvision").apply {
            isSelected = initial.darkvisionRange != null
        }
        val darkvisionRangeField = rangeField(
            initial.darkvisionRange?.amount,
            DEFAULT_DARKVISION_RANGE,
        )
        val darkvisionUnitBox = unitBox(initial.darkvisionRange?.unit ?: DistanceUnit.FEET)

        val movementRangeField = rangeField(
            initial.movementRange?.amount,
            DEFAULT_MOVEMENT_RANGE,
        )
        val movementUnitBox = unitBox(initial.movementRange?.unit ?: DistanceUnit.FEET)

        fun syncDarkvisionInputs() {
            val enabled = darkvisionEnabled.isSelected
            darkvisionRangeField.isDisable = !enabled
            darkvisionUnitBox.isDisable = !enabled
        }
        darkvisionEnabled.selectedProperty().addListener { _, _, _ -> syncDarkvisionInputs() }
        syncDarkvisionInputs()

        val content = VBox(
            10.0,
            Label(actor.name).apply {
                isWrapText = true
                style = "-fx-font-weight: bold;"
            },
            VBox(
                6.0,
                darkvisionEnabled,
                HBox(
                    8.0,
                    Label("Range").apply { minWidth = LABEL_WIDTH },
                    darkvisionRangeField,
                    darkvisionUnitBox,
                ).apply {
                    alignment = Pos.CENTER_LEFT
                    HBox.setHgrow(darkvisionUnitBox, Priority.ALWAYS)
                },
            ),
            VBox(
                6.0,
                Label("Movement").apply {
                    style = "-fx-font-weight: bold;"
                },
                HBox(
                    8.0,
                    Label("Range").apply { minWidth = LABEL_WIDTH },
                    movementRangeField,
                    movementUnitBox,
                ).apply {
                    alignment = Pos.CENTER_LEFT
                    HBox.setHgrow(movementUnitBox, Priority.ALWAYS)
                },
            ),
        ).apply {
            padding = Insets(8.0)
            prefWidth = DIALOG_CONTENT_WIDTH
        }

        return DialogFlows.showResultDialog(
            owner = owner,
            title = "Actor Features",
            headerText = "Configure sight and movement",
            content = content,
            buttonTypes = listOf(ButtonType.OK, ButtonType.CANCEL),
        ) { button ->
            DialogFlows.resultForButton(button) {
                ActorFeatures(
                    darkvisionRange = if (darkvisionEnabled.isSelected) {
                        DistanceRange(
                            amount = darkvisionRangeField.text.toIntOrNull() ?: DEFAULT_DARKVISION_RANGE,
                            unit = darkvisionUnitBox.value ?: DistanceUnit.FEET,
                        )
                    } else {
                        null
                    },
                    movementRange = movementRangeField.text.toIntOrNull()?.let { amount ->
                        DistanceRange(
                            amount = amount,
                            unit = movementUnitBox.value ?: DistanceUnit.FEET,
                        )
                    },
                )
            }
        }
    }

    private fun rangeField(
        value: Int?,
        defaultValue: Int,
    ): TextField =
        TextField(value?.toString().orEmpty()).apply {
            promptText = defaultValue.toString()
            prefColumnCount = 6
            allowOnlyNonNegativeIntegers()
        }

    private fun unitBox(initial: DistanceUnit): ComboBox<DistanceUnit> =
        ComboBox<DistanceUnit>().apply {
            items.addAll(DistanceUnit.entries)
            value = initial
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip("Distance unit")
        }

    private companion object {
        const val DEFAULT_DARKVISION_RANGE = 60
        const val DEFAULT_MOVEMENT_RANGE = 30
        const val DIALOG_CONTENT_WIDTH = 320.0
        const val LABEL_WIDTH = 56.0
    }
}
