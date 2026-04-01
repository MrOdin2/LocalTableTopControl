package com.tabletopcontrol.light

/**
 * Enumeration of lighting effects available for ambient light control.
 *
 * Each entry carries a human-readable [displayName] suitable for display in a
 * combo-box and a [wledEffectId] that maps to the corresponding effect index in
 * the [WLED JSON API](https://kno.wled.ge/interfaces/json-api/).
 *
 * Only effects that work on a 1D LED ring (as opposed to 2D LED matrices) are
 * included here.  Effect behaviour can be further tuned via the speed and
 * intensity parameters in [LightController].
 *
 * @see <a href="https://kno.wled.ge/features/effects/">WLED effect list</a>
 */
enum class LightEffect(val displayName: String, val wledEffectId: Int) {
    NONE("None", 0),
    BLINK("Blink", 1),
    FADE("Breathe", 2),
    COLOR_WIPE("Color Wipe", 3),
    COLORLOOP("Color Loop", 8),
    RAINBOW("Rainbow", 9),
    SCAN("Scan", 10),
    DUAL_SCAN("Dual Scan", 11),
    THEATER("Theater", 13),
    THEATER_RAINBOW("Theater Rainbow", 14),
    RUNNING("Running", 15),
    TWINKLE("Twinkle", 17),
    SPARKLE("Sparkle", 20),
    STROBE("Strobe", 23),
    STROBE_RAINBOW("Strobe Rainbow", 24),
    CHASE_COLOR("Chase Color", 28),
    RAINBOW_RUNNER("Rainbow Runner", 33),
    SCANNER("Scanner", 40),
    FIREWORKS("Fireworks", 42),
    FIRE("Fire", 45),
    LIGHTNING("Lightning", 57),
    METEOR("Meteor", 76),
    SMOOTH_METEOR("Smooth Meteor", 77),
    GLITTER("Glitter", 87),
    CANDLE("Candle", 88),
    BOUNCING_BALLS("Bouncing Balls", 91),
    SINELON("Sinelon", 92),
    DRIP("Drip", 96),
    HEARTBEAT("Heartbeat", 100),
    OCEAN("Ocean", 101),
    CANDLE_MULTI("Candle Multi", 102),
}
