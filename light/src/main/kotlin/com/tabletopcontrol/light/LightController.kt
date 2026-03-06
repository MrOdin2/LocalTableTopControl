package com.tabletopcontrol.light

/**
 * Pure-Kotlin controller that holds and validates the ambient-light state.
 *
 * This class contains no JavaFX dependencies so it can be tested without a
 * running FX toolkit.  The [LightPlugin] binds UI controls to it.
 *
 * State fields:
 * - **color**        — selected color as a CSS hex string (`#RRGGBB` or `#RGB`).
 * - **effect**       — selected [LightEffect].
 * - **colorCycling** — whether automatic color cycling is active.
 * - **brightness**   — output brightness in the range `0.0`–`1.0`.
 */
class LightController {

    /** Current color as a CSS hex string, e.g. `"#FFFFFF"` or `"#FFF"`. */
    var color: String = "#FFFFFF"
        private set

    /** Currently selected light effect. */
    var effect: LightEffect = LightEffect.NONE
        private set

    /** Whether automatic color cycling is enabled. */
    var colorCycling: Boolean = false
        private set

    /** Brightness level in the range `0.0` (off) to `1.0` (full). */
    var brightness: Double = 1.0
        private set

    /**
     * Sets the light color.
     *
     * @param hex a CSS hex color string in the form `#RRGGBB` or `#RGB`
     * @throws IllegalArgumentException if [hex] is not a valid CSS hex color
     */
    fun setColor(hex: String) {
        require(HEX_COLOR_REGEX.matches(hex)) {
            "Color must be a CSS hex string (#RRGGBB or #RGB), was: $hex"
        }
        color = hex.uppercase()
    }

    /**
     * Sets the active light effect.
     *
     * @param lightEffect the desired [LightEffect]
     */
    fun setEffect(lightEffect: LightEffect) {
        effect = lightEffect
    }

    /**
     * Enables or disables automatic color cycling.
     *
     * @param enabled `true` to enable cycling; `false` to disable
     */
    fun setColorCycling(enabled: Boolean) {
        colorCycling = enabled
    }

    /**
     * Sets the output brightness.
     *
     * @param value a value in the range `0.0` (off) to `1.0` (full brightness)
     * @throws IllegalArgumentException if [value] is outside `0.0..1.0`
     */
    fun setBrightness(value: Double) {
        require(value in 0.0..1.0) { "Brightness must be between 0.0 and 1.0, was $value" }
        brightness = value
    }

    private companion object {
        /** Matches `#RGB` and `#RRGGBB` (case-insensitive). */
        val HEX_COLOR_REGEX = Regex("^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$")
    }
}
