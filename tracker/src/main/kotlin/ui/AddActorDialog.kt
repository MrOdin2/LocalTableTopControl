package com.tabletopcontrol.new_tracker.ui

import com.tabletopcontrol.core.ui.InputHelpers
import com.tabletopcontrol.core.ui.InputHelpers.Companion.allowOnlyNonNegativeIntegers
import com.tabletopcontrol.core.ui.dialog.DialogFlows
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorTracker
import javafx.scene.control.ButtonType
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import javafx.stage.Window

class AddActorDialog(
    private val actorTracker: ActorTracker
) {

    fun show(owner: Window?): Actor? {
        val nameInput = InputHelpers.labeledTextField("Name", "Actor name")

        val hpInput = InputHelpers.labeledTextField("HP", "Hit points").apply {
            children[1].let { (it as TextField).allowOnlyNonNegativeIntegers() }
        }

        val acInput = InputHelpers.labeledTextField("AC", "Armour class").apply {
            children[1].let { (it as TextField).allowOnlyNonNegativeIntegers() }
        }

        val initiativeInput = InputHelpers.labeledTextField("Init", "Initiative").apply {
            children[1].let { (it as TextField).allowOnlyNonNegativeIntegers() }
        }

        val content = VBox(8.0, nameInput, hpInput, acInput, initiativeInput)

        return DialogFlows.showResultDialog(
            owner = owner,
            title = "Add Actor",
            headerText = "Create a new actor card",
            content = content,
            buttonTypes = listOf(ButtonType.OK, ButtonType.CANCEL),
        ) { button ->
            DialogFlows.resultForButton(button) {
                val nameField = nameInput.children[1] as TextField
                val hpField = hpInput.children[1] as TextField
                val acField = acInput.children[1] as TextField
                val initiativeField = initiativeInput.children[1] as TextField

                Actor(
                    name = nameField.text.trim().ifBlank { "Actor ${actorTracker.actorList.size + 1}" },
                    hp = hpField.text.toIntOrNull() ?: 0,
                    ac = acField.text.toIntOrNull() ?: 0,
                    initiative = initiativeField.text.toIntOrNull(),
                )
            }
        }
    }

}