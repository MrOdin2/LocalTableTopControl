package com.tabletopcontrol.light

/**
 * Enumeration of lighting effects available for ambient light control.
 *
 * Each entry carries a human-readable [displayName] suitable for display in a
 * combo-box and a [wledEffectId] that maps to the corresponding effect index in
 * the [WLED JSON API](https://kno.wled.ge/interfaces/json-api/).
 *
 * WLED effect IDs are stable across recent firmware releases:
 * - `0`  — Solid (no effect)
 * - `2`  — Breathe (slow fade in/out)
 * - `9`  — Rainbow Cycle
 * - `32` — Strobe
 * - `45` — Fire 2012
 * - `80` — Lake (blue ambient wash used as "Ocean" approximation)
 */
enum class LightEffect(val displayName: String, val wledEffectId: Int) {
    NONE("None", 0),
    FADE("Fade", 2),
    STROBE("Strobe", 32),
    RAINBOW("Rainbow", 9),
    FIRE("Fire", 45),
    OCEAN("Ocean", 80),
}
