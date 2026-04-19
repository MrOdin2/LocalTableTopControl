package com.tabletopcontrol.new_tracker.ui

import com.tabletopcontrol.core.ui.dialog.DialogFlows
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.InitiativeTieDecision
import javafx.geometry.Insets
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import javafx.stage.Window

class InitiativeTieDialog {

    fun show(
        owner: Window?,
        actorToPlace: Actor,
        existingActor: Actor,
    ): InitiativeTieDecision? {
        val initiative = requireNotNull(actorToPlace.initiative) {
            "Initiative tie dialog requires a non-null initiative."
        }

        val content = VBox(
            8.0,
            Label("Both actors have initiative $initiative. Choose which card should come first.").apply {
                isWrapText = true
            },
            Label(actorSummary("Actor being placed", actorToPlace)).apply {
                isWrapText = true
            },
            Label(actorSummary("Actor already listed", existingActor)).apply {
                isWrapText = true
            },
        ).apply {
            padding = Insets(4.0, 0.0, 0.0, 0.0)
        }

        val placeFirstButton = ButtonType("Place this actor first")
        val keepExistingButton = ButtonType("Keep listed actor first")

        return DialogFlows.showResultDialog(
            owner = owner,
            title = "Resolve Initiative Tie",
            headerText = "Two actors share the same initiative",
            content = content,
            buttonTypes = listOf(placeFirstButton, keepExistingButton, ButtonType.CANCEL),
        ) { button ->
            when (button) {
                placeFirstButton -> InitiativeTieDecision.NEW_ACTOR_FIRST
                keepExistingButton -> InitiativeTieDecision.EXISTING_ACTOR_FIRST
                else -> null
            }
        }
    }

    private fun actorSummary(prefix: String, actor: Actor): String =
        "$prefix: ${actor.name} (HP ${actor.hp}, AC ${actor.ac})"
}
