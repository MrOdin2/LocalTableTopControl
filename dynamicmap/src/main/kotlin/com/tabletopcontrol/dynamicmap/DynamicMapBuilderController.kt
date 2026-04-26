package com.tabletopcontrol.dynamicmap

import com.tabletopcontrol.core.EventBus

class DynamicMapBuilderController {
    private var document: DynamicMapDocument = DynamicMapDraftSerializer.load()
    private var selectedPreset: DynamicMapLightPreset = DynamicMapLightPresets.defaultPreset

    private val documentListeners = mutableListOf<(DynamicMapDocument) -> Unit>()
    private val presetListeners = mutableListOf<(DynamicMapLightPreset) -> Unit>()

    private val presetSubscription = EventBus.subscribe<DynamicMapLightPresetSelectedEvent> { event ->
        setSelectedPreset(DynamicMapLightPresets.byId(event.presetId))
    }
    private val clearWallsSubscription = EventBus.subscribe<DynamicMapClearWallsRequestedEvent> {
        clearWalls()
    }
    private val clearLightsSubscription = EventBus.subscribe<DynamicMapClearLightsRequestedEvent> {
        clearLights()
    }
    private val snapshotSubscription = EventBus.subscribe<DynamicMapDocumentSnapshotRequestedEvent> {
        publishDocumentChanged()
    }
    private val removalSubscription = EventBus.subscribe<DynamicMapElementRemovalRequestedEvent> { event ->
        when (event.selection.kind) {
            DynamicMapElementKind.WALL -> removeWall(event.selection.elementId)
            DynamicMapElementKind.LIGHT -> removeLight(event.selection.elementId)
        }
    }
    private val lightEnabledSubscription = EventBus.subscribe<DynamicMapLightEnabledRequestedEvent> { event ->
        setLightEnabled(event.lightId, event.enabled)
    }
    private val elementRenameSubscription = EventBus.subscribe<DynamicMapElementRenameRequestedEvent> { event ->
        renameElement(event.selection, event.label)
    }
    private val groupCreationSubscription = EventBus.subscribe<DynamicMapGroupCreationRequestedEvent> { event ->
        createGroup(event.selections)
    }
    private val groupRenameSubscription = EventBus.subscribe<DynamicMapGroupRenameRequestedEvent> { event ->
        renameGroup(event.groupId, event.label)
    }
    private val groupRemovalSubscription = EventBus.subscribe<DynamicMapGroupRemovalRequestedEvent> { event ->
        removeGroups(event.groupIds)
    }

    init {
        publishDocumentChanged()
    }

    fun currentDocument(): DynamicMapDocument = document

    fun currentPreset(): DynamicMapLightPreset = selectedPreset

    fun observeDocument(listener: (DynamicMapDocument) -> Unit): () -> Unit {
        documentListeners += listener
        listener(document)
        return { documentListeners.remove(listener) }
    }

    fun observePreset(listener: (DynamicMapLightPreset) -> Unit): () -> Unit {
        presetListeners += listener
        listener(selectedPreset)
        return { presetListeners.remove(listener) }
    }

    fun setMapSize(cols: Int, rows: Int) {
        updateDocument {
            it.copy(
                cols = cols.coerceAtLeast(5),
                rows = rows.coerceAtLeast(5),
            )
        }
    }

    fun setBackgroundImage(uri: String?, displayPath: String?) {
        setBackgroundImage(uri = uri, displayPath = displayPath, calibration = document.backgroundCalibration)
    }

    fun setBackgroundImage(
        uri: String?,
        displayPath: String?,
        calibration: DynamicMapBackgroundCalibration,
    ) {
        updateDocument {
            it.copy(
                backgroundImageUri = uri,
                backgroundDisplayPath = displayPath,
                backgroundCalibration = calibration,
            )
        }
    }

    fun clearBackgroundImage() {
        updateDocument {
            it.copy(
                backgroundImageUri = null,
                backgroundDisplayPath = null,
                backgroundCalibration = DynamicMapBackgroundCalibration(),
            )
        }
    }

    fun setBackgroundCalibration(calibration: DynamicMapBackgroundCalibration) {
        updateDocument { it.copy(backgroundCalibration = calibration) }
    }

    fun setLayerVisible(layer: DynamicMapLayer, visible: Boolean) {
        updateDocument {
            val visibility = when (layer) {
                DynamicMapLayer.BACKGROUND -> it.visibility.copy(background = visible)
                DynamicMapLayer.WALLS -> it.visibility.copy(walls = visible)
                DynamicMapLayer.LIGHTS -> it.visibility.copy(lights = visible)
                DynamicMapLayer.GRID -> it.visibility.copy(grid = visible)
            }
            it.copy(visibility = visibility)
        }
    }

    fun addWall(wall: DynamicMapWall) {
        updateDocument { it.copy(walls = it.walls + wall) }
    }

    fun addWalls(walls: List<DynamicMapWall>) {
        if (walls.isEmpty()) return
        updateDocument { it.copy(walls = it.walls + walls) }
    }

    fun removeWall(id: String) {
        updateDocument { it.copy(walls = it.walls.filterNot { wall -> wall.id == id }) }
    }

    fun clearWalls() {
        updateDocument { it.copy(walls = emptyList()) }
    }

    fun addLight(position: DynamicMapPoint) {
        val preset = selectedPreset
        val light = DynamicMapLight(
            label = preset.displayName,
            position = position,
            brightRadius = preset.brightRadius,
            dimRadius = preset.dimRadius,
            colorHex = preset.colorHex,
        )
        updateDocument { it.copy(lights = it.lights + light) }
    }

    fun removeLight(id: String) {
        updateDocument { it.copy(lights = it.lights.filterNot { light -> light.id == id }) }
    }

    fun moveSelections(
        selections: Set<DynamicMapElementSelection>,
        delta: DynamicMapPoint,
    ) {
        updateDocument { it.moveSelections(selections, delta) }
    }

    fun setLightEnabled(id: String, enabled: Boolean) {
        updateDocument { current ->
            current.copy(
                lights = current.lights.map { light ->
                    if (light.id == id) {
                        light.copy(enabled = enabled)
                    } else {
                        light
                    }
                },
            )
        }
    }

    fun clearLights() {
        updateDocument { it.copy(lights = emptyList()) }
    }

    fun renameElement(selection: DynamicMapElementSelection, label: String) {
        val cleanedLabel = cleanBuilderLabel(label) ?: return
        updateDocument { current ->
            when (selection.kind) {
                DynamicMapElementKind.WALL -> current.copy(
                    walls = current.walls.map { wall ->
                        if (wall.id == selection.elementId) wall.copy(label = cleanedLabel) else wall
                    },
                )
                DynamicMapElementKind.LIGHT -> current.copy(
                    lights = current.lights.map { light ->
                        if (light.id == selection.elementId) light.copy(label = cleanedLabel) else light
                    },
                )
            }
        }
    }

    fun createGroup(selections: Set<DynamicMapElementSelection>) {
        updateDocument { current ->
            val elements = current.filterExistingSelections(selections)
            if (elements.isEmpty()) {
                current
            } else {
                current.copy(
                    groups = current.groups + DynamicMapElementGroup(
                        label = nextGroupLabel(current.groups),
                        elements = elements,
                    ),
                )
            }
        }
    }

    fun renameGroup(groupId: String, label: String) {
        val cleanedLabel = cleanBuilderLabel(label) ?: return
        updateDocument { current ->
            current.copy(
                groups = current.groups.map { group ->
                    if (group.id == groupId) group.copy(label = cleanedLabel) else group
                },
            )
        }
    }

    fun removeGroups(groupIds: Set<String>) {
        if (groupIds.isEmpty()) return
        updateDocument { current ->
            current.copy(groups = current.groups.filterNot { it.id in groupIds })
        }
    }

    fun onShutdown() {
        presetSubscription.unsubscribe()
        clearWallsSubscription.unsubscribe()
        clearLightsSubscription.unsubscribe()
        snapshotSubscription.unsubscribe()
        removalSubscription.unsubscribe()
        lightEnabledSubscription.unsubscribe()
        elementRenameSubscription.unsubscribe()
        groupCreationSubscription.unsubscribe()
        groupRenameSubscription.unsubscribe()
        groupRemovalSubscription.unsubscribe()
    }

    private fun setSelectedPreset(preset: DynamicMapLightPreset) {
        if (selectedPreset.id == preset.id) return
        selectedPreset = preset
        presetListeners.toList().forEach { it(selectedPreset) }
    }

    private fun updateDocument(transform: (DynamicMapDocument) -> DynamicMapDocument) {
        val updated = transform(document).pruneInvalidGroups()
        if (updated == document) return
        document = updated
        DynamicMapDraftSerializer.save(document)
        documentListeners.toList().forEach { it(document) }
        publishDocumentChanged()
    }

    private fun publishDocumentChanged() {
        EventBus.publish(DynamicMapDocumentChangedEvent(document))
    }
}

private fun cleanBuilderLabel(label: String): String? =
    label.trim().takeIf { it.isNotEmpty() }?.take(80)

private fun nextGroupLabel(groups: List<DynamicMapElementGroup>): String {
    val usedLabels = groups.mapTo(mutableSetOf()) { it.label }
    var index = 1
    while (true) {
        val candidate = "Group $index"
        if (candidate !in usedLabels) return candidate
        index += 1
    }
}
