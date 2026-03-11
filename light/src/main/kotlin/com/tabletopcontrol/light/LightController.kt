package com.tabletopcontrol.light

/**
 * Pure-Kotlin controller that holds and validates the ambient-light state.
 *
 * This class contains no JavaFX dependencies so it can be tested without a
 * running FX toolkit.  The [LightPlugin] binds UI controls to it.
 *
 * State fields:
 * - **power**        — whether the lights are on.
 * - **color**        — selected color as a CSS hex string (`#RRGGBB` or `#RGB`).
 * - **effect**       — selected [LightEffect].
 * - **colorCycling** — whether automatic color cycling is active.
 * - **brightness**   — output brightness in the range `0.0`–`1.0`.
 * - **preset**       — active WLED preset ID (`1–250`), or `null` for manual control.
 *
 * When [preset] is non-null the WLED device runs its stored preset animation
 * autonomously; the host does not need to continuously send state updates.
 * Setting [preset] to `null` reverts to manual control.
 *
 * Observers can register a callback with [addChangeListener] to be notified
 * whenever any state field changes.
 */
class LightController {

    /** Whether the lights are powered on. */
    var power: Boolean = true
        private set

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
     * Active WLED preset ID in the range `1–250`, or `null` when the device
     * is in manual control mode (color / effect / brightness are used instead).
     */
    var preset: Int? = null
        private set

    private val changeListeners = mutableListOf<() -> Unit>()

    /**
     * Registers [listener] to be called whenever any state field changes.
     *
     * Returns the same [listener] so callers can hold a reference for later
     * removal via [removeChangeListener].
     */
    fun addChangeListener(listener: () -> Unit): () -> Unit {
        changeListeners += listener
        return listener
    }

    /** Removes a previously registered [listener]. */
    fun removeChangeListener(listener: () -> Unit) {
        changeListeners -= listener
    }

    /**
     * Turns the lights on or off.
     *
     * @param on `true` to switch the lights on; `false` to switch them off
     */
    fun setPower(on: Boolean) {
        power = on
        notifyChange()
    }

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
        notifyChange()
    }

    /**
     * Sets the active light effect.
     *
     * @param lightEffect the desired [LightEffect]
     */
    fun setEffect(lightEffect: LightEffect) {
        effect = lightEffect
        notifyChange()
    }

    /**
     * Enables or disables automatic color cycling.
     *
     * @param enabled `true` to enable cycling; `false` to disable
     */
    fun setColorCycling(enabled: Boolean) {
        colorCycling = enabled
        notifyChange()
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
        notifyChange()
    }

    /**
     * Activates a WLED preset by ID, or clears preset mode to resume manual control.
     *
     * When a preset is active the WLED device runs its stored animation
     * autonomously; the host sends only `{"ps":N}` rather than continuously
     * updating color / effect / brightness.  Setting [id] to `null` clears the
     * active preset and returns to manual control.
     *
     * @param id a WLED preset ID in the range `1..250`, or `null` to clear
     * @throws IllegalArgumentException if [id] is not `null` and outside `1..250`
     */
    fun setPreset(id: Int?) {
        if (id != null) {
            require(id in 1..250) { "Preset ID must be between 1 and 250, was $id" }
        }
        preset = id
        notifyChange()
    }

    private fun notifyChange() {
        changeListeners.forEach { it() }
    }

    private companion object {
        /** Matches `#RGB` and `#RRGGBB` (case-insensitive). */
        val HEX_COLOR_REGEX = Regex("^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$")
    }
}
