package com.tabletopcontrol.dynamicmap

import com.tabletopcontrol.core.EventBus

class DynamicMapBuilderController {
    private val constructionSiteStore = DynamicMapConstructionSiteStore()
    private var activeConstructionSite: DynamicMapConstructionSiteSummary? = null
    private var document: DynamicMapDocument = loadInitialDocument()
    private var selectedPreset: DynamicMapLightPreset = DynamicMapLightPresets.defaultPreset

    private val documentListeners = mutableListOf<(DynamicMapDocument) -> Unit>()
    private val presetListeners = mutableListOf<(DynamicMapLightPreset) -> Unit>()

    private val presetSubscription = EventBus.subscribe<DynamicMapLightPresetSelectedEvent> { event ->
        setSelectedPreset(DynamicMapLightPresets.byId(event.presetId))
    }
    private val clearWallsSubscription = EventBus.subscribe<DynamicMapClearWallsRequestedEvent> {
        clearWalls()
    }
    private val optimizeWallsSubscription = EventBus.subscribe<DynamicMapOptimizeWallsRequestedEvent> {
        optimizeWalls()
    }
    private val clearLightsSubscription = EventBus.subscribe<DynamicMapClearLightsRequestedEvent> {
        clearLights()
    }
    private val clearSunlightAreasSubscription = EventBus.subscribe<DynamicMapClearSunlightAreasRequestedEvent> {
        clearSunlightAreas()
    }
    private val snapshotSubscription = EventBus.subscribe<DynamicMapDocumentSnapshotRequestedEvent> {
        publishDocumentChanged()
    }
    private val removalSubscription = EventBus.subscribe<DynamicMapElementRemovalRequestedEvent> { event ->
        when (event.selection.kind) {
            DynamicMapElementKind.WALL -> removeWall(event.selection.elementId)
            DynamicMapElementKind.LIGHT -> removeLight(event.selection.elementId)
            DynamicMapElementKind.SUNLIGHT_AREA -> removeSunlightArea(event.selection.elementId)
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
    private val constructionSiteSnapshotSubscription =
        EventBus.subscribe<DynamicMapConstructionSiteSnapshotRequestedEvent> {
            publishConstructionSiteStateChanged()
        }
    private val constructionSiteCreateSubscription =
        EventBus.subscribe<DynamicMapConstructionSiteCreateRequestedEvent> { event ->
            createConstructionSite(event.name)
        }
    private val constructionSiteSaveAsSubscription =
        EventBus.subscribe<DynamicMapConstructionSiteSaveAsRequestedEvent> { event ->
            saveCurrentConstructionSiteAs(event.name)
        }
    private val constructionSiteSaveSubscription =
        EventBus.subscribe<DynamicMapConstructionSiteSaveRequestedEvent> {
            saveCurrentConstructionSite()
        }
    private val constructionSiteLoadSubscription =
        EventBus.subscribe<DynamicMapConstructionSiteLoadRequestedEvent> { event ->
            loadConstructionSite(event.siteId)
        }
    private val constructionSiteDeleteSubscription =
        EventBus.subscribe<DynamicMapConstructionSiteDeleteRequestedEvent> { event ->
            deleteConstructionSite(event.siteId)
        }
    private val gameplayExportSubscription =
        EventBus.subscribe<DynamicMapGameplayExportRequestedEvent> { event ->
            exportGameplayBundle(event.targetFile)
        }

    init {
        publishDocumentChanged()
        publishConstructionSiteStateChanged()
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

    fun optimizeWalls() {
        val before = document
        updateDocument { it.optimizeWallTopology() }
        if (document != before) {
            EventBus.publish(DynamicMapSelectionChangedEvent(emptySet()))
        }
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

    fun addSunlightArea(points: List<DynamicMapPoint>) {
        val polygon = sanitizedSunlightPolygon(points)
        if (!isValidSunlightPolygon(polygon)) return
        val area = DynamicMapSunlightArea(
            label = nextSunlightAreaLabel(document.sunlightAreas),
            points = polygon,
        )
        updateDocument { it.copy(sunlightAreas = it.sunlightAreas + area) }
    }

    fun removeSunlightArea(id: String) {
        updateDocument { it.copy(sunlightAreas = it.sunlightAreas.filterNot { area -> area.id == id }) }
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

    fun clearSunlightAreas() {
        updateDocument { it.copy(sunlightAreas = emptyList()) }
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
                DynamicMapElementKind.SUNLIGHT_AREA -> current.copy(
                    sunlightAreas = current.sunlightAreas.map { area ->
                        if (area.id == selection.elementId) area.copy(label = cleanedLabel) else area
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

    fun createConstructionSite(name: String) {
        val emptyDocument = DynamicMapDocument()
        val site = constructionSiteStore.createSite(name, emptyDocument) ?: return
        replaceDocument(
            updated = emptyDocument,
            constructionSite = site,
            clearSelection = true,
        )
    }

    fun saveCurrentConstructionSite() {
        val activeSite = activeConstructionSite ?: return
        val savedSite = constructionSiteStore.saveSite(
            id = activeSite.id,
            name = activeSite.name,
            document = document,
        ) ?: return
        activeConstructionSite = savedSite
        constructionSiteStore.saveActiveSite(savedSite)
        publishConstructionSiteStateChanged()
    }

    fun saveCurrentConstructionSiteAs(name: String) {
        val site = constructionSiteStore.createSite(name, document) ?: return
        activeConstructionSite = site
        constructionSiteStore.saveActiveSite(site)
        publishConstructionSiteStateChanged()
    }

    fun loadConstructionSite(siteId: String) {
        val site = constructionSiteStore.summaryForId(siteId) ?: return
        val loadedDocument = constructionSiteStore.loadSite(site.id) ?: return
        replaceDocument(
            updated = loadedDocument,
            constructionSite = site,
            clearSelection = true,
        )
    }

    fun deleteConstructionSite(siteId: String) {
        constructionSiteStore.deleteSite(siteId)
        if (activeConstructionSite?.id == siteId) {
            activeConstructionSite = null
            constructionSiteStore.saveActiveSite(null)
            persistCurrentDocument()
        }
        publishConstructionSiteStateChanged()
    }

    fun onShutdown() {
        presetSubscription.unsubscribe()
        clearWallsSubscription.unsubscribe()
        optimizeWallsSubscription.unsubscribe()
        clearLightsSubscription.unsubscribe()
        clearSunlightAreasSubscription.unsubscribe()
        snapshotSubscription.unsubscribe()
        removalSubscription.unsubscribe()
        lightEnabledSubscription.unsubscribe()
        elementRenameSubscription.unsubscribe()
        groupCreationSubscription.unsubscribe()
        groupRenameSubscription.unsubscribe()
        groupRemovalSubscription.unsubscribe()
        constructionSiteSnapshotSubscription.unsubscribe()
        constructionSiteCreateSubscription.unsubscribe()
        constructionSiteSaveAsSubscription.unsubscribe()
        constructionSiteSaveSubscription.unsubscribe()
        constructionSiteLoadSubscription.unsubscribe()
        constructionSiteDeleteSubscription.unsubscribe()
        gameplayExportSubscription.unsubscribe()
    }

    private fun loadInitialDocument(): DynamicMapDocument {
        val activeSite = constructionSiteStore.loadActiveSite()
        val activeDocument = activeSite?.let { constructionSiteStore.loadSite(it.id) }
        if (activeSite != null && activeDocument != null) {
            activeConstructionSite = activeSite
            return activeDocument.pruneInvalidGroups()
        }
        constructionSiteStore.saveActiveSite(null)
        return DynamicMapDraftSerializer.load()
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
        persistCurrentDocument()
        documentListeners.toList().forEach { it(document) }
        publishDocumentChanged()
        publishConstructionSiteStateChanged()
    }

    private fun replaceDocument(
        updated: DynamicMapDocument,
        constructionSite: DynamicMapConstructionSiteSummary?,
        clearSelection: Boolean,
    ) {
        document = updated.pruneInvalidGroups()
        activeConstructionSite = constructionSite
        constructionSiteStore.saveActiveSite(constructionSite)
        documentListeners.toList().forEach { it(document) }
        publishDocumentChanged()
        publishConstructionSiteStateChanged()
        if (clearSelection) {
            EventBus.publish(DynamicMapSelectionChangedEvent(emptySet()))
        }
    }

    private fun persistCurrentDocument() {
        val activeSite = activeConstructionSite
        if (activeSite == null) {
            DynamicMapDraftSerializer.save(document)
            return
        }
        val savedSite = constructionSiteStore.saveSite(
            id = activeSite.id,
            name = activeSite.name,
            document = document,
        )
        if (savedSite != null) {
            activeConstructionSite = savedSite
            constructionSiteStore.saveActiveSite(savedSite)
        }
    }

    private fun exportGameplayBundle(targetFile: java.io.File) {
        DynamicMapGameplayExporter.export(document, targetFile)
            .fold(
                onSuccess = { summary ->
                    EventBus.publish(DynamicMapGameplayExportCompletedEvent(summary))
                },
                onFailure = { error ->
                    EventBus.publish(
                        DynamicMapGameplayExportFailedEvent(
                            targetFile = targetFile,
                            message = error.message ?: "The map could not be exported.",
                        ),
                    )
                },
            )
    }

    private fun publishDocumentChanged() {
        EventBus.publish(DynamicMapDocumentChangedEvent(document))
    }

    private fun publishConstructionSiteStateChanged() {
        EventBus.publish(
            DynamicMapConstructionSiteStateChangedEvent(
                sites = constructionSiteStore.listSites(),
                activeSite = activeConstructionSite,
            ),
        )
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

private fun nextSunlightAreaLabel(areas: List<DynamicMapSunlightArea>): String {
    val usedLabels = areas.mapTo(mutableSetOf()) { it.label }
    var index = 1
    while (true) {
        val candidate = "Sunlight Area $index"
        if (candidate !in usedLabels) return candidate
        index += 1
    }
}
