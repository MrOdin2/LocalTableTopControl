package com.tabletopcontrol.light

/**
 * Immutable snapshot of the validated light state.
 *
 * The snapshot converts itself into the exact serial command that should be
 * sent, keeping the preset / power priority logic out of the plugin layer.
 */
data class LightStateSnapshot(
    val power: Boolean,
    val color: String,
    val effect: LightEffect,
    val brightness: Double,
    val colorCycling: Boolean,
    val effectSpeed: Int,
    val effectIntensity: Int,
    val preset: Int?,
) {
    internal fun toSerialCommand(): LightSerialCommand =
        when {
            !power -> LightSerialCommand.State(
                on = false,
                color = color,
                effect = effect,
                brightness = brightness,
                colorCycling = colorCycling,
                speed = effectSpeed,
                intensity = effectIntensity,
            )
            preset != null -> LightSerialCommand.Preset(preset)
            else -> LightSerialCommand.State(
                on = true,
                color = color,
                effect = effect,
                brightness = brightness,
                colorCycling = colorCycling,
                speed = effectSpeed,
                intensity = effectIntensity,
            )
        }
}

internal sealed interface LightSerialCommand {
    fun sendWith(sender: WledSerialSender): String

    data class State(
        val on: Boolean,
        val color: String,
        val effect: LightEffect,
        val brightness: Double,
        val colorCycling: Boolean,
        val speed: Int,
        val intensity: Int,
    ) : LightSerialCommand {
        override fun sendWith(sender: WledSerialSender): String =
            sender.sendStateJson(
                on = on,
                color = color,
                effect = effect,
                brightness = brightness,
                colorCycling = colorCycling,
                speed = speed,
                intensity = intensity,
            )
    }

    data class Preset(val id: Int) : LightSerialCommand {
        override fun sendWith(sender: WledSerialSender): String = sender.sendPresetJson(id)
    }
}
