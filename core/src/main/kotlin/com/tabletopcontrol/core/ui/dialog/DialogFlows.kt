package com.tabletopcontrol.core.ui.dialog

import javafx.event.ActionEvent
import javafx.scene.Node
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.DialogEvent
import javafx.stage.Window
import javafx.event.EventHandler

/**
 * Shared dialog helpers for common owner/result/validation/cancel flows.
 *
 * Example:
 * ```
 * val result = DialogFlows.showResultDialog(
 *     owner = owner,
 *     title = "Example",
 *     headerText = "Configure value",
 *     content = content,
 * ) { button ->
 *     DialogFlows.resultForButton(button) { "value" }
 * }
 * ```
 */
object DialogFlows {
    /**
     * Tracks whether a dialog confirm/apply action has completed successfully.
     * This is typically used to restore previous state when a dialog is cancelled.
     */
    class ConfirmationTracker {
        var confirmed: Boolean = false
            private set

        /**
         * Runs [attempt] and updates [confirmed] only when it succeeds.
         *
         * @return `true` when [attempt] succeeds, `false` when validation failed.
         */
        fun attemptConfirm(attempt: () -> Boolean): Boolean {
            val accepted = attempt()
            if (accepted) confirmed = true
            return accepted
        }
    }

    /**
     * Creates a dialog with shared owner/title/header/content/button plumbing.
     *
     * @param owner owning window for modality (optional).
     * @param title dialog window title.
     * @param headerText optional header text shown above content.
     * @param content optional dialog body node.
     * @param buttonTypes button types to register on the dialog pane.
     * @param isResizable whether the dialog is resizable.
     * @return a configured [Dialog] ready for additional wiring.
     */
    fun <R> createDialog(
        owner: Window?,
        title: String,
        headerText: String? = null,
        content: Node? = null,
        buttonTypes: List<ButtonType>,
        isResizable: Boolean = false,
    ): Dialog<R> =
        Dialog<R>().apply {
            this.title = title
            this.headerText = headerText
            owner?.let { initOwner(it) }
            dialogPane.buttonTypes.setAll(buttonTypes)
            if (content != null) {
                dialogPane.content = content
            }
            this.isResizable = isResizable
        }

    /**
     * Convenience helper to create, configure result conversion, and show a dialog.
     *
     * @param owner owning window for modality (optional).
     * @param title dialog window title.
     * @param headerText optional header text shown above content.
     * @param content dialog body node.
     * @param buttonTypes button types to register on the dialog pane.
     * @param isResizable whether the dialog is resizable.
     * @param resultFactory result mapping invoked from the dialog result converter.
     * @return the converted dialog result, or `null` when cancelled/closed.
     */
    fun <R> showResultDialog(
        owner: Window?,
        title: String,
        headerText: String? = null,
        content: Node,
        buttonTypes: List<ButtonType> = listOf(ButtonType.OK, ButtonType.CANCEL),
        isResizable: Boolean = false,
        resultFactory: (ButtonType) -> R?,
    ): R? {
        val dialog = createDialog<R>(
            owner = owner,
            title = title,
            headerText = headerText,
            content = content,
            buttonTypes = buttonTypes,
            isResizable = isResizable,
        )
        dialog.setResultConverter(resultFactory)
        return dialog.showAndWait().orElse(null)
    }

    /**
     * Returns a result only when the pressed [button] matches [confirmButton].
     *
     * @param button button returned by dialog result conversion.
     * @param confirmButton button treated as confirmation.
     * @param buildResult supplier used to create the result on confirmation.
     * @return the built result when confirmed, otherwise `null`.
     */
    fun <R> resultForButton(
        button: ButtonType,
        confirmButton: ButtonType = ButtonType.OK,
        buildResult: () -> R,
    ): R? = if (button == confirmButton) buildResult() else null

    /**
     * Wires a validated confirm/apply button and consumes the action event when invalid.
     *
     * The [onConfirmAttempt] callback should return `true` when validation passes and
     * the dialog can close, or `false` when validation fails and the dialog should stay open.
     *
     * @param dialog target dialog containing [confirmButton].
     * @param tracker confirmation state tracker updated on successful attempts.
     * @param confirmButton button type used as confirm/apply action.
     * @param onConfirmAttempt validation-and-apply callback for the confirm action.
     */
    fun installValidatedConfirm(
        dialog: Dialog<*>,
        tracker: ConfirmationTracker,
        confirmButton: ButtonType = ButtonType.OK,
        onConfirmAttempt: () -> Boolean,
    ) {
        val confirmNode = requireNotNull(dialog.dialogPane.lookupButton(confirmButton)) {
            "confirmButton must be registered in dialog.buttonTypes before installing validation: $confirmButton"
        }
        confirmNode.addEventFilter(ActionEvent.ACTION) { evt ->
            if (!tracker.attemptConfirm(onConfirmAttempt)) {
                evt.consume()
            }
        }
    }

    /**
     * Invokes [onCancel] only when the dialog was dismissed without confirmation.
     * [onAlways] always runs when the dialog is hidden.
     * Preserves and invokes the [Dialog.onHidden] handler that existed at the time this
     * function is called, prior to running [onAlways] and [onCancel].
     * Repeated calls to this helper chain wrappers by capturing the previously installed
     * handler each time. Prefer installing this flow once per dialog.
     *
     * @param dialog target dialog.
     * @param tracker confirmation state tracker.
     * @param onCancel callback invoked when dialog closes without confirmed apply/ok.
     * @param onAlways callback always invoked when dialog is hidden.
     */
    fun onHiddenWithCancelRestore(
        dialog: Dialog<*>,
        tracker: ConfirmationTracker,
        onCancel: () -> Unit,
        onAlways: () -> Unit = {},
    ) {
        val previousOnHidden: EventHandler<DialogEvent>? = dialog.onHidden
        dialog.setOnHidden { event ->
            previousOnHidden?.handle(event)
            onAlways()
            if (!tracker.confirmed) {
                onCancel()
            }
        }
    }
}
