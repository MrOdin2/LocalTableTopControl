package com.tabletopcontrol.core.ui

import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.TextFormatter
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.util.converter.IntegerStringConverter
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

    }

}