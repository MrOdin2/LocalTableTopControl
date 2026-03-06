package com.tabletopcontrol.light

/**
 * Enumeration of lighting effects available for ambient light control.
 *
 * Each entry carries a human-readable [displayName] suitable for display in a combo-box.
 */
enum class LightEffect(val displayName: String) {
    NONE("None"),
    FADE("Fade"),
    STROBE("Strobe"),
    RAINBOW("Rainbow"),
    FIRE("Fire"),
    OCEAN("Ocean"),
}
