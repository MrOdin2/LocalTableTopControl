package com.tabletopcontrol.light.ui

import com.tabletopcontrol.core.ui.color.ColorEditorDialog
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.light.LightController
import com.tabletopcontrol.light.LightEffect
import com.tabletopcontrol.light.LightOperationResult
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.util.StringConverter

internal class LightMainControlsSection(
    private val controller: LightController,
    private val feedback: LightOperatorFeedbackPresenter,
    private val effectParamsNode: Node,
) {
    fun createSection(): LightSection =
        LightSection(
            VBox(
                8.0,
                buildPowerRow(),
                buildColorRow(),
                buildEffectRow(),
                effectParamsNode,
                buildColorCyclingRow(),
                buildBrightnessRow(),
            ),
        )

    private fun buildPowerRow(): HBox {
        val check = CheckBox("Power on").apply {
            isSelected = controller.power
            tooltip = Tooltip("Turn the WLED device on or off")
            selectedProperty().addListener { _, _, on ->
                controller.setPower(on)
            }
        }
        return HBox(8.0, check).apply { alignment = Pos.CENTER_LEFT }
    }

    private fun buildColorRow(): HBox {
        var appliedColor = ColorHexCodec.parseOrDefault(controller.color, Color.WHITE)
        val colorButton = Button().apply {
            tooltip = Tooltip("Select the ambient light color")
            maxWidth = Double.MAX_VALUE
            alignment = Pos.CENTER_LEFT
        }

        val swatch = Region().apply {
            minWidth = 16.0
            minHeight = 16.0
            prefWidth = 16.0
            prefHeight = 16.0
            style = SWATCH_STYLE_BASE
        }
        val valueLabel = Label()

        fun refreshButtonLabel(color: Color) {
            val hex = ColorHexCodec.colorToHex(color)
            swatch.style = "-fx-background-color: $hex; $SWATCH_STYLE_BASE"
            valueLabel.text = hex
        }

        refreshButtonLabel(appliedColor)
        colorButton.graphic = HBox(8.0, swatch, valueLabel).apply { alignment = Pos.CENTER_LEFT }
        colorButton.text = ""
        colorButton.setOnAction {
            val selected = ColorEditorDialog.showDialog(
                owner = colorButton.scene?.window,
                title = "Set Light Color",
                prompt = "Select the ambient light color",
                initialColor = appliedColor,
            )
            if (selected != null) {
                when (val result = controller.setColor(ColorHexCodec.colorToHex(selected))) {
                    LightOperationResult.Applied -> {
                        appliedColor = selected
                        refreshButtonLabel(appliedColor)
                    }
                    is LightOperationResult.Failure ->
                        feedback.showFailureDialog(
                            owner = colorButton.scene?.window,
                            title = "Light Color Error",
                            failure = result,
                        )
                }
            }
        }

        return HBox(8.0, Label("Color:"), colorButton).apply {
            HBox.setHgrow(colorButton, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }
    }

    private fun buildEffectRow(): HBox {
        val combo = ComboBox<LightEffect>().apply {
            items.setAll(*LightEffect.entries.toTypedArray())
            value = controller.effect
            converter = object : StringConverter<LightEffect>() {
                override fun toString(effect: LightEffect?) = effect?.displayName ?: ""

                override fun fromString(value: String?) =
                    LightEffect.entries.firstOrNull { it.displayName == value } ?: LightEffect.NONE
            }
            tooltip = Tooltip("Select a lighting effect")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newEffect ->
                if (newEffect != null) {
                    controller.setEffect(newEffect)
                }
            }
        }
        return HBox(8.0, Label("Effect:"), combo).apply {
            HBox.setHgrow(combo, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }
    }

    private fun buildColorCyclingRow(): HBox {
        val check = CheckBox("Enable color cycling").apply {
            isSelected = controller.colorCycling
            tooltip = Tooltip("Automatically cycle through colors (uses WLED Rainbow effect)")
            selectedProperty().addListener { _, _, selected ->
                controller.setColorCycling(selected)
            }
        }
        return HBox(8.0, check).apply { alignment = Pos.CENTER_LEFT }
    }

    private fun buildBrightnessRow(): VBox {
        val valueLabel = Label(brightnessLabel(controller.brightness))
        val slider = Slider(0.0, 100.0, controller.brightness * 100.0).apply {
            isShowTickMarks = true
            isShowTickLabels = true
            majorTickUnit = 50.0
            blockIncrement = 5.0
            tooltip = Tooltip("Adjust the output brightness (0 - 100 %)")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newValue ->
                val normalized = newValue.toDouble() / 100.0
                when (val result = controller.setBrightness(normalized)) {
                    LightOperationResult.Applied ->
                        valueLabel.text = brightnessLabel(normalized)
                    is LightOperationResult.Failure ->
                        feedback.showFailureDialog(
                            owner = this.scene?.window,
                            title = "Light Brightness Error",
                            failure = result,
                        )
                }
            }
        }

        val sliderRow = HBox(8.0, slider, valueLabel).apply {
            HBox.setHgrow(slider, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }

        return VBox(4.0, Label("Brightness:"), sliderRow)
    }

    private fun brightnessLabel(value: Double): String = "${(value * 100).toInt()} %"

    private companion object {
        private const val SWATCH_STYLE_BASE: String =
            "-fx-border-color: -tc-border; -fx-border-radius: 3; -fx-background-radius: 3;"
    }
}
