package com.tabletopcontrol.dynamicmap

import java.io.File

/**
 * Published when the light browser chooses the preset to use for newly placed lights.
 */
data class DynamicMapLightPresetSelectedEvent(val presetId: String)

/**
 * Published when a builder side-pane changes the active placement/editing tool.
 */
data class DynamicMapToolSelectedEvent(val tool: DynamicMapTool?)

/**
 * Published when the wall tools pane changes the kind used for newly placed walls.
 */
data class DynamicMapWallKindSelectedEvent(val kind: DynamicMapWallKind)

/**
 * Published when a side-pane requests bulk removal of all wall segments.
 */
data object DynamicMapClearWallsRequestedEvent

/**
 * Published when the wall tools pane requests wall topology cleanup.
 */
data object DynamicMapOptimizeWallsRequestedEvent

/**
 * Published when a side-pane requests bulk removal of all placed lights.
 */
data object DynamicMapClearLightsRequestedEvent

/**
 * Published when a side-pane requests bulk removal of all sunlight/outside areas.
 */
data object DynamicMapClearSunlightAreasRequestedEvent

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
data class DynamicMapSelectionChangedEvent(val selections: Set<DynamicMapElementSelection>) {
    constructor(selection: DynamicMapElementSelection?) : this(selection?.let(::setOf).orEmpty())

    val selection: DynamicMapElementSelection?
        get() = selections.firstOrNull()
}

/**
 * Published when a builder pane requests removal of a specific element.
 */
data class DynamicMapElementRemovalRequestedEvent(val selection: DynamicMapElementSelection)

/**
 * Published when the outline pane renames a single placed wall or light.
 */
data class DynamicMapElementRenameRequestedEvent(
    val selection: DynamicMapElementSelection,
    val label: String,
)

/**
 * Published when the outline pane creates a named group from the current element selection.
 */
data class DynamicMapGroupCreationRequestedEvent(val selections: Set<DynamicMapElementSelection>)

/**
 * Published when the outline pane renames a user-defined group.
 */
data class DynamicMapGroupRenameRequestedEvent(
    val groupId: String,
    val label: String,
)

/**
 * Published when the outline pane removes grouping metadata without deleting the grouped elements.
 */
data class DynamicMapGroupRemovalRequestedEvent(val groupIds: Set<String>)

/**
 * Published when a builder pane needs the current construction-site list and active site.
 */
data object DynamicMapConstructionSiteSnapshotRequestedEvent

/**
 * Published whenever the available construction sites or active site changes.
 */
data class DynamicMapConstructionSiteStateChangedEvent(
    val sites: List<DynamicMapConstructionSiteSummary>,
    val activeSite: DynamicMapConstructionSiteSummary?,
)

/**
 * Published when the construction-site toolbar creates a new empty builder scene.
 */
data class DynamicMapConstructionSiteCreateRequestedEvent(val name: String)

/**
 * Published when the construction-site toolbar saves the current document as a new site.
 */
data class DynamicMapConstructionSiteSaveAsRequestedEvent(val name: String)

/**
 * Published when the construction-site toolbar saves the current document to the active site.
 */
data object DynamicMapConstructionSiteSaveRequestedEvent

/**
 * Published when the construction-site toolbar loads a saved builder scene.
 */
data class DynamicMapConstructionSiteLoadRequestedEvent(val siteId: String)

/**
 * Published when the construction-site toolbar deletes a saved builder scene file.
 */
data class DynamicMapConstructionSiteDeleteRequestedEvent(val siteId: String)

/**
 * Published when the construction-site toolbar exports the current builder document for gameplay.
 */
data class DynamicMapGameplayExportRequestedEvent(val targetFile: File)

/**
 * Published after a gameplay export completes.
 */
data class DynamicMapGameplayExportCompletedEvent(val summary: DynamicMapGameplayExportSummary)

/**
 * Published when a gameplay export fails.
 */
data class DynamicMapGameplayExportFailedEvent(
    val targetFile: File,
    val message: String,
)

/**
 * Published when a builder pane turns a light on or off.
 */
data class DynamicMapLightEnabledRequestedEvent(
    val lightId: String,
    val enabled: Boolean,
)
