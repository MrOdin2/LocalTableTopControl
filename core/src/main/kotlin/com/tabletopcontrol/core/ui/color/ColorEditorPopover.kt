package com.tabletopcontrol.core.ui.color

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.canvas.Canvas
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.stage.Window
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reusable wheel-based color editor used by plugins.
 */
object ColorEditorPopover {
    /** Practical hue upper bound kept below 360 because 360 maps to 0 in HSB. */
    private const val MAX_HUE_BELOW_360 = 359.999
    private const val WHEEL_IMAGE_CACHE_MAX_SIZE = 16
    private const val MARKER_CONTRAST_BRIGHTNESS_THRESHOLD = 0.45
    private const val KEYBOARD_HUE_STEP_DEGREES = 3.0
    private const val KEYBOARD_SATURATION_STEP = 0.02

    /**
     * Shows a modal color editor and returns the chosen color when confirmed.
     *
     * @param owner owner window for modality.
     * @param title dialog title.
     * @param prompt explanatory text shown above controls.
     * @param initialColor initially selected color.
     * @return selected color when confirmed, otherwise `null`.
     */
    fun showDialog(
        owner: Window?,
        title: String,
        prompt: String,
        initialColor: Color,
    ): Color? {
        var draftColor = clampColor(initialColor)
        val wheelSize = 220.0
        val wheelRadius = wheelSize / 2.0

        val wheel = Canvas(wheelSize, wheelSize)
        val marker = Circle(6.0).apply {
            fill = Color.TRANSPARENT
            stroke = Color.WHITE
            strokeWidth = 2.0
            isMouseTransparent = true
        }
        val wheelPane = Pane(wheel, marker).apply {
            prefWidth = wheelSize
            prefHeight = wheelSize
            minWidth = wheelSize
            minHeight = wheelSize
            maxWidth = wheelSize
            maxHeight = wheelSize
            isFocusTraversable = true
            accessibleText = "Color wheel. Use arrow keys to adjust hue and saturation."
            accessibleHelp = "Press arrow keys to adjust: Left/Right for hue, Up/Down for saturation."
        }

        val brightnessSlider = Slider(0.0, 100.0, valuePercent(draftColor)).apply {
            tooltip = Tooltip("Value (brightness)")
            maxWidth = Double.MAX_VALUE
            majorTickUnit = 25.0
            blockIncrement = 1.0
            isShowTickMarks = true
            isShowTickLabels = true
        }
        val valuePercentLabel = Label("${valuePercent(draftColor).toInt()}%")
        val preview = Circle(14.0, draftColor)

        val hexField = TextField(ColorHexCodec.colorToHex(draftColor)).apply {
            prefColumnCount = 8
            promptText = "#RRGGBB"
            tooltip = Tooltip("Hex color")
        }
        val rField = TextField((draftColor.red * 255.0).roundToInt().toString()).apply {
            prefColumnCount = 4
            tooltip = Tooltip("Red (0–255)")
        }
        val gField = TextField((draftColor.green * 255.0).roundToInt().toString()).apply {
            prefColumnCount = 4
            tooltip = Tooltip("Green (0–255)")
        }
        val bField = TextField((draftColor.blue * 255.0).roundToInt().toString()).apply {
            prefColumnCount = 4
            tooltip = Tooltip("Blue (0–255)")
        }
        val hField = TextField(normalizedHueDegrees(draftColor).roundToInt().toString()).apply {
            prefColumnCount = 4
            tooltip = Tooltip("Hue (0–359)")
        }
        val sField = TextField((draftColor.saturation * 100.0).roundToInt().toString()).apply {
            prefColumnCount = 4
            tooltip = Tooltip("Saturation (0–100%)")
        }
        val vField = TextField((draftColor.brightness * 100.0).roundToInt().toString()).apply {
            prefColumnCount = 4
            tooltip = Tooltip("Value (0–100%)")
        }

        val wheelImageCache = object : LinkedHashMap<Int, javafx.scene.image.WritableImage>(WHEEL_IMAGE_CACHE_MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, javafx.scene.image.WritableImage>?): Boolean =
                size > WHEEL_IMAGE_CACHE_MAX_SIZE
        }
        fun wheelImageFor(valuePercent: Int): javafx.scene.image.WritableImage = wheelImageCache.getOrPut(valuePercent) {
            val image = javafx.scene.image.WritableImage(wheelSize.toInt(), wheelSize.toInt())
            val pixels = image.pixelWriter
            val value = valuePercent / 100.0
            for (y in 0 until wheelSize.toInt()) {
                for (x in 0 until wheelSize.toInt()) {
                    val dx = x + 0.5 - wheelRadius
                    val dy = y + 0.5 - wheelRadius
                    val distance = sqrt(dx * dx + dy * dy)
                    if (distance <= wheelRadius) {
                        val saturation = (distance / wheelRadius).coerceIn(0.0, 1.0)
                        val hue = ((atan2(dy, dx) * 180.0 / PI) + 360.0) % 360.0
                        pixels.setColor(x, y, Color.hsb(hue, saturation, value))
                    } else {
                        pixels.setColor(x, y, Color.TRANSPARENT)
                    }
                }
            }
            image
        }

        var isUpdatingInputs = false
        var lastWheelValuePercent = -1

        fun markerFromColor(color: Color) {
            val hueRad = color.hue * PI / 180.0
            val radius = color.saturation * wheelRadius
            marker.centerX = wheelRadius + cos(hueRad) * radius
            marker.centerY = wheelRadius + sin(hueRad) * radius
            marker.stroke = if (color.brightness < MARKER_CONTRAST_BRIGHTNESS_THRESHOLD) Color.WHITE else Color.BLACK
        }

        fun redrawWheelIfNeeded(value: Double) {
            val percent = (value * 100.0).roundToInt().coerceIn(0, 100)
            if (percent == lastWheelValuePercent) return
            val gc = wheel.graphicsContext2D
            gc.clearRect(0.0, 0.0, wheelSize, wheelSize)
            gc.drawImage(wheelImageFor(percent), 0.0, 0.0)
            lastWheelValuePercent = percent
        }

        fun applyDraftColor(color: Color) {
            draftColor = clampColor(color)
            isUpdatingInputs = true
            try {
                brightnessSlider.value = valuePercent(draftColor)
                valuePercentLabel.text = "${valuePercent(draftColor).toInt()}%"
                preview.fill = draftColor
                hexField.text = ColorHexCodec.colorToHex(draftColor)
                rField.text = (draftColor.red * 255.0).roundToInt().toString()
                gField.text = (draftColor.green * 255.0).roundToInt().toString()
                bField.text = (draftColor.blue * 255.0).roundToInt().toString()
                hField.text = normalizedHueDegrees(draftColor).roundToInt().toString()
                sField.text = (draftColor.saturation * 100.0).roundToInt().toString()
                vField.text = (draftColor.brightness * 100.0).roundToInt().toString()
            } finally {
                isUpdatingInputs = false
            }
            redrawWheelIfNeeded(draftColor.brightness)
            markerFromColor(draftColor)
        }

        fun updateFromWheel(x: Double, y: Double) {
            val dx = x - wheelRadius
            val dy = y - wheelRadius
            val distance = sqrt(dx * dx + dy * dy).coerceAtMost(wheelRadius)
            val saturation = (distance / wheelRadius).coerceIn(0.0, 1.0)
            val hue = ((atan2(dy, dx) * 180.0 / PI) + 360.0) % 360.0
            applyDraftColor(Color.hsb(hue, saturation, brightnessSlider.value / 100.0))
        }

        fun parseIntField(field: TextField, min: Int, max: Int): Int? =
            field.text.trim().toIntOrNull()?.coerceIn(min, max)

        fun applyRgbFieldValues() {
            val red = parseIntField(rField, 0, 255) ?: return
            val green = parseIntField(gField, 0, 255) ?: return
            val blue = parseIntField(bField, 0, 255) ?: return
            applyDraftColor(Color.rgb(red, green, blue))
        }

        fun applyHsvFieldValues() {
            val hue = hField.text.trim().toDoubleOrNull()?.coerceIn(0.0, MAX_HUE_BELOW_360) ?: return
            val saturation = sField.text.trim().toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: return
            val value = vField.text.trim().toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: return
            applyDraftColor(Color.hsb(hue, saturation / 100.0, value / 100.0))
        }

        wheelPane.setOnMousePressed { updateFromWheel(it.x, it.y) }
        wheelPane.setOnMouseDragged { updateFromWheel(it.x, it.y) }
        wheelPane.setOnKeyPressed { event ->
            val next = when (event.code) {
                KeyCode.LEFT -> Color.hsb((draftColor.hue - KEYBOARD_HUE_STEP_DEGREES + 360.0) % 360.0, draftColor.saturation, draftColor.brightness)
                KeyCode.RIGHT -> Color.hsb((draftColor.hue + KEYBOARD_HUE_STEP_DEGREES) % 360.0, draftColor.saturation, draftColor.brightness)
                KeyCode.UP -> Color.hsb(draftColor.hue, (draftColor.saturation + KEYBOARD_SATURATION_STEP).coerceIn(0.0, 1.0), draftColor.brightness)
                KeyCode.DOWN -> Color.hsb(draftColor.hue, (draftColor.saturation - KEYBOARD_SATURATION_STEP).coerceIn(0.0, 1.0), draftColor.brightness)
                else -> null
            }
            if (next != null) {
                applyDraftColor(next)
                event.consume()
            }
        }
        brightnessSlider.valueProperty().addListener { _, _, newValue ->
            if (isUpdatingInputs) return@addListener
            applyDraftColor(Color.hsb(draftColor.hue, draftColor.saturation, newValue.toDouble() / 100.0))
        }
        hexField.setOnAction {
            ColorHexCodec.parseOrNull(hexField.text)?.let { applyDraftColor(it) }
        }
        listOf(rField, gField, bField).forEach { field ->
            field.setOnAction { applyRgbFieldValues() }
        }
        listOf(hField, sField, vField).forEach { field ->
            field.setOnAction { applyHsvFieldValues() }
        }

        val inputs = GridPane().apply {
            hgap = 6.0
            vgap = 6.0
            add(Label("Hex"), 0, 0)
            add(hexField, 1, 0, 3, 1)
            add(Label("R"), 0, 1)
            add(rField, 1, 1)
            add(Label("G"), 2, 1)
            add(gField, 3, 1)
            add(Label("B"), 4, 1)
            add(bField, 5, 1)
            add(Label("H"), 0, 2)
            add(hField, 1, 2)
            add(Label("S"), 2, 2)
            add(sField, 3, 2)
            add(Label("V"), 4, 2)
            add(vField, 5, 2)
        }

        val content = VBox(
            8.0,
            Label(prompt),
            wheelPane,
            HBox(8.0, Label("Value"), brightnessSlider, valuePercentLabel).apply {
                alignment = Pos.CENTER_LEFT
                HBox.setHgrow(brightnessSlider, Priority.ALWAYS)
            },
            HBox(8.0, Label("Preview"), preview).apply { alignment = Pos.CENTER_LEFT },
            inputs,
        ).apply {
            padding = Insets(10.0)
            minWidth = 420.0
        }

        val dialog = Dialog<Color>().apply {
            this.title = title
            dialogPane.content = content
            dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
            if (owner != null) initOwner(owner)
            setResultConverter { button -> if (button == ButtonType.OK) draftColor else null }
        }

        applyDraftColor(draftColor)
        return dialog.showAndWait().orElse(null)
    }

    private fun clampColor(color: Color): Color =
        Color.hsb(normalizedHueDegrees(color), color.saturation.coerceIn(0.0, 1.0), color.brightness.coerceIn(0.0, 1.0))

    private fun normalizedHueDegrees(color: Color): Double =
        color.hue.takeUnless { it.isNaN() || it.isInfinite() }?.coerceIn(0.0, MAX_HUE_BELOW_360) ?: 0.0

    private fun valuePercent(color: Color): Double =
        (color.brightness * 100.0).takeUnless { it.isNaN() || it.isInfinite() }?.coerceIn(0.0, 100.0) ?: 0.0
}
