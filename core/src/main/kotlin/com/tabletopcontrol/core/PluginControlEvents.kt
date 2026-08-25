package com.tabletopcontrol.core

/** Selects the light surface that should receive a [LightControlEvent]. */
sealed interface LightControlTarget {
    /** The global light controlled by the regular Lights plugin. */
    data object Global : LightControlTarget

    /**
     * WLED segments controlled by Advanced Light. An empty set means every loaded segment.
     */
    data class Segments(val ids: Set<Int> = emptySet()) : LightControlTarget
}

/**
 * Plugin-neutral light command published through [EventBus]. Null fields are left unchanged.
 * Effect IDs use WLED's stable numeric effect identifiers so core does not depend on Lights.
 */
data class LightControlEvent(
    val target: LightControlTarget,
    val power: Boolean? = null,
    val colorHex: String? = null,
    val effectId: Int? = null,
    val brightness: Double? = null,
    val effectSpeed: Int? = null,
    val effectIntensity: Int? = null,
)

/** Operations supported by [MusicControlEvent]. */
enum class MusicControlOperation {
    START,
    STOP,
    SWITCH,
}

/**
 * Plugin-neutral music command published through [EventBus].
 *
 * START and SWITCH require [trackUri]. STOP without a URI stops every track; with a URI it
 * stops the matching configured track.
 */
data class MusicControlEvent(
    val operation: MusicControlOperation,
    val trackUri: String? = null,
)
