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
 * The three accent colours are expressed as CSS hex strings (e.g. `"#1565c0"`) and
 * are injected into every scene as CSS looked-up colour variables so that any node
 * in the scene graph can reference them by name in inline or class-based styles.
 *
 * @param mode           overall light / dark palette selection.
 * @param primaryColor   CSS hex string for the primary accent colour (e.g. action buttons).
 * @param secondaryColor CSS hex string for the secondary accent colour (e.g. success states).
 * @param tertiaryColor  CSS hex string for the tertiary accent colour (e.g. warnings).
 */
data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.LIGHT,
    val primaryColor: String = "#1565c0",
    val secondaryColor: String = "#2e7d32",
    val tertiaryColor: String = "#e65100",
) {
    companion object {
        /** Default accent colours for the light theme. */
        val LIGHT_DEFAULTS = ThemeConfig(
            mode = ThemeMode.LIGHT,
            primaryColor = "#1565c0",
            secondaryColor = "#2e7d32",
            tertiaryColor = "#e65100",
        )

        /** Default accent colours for the dark theme. */
        val DARK_DEFAULTS = ThemeConfig(
            mode = ThemeMode.DARK,
            primaryColor = "#82b1ff",
            secondaryColor = "#69f0ae",
            tertiaryColor = "#ffd740",
        )
    }
}
