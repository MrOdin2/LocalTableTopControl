package com.tabletopcontrol.map

import com.tabletopcontrol.core.ui.dialog.DialogFlows
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
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
import kotlin.math.hypot

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
            MapDialogFormSupport.buildStepRow(offsetXField, 5.0, ::tryPreview),
            Label("Offset Y (px from centre):"),
            MapDialogFormSupport.buildStepRow(offsetYField, 5.0, ::tryPreview),
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
        var step1Calibration: MapCalibration? = null
        var step = GuidedCalibrationStep.SELECT_CENTER
        val viewport = MapViewportState()
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
        val errorLabel = Label().apply { textFill = Color.RED }

        val canvas = Canvas()
        val renderer = MapRenderer(canvas).apply {
            mapCalibration = working
            gridCalibration = settingsService.gridCalibration
            gridConfig = settingsService.currentGridConfig
            backgroundColor = settingsService.backgroundColor
            settingsService.currentMapImageUri?.let { loadImage(it) }
        }
        canvas.sceneProperty().addListener { _, _, newScene ->
            if (newScene == null) renderer.dispose()
        }

        val canvasPane = object : Pane() {
            init {
                children.add(canvas)
                minWidth = 400.0
                minHeight = 280.0
                prefWidth = 600.0
                prefHeight = 400.0
            }

            override fun layoutChildren() {
                if (canvas.width != width || canvas.height != height) {
                    canvas.width = width
                    canvas.height = height
                    renderer.redraw()
                }
            }
        }

        calibrationService.setMapCalibrationMode(true)
        calibrationService.setGridCalibrationMode(true)

        dialog.dialogPane.prefWidth = 640.0

        val applyButton = dialog.dialogPane.lookupButton(ButtonType.APPLY)
        applyButton.isDisable = true

        val backButton = Button("\u2190 Back to Step 1").apply {
            tooltip = Tooltip("Discard Step 1 and restart from the saved calibration.")
            isDisable = true
        }
        val skipButton = Button("Skip this step \u2192").apply {
            tooltip = Tooltip("Skip this step and keep the current calibration for it.")
        }
        val navRow = HBox().also { row ->
            val spacer = Region()
            HBox.setHgrow(spacer, Priority.ALWAYS)
            row.children.addAll(backButton, spacer, skipButton)
        }

        fun applyStepText() {
            when (step) {
                GuidedCalibrationStep.SELECT_CENTER -> {
                    stepLabel.text = "Step 1 of 2: Select the grid centre"
                    instructionLabel.text =
                        "Click the point on the map that represents the grid centre.\n" +
                            "The map will translate so that point aligns with the canvas centre.\n" +
                            "Drag to pan, scroll to zoom, or use the view controls below.\n" +
                            "If the centre is already aligned, use \"Skip this step ->\" to proceed."
                }
                GuidedCalibrationStep.SELECT_ADJACENT_CORNER -> {
                    stepLabel.text = "Step 2 of 2: Select an adjacent tile corner"
                    instructionLabel.text =
                        "Click the corner of a tile that is directly adjacent to the centre point.\n" +
                            "The grid lines show where tile corners will land after calibration.\n" +
                            "Drag to pan, scroll to zoom, or use the view controls below.\n" +
                            "If the scale is already aligned, use \"Skip this step ->\" to proceed."
                }
            }
        }

        fun advanceToStep2() {
            step1Calibration = working
            step = GuidedCalibrationStep.SELECT_ADJACENT_CORNER
            backButton.isDisable = false
            errorLabel.text = ""
            applyStepText()
        }

        skipButton.setOnAction {
            when (step) {
                GuidedCalibrationStep.SELECT_CENTER -> advanceToStep2()
                GuidedCalibrationStep.SELECT_ADJACENT_CORNER -> applyButton.isDisable = false
            }
        }

        backButton.setOnAction {
            working = saved
            step1Calibration = null
            step = GuidedCalibrationStep.SELECT_CENTER
            applyButton.isDisable = true
            backButton.isDisable = true
            errorLabel.text = ""
            applyStepText()
            settingsService.previewMapCalibration(working)
        }

        var dragStartX = 0.0
        var dragStartY = 0.0
        var dragStartOffsetX = 0.0
        var dragStartOffsetY = 0.0
        var dragDistance = 0.0
        val clickThresholdPx = 5.0

        canvas.setOnMousePressed { event ->
            if (event.button == MouseButton.PRIMARY) {
                dragStartX = event.x
                dragStartY = event.y
                dragStartOffsetX = viewport.offsetX
                dragStartOffsetY = viewport.offsetY
                dragDistance = 0.0
            }
        }

        canvas.setOnMouseDragged { event ->
            if (event.isPrimaryButtonDown) {
                val dx = event.x - dragStartX
                val dy = event.y - dragStartY
                dragDistance = hypot(dx, dy)
                viewport.setPan(renderer, dragStartOffsetX + dx, dragStartOffsetY + dy)
            }
        }

        canvas.setOnMouseReleased { event ->
            if (event.button != MouseButton.PRIMARY || dragDistance >= clickThresholdPx) return@setOnMouseReleased

            val centerX = canvas.width / 2.0
            val centerY = canvas.height / 2.0
            val (worldX, worldY) = viewport.canvasToWorld(
                canvasWidth = canvas.width,
                canvasHeight = canvas.height,
                canvasX = event.x,
                canvasY = event.y,
            )

            when (step) {
                GuidedCalibrationStep.SELECT_CENTER -> {
                    working = calibrationService.guidedStep1(
                        current = working,
                        worldX = worldX,
                        worldY = worldY,
                        canvasCenterX = centerX,
                        canvasCenterY = centerY,
                    )
                    errorLabel.text = ""
                    settingsService.previewMapCalibration(working)
                    advanceToStep2()
                }
                GuidedCalibrationStep.SELECT_ADJACENT_CORNER -> {
                    when (
                        val result = calibrationService.guidedStep2(
                            step1Calibration = step1Calibration,
                            worldX = worldX,
                            worldY = worldY,
                            canvasCenterX = centerX,
                            canvasCenterY = centerY,
                            gridCellPixels = settingsService.gridCalibration.effectiveCellSizeInPixels(),
                        )
                    ) {
                        is MapResult.Success -> {
                            working = result.value
                            errorLabel.text = ""
                            settingsService.previewMapCalibration(working)
                            applyButton.isDisable = false
                        }
                        is MapResult.Failure -> {
                            errorLabel.text = result.error.toUserMessage()
                        }
                    }
                }
            }
        }

        canvas.setOnScroll { event ->
            viewport.zoomFromScroll(renderer, event.deltaY)
        }

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
        val resetViewButton = Button("Reset View").apply {
            tooltip = Tooltip("Reset zoom and pan to default")
            setOnAction { viewport.reset(renderer) }
        }
        val panLeftButton = Button("\u25c0").apply {
            tooltip = Tooltip("Pan view left")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction { viewport.panLeft(renderer) }
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
        val panRightButton = Button("\u25b6").apply {
            tooltip = Tooltip("Pan view right")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction { viewport.panRight(renderer) }
        }

        val viewControlsRow = HBox(
            4.0,
            zoomOutButton,
            zoomInButton,
            resetViewButton,
            Label("  "),
            panLeftButton,
            panUpButton,
            panDownButton,
            panRightButton,
        )

        applyStepText()
        dialog.dialogPane.content = VBox(
            10.0,
            stepLabel,
            instructionLabel,
            errorLabel,
            navRow,
            canvasPane,
            viewControlsRow,
        )

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
            onAlways = {
                calibrationService.setMapCalibrationMode(false)
                calibrationService.setGridCalibrationMode(false)
            },
        )

        dialog.showAndWait()
    }
}

private enum class GuidedCalibrationStep {
    SELECT_CENTER,
    SELECT_ADJACENT_CORNER,
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
