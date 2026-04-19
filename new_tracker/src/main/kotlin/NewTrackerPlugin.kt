package com.tabletopcontrol.new_tracker

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.ui.InputHelpers
import com.tabletopcontrol.core.ui.InputHelpers.Companion.allowOnlyNonNegativeIntegers
import com.tabletopcontrol.core.ui.InputHelpers.Companion.integerField
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.core.ui.dialog.DialogFlows
import com.tabletopcontrol.new_tracker.ImageHandling.ImageHandling
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorTracker
import com.tabletopcontrol.new_tracker.model.InitiativeTieDecision
import com.tabletopcontrol.new_tracker.model.InitiativeTieResolver
import com.tabletopcontrol.new_tracker.preset.ActorPresetService
import com.tabletopcontrol.new_tracker.ui.ActorPresetLibraryDialog
import com.tabletopcontrol.new_tracker.ui.InitiativeTieDialog
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Window

class NewTrackerPlugin : DmPlugin {

    override val displayName: String = "NEWTracker"

    val actorTracker = ActorTracker()
    private val imageHandling = ImageHandling()
    private val presetService = ActorPresetService(actorTracker)
    private val presetLibraryDialog = ActorPresetLibraryDialog(presetService)
    private val initiativeTieDialog = InitiativeTieDialog()

    override fun createView(): Node {

        val label = Label().apply {
            text = "Tracker plugin is under construction"
        }

        val roundLabel = Label()

        val actorList = VBox(8.0).apply {
            isFillWidth = true
        }

        val addActorButton = Button("+").apply {
            style = "-fx-base: -tc-accent;"
            setOnAction { e ->
                val owner = (e.source as? Button)?.scene?.window
                addActorDialog(owner)?.let { actor ->
                    actorTracker.addActor(actor, initiativeTieResolver(owner))
                    refreshTrackerView(actorList, roundLabel)
                }
            }
        }

        val nextButton = Button("NEXT").apply {
            style = "-fx-base: -tc-accent;"
            setOnAction {
                actorTracker.next()
                refreshTrackerView(actorList, roundLabel)
                }
            }

        val presetsButton = Button("Presets...").apply {
            setOnAction { event ->
                val owner = (event.source as? Button)?.scene?.window
                presetLibraryDialog.show(owner) {
                    refreshTrackerView(actorList, roundLabel)
                }
            }
        }

        val toolbar = HBox(8.0, addActorButton, nextButton, presetsButton, roundLabel).apply {
            alignment = Pos.CENTER_LEFT
        }

        val scrollPane = ScrollPane(actorList).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            style = "-fx-background-color: transparent;"
        }

        val root = VBox(12.0, label, toolbar, scrollPane).apply {
            padding = Insets(12.0)
            style = "-fx-background-color: -tc-bg;"
            VBox.setVgrow(scrollPane, Priority.ALWAYS)
        }

        refreshTrackerView(actorList, roundLabel)
        return root
    }

    private fun addActorDialog(owner: Window?): Actor? {
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

    private fun refreshActorList(actorList: VBox) {
        actorList.children.setAll(
            if (actorTracker.actorList.isEmpty()) {
                listOf(
                    Label("No actors yet. Use + to add one.").apply {
                        style = "-fx-text-fill: -tc-text-muted;"
                    },
                )
            } else {
                actorTracker.actorList.map { actor -> actorCard(actor, actorList) }
            },
        )
    }

    private fun refreshTrackerView(actorList: VBox, roundLabel: Label) {
        roundLabel.text = "Round: ${actorTracker.roundCount}"
        refreshActorList(actorList)
    }

    private fun actorCard(actor: Actor, actorList: VBox): VBox {
        val nameField = TextField(actor.name).apply {
            promptText = "Name"
            HBox.setHgrow(this, Priority.ALWAYS)
            textProperty().addListener { _, _, newValue ->
                actorTracker.findActor(actor.id)?.let { currentActor ->
                    actorTracker.updateActor(currentActor.copy(name = newValue))
                }
            }
        }

        val swatchColor = actor.color
        val swatch = Region().apply {
            minWidth = 14.0; maxWidth = 14.0
            minHeight = 14.0; maxHeight = 14.0
            val hex = swatchColor.let { ColorHexCodec.colorToHex(it) }
            style = "-fx-background-color: $hex; -fx-background-radius: 7;"
            Tooltip.install(this, Tooltip("Map token colour"))
        }

        val hpField = integerField(actor.hp) { value ->
            actorTracker.findActor(actor.id)?.let { currentActor ->
                actorTracker.updateActor(currentActor.copy(hp = value ?: 0))
            }
        }.apply {
            promptText = "HP"
        }

        val acField = integerField(actor.ac) { value ->
            actorTracker.findActor(actor.id)?.let { currentActor ->
                actorTracker.updateActor(currentActor.copy(ac = value ?: 0))
            }
        }.apply {
            promptText = "AC"
        }

        lateinit var initiativeField: TextField
        initiativeField = integerField(actor.initiative) { value ->
            actorTracker.findActor(actor.id)?.let { currentActor ->
                val reSorted = actorTracker.updateActor(
                    currentActor.copy(initiative = value),
                    initiativeTieResolver(initiativeField.scene?.window),
                )
                if (reSorted) {
                    refreshActorList(actorList)
                }
            }
        }.apply {
            promptText = "Init"
        }

        val deleteButton = Button("Delete").apply {
            setOnAction {
                actorTracker.findActor(actor.id)?.let(actorTracker::removeActor)
                refreshActorList(actorList)
            }
        }

        val duplicateButton = Button("Dup").apply {
            setOnAction {
                val owner = scene?.window
                actorTracker.findActor(actor.id)?.let { currentActor ->
                    actorTracker.duplicateActor(currentActor, initiativeTieResolver(owner))
                }?.let {
                    refreshActorList(actorList)
                }
            }
        }

        val pictureButton = imageHandling.createPictureButton(
            actorProvider = { actorTracker.findActor(actor.id) },
            onActorUpdated = { updatedActor -> actorTracker.updateActor(updatedActor) },
            onRefresh = { refreshActorList(actorList) },
        )

        val saveButton = Button("SAVE").apply {
            tooltip = Tooltip("Save this actor as a preset")
            setOnAction {
                actorTracker.findActor(actor.id)?.let(presetService::saveActor)
            }
        }

        val header = HBox(8.0, swatch, nameField, deleteButton, duplicateButton).apply {
            alignment = Pos.CENTER_LEFT
        }

        val stats = HBox(
            8.0,
            InputHelpers.labeledField("HP", hpField),
            InputHelpers.labeledField("AC", acField),
            InputHelpers.labeledField("Initiative", initiativeField),
            pictureButton,
            saveButton,
        ).apply {
            alignment = Pos.CENTER_LEFT
        }

        val borderStyle =
        if (actor.id == actorTracker.getCurrentActor()?.id) "-tc-card-active-border" else "-tc-card-border"

        return VBox(8.0, header, stats).apply {
            padding = Insets(12.0)
            style = """
                -fx-background-color: -tc-surface;
                -fx-border-color: $borderStyle;
                -fx-border-radius: 8;
                -fx-background-radius: 8;
            """.trimIndent().replace("\n", " ")
            maxWidth = Double.MAX_VALUE
        }
    }

    private fun initiativeTieResolver(owner: Window?): InitiativeTieResolver =
        { actorToPlace, existingActor ->
            initiativeTieDialog.show(owner, actorToPlace, existingActor)
                ?: InitiativeTieDecision.EXISTING_ACTOR_FIRST
        }
}
