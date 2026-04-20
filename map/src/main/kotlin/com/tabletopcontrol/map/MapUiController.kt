package com.tabletopcontrol.map

import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.ui.ContextMenuRenderer
import com.tabletopcontrol.core.ui.MenuAction
import com.tabletopcontrol.core.ui.MenuSection
import com.tabletopcontrol.core.ui.color.ColorContrast
import com.tabletopcontrol.core.ui.color.ColorEditorDialog
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.map.logic.MapCalibrationService
import com.tabletopcontrol.map.logic.MapFogOfWarService
import com.tabletopcontrol.map.logic.MapMeasurementService
import com.tabletopcontrol.map.logic.MapSettingsService
import com.tabletopcontrol.map.logic.MapTokenSyncService
import com.tabletopcontrol.map.logic.MapViewportState
import com.tabletopcontrol.map.ui.MapCalibrationDialogs
import com.tabletopcontrol.map.ui.MapMeasurementDialogs
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.CustomMenuItem
import javafx.scene.control.Label
import javafx.scene.control.MenuButton
import javafx.scene.control.Separator
import javafx.scene.control.TextField
import javafx.scene.control.ToggleButton
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.FileChooser

private enum class FogTool { NONE, DRAW, ERASE }

private enum class MeasurementTool { NONE, LINE, CONE, RECTANGLE, CIRCLE }

class MapUiController {
    private val settingsService = MapSettingsService()
    private val calibrationService = MapCalibrationService()
    private val fogOfWarService = MapFogOfWarService()
    private val measurementService = MapMeasurementService()
    private val tokenSyncService = MapTokenSyncService()

    private var measurementUnitsComboBox: ComboBox<String>? = null

    fun createTableView(): Node {
        val canvas = Canvas()
        val renderer = MapRenderer(canvas).apply {
            hideTokensInFog = true
            showDmOnlyMeasurements = false
        }
        hydrateRenderer(canvas, renderer)
        return object : Pane() {
            init {
                children.add(canvas)
            }

            override fun layoutChildren() {
                if (canvas.width != width || canvas.height != height) {
                    canvas.width = width
                    canvas.height = height
                    renderer.redraw()
                    EventBus.publish(TableViewportChangedEvent(width = width, height = height))
                }
            }
        }
    }

    fun createView(): Node {
        val view = VBox(4.0).apply { padding = Insets(4.0) }
        val minimapSection = buildMinimapSection()
        VBox.setVgrow(minimapSection, Priority.ALWAYS)
        view.children.addAll(
            minimapSection,
            Separator(),
            buildCompactControls(),
        )
        return view
    }

    fun onShutdown() {
        tokenSyncService.dispose()
    }

    private fun hydrateRenderer(canvas: Canvas, renderer: MapRenderer) {
        canvas.sceneProperty().addListener { _, _, newScene ->
            if (newScene == null) {
                renderer.dispose()
            }
        }
        settingsService.publishCurrentSettings()
        fogOfWarService.replayState()
        measurementService.replayState()
        tokenSyncService.replayState()
    }

    private fun buildMinimapSection(): VBox {
        val canvas = Canvas(1.0, 1.0)
        val renderer = MapRenderer(canvas).apply {
            fogOpacity = 0.5
            applyTableMapOffset = false
            showTableViewportOutline = true
        }
        val viewport = MapViewportState()

        hydrateRenderer(canvas, renderer)

        val canvasPane = object : Pane() {
            init {
                children.add(canvas)
                style = "-fx-border-color: gray;"
                minHeight = 80.0
            }

            override fun layoutChildren() {
                if (kotlin.math.abs(canvas.width - width) > 0.5 || kotlin.math.abs(canvas.height - height) > 0.5) {
                    canvas.width = width
                    canvas.height = height
                    renderer.redraw()
                }
            }
        }

        var fogTool = FogTool.NONE
        var measurementTool = MeasurementTool.NONE
        var activeMeasurementId: String? = null
        var dragStartX = 0.0
        var dragStartY = 0.0
        var dragStartOffsetX = 0.0
        var dragStartOffsetY = 0.0

        val drawFogButton = ToggleButton("Draw Fog").apply {
            tooltip = Tooltip("Draw fog by clicking or dragging on the minimap")
        }
        val eraseFogButton = ToggleButton("Erase Fog").apply {
            tooltip = Tooltip("Reveal fog by clicking or dragging on the minimap")
        }
        val lineMeasureButton = ToggleButton("Line").apply {
            tooltip = Tooltip("Measurement tool: line")
        }
        val coneMeasureButton = ToggleButton("Cone").apply {
            tooltip = Tooltip("Measurement tool: cone")
        }
        val rectMeasureButton = ToggleButton("Rect").apply {
            tooltip = Tooltip("Measurement tool: rectangle")
        }
        val circleMeasureButton = ToggleButton("Circle").apply {
            tooltip = Tooltip("Measurement tool: circle")
        }
        val measureMenuButton = MenuButton("Measure").apply {
            tooltip = Tooltip("Measurement tools and options")
        }

        fun deactivateMeasurementButtons() {
            lineMeasureButton.isSelected = false
            coneMeasureButton.isSelected = false
            rectMeasureButton.isSelected = false
            circleMeasureButton.isSelected = false
            measurementTool = MeasurementTool.NONE
            measureMenuButton.text = "Measure"
        }

        fun activateMeasurementTool(tool: MeasurementTool, button: ToggleButton) {
            if (button.isSelected) {
                drawFogButton.isSelected = false
                eraseFogButton.isSelected = false
                fogTool = FogTool.NONE
                deactivateMeasurementButtons()
                button.isSelected = true
                measurementTool = tool
                measureMenuButton.text = "Measure: ${button.text}"
            } else {
                deactivateMeasurementButtons()
            }
        }

        canvas.setOnMousePressed { event ->
            if (event.button == MouseButton.PRIMARY) {
                when {
                    measurementTool != MeasurementTool.NONE -> {
                        val cell = renderer.canvasCoordsToGridCell(event.x, event.y)
                        measurementService.createMeasurement(measurementTypeFor(measurementTool), cell)
                            .onSuccess { activeMeasurementId = it.id }
                    }
                    fogTool != FogTool.NONE -> {
                        renderer.canvasCoordsToFogCell(event.x, event.y)?.let { cell ->
                            fogOfWarService.paintCell(cell, revealed = fogTool == FogTool.ERASE)
                        }
                    }
                    else -> {
                        val token = renderer.tokenAtCanvasCoords(event.x, event.y)
                        if (token != null) {
                            tokenSyncService.beginDrag(token)
                        } else {
                            dragStartX = event.x
                            dragStartY = event.y
                            dragStartOffsetX = viewport.offsetX
                            dragStartOffsetY = viewport.offsetY
                        }
                    }
                }
            } else if (event.button == MouseButton.SECONDARY) {
                val clickedCell = renderer.canvasCoordsToGridCell(event.x, event.y)
                val selectedMeasurement = measurementService.findMeasurementAt(clickedCell)
                ContextMenuRenderer.build(
                    buildMeasurementContextMenuActions(canvas, selectedMeasurement),
                ).show(canvas, event.screenX, event.screenY)
            }
        }

        canvas.setOnMouseDragged { event ->
            if (!event.isPrimaryButtonDown) return@setOnMouseDragged
            when {
                measurementTool != MeasurementTool.NONE && activeMeasurementId != null -> {
                    val cell = renderer.canvasCoordsToGridCell(event.x, event.y)
                    measurementService.updateMeasurement(activeMeasurementId.orEmpty(), cell)
                }
                fogTool != FogTool.NONE -> {
                    renderer.canvasCoordsToFogCell(event.x, event.y)?.let { cell ->
                        fogOfWarService.paintCell(cell, revealed = fogTool == FogTool.ERASE)
                    }
                }
                else -> {
                    val cell = renderer.canvasCoordsToGridCell(event.x, event.y)
                    when (tokenSyncService.publishDraggedTokenMove(cell)) {
                        is MapResult.Success -> Unit
                        is MapResult.Failure -> {
                            viewport.setPan(
                                renderer,
                                dragStartOffsetX + (event.x - dragStartX),
                                dragStartOffsetY + (event.y - dragStartY),
                            )
                        }
                    }
                }
            }
        }

        canvas.setOnMouseReleased { event ->
            if (event.button == MouseButton.PRIMARY) {
                tokenSyncService.endDrag()
                activeMeasurementId = null
            }
        }

        canvas.setOnContextMenuRequested { event -> event.consume() }
        canvas.setOnScroll { event -> viewport.zoomFromScroll(renderer, event.deltaY) }

        val zoomOutButton = Button("-").apply {
            tooltip = Tooltip("Zoom out")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction { viewport.zoomOut(renderer) }
        }
        val zoomInButton = Button("+").apply {
            tooltip = Tooltip("Zoom in")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction { viewport.zoomIn(renderer) }
        }
        val resetButton = Button("Reset").apply {
            tooltip = Tooltip("Reset zoom and pan to default")
            setOnAction { viewport.reset(renderer) }
        }
        val panLeftButton = Button("\u25c0").apply {
            tooltip = Tooltip("Pan view left")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction { viewport.panLeft(renderer) }
        }
        val panRightButton = Button("\u25b6").apply {
            tooltip = Tooltip("Pan view right")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction { viewport.panRight(renderer) }
        }
        val panUpButton = Button("\u25b2").apply {
            tooltip = Tooltip("Pan view up")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction { viewport.panUp(renderer) }
        }
        val panDownButton = Button("\u25bc").apply {
            tooltip = Tooltip("Pan view down")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction { viewport.panDown(renderer) }
        }
        val mapLeftButton = Button("\u25c0").apply {
            tooltip = Tooltip("Move the whole table map left by one tile")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction { settingsService.nudgeTableMap(dxTiles = -1) }
        }
        val mapRightButton = Button("\u25b6").apply {
            tooltip = Tooltip("Move the whole table map right by one tile")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction { settingsService.nudgeTableMap(dxTiles = 1) }
        }
        val mapUpButton = Button("\u25b2").apply {
            tooltip = Tooltip("Move the whole table map up by one tile")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction { settingsService.nudgeTableMap(dyTiles = -1) }
        }
        val mapDownButton = Button("\u25bc").apply {
            tooltip = Tooltip("Move the whole table map down by one tile")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction { settingsService.nudgeTableMap(dyTiles = 1) }
        }
        val centerMapButton = Button("Center").apply {
            tooltip = Tooltip("Reset the whole table map to the screen centre")
            setOnAction { settingsService.resetTableMapOffset() }
        }

        val mirrorCheck = CheckBox("Mirror").apply {
            isSelected = measurementService.defaultMirrorToTable
            tooltip = Tooltip("Show newly created measurements on the table screen too")
            setOnAction { measurementService.setDefaultMirrorToTable(isSelected) }
        }

        val unitsBox = ComboBox<String>().apply {
            items.addAll(measurementService.availableUnits)
            selectionModel.select(measurementService.measurementUnits)
            tooltip = Tooltip("Units for new measurement labels")
            setOnAction {
                measurementService.setMeasurementUnits(value ?: measurementService.measurementUnits)
                    .onSuccess(::syncMeasurementUnitsSelection)
                    .onFailure { syncMeasurementUnitsSelection(measurementService.measurementUnits) }
            }
        }
        measurementUnitsComboBox = unitsBox

        val coneAngleBox = ComboBox<String>().apply {
            items.addAll("15\u00b0", "30\u00b0", "45\u00b0", "60\u00b0", "90\u00b0", "120\u00b0")
            val angleText = "${measurementService.coneAngleDegrees.toInt()}\u00b0"
            if (!items.contains(angleText)) {
                items.add(angleText)
            }
            selectionModel.select(angleText)
            tooltip = Tooltip("Cone angle for cone measurements")
            setOnAction {
                val parsed = (value ?: angleText).removeSuffix("\u00b0").toDoubleOrNull()
                if (parsed != null) {
                    measurementService.setConeAngleDegrees(parsed)
                }
            }
        }

        drawFogButton.setOnAction {
            if (drawFogButton.isSelected) {
                deactivateMeasurementButtons()
                eraseFogButton.isSelected = false
                fogTool = FogTool.DRAW
                fogOfWarService.ensureInitialized()
            } else {
                fogTool = FogTool.NONE
            }
        }
        eraseFogButton.setOnAction {
            if (eraseFogButton.isSelected) {
                deactivateMeasurementButtons()
                drawFogButton.isSelected = false
                fogTool = FogTool.ERASE
                fogOfWarService.ensureInitialized()
            } else {
                fogTool = FogTool.NONE
            }
        }
        lineMeasureButton.setOnAction { activateMeasurementTool(MeasurementTool.LINE, lineMeasureButton) }
        coneMeasureButton.setOnAction { activateMeasurementTool(MeasurementTool.CONE, coneMeasureButton) }
        rectMeasureButton.setOnAction { activateMeasurementTool(MeasurementTool.RECTANGLE, rectMeasureButton) }
        circleMeasureButton.setOnAction { activateMeasurementTool(MeasurementTool.CIRCLE, circleMeasureButton) }

        val measurementMenuContent = VBox(
            6.0,
            HBox(4.0, Label("Tools:"), lineMeasureButton, coneMeasureButton, rectMeasureButton, circleMeasureButton),
            HBox(4.0, Label("Units:"), unitsBox, Label("Cone:"), coneAngleBox),
            mirrorCheck,
        )
        measureMenuButton.items.setAll(CustomMenuItem(measurementMenuContent, false))

        val controlsRow = HBox(
            4.0,
            zoomOutButton,
            zoomInButton,
            resetButton,
            Separator(Orientation.VERTICAL),
            Label("View:"),
            panLeftButton,
            panUpButton,
            panDownButton,
            panRightButton,
            Separator(Orientation.VERTICAL),
            Label("Table:"),
            mapLeftButton,
            mapUpButton,
            mapDownButton,
            mapRightButton,
            centerMapButton,
            Separator(Orientation.VERTICAL),
            drawFogButton,
            eraseFogButton,
            Separator(Orientation.VERTICAL),
            measureMenuButton,
        )

        fogOfWarService.ensureInitialized()

        return VBox(4.0, canvasPane, controlsRow).apply {
            VBox.setVgrow(canvasPane, Priority.ALWAYS)
        }
    }

    private fun buildCompactControls(): VBox {
        val pathField = TextField(settingsService.currentMapDisplayPath.orEmpty()).apply {
            isEditable = false
            promptText = "No map loaded"
            tooltip = Tooltip("Path to the currently loaded map image")
        }
        HBox.setHgrow(pathField, Priority.ALWAYS)

        val loadButton = Button("Load Map...").apply {
            tooltip = Tooltip("Open a map image file")
            setOnAction { event ->
                val chooser = FileChooser().apply {
                    title = "Select map image"
                    extensionFilters.addAll(
                        FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
                        FileChooser.ExtensionFilter("All files", "*.*"),
                    )
                }
                val owner = (event.source as? Button)?.scene?.window
                val file = chooser.showOpenDialog(owner)
                if (file != null) {
                    pathField.text = file.absolutePath
                    settingsService.applyMapLoad(
                        uri = file.toURI().toString(),
                        displayPath = file.absolutePath,
                    )
                }
            }
        }

        val calibrateMapButton = Button("Calibrate Map...").apply {
            tooltip = Tooltip("Adjust map image scale and centre position")
            setOnAction { event ->
                MapCalibrationDialogs.showMapCalibrationDialog(
                    owner = (event.source as? Button)?.scene?.window,
                    settingsService = settingsService,
                    calibrationService = calibrationService,
                )
            }
        }

        val guidedCalibrationButton = Button("Guided Calibration...").apply {
            tooltip = Tooltip("Interactive two-step map calibration")
            setOnAction { event ->
                MapCalibrationDialogs.showGuidedCalibrationDialog(
                    owner = (event.source as? Button)?.scene?.window,
                    settingsService = settingsService,
                    calibrationService = calibrationService,
                )
            }
        }

        val rotateCcwButton = Button("CCW 90").apply {
            tooltip = Tooltip("Rotate map image 90 degrees counter-clockwise")
            setOnAction { settingsService.rotateMapBy(-90) }
        }

        val rotateCwButton = Button("CW 90").apply {
            tooltip = Tooltip("Rotate map image 90 degrees clockwise")
            setOnAction { settingsService.rotateMapBy(90) }
        }

        val visibleCheck = CheckBox("Show Grid").apply {
            isSelected = settingsService.currentGridConfig != null
            tooltip = Tooltip("Toggle grid overlay visibility")
        }

        val gridColorButton = Button("Grid Color").apply {
            prefWidth = 88.0
            tooltip = Tooltip("Grid line colour; applied when you click Apply Grid")
            style = colorButtonStyle(settingsService.gridColor)
        }
        gridColorButton.setOnAction {
            val selected = ColorEditorDialog.showDialog(
                owner = gridColorButton.scene?.window,
                title = "Set Grid Color",
                prompt = "Choose a line color for the overlay grid",
                initialColor = settingsService.gridColor,
            )
            if (selected != null) {
                settingsService.setGridColor(selected)
                gridColorButton.style = colorButtonStyle(settingsService.gridColor)
            }
        }

        val applyGridButton = Button("Apply Grid").apply {
            tooltip = Tooltip("Publish the current grid visibility and colour settings")
            setOnAction {
                settingsService.applyGridConfig(visibleCheck.isSelected)
            }
        }

        val calibrateGridButton = Button("Calibrate Grid...").apply {
            tooltip = Tooltip("Adjust grid cell size and centre position")
            setOnAction { event ->
                MapCalibrationDialogs.showGridCalibrationDialog(
                    owner = (event.source as? Button)?.scene?.window,
                    settingsService = settingsService,
                    calibrationService = calibrationService,
                )
            }
        }

        val revealAllButton = Button("Reveal All").apply {
            tooltip = Tooltip("Remove fog from the entire map")
            setOnAction { fogOfWarService.reset(revealAll = true) }
        }

        val hideAllButton = Button("Hide All").apply {
            tooltip = Tooltip("Cover the entire map with fog")
            setOnAction { fogOfWarService.reset(revealAll = false) }
        }

        val showNamesCheck = CheckBox("Show Names").apply {
            isSelected = settingsService.showTokenNames
            tooltip = Tooltip("Show token names on the table view")
            setOnAction { settingsService.setShowTokenNames(isSelected) }
        }

        val backgroundColorButton = Button("BG Color").apply {
            prefWidth = 88.0
            tooltip = Tooltip("Set the plain-colour background behind the map")
            style = colorButtonStyle(settingsService.backgroundColor)
        }
        backgroundColorButton.setOnAction {
            val selected = ColorEditorDialog.showDialog(
                owner = backgroundColorButton.scene?.window,
                title = "Set Background Color",
                prompt = "Choose a plain-colour background",
                initialColor = settingsService.backgroundColor,
            )
            if (selected != null) {
                val suggestedGridColor = settingsService.applyBackgroundColor(selected)
                backgroundColorButton.style = colorButtonStyle(settingsService.backgroundColor)
                gridColorButton.style = colorButtonStyle(suggestedGridColor)
            }
        }

        val mapRow = HBox(
            4.0,
            loadButton,
            pathField,
            calibrateMapButton,
            guidedCalibrationButton,
            rotateCcwButton,
            rotateCwButton,
            Separator(Orientation.VERTICAL),
            Label("BG:"),
            backgroundColorButton,
        )
        val gridRow = HBox(
            4.0,
            visibleCheck,
            Label("Grid:"),
            gridColorButton,
            applyGridButton,
            calibrateGridButton,
            Separator(Orientation.VERTICAL),
            revealAllButton,
            hideAllButton,
            Separator(Orientation.VERTICAL),
            showNamesCheck,
        )

        return VBox(4.0, mapRow, gridRow)
    }

    private fun buildMeasurementContextMenuActions(
        canvas: Canvas,
        selectedMeasurement: MeasurementOverlay?,
    ): List<MenuAction> {
        val actions = mutableListOf(
            MenuAction(
                id = "map.measure.units",
                label = "Set Units...",
                section = MenuSection.BASIC,
                onAction = {
                    val chosenUnits = MapMeasurementDialogs.showUnitsDialog(
                        owner = canvas.scene?.window,
                        currentUnits = measurementService.measurementUnits,
                        supportedUnits = measurementService.availableUnits,
                    )
                    if (chosenUnits != null) {
                        measurementService.setMeasurementUnits(chosenUnits)
                            .onSuccess(::syncMeasurementUnitsSelection)
                    }
                },
            ),
            MenuAction(
                id = "map.measure.clear-all",
                label = "Clear All Measurements",
                section = MenuSection.DANGER_ZONE,
                isEnabled = measurementService.hasMeasurements(),
                requiresConfirmation = true,
                confirmationMessage = "Remove all measurements?",
                onAction = { measurementService.clearAll() },
            ),
        )

        if (selectedMeasurement != null) {
            actions += MenuAction(
                id = "map.measure.toggle-mirror",
                label = if (selectedMeasurement.mirroredToTable) "Hide from Table" else "Mirror to Table",
                section = MenuSection.APPEARANCE,
                onAction = { measurementService.toggleMirroredToTable(selectedMeasurement.id) },
            )
            actions += MenuAction(
                id = "map.measure.label",
                label = "Set Label...",
                section = MenuSection.APPEARANCE,
                onAction = {
                    val label = MapMeasurementDialogs.showLabelDialog(
                        owner = canvas.scene?.window,
                        currentLabel = selectedMeasurement.unitLabel,
                    )
                    if (label != null) {
                        measurementService.updateLabel(selectedMeasurement.id, label)
                    }
                },
            )
            actions += MenuAction(
                id = "map.measure.remove",
                label = "Remove Measurement",
                section = MenuSection.DANGER_ZONE,
                requiresConfirmation = true,
                confirmationMessage = "Remove selected measurement?",
                onAction = { measurementService.removeMeasurement(selectedMeasurement.id) },
            )
        }

        return actions
    }

    private fun measurementTypeFor(tool: MeasurementTool): MeasurementType =
        when (tool) {
            MeasurementTool.LINE -> MeasurementType.LINE
            MeasurementTool.CONE -> MeasurementType.CONE
            MeasurementTool.RECTANGLE -> MeasurementType.RECTANGLE
            MeasurementTool.CIRCLE -> MeasurementType.CIRCLE
            MeasurementTool.NONE -> error("MeasurementTool.NONE does not map to a measurement type.")
        }

    private fun syncMeasurementUnitsSelection(units: String) {
        val combo = measurementUnitsComboBox ?: return
        if (combo.selectionModel.selectedItem != units) {
            combo.selectionModel.select(units)
        }
    }

    private fun colorButtonStyle(color: Color): String =
        "-fx-background-color: ${ColorHexCodec.colorToHex(color)}; " +
            "-fx-text-fill: ${ColorContrast.textColorHexForBackground(color)}; " +
            "-fx-border-color: -tc-border;"
}
