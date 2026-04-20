package com.tabletopcontrol.light.ui

import com.tabletopcontrol.light.LightController
import com.tabletopcontrol.light.LightOperationResult
import javafx.application.Platform
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kotlin.math.roundToInt

internal class LightEffectParamsSection(
    private val controller: LightController,
    private val feedback: LightOperatorFeedbackPresenter,
) {
    fun createSection(): LightSection {
        val speedValueLabel = Label("${(controller.effectSpeed * 100) / 255} %")
        val intensityValueLabel = Label("${(controller.effectIntensity * 100) / 255} %")
        val speedNameLabel = Label("${controller.effect.speedName}:")
        val intensityNameLabel = Label("${controller.effect.intensityName}:")

        val speedSlider = Slider(0.0, 100.0, controller.effectSpeed * 100.0 / 255.0).apply {
            isShowTickMarks = true
            isShowTickLabels = true
            majorTickUnit = 50.0
            blockIncrement = 5.0
            tooltip = Tooltip("Effect speed parameter (WLED sx) - meaning depends on the selected effect")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newValue ->
                val speed = (newValue.toDouble() * 255.0 / 100.0).roundToInt().coerceIn(0, 255)
                when (val result = controller.setEffectSpeed(speed)) {
                    LightOperationResult.Applied ->
                        speedValueLabel.text = "${(speed * 100) / 255} %"
                    is LightOperationResult.Failure ->
                        feedback.showFailureDialog(
                            owner = this.scene?.window,
                            title = "Light Effect Error",
                            failure = result,
                        )
                }
            }
        }

        val intensitySlider = Slider(0.0, 100.0, controller.effectIntensity * 100.0 / 255.0).apply {
            isShowTickMarks = true
            isShowTickLabels = true
            majorTickUnit = 50.0
            blockIncrement = 5.0
            tooltip = Tooltip("Effect intensity parameter (WLED ix) - meaning depends on the selected effect")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newValue ->
                val intensity = (newValue.toDouble() * 255.0 / 100.0).roundToInt().coerceIn(0, 255)
                when (val result = controller.setEffectIntensity(intensity)) {
                    LightOperationResult.Applied ->
                        intensityValueLabel.text = "${(intensity * 100) / 255} %"
                    is LightOperationResult.Failure ->
                        feedback.showFailureDialog(
                            owner = this.scene?.window,
                            title = "Light Effect Error",
                            failure = result,
                        )
                }
            }
        }

        var lastEffect = controller.effect
        val listener = controller.addChangeListener {
            val currentEffect = controller.effect
            if (currentEffect != lastEffect) {
                lastEffect = currentEffect
                Platform.runLater {
                    speedNameLabel.text = "${currentEffect.speedName}:"
                    intensityNameLabel.text = "${currentEffect.intensityName}:"
                }
            }
        }

        val speedRow = HBox(8.0, speedSlider, speedValueLabel).apply {
            HBox.setHgrow(speedSlider, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }
        val intensityRow = HBox(8.0, intensitySlider, intensityValueLabel).apply {
            HBox.setHgrow(intensitySlider, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }

        return LightSection(
            node = VBox(
                4.0,
                speedNameLabel,
                speedRow,
                intensityNameLabel,
                intensityRow,
            ),
            dispose = { controller.removeChangeListener(listener) },
        )
    }
}
