package com.tabletopcontrol.dynamicmap

/**
 * Published when the light browser chooses the preset to use for newly placed lights.
 */
data class DynamicMapLightPresetSelectedEvent(val presetId: String)

/**
 * Published when a builder side-pane changes the active placement/editing tool.
 */
data class DynamicMapToolSelectedEvent(val tool: DynamicMapTool?)

/**
 * Published when a side-pane requests bulk removal of all wall segments.
 */
data object DynamicMapClearWallsRequestedEvent

/**
 * Published when a side-pane requests bulk removal of all placed lights.
 */
data object DynamicMapClearLightsRequestedEvent
