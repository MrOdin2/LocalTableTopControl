package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.light.LightEffect

/**
 * Persistent configuration for a single WLED segment.
 */
internal data class SegmentSavedState(
    val color: String = "#FFFFFF",
    val effect: LightEffect = LightEffect.NONE,
    val brightness: Double = 1.0,
    val effectSpeed: Int = 128,
    val effectIntensity: Int = 128,
)

/**
 * Full persisted configuration for the Advanced Lighting plugin.
 *
 * @param segmentNames  Maps segment id to a user-chosen display name.
 * @param segmentOrder  Ordered list of segment ids (controls table row order).
 * @param segmentStates Maps segment id to its last-known visual state.
 */
internal data class AdvancedLightConfig(
    val segmentNames: Map<Int, String> = emptyMap(),
    val segmentOrder: List<Int> = emptyList(),
    val segmentStates: Map<Int, SegmentSavedState> = emptyMap(),
)
