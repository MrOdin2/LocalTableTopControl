package com.tabletopcontrol.core.ui

import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.control.TextFormatter
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import kotlin.math.floor

class InputHelpers {

    companion object {
        fun formatDouble(value: Double): String = if (value == floor(value) && !value.isInfinite()) {
            value.toLong().toString()
        } else {
            "%.4g".format(value)
        }

        fun TextField.allowOnlyNonNegativeIntegers() {
            textFormatter = TextFormatter<String> { change ->
                val text = change.controlNewText
                if (text.isEmpty() || text.matches(Regex("\\d+"))) change else null
            }
        }

        fun labeledTextField(labelText: String, promptText: String): HBox {
            val label = Label(labelText)
            val textField = TextField().apply {
                this.promptText = promptText
                maxWidth = Double.MAX_VALUE
            }
            return HBox(label, textField)
        }

        fun integerField(value: Int?, onChange: (Int?) -> Unit): TextField =
            TextField(value?.toString().orEmpty()).apply {
                prefColumnCount = 5
                allowOnlyNonNegativeIntegers()
                textProperty().addListener { _, _, newValue ->
                    onChange(newValue.toIntOrNull())
                }
            }

        /**
         * Applies [onValueChanged] on every slider movement while deferring [onCommit] until the
         * interaction is finished (drag release or focus loss).
         */
        fun configureSliderDeferredCommit(
            slider: Slider,
            onValueChanged: (Number) -> Unit,
            onCommit: () -> Unit,
        ) {
            var pendingCommit = false

            fun commitIfNeeded() {
                if (pendingCommit) {
                    pendingCommit = false
                    onCommit()
                }
            }

            slider.valueProperty().addListener { _, _, newValue ->
                onValueChanged(newValue)
                pendingCommit = true
            }
            slider.valueChangingProperty().addListener { _, wasChanging, isChanging ->
                if (wasChanging && !isChanging) {
                    commitIfNeeded()
                }
            }
            slider.focusedProperty().addListener { _, wasFocused, isFocused ->
                if (wasFocused && !isFocused) {
                    commitIfNeeded()
                }
            }
        }

        fun labeledField(labelText: String, field: TextField): VBox =
            VBox(4.0, Label(labelText).apply { style = "-fx-text-fill: -tc-text-muted;" }, field)
    }

}
