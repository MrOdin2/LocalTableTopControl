package com.tabletopcontrol.core.ui.color

import javafx.scene.paint.Color
import kotlin.math.roundToInt

/**
 * Shared hex/color conversion helpers.
 */
object ColorHexCodec {
    /**
     * Converts a JavaFX [Color] to an uppercase `#RRGGBB` string.
     *
     * @param color source color.
     * @return uppercase CSS hex color string.
     */
    fun colorToHex(color: Color): String {
        fun channel(value: Double): Int = (value * 255.0).roundToInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(
            channel(color.red),
            channel(color.green),
            channel(color.blue),
        )
    }

    /**
     * Parses [hex] using JavaFX `Color.web(...)` rules.
     *
     * @param hex CSS-like color string.
     * @return parsed [Color].
     */
    fun hexToColor(hex: String): Color = Color.web(hex)

    /**
     * Safely parses [hex], returning `null` for invalid or blank values.
     *
     * @param hex CSS-like color string; blank or whitespace-only values are treated as invalid.
     * @return parsed [Color], or `null` when parsing fails.
     */
    fun parseOrNull(hex: String?): Color? =
        hex?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { hexToColor(it) }.getOrNull() }

    /**
     * Safely parses [hex], falling back to [fallback] when parsing fails.
     *
     * @param hex CSS-like color string.
     * @param fallback fallback color used when parsing fails.
     * @return parsed [Color], or [fallback] when invalid.
     */
    fun parseOrDefault(hex: String?, fallback: Color): Color = parseOrNull(hex) ?: fallback
}
