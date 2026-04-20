package com.tabletopcontrol.audio

import com.tabletopcontrol.core.ui.color.ColorEditorDialog
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.core.ui.dialog.AudioFileChooserDialog
import javafx.scene.paint.Color
import javafx.stage.Window
import java.io.File

internal object SoundboardSlotDialogs {
    fun chooseAudioFile(
        owner: Window?,
        slotNumber: Int,
    ): File? = AudioFileChooserDialog.showOpenDialog(
        owner = owner,
        title = "Load sound for Slot $slotNumber",
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
