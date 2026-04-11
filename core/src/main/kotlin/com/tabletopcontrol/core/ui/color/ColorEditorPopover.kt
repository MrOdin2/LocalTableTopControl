package com.tabletopcontrol.core.ui.color

import javafx.scene.paint.Color
import javafx.stage.Window

/**
 * Backwards-compatible alias for callers still using the previous name.
 */
@Deprecated(
    message = "Renamed to ColorEditorDialog to match modal dialog behavior.",
    replaceWith = ReplaceWith(
        "ColorEditorDialog",
        imports = ["com.tabletopcontrol.core.ui.color.ColorEditorDialog"],
    ),
)
object ColorEditorPopover {
    /**
     * Opens the shared modal colour editor dialog and returns the confirmed colour, or `null` when cancelled.
     */
    fun showDialog(
        owner: Window?,
        title: String,
        prompt: String,
        initialColor: Color,
    ): Color? = ColorEditorDialog.showDialog(owner, title, prompt, initialColor)
}
