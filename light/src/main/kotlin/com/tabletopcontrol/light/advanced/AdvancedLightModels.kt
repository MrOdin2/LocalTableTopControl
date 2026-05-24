package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.light.LightController
import com.tabletopcontrol.light.LightEffect

internal data class WledDeviceSnapshot(
    val segments: List<WledSegmentSnapshot>,
)

internal data class WledSegmentSnapshot(
    val id: Int,
    val on: Boolean,
    val selectedForEdit: Boolean,
    val color: String,
    val brightness: Double,
    val effect: LightEffect,
    val effectSpeed: Int,
    val effectIntensity: Int,
)

internal data class AdvancedLightPreferences(
    val order: List<Int> = emptyList(),
    val segments: Map<Int, AdvancedLightSegmentPreference> = emptyMap(),
)

internal data class AdvancedLightSegmentPreference(
    val name: String? = null,
    val color: String? = null,
    val effect: LightEffect? = null,
    val brightness: Double? = null,
    val brightnessScale: Double? = null,
    val effectSpeed: Int? = null,
    val effectIntensity: Int? = null,
)

internal data class AdvancedLightSegmentState(
    val id: Int,
    val name: String = id.toString(),
    val on: Boolean = true,
    val selectedForEdit: Boolean = false,
    val color: String = "#FFFFFF",
    val brightness: Double = 1.0,
    val brightnessScale: Double = 1.0,
    val effect: LightEffect = LightEffect.NONE,
    val effectSpeed: Int = LightController.DEFAULT_EFFECT_SPEED,
    val effectIntensity: Int = LightController.DEFAULT_EFFECT_INTENSITY,
) {
    fun toCommand(): AdvancedLightSegmentCommand =
        AdvancedLightSegmentCommand(
            id = id,
            on = on,
            color = color,
            brightness = effectiveBrightness,
            effect = effect,
            effectSpeed = effectSpeed,
            effectIntensity = effectIntensity,
        )

    val effectiveBrightness: Double
        get() = (brightness * brightnessScale).coerceIn(0.0, 1.0)
}

internal data class AdvancedLightSegmentCommand(
    val id: Int,
    val on: Boolean,
    val color: String,
    val brightness: Double,
    val effect: LightEffect,
    val effectSpeed: Int,
    val effectIntensity: Int,
)

internal fun effectFromWledId(id: Int): LightEffect =
    LightEffect.entries.firstOrNull { it.wledEffectId == id } ?: LightEffect.NONE

internal fun mergeDeviceSegment(
    device: WledSegmentSnapshot,
    preference: AdvancedLightSegmentPreference?,
): AdvancedLightSegmentState =
    AdvancedLightSegmentState(
        id = device.id,
        name = preference?.name?.takeIf { it.isNotBlank() } ?: device.id.toString(),
        on = device.on,
        selectedForEdit = device.selectedForEdit,
        color = preference?.color?.takeIf(AdvancedLightJson::isValidHexColor) ?: device.color,
        brightness = preference?.brightness?.coerceIn(0.0, 1.0) ?: device.brightness,
        brightnessScale = preference?.brightnessScale?.coerceIn(0.0, 1.0) ?: 1.0,
        effect = preference?.effect ?: device.effect,
        effectSpeed = preference?.effectSpeed?.coerceIn(0, 255) ?: device.effectSpeed,
        effectIntensity = preference?.effectIntensity?.coerceIn(0, 255) ?: device.effectIntensity,
    )
