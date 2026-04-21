package com.tabletopcontrol.audio

import com.tabletopcontrol.core.ui.color.ColorEditorDialog
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.core.ui.dialog.AudioFileChooserDialog
import com.tabletopcontrol.core.ui.dialog.FileChooserHistoryStore
import javafx.scene.paint.Color
import javafx.stage.Window
import java.io.File

internal object SoundboardSlotDialogs {
    private const val SOUNDBOARD_HISTORY_KEY = "soundboard.browser"

    fun chooseAudioFile(
        owner: Window?,
        slotNumber: Int,
        currentUri: String? = null,
    ): File? = AudioFileChooserDialog.showOpenDialog(
        owner = owner,
        title = "Load sound for Slot $slotNumber",
        historyKey = SOUNDBOARD_HISTORY_KEY,
        fallbackSelection = FileChooserHistoryStore.fileFromUri(currentUri),
    )

    fun chooseColor(
        owner: Window?,
        currentColorHex: String?,
    ): String? {
        val initialColor = ColorHexCodec.parseOrDefault(currentColorHex, Color.GRAY)
        val selectedColor = ColorEditorDialog.showDialog(
            owner = owner,
            title = "Set Button Color",
            prompt = "Choose a color for this button",
            initialColor = initialColor,
        )
        return selectedColor?.let(ColorHexCodec::colorToHex)
    }
}
