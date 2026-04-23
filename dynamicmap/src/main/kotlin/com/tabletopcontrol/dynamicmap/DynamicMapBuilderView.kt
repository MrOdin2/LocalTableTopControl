package com.tabletopcontrol.dynamicmap

import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.ThemeChangedEvent
import com.tabletopcontrol.core.ThemeManager
import com.tabletopcontrol.core.ui.ContextMenuRenderer
import com.tabletopcontrol.core.ui.MenuAction
import com.tabletopcontrol.core.ui.MenuSection
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.core.ui.dialog.FileChooserHistoryStore
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import java.io.File
import java.util.Locale
import kotlin.math.min

private const val BACKGROUND_HISTORY_KEY = "dynamicmap_builder.background"
private const val SNAP_STEP = 0.25

class DynamicMapBuilderView(
    private val controller: DynamicMapBuilderController,
) {
    private var document: DynamicMapDocument = controller.currentDocument()
    private var preset: DynamicMapLightPreset = controller.currentPreset()
    private var activeTool: DynamicMapTool? = null
    private var snapEnabled: Boolean = true
    private var dragStart: DynamicMapPoint? = null
    private var dragCurrent: DynamicMapPoint? = null
    private var cachedBackgroundUri: String? = null
    private var cachedBackgroundImage: Image? = null
    private var isPanning: Boolean = false
    private var panDragStartX: Double = 0.0
    private var panDragStartY: Double = 0.0
    private var panDragStartOffsetX: Double = 0.0
    private var panDragStartOffsetY: Double = 0.0
    private val viewport = DynamicMapWorkspaceViewport()

    private val canvas = Canvas(1.0, 1.0)
    private val pathField = TextField(document.backgroundDisplayPath.orEmpty()).apply {
        isEditable = false
        promptText = "No texture loaded"
    }
    private val colsField = TextField(document.cols.toString()).apply {
        prefWidth = 56.0
        tooltip = Tooltip("Map width in grid cells")
    }
    private val rowsField = TextField(document.rows.toString()).apply {
        prefWidth = 56.0
        tooltip = Tooltip("Map height in grid cells")
    }
    private val statusLabel = Label()
    private val showBackgroundCheck = CheckBox("BG").apply { isSelected = document.visibility.background }
    private val showWallsCheck = CheckBox("Walls").apply { isSelected = document.visibility.walls }
    private val showLightsCheck = CheckBox("Lights").apply { isSelected = document.visibility.lights }
    private val showGridCheck = CheckBox("Grid").apply { isSelected = document.visibility.grid }
    private val snapCheck = CheckBox("Snap 0.25").apply { isSelected = snapEnabled }
    private val clearTextureButton = Button("Clear Texture").apply {
        tooltip = Tooltip("Remove the current background texture")
        setOnAction { controller.clearBackgroundImage() }
    }
    private val calibrateTextureButton = Button("Calibrate Texture...").apply {
        tooltip = Tooltip("Adjust the texture scale and position against the builder grid")
    }
    private val guidedCalibrationButton = Button("Guided Calibration...").apply {
        tooltip = Tooltip("Interactive two-step texture calibration")
    }
    private val zoomOutButton = Button("-").apply {
        tooltip = Tooltip("Zoom out")
        style = "-fx-min-width: 28px; -fx-max-width: 28px;"
        setOnAction {
            viewport.zoomOut()
            redraw()
            updateStatus(null)
        }
    }
    private val zoomInButton = Button("+").apply {
        tooltip = Tooltip("Zoom in")
        style = "-fx-min-width: 28px; -fx-max-width: 28px;"
        setOnAction {
            viewport.zoomIn()
            redraw()
            updateStatus(null)
        }
    }
    private val resetViewButton = Button("Reset View").apply {
        tooltip = Tooltip("Reset zoom and pan without changing the map grid or draft")
        setOnAction {
            viewport.reset()
            redraw()
            updateStatus(null)
        }
    }

    val root: Node

    init {
        HBox.setHgrow(pathField, Priority.ALWAYS)

        val textureRow = HBox(
            6.0,
            Button("Load Texture...").apply {
                tooltip = Tooltip("Load an image as the builder background")
                setOnAction { openTextureChooser() }
            },
            clearTextureButton,
            calibrateTextureButton,
            guidedCalibrationButton,
            pathField,
        )

        val layoutRow = HBox(
            6.0,
            Label("Map:"),
            colsField,
            Label("x"),
            rowsField,
            Button("Apply Size").apply {
                tooltip = Tooltip("Resize the logical map bounds without deleting placed content")
                setOnAction { applyMapSize() }
            },
            Separator(Orientation.VERTICAL),
            Label("Layers:"),
            showBackgroundCheck,
            showWallsCheck,
            showLightsCheck,
            showGridCheck,
            Separator(Orientation.VERTICAL),
            snapCheck,
            Separator(Orientation.VERTICAL),
            Label("View:"),
            zoomOutButton,
            zoomInButton,
            resetViewButton,
        )

        val canvasPane = object : Pane() {
            init {
                children += canvas
                minHeight = 260.0
                style = "-fx-background-color: -tc-surface; -fx-border-color: -tc-border;"
            }

            override fun layoutChildren() {
                if (canvas.width != width || canvas.height != height) {
                    canvas.width = width
                    canvas.height = height
                    redraw()
                }
            }
        }

        root = VBox(
            6.0,
            textureRow,
            layoutRow,
            canvasPane,
            statusLabel,
        ).apply {
            padding = Insets(6.0)
            style = "-fx-background-color: -tc-bg;"
            isFocusTraversable = true
            VBox.setVgrow(canvasPane, Priority.ALWAYS)
            setOnKeyPressed { event ->
                if (event.code == KeyCode.ESCAPE && activeTool != null) {
                    EventBus.publish(DynamicMapToolSelectedEvent(tool = null))
                    event.consume()
                }
            }
        }

        showBackgroundCheck.setOnAction {
            controller.setLayerVisible(DynamicMapLayer.BACKGROUND, showBackgroundCheck.isSelected)
        }
        showWallsCheck.setOnAction {
            controller.setLayerVisible(DynamicMapLayer.WALLS, showWallsCheck.isSelected)
        }
        showLightsCheck.setOnAction {
            controller.setLayerVisible(DynamicMapLayer.LIGHTS, showLightsCheck.isSelected)
        }
        showGridCheck.setOnAction {
            controller.setLayerVisible(DynamicMapLayer.GRID, showGridCheck.isSelected)
        }
        snapCheck.setOnAction {
            snapEnabled = snapCheck.isSelected
            redraw()
            updateStatus(null)
        }

        calibrateTextureButton.setOnAction {
            DynamicMapCalibrationDialogs.showBackgroundCalibrationDialog(
                owner = root.scene?.window,
                controller = controller,
                previewCellSize = currentMetrics()?.cellSize ?: 1.0,
            )
        }
        guidedCalibrationButton.setOnAction {
            DynamicMapCalibrationDialogs.showGuidedCalibrationDialog(
                owner = root.scene?.window,
                controller = controller,
            )
        }

        canvas.setOnMousePressed { event ->
            root.requestFocus()
            when (event.button) {
                MouseButton.PRIMARY -> {
                    when (activeTool) {
                        DynamicMapTool.LIGHT -> {
                            val point = mapPointFromCanvas(event.x, event.y, clampToBounds = false)
                                ?: return@setOnMousePressed
                            controller.addLight(normalizePoint(point))
                        }

                        DynamicMapTool.WALL_LINE,
                        DynamicMapTool.WALL_RECT,
                        -> {
                            val point = mapPointFromCanvas(event.x, event.y, clampToBounds = false)
                                ?: return@setOnMousePressed
                            dragStart = normalizePoint(point)
                            dragCurrent = dragStart
                            redraw()
                        }

                        null -> beginPan(event.x, event.y)
                    }
                }

                MouseButton.MIDDLE -> beginPan(event.x, event.y)
                else -> Unit
            }
        }

        canvas.setOnMouseDragged { event ->
            if (isPanning && (event.isPrimaryButtonDown || event.isMiddleButtonDown)) {
                viewport.setPan(
                    x = panDragStartOffsetX + (event.x - panDragStartX),
                    y = panDragStartOffsetY + (event.y - panDragStartY),
                )
                redraw()
                updateStatus(mapPointFromCanvas(event.x, event.y, clampToBounds = false)?.let(::normalizePoint))
                return@setOnMouseDragged
            }
            if (!event.isPrimaryButtonDown) return@setOnMouseDragged
            if (activeTool == DynamicMapTool.WALL_LINE || activeTool == DynamicMapTool.WALL_RECT) {
                val point = mapPointFromCanvas(event.x, event.y, clampToBounds = true) ?: return@setOnMouseDragged
                dragCurrent = normalizePoint(point)
                redraw()
                updateStatus(dragCurrent)
            }
        }

        canvas.setOnMouseReleased { event ->
            if (isPanning) {
                isPanning = false
                event.consume()
                return@setOnMouseReleased
            }
            if (event.button != MouseButton.PRIMARY) return@setOnMouseReleased
            val start = dragStart ?: return@setOnMouseReleased
            val end = dragCurrent ?: start
            when (activeTool) {
                DynamicMapTool.WALL_LINE -> {
                    if (start != end) {
                        controller.addWall(DynamicMapWall(start = start, end = end))
                    }
                }

                DynamicMapTool.WALL_RECT -> {
                    controller.addWalls(buildRectangleWalls(start, end))
                }

                else -> Unit
            }
            dragStart = null
            dragCurrent = null
            redraw()
        }

        canvas.setOnMouseMoved { event ->
            updateStatus(mapPointFromCanvas(event.x, event.y, clampToBounds = false)?.let(::normalizePoint))
        }

        canvas.setOnMouseExited {
            updateStatus(null)
        }

        canvas.setOnScroll { event ->
            viewport.zoomFromScroll(event.deltaY)
            redraw()
            updateStatus(mapPointFromCanvas(event.x, event.y, clampToBounds = false)?.let(::normalizePoint))
            event.consume()
        }

        canvas.setOnContextMenuRequested { event ->
            if (showContextMenu(event.x, event.y, event.screenX, event.screenY)) {
                event.consume()
            }
        }

        val disposers = mutableListOf<() -> Unit>()
        disposers += controller.observeDocument { updated ->
            document = updated
            pathField.text = updated.backgroundDisplayPath.orEmpty()
            colsField.text = updated.cols.toString()
            rowsField.text = updated.rows.toString()
            showBackgroundCheck.isSelected = updated.visibility.background
            showWallsCheck.isSelected = updated.visibility.walls
            showLightsCheck.isSelected = updated.visibility.lights
            showGridCheck.isSelected = updated.visibility.grid
            clearTextureButton.isDisable = updated.backgroundImageUri == null
            calibrateTextureButton.isDisable = updated.backgroundImageUri == null
            guidedCalibrationButton.isDisable = updated.backgroundImageUri == null
            redraw()
            updateStatus(null)
        }
        disposers += controller.observePreset { updated ->
            preset = updated
            updateStatus(null)
        }
        val toolSubscription = EventBus.subscribe<DynamicMapToolSelectedEvent> { event ->
            activeTool = event.tool
            dragStart = null
            dragCurrent = null
            redraw()
            updateStatus(null)
        }
        disposers += { toolSubscription.unsubscribe() }
        val themeSubscription = EventBus.subscribe<ThemeChangedEvent> {
            redraw()
        }
        disposers += { themeSubscription.unsubscribe() }

        root.sceneProperty().addListener { _, _, newScene ->
            if (newScene == null) {
                disposers.toList().forEach { it() }
            }
        }

        redraw()
        updateStatus(null)
    }

    private fun openTextureChooser() {
        val chooser = FileChooser().apply {
            title = "Select background texture"
            extensionFilters.addAll(
                FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
                FileChooser.ExtensionFilter("All files", "*.*"),
            )
            FileChooserHistoryStore.configureInitialDirectory(
                chooser = this,
                key = BACKGROUND_HISTORY_KEY,
                fallbackSelection = document.backgroundDisplayPath?.let(::File),
            )
        }
        val file = chooser.showOpenDialog(root.scene?.window)
        if (file != null) {
            FileChooserHistoryStore.rememberSelection(BACKGROUND_HISTORY_KEY, file)
            val uri = file.toURI().toString()
            val image = runCatching { Image(uri, false) }
                .getOrNull()
                ?.takeUnless { it.isError }
            val calibration = image?.let {
                fittedBackgroundCalibration(
                    imageWidth = it.width,
                    imageHeight = it.height,
                    cols = document.cols,
                    rows = document.rows,
                )
            } ?: DynamicMapBackgroundCalibration()
            controller.setBackgroundImage(uri, file.absolutePath, calibration)
        }
    }

    private fun applyMapSize() {
        val cols = colsField.text.toIntOrNull()
        val rows = rowsField.text.toIntOrNull()
        if (cols != null && rows != null) {
            controller.setMapSize(cols, rows)
        } else {
            colsField.text = document.cols.toString()
            rowsField.text = document.rows.toString()
        }
    }

    private fun beginPan(canvasX: Double, canvasY: Double) {
        isPanning = true
        panDragStartX = canvasX
        panDragStartY = canvasY
        panDragStartOffsetX = viewport.offsetX
        panDragStartOffsetY = viewport.offsetY
    }

    private fun showContextMenu(canvasX: Double, canvasY: Double, screenX: Double, screenY: Double): Boolean {
        val point = mapPointFromCanvas(canvasX, canvasY, clampToBounds = false)
        val metrics = currentMetrics() ?: return false
        val tolerance = 12.0 / (metrics.cellSize * viewport.scale)

        val nearestLight = point?.let {
            document.lights
                .map { light -> light to distanceToSegment(it, light.position, light.position) }
                .filter { (_, distance) -> distance <= tolerance }
                .minByOrNull { it.second }
        }
        val nearestWall = point?.let {
            document.walls
                .map { wall -> wall to distanceToWall(it, wall) }
                .filter { (_, distance) -> distance <= tolerance }
                .minByOrNull { it.second }
        }

        val actions = mutableListOf<MenuAction>()
        val chosenLight = nearestLight?.takeIf { nearestWall == null || it.second <= nearestWall.second }?.first
        val chosenWall = nearestWall?.takeIf { nearestLight == null || it.second < nearestLight.second }?.first

        if (chosenLight != null) {
            actions += MenuAction(
                id = "dynamicmap_builder.remove-light",
                label = "Remove ${chosenLight.label}",
                section = MenuSection.DANGER_ZONE,
                onAction = { controller.removeLight(chosenLight.id) },
            )
        }
        if (chosenWall != null) {
            actions += MenuAction(
                id = "dynamicmap_builder.remove-wall",
                label = "Remove Wall",
                section = MenuSection.DANGER_ZONE,
                onAction = { controller.removeWall(chosenWall.id) },
            )
        }
        if (actions.isEmpty()) return false
        ContextMenuRenderer.build(actions).show(canvas, screenX, screenY)
        return true
    }

    private fun redraw() {
        val width = canvas.width
        val height = canvas.height
        if (width <= 0.0 || height <= 0.0) return

        val metrics = computeMetrics(width, height)
        val theme = ThemeManager.currentTheme
        val backgroundColor = ColorHexCodec.hexToColor(theme.bgColor)
        val surfaceColor = ColorHexCodec.hexToColor(theme.surfaceColor)
        val borderColor = ColorHexCodec.hexToColor(theme.borderColor)
        val accentColor = ColorHexCodec.hexToColor(theme.accentColor)

        val gc = canvas.graphicsContext2D
        gc.clearRect(0.0, 0.0, width, height)
        gc.fill = backgroundColor
        gc.fillRect(0.0, 0.0, width, height)

        gc.save()
        applyWorkspaceViewportTransform(width, height)

        gc.fill = surfaceColor
        gc.fillRect(metrics.originX, metrics.originY, metrics.mapWidth, metrics.mapHeight)

        if (document.visibility.background) {
            resolveBackgroundImage()?.let { image ->
                drawCalibratedBackgroundImage(gc, image, metrics, document.backgroundCalibration)
            }
        }

        if (document.visibility.lights) {
            drawLightHalos(gc, metrics)
        }
        if (document.visibility.grid) {
            gc.stroke = borderColor.deriveColor(0.0, 1.0, 1.0, 0.55)
            gc.lineWidth = 1.0
            for (col in 0..document.cols) {
                val x = metrics.originX + col * metrics.cellSize
                gc.strokeLine(x, metrics.originY, x, metrics.originY + metrics.mapHeight)
            }
            for (row in 0..document.rows) {
                val y = metrics.originY + row * metrics.cellSize
                gc.strokeLine(metrics.originX, y, metrics.originX + metrics.mapWidth, y)
            }
        }
        if (document.visibility.walls) {
            gc.stroke = accentColor
            gc.lineWidth = (metrics.cellSize * 0.12).coerceAtLeast(2.0)
            document.walls.forEach { wall ->
                gc.strokeLine(
                    metrics.originX + wall.start.x * metrics.cellSize,
                    metrics.originY + wall.start.y * metrics.cellSize,
                    metrics.originX + wall.end.x * metrics.cellSize,
                    metrics.originY + wall.end.y * metrics.cellSize,
                )
            }
        }
        if (document.visibility.lights) {
            drawLightMarkers(gc, metrics)
        }

        if (dragStart != null && dragCurrent != null) {
            gc.stroke = accentColor.deriveColor(0.0, 1.0, 1.0, 0.8)
            gc.setLineDashes(8.0, 6.0)
            gc.lineWidth = 2.0
            when (activeTool) {
                DynamicMapTool.WALL_LINE -> {
                    val start = dragStart ?: return
                    val end = dragCurrent ?: return
                    gc.strokeLine(
                        metrics.originX + start.x * metrics.cellSize,
                        metrics.originY + start.y * metrics.cellSize,
                        metrics.originX + end.x * metrics.cellSize,
                        metrics.originY + end.y * metrics.cellSize,
                    )
                }

                DynamicMapTool.WALL_RECT -> {
                    val start = dragStart ?: return
                    val end = dragCurrent ?: return
                    val minX = min(start.x, end.x)
                    val minY = min(start.y, end.y)
                    val widthCells = kotlin.math.abs(end.x - start.x)
                    val heightCells = kotlin.math.abs(end.y - start.y)
                    gc.strokeRect(
                        metrics.originX + minX * metrics.cellSize,
                        metrics.originY + minY * metrics.cellSize,
                        widthCells * metrics.cellSize,
                        heightCells * metrics.cellSize,
                    )
                }

                else -> Unit
            }
            gc.setLineDashes()
        }

        gc.stroke = borderColor
        gc.lineWidth = 2.0
        gc.strokeRect(metrics.originX, metrics.originY, metrics.mapWidth, metrics.mapHeight)

        gc.restore()
    }

    private fun applyWorkspaceViewportTransform(width: Double, height: Double) {
        val gc = canvas.graphicsContext2D
        val centerX = width / 2.0
        val centerY = height / 2.0
        gc.translate(centerX + viewport.offsetX, centerY + viewport.offsetY)
        gc.scale(viewport.scale, viewport.scale)
        gc.translate(-centerX, -centerY)
    }

    private fun drawLightHalos(
        gc: javafx.scene.canvas.GraphicsContext,
        metrics: DynamicMapEditorMetrics,
    ) {
        document.lights.filter { it.enabled }.forEach { light ->
            val color = ColorHexCodec.hexToColor(light.colorHex)
            val centerX = metrics.originX + light.position.x * metrics.cellSize
            val centerY = metrics.originY + light.position.y * metrics.cellSize
            val dimRadius = light.dimRadius * metrics.cellSize
            val brightRadius = light.brightRadius * metrics.cellSize

            gc.fill = color.deriveColor(0.0, 1.0, 1.0, 0.14)
            gc.fillOval(centerX - dimRadius, centerY - dimRadius, dimRadius * 2.0, dimRadius * 2.0)

            gc.fill = color.deriveColor(0.0, 1.0, 1.0, 0.28)
            gc.fillOval(centerX - brightRadius, centerY - brightRadius, brightRadius * 2.0, brightRadius * 2.0)
        }
    }

    private fun drawLightMarkers(
        gc: javafx.scene.canvas.GraphicsContext,
        metrics: DynamicMapEditorMetrics,
    ) {
        document.lights.filter { it.enabled }.forEach { light ->
            val color = ColorHexCodec.hexToColor(light.colorHex)
            val centerX = metrics.originX + light.position.x * metrics.cellSize
            val centerY = metrics.originY + light.position.y * metrics.cellSize
            val radius = (metrics.cellSize * 0.18).coerceAtLeast(4.0)

            gc.fill = color
            gc.fillOval(centerX - radius, centerY - radius, radius * 2.0, radius * 2.0)
            gc.stroke = Color.WHITE.deriveColor(0.0, 1.0, 1.0, 0.7)
            gc.lineWidth = 1.0
            gc.strokeOval(centerX - radius, centerY - radius, radius * 2.0, radius * 2.0)
        }
    }

    private fun resolveBackgroundImage(): Image? {
        val uri = document.backgroundImageUri
        if (uri.isNullOrBlank()) {
            cachedBackgroundUri = null
            cachedBackgroundImage = null
            return null
        }
        if (uri != cachedBackgroundUri) {
            cachedBackgroundUri = uri
            cachedBackgroundImage = runCatching { Image(uri, false) }
                .getOrNull()
                ?.takeUnless { it.isError }
        }
        return cachedBackgroundImage
    }

    private fun computeMetrics(width: Double, height: Double): DynamicMapEditorMetrics =
        computeEditorMetrics(
            width = width,
            height = height,
            cols = document.cols,
            rows = document.rows,
        )

    private fun currentMetrics(): DynamicMapEditorMetrics? {
        val width = canvas.width
        val height = canvas.height
        if (width <= 0.0 || height <= 0.0) return null
        return computeMetrics(width, height)
    }

    private fun mapPointFromCanvas(
        canvasX: Double,
        canvasY: Double,
        clampToBounds: Boolean,
    ): DynamicMapPoint? {
        val metrics = currentMetrics() ?: return null
        val (worldX, worldY) = viewport.canvasToWorld(
            canvasWidth = canvas.width,
            canvasHeight = canvas.height,
            canvasX = canvasX,
            canvasY = canvasY,
        )
        val raw = DynamicMapPoint(
            x = (worldX - metrics.originX) / metrics.cellSize,
            y = (worldY - metrics.originY) / metrics.cellSize,
        )
        return if (clampToBounds) {
            clampPointToMap(raw, document.cols, document.rows)
        } else {
            raw.takeIf { it.x in 0.0..document.cols.toDouble() && it.y in 0.0..document.rows.toDouble() }
        }
    }

    private fun normalizePoint(point: DynamicMapPoint): DynamicMapPoint {
        val clamped = clampPointToMap(point, document.cols, document.rows)
        return if (snapEnabled) snapPoint(clamped, SNAP_STEP) else clamped
    }

    private fun updateStatus(mapPoint: DynamicMapPoint?) {
        val toolText = when (activeTool) {
            DynamicMapTool.WALL_LINE -> "Tool: wall line"
            DynamicMapTool.WALL_RECT -> "Tool: wall rectangle"
            DynamicMapTool.LIGHT -> "Tool: light placement"
            null -> "Tool: none"
        }
        val pointerText = mapPoint?.let {
            "Pointer ${formatGrid(it.x)}, ${formatGrid(it.y)}"
        } ?: "Pointer off map"
        statusLabel.text =
            "$toolText | Preset: ${preset.displayName} | Walls: ${document.walls.size} | " +
                "Lights: ${document.lights.size} | View: ${(viewport.scale * 100).toInt()}% | $pointerText"
        statusLabel.style = "-fx-text-fill: -tc-text-muted;"
    }

    private fun formatGrid(value: Double): String =
        String.format(Locale.US, "%.2f", value)
}
