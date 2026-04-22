package com.tabletopcontrol.dynamicmap

import com.tabletopcontrol.core.ThemeManager
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.core.ui.dialog.DialogFlows
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

object DynamicMapCalibrationDialogs {
    fun showBackgroundCalibrationDialog(
        owner: Window?,
        controller: DynamicMapBuilderController,
        previewCellSize: Double,
    ) {
        val document = controller.currentDocument()
        if (document.backgroundImageUri.isNullOrBlank()) return

        val safeCellSize = previewCellSize.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val saved = document.backgroundCalibration
        val dialog = DialogFlows.createDialog<ButtonType>(
            owner = owner,
            title = "Calibrate Background Texture",
            headerText = "Adjust the texture scale and centre position.\n" +
                "Changes are previewed live in the builder pane and stay aligned to the grid when the pane is resized.",
            buttonTypes = listOf(ButtonType.APPLY, ButtonType.CANCEL),
        )

        val scaleField = TextField(DynamicMapCalibrationFormSupport.formatDouble(saved.scale * safeCellSize)).apply {
            tooltip = Tooltip("Rendered image scale in preview pixels per image pixel")
            prefColumnCount = 8
        }
        val offsetXField = TextField(DynamicMapCalibrationFormSupport.formatDouble(saved.offsetX * safeCellSize)).apply {
            tooltip = Tooltip("Horizontal displacement from the map centre in preview pixels")
            prefColumnCount = 8
        }
        val offsetYField = TextField(DynamicMapCalibrationFormSupport.formatDouble(saved.offsetY * safeCellSize)).apply {
            tooltip = Tooltip("Vertical displacement from the map centre in preview pixels")
            prefColumnCount = 8
        }
        val errorLabel = Label().apply { textFill = Color.RED }
        val fieldsByInput = mapOf(
            DynamicMapCalibrationInput.SCALE to scaleField,
            DynamicMapCalibrationInput.OFFSET_X to offsetXField,
            DynamicMapCalibrationInput.OFFSET_Y to offsetYField,
        )

        fun tryPreview() {
            when (
                val result = DynamicMapCalibrationFormSupport.parseBackgroundCalibration(
                    scaleText = scaleField.text,
                    offsetXText = offsetXField.text,
                    offsetYText = offsetYField.text,
                    previewCellSize = safeCellSize,
                )
            ) {
                is DynamicMapCalibrationParseResult.Success -> controller.setBackgroundCalibration(result.value)
                is DynamicMapCalibrationParseResult.Failure -> Unit
            }
        }

        scaleField.textProperty().addListener { _, _, _ -> tryPreview() }
        offsetXField.textProperty().addListener { _, _, _ -> tryPreview() }
        offsetYField.textProperty().addListener { _, _, _ -> tryPreview() }

        dialog.dialogPane.content = VBox(
            8.0,
            Label("Scale:"),
            DynamicMapCalibrationFormSupport.buildStepRow(scaleField, 0.05, ::tryPreview),
            Label("Offset X (px from centre):"),
            DynamicMapCalibrationFormSupport.buildStepRow(offsetXField, 5.0, ::tryPreview),
            Label("Offset Y (px from centre):"),
            DynamicMapCalibrationFormSupport.buildStepRow(offsetYField, 5.0, ::tryPreview),
            errorLabel,
        )

        val confirmation = DialogFlows.ConfirmationTracker()
        DialogFlows.installValidatedConfirm(
            dialog = dialog,
            tracker = confirmation,
            confirmButton = ButtonType.APPLY,
        ) {
            DynamicMapCalibrationFormSupport.clearFieldErrors(errorLabel, fieldsByInput.values)
            when (
                val result = DynamicMapCalibrationFormSupport.parseBackgroundCalibration(
                    scaleText = scaleField.text,
                    offsetXText = offsetXField.text,
                    offsetYText = offsetYField.text,
                    previewCellSize = safeCellSize,
                )
            ) {
                is DynamicMapCalibrationParseResult.Success -> {
                    controller.setBackgroundCalibration(result.value)
                    true
                }

                is DynamicMapCalibrationParseResult.Failure -> {
                    DynamicMapCalibrationFormSupport.showParseError(result, errorLabel, fieldsByInput)
                    false
                }
            }
        }

        DialogFlows.onHiddenWithCancelRestore(
            dialog = dialog,
            tracker = confirmation,
            onCancel = { controller.setBackgroundCalibration(saved) },
        )

        dialog.showAndWait()
    }

    fun showGuidedCalibrationDialog(
        owner: Window?,
        controller: DynamicMapBuilderController,
    ) {
        val document = controller.currentDocument()
        val imageUri = document.backgroundImageUri ?: return
        val image = runCatching { javafx.scene.image.Image(imageUri, false) }
            .getOrNull()
            ?.takeUnless { it.isError }
            ?: return

        val saved = document.backgroundCalibration
        var working = saved
        var step = GuidedCalibrationStep.SELECT_CENTER
        var step2Axis = DynamicMapGuidedCalibrationAxis.HORIZONTAL
        var centerCursorX = 0.0
        var centerCursorY = 0.0
        var centerCursorInitialized = false
        var step2OffsetPx = 0.0
        val viewport = DynamicMapViewportState(
            zoomFactor = 1.5,
            maxScale = 256.0,
            panStep = 40.0,
        )
        val confirmation = DialogFlows.ConfirmationTracker()

        val dialog = DialogFlows.createDialog<ButtonType>(
            owner = owner,
            title = "Guided Background Calibration",
            headerText = null,
            buttonTypes = listOf(ButtonType.APPLY, ButtonType.CANCEL),
        )
        dialog.dialogPane.prefWidth = 760.0

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

        val guidedFieldsByInput = mapOf(DynamicMapCalibrationInput.GUIDED_TILE_SPAN to wideModeTilesField)

        fun previewMetricsOrNull(): DynamicMapEditorMetrics? {
            if (canvas.width <= 0.0 || canvas.height <= 0.0) return null
            return computeEditorMetrics(
                width = canvas.width,
                height = canvas.height,
                cols = document.cols,
                rows = document.rows,
            )
        }

        fun currentTileSpanOrNull(): Int? =
            if (!wideModeCheck.isSelected) {
                1
            } else {
                wideModeTilesField.text.trim().toIntOrNull()?.takeIf { it > 0 }
            }

        fun resolveTileSpan(): Int? {
            DynamicMapCalibrationFormSupport.clearFieldErrors(errorLabel, guidedFieldsByInput.values)
            if (!wideModeCheck.isSelected) return 1
            return when (val result = DynamicMapCalibrationFormSupport.parseGuidedTileSpan(wideModeTilesField.text)) {
                is DynamicMapCalibrationParseResult.Success -> result.value
                is DynamicMapCalibrationParseResult.Failure -> {
                    DynamicMapCalibrationFormSupport.showParseError(result, errorLabel, guidedFieldsByInput)
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
            val cellSize = previewMetricsOrNull()?.cellSize ?: 1.0
            val screenDistance = cellSize * tileSpan * viewport.scale
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
                DynamicMapGuidedCalibrationAxis.HORIZONTAL ->
                    (baseX + step2OffsetPx).coerceIn(0.0, canvas.width) - baseX

                DynamicMapGuidedCalibrationAxis.VERTICAL ->
                    (baseY + step2OffsetPx).coerceIn(0.0, canvas.height) - baseY
            }
        }

        fun selectedCanvasPoint(): Pair<Double, Double> =
            when (step) {
                GuidedCalibrationStep.SELECT_CENTER -> Pair(centerCursorX, centerCursorY)
                GuidedCalibrationStep.SELECT_SECOND_POINT -> {
                    val (baseX, baseY) = baseCanvasPoint()
                    when (step2Axis) {
                        DynamicMapGuidedCalibrationAxis.HORIZONTAL -> Pair(baseX + step2OffsetPx, baseY)
                        DynamicMapGuidedCalibrationAxis.VERTICAL -> Pair(baseX, baseY + step2OffsetPx)
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
            pointLeftButton.isDisable = axisLocked && step2Axis == DynamicMapGuidedCalibrationAxis.VERTICAL
            pointRightButton.isDisable = axisLocked && step2Axis == DynamicMapGuidedCalibrationAxis.VERTICAL
            pointUpButton.isDisable = axisLocked && step2Axis == DynamicMapGuidedCalibrationAxis.HORIZONTAL
            pointDownButton.isDisable = axisLocked && step2Axis == DynamicMapGuidedCalibrationAxis.HORIZONTAL
        }

        fun redrawPreview() {
            val width = canvas.width
            val height = canvas.height
            if (width <= 0.0 || height <= 0.0) return

            val metrics = computeEditorMetrics(width, height, document.cols, document.rows)
            val theme = ThemeManager.currentTheme
            val backgroundColor = ColorHexCodec.hexToColor(theme.bgColor)
            val surfaceColor = ColorHexCodec.hexToColor(theme.surfaceColor)
            val borderColor = ColorHexCodec.hexToColor(theme.borderColor)

            val gc = canvas.graphicsContext2D
            gc.clearRect(0.0, 0.0, width, height)
            gc.fill = backgroundColor
            gc.fillRect(0.0, 0.0, width, height)

            gc.save()
            val centerX = width / 2.0
            val centerY = height / 2.0
            gc.translate(centerX + viewport.offsetX, centerY + viewport.offsetY)
            gc.scale(viewport.scale, viewport.scale)
            gc.translate(-centerX, -centerY)

            gc.fill = surfaceColor
            gc.fillRect(metrics.originX, metrics.originY, metrics.mapWidth, metrics.mapHeight)
            drawCalibratedBackgroundImage(gc, image, metrics, working)

            gc.stroke = borderColor.deriveColor(0.0, 1.0, 1.0, 0.6)
            gc.lineWidth = 1.0
            for (col in 0..document.cols) {
                val x = metrics.originX + col * metrics.cellSize
                gc.strokeLine(x, metrics.originY, x, metrics.originY + metrics.mapHeight)
            }
            for (row in 0..document.rows) {
                val y = metrics.originY + row * metrics.cellSize
                gc.strokeLine(metrics.originX, y, metrics.originX + metrics.mapWidth, y)
            }

            gc.stroke = borderColor
            gc.lineWidth = 2.0
            gc.strokeRect(metrics.originX, metrics.originY, metrics.mapWidth, metrics.mapHeight)
            gc.restore()
        }

        fun updateStatusText() {
            val metrics = previewMetricsOrNull()
            val previewScale = metrics?.cellSize?.let { working.scale * it } ?: working.scale
            val previewOffsetX = metrics?.cellSize?.let { working.offsetX * it } ?: working.offsetX
            val previewOffsetY = metrics?.cellSize?.let { working.offsetY * it } ?: working.offsetY
            statusLabel.text = when (step) {
                GuidedCalibrationStep.SELECT_CENTER ->
                    "Preview scale ${DynamicMapCalibrationFormSupport.formatDouble(previewScale)} px/image px | " +
                        "Offset ${DynamicMapCalibrationFormSupport.formatDouble(previewOffsetX)}, " +
                        "${DynamicMapCalibrationFormSupport.formatDouble(previewOffsetY)} px"

                GuidedCalibrationStep.SELECT_SECOND_POINT ->
                    "Axis: ${step2Axis.label} | Tiles away: ${currentTileSpanOrNull() ?: "?"} | " +
                        "Preview scale ${DynamicMapCalibrationFormSupport.formatDouble(previewScale)} px/image px"
            }
        }

        fun drawCursorMarker(x: Double, y: Double) {
            overlayGc.save()
            overlayGc.stroke = Color.BLACK
            overlayGc.lineWidth = 4.0
            overlayGc.strokeLine(x - 10.0, y, x + 10.0, y)
            overlayGc.strokeLine(x, y - 10.0, x, y + 10.0)

            overlayGc.stroke = Color.WHITE
            overlayGc.lineWidth = 2.5
            overlayGc.strokeLine(x - 10.0, y, x + 10.0, y)
            overlayGc.strokeLine(x, y - 10.0, x, y + 10.0)

            overlayGc.stroke = Color.DODGERBLUE
            overlayGc.lineWidth = 1.5
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
                        DynamicMapGuidedCalibrationAxis.HORIZONTAL ->
                            overlayGc.strokeLine(0.0, baseY, overlayCanvas.width, baseY)

                        DynamicMapGuidedCalibrationAxis.VERTICAL ->
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
                        DynamicMapGuidedCalibrationAxis.HORIZONTAL -> screenDx
                        DynamicMapGuidedCalibrationAxis.VERTICAL -> screenDy
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
                        "Drag the texture with the mouse for coarse positioning.\n" +
                            "Use the on-screen arrows or the keyboard arrow keys to move the blue cursor by one calibration-screen pixel at a time.\n" +
                            "This point becomes the centre for translation and scaling."
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
            DynamicMapCalibrationFormSupport.clearFieldErrors(errorLabel, guidedFieldsByInput.values)
            step2Axis = if (verticalAxisButton.isSelected) {
                DynamicMapGuidedCalibrationAxis.VERTICAL
            } else {
                DynamicMapGuidedCalibrationAxis.HORIZONTAL
            }
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
                    redrawPreview()
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
                x = dragStartOffsetX + (event.x - dragStartX),
                y = dragStartOffsetY + (event.y - dragStartY),
            )
            redrawPreview()
            refreshOverlayAndStatus()
        }

        canvas.setOnScroll { event ->
            viewport.zoomFromScroll(event.deltaY)
            redrawPreview()
            refreshOverlayAndStatus()
        }

        usePointButton.setOnAction {
            val cellSize = previewMetricsOrNull()?.cellSize ?: return@setOnAction
            when (step) {
                GuidedCalibrationStep.SELECT_CENTER -> {
                    val (worldX, worldY) = selectedWorldPoint()
                    working = guidedBackgroundCalibrationStep1(
                        current = working,
                        clickX = worldX,
                        clickY = worldY,
                        targetX = canvas.width / 2.0,
                        targetY = canvas.height / 2.0,
                        cellSizeInPixels = cellSize,
                    )
                    DynamicMapCalibrationFormSupport.clearFieldErrors(errorLabel, guidedFieldsByInput.values)
                    controller.setBackgroundCalibration(working)
                    redrawPreview()
                    advanceToStep2()
                    syncStep2OptionsVisibility()
                }

                GuidedCalibrationStep.SELECT_SECOND_POINT -> {
                    val tileSpan = resolveTileSpan() ?: run {
                        applyButton.isDisable = true
                        return@setOnAction
                    }
                    val (worldX, worldY) = selectedWorldPoint()
                    val updated = guidedBackgroundCalibrationStep2(
                        currentCalibration = working,
                        cornerX = worldX,
                        cornerY = worldY,
                        targetX = canvas.width / 2.0,
                        targetY = canvas.height / 2.0,
                        cellSizeInPixels = cellSize,
                        axis = step2Axis,
                        targetTileSpan = tileSpan,
                    )
                    if (updated != null) {
                        working = updated
                        DynamicMapCalibrationFormSupport.clearFieldErrors(errorLabel, guidedFieldsByInput.values)
                        controller.setBackgroundCalibration(working)
                        redrawPreview()
                        applyButton.isDisable = false
                        refreshOverlayAndStatus()
                    } else {
                        DynamicMapCalibrationFormSupport.showGuidedTargetTooCloseError(
                            errorLabel = errorLabel,
                            fields = guidedFieldsByInput.values,
                        )
                        applyButton.isDisable = true
                    }
                }
            }
        }

        skipButton.setOnAction {
            DynamicMapCalibrationFormSupport.clearFieldErrors(errorLabel, guidedFieldsByInput.values)
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
            DynamicMapCalibrationFormSupport.clearFieldErrors(errorLabel, guidedFieldsByInput.values)
            controller.setBackgroundCalibration(working)
            redrawPreview()
            if (centerCursorInitialized) {
                resetCenterCursor()
            }
            applyStepText()
            syncStep2OptionsVisibility()
        }

        zoomOutButton.setOnAction {
            viewport.zoomOut()
            redrawPreview()
            refreshOverlayAndStatus()
        }
        zoomInButton.setOnAction {
            viewport.zoomIn()
            redrawPreview()
            refreshOverlayAndStatus()
        }
        resetViewButton.setOnAction {
            viewport.reset()
            redrawPreview()
            refreshOverlayAndStatus()
        }
        panLeftButton.setOnAction {
            viewport.panLeft()
            redrawPreview()
            refreshOverlayAndStatus()
        }
        panUpButton.setOnAction {
            viewport.panUp()
            redrawPreview()
            refreshOverlayAndStatus()
        }
        panDownButton.setOnAction {
            viewport.panDown()
            redrawPreview()
            refreshOverlayAndStatus()
        }
        panRightButton.setOnAction {
            viewport.panRight()
            redrawPreview()
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
            step2Axis = DynamicMapGuidedCalibrationAxis.HORIZONTAL
            if (step == GuidedCalibrationStep.SELECT_SECOND_POINT) {
                resetStep2Cursor()
                applyButton.isDisable = true
            }
            refreshOverlayAndStatus()
        }
        verticalAxisButton.setOnAction {
            step2Axis = DynamicMapGuidedCalibrationAxis.VERTICAL
            if (step == GuidedCalibrationStep.SELECT_SECOND_POINT) {
                resetStep2Cursor()
                applyButton.isDisable = true
            }
            refreshOverlayAndStatus()
        }

        wideModeCheck.selectedProperty().addListener { _, _, isSelected ->
            wideModeTilesField.isDisable = !isSelected
            DynamicMapCalibrationFormSupport.clearFieldErrors(errorLabel, guidedFieldsByInput.values)
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
            redrawPreview()
            refreshOverlayAndStatus()
        }

        DialogFlows.installValidatedConfirm(
            dialog = dialog,
            tracker = confirmation,
            confirmButton = ButtonType.APPLY,
        ) {
            controller.setBackgroundCalibration(working)
            true
        }

        DialogFlows.onHiddenWithCancelRestore(
            dialog = dialog,
            tracker = confirmation,
            onCancel = { controller.setBackgroundCalibration(saved) },
        )

        dialog.showAndWait()
    }
}

private enum class GuidedCalibrationStep {
    SELECT_CENTER,
    SELECT_SECOND_POINT,
}

private enum class DynamicMapCalibrationInput {
    SCALE,
    OFFSET_X,
    OFFSET_Y,
    GUIDED_TILE_SPAN,
}

private sealed interface DynamicMapCalibrationParseResult<out T> {
    data class Success<T>(val value: T) : DynamicMapCalibrationParseResult<T>

    data class Failure(
        val field: DynamicMapCalibrationInput,
        val message: String,
    ) : DynamicMapCalibrationParseResult<Nothing>
}

private val DynamicMapGuidedCalibrationAxis.label: String
    get() =
        when (this) {
            DynamicMapGuidedCalibrationAxis.HORIZONTAL -> "Horizontal"
            DynamicMapGuidedCalibrationAxis.VERTICAL -> "Vertical"
        }

private object DynamicMapCalibrationFormSupport {
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

    fun formatDouble(value: Double): String =
        if (value == floor(value) && !value.isInfinite()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.4g", value)
        }

    fun parseBackgroundCalibration(
        scaleText: String,
        offsetXText: String,
        offsetYText: String,
        previewCellSize: Double,
    ): DynamicMapCalibrationParseResult<DynamicMapBackgroundCalibration> {
        val scale = scaleText.toDoubleOrNull()
            ?: return DynamicMapCalibrationParseResult.Failure(
                field = DynamicMapCalibrationInput.SCALE,
                message = "Scale must be a positive number.",
            )
        if (scale <= 0.0) {
            return DynamicMapCalibrationParseResult.Failure(
                field = DynamicMapCalibrationInput.SCALE,
                message = "Scale must be a positive number.",
            )
        }
        val offsetX = offsetXText.toDoubleOrNull()
            ?: return DynamicMapCalibrationParseResult.Failure(
                field = DynamicMapCalibrationInput.OFFSET_X,
                message = "Offset X must be a number.",
            )
        val offsetY = offsetYText.toDoubleOrNull()
            ?: return DynamicMapCalibrationParseResult.Failure(
                field = DynamicMapCalibrationInput.OFFSET_Y,
                message = "Offset Y must be a number.",
            )
        val safeCellSize = previewCellSize.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        return DynamicMapCalibrationParseResult.Success(
            DynamicMapBackgroundCalibration(
                scale = scale / safeCellSize,
                offsetX = offsetX / safeCellSize,
                offsetY = offsetY / safeCellSize,
            ),
        )
    }

    fun parseGuidedTileSpan(tileSpanText: String): DynamicMapCalibrationParseResult<Int> {
        val tileSpan = tileSpanText.trim().toIntOrNull()
            ?: return DynamicMapCalibrationParseResult.Failure(
                field = DynamicMapCalibrationInput.GUIDED_TILE_SPAN,
                message = "Wide mode tiles away must be a positive whole number.",
            )
        if (tileSpan <= 0) {
            return DynamicMapCalibrationParseResult.Failure(
                field = DynamicMapCalibrationInput.GUIDED_TILE_SPAN,
                message = "Wide mode tiles away must be a positive whole number.",
            )
        }
        return DynamicMapCalibrationParseResult.Success(tileSpan)
    }

    fun clearFieldErrors(errorLabel: Label, fields: Collection<TextField>) {
        fields.forEach { it.style = "" }
        errorLabel.text = ""
    }

    fun showParseError(
        error: DynamicMapCalibrationParseResult.Failure,
        errorLabel: Label,
        fieldsByInput: Map<DynamicMapCalibrationInput, TextField>,
    ) {
        clearFieldErrors(errorLabel, fieldsByInput.values)
        fieldsByInput[error.field]?.style = "-fx-border-color: red;"
        errorLabel.text = error.message
    }

    fun showGuidedTargetTooCloseError(
        errorLabel: Label,
        fields: Collection<TextField>,
    ) {
        clearFieldErrors(errorLabel, fields)
        errorLabel.text = "Move the second point at least one screen pixel away from the centre point on the selected axis."
    }
}

private class DynamicMapViewportState(
    private val zoomFactor: Double = 1.25,
    private val minScale: Double = 0.125,
    private val maxScale: Double = 8.0,
    private val panStep: Double = 20.0,
) {
    var scale: Double = 1.0
        private set

    var offsetX: Double = 0.0
        private set

    var offsetY: Double = 0.0
        private set

    fun reset() {
        scale = 1.0
        offsetX = 0.0
        offsetY = 0.0
    }

    fun zoomFromScroll(deltaY: Double) {
        val factor = if (deltaY > 0.0) zoomFactor else 1.0 / zoomFactor
        zoomBy(factor)
    }

    fun zoomIn() {
        zoomBy(zoomFactor)
    }

    fun zoomOut() {
        zoomBy(1.0 / zoomFactor)
    }

    fun panLeft() {
        offsetX -= panStep
    }

    fun panRight() {
        offsetX += panStep
    }

    fun panUp() {
        offsetY -= panStep
    }

    fun panDown() {
        offsetY += panStep
    }

    fun setPan(x: Double, y: Double) {
        offsetX = x
        offsetY = y
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

    private fun zoomBy(factor: Double) {
        scale = (scale * factor).coerceIn(minScale, maxScale)
    }
}
