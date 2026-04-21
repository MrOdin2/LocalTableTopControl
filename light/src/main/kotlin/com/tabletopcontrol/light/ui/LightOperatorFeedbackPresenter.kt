package com.tabletopcontrol.light.ui

import com.tabletopcontrol.core.ui.dialog.DialogFlows
import com.tabletopcontrol.light.LightOperationResult
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.stage.Window

internal class LightOperatorFeedbackPresenter {
    fun showInfo(label: Label, text: String) {
        applyStatus(label, text, INFO_STYLE)
    }

    fun showSuccess(label: Label, text: String) {
        applyStatus(label, text, SUCCESS_STYLE)
    }

    fun showError(label: Label, text: String) {
        applyStatus(label, text, ERROR_STYLE)
    }

    fun showFailureDialog(
        owner: Window?,
        title: String,
        failure: LightOperationResult.Failure,
    ) {
        val details = failure.details
            ?.takeIf { it.isNotBlank() && it != failure.operatorMessage }
            ?.let {
                TextArea(it).apply {
                    isEditable = false
                    isWrapText = true
                    prefRowCount = 3
                }
            }

        val dialog = DialogFlows.createDialog<Unit>(
            owner = owner,
            title = title,
            headerText = failure.operatorMessage,
            content = details,
            buttonTypes = listOf(ButtonType.OK),
            isResizable = false,
        )
        dialog.setResultConverter { null }
        dialog.showAndWait()
    }

    private fun applyStatus(label: Label, text: String, style: String) {
        label.text = text
        label.style = style
    }

    private companion object {
        private const val INFO_STYLE = "-fx-text-fill: -tc-text-muted;"
        private const val SUCCESS_STYLE = "-fx-text-fill: -tc-success;"
        private const val ERROR_STYLE = "-fx-text-fill: -tc-error;"
    }
}
