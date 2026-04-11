package com.tabletopcontrol.core.ui.color

import javafx.scene.paint.Color

/**
 * Shared readable-foreground helpers for colored backgrounds.
 */
object ColorContrast {
    private const val DEFAULT_THRESHOLD = 0.55
    const val BLACK_HEX: String = "#000000"
    const val WHITE_HEX: String = "#FFFFFF"

    /**
     * Returns a readable black/white foreground hex for [backgroundHex].
     *
     * @param backgroundHex background color hex string.
     * @param threshold luminance threshold used to switch between black/white.
     * @return [BLACK_HEX] or [WHITE_HEX].
     */
    fun textColorHexForBackgroundHex(backgroundHex: String, threshold: Double = DEFAULT_THRESHOLD): String {
        val background = ColorHexCodec.parseOrDefault(backgroundHex, Color.GRAY)
        return textColorHexForBackground(background, threshold)
    }

    /**
     * Returns a readable black/white foreground hex for [background].
     *
     * @param background background color.
     * @param threshold luminance threshold used to switch between black/white.
     * @return [BLACK_HEX] or [WHITE_HEX].
     */
    fun textColorHexForBackground(background: Color, threshold: Double = DEFAULT_THRESHOLD): String {
        val luminance = 0.299 * background.red + 0.587 * background.green + 0.114 * background.blue
        return if (luminance > threshold) BLACK_HEX else WHITE_HEX
    }
}
