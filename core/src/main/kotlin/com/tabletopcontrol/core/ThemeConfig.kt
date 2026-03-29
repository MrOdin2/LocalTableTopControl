package com.tabletopcontrol.core

/**
 * Selects the overall visual style of the application.
 *
 * - [LIGHT] — light (default) theme; standard JavaFX Modena colours.
 * - [DARK]  — dark theme with dark panel backgrounds and lighter text.
 */
enum class ThemeMode { LIGHT, DARK }

/**
 * Immutable snapshot of the active visual theme.
 *
 * The four user-configurable colours are expressed as CSS hex strings
 * (e.g. `"#1565c0"`) and are injected into every scene as CSS looked-up
 * colour variables so that any node in the scene graph can reference them
 * by name in inline or class-based styles.
 *
 * @param mode         overall light / dark palette selection.
 * @param accentColor  CSS hex string for buttons and interactive highlights.
 * @param bgColor      CSS hex string for the window / scene background.
 * @param surfaceColor CSS hex string for panel and card surfaces.
 * @param borderColor  CSS hex string for panel edges and control borders.
 */
data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.LIGHT,
    val accentColor: String = "#1565c0",
    val bgColor: String = "#f4f4f4",
    val surfaceColor: String = "#ffffff",
    val borderColor: String = "#c8c8c8",
) {
    companion object {
        /** Default colours for the light theme. */
        val LIGHT_DEFAULTS = ThemeConfig(
            mode = ThemeMode.LIGHT,
            accentColor = "#1565c0",
            bgColor = "#f4f4f4",
            surfaceColor = "#ffffff",
            borderColor = "#c8c8c8",
        )

        /** Default colours for the dark theme. */
        val DARK_DEFAULTS = ThemeConfig(
            mode = ThemeMode.DARK,
            accentColor = "#82b1ff",
            bgColor = "#1e1e2e",
            surfaceColor = "#2d2d3e",
            borderColor = "#555577",
        )

        /** Matches `#RGB` or `#RRGGBB` CSS hex colour strings. */
        private val HEX_COLOR_REGEX = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")

        /** Returns `true` if [color] is a valid `#RGB` or `#RRGGBB` hex string. */
        fun isValidHexColor(color: String): Boolean = color.matches(HEX_COLOR_REGEX)
    }
}
