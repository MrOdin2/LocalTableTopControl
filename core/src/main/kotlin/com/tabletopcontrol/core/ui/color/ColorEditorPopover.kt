package com.tabletopcontrol.core.ui.color

import javafx.scene.paint.Color
import javafx.stage.Window

/**
 * Backwards-compatible alias for callers still using the previous name.
 */
@Deprecated(
    message = "Renamed to ColorEditorDialog to match modal dialog behavior.",
    replaceWith = ReplaceWith("ColorEditorDialog"),
)
object ColorEditorPopover {
    fun showDialog(
        owner: Window?,
        title: String,
        prompt: String,
        initialColor: Color,
    ): Color? = ColorEditorDialog.showDialog(owner, title, prompt, initialColor)
}
