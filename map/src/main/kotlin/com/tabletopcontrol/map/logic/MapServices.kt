package com.tabletopcontrol.map.logic

import com.tabletopcontrol.core.ActiveTokenChangedEvent
import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.TokenAddedEvent
import com.tabletopcontrol.core.TokenImageChangedEvent
import com.tabletopcontrol.core.TokenMovedEvent
import com.tabletopcontrol.core.TokenRemovedEvent
import com.tabletopcontrol.core.TokensResetEvent
import com.tabletopcontrol.core.persistence.LocalFiles
import com.tabletopcontrol.map.FogOfWarCellEvent
import com.tabletopcontrol.map.FogOfWarResetEvent
import com.tabletopcontrol.map.FogOfWarSetupEvent
import com.tabletopcontrol.map.GridCalibrationEvent
import com.tabletopcontrol.map.GridCalibrationModeEvent
import com.tabletopcontrol.map.GridUpdateEvent
import com.tabletopcontrol.map.MapBackgroundEvent
import com.tabletopcontrol.map.MapCalibrationEvent
import com.tabletopcontrol.map.MapClearEvent
import com.tabletopcontrol.map.MapCalibrationModeEvent
import com.tabletopcontrol.map.MapInputField
import com.tabletopcontrol.map.MapFogSceneMode
import com.tabletopcontrol.map.MapFogSceneState
import com.tabletopcontrol.map.MapLoadEvent
import com.tabletopcontrol.map.MapOperationError
import com.tabletopcontrol.map.MapRenderer
import com.tabletopcontrol.map.MapResult
import com.tabletopcontrol.map.MapRotationEvent
import com.tabletopcontrol.map.MapSceneState
import com.tabletopcontrol.map.MapSavedSettings
import com.tabletopcontrol.map.MapSettingsSerializer
import com.tabletopcontrol.map.MeasurementAddedEvent
import com.tabletopcontrol.map.MeasurementOverlay
import com.tabletopcontrol.map.MeasurementRemovedEvent
import com.tabletopcontrol.map.MeasurementType
import com.tabletopcontrol.map.MeasurementUpdatedEvent
import com.tabletopcontrol.map.MeasurementsClearedEvent
import com.tabletopcontrol.map.ShowTokenNamesEvent
import com.tabletopcontrol.map.TableMapOffsetEvent
import javafx.scene.paint.Color
import java.util.UUID

class MapSettingsService(savedSettings: MapSavedSettings = MapSettingsSerializer.load()) {
    var mapCalibration: MapCalibration = savedSettings.mapCalibration ?: MapCalibration()
        private set

    var gridCalibration: GridCalibration = savedSettings.gridCalibration ?: GridCalibration()
        private set

    var gridColor: Color = savedSettings.gridColor ?: GridConfig().color
        private set

    var backgroundColor: Color = savedSettings.backgroundColor ?: Color.BLACK
        private set

    var mapRotation: Int = savedSettings.mapRotation ?: 0
        private set

    var tableMapOffset: TableMapOffset = savedSettings.tableMapOffset ?: TableMapOffset()
        private set

    var showTokenNames: Boolean = false
        private set

    var currentMapImageUri: String? = null
        private set

    var currentMapDisplayPath: String? = null
        private set

    var currentGridConfig: GridConfig? = null
        private set

    fun publishCurrentSettings() {
        currentMapImageUri?.let { EventBus.publish(MapLoadEvent(it)) }
        EventBus.publish(MapCalibrationEvent(mapCalibration))
        EventBus.publish(GridCalibrationEvent(gridCalibration))
        EventBus.publish(MapBackgroundEvent(backgroundColor))
        EventBus.publish(GridUpdateEvent(currentGridConfig))
        EventBus.publish(MapRotationEvent(mapRotation))
        EventBus.publish(TableMapOffsetEvent(tableMapOffset))
        EventBus.publish(ShowTokenNamesEvent(showTokenNames))
    }

    internal fun applySceneState(sceneState: MapSceneState) {
        currentMapImageUri = sceneState.mapImageUri
        currentMapDisplayPath = sceneState.mapDisplayPath ?: sceneState.mapImageUri
        mapCalibration = sceneState.mapCalibration
        gridCalibration = sceneState.gridCalibration
        gridColor = sceneState.gridColor
        backgroundColor = sceneState.backgroundColor
        mapRotation = normalizeRotation(sceneState.mapRotation)
        tableMapOffset = sceneState.tableMapOffset
        showTokenNames = sceneState.showTokenNames
        currentGridConfig = if (sceneState.gridVisible) GridConfig(color = gridColor) else null

        if (currentMapImageUri != null) {
            EventBus.publish(MapLoadEvent(currentMapImageUri.orEmpty()))
        } else {
            EventBus.publish(MapClearEvent)
        }
        EventBus.publish(MapCalibrationEvent(mapCalibration))
        EventBus.publish(GridCalibrationEvent(gridCalibration))
        EventBus.publish(MapBackgroundEvent(backgroundColor))
        EventBus.publish(GridUpdateEvent(currentGridConfig))
        EventBus.publish(MapRotationEvent(mapRotation))
        EventBus.publish(TableMapOffsetEvent(tableMapOffset))
        EventBus.publish(ShowTokenNamesEvent(showTokenNames))
        save()
    }

    fun applyMapLoad(uri: String, displayPath: String = uri) {
        currentMapImageUri = uri
        currentMapDisplayPath = displayPath
        EventBus.publish(MapLoadEvent(uri))
    }

    fun clearMapImage() {
        currentMapImageUri = null
        currentMapDisplayPath = null
        EventBus.publish(MapClearEvent)
    }

    fun previewMapCalibration(calibration: MapCalibration) {
        EventBus.publish(MapCalibrationEvent(calibration))
    }

    fun commitMapCalibration(calibration: MapCalibration) {
        mapCalibration = calibration
        EventBus.publish(MapCalibrationEvent(mapCalibration))
        save()
    }

    fun restoreMapCalibration(calibration: MapCalibration) {
        mapCalibration = calibration
        EventBus.publish(MapCalibrationEvent(mapCalibration))
    }

    fun previewGridCalibration(calibration: GridCalibration) {
        EventBus.publish(GridCalibrationEvent(calibration))
    }

    fun commitGridCalibration(calibration: GridCalibration) {
        gridCalibration = calibration
        EventBus.publish(GridCalibrationEvent(gridCalibration))
        save()
    }

    fun restoreGridCalibration(calibration: GridCalibration) {
        gridCalibration = calibration
        EventBus.publish(GridCalibrationEvent(gridCalibration))
    }

    fun setGridColor(color: Color) {
        gridColor = color
    }

    fun applyGridConfig(visible: Boolean): GridConfig? {
        currentGridConfig = if (visible) GridConfig(color = gridColor) else null
        EventBus.publish(GridUpdateEvent(currentGridConfig))
        save()
        return currentGridConfig
    }

    fun applyBackgroundColor(color: Color): Color {
        backgroundColor = color
        gridColor = contrastingGridColor(color)
        EventBus.publish(MapBackgroundEvent(backgroundColor))
        save()
        return gridColor
    }

    fun rotateMapBy(deltaDegrees: Int): Int {
        mapRotation = normalizeRotation(mapRotation + deltaDegrees)
        EventBus.publish(MapRotationEvent(mapRotation))
        save()
        return mapRotation
    }

    fun nudgeTableMap(dxTiles: Int = 0, dyTiles: Int = 0): TableMapOffset {
        val step = gridCalibration.effectiveCellSizeInPixels()
        tableMapOffset = tableMapOffset.copy(
            offsetX = tableMapOffset.offsetX + dxTiles * step,
            offsetY = tableMapOffset.offsetY + dyTiles * step,
        )
        EventBus.publish(TableMapOffsetEvent(tableMapOffset))
        save()
        return tableMapOffset
    }

    fun resetTableMapOffset(): TableMapOffset {
        tableMapOffset = TableMapOffset()
        EventBus.publish(TableMapOffsetEvent(tableMapOffset))
        save()
        return tableMapOffset
    }

    fun setShowTokenNames(show: Boolean) {
        showTokenNames = show
        EventBus.publish(ShowTokenNamesEvent(show))
    }

    private fun save() {
        MapSettingsSerializer.save(
            gridCalibration = gridCalibration,
            mapCalibration = mapCalibration,
            gridColor = gridColor,
            backgroundColor = backgroundColor,
            mapRotation = mapRotation,
            tableMapOffset = tableMapOffset,
        )
    }

    private fun normalizeRotation(degrees: Int): Int = ((degrees % 360) + 360) % 360
}

class MapCalibrationService {
    fun setMapCalibrationMode(active: Boolean) {
        EventBus.publish(MapCalibrationModeEvent(active))
    }

    fun setGridCalibrationMode(active: Boolean) {
        EventBus.publish(GridCalibrationModeEvent(active))
    }

    fun parseMapCalibration(
        scaleText: String,
        offsetXText: String,
        offsetYText: String,
    ): MapResult<MapCalibration> {
        val scale = scaleText.toDoubleOrNull()
            ?: return MapResult.failure(
                MapOperationError.Validation(
                    field = MapInputField.SCALE,
                    reason = "Scale must be a positive number.",
                ),
            )
        if (scale <= 0.0) {
            return MapResult.failure(
                MapOperationError.Validation(
                    field = MapInputField.SCALE,
                    reason = "Scale must be a positive number.",
                ),
            )
        }
        val offsetX = offsetXText.toDoubleOrNull()
            ?: return MapResult.failure(
                MapOperationError.Validation(
                    field = MapInputField.OFFSET_X,
                    reason = "Offset X must be a number.",
                ),
            )
        val offsetY = offsetYText.toDoubleOrNull()
            ?: return MapResult.failure(
                MapOperationError.Validation(
                    field = MapInputField.OFFSET_Y,
                    reason = "Offset Y must be a number.",
                ),
            )
        return MapResult.success(MapCalibration(scale = scale, offsetX = offsetX, offsetY = offsetY))
    }

    fun parseGridCalibration(
        cellSizeText: String,
        scaleText: String,
        offsetXText: String,
        offsetYText: String,
    ): MapResult<GridCalibration> {
        val cellSize = cellSizeText.toDoubleOrNull()
            ?: return MapResult.failure(
                MapOperationError.Validation(
                    field = MapInputField.CELL_SIZE,
                    reason = "Cell size must be a positive number.",
                ),
            )
        if (cellSize <= 0.0) {
            return MapResult.failure(
                MapOperationError.Validation(
                    field = MapInputField.CELL_SIZE,
                    reason = "Cell size must be a positive number.",
                ),
            )
        }
        val scale = scaleText.toDoubleOrNull()
            ?: return MapResult.failure(
                MapOperationError.Validation(
                    field = MapInputField.SCALE,
                    reason = "Scale must be a positive number.",
                ),
            )
        if (scale <= 0.0) {
            return MapResult.failure(
                MapOperationError.Validation(
                    field = MapInputField.SCALE,
                    reason = "Scale must be a positive number.",
                ),
            )
        }
        val offsetX = offsetXText.toDoubleOrNull()
            ?: return MapResult.failure(
                MapOperationError.Validation(
                    field = MapInputField.OFFSET_X,
                    reason = "Offset X must be a number.",
                ),
            )
        val offsetY = offsetYText.toDoubleOrNull()
            ?: return MapResult.failure(
                MapOperationError.Validation(
                    field = MapInputField.OFFSET_Y,
                    reason = "Offset Y must be a number.",
                ),
            )
        return MapResult.success(
            GridCalibration(
                cellSizeInPixels = cellSize,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
            ),
        )
    }

    fun parseGuidedTileSpan(tileSpanText: String): MapResult<Int> {
        val tileSpan = tileSpanText.trim().toIntOrNull()
            ?: return MapResult.failure(
                MapOperationError.Validation(
                    field = MapInputField.GUIDED_TILE_SPAN,
                    reason = "Wide mode tiles away must be a positive whole number.",
                ),
            )
        if (tileSpan <= 0) {
            return MapResult.failure(
                MapOperationError.Validation(
                    field = MapInputField.GUIDED_TILE_SPAN,
                    reason = "Wide mode tiles away must be a positive whole number.",
                ),
            )
        }
        return MapResult.success(tileSpan)
    }

    fun guidedStep1(
        current: MapCalibration,
        worldX: Double,
        worldY: Double,
        canvasCenterX: Double,
        canvasCenterY: Double,
    ): MapCalibration =
        guidedCalibrationStep1(
            current = current,
            clickX = worldX,
            clickY = worldY,
            targetX = canvasCenterX,
            targetY = canvasCenterY,
        )

    fun guidedStep2(
        currentCalibration: MapCalibration?,
        worldX: Double,
        worldY: Double,
        canvasCenterX: Double,
        canvasCenterY: Double,
        gridCellPixels: Double,
        axis: GuidedCalibrationAxis = GuidedCalibrationAxis.HORIZONTAL,
        targetTileSpan: Int = 1,
    ): MapResult<MapCalibration> {
        val current = currentCalibration
            ?: return MapResult.failure(MapOperationError.GuidedCalibrationBaseMissing)
        if (targetTileSpan <= 0) {
            return MapResult.failure(
                MapOperationError.Validation(
                    field = MapInputField.GUIDED_TILE_SPAN,
                    reason = "Wide mode tiles away must be a positive whole number.",
                ),
            )
        }

        val updated = guidedCalibrationStep2(
            currentCalibration = current,
            cornerX = worldX,
            cornerY = worldY,
            targetX = canvasCenterX,
            targetY = canvasCenterY,
            cellSizeInPixels = gridCellPixels,
            axis = axis,
            targetTileSpan = targetTileSpan,
        ) ?: return MapResult.failure(MapOperationError.GuidedCalibrationTargetTooClose)

        return MapResult.success(updated)
    }
}

class MapFogOfWarService(private val halfFogCells: Int = DEFAULT_HALF_FOG_CELLS) {
    private var fogState: FogOfWarState? = null
    private var fogColOffset: Int = -halfFogCells
    private var fogRowOffset: Int = -halfFogCells

    fun ensureInitialized() {
        if (fogState != null) return
        val cols = halfFogCells * 2
        val rows = halfFogCells * 2
        fogState = FogOfWarState(cols = cols, rows = rows)
        fogColOffset = -halfFogCells
        fogRowOffset = -halfFogCells
        EventBus.publish(
            FogOfWarSetupEvent(
                cols = cols,
                rows = rows,
                colOffset = fogColOffset,
                rowOffset = fogRowOffset,
            ),
        )
    }

    fun paintCell(cell: Pair<Int, Int>, revealed: Boolean): MapResult<Unit> {
        ensureInitialized()
        val state = fogState ?: return MapResult.success(Unit)
        val (col, row) = cell
        if (col !in 0 until state.cols || row !in 0 until state.rows) {
            return MapResult.success(Unit)
        }
        if (state.isRevealed(col, row) == revealed) {
            return MapResult.success(Unit)
        }
        if (revealed) {
            state.revealCell(col, row)
        } else {
            state.hideCell(col, row)
        }
        EventBus.publish(FogOfWarCellEvent(col = col, row = row, revealed = revealed))
        return MapResult.success(Unit)
    }

    fun reset(revealAll: Boolean): MapResult<Unit> {
        ensureInitialized()
        val state = fogState ?: return MapResult.success(Unit)
        if (revealAll) {
            state.revealAll()
        } else {
            state.hideAll()
        }
        EventBus.publish(FogOfWarResetEvent(revealAll = revealAll))
        return MapResult.success(Unit)
    }

    fun replayState() {
        val state = fogState ?: return
        EventBus.publish(
            FogOfWarSetupEvent(
                cols = state.cols,
                rows = state.rows,
                colOffset = fogColOffset,
                rowOffset = fogRowOffset,
            ),
        )

        val revealedCells = state.revealedCount()
        if (revealedCells == 0) return
        if (revealedCells == state.cols * state.rows) {
            EventBus.publish(FogOfWarResetEvent(revealAll = true))
            return
        }

        for (col in 0 until state.cols) {
            for (row in 0 until state.rows) {
                if (state.isRevealed(col, row)) {
                    EventBus.publish(FogOfWarCellEvent(col = col, row = row, revealed = true))
                }
            }
        }
    }

    internal fun snapshot(): MapFogSceneState? {
        val state = fogState ?: return null
        val totalCells = state.cols * state.rows
        val revealedCells = state.revealedCount()
        val hiddenCells = totalCells - revealedCells
        val mode = when {
            revealedCells == 0 -> MapFogSceneMode.HIDDEN_ALL
            hiddenCells == 0 -> MapFogSceneMode.REVEALED_ALL
            revealedCells <= hiddenCells -> MapFogSceneMode.PARTIAL_REVEALED
            else -> MapFogSceneMode.PARTIAL_HIDDEN
        }

        val cells = if (mode == MapFogSceneMode.PARTIAL_REVEALED || mode == MapFogSceneMode.PARTIAL_HIDDEN) {
            buildList {
                for (col in 0 until state.cols) {
                    for (row in 0 until state.rows) {
                        val includeCell = when (mode) {
                            MapFogSceneMode.PARTIAL_REVEALED -> state.isRevealed(col, row)
                            MapFogSceneMode.PARTIAL_HIDDEN -> !state.isRevealed(col, row)
                            else -> false
                        }
                        if (includeCell) {
                            add(Pair(col, row))
                        }
                    }
                }
            }
        } else {
            emptyList()
        }

        return MapFogSceneState(
            cols = state.cols,
            rows = state.rows,
            colOffset = fogColOffset,
            rowOffset = fogRowOffset,
            mode = mode,
            cells = cells,
        )
    }

    internal fun applySnapshot(snapshot: MapFogSceneState?) {
        if (snapshot == null) {
            fogState = null
            fogColOffset = -halfFogCells
            fogRowOffset = -halfFogCells
            ensureInitialized()
            return
        }

        val nextState = FogOfWarState(cols = snapshot.cols, rows = snapshot.rows)
        when (snapshot.mode) {
            MapFogSceneMode.HIDDEN_ALL -> Unit
            MapFogSceneMode.REVEALED_ALL -> nextState.revealAll()
            MapFogSceneMode.PARTIAL_REVEALED -> snapshot.cells.forEach { (col, row) ->
                if (col in 0 until snapshot.cols && row in 0 until snapshot.rows) {
                    nextState.revealCell(col, row)
                }
            }
            MapFogSceneMode.PARTIAL_HIDDEN -> {
                nextState.revealAll()
                snapshot.cells.forEach { (col, row) ->
                    if (col in 0 until snapshot.cols && row in 0 until snapshot.rows) {
                        nextState.hideCell(col, row)
                    }
                }
            }
        }
        fogState = nextState
        fogColOffset = snapshot.colOffset
        fogRowOffset = snapshot.rowOffset
        replayState()
    }

    companion object {
        private const val DEFAULT_HALF_FOG_CELLS = 100
    }
}

class MapMeasurementService {
    private val measurements = linkedMapOf<String, MeasurementOverlay>()

    val availableUnits: List<String> = listOf("ft", "m")

    var measurementUnits: String = "ft"
        private set

    var coneAngleDegrees: Double = MeasurementOverlay.DEFAULT_MEASUREMENT_CONE_ANGLE_DEGREES
        private set

    var defaultMirrorToTable: Boolean = false
        private set

    fun hasMeasurements(): Boolean = measurements.isNotEmpty()

    fun replayState() {
        measurements.values.forEach { EventBus.publish(MeasurementAddedEvent(it)) }
    }

    fun findMeasurementAt(cell: Pair<Int, Int>): MeasurementOverlay? =
        measurements.values.lastOrNull { it.isNearCell(cell.first, cell.second) }

    fun setMeasurementUnits(units: String): MapResult<String> {
        val normalized = units.trim().lowercase()
        if (normalized !in availableUnits) {
            return MapResult.failure(MapOperationError.UnsupportedMeasurementUnits(units))
        }
        measurementUnits = normalized
        return MapResult.success(measurementUnits)
    }

    fun setConeAngleDegrees(degrees: Double): MapResult<Double> {
        if (!degrees.isFinite() || degrees !in 1.0..360.0) {
            return MapResult.failure(
                MapOperationError.Validation(
                    field = MapInputField.CONE_ANGLE,
                    reason = "Cone angle must be between 1 and 360 degrees.",
                ),
            )
        }
        coneAngleDegrees = degrees
        return MapResult.success(coneAngleDegrees)
    }

    fun setDefaultMirrorToTable(enabled: Boolean) {
        defaultMirrorToTable = enabled
    }

    fun createMeasurement(type: MeasurementType, cell: Pair<Int, Int>): MapResult<MeasurementOverlay> {
        val overlay = MeasurementOverlay(
            id = UUID.randomUUID().toString(),
            type = type,
            startCol = cell.first,
            startRow = cell.second,
            endCol = cell.first,
            endRow = cell.second,
            coneAngleDegrees = coneAngleDegrees,
            mirroredToTable = defaultMirrorToTable,
            unitsSuffix = measurementUnits,
        )
        measurements[overlay.id] = overlay
        EventBus.publish(MeasurementAddedEvent(overlay))
        return MapResult.success(overlay)
    }

    fun updateMeasurement(id: String, endCell: Pair<Int, Int>): MapResult<MeasurementOverlay> {
        val current = measurements[id]
            ?: return MapResult.failure(MapOperationError.MeasurementNotFound(id))
        val updated = current.copy(
            endCol = endCell.first,
            endRow = endCell.second,
            coneAngleDegrees = coneAngleDegrees,
        )
        measurements[id] = updated
        EventBus.publish(MeasurementUpdatedEvent(updated))
        return MapResult.success(updated)
    }

    fun toggleMirroredToTable(id: String): MapResult<MeasurementOverlay> {
        val current = measurements[id]
            ?: return MapResult.failure(MapOperationError.MeasurementNotFound(id))
        val updated = current.copy(mirroredToTable = !current.mirroredToTable)
        measurements[id] = updated
        EventBus.publish(MeasurementUpdatedEvent(updated))
        return MapResult.success(updated)
    }

    fun updateLabel(id: String, label: String): MapResult<MeasurementOverlay> {
        val current = measurements[id]
            ?: return MapResult.failure(MapOperationError.MeasurementNotFound(id))
        val updated = current.copy(unitLabel = label.trim())
        measurements[id] = updated
        EventBus.publish(MeasurementUpdatedEvent(updated))
        return MapResult.success(updated)
    }

    fun removeMeasurement(id: String): MapResult<Unit> {
        val removed = measurements.remove(id)
            ?: return MapResult.failure(MapOperationError.MeasurementNotFound(id))
        EventBus.publish(MeasurementRemovedEvent(removed.id))
        return MapResult.success(Unit)
    }

    fun clearAll(): MapResult<Unit> {
        if (measurements.isEmpty()) {
            return MapResult.success(Unit)
        }
        measurements.clear()
        EventBus.publish(MeasurementsClearedEvent)
        return MapResult.success(Unit)
    }
}

class MapTokenSyncService {
    private val tokens = linkedMapOf<String, Token>()
    private val subscriptions = mutableListOf<EventBus.Subscription>()

    private var activeTokenId: String? = null
    private var draggedTokenId: String? = null
    private var draggedTokenName: String? = null
    private var draggedTokenAnchorOffset: Pair<Int, Int> = Pair(0, 0)
    private var lastDragCell: Pair<Int, Int>? = null

    init {
        attachToEventBus()
    }

    fun beginDrag(token: Token, grabbedCell: Pair<Int, Int> = Pair(token.col, token.row)) {
        draggedTokenId = token.id
        draggedTokenName = token.name
        draggedTokenAnchorOffset = tokenDragAnchor(token, grabbedCell)
        lastDragCell = Pair(token.col, token.row)
    }

    fun publishDraggedTokenMove(cell: Pair<Int, Int>): MapResult<Unit> {
        val tokenId = draggedTokenId ?: return MapResult.failure(MapOperationError.TokenDragNotActive)
        val originCell = draggedTokenOrigin(cell, draggedTokenAnchorOffset)
        if (originCell == lastDragCell) {
            return MapResult.success(Unit)
        }
        lastDragCell = originCell
        val currentName = tokens[tokenId]?.name ?: draggedTokenName.orEmpty()
        EventBus.publish(
            TokenMovedEvent(
                id = tokenId,
                name = currentName,
                col = originCell.first,
                row = originCell.second,
            ),
        )
        return MapResult.success(Unit)
    }

    fun endDrag() {
        draggedTokenId = null
        draggedTokenName = null
        draggedTokenAnchorOffset = Pair(0, 0)
        lastDragCell = null
    }

    fun replayState() {
        tokens.values.forEach { token ->
            EventBus.publish(TokenAddedEvent(token.id, token.name, token.color, token.size))
            EventBus.publish(TokenMovedEvent(token.id, token.name, token.col, token.row))
            EventBus.publish(
                TokenImageChangedEvent(
                    id = token.id,
                    imageUri = token.imageUri,
                    imageScaleX = token.imageScaleX,
                    imageScaleY = token.imageScaleY,
                    imageOffsetX = token.imageOffsetX,
                    imageOffsetY = token.imageOffsetY,
                ),
            )
        }
        EventBus.publish(ActiveTokenChangedEvent(activeTokenId, null))
    }

    internal fun snapshotTokens(): List<Token> = tokens.values.map { token -> token.copy() }

    internal fun snapshotActiveTokenId(): String? = activeTokenId

    internal fun replaceState(nextTokens: List<Token>, nextActiveTokenId: String?) {
        val previousTokensById = tokens.mapValues { (_, token) -> token.copy() }
        EventBus.publish(TokensResetEvent())
        tokens.clear()
        nextTokens.forEach { token ->
            val previous = previousTokensById[token.id]
            tokens[token.id] = mergeRestoredToken(previous, token)
        }
        activeTokenId = nextActiveTokenId
        endDrag()
        replayState()
    }

    private fun mergeRestoredToken(previous: Token?, restored: Token): Token {
        val restoredHasUsableImage = LocalFiles.exists(restored.imageUri)
        val previousHasUsableImage = LocalFiles.exists(previous?.imageUri)

        if (restoredHasUsableImage || !previousHasUsableImage) {
            return restored.copy()
        }

        val previousToken = previous ?: return restored.copy()
        return restored.copy(
            imageUri = previousToken.imageUri,
            imageScaleX = previousToken.imageScaleX,
            imageScaleY = previousToken.imageScaleY,
            imageOffsetX = previousToken.imageOffsetX,
            imageOffsetY = previousToken.imageOffsetY,
        )
    }
    fun dispose() {
        subscriptions.forEach { it.unsubscribe() }
        subscriptions.clear()
    }

    private fun attachToEventBus() {
        subscriptions += EventBus.subscribe<TokenAddedEvent> { event ->
            val existing = tokens[event.id]
            if (existing != null) {
                tokens[event.id] = existing.copy(name = event.name, color = event.color, size = event.size)
            } else {
                val (nextTokenCol, nextTokenRow) = nextAvailableTokenPlacement(tokens.values, event.size)

                tokens[event.id] = Token(
                    id = event.id,
                    name = event.name,
                    col = nextTokenCol,
                    row = nextTokenRow,
                    size = event.size,
                    color = event.color,
                )
            }
        }
        subscriptions += EventBus.subscribe<TokenRemovedEvent> { event ->
            tokens.remove(event.id)
            if (activeTokenId == event.id) {
                activeTokenId = null
            }
            if (draggedTokenId == event.id) {
                endDrag()
            }
        }
        subscriptions += EventBus.subscribe<TokenMovedEvent> { event ->
            val current = tokens[event.id] ?: return@subscribe
            tokens[event.id] = current.copy(col = event.col, row = event.row)
        }
        subscriptions += EventBus.subscribe<ActiveTokenChangedEvent> { event ->
            activeTokenId = event.id
        }
        subscriptions += EventBus.subscribe<TokensResetEvent> {
            tokens.clear()
            activeTokenId = null
            endDrag()
        }
        subscriptions += EventBus.subscribe<TokenImageChangedEvent> { event ->
            val current = tokens[event.id] ?: return@subscribe
            tokens[event.id] = current.copy(
                imageUri = event.imageUri,
                imageScaleX = event.imageScaleX,
                imageScaleY = event.imageScaleY,
                imageOffsetX = event.imageOffsetX,
                imageOffsetY = event.imageOffsetY,
            )
        }
    }
}

class MapViewportState(
    private val zoomFactor: Double = 1.25,
    private val minScale: Double = 0.125,
    private val maxScale: Double = 8.0,
    val panStep: Double = 20.0,
) {
    var scale: Double = 1.0
        private set

    var offsetX: Double = 0.0
        private set

    var offsetY: Double = 0.0
        private set

    fun reset(renderer: MapRenderer) {
        scale = 1.0
        offsetX = 0.0
        offsetY = 0.0
        apply(renderer)
    }

    fun zoomFromScroll(renderer: MapRenderer, deltaY: Double) {
        val factor = if (deltaY > 0.0) zoomFactor else 1.0 / zoomFactor
        zoomBy(renderer, factor)
    }

    fun zoomIn(renderer: MapRenderer) {
        zoomBy(renderer, zoomFactor)
    }

    fun zoomOut(renderer: MapRenderer) {
        zoomBy(renderer, 1.0 / zoomFactor)
    }

    fun panLeft(renderer: MapRenderer) {
        panBy(renderer, dx = -panStep)
    }

    fun panRight(renderer: MapRenderer) {
        panBy(renderer, dx = panStep)
    }

    fun panUp(renderer: MapRenderer) {
        panBy(renderer, dy = -panStep)
    }

    fun panDown(renderer: MapRenderer) {
        panBy(renderer, dy = panStep)
    }

    fun setPan(renderer: MapRenderer, x: Double, y: Double) {
        offsetX = x
        offsetY = y
        apply(renderer)
    }

    fun canvasToWorld(
        canvasWidth: Double,
        canvasHeight: Double,
        canvasX: Double,
        canvasY: Double,
    ): Pair<Double, Double> {
        val centerX = canvasWidth / 2.0
        val centerY = canvasHeight / 2.0
        val worldX = (canvasX - centerX - offsetX) / scale + centerX
        val worldY = (canvasY - centerY - offsetY) / scale + centerY
        return Pair(worldX, worldY)
    }

    fun worldToCanvas(
        canvasWidth: Double,
        canvasHeight: Double,
        worldX: Double,
        worldY: Double,
    ): Pair<Double, Double> {
        val centerX = canvasWidth / 2.0
        val centerY = canvasHeight / 2.0
        val canvasX = centerX + offsetX + scale * (worldX - centerX)
        val canvasY = centerY + offsetY + scale * (worldY - centerY)
        return Pair(canvasX, canvasY)
    }

    private fun panBy(renderer: MapRenderer, dx: Double = 0.0, dy: Double = 0.0) {
        offsetX += dx
        offsetY += dy
        apply(renderer)
    }

    private fun zoomBy(renderer: MapRenderer, factor: Double) {
        scale = (scale * factor).coerceIn(minScale, maxScale)
        apply(renderer)
    }

    private fun apply(renderer: MapRenderer) {
        renderer.viewportScale = scale
        renderer.viewportOffsetX = offsetX
        renderer.viewportOffsetY = offsetY
        renderer.redraw()
    }
}
