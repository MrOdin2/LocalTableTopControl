package com.tabletopcontrol.new_tracker.ui

import com.tabletopcontrol.core.ui.InputHelpers.Companion.allowOnlyNonNegativeIntegers
import com.tabletopcontrol.core.ui.dialog.DialogFlows
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.Effect
import com.tabletopcontrol.new_tracker.model.EffectLibrary
import com.tabletopcontrol.new_tracker.model.EffectTemplate
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Window
import java.util.UUID

/** Lets the DM add, edit, and remove the conditions currently affecting one actor. */
class ActorEffectsDialog {

    fun show(
        owner: Window?,
        actor: Actor,
    ): List<Effect>? {
        val workingEffects = actor.effects.toMutableList()
        var editingEffectId: String? = null

        val activeEffects = VBox(6.0)
        val libraryBox = ComboBox<EffectTemplate>().apply {
            items.addAll(EffectLibrary.commonConditions)
            promptText = "Built-in condition"
            maxWidth = Double.MAX_VALUE
        }
        val nameField = TextField().apply { promptText = "Effect name" }
        val iconField = TextField().apply {
            promptText = "Icon or short marker"
            prefColumnCount = 8
        }
        val durationField = TextField().apply {
            promptText = "Rounds (blank = until removed)"
            allowOnlyNonNegativeIntegers()
        }
        val descriptionArea = TextArea().apply {
            promptText = "Optional DM note or description"
            prefRowCount = 2
            isWrapText = true
        }
        val visibleToPlayers = CheckBox("Show on the player-facing map").apply {
            isSelected = true
        }
        val validationLabel = Label().apply {
            style = "-fx-text-fill: -tc-text-muted;"
            isVisible = false
            isManaged = false
        }
        val saveEffectButton = Button("Add effect")

        fun clearForm() {
            editingEffectId = null
            libraryBox.selectionModel.clearSelection()
            nameField.clear()
            iconField.clear()
            durationField.clear()
            descriptionArea.clear()
            visibleToPlayers.isSelected = true
            saveEffectButton.text = "Add effect"
            validationLabel.isVisible = false
            validationLabel.isManaged = false
        }

        fun loadForm(effect: Effect) {
            editingEffectId = effect.id
            libraryBox.selectionModel.clearSelection()
            nameField.text = effect.name
            iconField.text = effect.icon.orEmpty()
            durationField.text = effect.durationRounds?.toString().orEmpty()
            descriptionArea.text = effect.description.orEmpty()
            visibleToPlayers.isSelected = effect.visibleToPlayers
            saveEffectButton.text = "Update effect"
            validationLabel.isVisible = false
            validationLabel.isManaged = false
        }

        fun rebuildEffects() {
            activeEffects.children.clear()
            if (workingEffects.isEmpty()) {
                activeEffects.children += Label("No active effects.").apply {
                    style = "-fx-text-fill: -tc-text-muted;"
                }
            } else {
                activeEffects.children.addAll(
                    workingEffects.map { effect ->
                        val detail = buildString {
                            append(effect.badgeText)
                            append(" — ")
                            append(effect.durationLabel)
                            if (!effect.visibleToPlayers) append(" — DM only")
                        }
                        HBox(
                            8.0,
                            Label(detail).apply {
                                maxWidth = Double.MAX_VALUE
                                tooltip = Tooltip(effect.description ?: detail)
                            },
                            Button("Edit").apply {
                                setOnAction { loadForm(effect) }
                            },
                            Button("Remove").apply {
                                setOnAction {
                                    workingEffects.removeAll { it.id == effect.id }
                                    if (editingEffectId == effect.id) clearForm()
                                    rebuildEffects()
                                }
                            },
                        ).apply {
                            alignment = Pos.CENTER_LEFT
                            HBox.setHgrow(children[0], Priority.ALWAYS)
                        }
                    },
                )
            }
        }

        libraryBox.valueProperty().addListener { _, _, template ->
            if (template == null) return@addListener
            val effect = template.createEffect()
            nameField.text = effect.name
            iconField.text = effect.icon.orEmpty()
            descriptionArea.text = effect.description.orEmpty()
            visibleToPlayers.isSelected = effect.visibleToPlayers
        }

        saveEffectButton.setOnAction {
            val name = nameField.text.trim()
            val duration = durationField.text.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
            if (name.isEmpty() || durationField.text.isNotBlank() && (duration == null || duration <= 0)) {
                validationLabel.text = "Enter an effect name and a positive duration, or leave duration blank."
                validationLabel.isVisible = true
                validationLabel.isManaged = true
                return@setOnAction
            }
            val effect = Effect(
                id = editingEffectId ?: UUID.randomUUID().toString(),
                name = name,
                icon = iconField.text.trim().takeIf(String::isNotEmpty),
                durationRounds = duration,
                description = descriptionArea.text.trim().takeIf(String::isNotEmpty),
                visibleToPlayers = visibleToPlayers.isSelected,
            )
            val existingIndex = workingEffects.indexOfFirst { it.id == effect.id }
            if (existingIndex >= 0) {
                workingEffects[existingIndex] = effect
            } else {
                workingEffects += effect
            }
            clearForm()
            rebuildEffects()
        }

        rebuildEffects()

        val content = VBox(
            10.0,
            Label(actor.name).apply { style = "-fx-font-weight: bold;" },
            Label("Active effects"),
            ScrollPane(activeEffects).apply {
                isFitToWidth = true
                hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
                prefViewportHeight = 160.0
                maxHeight = 180.0
            },
            Separator(),
            Label("Add or edit an effect").apply { style = "-fx-font-weight: bold;" },
            libraryBox,
            HBox(8.0, nameField, iconField).apply {
                HBox.setHgrow(nameField, Priority.ALWAYS)
            },
            durationField,
            descriptionArea,
            visibleToPlayers,
            HBox(8.0, saveEffectButton, validationLabel).apply {
                alignment = Pos.CENTER_LEFT
            },
        ).apply {
            padding = Insets(8.0)
            prefWidth = DIALOG_CONTENT_WIDTH
        }

        return DialogFlows.showResultDialog(
            owner = owner,
            title = "Actor Effects",
            headerText = "Manage conditions for ${actor.name}",
            content = content,
            buttonTypes = listOf(ButtonType.OK, ButtonType.CANCEL),
        ) { button ->
            DialogFlows.resultForButton(button) { workingEffects.toList() }
        }
    }

    private companion object {
        const val DIALOG_CONTENT_WIDTH = 420.0
    }
}
