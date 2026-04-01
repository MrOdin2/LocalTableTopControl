package com.tabletopcontrol.light

/**
 * Enumeration of lighting effects available for ambient light control.
 *
 * Each entry carries a human-readable [displayName] suitable for display in a
 * combo-box, a [wledEffectId] that maps to the corresponding effect index in
 * the [WLED JSON API](https://kno.wled.ge/interfaces/json-api/), and the
 * effect-specific names for the speed ([speedName]) and intensity
 * ([intensityName]) parameters (`sx` and `ix` in the WLED segment object).
 *
 * The [speedName] and [intensityName] strings match the labels shown in the
 * WLED web UI for each effect, so the DM-panel sliders reflect the actual
 * parameter being adjusted.
 *
 * Only effects that work on a 1D LED ring (as opposed to 2D LED matrices) are
 * included here.
 *
 * @see <a href="https://kno.wled.ge/features/effects/">WLED effect list</a>
 */
enum class LightEffect(
    val displayName: String,
    val wledEffectId: Int,
    val speedName: String = "Speed",
    val intensityName: String = "Intensity",
) {
    NONE("None", 0),
    BLINK("Blink", 1, speedName = "Speed", intensityName = "Duty Cycle"),
    FADE("Breathe", 2),
    COLOR_WIPE("Color Wipe", 3),
    COLORLOOP("Color Loop", 8, intensityName = "Saturation"),
    RAINBOW("Rainbow", 9),
    SCAN("Scan", 10, intensityName = "# LEDs"),
    DUAL_SCAN("Dual Scan", 11, intensityName = "# LEDs"),
    THEATER("Theater", 13, intensityName = "Gap Size"),
    THEATER_RAINBOW("Theater Rainbow", 14, intensityName = "Gap Size"),
    RUNNING("Running", 15, intensityName = "Wave Width"),
    TWINKLE("Twinkle", 17, intensityName = "# Twinkles"),
    SPARKLE("Sparkle", 20),
    STROBE("Strobe", 23),
    STROBE_RAINBOW("Strobe Rainbow", 24),
    CHASE_COLOR("Chase Color", 28),
    RAINBOW_RUNNER("Rainbow Runner", 33, intensityName = "Wave Width"),
    SCANNER("Scanner", 40, intensityName = "Trail Decay"),
    FIREWORKS("Fireworks", 42, intensityName = "Frequency"),
    FIRE("Fire", 45, speedName = "Cooling", intensityName = "Sparking"),
    LIGHTNING("Lightning", 57, speedName = "Frequency", intensityName = "# Strikes"),
    METEOR("Meteor", 76, intensityName = "Trail Decay"),
    SMOOTH_METEOR("Smooth Meteor", 77, intensityName = "Trail Smoothing"),
    GLITTER("Glitter", 87, intensityName = "# Glitter"),
    CANDLE("Candle", 88, intensityName = "Flicker"),
    BOUNCING_BALLS("Bouncing Balls", 91, speedName = "Gravity", intensityName = "# Balls"),
    SINELON("Sinelon", 92),
    DRIP("Drip", 96, speedName = "Gravity", intensityName = "# Drips"),
    HEARTBEAT("Heartbeat", 100),
    OCEAN("Ocean", 101),
    CANDLE_MULTI("Candle Multi", 102, intensityName = "Flicker"),
}
