package com.tabletopcontrol.map

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.EventBus
import javafx.event.ActionEvent
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
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
import javafx.stage.Window
import kotlin.math.floor

/** Active fog-of-war painting tool for the DM minimap canvas. */
private enum class FogTool { NONE, DRAW, ERASE }

/**
 * DM-panel plugin that exposes map-viewer controls and a live minimap preview.
 *
 * Controls provided:
 * - **Minimap preview** — a scaled-down live view of the table screen, showing the
 *   current map image, grid overlay, and fog-of-war state.
 * - **Load map** — opens a file chooser and publishes [MapLoadEvent].
 * - **Calibrate Map…** — opens a pop-up dialog for adjusting the map image
 *   scale and centre offset; publishes [MapCalibrationEvent] on every field change
 *   for live feedback and restores the original calibration if the dialog is cancelled.
 * - **Grid** — toggle checkbox and apply button, publishing [GridUpdateEvent].
 * - **Calibrate Grid…** — opens a pop-up dialog for adjusting the grid cell size,
 *   scale, and centre offset; publishes [GridCalibrationEvent] on every field change
 *   for live feedback and restores the original calibration if the dialog is cancelled.
 * - **Fog of war** — reveal-all / hide-all buttons, publishing [FogOfWarResetEvent].
 */
class MapPlugin : DmPlugin {

    override val displayName: String = "Map"
    override val iconPath: String? = null

    /** The most recently confirmed map calibration; used to restore on dialog cancel. */
    private var lastMapCalibration: MapCalibration = MapCalibration()

    /** The most recently confirmed grid calibration; used to restore on dialog cancel. */
    private var lastGridCalibration: GridCalibration = GridCalibration()

    /**
     * Whether the fog-of-war grid has been initialised via [FogOfWarSetupEvent].
     * Set to `true` the first time [ensureFogInitialized] is called.
     */
    private var fogInitialized = false

    /**
     * Publishes [FogOfWarSetupEvent] the first time it is called, creating a fog grid
     * centred on the grid origin and large enough to cover a typical tabletop display.
     *
     * The grid spans [halfFogCells]×2 cells in each dimension, so fog cells cover grid
     * columns and rows in the range `[-halfFogCells .. halfFogCells-1]`.  With the
     * default 50 px cell size this provides ±5 000 px of coverage in every direction.
     *
     * Subsequent calls are no-ops; the fog persists until the plugin is replaced.
     */
    private fun ensureFogInitialized() {
        if (fogInitialized) return
        val halfFogCells = 100
        EventBus.publish(
            FogOfWarSetupEvent(
                cols = halfFogCells * 2,
                rows = halfFogCells * 2,
                colOffset = -halfFogCells,
                rowOffset = -halfFogCells,
            ),
        )
        fogInitialized = true
    }

    /**
     * Creates the table-screen [Node] — a [Canvas] backed by a [MapRenderer] that
     * subscribes to map events and redraws on demand.
     *
     * The canvas is sized to fill its parent via a [Pane] that overrides
     * [Pane.layoutChildren]; this ensures the canvas always covers the full
     * table-screen area regardless of window size.
     */
    override fun createTableView(): Node {
        val canvas = Canvas()
        val renderer = MapRenderer(canvas)
        return object : Pane() {
            init {
                children.add(canvas)
            }

            override fun layoutChildren() {
                // Only resize and redraw when the available area actually changes.
                if (canvas.width != width || canvas.height != height) {
                    canvas.width = width
                    canvas.height = height
                    renderer.redraw()
                }
            }
        }
    }

    /**
     * Creates the DM-panel [Node] containing all map controls.
     *
     * The central element is a [Canvas] minimap that fills all available vertical
     * space in the pane and mirrors the table screen.  Below it a compact two-row
     * toolbar holds every control so they consume minimal fixed space:
     * - Row 1 — Load map button, path readout, Calibrate Map button.
     * - Row 2 — Show grid checkbox, Apply Grid, Calibrate Grid, Reveal All, Hide All.
     */
    override fun createView(): Node {
        val vbox = VBox(4.0).apply { padding = Insets(4.0) }

        val minimapSection = buildMinimapSection()
        VBox.setVgrow(minimapSection, Priority.ALWAYS)

        vbox.children.addAll(
            minimapSection,
            Separator(),
            buildCompactControls(),
        )

        return vbox
    }

    // -------------------------------------------------------------------------
    // Section builders
    // -------------------------------------------------------------------------

    /** Sets a red border on [field] and displays [message] in [errorLabel]. */
    private fun showFieldError(field: TextField, errorLabel: Label, message: String) {
        field.style = "-fx-border-color: red;"
        errorLabel.text = message
    }

    /** Clears red borders and error text from all [fields] and resets [errorLabel]. */
    private fun clearAllFieldErrors(vararg fields: TextField, errorLabel: Label) {
        fields.forEach { it.style = "" }
        errorLabel.text = ""
    }

    /**
     * Wraps [field] in an [HBox] with decrement (`−`) and increment (`+`) buttons.
     *
     * Each button adjusts the field's numeric value by [step] and then invokes
     * [onChanged] so the caller can publish a live calibration event.
     *
     * @param field     the [TextField] to wrap.
     * @param step      amount to add or subtract on each button press.
     * @param onChanged callback invoked after each button-triggered change.
     * @return an [HBox] containing `[−] [field] [+]`.
     */
    private fun buildStepRow(field: TextField, step: Double, onChanged: () -> Unit): HBox {
        fun adjust(delta: Double) {
            val current = field.text.toDoubleOrNull() ?: 0.0
            field.text = formatDouble(current + delta)
            onChanged()
        }
        val decBtn = Button("−").apply {
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            tooltip = Tooltip("Decrease by $step")
            setOnAction { adjust(-step) }
        }
        val incBtn = Button("+").apply {
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            tooltip = Tooltip("Increase by $step")
            setOnAction { adjust(step) }
        }
        HBox.setHgrow(field, Priority.ALWAYS)
        return HBox(4.0, decBtn, field, incBtn)
    }

    /**
     * Formats [value] as a compact string: whole numbers are shown without a
     * decimal point; fractional values are shown with up to four significant digits.
     */
    private fun formatDouble(value: Double): String =
        if (value == floor(value) && !value.isInfinite()) {
            value.toLong().toString()
        } else {
            "%.4g".format(value)
        }

    /**
     * Builds the minimap preview section.
     *
     * A [Canvas] is placed inside a [Pane] subclass that resizes it to fill all
     * available space on every layout pass, so the minimap grows and shrinks with
     * the DM panel.  A second [MapRenderer] instance is created for this canvas and
     * automatically subscribes to all map events, keeping the content in sync with
     * the table screen.
     *
     * The minimap has its own independent viewport that does **not** affect the
     * table-view renderer:
     * - **Drag** (left button) on the canvas to pan (when no fog tool is active).
     * - **Scroll wheel** to zoom in/out around the canvas centre.
     * - **`−`/`+`** buttons to zoom out/in by 25 % per click.
     * - **◀ ▶ ▲ ▼** buttons to pan by 20 canvas-space pixels per click.
     * - **Reset** button to restore the default view (scale 1, no offset).
     * - **Draw Fog** / **Erase Fog** toggle buttons to activate the fog paint tool;
     *   left-click or drag on the canvas to cover or uncover cells.
     */
    private fun buildMinimapSection(): VBox {
        val minimapCanvas = Canvas(1.0, 1.0)
        val minimapRenderer = MapRenderer(minimapCanvas)
        // DM can see through fog on the minimap; players see fully opaque fog on the table view.
        minimapRenderer.fogOpacity = 0.5

        // A Pane that keeps the canvas sized to fill its layout bounds.
        val canvasPane = object : Pane() {
            init {
                children.add(minimapCanvas)
                style = "-fx-border-color: gray;"
                minHeight = 80.0
            }

            override fun layoutChildren() {
                // Use a 0.5 px threshold to avoid superfluous redraws during
                // sub-pixel layout adjustments.
                if (Math.abs(minimapCanvas.width - width) > 0.5 ||
                    Math.abs(minimapCanvas.height - height) > 0.5
                ) {
                    minimapCanvas.width = width
                    minimapCanvas.height = height
                    minimapRenderer.redraw()
                }
            }
        }

        // ------------------------------------------------------------------
        // Pan step (canvas-space pixels per button press)
        // ------------------------------------------------------------------
        val panStep = 20.0
        val zoomFactor = 1.25
        val minScale = 0.125
        val maxScale = 8.0

        fun resetViewport() {
            minimapRenderer.viewportScale = 1.0
            minimapRenderer.viewportOffsetX = 0.0
            minimapRenderer.viewportOffsetY = 0.0
            minimapRenderer.redraw()
        }

        // ------------------------------------------------------------------
        // Fog paint tool state
        // ------------------------------------------------------------------
        var fogTool: FogTool = FogTool.NONE

        // ------------------------------------------------------------------
        // Mouse drag to pan / fog paint
        // ------------------------------------------------------------------
        var dragStartX = 0.0
        var dragStartY = 0.0
        var dragStartOffX = 0.0
        var dragStartOffY = 0.0

        minimapCanvas.setOnMousePressed { e ->
            if (e.button == MouseButton.PRIMARY) {
                if (fogTool != FogTool.NONE) {
                    // Fog painting: determine the clicked cell and publish an event.
                    val cell = minimapRenderer.canvasCoordsToFogCell(e.x, e.y)
                    if (cell != null) {
                        EventBus.publish(
                            FogOfWarCellEvent(cell.first, cell.second, revealed = fogTool == FogTool.ERASE),
                        )
                    }
                } else {
                    // Pan mode: record the drag start position.
                    dragStartX = e.x
                    dragStartY = e.y
                    dragStartOffX = minimapRenderer.viewportOffsetX
                    dragStartOffY = minimapRenderer.viewportOffsetY
                }
            }
        }
        minimapCanvas.setOnMouseDragged { e ->
            if (e.isPrimaryButtonDown) {
                if (fogTool != FogTool.NONE) {
                    // Fog painting: paint every cell the mouse passes over.
                    val cell = minimapRenderer.canvasCoordsToFogCell(e.x, e.y)
                    if (cell != null) {
                        EventBus.publish(
                            FogOfWarCellEvent(cell.first, cell.second, revealed = fogTool == FogTool.ERASE),
                        )
                    }
                } else {
                    minimapRenderer.viewportOffsetX = dragStartOffX + (e.x - dragStartX)
                    minimapRenderer.viewportOffsetY = dragStartOffY + (e.y - dragStartY)
                    minimapRenderer.redraw()
                }
            }
        }

        // ------------------------------------------------------------------
        // Scroll wheel to zoom from the canvas centre
        // ------------------------------------------------------------------
        minimapCanvas.setOnScroll { e ->
            val factor = if (e.deltaY > 0) zoomFactor else 1.0 / zoomFactor
            minimapRenderer.viewportScale =
                (minimapRenderer.viewportScale * factor).coerceIn(minScale, maxScale)
            minimapRenderer.redraw()
        }

        // ------------------------------------------------------------------
        // Zoom buttons
        // ------------------------------------------------------------------
        val zoomOutBtn = Button("−").apply {
            tooltip = Tooltip("Zoom out (minimap only)")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction {
                minimapRenderer.viewportScale =
                    (minimapRenderer.viewportScale / zoomFactor).coerceAtLeast(minScale)
                minimapRenderer.redraw()
            }
        }
        val zoomInBtn = Button("+").apply {
            tooltip = Tooltip("Zoom in (minimap only)")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction {
                minimapRenderer.viewportScale =
                    (minimapRenderer.viewportScale * zoomFactor).coerceAtMost(maxScale)
                minimapRenderer.redraw()
            }
        }
        val resetBtn = Button("Reset").apply {
            tooltip = Tooltip("Reset minimap zoom and pan to default")
            setOnAction { resetViewport() }
        }

        // ------------------------------------------------------------------
        // Pan buttons
        // ------------------------------------------------------------------
        val panLeft = Button("◀").apply {
            tooltip = Tooltip("Pan view left")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction {
                minimapRenderer.viewportOffsetX -= panStep
                minimapRenderer.redraw()
            }
        }
        val panRight = Button("▶").apply {
            tooltip = Tooltip("Pan view right")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction {
                minimapRenderer.viewportOffsetX += panStep
                minimapRenderer.redraw()
            }
        }
        val panUp = Button("▲").apply {
            tooltip = Tooltip("Pan view up")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction {
                minimapRenderer.viewportOffsetY -= panStep
                minimapRenderer.redraw()
            }
        }
        val panDown = Button("▼").apply {
            tooltip = Tooltip("Pan view down")
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            setOnAction {
                minimapRenderer.viewportOffsetY += panStep
                minimapRenderer.redraw()
            }
        }

        // ------------------------------------------------------------------
        // Fog paint tool buttons (inline with zoom/pan)
        // ------------------------------------------------------------------
        val drawFogBtn = ToggleButton("Draw Fog").apply {
            tooltip = Tooltip("Draw fog: click/drag on map to cover cells with fog")
        }
        val eraseFogBtn = ToggleButton("Erase Fog").apply {
            tooltip = Tooltip("Erase fog: click/drag on map to reveal cells")
        }
        drawFogBtn.setOnAction {
            if (drawFogBtn.isSelected) {
                eraseFogBtn.isSelected = false
                fogTool = FogTool.DRAW
                ensureFogInitialized()
            } else {
                fogTool = FogTool.NONE
            }
        }
        eraseFogBtn.setOnAction {
            if (eraseFogBtn.isSelected) {
                drawFogBtn.isSelected = false
                fogTool = FogTool.ERASE
                ensureFogInitialized()
            } else {
                fogTool = FogTool.NONE
            }
        }

        val controlsRow = HBox(
            4.0,
            zoomOutBtn, zoomInBtn, resetBtn,
            Label("  "),
            panLeft, panUp, panDown, panRight,
            Label("  "),
            drawFogBtn, eraseFogBtn,
        )

        val section = VBox(4.0, canvasPane, controlsRow)
        VBox.setVgrow(canvasPane, Priority.ALWAYS)
        return section
    }

    // -------------------------------------------------------------------------
    // Compact controls toolbar
    // -------------------------------------------------------------------------

    /**
     * Builds the compact two-row controls strip shown below the minimap.
     *
     * **Row 1 — Map image:**
     * `[Load Map…]  [path readout (grows)]  [Calibrate Map…]`
     *
     * **Row 2 — Grid & Fog of war:**
     * `[☐ Show grid]  [Apply Grid]  [Calibrate Grid…]  │  [Reveal All]  [Hide All]`
     *
     * [Reveal All] and [Hide All] auto-initialise the fog grid (via [ensureFogInitialized])
     * if it has not already been created.  Every element publishes the appropriate
     * [EventBus] event; the calibration buttons open their respective pop-up dialogs.
     */
    private fun buildCompactControls(): VBox {
        // --- Row 1: Map image ---
        val pathField = TextField().apply {
            isEditable = false
            promptText = "No map loaded"
            tooltip = Tooltip("Path to the currently loaded map image")
        }
        HBox.setHgrow(pathField, Priority.ALWAYS)

        val loadBtn = Button("Load Map…").apply {
            tooltip = Tooltip("Open a map image file")
            setOnAction { e ->
                val chooser = FileChooser().apply {
                    title = "Select map image"
                    extensionFilters.addAll(
                        FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
                        FileChooser.ExtensionFilter("All files", "*.*"),
                    )
                }
                val owner = (e.source as? Button)?.scene?.window
                val file = chooser.showOpenDialog(owner)
                if (file != null) {
                    pathField.text = file.absolutePath
                    EventBus.publish(MapLoadEvent(file.toURI().toString()))
                }
            }
        }

        val calibrateMapBtn = Button("Calibrate Map…").apply {
            tooltip = Tooltip("Adjust map image scale and centre position")
            setOnAction { e ->
                showMapCalibrationDialog((e.source as? Button)?.scene?.window)
            }
        }

        val mapRow = HBox(4.0, loadBtn, pathField, calibrateMapBtn)

        // --- Row 2: Grid + Fog of war ---
        val visibleCheck = CheckBox("Show Grid").apply {
            isSelected = false
            tooltip = Tooltip("Toggle grid overlay visibility")
        }

        val applyGridBtn = Button("Apply Grid").apply {
            tooltip = Tooltip("Publish the current grid visibility setting")
            setOnAction {
                EventBus.publish(
                    if (!visibleCheck.isSelected) GridUpdateEvent(null)
                    else GridUpdateEvent(GridConfig()),
                )
            }
        }

        val calibrateGridBtn = Button("Calibrate Grid…").apply {
            tooltip = Tooltip("Adjust grid cell size and centre position")
            setOnAction { e ->
                showGridCalibrationDialog((e.source as? Button)?.scene?.window)
            }
        }

        val revealAllBtn = Button("Reveal All").apply {
            tooltip = Tooltip("Remove fog from the entire map")
            setOnAction {
                ensureFogInitialized()
                EventBus.publish(FogOfWarResetEvent(revealAll = true))
            }
        }

        val hideAllBtn = Button("Hide All").apply {
            tooltip = Tooltip("Cover the entire map with fog")
            setOnAction {
                ensureFogInitialized()
                EventBus.publish(FogOfWarResetEvent(revealAll = false))
            }
        }

        val fowSep = Separator(Orientation.VERTICAL)
        val gridFowRow = HBox(4.0, visibleCheck, applyGridBtn, calibrateGridBtn, fowSep, revealAllBtn, hideAllBtn)

        return VBox(4.0, mapRow, gridFowRow)
    }

    // -------------------------------------------------------------------------
    // Calibration dialogs
    // -------------------------------------------------------------------------

    /**
     * Opens the map-image calibration dialog.
     *
     * While the dialog is open a red dot is shown at the canvas centre on the
     * table view (via [MapCalibrationModeEvent]) so the DM can align a reference
     * point on the map image.
     *
     * Every field change immediately publishes a [MapCalibrationEvent] for live
     * feedback in the minimap and table view.  Clicking **Apply** confirms the
     * current values; clicking **Cancel** or closing the dialog restores the
     * calibration that was active when the dialog opened.
     *
     * @param owner optional owner window for modality.
     */
    private fun showMapCalibrationDialog(owner: Window?) {
        val saved = lastMapCalibration

        val dialog = Dialog<ButtonType>().apply {
            title = "Calibrate Map"
            headerText = "Adjust the map image scale and position.\n" +
                "A red dot marks the canvas centre — align it with a known reference point on the map.\n" +
                "Changes are previewed live; Cancel restores the previous calibration."
            initOwner(owner)
        }

        EventBus.publish(MapCalibrationModeEvent(active = true))

        val scaleField = TextField(formatDouble(saved.scale)).apply {
            tooltip = Tooltip("Uniform zoom factor (1.0 = no zoom)")
            prefColumnCount = 8
        }
        val offsetXField = TextField(formatDouble(saved.offsetX)).apply {
            tooltip = Tooltip("Horizontal displacement of the image centre from the canvas centre (px)")
            prefColumnCount = 8
        }
        val offsetYField = TextField(formatDouble(saved.offsetY)).apply {
            tooltip = Tooltip("Vertical displacement of the image centre from the canvas centre (px)")
            prefColumnCount = 8
        }
        val errorLabel = Label().apply { textFill = Color.RED }

        /** Attempts to parse current field values and publish a live preview event. */
        fun tryPublishLive() {
            val scale = scaleField.text.toDoubleOrNull() ?: return
            val ox = offsetXField.text.toDoubleOrNull() ?: return
            val oy = offsetYField.text.toDoubleOrNull() ?: return
            if (scale <= 0) return
            EventBus.publish(MapCalibrationEvent(MapCalibration(scale, ox, oy)))
        }

        // Attach live-feedback listeners to all three fields.
        scaleField.textProperty().addListener { _, _, _ -> tryPublishLive() }
        offsetXField.textProperty().addListener { _, _, _ -> tryPublishLive() }
        offsetYField.textProperty().addListener { _, _, _ -> tryPublishLive() }

        dialog.dialogPane.content = VBox(
            8.0,
            Label("Scale:"), buildStepRow(scaleField, 0.05) { tryPublishLive() },
            Label("Offset X (px from centre):"), buildStepRow(offsetXField, 5.0) { tryPublishLive() },
            Label("Offset Y (px from centre):"), buildStepRow(offsetYField, 5.0) { tryPublishLive() },
            errorLabel,
        )
        dialog.dialogPane.buttonTypes.addAll(ButtonType.APPLY, ButtonType.CANCEL)

        var confirmed = false

        // Validate on Apply — consume the event to keep the dialog open on error.
        dialog.dialogPane.lookupButton(ButtonType.APPLY)
            .addEventFilter(ActionEvent.ACTION) { evt ->
                val scale = scaleField.text.toDoubleOrNull()
                val ox = offsetXField.text.toDoubleOrNull()
                val oy = offsetYField.text.toDoubleOrNull()

                clearAllFieldErrors(scaleField, offsetXField, offsetYField, errorLabel = errorLabel)

                when {
                    scale == null || scale <= 0 -> {
                        showFieldError(scaleField, errorLabel, "Scale must be a positive number.")
                        evt.consume()
                    }
                    ox == null -> {
                        showFieldError(offsetXField, errorLabel, "Offset X must be a number.")
                        evt.consume()
                    }
                    oy == null -> {
                        showFieldError(offsetYField, errorLabel, "Offset Y must be a number.")
                        evt.consume()
                    }
                    else -> {
                        lastMapCalibration = MapCalibration(scale, ox, oy)
                        EventBus.publish(MapCalibrationEvent(lastMapCalibration))
                        confirmed = true
                    }
                }
            }

        dialog.setOnHidden {
            EventBus.publish(MapCalibrationModeEvent(active = false))
            // Restore the saved calibration when the dialog is dismissed without Apply.
            if (!confirmed) {
                lastMapCalibration = saved
                EventBus.publish(MapCalibrationEvent(saved))
            }
        }

        dialog.showAndWait()
    }

    /**
     * Opens the grid calibration dialog.
     *
     * While the dialog is open a yellow crosshair is shown through the canvas
     * centre on the table view (via [GridCalibrationModeEvent]).  The intersection
     * marks the scale origin and grid origin for all scale operations.
     *
     * Every field change immediately publishes a [GridCalibrationEvent] for live
     * feedback in the minimap and table view.  Clicking **Apply** confirms the
     * current values; clicking **Cancel** or closing the dialog restores the
     * calibration that was active when the dialog opened.
     *
     * @param owner optional owner window for modality.
     */
    private fun showGridCalibrationDialog(owner: Window?) {
        val saved = lastGridCalibration

        val dialog = Dialog<ButtonType>().apply {
            title = "Calibrate Grid"
            headerText = "Adjust the grid cell size and position.\n" +
                "A yellow crosshair marks the canvas centre — this is the origin for all scale operations.\n" +
                "Changes are previewed live; Cancel restores the previous calibration."
            initOwner(owner)
        }

        EventBus.publish(GridCalibrationModeEvent(active = true))

        val cellSizeField = TextField(formatDouble(saved.cellSizeInPixels)).apply {
            tooltip = Tooltip("Grid cell size in canvas pixels at scale 1.0")
            prefColumnCount = 8
        }
        val scaleField = TextField(formatDouble(saved.scale)).apply {
            tooltip = Tooltip("Zoom factor applied from the canvas centre (1.0 = no zoom)")
            prefColumnCount = 8
        }
        val offsetXField = TextField(formatDouble(saved.offsetX)).apply {
            tooltip = Tooltip("Horizontal displacement of the grid origin from the canvas centre (px)")
            prefColumnCount = 8
        }
        val offsetYField = TextField(formatDouble(saved.offsetY)).apply {
            tooltip = Tooltip("Vertical displacement of the grid origin from the canvas centre (px)")
            prefColumnCount = 8
        }
        val errorLabel = Label().apply { textFill = Color.RED }

        /** Attempts to parse current field values and publish a live preview event. */
        fun tryPublishLive() {
            val cellSize = cellSizeField.text.toDoubleOrNull() ?: return
            val scale = scaleField.text.toDoubleOrNull() ?: return
            val ox = offsetXField.text.toDoubleOrNull() ?: return
            val oy = offsetYField.text.toDoubleOrNull() ?: return
            if (cellSize <= 0 || scale <= 0) return
            EventBus.publish(GridCalibrationEvent(GridCalibration(cellSize, scale, ox, oy)))
        }

        // Attach live-feedback listeners to all four fields.
        cellSizeField.textProperty().addListener { _, _, _ -> tryPublishLive() }
        scaleField.textProperty().addListener { _, _, _ -> tryPublishLive() }
        offsetXField.textProperty().addListener { _, _, _ -> tryPublishLive() }
        offsetYField.textProperty().addListener { _, _, _ -> tryPublishLive() }

        dialog.dialogPane.content = VBox(
            8.0,
            Label("Cell size (px):"), buildStepRow(cellSizeField, 1.0) { tryPublishLive() },
            Label("Scale:"), buildStepRow(scaleField, 0.05) { tryPublishLive() },
            Label("Offset X (px from centre):"), buildStepRow(offsetXField, 1.0) { tryPublishLive() },
            Label("Offset Y (px from centre):"), buildStepRow(offsetYField, 1.0) { tryPublishLive() },
            errorLabel,
        )
        dialog.dialogPane.buttonTypes.addAll(ButtonType.APPLY, ButtonType.CANCEL)

        var confirmed = false

        // Validate on Apply — consume the event to keep the dialog open on error.
        dialog.dialogPane.lookupButton(ButtonType.APPLY)
            .addEventFilter(ActionEvent.ACTION) { evt ->
                val cellSize = cellSizeField.text.toDoubleOrNull()
                val scale = scaleField.text.toDoubleOrNull()
                val ox = offsetXField.text.toDoubleOrNull()
                val oy = offsetYField.text.toDoubleOrNull()

                clearAllFieldErrors(cellSizeField, scaleField, offsetXField, offsetYField, errorLabel = errorLabel)

                when {
                    cellSize == null || cellSize <= 0 -> {
                        showFieldError(cellSizeField, errorLabel, "Cell size must be a positive number.")
                        evt.consume()
                    }
                    scale == null || scale <= 0 -> {
                        showFieldError(scaleField, errorLabel, "Scale must be a positive number.")
                        evt.consume()
                    }
                    ox == null -> {
                        showFieldError(offsetXField, errorLabel, "Offset X must be a number.")
                        evt.consume()
                    }
                    oy == null -> {
                        showFieldError(offsetYField, errorLabel, "Offset Y must be a number.")
                        evt.consume()
                    }
                    else -> {
                        lastGridCalibration = GridCalibration(cellSize, scale, ox, oy)
                        EventBus.publish(GridCalibrationEvent(lastGridCalibration))
                        confirmed = true
                    }
                }
            }

        dialog.setOnHidden {
            EventBus.publish(GridCalibrationModeEvent(active = false))
            // Restore the saved calibration when the dialog is dismissed without Apply.
            if (!confirmed) {
                lastGridCalibration = saved
                EventBus.publish(GridCalibrationEvent(saved))
            }
        }

        dialog.showAndWait()
    }
}
