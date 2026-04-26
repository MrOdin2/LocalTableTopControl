package com.tabletopcontrol.dynamicmap

import com.tabletopcontrol.core.ui.dialog.FileChooserHistoryStore
import com.tabletopcontrol.core.EventBus
import java.io.File
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextInputDialog
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.stage.FileChooser
import javafx.util.StringConverter

class DynamicMapConstructionSiteToolbar {
    fun createView(): Node {
        var activeSite: DynamicMapConstructionSiteSummary? = null
        var isApplyingState = false

        val siteCombo = ComboBox<DynamicMapConstructionSiteSummary>().apply {
            prefWidth = 220.0
            promptText = "Scratch draft"
            tooltip = Tooltip("Select the active Dynamic Map Builder construction site")
            converter = object : StringConverter<DynamicMapConstructionSiteSummary>() {
                override fun toString(site: DynamicMapConstructionSiteSummary?): String =
                    site?.name ?: "Scratch draft"

                override fun fromString(string: String?): DynamicMapConstructionSiteSummary? = null
            }
        }
        val newButton = Button("New...")
        val saveButton = Button("Save")
        val saveAsButton = Button("Save As...")
        val deleteButton = Button("Delete")
        val exportButton = Button("Export...")

        fun selectedSite(): DynamicMapConstructionSiteSummary? =
            siteCombo.selectionModel.selectedItem

        fun refreshButtons() {
            saveButton.isDisable = activeSite == null
            deleteButton.isDisable = selectedSite() == null
        }

        fun promptSiteName(
            title: String,
            initialName: String,
        ): String? {
            val dialog = TextInputDialog(initialName).apply {
                this.title = title
                headerText = title
                contentText = "Name:"
                siteCombo.scene?.window?.let(::initOwner)
            }
            val result = dialog.showAndWait()
            if (!result.isPresent) return null
            return result.get().trim().takeIf { it.isNotEmpty() }
        }

        fun applyState(event: DynamicMapConstructionSiteStateChangedEvent) {
            isApplyingState = true
            activeSite = event.activeSite
            siteCombo.items.setAll(event.sites)
            if (activeSite == null) {
                siteCombo.selectionModel.clearSelection()
            } else {
                event.sites.firstOrNull { it.id == activeSite?.id }?.let { siteCombo.selectionModel.select(it) }
            }
            isApplyingState = false
            refreshButtons()
        }

        siteCombo.selectionModel.selectedItemProperty().addListener { _, _, site ->
            refreshButtons()
            if (!isApplyingState && site != null && site.id != activeSite?.id) {
                EventBus.publish(DynamicMapConstructionSiteLoadRequestedEvent(site.id))
            }
        }

        newButton.setOnAction {
            val name = promptSiteName(
                title = "New Construction Site",
                initialName = "New Construction Site",
            ) ?: return@setOnAction
            EventBus.publish(DynamicMapConstructionSiteCreateRequestedEvent(name))
        }
        saveButton.setOnAction {
            EventBus.publish(DynamicMapConstructionSiteSaveRequestedEvent)
        }
        saveAsButton.setOnAction {
            val defaultName = activeSite?.name?.let { "$it Copy" } ?: "New Construction Site"
            val name = promptSiteName(
                title = "Save Construction Site As",
                initialName = defaultName,
            ) ?: return@setOnAction
            EventBus.publish(DynamicMapConstructionSiteSaveAsRequestedEvent(name))
        }
        deleteButton.setOnAction {
            val site = selectedSite() ?: return@setOnAction
            val confirmation = Alert(Alert.AlertType.CONFIRMATION).apply {
                title = "Delete Construction Site"
                headerText = "Delete ${site.name}?"
                contentText = "This removes the saved construction-site file."
                siteCombo.scene?.window?.let(::initOwner)
            }
            val result = confirmation.showAndWait()
            if (result.isPresent && result.get() == ButtonType.OK) {
                EventBus.publish(DynamicMapConstructionSiteDeleteRequestedEvent(site.id))
            }
        }
        exportButton.setOnAction {
            val chooser = FileChooser().apply {
                title = "Export Dynamic Map"
                initialFileName = exportInitialFileName(activeSite)
                extensionFilters.addAll(
                    FileChooser.ExtensionFilter("Dynamic Map bundle", "*.dynamicmap"),
                    FileChooser.ExtensionFilter("ZIP archive", "*.zip"),
                    FileChooser.ExtensionFilter("All files", "*.*"),
                )
                FileChooserHistoryStore.configureInitialDirectory(this, EXPORT_HISTORY_KEY)
            }
            val selected = chooser.showSaveDialog(siteCombo.scene?.window) ?: return@setOnAction
            val target = ensureExportExtension(selected)
            FileChooserHistoryStore.rememberSelection(EXPORT_HISTORY_KEY, target)
            EventBus.publish(DynamicMapGameplayExportRequestedEvent(target))
        }

        val stateSubscription = EventBus.subscribe<DynamicMapConstructionSiteStateChangedEvent> { event ->
            applyState(event)
        }
        val exportCompletedSubscription = EventBus.subscribe<DynamicMapGameplayExportCompletedEvent> { event ->
            Alert(Alert.AlertType.INFORMATION).apply {
                title = "Export Dynamic Map"
                headerText = "Dynamic Map export complete"
                contentText = buildString {
                    append("Saved gameplay bundle to:\n")
                    append(event.summary.targetFile.absolutePath)
                    if (event.summary.missingBackground) {
                        append("\n\nThe background image could not be bundled because the source file is not available.")
                    }
                }
                siteCombo.scene?.window?.let(::initOwner)
            }.showAndWait()
        }
        val exportFailedSubscription = EventBus.subscribe<DynamicMapGameplayExportFailedEvent> { event ->
            Alert(Alert.AlertType.ERROR).apply {
                title = "Export Dynamic Map"
                headerText = "Export failed"
                contentText = "Could not write:\n${event.targetFile.absolutePath}\n\n${event.message}"
                siteCombo.scene?.window?.let(::initOwner)
            }.showAndWait()
        }

        EventBus.publish(DynamicMapConstructionSiteSnapshotRequestedEvent)

        return HBox(
            6.0,
            Label("Site:"),
            siteCombo,
            newButton,
            saveButton,
            saveAsButton,
            deleteButton,
            exportButton,
        ).apply {
            sceneProperty().addListener { _, _, newScene ->
                if (newScene == null) {
                    stateSubscription.unsubscribe()
                    exportCompletedSubscription.unsubscribe()
                    exportFailedSubscription.unsubscribe()
                }
            }
            refreshButtons()
        }
    }
}

private const val EXPORT_HISTORY_KEY = "dynamic-map-builder-export"

private fun exportInitialFileName(activeSite: DynamicMapConstructionSiteSummary?): String {
    val baseName = activeSite?.name?.let(::safeExportBaseName) ?: "dynamic-map"
    return "$baseName.${DynamicMapGameplayExporter.DEFAULT_EXTENSION}"
}

private fun safeExportBaseName(name: String): String =
    name
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "dynamic-map" }

private fun ensureExportExtension(file: File): File =
    if (file.extension.isBlank()) {
        File(file.parentFile, "${file.name}.${DynamicMapGameplayExporter.DEFAULT_EXTENSION}")
    } else {
        file
    }
