package com.tabletopcontrol.core.scene

import com.tabletopcontrol.core.ui.dialog.DialogFlows
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Window

class SceneBrowserDialog(
    private val sceneManager: SceneManager,
) {
    fun show(owner: Window?) {
        val dialog = DialogFlows.createDialog<Unit>(
            owner = owner,
            title = "Scenes",
            buttonTypes = listOf(ButtonType.CLOSE),
        )

        val listBox = VBox(4.0).apply {
            padding = Insets(4.0)
        }

        fun rebuildList() {
            val scenes = sceneManager.loadAllScenes()
            listBox.children.clear()
            if (scenes.isEmpty()) {
                listBox.children.add(
                    Label("No scenes saved yet. Use Save Current Scene to capture the current setup.").apply {
                        padding = Insets(8.0)
                    },
                )
                return
            }

            scenes.forEach { scene ->
                listBox.children.add(sceneRow(owner, scene, ::rebuildList))
            }
        }

        val saveButton = Button("Save Current Scene").apply {
            tooltip = Tooltip("Save the current cross-plugin setup as a reusable scene")
            setOnAction {
                val sceneName = promptForSceneName(owner) ?: return@setOnAction
                val overwrite = sceneManager.hasScene(sceneName)
                if (!confirmSave(owner, sceneName, overwrite)) {
                    return@setOnAction
                }

                val result = sceneManager.saveScene(sceneName)
                rebuildList()
                showPartialFailureWarning(owner, "saved", result.failures)
            }
        }

        val openFolderButton = Button("Open Scene Folder").apply {
            tooltip = Tooltip("Open the saved scene folder in the system file manager")
            setOnAction {
                Thread { sceneManager.openScenesFolder() }.also { it.isDaemon = true }.start()
            }
        }

        val headerBar = HBox(8.0).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(0.0, 0.0, 8.0, 0.0)
            children.addAll(
                Label("Load, save, or remove reusable encounter scenes").apply {
                    HBox.setHgrow(this, Priority.ALWAYS)
                },
                saveButton,
                openFolderButton,
            )
        }

        dialog.dialogPane.content = VBox(
            4.0,
            headerBar,
            ScrollPane(listBox).apply {
                isFitToWidth = true
                prefHeight = 320.0
                hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            },
        )

        rebuildList()
        dialog.showAndWait()
    }

    private fun sceneRow(
        owner: Window?,
        scene: SavedScene,
        rebuildList: () -> Unit,
    ): HBox {
        val info = Label(scene.name).apply {
            HBox.setHgrow(this, Priority.ALWAYS)
        }

        val loadButton = Button("Load").apply {
            tooltip = Tooltip("Replace the current setup with this saved scene")
            setOnAction {
                if (!confirmLoad(owner, scene.name)) {
                    return@setOnAction
                }
                val result = sceneManager.loadScene(scene)
                showPartialFailureWarning(owner, "loaded", result.failures)
            }
        }

        val deleteButton = Button("Delete").apply {
            tooltip = Tooltip("Delete this saved scene")
            setOnAction {
                val confirmation = Alert(Alert.AlertType.CONFIRMATION).apply {
                    owner?.let(::initOwner)
                    title = "Delete scene"
                    headerText = "Delete scene \"${scene.name}\"?"
                    contentText = "This removes the saved scene from disk."
                    buttonTypes.setAll(ButtonType.OK, ButtonType.CANCEL)
                }
                if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    sceneManager.deleteScene(scene.name)
                    rebuildList()
                }
            }
        }

        return HBox(8.0, info, loadButton, deleteButton).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(4.0, 2.0, 4.0, 2.0)
        }
    }

    private fun promptForSceneName(owner: Window?): String? {
        val nameField = TextField().apply {
            promptText = "Scene name"
        }

        val dialog = DialogFlows.createDialog<String>(
            owner = owner,
            title = "Save Scene",
            headerText = "Name the scene you want to save.",
            content = VBox(6.0, Label("Scene name:"), nameField),
            buttonTypes = listOf(ButtonType.OK, ButtonType.CANCEL),
        )
        val okButton = dialog.dialogPane.lookupButton(ButtonType.OK)
        okButton.isDisable = true
        nameField.textProperty().addListener { _, _, newValue ->
            okButton.isDisable = newValue.trim().isEmpty()
        }
        dialog.setResultConverter { button ->
            DialogFlows.resultForButton(button) { nameField.text.trim() }
        }

        Platform.runLater {
            nameField.requestFocus()
            nameField.selectAll()
        }

        return dialog.showAndWait().orElse(null)
    }

    private fun confirmSave(
        owner: Window?,
        sceneName: String,
        overwrite: Boolean,
    ): Boolean =
        Alert(Alert.AlertType.CONFIRMATION).apply {
            owner?.let(::initOwner)
            title = if (overwrite) "Overwrite scene" else "Save scene"
            headerText =
                if (overwrite) {
                    "Overwrite scene \"$sceneName\"?"
                } else {
                    "Save current setup as scene \"$sceneName\"?"
                }
            contentText =
                if (overwrite) {
                    "This replaces the previously saved scene with the current map, tracker, music, soundboard, and light setup."
                } else {
                    "This captures the current map, tracker, music, soundboard, and light setup as a reusable scene."
                }
            buttonTypes.setAll(ButtonType.OK, ButtonType.CANCEL)
        }.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK

    private fun confirmLoad(owner: Window?, sceneName: String): Boolean =
        Alert(Alert.AlertType.CONFIRMATION).apply {
            owner?.let(::initOwner)
            title = "Load scene"
            headerText = "Load scene \"$sceneName\"?"
            contentText =
                "Loading a scene replaces the current map, tracker, music, soundboard, and light setup."
            buttonTypes.setAll(ButtonType.OK, ButtonType.CANCEL)
        }.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK

    private fun showPartialFailureWarning(
        owner: Window?,
        action: String,
        failures: List<SceneParticipantFailure>,
    ) {
        if (failures.isEmpty()) return

        val names = failures.joinToString(", ") { it.displayName }
        Alert(Alert.AlertType.WARNING).apply {
            owner?.let(::initOwner)
            title = "Scene operation completed with warnings"
            headerText = "Some sections could not be $action completely."
            contentText = "Affected sections: $names"
            buttonTypes.setAll(ButtonType.OK)
        }.showAndWait()
    }
}
