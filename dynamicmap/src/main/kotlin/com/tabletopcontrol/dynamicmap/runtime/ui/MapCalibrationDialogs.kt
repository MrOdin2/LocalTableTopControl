package com.tabletopcontrol.dynamicmap.runtime.ui

import com.tabletopcontrol.core.ui.dialog.DialogFlows
import com.tabletopcontrol.dynamicmap.runtime.MapInputField
import com.tabletopcontrol.dynamicmap.runtime.MapOperationError
import com.tabletopcontrol.dynamicmap.runtime.MapRenderer
import com.tabletopcontrol.dynamicmap.runtime.MapResult
import com.tabletopcontrol.dynamicmap.runtime.logic.GuidedCalibrationAxis
import com.tabletopcontrol.dynamicmap.runtime.logic.MapCalibrationService
import com.tabletopcontrol.dynamicmap.runtime.logic.MapSettingsService
import com.tabletopcontrol.dynamicmap.runtime.logic.MapViewportState
import com.tabletopcontrol.dynamicmap.runtime.toUserMessage
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.RadioButton
import javafx.scene.control.TextField
import javafx.scene.control.TextInputControl
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Window
import java.util.Locale
import kotlin.math.floor

object MapCalibrationDialogs {
    fun showMapCalibrationDialog(
        owner: Window?,
        settingsService: MapSettingsService,
        calibrationService: MapCalibrationService,
    ) {
        val saved = settingsService.mapCalibration
        val dialog = DialogFlows.createDialog<ButtonType>(
            owner = owner,
            title = "Calibrate Map",
            headerText = "Adjust the map image scale and position.\n" +
                "A red dot marks the canvas centre; align it with a known reference point on the map.\n" +
                "Changes are previewed live; Cancel restores the previous calibration.",
            buttonTypes = listOf(ButtonType.APPLY, ButtonType.CANCEL),
        )

        calibrationService.setMapCalibrationMode(true)

        val scaleField = TextField(MapDialogFormSupport.formatDouble(saved.scale)).apply {
            tooltip = Tooltip("Uniform zoom factor (1.0 = no zoom)")
            prefColumnCount = 8
        }
        val offsetXField = TextField(MapDialogFormSupport.formatDouble(saved.offsetX)).apply {
            tooltip = Tooltip("Horizontal displacement of the image centre from the canvas centre (px)")
            prefColumnCount = 8
        }
        val offsetYField = TextField(MapDialogFormSupport.formatDouble(saved.offsetY)).apply {
            tooltip = Tooltip("Vertical displacement of the image centre from the canvas centre (px)")
            prefColumnCount = 8
        }
        val errorLabel = Label().apply { textFill = Color.RED }
        val tileStep = settingsService.gridCalibration.effectiveCellSizeInPixels()
        val fieldsByInput = mapOf(
            MapInputField.SCALE to scaleField,
            MapInputField.OFFSET_X to offsetXField,
            MapInputField.OFFSET_Y to offsetYField,
        )

        fun tryPreview() {
            when (
                val result = calibrationService.parseMapCalibration(
                    scaleText = scaleField.text,
                    offsetXText = offsetXField.text,
                    offsetYText = offsetYField.text,
                )
            ) {
                is MapResult.Success -> settingsService.previewMapCalibration(result.value)
                is MapResult.Failure -> Unit
            }
        }

        scaleField.textProperty().addListener { _, _, _ -> tryPreview() }
        offsetXField.textProperty().addListener { _, _, _ -> tryPreview() }
        offsetYField.textProperty().addListener { _, _, _ -> tryPreview() }

        dialog.dialogPane.content = VBox(
            8.0,
            Label("Scale:"),
            MapDialogFormSupport.buildStepRow(scaleField, 0.05, ::tryPreview),
            Label("Offset X (px from centre):"),
            MapDialogFormSupport.buildOffsetStepRow(
                field = offsetXField,
                smallStep = 5.0,
                tileStep = tileStep,
                axis = CalibrationOffsetAxis.HORIZONTAL,
                onChanged = ::tryPreview,
            ),
            Label("Offset Y (px from centre):"),
            MapDialogFormSupport.buildOffsetStepRow(
                field = offsetYField,
                smallStep = 5.0,
                tileStep = tileStep,
                axis = CalibrationOffsetAxis.VERTICAL,
                onChanged = ::tryPreview,
            ),
            errorLabel,
        )

        val confirmation = DialogFlows.ConfirmationTracker()
        DialogFlows.installValidatedConfirm(
            dialog = dialog,
            tracker = confirmation,
            confirmButton = ButtonType.APPLY,
        ) {
            MapDialogFormSupport.clearFieldErrors(errorLabel, fieldsByInput.values)
            when (
                val result = calibrationService.parseMapCalibration(
                    scaleText = scaleField.text,
                    offsetXText = offsetXField.text,
                    offsetYText = offsetYField.text,
                )
            ) {
                is MapResult.Success -> {
                    settingsService.commitMapCalibration(result.value)
                    true
                }
                is MapResult.Failure -> {
                    MapDialogFormSupport.showOperationError(result.error, errorLabel, fieldsByInput)
                    false
                }
            }
        }

        DialogFlows.onHiddenWithCancelRestore(
            dialog = dialog,
            tracker = confirmation,
            onCancel = { settingsService.restoreMapCalibration(saved) },
            onAlways = { calibrationService.setMapCalibrationMode(false) },
        )

        dialog.showAndWait()
    }

    fun showGridCalibrationDialog(
        owner: Window?,
        settingsService: MapSettingsService,
        calibrationService: MapCalibrationService,
    ) {
        val saved = settingsService.gridCalibration
        val dialog = DialogFlows.createDialog<ButtonType>(
            owner = owner,
            title = "Calibrate Grid",
            headerText = "Adjust the grid cell size and position.\n" +
                "A yellow crosshair marks the canvas centre; this is the origin for all scale operations.\n" +
                "Changes are previewed live; Cancel restores the previous calibration.",
            buttonTypes = listOf(ButtonType.APPLY, ButtonType.CANCEL),
        )

        calibrationService.setGridCalibrationMode(true)

        val cellSizeField = TextField(MapDialogFormSupport.formatDouble(saved.cellSizeInPixels)).apply {
            tooltip = Tooltip("Grid cell size in canvas pixels at scale 1.0")
            prefColumnCount = 8
        }
        val scaleField = TextField(MapDialogFormSupport.formatDouble(saved.scale)).apply {
            tooltip = Tooltip("Zoom factor applied from the canvas centre (1.0 = no zoom)")
            prefColumnCount = 8
        }
        val offsetXField = TextField(MapDialogFormSupport.formatDouble(saved.offsetX)).apply {
            tooltip = Tooltip("Horizontal displacement of the grid origin from the canvas centre (px)")
            prefColumnCount = 8
        }
        val offsetYField = TextField(MapDialogFormSupport.formatDouble(saved.offsetY)).apply {
            tooltip = Tooltip("Vertical displacement of the grid origin from the canvas centre (px)")
            prefColumnCount = 8
        }
        val errorLabel = Label().apply { textFill = Color.RED }
        val fieldsByInput = mapOf(
            MapInputField.CELL_SIZE to cellSizeField,
            MapInputField.SCALE to scaleField,
            MapInputField.OFFSET_X to offsetXField,
            MapInputField.OFFSET_Y to offsetYField,
        )

        fun tryPreview() {
            when (
                val result = calibrationService.parseGridCalibration(
                    cellSizeText = cellSizeField.text,
                    scaleText = scaleField.text,
                    offsetXText = offsetXField.text,
                    offsetYText = offsetYField.text,
                )
            ) {
                is MapResult.Success -> settingsService.previewGridCalibration(result.value)
                is MapResult.Failure -> Unit
            }
        }

        cellSizeField.textProperty().addListener { _, _, _ -> tryPreview() }
        scaleField.textProperty().addListener { _, _, _ -> tryPreview() }
        offsetXField.textProperty().addListener { _, _, _ -> tryPreview() }
        offsetYField.textProperty().addListener { _, _, _ -> tryPreview() }

        dialog.dialogPane.content = VBox(
            8.0,
            Label("Cell size (px):"),
            MapDialogFormSupport.buildStepRow(cellSizeField, 1.0, ::tryPreview),
            Label("Scale:"),
            MapDialogFormSupport.buildStepRow(scaleField, 0.05, ::tryPreview),
            Label("Offset X (px from centre):"),
            MapDialogFormSupport.buildStepRow(offsetXField, 1.0, ::tryPreview),
            Label("Offset Y (px from centre):"),
            MapDialogFormSupport.buildStepRow(offsetYField, 1.0, ::tryPreview),
            errorLabel,
        )

        val confirmation = DialogFlows.ConfirmationTracker()
        DialogFlows.installValidatedConfirm(
            dialog = dialog,
            tracker = confirmation,
            confirmButton = ButtonType.APPLY,
        ) {
            MapDialogFormSupport.clearFieldErrors(errorLabel, fieldsByInput.values)
            when (
                val result = calibrationService.parseGridCalibration(
                    cellSizeText = cellSizeField.text,
                    scaleText = scaleField.text,
                    offsetXText = offsetXField.text,
                    offsetYText = offsetYField.text,
                )
            ) {
                is MapResult.Success -> {
                    settingsService.commitGridCalibration(result.value)
                    true
                }
                is MapResult.Failure -> {
                    MapDialogFormSupport.showOperationError(result.error, errorLabel, fieldsByInput)
                    false
                }
            }
        }

        DialogFlows.onHiddenWithCancelRestore(
            dialog = dialog,
            tracker = confirmation,
            onCancel = { settingsService.restoreGridCalibration(saved) },
            onAlways = { calibrationService.setGridCalibrationMode(false) },
        )

        dialog.showAndWait()
    }

    fun showGuidedCalibrationDialog(
        owner: Window?,
        settingsService: MapSettingsService,
        calibrationService: MapCalibrationService,
    ) {
        val saved = settingsService.mapCalibration
        var working = saved
        var step = GuidedCalibrationStep.SELECT_CENTER
        var step2Axis = GuidedCalibrationAxis.HORIZONTAL
        var centerCursorX = 0.0
        var centerCursorY = 0.0
        var centerCursorInitialized = false
        var step2OffsetPx = 0.0
        val viewport = MapViewportState(
            zoomFactor = 1.5,
            maxScale = 256.0,
            panStep = 40.0,
        )
        val confirmation = DialogFlows.ConfirmationTracker()

        val dialog = DialogFlows.createDialog<ButtonType>(
            owner = owner,
            title = "Guided Map Calibration",
            headerText = null,
            buttonTypes = listOf(ButtonType.APPLY, ButtonType.CANCEL),
        )

        val stepLabel = Label()
        val instructionLabel = Label().apply {
            isWrapText = true
            prefWidth = 580.0
        }
        val statusLabel = Label().apply {
            isWrapText = true
            prefWidth = 680.0
        }
        val errorLabel = Label().apply { textFill = Color.RED }

        val canvas = Canvas()
        val overlayCanvas = Canvas().apply {
            isMouseTransparent = true
        }
        val overlayGc = overlayCanvas.graphicsContext2D
        val renderer = MapRenderer(canvas).apply {
            applyTableMapOffset = false
            mapCalibration = working
            gridCalibration = settingsService.gridCalibration
            gridConfig = settingsService.currentGridConfig
            backgroundColor = settingsService.backgroundColor
            settingsService.currentMapImageUri?.let { loadImage(it) }
        }
        canvas.sceneProperty().addListener { _, _, newScene ->
            if (newScene == null) renderer.dispose()
        }

        dialog.dialogPane.prefWidth = 760.0

        val applyButton = dialog.dialogPane.lookupButton(ButtonType.APPLY)
        applyButton.isDisable = true

        val backButton = Button("\u2190 Back to Step 1").apply {
            tooltip = Tooltip("Discard Step 2 and restart from the saved calibration.")
            isDisable = true
        }
        val usePointButton = Button().apply {
            tooltip = Tooltip("Preview the calibration using the current point.")
        }
        val skipButton = Button().apply {
            tooltip = Tooltip("Skip this step and keep the current preview.")
        }

        val axisToggleGroup = ToggleGroup()
        val horizontalAxisButton = RadioButton("Horizontal").apply {
            toggleGroup = axisToggleGroup
            isSelected = true
            tooltip = Tooltip("Use a point on the next horizontal tile boundary.")
        }
        val verticalAxisButton = RadioButton("Vertical").apply {
            toggleGroup = axisToggleGroup
            tooltip = Tooltip("Use a point on the next vertical tile boundary.")
        }

        val wideModeCheck = CheckBox("Wide mode").apply {
            tooltip = Tooltip("Measure to the nth tile boundary instead of the very next one.")
        }
        val wideModeTilesField = TextField("3").apply {
            prefColumnCount = 4
            isDisable = true
            tooltip = Tooltip("How many tile boundaries away Step 2 should target.")
        }

        val zoomOutButton = Button("-").apply {
            tooltip = Tooltip("Zoom out")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
        }
        val zoomInButton = Button("+").apply {
            tooltip = Tooltip("Zoom in")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
        }
        val resetViewButton = Button("Reset View").apply {
            tooltip = Tooltip("Reset zoom and pan to default")
        }
        val panLeftButton = Button("\u25c0").apply {
            tooltip = Tooltip("Pan view left")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
        }
        val panUpButton = Button("\u25b2").apply {
            tooltip = Tooltip("Pan view up")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
        }
        val panDownButton = Button("\u25bc").apply {
            tooltip = Tooltip("Pan view down")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
        }
        val panRightButton = Button("\u25b6").apply {
            tooltip = Tooltip("Pan view right")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
        }

        val pointLeftButton = Button("\u25c0").apply {
            tooltip = Tooltip("Move the active point left by one calibration-screen pixel")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
        }
        val pointUpButton = Button("\u25b2").apply {
            tooltip = Tooltip("Move the active point up by one calibration-screen pixel")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
        }
        val pointDownButton = Button("\u25bc").apply {
            tooltip = Tooltip("Move the active point down by one calibration-screen pixel")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
        }
        val pointRightButton = Button("\u25b6").apply {
            tooltip = Tooltip("Move the active point right by one calibration-screen pixel")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
        }
        val resetPointButton = Button("Reset Point").apply {
            tooltip = Tooltip("Reset the active point to its default screen position")
        }

        val guidedFieldsByInput = mapOf(MapInputField.GUIDED_TILE_SPAN to wideModeTilesField)

        fun currentTileSpanOrNull(): Int? =
            if (!wideModeCheck.isSelected) {
                1
            } else {
                wideModeTilesField.text.trim().toIntOrNull()?.takeIf { it > 0 }
            }

        fun resolveTileSpan(): Int? {
            MapDialogFormSupport.clearFieldErrors(errorLabel, guidedFieldsByInput.values)
            if (!wideModeCheck.isSelected) return 1
            return when (val result = calibrationService.parseGuidedTileSpan(wideModeTilesField.text)) {
                is MapResult.Success -> result.value
                is MapResult.Failure -> {
                    MapDialogFormSupport.showOperationError(result.error, errorLabel, guidedFieldsByInput)
                    null
                }
            }
        }

        fun baseCanvasPoint(): Pair<Double, Double> =
            viewport.worldToCanvas(
                canvasWidth = canvas.width,
                canvasHeight = canvas.height,
                worldX = canvas.width / 2.0,
                worldY = canvas.height / 2.0,
            )

        fun defaultStep2OffsetPx(): Double {
            val tileSpan = currentTileSpanOrNull() ?: 1
            val screenDistance = settingsService.gridCalibration.effectiveCellSizeInPixels() * tileSpan * viewport.scale
            return if (screenDistance.isFinite()) {
                screenDistance.coerceAtLeast(1.0)
            } else {
                tileSpan.toDouble()
            }
        }

        fun clampCenterCursorToCanvas() {
            if (canvas.width <= 0.0 || canvas.height <= 0.0) return
            centerCursorX = centerCursorX.coerceIn(0.0, canvas.width)
            centerCursorY = centerCursorY.coerceIn(0.0, canvas.height)
        }

        fun clampStep2Offset() {
            if (canvas.width <= 0.0 || canvas.height <= 0.0) return
            val (baseX, baseY) = baseCanvasPoint()
            step2OffsetPx = when (step2Axis) {
                GuidedCalibrationAxis.HORIZONTAL ->
                    (baseX + step2OffsetPx).coerceIn(0.0, canvas.width) - baseX
                GuidedCalibrationAxis.VERTICAL ->
                    (baseY + step2OffsetPx).coerceIn(0.0, canvas.height) - baseY
            }
        }

        fun selectedCanvasPoint(): Pair<Double, Double> =
            when (step) {
                GuidedCalibrationStep.SELECT_CENTER -> Pair(centerCursorX, centerCursorY)
                GuidedCalibrationStep.SELECT_SECOND_POINT -> {
                    val (baseX, baseY) = baseCanvasPoint()
                    when (step2Axis) {
                        GuidedCalibrationAxis.HORIZONTAL -> Pair(baseX + step2OffsetPx, baseY)
                        GuidedCalibrationAxis.VERTICAL -> Pair(baseX, baseY + step2OffsetPx)
                    }
                }
            }

        fun selectedWorldPoint(): Pair<Double, Double> {
            val (canvasX, canvasY) = selectedCanvasPoint()
            return viewport.canvasToWorld(
                canvasWidth = canvas.width,
                canvasHeight = canvas.height,
                canvasX = canvasX,
                canvasY = canvasY,
            )
        }

        fun updatePointControlAvailability() {
            val axisLocked = step == GuidedCalibrationStep.SELECT_SECOND_POINT
            pointLeftButton.isDisable = axisLocked && step2Axis == GuidedCalibrationAxis.VERTICAL
            pointRightButton.isDisable = axisLocked && step2Axis == GuidedCalibrationAxis.VERTICAL
            pointUpButton.isDisable = axisLocked && step2Axis == GuidedCalibrationAxis.HORIZONTAL
            pointDownButton.isDisable = axisLocked && step2Axis == GuidedCalibrationAxis.HORIZONTAL
        }

        fun updateStatusText() {
            if (canvas.width <= 0.0 || canvas.height <= 0.0) {
                statusLabel.text = "Resize the dialog to begin calibration."
                return
            }
            val zoomText = "Zoom ${MapDialogFormSupport.formatDouble(viewport.scale)}x."
            statusLabel.text = when (step) {
                GuidedCalibrationStep.SELECT_CENTER -> {
                    val (canvasX, canvasY) = selectedCanvasPoint()
                    "$zoomText Cursor: x=${MapDialogFormSupport.formatDouble(canvasX)}, " +
                        "y=${MapDialogFormSupport.formatDouble(canvasY)} screen px. " +
                        "Drag with the mouse for coarse positioning, then use the arrows for exact pixel nudges."
                }
                GuidedCalibrationStep.SELECT_SECOND_POINT -> {
                    val tileSpan = currentTileSpanOrNull()
                    val offsetText = MapDialogFormSupport.formatDouble(step2OffsetPx)
                    if (tileSpan == null) {
                        "$zoomText Step 2 is locked to the ${step2Axis.label.lowercase()} axis. " +
                            "Enter a positive whole number for Wide mode."
                    } else {
                        "$zoomText Step 2 is locked to the ${step2Axis.label.lowercase()} axis. " +
                            "Cursor offset: $offsetText screen px from the centre marker. " +
                            "Tile span: $tileSpan."
                    }
                }
            }
        }

        fun drawCursorMarker(x: Double, y: Double) {
            overlayGc.save()
            overlayGc.stroke = Color.BLACK
            overlayGc.lineWidth = 4.0
            overlayGc.strokeOval(x - 8.0, y - 8.0, 16.0, 16.0)
            overlayGc.strokeLine(x - 12.0, y, x + 12.0, y)
            overlayGc.strokeLine(x, y - 12.0, x, y + 12.0)

            overlayGc.stroke = Color.WHITE
            overlayGc.lineWidth = 2.5
            overlayGc.strokeOval(x - 8.0, y - 8.0, 16.0, 16.0)

            overlayGc.stroke = Color.DEEPSKYBLUE
            overlayGc.lineWidth = 1.5
            overlayGc.strokeOval(x - 4.0, y - 4.0, 8.0, 8.0)
            overlayGc.strokeLine(x - 10.0, y, x + 10.0, y)
            overlayGc.strokeLine(x, y - 10.0, x, y + 10.0)
            overlayGc.restore()
        }

        fun drawCenterMarker(x: Double, y: Double) {
            overlayGc.save()
            overlayGc.stroke = Color.BLACK
            overlayGc.lineWidth = 4.0
            overlayGc.strokeOval(x - 7.0, y - 7.0, 14.0, 14.0)

            overlayGc.stroke = Color.WHITE
            overlayGc.lineWidth = 2.5
            overlayGc.strokeOval(x - 7.0, y - 7.0, 14.0, 14.0)

            overlayGc.stroke = Color.RED
            overlayGc.lineWidth = 1.5
            overlayGc.strokeOval(x - 5.0, y - 5.0, 10.0, 10.0)
            overlayGc.strokeLine(x - 8.0, y, x + 8.0, y)
            overlayGc.strokeLine(x, y - 8.0, x, y + 8.0)
            overlayGc.restore()
        }

        fun redrawOverlay() {
            overlayGc.clearRect(0.0, 0.0, overlayCanvas.width, overlayCanvas.height)
            if (overlayCanvas.width <= 0.0 || overlayCanvas.height <= 0.0) return

            when (step) {
                GuidedCalibrationStep.SELECT_CENTER -> {
                    val (cursorX, cursorY) = selectedCanvasPoint()
                    drawCursorMarker(cursorX, cursorY)
                }
                GuidedCalibrationStep.SELECT_SECOND_POINT -> {
                    val (baseX, baseY) = baseCanvasPoint()
                    overlayGc.save()
                    overlayGc.stroke = Color.color(1.0, 1.0, 1.0, 0.65)
                    overlayGc.lineWidth = 1.25
                    overlayGc.setLineDashes(6.0, 4.0)
                    when (step2Axis) {
                        GuidedCalibrationAxis.HORIZONTAL ->
                            overlayGc.strokeLine(0.0, baseY, overlayCanvas.width, baseY)
                        GuidedCalibrationAxis.VERTICAL ->
                            overlayGc.strokeLine(baseX, 0.0, baseX, overlayCanvas.height)
                    }
                    overlayGc.restore()

                    drawCenterMarker(baseX, baseY)
                    val (cursorX, cursorY) = selectedCanvasPoint()
                    drawCursorMarker(cursorX, cursorY)
                }
            }
        }

        fun refreshOverlayAndStatus() {
            when (step) {
                GuidedCalibrationStep.SELECT_CENTER -> clampCenterCursorToCanvas()
                GuidedCalibrationStep.SELECT_SECOND_POINT -> clampStep2Offset()
            }
            updatePointControlAvailability()
            redrawOverlay()
            updateStatusText()
        }

        fun resetCenterCursor() {
            if (canvas.width <= 0.0 || canvas.height <= 0.0) return
            centerCursorX = canvas.width / 2.0
            centerCursorY = canvas.height / 2.0
            centerCursorInitialized = true
        }

        fun resetStep2Cursor() {
            step2OffsetPx = defaultStep2OffsetPx()
            clampStep2Offset()
        }

        fun nudgeSelection(screenDx: Double = 0.0, screenDy: Double = 0.0) {
            when (step) {
                GuidedCalibrationStep.SELECT_CENTER -> {
                    centerCursorInitialized = true
                    centerCursorX += screenDx
                    centerCursorY += screenDy
                }
                GuidedCalibrationStep.SELECT_SECOND_POINT -> {
                    step2OffsetPx += when (step2Axis) {
                        GuidedCalibrationAxis.HORIZONTAL -> screenDx
                        GuidedCalibrationAxis.VERTICAL -> screenDy
                    }
                    applyButton.isDisable = true
                }
            }
            refreshOverlayAndStatus()
        }

        fun applyStepText() {
            when (step) {
                GuidedCalibrationStep.SELECT_CENTER -> {
                    stepLabel.text = "Step 1 of 2: Choose the centre point"
                    instructionLabel.text =
                        "Drag the map with the mouse for coarse positioning.\n" +
                            "Use the on-screen arrows or the keyboard arrow keys to move the blue cursor by one calibration-screen pixel at a time.\n" +
                            "This point becomes the centre for translation, rotation, and scaling."
                    usePointButton.text = "Use This Centre Point \u2192"
                    skipButton.text = "Skip Step 1 \u2192"
                    backButton.isDisable = true
                }
                GuidedCalibrationStep.SELECT_SECOND_POINT -> {
                    stepLabel.text = "Step 2 of 2: Choose the next tile boundary"
                    instructionLabel.text =
                        "The red marker shows the centre point from Step 1.\n" +
                            "Choose a second point only on the horizontal or vertical axis, never diagonally.\n" +
                            "Use mouse panning for coarse positioning and arrow-key nudging for one-screen-pixel precision."
                    usePointButton.text = "Preview Step 2 \u2192"
                    skipButton.text = "Skip Step 2 \u2192"
                    backButton.isDisable = false
                }
            }
            wideModeTilesField.isDisable = !wideModeCheck.isSelected
            refreshOverlayAndStatus()
        }

        fun advanceToStep2() {
            step = GuidedCalibrationStep.SELECT_SECOND_POINT
            applyButton.isDisable = true
            MapDialogFormSupport.clearFieldErrors(errorLabel, guidedFieldsByInput.values)
            step2Axis = if (verticalAxisButton.isSelected) GuidedCalibrationAxis.VERTICAL else GuidedCalibrationAxis.HORIZONTAL
            resetStep2Cursor()
            applyStepText()
        }

        val canvasPane = object : Pane() {
            init {
                children.addAll(canvas, overlayCanvas)
                minWidth = 420.0
                minHeight = 320.0
                prefWidth = 680.0
                prefHeight = 480.0
                isFocusTraversable = true
            }

            override fun layoutChildren() {
                if (canvas.width != width || canvas.height != height) {
                    canvas.width = width
                    canvas.height = height
                    overlayCanvas.width = width
                    overlayCanvas.height = height
                    renderer.redraw()
                    if (!centerCursorInitialized) {
                        resetCenterCursor()
                    }
                    if (step == GuidedCalibrationStep.SELECT_SECOND_POINT) {
                        clampStep2Offset()
                    }
                }
                refreshOverlayAndStatus()
            }
        }

        val navRow = HBox().also { row ->
            val spacer = Region()
            HBox.setHgrow(spacer, Priority.ALWAYS)
            row.children.addAll(backButton, spacer, usePointButton, skipButton)
        }

        val step2OptionsRow = HBox().also { row ->
            val spacer = Region()
            HBox.setHgrow(spacer, Priority.ALWAYS)
            row.spacing = 8.0
            row.children.addAll(
                Label("Axis:"),
                horizontalAxisButton,
                verticalAxisButton,
                spacer,
                wideModeCheck,
                Label("Tiles away:"),
                wideModeTilesField,
            )
        }

        val viewControlsRow = HBox(
            4.0,
            Label("View:"),
            zoomOutButton,
            zoomInButton,
            resetViewButton,
            panLeftButton,
            panUpButton,
            panDownButton,
            panRightButton,
        )
        val pointControlsRow = HBox(
            4.0,
            Label("Point:"),
            pointLeftButton,
            pointUpButton,
            pointDownButton,
            pointRightButton,
            resetPointButton,
            Label("All arrows move by exactly 1 calibration-screen pixel"),
        )

        fun syncStep2OptionsVisibility() {
            val visible = step == GuidedCalibrationStep.SELECT_SECOND_POINT
            step2OptionsRow.isVisible = visible
            step2OptionsRow.isManaged = visible
        }

        var dragStartX = 0.0
        var dragStartY = 0.0
        var dragStartOffsetX = 0.0
        var dragStartOffsetY = 0.0

        canvas.setOnMousePressed { event ->
            canvasPane.requestFocus()
            if (event.button == MouseButton.PRIMARY) {
                dragStartX = event.x
                dragStartY = event.y
                dragStartOffsetX = viewport.offsetX
                dragStartOffsetY = viewport.offsetY
            }
        }

        canvas.setOnMouseDragged { event ->
            if (!event.isPrimaryButtonDown) return@setOnMouseDragged
            viewport.setPan(
                renderer,
                dragStartOffsetX + (event.x - dragStartX),
                dragStartOffsetY + (event.y - dragStartY),
            )
            refreshOverlayAndStatus()
        }

        canvas.setOnScroll { event ->
            viewport.zoomFromScroll(renderer, event.deltaY)
            refreshOverlayAndStatus()
        }

        usePointButton.setOnAction {
            when (step) {
                GuidedCalibrationStep.SELECT_CENTER -> {
                    val (worldX, worldY) = selectedWorldPoint()
                    working = calibrationService.guidedStep1(
                        current = working,
                        worldX = worldX,
                        worldY = worldY,
                        canvasCenterX = canvas.width / 2.0,
                        canvasCenterY = canvas.height / 2.0,
                    )
                    MapDialogFormSupport.clearFieldErrors(errorLabel, guidedFieldsByInput.values)
                    settingsService.previewMapCalibration(working)
                    advanceToStep2()
                    syncStep2OptionsVisibility()
                }
                GuidedCalibrationStep.SELECT_SECOND_POINT -> {
                    val tileSpan = resolveTileSpan() ?: run {
                        applyButton.isDisable = true
                        return@setOnAction
                    }
                    val (worldX, worldY) = selectedWorldPoint()
                    when (
                        val result = calibrationService.guidedStep2(
                            currentCalibration = working,
                            worldX = worldX,
                            worldY = worldY,
                            canvasCenterX = canvas.width / 2.0,
                            canvasCenterY = canvas.height / 2.0,
                            gridCellPixels = settingsService.gridCalibration.effectiveCellSizeInPixels(),
                            axis = step2Axis,
                            targetTileSpan = tileSpan,
                        )
                    ) {
                        is MapResult.Success -> {
                            working = result.value
                            MapDialogFormSupport.clearFieldErrors(errorLabel, guidedFieldsByInput.values)
                            settingsService.previewMapCalibration(working)
                            applyButton.isDisable = false
                            refreshOverlayAndStatus()
                        }
                        is MapResult.Failure -> {
                            MapDialogFormSupport.showOperationError(result.error, errorLabel, guidedFieldsByInput)
                            applyButton.isDisable = true
                        }
                    }
                }
            }
        }

        skipButton.setOnAction {
            MapDialogFormSupport.clearFieldErrors(errorLabel, guidedFieldsByInput.values)
            when (step) {
                GuidedCalibrationStep.SELECT_CENTER -> {
                    advanceToStep2()
                    syncStep2OptionsVisibility()
                }
                GuidedCalibrationStep.SELECT_SECOND_POINT -> applyButton.isDisable = false
            }
        }

        backButton.setOnAction {
            working = saved
            step = GuidedCalibrationStep.SELECT_CENTER
            applyButton.isDisable = true
            MapDialogFormSupport.clearFieldErrors(errorLabel, guidedFieldsByInput.values)
            settingsService.previewMapCalibration(working)
            if (centerCursorInitialized) {
                resetCenterCursor()
            }
            applyStepText()
            syncStep2OptionsVisibility()
        }

        zoomOutButton.setOnAction {
            viewport.zoomOut(renderer)
            refreshOverlayAndStatus()
        }
        zoomInButton.setOnAction {
            viewport.zoomIn(renderer)
            refreshOverlayAndStatus()
        }
        resetViewButton.setOnAction {
            viewport.reset(renderer)
            refreshOverlayAndStatus()
        }
        panLeftButton.setOnAction {
            viewport.panLeft(renderer)
            refreshOverlayAndStatus()
        }
        panUpButton.setOnAction {
            viewport.panUp(renderer)
            refreshOverlayAndStatus()
        }
        panDownButton.setOnAction {
            viewport.panDown(renderer)
            refreshOverlayAndStatus()
        }
        panRightButton.setOnAction {
            viewport.panRight(renderer)
            refreshOverlayAndStatus()
        }

        pointLeftButton.setOnAction { nudgeSelection(screenDx = -1.0) }
        pointRightButton.setOnAction { nudgeSelection(screenDx = 1.0) }
        pointUpButton.setOnAction { nudgeSelection(screenDy = -1.0) }
        pointDownButton.setOnAction { nudgeSelection(screenDy = 1.0) }
        resetPointButton.setOnAction {
            when (step) {
                GuidedCalibrationStep.SELECT_CENTER -> resetCenterCursor()
                GuidedCalibrationStep.SELECT_SECOND_POINT -> {
                    resetStep2Cursor()
                    applyButton.isDisable = true
                }
            }
            refreshOverlayAndStatus()
        }

        horizontalAxisButton.setOnAction {
            step2Axis = GuidedCalibrationAxis.HORIZONTAL
            if (step == GuidedCalibrationStep.SELECT_SECOND_POINT) {
                resetStep2Cursor()
                applyButton.isDisable = true
            }
            refreshOverlayAndStatus()
        }
        verticalAxisButton.setOnAction {
            step2Axis = GuidedCalibrationAxis.VERTICAL
            if (step == GuidedCalibrationStep.SELECT_SECOND_POINT) {
                resetStep2Cursor()
                applyButton.isDisable = true
            }
            refreshOverlayAndStatus()
        }

        wideModeCheck.selectedProperty().addListener { _, _, isSelected ->
            wideModeTilesField.isDisable = !isSelected
            MapDialogFormSupport.clearFieldErrors(errorLabel, guidedFieldsByInput.values)
            if (step == GuidedCalibrationStep.SELECT_SECOND_POINT) {
                resetStep2Cursor()
                applyButton.isDisable = true
            }
            refreshOverlayAndStatus()
        }
        wideModeTilesField.textProperty().addListener { _, _, _ ->
            wideModeTilesField.style = ""
            if (step == GuidedCalibrationStep.SELECT_SECOND_POINT) {
                errorLabel.text = ""
                if (wideModeCheck.isSelected && currentTileSpanOrNull() != null) {
                    resetStep2Cursor()
                }
                applyButton.isDisable = true
            }
            refreshOverlayAndStatus()
        }

        dialog.dialogPane.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            if (dialog.dialogPane.scene?.focusOwner is TextInputControl) return@addEventFilter
            when (event.code) {
                KeyCode.LEFT -> {
                    nudgeSelection(screenDx = -1.0)
                    event.consume()
                }
                KeyCode.RIGHT -> {
                    nudgeSelection(screenDx = 1.0)
                    event.consume()
                }
                KeyCode.UP -> {
                    nudgeSelection(screenDy = -1.0)
                    event.consume()
                }
                KeyCode.DOWN -> {
                    nudgeSelection(screenDy = 1.0)
                    event.consume()
                }
                else -> Unit
            }
        }

        applyStepText()
        syncStep2OptionsVisibility()
        dialog.dialogPane.content = VBox(
            10.0,
            stepLabel,
            instructionLabel,
            statusLabel,
            errorLabel,
            navRow,
            step2OptionsRow,
            canvasPane,
            pointControlsRow,
            viewControlsRow,
        )

        dialog.setOnShown {
            canvasPane.requestFocus()
            if (!centerCursorInitialized) {
                resetCenterCursor()
            }
            refreshOverlayAndStatus()
        }

        DialogFlows.installValidatedConfirm(
            dialog = dialog,
            tracker = confirmation,
            confirmButton = ButtonType.APPLY,
        ) {
            settingsService.commitMapCalibration(working)
            true
        }

        DialogFlows.onHiddenWithCancelRestore(
            dialog = dialog,
            tracker = confirmation,
            onCancel = { settingsService.restoreMapCalibration(saved) },
            onAlways = {},
        )

        dialog.showAndWait()
    }
}

private enum class GuidedCalibrationStep {
    SELECT_CENTER,
    SELECT_SECOND_POINT,
}

private enum class CalibrationOffsetAxis {
    HORIZONTAL,
    VERTICAL,
}

private val GuidedCalibrationAxis.label: String
    get() =
        when (this) {
            GuidedCalibrationAxis.HORIZONTAL -> "Horizontal"
            GuidedCalibrationAxis.VERTICAL -> "Vertical"
        }

private object MapDialogFormSupport {
    fun buildStepRow(field: TextField, step: Double, onChanged: () -> Unit): HBox {
        fun adjust(delta: Double) {
            val current = field.text.toDoubleOrNull() ?: 0.0
            field.text = formatDouble(current + delta)
            onChanged()
        }

        val decrementButton = Button("-").apply {
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            tooltip = Tooltip("Decrease by $step")
            setOnAction { adjust(-step) }
        }
        val incrementButton = Button("+").apply {
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            tooltip = Tooltip("Increase by $step")
            setOnAction { adjust(step) }
        }

        HBox.setHgrow(field, Priority.ALWAYS)
        return HBox(4.0, decrementButton, field, incrementButton)
    }

    fun buildOffsetStepRow(
        field: TextField,
        smallStep: Double,
        tileStep: Double,
        axis: CalibrationOffsetAxis,
        onChanged: () -> Unit,
    ): HBox {
        fun adjust(delta: Double) {
            val current = field.text.toDoubleOrNull() ?: 0.0
            field.text = formatDouble(current + delta)
            onChanged()
        }

        val safeTileStep = tileStep.takeIf { it.isFinite() && it > 0.0 } ?: smallStep
        val backwardLabel = if (axis == CalibrationOffsetAxis.HORIZONTAL) "\u2190" else "\u2191"
        val forwardLabel = if (axis == CalibrationOffsetAxis.HORIZONTAL) "\u2192" else "\u2193"
        val backwardDirection = if (axis == CalibrationOffsetAxis.HORIZONTAL) "left" else "up"
        val forwardDirection = if (axis == CalibrationOffsetAxis.HORIZONTAL) "right" else "down"

        val tileBackwardButton = Button(backwardLabel).apply {
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            tooltip = Tooltip("Move image $backwardDirection by one tile (${formatDouble(safeTileStep)} px)")
            setOnAction { adjust(-safeTileStep) }
        }
        val decrementButton = Button("-").apply {
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            tooltip = Tooltip("Decrease by $smallStep px")
            setOnAction { adjust(-smallStep) }
        }
        val incrementButton = Button("+").apply {
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            tooltip = Tooltip("Increase by $smallStep px")
            setOnAction { adjust(smallStep) }
        }
        val tileForwardButton = Button(forwardLabel).apply {
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            tooltip = Tooltip("Move image $forwardDirection by one tile (${formatDouble(safeTileStep)} px)")
            setOnAction { adjust(safeTileStep) }
        }

        HBox.setHgrow(field, Priority.ALWAYS)
        return HBox(4.0, tileBackwardButton, decrementButton, field, incrementButton, tileForwardButton)
    }

    fun formatDouble(value: Double): String =
        if (value == floor(value) && !value.isInfinite()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.4g", value)
        }

    fun clearFieldErrors(errorLabel: Label, fields: Collection<TextField>) {
        fields.forEach { it.style = "" }
        errorLabel.text = ""
    }

    fun showOperationError(
        error: MapOperationError,
        errorLabel: Label,
        fieldsByInput: Map<MapInputField, TextField>,
    ) {
        clearFieldErrors(errorLabel, fieldsByInput.values)
        when (error) {
            is MapOperationError.Validation -> {
                fieldsByInput[error.field]?.style = "-fx-border-color: red;"
                errorLabel.text = error.toUserMessage()
            }
            else -> errorLabel.text = error.toUserMessage()
        }
    }
}
