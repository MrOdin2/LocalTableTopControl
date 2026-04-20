package com.tabletopcontrol.new_tracker.ui

import com.tabletopcontrol.core.ui.dialog.DialogFlows
import com.tabletopcontrol.new_tracker.preset.ActorPresetService
import com.tabletopcontrol.new_tracker.preset.PresetLibrary
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Window
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ActorPresetLibraryDialog(
    private val presetService: ActorPresetService,
) {

    fun show(
        owner: Window?,
        onRefresh: () -> Unit,
    ) {
        val dialog = DialogFlows.createDialog<Unit>(
            owner = owner,
            title = "Presets",
            buttonTypes = listOf(ButtonType.CLOSE),
        )

        val listBox = VBox(4.0).apply {
            padding = Insets(4.0)
        }
        val rebuildGeneration = AtomicInteger(0)
        val rebuildExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "new-tracker-preset-rebuild").apply { isDaemon = true }
        }

        fun rebuildList() {
            listBox.children.setAll(
                Label("Loading...").apply {
                    padding = Insets(8.0)
                },
            )
            val generation = rebuildGeneration.incrementAndGet()
            rebuildExecutor.submit {
                val presets = presetService.loadAll()
                Platform.runLater {
                    if (rebuildGeneration.get() != generation) {
                        return@runLater
                    }
                    listBox.children.clear()
                    if (presets.isEmpty()) {
                        listBox.children.add(
                            Label("No presets saved yet. Use SAVE on an actor card to create one.").apply {
                                padding = Insets(8.0)
                            },
                        )
                        return@runLater
                    }

                    val grouped = presets.groupBy { it.folder }
                    val sortedFolders = grouped.keys.sortedWith(
                        compareBy({ if (it.isEmpty()) 0 else 1 }, { it }),
                    )
                    val showHeaders = sortedFolders.size > 1

                    for (folder in sortedFolders) {
                        val presetsInFolder = grouped[folder] ?: continue
                        if (showHeaders) {
                            val headerLabel = if (folder.isEmpty()) "Root" else folder
                            listBox.children.add(
                                Label(headerLabel).apply {
                                    style = "-fx-font-weight: bold;"
                                    padding = Insets(6.0, 2.0, 2.0, 2.0)
                                },
                            )
                        }

                        presetsInFolder.forEach { preset ->
                            listBox.children.add(presetRow(preset, onRefresh, ::rebuildList))
                        }
                    }
                }
            }
        }

        rebuildList()

        val openFolderButton = Button("Open Preset Folder").apply {
            tooltip = Tooltip("Open the preset folder in the system file manager")
            setOnAction {
                Thread { presetService.openPresetsFolder() }.also { it.isDaemon = true }.start()
            }
        }

        val headerBar = HBox(8.0).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(0.0, 0.0, 8.0, 0.0)
            children.addAll(
                Label("Load or manage saved actor presets").apply {
                    HBox.setHgrow(this, Priority.ALWAYS)
                },
                openFolderButton,
            )
        }

        dialog.dialogPane.content = VBox(
            4.0,
            headerBar,
            ScrollPane(listBox).apply {
                isFitToWidth = true
                prefHeight = 300.0
                hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            },
        )

        dialog.showAndWait()
        rebuildExecutor.shutdownNow()
    }

    private fun presetRow(
        preset: PresetLibrary.Preset,
        onRefresh: () -> Unit,
        rebuildList: () -> Unit,
    ): HBox {
        val info = Label(
            "${preset.name}  HP: ${preset.hp}  AC: ${preset.ac}  Init: ${preset.initiativeText()}",
        ).apply {
            HBox.setHgrow(this, Priority.ALWAYS)
        }

        val loadButton = Button("Load").apply {
            tooltip = Tooltip("Add this preset as a new actor")
            setOnAction {
                presetService.loadPreset(preset, onRefresh)
            }
        }

        val deleteButton = Button("Delete").apply {
            tooltip = Tooltip("Delete every preset with this name from the preset folder")
            setOnAction {
                val confirmation = Alert(Alert.AlertType.CONFIRMATION).apply {
                    title = "Delete preset"
                    headerText = "Delete all presets named \"${preset.name}\"?"
                    contentText =
                        "This removes every preset with this name from the preset folder " +
                            "and its first-level subfolders."
                    buttonTypes.setAll(ButtonType.OK, ButtonType.CANCEL)
                }
                if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    presetService.deleteByName(preset.name)
                    rebuildList()
                }
            }
        }

        return HBox(8.0, info, loadButton, deleteButton).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(4.0, 2.0, 4.0, 2.0)
        }
    }

    private fun PresetLibrary.Preset.initiativeText(): String =
        "-"
}
