package com.tabletopcontrol.core.ui.color

import javafx.scene.paint.Color
import kotlin.math.roundToInt

/**
 * Shared hex/color conversion helpers.
 */
object ColorHexCodec {
    /** Converts [color] to uppercase `#RRGGBB`. */
    fun colorToHex(color: Color): String {
        fun channel(value: Double): Int = (value * 255.0).roundToInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(
            channel(color.red),
            channel(color.green),
            channel(color.blue),
        )
    }

    /** Parses [hex] with JavaFX color parsing rules. */
    fun hexToColor(hex: String): Color = Color.web(hex)

    /** Safely parses [hex], returning `null` for invalid input. */
    fun parseOrNull(hex: String?): Color? =
        hex?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { hexToColor(it) }.getOrNull() }

    /** Safely parses [hex], falling back to [fallback] when invalid. */
    fun parseOrDefault(hex: String?, fallback: Color): Color = parseOrNull(hex) ?: fallback
}
