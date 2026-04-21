package com.tabletopcontrol.core.ui.dialog

import javafx.stage.FileChooser
import javafx.stage.Window
import java.io.File

/** Shared audio file chooser used by audio-oriented plugins. */
object AudioFileChooserDialog {
    private val defaultAudioPatterns = listOf("*.mp3", "*.wav", "*.aac", "*.m4a")

    fun showOpenDialog(
        owner: Window?,
        title: String,
        audioPatterns: List<String> = defaultAudioPatterns,
        historyKey: String? = null,
        fallbackSelection: File? = null,
    ): File? {
        val resolvedPatterns = audioPatterns.ifEmpty { defaultAudioPatterns }
        val chooser = FileChooser().apply {
            this.title = title
            extensionFilters.addAll(
                FileChooser.ExtensionFilter("Audio files", *resolvedPatterns.toTypedArray()),
                FileChooser.ExtensionFilter("All files", "*.*"),
            )
            historyKey?.let { key ->
                FileChooserHistoryStore.configureInitialDirectory(this, key, fallbackSelection)
            }
        }
        val selected = chooser.showOpenDialog(owner)
        if (historyKey != null && selected != null) {
            FileChooserHistoryStore.rememberSelection(historyKey, selected)
        }
        return selected
    }
}
