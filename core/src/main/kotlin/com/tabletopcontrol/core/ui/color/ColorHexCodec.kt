package com.tabletopcontrol.core.ui.color

import javafx.scene.paint.Color
import kotlin.math.roundToInt

/**
 * Shared conversion helpers between JavaFX [Color] and CSS hex strings.
 */
object ColorHexCodec {
    /**
     * Converts [color] to `#RRGGBB`.
     */
    fun toHex(color: Color, uppercase: Boolean = true): String {
        fun channel(v: Double) = (v * 255).roundToInt().coerceIn(0, 255)
        val hex = "#%02x%02x%02x".format(channel(color.red), channel(color.green), channel(color.blue))
        return if (uppercase) hex.uppercase() else hex
    }

    /**
     * Parses [hex] via JavaFX [Color.web], wrapped in [Result].
     */
    fun parse(hex: String): Result<Color> = runCatching { Color.web(hex.trim()) }
}

