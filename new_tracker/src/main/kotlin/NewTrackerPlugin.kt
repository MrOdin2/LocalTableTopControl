package com.tabletopcontrol.new_tracker

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.ui.InputHelpers
import com.tabletopcontrol.core.ui.InputHelpers.Companion.allowOnlyNonNegativeIntegers
import com.tabletopcontrol.core.ui.dialog.DialogFlows
import com.tabletopcontrol.new_tracker.model.ActorTracker
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import javafx.stage.Window

class NewTrackerPlugin : DmPlugin {

    override val displayName: String = "NEWTracker"

    val actorTracker = ActorTracker();

    override fun createView(): Node {

        val label = Label().apply {
            text = "Tracker plugin is under construction"
        }

        val addActorButton = Button("+").apply {
            setOnAction { e ->
                val owner = (e.source as? Button)?.scene?.window
                addActorDialog(owner)
            }
        }

        val actorList = VBox().apply {

        }

        val root = VBox(0.0, label, addActorButton)
        return root
    }

    fun addActorDialog(owner: Window?) {

        val label = Label().apply {
            text = "Adding Actors is under construction"
        }

        val nameInput = InputHelpers.labeledTextField("Name", "Name of the Actor").apply {
            children[1].let { (it as TextField).allowOnlyNonNegativeIntegers() }
        }

        val hpInput = InputHelpers.labeledTextField("HP", "Max HP of the Actor").apply {
            children[1].let { (it as TextField).allowOnlyNonNegativeIntegers() }
        }

        val acInput = InputHelpers.labeledTextField("AC: ", "AC of the Actor").apply {
            children[1].let { (it as TextField).allowOnlyNonNegativeIntegers() }
        }

        val content = VBox(
            label,
            nameInput,
            hpInput,
            acInput,
        ).apply {
            spacing = 5.0
        }

//        return DialogFlows.showResultDialog(
//            owner = owner,
//            title = "Token Image",
//            headerText = "Select a picture and adjust scale/position",
//            content = content,
//            buttonTypes = listOf(ButtonType.OK, ButtonType.CANCEL),
//        ) { button
//        }

        val dialog = DialogFlows.createDialog<ButtonType>(
            owner = owner,
            title = "Add Actor",
            headerText = "Add an actor",
            content = content,
            buttonTypes = listOf(ButtonType.APPLY, ButtonType.CANCEL),
        )

        dialog.showAndWait()
    }

    fun actorCard(): VBox{

        return VBox().apply {}
    }

}