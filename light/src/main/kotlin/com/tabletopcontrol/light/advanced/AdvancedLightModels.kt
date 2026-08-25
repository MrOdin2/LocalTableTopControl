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
    val trackerTurnCuesEnabled: Boolean = false,
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
    val assignedTokenIds: Set<String> = emptySet(),
    val turnCue: AdvancedLightTurnCue? = null,
)

internal enum class AdvancedLightTurnCueDuration(
    private val label: String,
) {
    WHOLE_TURN("Whole turn"),
    TIMED("Play once"),
    ;

    override fun toString(): String = label
}

internal data class AdvancedLightTurnCue(
    val effect: LightEffect = LightEffect.HEARTBEAT,
    val color: String = "#FFD37A",
    val brightness: Double = 1.0,
    val effectSpeed: Int = LightController.DEFAULT_EFFECT_SPEED,
    val effectIntensity: Int = LightController.DEFAULT_EFFECT_INTENSITY,
    val duration: AdvancedLightTurnCueDuration = AdvancedLightTurnCueDuration.WHOLE_TURN,
    val durationMillis: Long = DEFAULT_DURATION_MILLIS,
) {
    companion object {
        const val DEFAULT_DURATION_MILLIS: Long = 1_500L
        const val MIN_DURATION_MILLIS: Long = 100L
        const val MAX_DURATION_MILLIS: Long = 60_000L
    }
}

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
    val assignedTokenIds: Set<String> = emptySet(),
    val turnCue: AdvancedLightTurnCue = AdvancedLightTurnCue(),
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

    fun toTurnCueCommand(): AdvancedLightSegmentCommand =
        AdvancedLightSegmentCommand(
            id = id,
            on = true,
            color = turnCue.color,
            brightness = (turnCue.brightness * brightnessScale).coerceIn(0.0, 1.0),
            effect = turnCue.effect,
            effectSpeed = turnCue.effectSpeed,
            effectIntensity = turnCue.effectIntensity,
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
        assignedTokenIds = preference?.assignedTokenIds
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.toSet()
            .orEmpty(),
        turnCue = preference?.turnCue?.let { cue ->
            cue.copy(
                color = cue.color.takeIf(AdvancedLightJson::isValidHexColor)?.uppercase() ?: "#FFD37A",
                brightness = cue.brightness.coerceIn(0.0, 1.0),
                effectSpeed = cue.effectSpeed.coerceIn(0, 255),
                effectIntensity = cue.effectIntensity.coerceIn(0, 255),
                durationMillis = cue.durationMillis.coerceIn(
                    AdvancedLightTurnCue.MIN_DURATION_MILLIS,
                    AdvancedLightTurnCue.MAX_DURATION_MILLIS,
                ),
            )
        } ?: AdvancedLightTurnCue(),
    )
