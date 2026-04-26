package com.tabletopcontrol.light

import java.io.StringWriter
import java.util.Properties

internal data class LightSceneState(
    val power: Boolean,
    val color: String,
    val effect: LightEffect,
    val colorCycling: Boolean,
    val brightness: Double,
    val effectSpeed: Int,
    val effectIntensity: Int,
)

internal object LightSceneCodec {
    private const val VERSION = 1

    fun serialize(state: LightSceneState): String {
        val props = Properties().apply {
            setProperty("version", VERSION.toString())
            setProperty("power", state.power.toString())
            setProperty("color", state.color)
            setProperty("effect", state.effect.name)
            setProperty("colorCycling", state.colorCycling.toString())
            setProperty("brightness", state.brightness.toString())
            setProperty("effectSpeed", state.effectSpeed.toString())
            setProperty("effectIntensity", state.effectIntensity.toString())
        }
        return StringWriter().use { writer ->
            props.store(writer, "TabletopControl light scene")
            writer.toString()
        }
    }

    fun deserialize(text: String): LightSceneState? {
        val props = runCatching {
            Properties().also { loaded ->
                text.reader().use { loaded.load(it) }
            }
        }.getOrNull() ?: return null

        val version = props.getProperty("version")?.toIntOrNull() ?: return null
        if (version != VERSION) return null

        val effect = runCatching { LightEffect.valueOf(props.getProperty("effect")) }.getOrNull()
            ?: LightEffect.NONE

        return LightSceneState(
            power = props.getProperty("power")?.toBooleanStrictOrNull() ?: true,
            color = props.getProperty("color") ?: "#FFFFFF",
            effect = effect,
            colorCycling = props.getProperty("colorCycling")?.toBooleanStrictOrNull() ?: false,
            brightness = props.getProperty("brightness")?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0,
            effectSpeed = props.getProperty("effectSpeed")?.toIntOrNull()?.coerceIn(0, 255)
                ?: LightController.DEFAULT_EFFECT_SPEED,
            effectIntensity = props.getProperty("effectIntensity")?.toIntOrNull()?.coerceIn(0, 255)
                ?: LightController.DEFAULT_EFFECT_INTENSITY,
        )
    }
}
