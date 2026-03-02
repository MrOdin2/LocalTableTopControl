package com.tabletopcontrol.light

import com.tabletopcontrol.core.DmPlugin
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.control.Slider
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.util.StringConverter

/**
 * DM-panel plugin for controlling physical ambient lighting.
 *
 * Provides a UI-only control panel with:
 * - **Color select** — a [ColorPicker] to choose the light color.
 * - **Effect select** — a [ComboBox] to pick from the available [LightEffect]s.
 * - **Color cycling** — a [CheckBox] to enable automatic color cycling.
 * - **Brightness** — a [Slider] to set output brightness (0 – 100 %).
 *
 * All state is held by a [LightController]; this class only handles the
 * JavaFX binding between controls and the controller.
 *
 * Integration with WLED or similar hardware is intentionally deferred.
 */
class LightPlugin : DmPlugin {

    override val displayName: String = "Lights"

    /** Pure-Kotlin state controller; no JavaFX dependencies. */
    private val controller = LightController()

    override fun createView(): Node {
        val root = VBox(8.0).apply { padding = Insets(10.0) }

        root.children.addAll(
            Label("Ambient Light Controls"),
            Separator(),
            buildColorRow(),
            buildEffectRow(),
            buildColorCyclingRow(),
            buildBrightnessRow(),
        )

        return ScrollPane(root).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        }
    }

    // -------------------------------------------------------------------------
    // Section builders
    // -------------------------------------------------------------------------

    /**
     * Builds the color-picker row.
     *
     * The [ColorPicker] is pre-seeded with the controller's current color and
     * writes back to the controller on every selection change.
     */
    private fun buildColorRow(): HBox {
        val picker = ColorPicker(hexToColor(controller.color)).apply {
            tooltip = Tooltip("Select the ambient light color")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newColor ->
                controller.setColor(colorToHex(newColor))
            }
        }
        return HBox(8.0, Label("Color:"), picker).apply {
            HBox.setHgrow(picker, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }
    }

    /**
     * Builds the effect-selector row.
     *
     * The [ComboBox] lists all [LightEffect] values by their [LightEffect.displayName]
     * and writes the selection back to the controller.
     */
    private fun buildEffectRow(): HBox {
        val combo = ComboBox<LightEffect>().apply {
            items.setAll(*LightEffect.entries.toTypedArray())
            value = controller.effect
            converter = object : StringConverter<LightEffect>() {
                override fun toString(e: LightEffect?) = e?.displayName ?: ""
                override fun fromString(s: String?) =
                    LightEffect.entries.firstOrNull { it.displayName == s } ?: LightEffect.NONE
            }
            tooltip = Tooltip("Select a lighting effect")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newEffect ->
                if (newEffect != null) controller.setEffect(newEffect)
            }
        }
        return HBox(8.0, Label("Effect:"), combo).apply {
            HBox.setHgrow(combo, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }
    }

    /**
     * Builds the color-cycling toggle row.
     *
     * When the [CheckBox] is selected, automatic color cycling is enabled in
     * the controller; the color picker is disabled because the cycle overrides it.
     */
    private fun buildColorCyclingRow(): HBox {
        val check = CheckBox("Enable color cycling").apply {
            isSelected = controller.colorCycling
            tooltip = Tooltip("Automatically cycle through colors")
            selectedProperty().addListener { _, _, selected ->
                controller.setColorCycling(selected)
            }
        }
        return HBox(8.0, check).apply {
            alignment = Pos.CENTER_LEFT
        }
    }

    /**
     * Builds the brightness-slider row.
     *
     * The [Slider] ranges from 0 to 100 (percent) and is displayed with major
     * tick marks at 0, 50, and 100.  Values are written to the controller
     * normalised to the range `0.0–1.0`.
     */
    private fun buildBrightnessRow(): VBox {
        val valueLabel = Label(brightnessLabel(controller.brightness))

        val slider = Slider(0.0, 100.0, controller.brightness * 100.0).apply {
            isShowTickMarks = true
            isShowTickLabels = true
            majorTickUnit = 50.0
            blockIncrement = 5.0
            tooltip = Tooltip("Adjust the output brightness (0 – 100 %)")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newValue ->
                val normalised = newValue.toDouble() / 100.0
                controller.setBrightness(normalised)
                valueLabel.text = brightnessLabel(normalised)
            }
        }

        val sliderRow = HBox(8.0, slider, valueLabel).apply {
            HBox.setHgrow(slider, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }

        return VBox(4.0, Label("Brightness:"), sliderRow)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Converts a JavaFX [Color] to an uppercase `#RRGGBB` hex string. */
    private fun colorToHex(color: Color): String {
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }

    /** Parses a `#RRGGBB` or `#RGB` hex string into a JavaFX [Color]. */
    private fun hexToColor(hex: String): Color = Color.web(hex)

    /** Formats a normalised brightness value as a percentage label. */
    private fun brightnessLabel(value: Double): String = "${(value * 100).toInt()} %"
}
