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

/**
 * Published when a builder pane needs the current shared document snapshot.
 */
data object DynamicMapDocumentSnapshotRequestedEvent

/**
 * Published whenever the builder draft changes.
 */
data class DynamicMapDocumentChangedEvent(val document: DynamicMapDocument)

/**
 * Published whenever the shared builder selection changes.
 */
data class DynamicMapSelectionChangedEvent(val selection: DynamicMapElementSelection?)

/**
 * Published when a builder pane requests removal of a specific element.
 */
data class DynamicMapElementRemovalRequestedEvent(val selection: DynamicMapElementSelection)

/**
 * Published when a builder pane turns a light on or off.
 */
data class DynamicMapLightEnabledRequestedEvent(
    val lightId: String,
    val enabled: Boolean,
)
