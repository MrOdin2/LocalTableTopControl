package com.tabletopcontrol.light

/**
 * Enumeration of lighting effects available for ambient light control.
 *
 * Each entry carries a human-readable [displayName] suitable for display in a
 * combo-box and a [wledEffectId] that maps to the corresponding effect index in
 * the [WLED JSON API](https://kno.wled.ge/interfaces/json-api/).
 *
 */
enum class LightEffect(val displayName: String, val wledEffectId: Int) {
    NONE("None", 0),
    FADE("Breathe", 2),
    STROBE("Strobe", 23),
    RAINBOW("Rainbow", 9),
    FIRE("Fire", 45),
    CANDLE("Candle", 88),
    OCEAN("Ocean", 101),
}
