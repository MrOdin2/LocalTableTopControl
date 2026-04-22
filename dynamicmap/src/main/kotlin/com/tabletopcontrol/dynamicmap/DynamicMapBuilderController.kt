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

    fun clearLights() {
        updateDocument { it.copy(lights = emptyList()) }
    }

    fun onShutdown() {
        presetSubscription.unsubscribe()
        clearWallsSubscription.unsubscribe()
        clearLightsSubscription.unsubscribe()
    }

    private fun setSelectedPreset(preset: DynamicMapLightPreset) {
        if (selectedPreset.id == preset.id) return
        selectedPreset = preset
        presetListeners.toList().forEach { it(selectedPreset) }
    }

    private fun updateDocument(transform: (DynamicMapDocument) -> DynamicMapDocument) {
        val updated = transform(document)
        if (updated == document) return
        document = updated
        DynamicMapDraftSerializer.save(document)
        documentListeners.toList().forEach { it(document) }
    }
}
