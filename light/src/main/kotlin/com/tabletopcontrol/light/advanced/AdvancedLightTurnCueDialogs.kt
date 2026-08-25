package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.core.ui.color.ColorEditorDialog
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.light.LightController
import com.tabletopcontrol.light.LightEffect
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Window
import javafx.util.StringConverter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal data class TrackedPlayerToken(
    val id: String,
    val name: String,
)

internal object AdvancedLightTurnCueDialogs {
    fun showTokenPicker(
        owner: Window?,
        title: String,
        header: String,
        candidates: List<TrackedPlayerToken>,
        initiallySelected: Set<String>,
        confirmText: String,
        emptyMessage: String,
    ): Set<String>? {
        val confirmButton = ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE)
        val checkBoxes = candidates
            .distinctBy(TrackedPlayerToken::id)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, TrackedPlayerToken::name).thenBy { it.id })
            .map { token ->
                CheckBox("${token.name} (${token.id})").apply {
                    isSelected = token.id in initiallySelected
                    userData = token.id
                }
            }
        val content = if (checkBoxes.isEmpty()) {
            Label(emptyMessage).apply { isWrapText = true }
        } else {
            ScrollPane(VBox(6.0).apply { children.addAll(checkBoxes) }).apply {
                isFitToWidth = true
                prefViewportHeight = 240.0
                hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            }
        }
        val dialog = Dialog<ButtonType>().apply {
            this.title = title
            headerText = header
            dialogPane.content = content
            dialogPane.buttonTypes.addAll(confirmButton, ButtonType.CANCEL)
            owner?.let(::initOwner)
        }
        if (checkBoxes.isEmpty()) {
            dialog.dialogPane.lookupButton(confirmButton).isDisable = true
        }
        if (dialog.showAndWait().orElse(null) != confirmButton) return null
        return checkBoxes
            .filter(CheckBox::isSelected)
            .mapNotNull { it.userData as? String }
            .toSet()
    }

    fun showTurnCueDialog(
        owner: Window?,
        segment: AdvancedLightSegmentState,
    ): AdvancedLightTurnCue? {
        val cue = segment.turnCue
        var selectedColor = cue.color
        val effectCombo = ComboBox<LightEffect>().apply {
            items.setAll(*LightEffect.entries.toTypedArray())
            converter = effectConverter()
            value = cue.effect
            maxWidth = Double.MAX_VALUE
        }
        val colorButton = Button(selectedColor).apply {
            maxWidth = Double.MAX_VALUE
            setOnAction {
                val selected = ColorEditorDialog.showDialog(
                    owner = scene?.window ?: owner,
                    title = "Turn Cue Color",
                    prompt = "Choose the cue color for ${segment.name}",
                    initialColor = ColorHexCodec.parseOrDefault(selectedColor, Color.WHITE),
                )
                if (selected != null) {
                    selectedColor = ColorHexCodec.colorToHex(selected)
                    text = selectedColor
                }
            }
        }
        val brightnessLabel = Label("${(cue.brightness * 100.0).roundToInt()} %")
        val brightnessSlider = Slider(0.0, 100.0, cue.brightness * 100.0).apply {
            isShowTickMarks = true
            isShowTickLabels = true
            majorTickUnit = 50.0
            blockIncrement = 5.0
            valueProperty().addListener { _, _, value ->
                brightnessLabel.text = "${value.toDouble().roundToInt()} %"
            }
        }
        val speedField = TextField(cue.effectSpeed.toString())
        val intensityField = TextField(cue.effectIntensity.toString())
        val durationCombo = ComboBox<AdvancedLightTurnCueDuration>().apply {
            items.setAll(*AdvancedLightTurnCueDuration.entries.toTypedArray())
            value = cue.duration
            maxWidth = Double.MAX_VALUE
        }
        val durationField = TextField(
            String.format(Locale.ROOT, "%.1f", cue.durationMillis / 1_000.0),
        ).apply {
            prefColumnCount = 6
        }
        fun refreshDurationAvailability() {
            durationField.isDisable = durationCombo.value != AdvancedLightTurnCueDuration.TIMED
        }
        durationCombo.valueProperty().addListener { _, _, _ -> refreshDurationAvailability() }
        refreshDurationAvailability()

        val content = VBox(
            8.0,
            Label("Effect"),
            effectCombo,
            Label("Color"),
            colorButton,
            Label("Brightness"),
            HBox(8.0, brightnessSlider, brightnessLabel).apply {
                alignment = Pos.CENTER_LEFT
                HBox.setHgrow(brightnessSlider, Priority.ALWAYS)
            },
            HBox(8.0, Label("Speed (0-255):"), speedField).apply {
                alignment = Pos.CENTER_LEFT
                HBox.setHgrow(speedField, Priority.ALWAYS)
            },
            HBox(8.0, Label("Intensity (0-255):"), intensityField).apply {
                alignment = Pos.CENTER_LEFT
                HBox.setHgrow(intensityField, Priority.ALWAYS)
            },
            Label("Duration"),
            durationCombo,
            HBox(8.0, Label("Play-once seconds:"), durationField).apply {
                alignment = Pos.CENTER_LEFT
                HBox.setHgrow(durationField, Priority.ALWAYS)
            },
        ).apply { prefWidth = 360.0 }

        val dialog = Dialog<ButtonType>().apply {
            title = "Configure Turn Cue"
            headerText = segment.name
            dialogPane.content = content
            dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
            owner?.let(::initOwner)
        }
        if (dialog.showAndWait().orElse(null) != ButtonType.OK) return null

        val durationSeconds = durationField.text.trim().replace(',', '.').toDoubleOrNull() ?: 1.5
        val durationMillis = (durationSeconds * 1_000.0)
            .roundToLong()
            .coerceIn(
                AdvancedLightTurnCue.MIN_DURATION_MILLIS,
                AdvancedLightTurnCue.MAX_DURATION_MILLIS,
            )
        return AdvancedLightTurnCue(
            effect = effectCombo.value ?: LightEffect.HEARTBEAT,
            color = selectedColor,
            brightness = (brightnessSlider.value / 100.0).coerceIn(0.0, 1.0),
            effectSpeed = speedField.text.toIntOrNull()?.coerceIn(0, 255)
                ?: LightController.DEFAULT_EFFECT_SPEED,
            effectIntensity = intensityField.text.toIntOrNull()?.coerceIn(0, 255)
                ?: LightController.DEFAULT_EFFECT_INTENSITY,
            duration = durationCombo.value ?: AdvancedLightTurnCueDuration.WHOLE_TURN,
            durationMillis = durationMillis,
        )
    }

    private fun effectConverter(): StringConverter<LightEffect> =
        object : StringConverter<LightEffect>() {
            override fun toString(effect: LightEffect?): String = effect?.displayName ?: ""

            override fun fromString(value: String?): LightEffect =
                LightEffect.entries.firstOrNull { it.displayName == value } ?: LightEffect.NONE
        }
}
