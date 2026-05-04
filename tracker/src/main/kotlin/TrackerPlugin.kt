package com.tabletopcontrol.new_tracker

import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.scene.SceneParticipant
import com.tabletopcontrol.core.ui.InputHelpers
import com.tabletopcontrol.core.ui.InputHelpers.Companion.allowOnlyNonNegativeIntegers
import com.tabletopcontrol.core.ui.InputHelpers.Companion.integerField
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.new_tracker.ImageHandling.ImageHandling
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorType
import com.tabletopcontrol.new_tracker.model.ActorTracker
import com.tabletopcontrol.new_tracker.model.InitiativeTieResolver
import com.tabletopcontrol.new_tracker.scene.TrackerSceneCodec
import com.tabletopcontrol.new_tracker.preset.ActorPresetService
import com.tabletopcontrol.new_tracker.ui.ActorPresetLibraryDialog
import com.tabletopcontrol.new_tracker.ui.ActorFeaturesDialog
import com.tabletopcontrol.new_tracker.ui.AddActorDialog
import com.tabletopcontrol.new_tracker.ui.InitiativeTieDialog
import com.tabletopcontrol.new_tracker.web.PlayerWebServer
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.MenuItem
import javafx.scene.control.RadioMenuItem
import javafx.scene.control.ScrollPane
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.TextField
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Window
import java.net.InetAddress

class TrackerPlugin : DmPlugin, SceneParticipant {

    override val displayName: String = "Tracker"
    override val sceneKey: String = "tracker"
    override val sceneDisplayName: String = displayName
    override val sceneLoadOrder: Int = 100

    val actorTracker = ActorTracker()
    private val imageHandling = ImageHandling()
    private val presetService = ActorPresetService(actorTracker)
    private val addActorDialog = AddActorDialog(actorTracker)
    private val actorFeaturesDialog = ActorFeaturesDialog()
    private val presetLibraryDialog = ActorPresetLibraryDialog(presetService)
    private val initiativeTieDialog = InitiativeTieDialog()
    private val webServer = PlayerWebServer(actorTracker)

    companion object {
        private const val MUTED_SMALL_LABEL_STYLE = "-fx-text-fill: -tc-text-muted; -fx-font-size: 11px;"
    }

    override fun createView(): Node {

        val roundLabel = Label()

        val actorList = VBox(8.0).apply {
            isFillWidth = true
        }

        val addActorButton = Button("+").apply {
            style = "-fx-base: -tc-accent;"
            setOnAction { e ->
                val owner = (e.source as? Button)?.scene?.window
                addActorDialog.show(owner)?.let { actor ->
                    actorTracker.addActor(actor, initiativeTieResolver(owner))
                    refreshTrackerView(actorList, roundLabel)
                }
            }
        }

        val nextButton = Button("NEXT").apply {
            style = "-fx-base: -tc-accent;"
            setOnAction {
                if (actorTracker.hasPlayerCharactersMissingInitiative()) {
                    webServer.requestInitiatives()
                    return@setOnAction
                }
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

        val webUrlLabel = Label().apply {
            style = MUTED_SMALL_LABEL_STYLE
            isVisible = false
            isManaged = false
        }

        val webToggleButton = Button("Web: OFF").apply {
            setOnAction {
                if (webServer.isRunning) {
                    webServer.stop()
                    text = "Web: OFF"
                    webUrlLabel.isVisible = false
                    webUrlLabel.isManaged = false
                } else {
                    try {
                        webServer.start()
                        text = "Web: ON"
                        val host = runCatching { InetAddress.getLocalHost().hostAddress }.getOrDefault("localhost")
                        webUrlLabel.text = "http://$host:${webServer.port}"
                        webUrlLabel.isVisible = true
                        webUrlLabel.isManaged = true
                    } catch (ex: Exception) {
                        text = "Web: ERR"
                        webUrlLabel.text = ex.message ?: "Failed to start"
                        webUrlLabel.isVisible = true
                        webUrlLabel.isManaged = true
                    }
                }
            }
            Tooltip.install(
                this,
                Tooltip("Start/stop the player web companion server (port ${webServer.port})"),
            )
        }

//        val webBar = HBox(8.0, ).apply {
//            alignment = Pos.CENTER_LEFT
//            padding = Insets(0.0, 0.0, 4.0, 0.0)
//        }

        val toolbar = HBox(8.0, addActorButton, nextButton, presetsButton, roundLabel, webToggleButton, webUrlLabel).apply {
            alignment = Pos.CENTER_LEFT
        }



        val scrollPane = ScrollPane(actorList).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            style = "-fx-background-color: transparent;"
        }

        val root = VBox(8.0, toolbar, scrollPane).apply {
            padding = Insets(12.0)
            style = "-fx-background-color: -tc-bg;"
            VBox.setVgrow(scrollPane, Priority.ALWAYS)
        }

        webServer.onActorChanged = {
            refreshTrackerView(actorList, roundLabel)
        }
        refreshTrackerView(actorList, roundLabel)
        return root
    }

    override fun captureSceneState(): String = TrackerSceneCodec.serialize(actorTracker.snapshot())

    override fun applySceneState(payload: String) {
        val state = requireNotNull(TrackerSceneCodec.deserialize(payload)) {
            "Invalid tracker scene payload"
        }
        actorTracker.replaceAllActors(presetService.recoverSceneActors(state))
    }

    override fun onShutdown() {
        webServer.stop()
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
        roundLabel.text = "${actorTracker.roundCount} - Round"
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

        val initiativeField = committedInitiativeField(actor, actorList)

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
                val owner = scene?.window
                actorTracker.findActor(actor.id)?.let { currentActor ->
                    presetService.saveActor(currentActor) { presetName ->
                        confirmPresetOverwrite(owner, presetName)
                    }
                }
            }
        }

        val header = HBox(8.0, swatch, actorTypeBadge(actor), nameField, deleteButton, duplicateButton).apply {
            alignment = Pos.CENTER_LEFT
        }

        val stats = HBox(
            8.0,
            InputHelpers.labeledField("HP", hpField),
            InputHelpers.labeledField("AC", acField),
            InputHelpers.labeledField("Initiative", initiativeField),
            pictureButton,
            saveButton,
            actorFeatureSummary(actor),
        ).apply {
            alignment = Pos.CENTER_LEFT
        }

        val borderStyle =
        if (actor.id == actorTracker.getCurrentActor()?.id) "-tc-card-active-border" else "-tc-card-border"

        val cardChildren = buildList {
            add(header)
            add(stats)
        }

        return VBox(8.0, *cardChildren.toTypedArray()).apply {
            padding = Insets(12.0)
            style = """
                -fx-background-color: -tc-surface;
                -fx-border-color: $borderStyle;
                -fx-border-radius: 8;
                -fx-background-radius: 8;
            """.trimIndent().replace("\n", " ")
            maxWidth = Double.MAX_VALUE
            Tooltip.install(
                this,
                Tooltip(
                    "Right-click to change actor features, type, or token size. " +
                        "Current: ${actor.actorType.shortLabel}, ${actor.tokenSize.menuLabel}, " +
                        actor.features.summaryText,
                ),
            )
            setOnContextMenuRequested { event ->
                buildActorContextMenu(actor.id, actorList, scene?.window).show(this, event.screenX, event.screenY)
                event.consume()
            }
        }
    }

    private fun actorTypeBadge(actor: Actor): Label =
        Label(actor.actorType.shortLabel).apply {
            minWidth = 32.0
            alignment = Pos.CENTER
            style = """
                -fx-text-fill: ${if (actor.actorType == ActorType.PC) "-tc-accent" else "-tc-text-muted"};
                -fx-font-size: 11px;
                -fx-font-weight: bold;
            """.trimIndent().replace("\n", " ")
            Tooltip.install(this, Tooltip(actor.actorType.displayName))
        }

    private fun actorFeatureSummary(actor: Actor): Label =
        Label(actor.features.summaryText).apply {
            isVisible = actor.features.hasAny
            isManaged = actor.features.hasAny
            style = "-fx-text-fill: -tc-text-muted;"
            Tooltip.install(this, Tooltip(actor.features.summaryText))
        }

    private fun buildActorContextMenu(
        actorId: String,
        actorList: VBox,
        owner: Window?,
    ): ContextMenu {
        val actor = actorTracker.findActor(actorId) ?: return ContextMenu()
        val featuresItem = MenuItem("Features...").apply {
            setOnAction {
                actorTracker.findActor(actorId)?.let { currentActor ->
                    val updatedFeatures = actorFeaturesDialog.show(owner, currentActor) ?: return@let
                    if (currentActor.features == updatedFeatures) return@let
                    actorTracker.updateActor(currentActor.copy(features = updatedFeatures))
                    refreshActorList(actorList)
                }
            }
        }
        val typeGroup = ToggleGroup()
        val typeItems = ActorType.entries.map { actorType ->
            RadioMenuItem(actorType.menuLabel).apply {
                toggleGroup = typeGroup
                isSelected = actor.actorType == actorType
                setOnAction {
                    actorTracker.findActor(actorId)?.let { currentActor ->
                        if (currentActor.actorType == actorType) return@let
                        actorTracker.updateActor(currentActor.copy(actorType = actorType))
                        refreshActorList(actorList)
                    }
                }
            }
        }
        val sizeGroup = ToggleGroup()
        val sizeItems = TokenSize.entries.map { size ->
            RadioMenuItem(size.menuLabel).apply {
                toggleGroup = sizeGroup
                isSelected = actor.tokenSize == size
                setOnAction {
                    actorTracker.findActor(actorId)?.let { currentActor ->
                        if (currentActor.tokenSize == size) return@let
                        actorTracker.updateActor(currentActor.copy(tokenSize = size))
                        refreshActorList(actorList)
                    }
                }
            }
        }
        return ContextMenu(
            featuresItem,
            SeparatorMenuItem(),
            MenuItem("Actor Type").apply { isDisable = true },
            *typeItems.toTypedArray(),
            SeparatorMenuItem(),
            MenuItem("Token Size").apply { isDisable = true },
            *sizeItems.toTypedArray(),
            SeparatorMenuItem(),
            MenuItem(
                "Current: ${actor.actorType.shortLabel}, ${actor.tokenSize.menuLabel}, ${actor.features.summaryText}",
            ).apply { isDisable = true },
        )
    }

    private fun committedInitiativeField(actor: Actor, actorList: VBox): TextField =
        TextField(actor.initiative?.toString().orEmpty()).apply {
            prefColumnCount = 5
            promptText = "Init"
            allowOnlyNonNegativeIntegers()

            fun commitValue() {
                actorTracker.findActor(actor.id)?.let { currentActor ->
                    val reSorted = actorTracker.updateActor(
                        currentActor.copy(initiative = text.toIntOrNull()),
                        initiativeTieResolver(scene?.window),
                    )
                    if (reSorted) {
                        refreshActorList(actorList)
                    }
                }
            }

            setOnAction { commitValue() }
            focusedProperty().addListener { _, _, isFocused ->
                if (!isFocused) {
                    commitValue()
                }
            }
        }

    private fun initiativeTieResolver(owner: Window?): InitiativeTieResolver =
        { actorsAtInitiative, initiative, movedActorId ->
            initiativeTieDialog.show(owner, actorsAtInitiative, initiative, movedActorId)
        }

    private fun confirmPresetOverwrite(
        owner: Window?,
        presetName: String,
    ): Boolean =
        Alert(Alert.AlertType.CONFIRMATION).apply {
            owner?.let { initOwner(it) }
            title = "Overwrite preset"
            headerText = "Overwrite preset \"$presetName\"?"
            contentText =
                "A preset with this name already exists. Choose OK to replace it " +
                    "with this actor's current settings, or Cancel to keep the existing preset."
            buttonTypes.setAll(ButtonType.OK, ButtonType.CANCEL)
        }.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK
}
