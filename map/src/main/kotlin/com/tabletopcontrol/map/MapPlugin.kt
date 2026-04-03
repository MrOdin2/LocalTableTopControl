package com.tabletopcontrol.map

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.TokenMovedEvent
import com.tabletopcontrol.core.ui.ContextMenuRenderer
import com.tabletopcontrol.core.ui.MenuAction
import com.tabletopcontrol.core.ui.MenuSection
import javafx.event.ActionEvent
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.ComboBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.TextField
import javafx.scene.control.TextInputDialog
import javafx.scene.control.ToggleButton
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import javafx.stage.Window
import kotlin.math.floor
import java.util.UUID

/** Active fog-of-war painting tool for the DM minimap canvas. */
private enum class FogTool { NONE, DRAW, ERASE }

/** Active measurement placement tool for the DM minimap canvas. */
private enum class MeasurementTool { NONE, LINE, CONE, RECTANGLE, CIRCLE }

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
 * - **Guided Calibration…** — opens a two-step interactive dialog: the DM clicks
 *   the grid centre on the map (Step 1, translates the map) and then an adjacent
 *   tile corner (Step 2, scales the map), aligning the image with the overlay grid.
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
    private var lastMapCalibration: MapCalibration

    /** The most recently confirmed grid calibration; used to restore on dialog cancel. */
    private var lastGridCalibration: GridCalibration

    /** The most recently applied grid line colour. */
    private var lastGridColor: Color

    /** The most recently applied plain-colour background. */
    private var lastBackgroundColor: Color

    /** The most recently applied map rotation in degrees (0, 90, 180, or 270). */
    private var lastMapRotation: Int

    /**
     * Whether token names are currently shown on the map view.
     * Persisted across renderer re-creations via [publishCurrentSettings].
     */
    private var lastShowTokenNames: Boolean = false

    init {
        val saved = MapSettingsSerializer.load()
        lastGridCalibration = saved.gridCalibration ?: GridCalibration()
        lastMapCalibration = saved.mapCalibration ?: MapCalibration()
        lastGridColor = saved.gridColor ?: GridConfig().color
        lastBackgroundColor = saved.backgroundColor ?: Color.BLACK
        lastMapRotation = saved.mapRotation ?: 0
    }

    /** URI of the most recently loaded map image, or `null` if no map has been loaded. */
    private var currentMapImageUri: String? = null

    /**
     * The most recently applied grid configuration, or `null` when the grid has never been
     * applied or was explicitly hidden.  Used to initialise the guided-calibration canvas.
     */
    private var currentGridConfig: GridConfig? = null

    /**
     * Whether the fog-of-war grid has been initialised via [FogOfWarSetupEvent].
     * Set to `true` the first time [ensureFogInitialized] is called.
     */
    private var fogInitialized = false

    private val supportedMeasurementUnits = setOf("ft", "m")

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
     * Publishes [MapCalibrationEvent], [GridCalibrationEvent], [MapBackgroundEvent],
     * [MapRotationEvent], and [ShowTokenNamesEvent] for the current persisted settings
     * so that any newly created renderer can initialise with the saved values.
     */
    private fun publishCurrentSettings() {
        EventBus.publish(MapCalibrationEvent(lastMapCalibration))
        EventBus.publish(GridCalibrationEvent(lastGridCalibration))
        EventBus.publish(MapBackgroundEvent(lastBackgroundColor))
        EventBus.publish(MapRotationEvent(lastMapRotation))
        EventBus.publish(ShowTokenNamesEvent(lastShowTokenNames))
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
        renderer.hideTokensInFog = true
        renderer.showDmOnlyMeasurements = false
        // Restore persisted calibration so the table view reflects the saved settings.
        publishCurrentSettings()
        // Release EventBus subscriptions when the canvas is removed from the scene.
        canvas.sceneProperty().addListener { _, _, newScene ->
            if (newScene == null) renderer.dispose()
        }
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
     * - **Drag** (left button) on the canvas to pan (when no fog tool is active and
     *   the cursor is not over a token).
     * - **Drag** over a token to move it to a new grid cell (publishes [TokenMovedEvent]).
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
        // Restore persisted calibration so the minimap reflects the saved settings.
        publishCurrentSettings()
        // Release EventBus subscriptions when the canvas is removed from the scene.
        minimapCanvas.sceneProperty().addListener { _, _, newScene ->
            if (newScene == null) minimapRenderer.dispose()
        }

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
        var measurementTool: MeasurementTool = MeasurementTool.NONE
        var defaultMirrorToTable = false
        var measurementUnits = "ft"
        var coneAngleDegrees = MeasurementOverlay.DEFAULT_MEASUREMENT_CONE_ANGLE_DEGREES
        val measurements = linkedMapOf<String, MeasurementOverlay>()
        var activeMeasurementId: String? = null
        var measurementUnitsComboBox: ComboBox<String>? = null

        fun setMeasurementUnits(units: String) {
            val normalized = units.trim().lowercase().takeIf { it in supportedMeasurementUnits } ?: "ft"
            measurementUnits = normalized
            val combo = measurementUnitsComboBox
            if (combo != null && combo.selectionModel.selectedItem != normalized) {
                combo.selectionModel.select(normalized)
            }
        }

        fun publishMeasurement(overlay: MeasurementOverlay, isUpdate: Boolean) {
            measurements[overlay.id] = overlay
            if (isUpdate) EventBus.publish(MeasurementUpdatedEvent(overlay)) else EventBus.publish(MeasurementAddedEvent(overlay))
        }

        fun deactivateMeasureButtons(vararg buttons: ToggleButton) {
            buttons.forEach { it.isSelected = false }
        }

        fun findMeasurementAt(cell: Pair<Int, Int>): MeasurementOverlay? =
            measurements.values.lastOrNull { it.isNearCell(cell.first, cell.second) }

        // ------------------------------------------------------------------
        // Mouse drag to pan / fog paint / token drag
        // ------------------------------------------------------------------
        var dragStartX = 0.0
        var dragStartY = 0.0
        var dragStartOffX = 0.0
        var dragStartOffY = 0.0
        /** Token currently being dragged, or `null` when not dragging a token. */
        var draggingToken: Token? = null
        /** Last cell published for the current token drag; used to skip redundant events. */
        var lastDragCell: Pair<Int, Int>? = null

        minimapCanvas.setOnMousePressed { e ->
            if (e.button == MouseButton.PRIMARY) {
                if (measurementTool != MeasurementTool.NONE) {
                    val cell = minimapRenderer.canvasCoordsToGridCell(e.x, e.y)
                    val type = when (measurementTool) {
                        MeasurementTool.LINE -> MeasurementType.LINE
                        MeasurementTool.CONE -> MeasurementType.CONE
                        MeasurementTool.RECTANGLE -> MeasurementType.RECTANGLE
                        MeasurementTool.CIRCLE -> MeasurementType.CIRCLE
                        MeasurementTool.NONE -> return@setOnMousePressed
                    }
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
                    activeMeasurementId = overlay.id
                    publishMeasurement(overlay, isUpdate = false)
                } else if (fogTool != FogTool.NONE) {
                    // Fog painting: determine the clicked cell and publish an event.
                    val cell = minimapRenderer.canvasCoordsToFogCell(e.x, e.y)
                    if (cell != null) {
                        EventBus.publish(
                            FogOfWarCellEvent(cell.first, cell.second, revealed = fogTool == FogTool.ERASE),
                        )
                    }
                } else {
                    // Check if the cursor is over a token — if so, start token drag.
                    val token = minimapRenderer.tokenAtCanvasCoords(e.x, e.y)
                    if (token != null) {
                        draggingToken = token
                    } else {
                        // Pan mode: record the drag start position.
                        dragStartX = e.x
                        dragStartY = e.y
                        dragStartOffX = minimapRenderer.viewportOffsetX
                        dragStartOffY = minimapRenderer.viewportOffsetY
                    }
                }
            } else if (e.button == MouseButton.SECONDARY) {
                val clickedCell = minimapRenderer.canvasCoordsToGridCell(e.x, e.y)
                val selected = findMeasurementAt(clickedCell)
                val actions = mutableListOf(
                    MenuAction(
                        id = "map.measure.units-ft",
                        label = "Units: feet (ft)",
                        section = MenuSection.BASIC,
                        isEnabled = true,
                        isVisible = measurementUnits != "ft",
                        onAction = { setMeasurementUnits("ft") },
                    ),
                    MenuAction(
                        id = "map.measure.units-m",
                        label = "Units: meters (m)",
                        section = MenuSection.BASIC,
                        isEnabled = true,
                        isVisible = measurementUnits != "m",
                        onAction = { setMeasurementUnits("m") },
                    ),
                    MenuAction(
                        id = "map.measure.clear-all",
                        label = "Clear All Measurements",
                        icon = "🗑",
                        section = MenuSection.DANGER_ZONE,
                        isEnabled = measurements.isNotEmpty(),
                        requiresConfirmation = true,
                        confirmationMessage = "Remove all measurements?",
                        onAction = {
                            measurements.clear()
                            EventBus.publish(MeasurementsClearedEvent)
                        },
                    ),
                )
                if (selected != null) {
                    actions += MenuAction(
                        id = "map.measure.toggle-mirror",
                        label = if (selected.mirroredToTable) "Hide from Table" else "Mirror to Table",
                        section = MenuSection.APPEARANCE,
                        onAction = {
                            val updated = selected.copy(mirroredToTable = !selected.mirroredToTable)
                            publishMeasurement(updated, isUpdate = true)
                        },
                    )
                    actions += MenuAction(
                        id = "map.measure.label",
                        label = "Set Label…",
                        icon = "✏️",
                        section = MenuSection.APPEARANCE,
                        onAction = {
                            val dialog = TextInputDialog(selected.unitLabel).apply {
                                title = "Measurement Label"
                                headerText = "Set optional measurement label"
                                contentText = "Label:"
                            }
                            val entered = dialog.showAndWait().orElse(selected.unitLabel)
                            publishMeasurement(selected.copy(unitLabel = entered.trim()), isUpdate = true)
                        },
                    )
                    actions += MenuAction(
                        id = "map.measure.remove",
                        label = "Remove Measurement",
                        icon = "❌",
                        section = MenuSection.DANGER_ZONE,
                        requiresConfirmation = true,
                        confirmationMessage = "Remove selected measurement?",
                        onAction = {
                            measurements.remove(selected.id)
                            EventBus.publish(MeasurementRemovedEvent(selected.id))
                        },
                    )
                }
                ContextMenuRenderer.build(actions).show(minimapCanvas, e.screenX, e.screenY)
            }
        }
        minimapCanvas.setOnMouseDragged { e ->
            if (e.isPrimaryButtonDown) {
                if (measurementTool != MeasurementTool.NONE && activeMeasurementId != null) {
                    val cell = minimapRenderer.canvasCoordsToGridCell(e.x, e.y)
                    val id = activeMeasurementId ?: return@setOnMouseDragged
                    val current = measurements[id] ?: return@setOnMouseDragged
                    publishMeasurement(
                        current.copy(
                            endCol = cell.first,
                            endRow = cell.second,
                            coneAngleDegrees = coneAngleDegrees,
                        ),
                        isUpdate = true,
                    )
                } else if (fogTool != FogTool.NONE) {
                    // Fog painting: paint every cell the mouse passes over.
                    val cell = minimapRenderer.canvasCoordsToFogCell(e.x, e.y)
                    if (cell != null) {
                        EventBus.publish(
                            FogOfWarCellEvent(cell.first, cell.second, revealed = fogTool == FogTool.ERASE),
                        )
                    }
                } else if (draggingToken != null) {
                    // Token drag: move token to the grid cell under the cursor.
                    // Skip publish if the cursor is still in the same cell to avoid
                    // redundant redraws on every pixel of mouse movement.
                    val cell = minimapRenderer.canvasCoordsToGridCell(e.x, e.y)
                    if (cell != lastDragCell) {
                        lastDragCell = cell
                        EventBus.publish(TokenMovedEvent(draggingToken!!.id, draggingToken!!.name, cell.first, cell.second))
                    }
                } else {
                    minimapRenderer.viewportOffsetX = dragStartOffX + (e.x - dragStartX)
                    minimapRenderer.viewportOffsetY = dragStartOffY + (e.y - dragStartY)
                    minimapRenderer.redraw()
                }
            }
        }
        minimapCanvas.setOnMouseReleased { e ->
            if (e.button == MouseButton.PRIMARY) {
                draggingToken = null
                lastDragCell = null
                activeMeasurementId = null
            }
        }
        // Prevent the parent DM pane context menu from opening after a minimap
        // right-click; the minimap provides its own context menu actions.
        minimapCanvas.setOnContextMenuRequested { event ->
            event.consume()
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
        val lineMeasureBtn = ToggleButton("Line").apply {
            tooltip = Tooltip("Measurement tool: line")
        }
        val coneMeasureBtn = ToggleButton("Cone").apply {
            tooltip = Tooltip("Measurement tool: cone")
        }
        val rectMeasureBtn = ToggleButton("Rect").apply {
            tooltip = Tooltip("Measurement tool: rectangle")
        }
        val circleMeasureBtn = ToggleButton("Circle").apply {
            tooltip = Tooltip("Measurement tool: circle")
        }
        val mirrorCheck = CheckBox("Mirror").apply {
            isSelected = defaultMirrorToTable
            tooltip = Tooltip(
                "When enabled, newly created measurements are also shown on the table screen.\n" +
                    "This default applies to the current app session.",
            )
            setOnAction { defaultMirrorToTable = isSelected }
        }
        val unitsBox = ComboBox<String>().apply {
            // Keep the unit selector intentionally narrow for now: these two options
            // are reflected directly in measurement dimension labels.
            items.addAll("ft", "m")
            selectionModel.select(measurementUnits)
            tooltip = Tooltip("Units for measurement labels")
            setOnAction {
                setMeasurementUnits(value ?: "ft")
            }
        }
        measurementUnitsComboBox = unitsBox
        val coneAngleBox = ComboBox<String>().apply {
            // Common tabletop cone templates (15°–120°) offered as quick presets.
            items.addAll("15°", "30°", "45°", "60°", "90°", "120°")
            val defaultConeAngleText = "${MeasurementOverlay.DEFAULT_MEASUREMENT_CONE_ANGLE_DEGREES.toInt()}°"
            val angleText = "${coneAngleDegrees.toInt()}°"
            if (!items.contains(angleText)) {
                items.add(angleText)
            }
            selectionModel.select(angleText)
            tooltip = Tooltip("Cone angle for cone measurements")
            setOnAction {
                val parsed = (value ?: defaultConeAngleText).removeSuffix("°").toDoubleOrNull()
                if (parsed != null) {
                    coneAngleDegrees = parsed
                }
            }
        }

        fun deactivateMeasurementTool() {
            measurementTool = MeasurementTool.NONE
            deactivateMeasureButtons(lineMeasureBtn, coneMeasureBtn, rectMeasureBtn, circleMeasureBtn)
        }

        fun activateMeasureTool(tool: MeasurementTool, button: ToggleButton) {
            if (button.isSelected) {
                drawFogBtn.isSelected = false
                eraseFogBtn.isSelected = false
                fogTool = FogTool.NONE
                deactivateMeasurementTool()
                button.isSelected = true
                measurementTool = tool
            } else {
                deactivateMeasurementTool()
            }
        }

        drawFogBtn.setOnAction {
            if (drawFogBtn.isSelected) {
                deactivateMeasurementTool()
                eraseFogBtn.isSelected = false
                fogTool = FogTool.DRAW
                ensureFogInitialized()
            } else {
                fogTool = FogTool.NONE
            }
        }
        eraseFogBtn.setOnAction {
            if (eraseFogBtn.isSelected) {
                deactivateMeasurementTool()
                drawFogBtn.isSelected = false
                fogTool = FogTool.ERASE
                ensureFogInitialized()
            } else {
                fogTool = FogTool.NONE
            }
        }
        lineMeasureBtn.setOnAction { activateMeasureTool(MeasurementTool.LINE, lineMeasureBtn) }
        coneMeasureBtn.setOnAction { activateMeasureTool(MeasurementTool.CONE, coneMeasureBtn) }
        rectMeasureBtn.setOnAction { activateMeasureTool(MeasurementTool.RECTANGLE, rectMeasureBtn) }
        circleMeasureBtn.setOnAction { activateMeasureTool(MeasurementTool.CIRCLE, circleMeasureBtn) }

        val controlsRow = HBox(
            4.0,
            zoomOutBtn, zoomInBtn, resetBtn,
            Label("  "),
            panLeft, panUp, panDown, panRight,
            Label("  "),
            drawFogBtn, eraseFogBtn,
            Label("  "),
            Label("Measure:"),
            lineMeasureBtn, coneMeasureBtn, rectMeasureBtn, circleMeasureBtn,
            Label("Units:"), unitsBox,
            Label("Cone:"), coneAngleBox,
            mirrorCheck,
        )

        val section = VBox(4.0, canvasPane, controlsRow)
        VBox.setVgrow(canvasPane, Priority.ALWAYS)

        // Eagerly initialize the fog grid so both the table view and the DM minimap
        // display fog as soon as the DM panel is shown, without requiring any button press.
        ensureFogInitialized()

        return section
    }

    // -------------------------------------------------------------------------
    // Compact controls toolbar
    // -------------------------------------------------------------------------

    /**
     * Builds the compact two-row controls strip shown below the minimap.
     *
     * **Row 1 — Map image:**
     * `[Load Map…]  [path readout (grows)]  [Calibrate Map…]  [Guided Calibration…]  [↺ 90°]  [↻ 90°]  BG: [■]`
     *
     * **Row 2 — Grid & Fog of war:**
     * `[☐ Show grid]  Grid: [■]  [Apply Grid]  [Calibrate Grid…]  │  [Reveal All]  [Hide All]`
     *
     * The background colour picker (BG) applies its colour immediately via [MapBackgroundEvent]
     * and also auto-suggests a contrasting grid line colour in the Grid picker.
     * The grid colour picker value is applied when the DM clicks [Apply Grid].
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
                    val uri = file.toURI().toString()
                    EventBus.publish(MapLoadEvent(uri))
                    // Track the URI after publishing, once the load is initiated.
                    currentMapImageUri = uri
                }
            }
        }

        val calibrateMapBtn = Button("Calibrate Map…").apply {
            tooltip = Tooltip("Adjust map image scale and centre position")
            setOnAction { e ->
                showMapCalibrationDialog((e.source as? Button)?.scene?.window)
            }
        }

        val guidedCalibrationBtn = Button("Guided Calibration…").apply {
            tooltip = Tooltip(
                "Two-step interactive calibration: click the grid centre on the map, " +
                    "then click an adjacent tile corner to align scale",
            )
            setOnAction { e ->
                showGuidedCalibrationDialog((e.source as? Button)?.scene?.window)
            }
        }

        val rotateCCWBtn = Button("↺ 90°").apply {
            tooltip = Tooltip("Rotate map image 90° counter-clockwise")
            setOnAction {
                lastMapRotation = (lastMapRotation - 90 + 360) % 360
                EventBus.publish(MapRotationEvent(lastMapRotation))
                MapSettingsSerializer.save(lastGridCalibration, lastMapCalibration, lastGridColor, lastBackgroundColor, lastMapRotation)
            }
        }

        val rotateCWBtn = Button("↻ 90°").apply {
            tooltip = Tooltip("Rotate map image 90° clockwise")
            setOnAction {
                lastMapRotation = (lastMapRotation + 90) % 360
                EventBus.publish(MapRotationEvent(lastMapRotation))
                MapSettingsSerializer.save(lastGridCalibration, lastMapCalibration, lastGridColor, lastBackgroundColor, lastMapRotation)
            }
        }

        // --- Row 2: Grid + Fog of war ---
        val visibleCheck = CheckBox("Show Grid").apply {
            isSelected = false
            tooltip = Tooltip("Toggle grid overlay visibility")
        }

        // Grid colour picker — pre-filled with the last saved colour.
        val gridColorPicker = ColorPicker(lastGridColor).apply {
            prefWidth = 80.0
            tooltip = Tooltip(
                "Grid line colour — applied when you click Apply Grid.\n" +
                    "Updated automatically to contrast with the background colour.",
            )
        }

        val applyGridBtn = Button("Apply Grid").apply {
            tooltip = Tooltip("Publish the current grid visibility and colour settings")
            setOnAction {
                lastGridColor = gridColorPicker.value
                val config = if (!visibleCheck.isSelected) null else GridConfig(color = lastGridColor)
                currentGridConfig = config
                EventBus.publish(GridUpdateEvent(config))
                MapSettingsSerializer.save(lastGridCalibration, lastMapCalibration, lastGridColor, lastBackgroundColor, lastMapRotation)
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

        val showNamesCheck = CheckBox("Show Names").apply {
            isSelected = lastShowTokenNames
            tooltip = Tooltip("Show token names on the map view so players can identify each combatant")
            setOnAction {
                lastShowTokenNames = isSelected
                EventBus.publish(ShowTokenNamesEvent(isSelected))
            }
        }

        // Background colour picker — applies immediately and auto-suggests a
        // contrasting grid colour in gridColorPicker.
        val bgColorPicker = ColorPicker(lastBackgroundColor).apply {
            prefWidth = 80.0
            tooltip = Tooltip(
                "Plain-colour background — replaces the default black fill.\n" +
                    "When no map image is loaded this is the sole visible background.\n" +
                    "Changing this colour auto-suggests a contrasting grid line colour.",
            )
            setOnAction {
                lastBackgroundColor = value
                // Auto-suggest a contrasting grid colour and persist the suggestion
                // so that it is visible in the picker on the next application launch.
                // The user can still override by selecting a different grid colour
                // before clicking Apply Grid.
                val suggestedGridColor = contrastingGridColor(lastBackgroundColor)
                gridColorPicker.value = suggestedGridColor
                lastGridColor = suggestedGridColor
                EventBus.publish(MapBackgroundEvent(lastBackgroundColor))
                MapSettingsSerializer.save(lastGridCalibration, lastMapCalibration, lastGridColor, lastBackgroundColor, lastMapRotation)
            }
        }

        val bgSep = Separator(Orientation.VERTICAL)
        val mapRow = HBox(4.0, loadBtn, pathField, calibrateMapBtn, guidedCalibrationBtn, rotateCCWBtn, rotateCWBtn, bgSep, Label("BG:"), bgColorPicker)

        val fowSep = Separator(Orientation.VERTICAL)
        val tokenSep = Separator(Orientation.VERTICAL)
        val gridFowRow = HBox(4.0, visibleCheck, Label("Grid:"), gridColorPicker, applyGridBtn, calibrateGridBtn, fowSep, revealAllBtn, hideAllBtn, tokenSep, showNamesCheck)

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
                        MapSettingsSerializer.save(lastGridCalibration, lastMapCalibration, lastGridColor, lastBackgroundColor, lastMapRotation)
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
                        MapSettingsSerializer.save(lastGridCalibration, lastMapCalibration, lastGridColor, lastBackgroundColor, lastMapRotation)
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

    /**
     * Opens the two-step guided map calibration dialog.
     *
     * The dialog presents a live canvas preview of the current map and grid.  The DM
     * follows two interactive steps to align the map image with the overlay grid:
     *
     * 1. **Select grid centre** — click the point on the map image that represents the
     *    grid origin.  The map is immediately translated so that point moves to the
     *    canvas centre (marked by the red dot and yellow crosshair).
     * 2. **Select adjacent tile corner** — click the corner of a tile that is directly
     *    adjacent to the centre (one cell away horizontally, vertically, or diagonally).
     *    The map is scaled so that the distance from the canvas centre to the clicked
     *    corner equals exactly one grid cell, perfectly aligning map and overlay grid.
     *
     * **Pan and zoom** — drag the canvas with the left mouse button to pan the view;
     * scroll the mouse wheel to zoom in/out.  Compact zoom/pan buttons are also
     * provided below the canvas.  A drag gesture longer than [clickThresholdPx]
     * pixels is treated as a pan and does not trigger a calibration step.
     *
     * **Skipping steps** — each step has a *Skip this step →* button.  Skipping
     * Step 1 keeps the current translation and advances to Step 2; skipping Step 2
     * keeps the current scale and enables Apply.  Either or both steps may be skipped,
     * which is useful when one dimension is already well aligned.
     *
     * **Going back** — a *← Back to Step 1* button (enabled in Step 2) discards the
     * Step 1 translation and restores the calibration to the state when the dialog
     * opened, so the DM can restart the translation step without closing and reopening
     * the dialog.
     *
     * **Grid corner dots** — during calibration the canvas also draws a small red dot
     * at every grid line intersection (via [MapRenderer.drawGridCornerDots]).  At the
     * default viewport zoom each dot is about 3 px in diameter.  Zooming in reveals
     * fine misalignments at the canvas edges that would otherwise be hard to spot.
     *
     * Changes are previewed live on all renderers via [MapCalibrationEvent].
     * Clicking **Apply** confirms both steps; **Cancel** (or closing the dialog)
     * restores the calibration that was active when the dialog opened.
     *
     * @param owner optional owner window for modality.
     */
    private fun showGuidedCalibrationDialog(owner: Window?) {
        val saved = lastMapCalibration
        var working = saved
        var step1Cal: MapCalibration? = null
        var step = 1
        var confirmed = false

        val dialog = Dialog<ButtonType>().apply {
            title = "Guided Map Calibration"
            headerText = null
            initOwner(owner)
        }

        val stepLabel = Label("Step 1 of 2: Select the grid centre").apply {
            style = "-fx-font-weight: bold;"
        }
        val instructionLabel = Label(
            "Click on the point on the map that represents the grid centre.\n" +
                "The map will translate so that point aligns with the canvas centre\n" +
                "(marked by the red dot and yellow crosshair).\n" +
                "Drag to pan · Scroll to zoom · Use the buttons below to fine-tune the view.\n" +
                "If the centre is already aligned, use \"Skip this step \u2192\" to proceed.",
        ).apply {
            isWrapText = true
            prefWidth = 580.0
        }

        // Build a canvas with a MapRenderer initialised from the current plugin state.
        val mapCanvas = Canvas()
        val dialogRenderer = MapRenderer(mapCanvas)
        dialogRenderer.mapCalibration = working
        dialogRenderer.gridCalibration = lastGridCalibration
        dialogRenderer.gridConfig = currentGridConfig
        dialogRenderer.backgroundColor = lastBackgroundColor
        currentMapImageUri?.let { dialogRenderer.loadImage(it) }

        // Release EventBus subscriptions when the canvas leaves the dialog scene.
        mapCanvas.sceneProperty().addListener { _, _, newScene ->
            if (newScene == null) dialogRenderer.dispose()
        }

        // A Pane that keeps the canvas sized to fill its layout bounds.
        val canvasPane = object : Pane() {
            init {
                children.add(mapCanvas)
                minWidth = 400.0
                minHeight = 280.0
                prefWidth = 600.0
                prefHeight = 400.0
            }

            override fun layoutChildren() {
                if (mapCanvas.width != width || mapCanvas.height != height) {
                    mapCanvas.width = width
                    mapCanvas.height = height
                    dialogRenderer.redraw()
                }
            }
        }

        // Show calibration overlays (red dot + yellow crosshair at canvas centre).
        EventBus.publish(MapCalibrationModeEvent(active = true))
        EventBus.publish(GridCalibrationModeEvent(active = true))

        // Register dialog buttons early so lookupButton() works before content is set.
        dialog.dialogPane.buttonTypes.addAll(ButtonType.APPLY, ButtonType.CANCEL)
        dialog.dialogPane.prefWidth = 640.0

        // Apply is disabled until Step 2 is completed (or skipped).
        val applyButton = dialog.dialogPane.lookupButton(ButtonType.APPLY)
        applyButton.isDisable = true

        // Back button — reverts to the pre-dialog calibration and restarts Step 1.
        val backButton = Button("\u2190 Back to Step 1").apply {
            tooltip = Tooltip(
                "Discard the Step 1 translation and restart from the saved calibration.",
            )
            isDisable = true  // only enabled in Step 2
        }

        // Skip button — advances the wizard without applying the current step's change.
        val skipButton = Button("Skip this step \u2192").apply {
            tooltip = Tooltip(
                "Skip this step and keep the current calibration for it.\n" +
                    "Useful when one axis is already aligned.",
            )
        }
        // Navigation row: back on the left, skip on the right.
        val navRow = HBox().also { row ->
            val spacer = Region()
            HBox.setHgrow(spacer, Priority.ALWAYS)
            row.children.addAll(backButton, spacer, skipButton)
        }

        // ------------------------------------------------------------------
        // Viewport pan/zoom constants (mirror the minimap's values).
        // ------------------------------------------------------------------
        val zoomFactor = 1.25
        val minScale = 0.125
        val maxScale = 8.0
        val panStep = 20.0

        /** Resets the dialog-canvas viewport to the default 1:1, centred view. */
        fun resetViewport() {
            dialogRenderer.viewportScale = 1.0
            dialogRenderer.viewportOffsetX = 0.0
            dialogRenderer.viewportOffsetY = 0.0
            dialogRenderer.redraw()
        }

        /**
         * Converts a canvas-space mouse position to world space, accounting for
         * the current viewport transform.  The calibration functions work in
         * world space (canvas coords with identity viewport).
         */
        fun canvasToWorld(canvasX: Double, canvasY: Double): Pair<Double, Double> {
            val cx = mapCanvas.width / 2.0
            val cy = mapCanvas.height / 2.0
            val worldX = (canvasX - cx - dialogRenderer.viewportOffsetX) / dialogRenderer.viewportScale + cx
            val worldY = (canvasY - cy - dialogRenderer.viewportOffsetY) / dialogRenderer.viewportScale + cy
            return Pair(worldX, worldY)
        }

        // ------------------------------------------------------------------
        // Pan drag state — used to distinguish a click from a pan gesture.
        // ------------------------------------------------------------------
        /** Canvas pixels the pointer must move before the gesture is treated as a pan. */
        val clickThresholdPx = 5.0
        var panDragStartX = 0.0
        var panDragStartY = 0.0
        var panDragStartOffX = 0.0
        var panDragStartOffY = 0.0
        var panDragDistance = 0.0

        /** Advances the wizard to Step 2 without changing the current translation. */
        fun advanceToStep2() {
            // Treat the current working calibration as the Step 1 result so that
            // Step 2 canvas clicks have a valid base calibration to work from.
            step1Cal = working
            step = 2
            backButton.isDisable = false
            stepLabel.text = "Step 2 of 2: Select an adjacent tile corner"
            instructionLabel.text =
                "Click on the corner of a tile that is directly adjacent to the\n" +
                    "centre point — one grid cell away (left/right/up/down or diagonally).\n" +
                    "The grid lines show where tile corners will be after calibration.\n" +
                    "Drag to pan · Scroll to zoom · Use the buttons below to fine-tune the view.\n" +
                    "If the scale is already aligned, use \"Skip this step \u2192\" to proceed."
        }

        skipButton.setOnAction {
            when (step) {
                1 -> advanceToStep2()
                2 -> applyButton.isDisable = false
            }
        }

        backButton.setOnAction {
            // Restore the pre-dialog calibration, clear the Step 1 result, and
            // reset the wizard to Step 1 so the DM can pick a new grid centre.
            working = saved
            step1Cal = null
            step = 1
            applyButton.isDisable = true
            backButton.isDisable = true
            stepLabel.text = "Step 1 of 2: Select the grid centre"
            instructionLabel.text =
                "Click on the point on the map that represents the grid centre.\n" +
                    "The map will translate so that point aligns with the canvas centre\n" +
                    "(marked by the red dot and yellow crosshair).\n" +
                    "Drag to pan · Scroll to zoom · Use the buttons below to fine-tune the view.\n" +
                    "If the centre is already aligned, use \"Skip this step \u2192\" to proceed."
            EventBus.publish(MapCalibrationEvent(working))
        }

        mapCanvas.setOnMousePressed { e ->
            if (e.button == MouseButton.PRIMARY) {
                panDragStartX = e.x
                panDragStartY = e.y
                panDragStartOffX = dialogRenderer.viewportOffsetX
                panDragStartOffY = dialogRenderer.viewportOffsetY
                panDragDistance = 0.0
            }
        }

        mapCanvas.setOnMouseDragged { e ->
            if (e.isPrimaryButtonDown) {
                val dx = e.x - panDragStartX
                val dy = e.y - panDragStartY
                panDragDistance = kotlin.math.hypot(dx, dy)
                dialogRenderer.viewportOffsetX = panDragStartOffX + dx
                dialogRenderer.viewportOffsetY = panDragStartOffY + dy
                dialogRenderer.redraw()
            }
        }

        mapCanvas.setOnMouseReleased { e ->
            if (e.button == MouseButton.PRIMARY && panDragDistance < clickThresholdPx) {
                // Short release with minimal movement: treat as a calibration click.
                val cx = mapCanvas.width / 2.0
                val cy = mapCanvas.height / 2.0
                val (worldX, worldY) = canvasToWorld(e.x, e.y)
                when (step) {
                    1 -> {
                        working = guidedCalibrationStep1(working, worldX, worldY, cx, cy)
                        EventBus.publish(MapCalibrationEvent(working))
                        advanceToStep2()
                    }
                    2 -> {
                        val s1 = step1Cal ?: return@setOnMouseReleased
                        val cellPx = lastGridCalibration.effectiveCellSizeInPixels()
                        working = guidedCalibrationStep2(s1, worldX, worldY, cx, cy, cellPx)
                            ?: return@setOnMouseReleased
                        EventBus.publish(MapCalibrationEvent(working))
                        applyButton.isDisable = false
                    }
                }
            }
        }

        // ------------------------------------------------------------------
        // Scroll wheel to zoom from the canvas centre.
        // ------------------------------------------------------------------
        mapCanvas.setOnScroll { e ->
            val factor = if (e.deltaY > 0) zoomFactor else 1.0 / zoomFactor
            dialogRenderer.viewportScale =
                (dialogRenderer.viewportScale * factor).coerceIn(minScale, maxScale)
            dialogRenderer.redraw()
        }

        // ------------------------------------------------------------------
        // Viewport control buttons (zoom +/−, reset, and pan arrows).
        // ------------------------------------------------------------------
        val zoomOutBtn = Button("−").apply {
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            tooltip = Tooltip("Zoom out")
            setOnAction {
                dialogRenderer.viewportScale =
                    (dialogRenderer.viewportScale / zoomFactor).coerceAtLeast(minScale)
                dialogRenderer.redraw()
            }
        }
        val zoomInBtn = Button("+").apply {
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            tooltip = Tooltip("Zoom in")
            setOnAction {
                dialogRenderer.viewportScale =
                    (dialogRenderer.viewportScale * zoomFactor).coerceAtMost(maxScale)
                dialogRenderer.redraw()
            }
        }
        val resetViewBtn = Button("Reset View").apply {
            tooltip = Tooltip("Reset zoom and pan to default")
            setOnAction { resetViewport() }
        }
        val panLeftBtn = Button("◀").apply {
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            tooltip = Tooltip("Pan view left")
            setOnAction { dialogRenderer.viewportOffsetX -= panStep; dialogRenderer.redraw() }
        }
        val panUpBtn = Button("▲").apply {
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            tooltip = Tooltip("Pan view up")
            setOnAction { dialogRenderer.viewportOffsetY -= panStep; dialogRenderer.redraw() }
        }
        val panDownBtn = Button("▼").apply {
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            tooltip = Tooltip("Pan view down")
            setOnAction { dialogRenderer.viewportOffsetY += panStep; dialogRenderer.redraw() }
        }
        val panRightBtn = Button("▶").apply {
            style = "-fx-min-width: 28px; -fx-max-width: 28px;"
            tooltip = Tooltip("Pan view right")
            setOnAction { dialogRenderer.viewportOffsetX += panStep; dialogRenderer.redraw() }
        }

        val viewControlsRow = HBox(
            4.0,
            zoomOutBtn, zoomInBtn, resetViewBtn,
            Label("  "),
            panLeftBtn, panUpBtn, panDownBtn, panRightBtn,
        )

        dialog.dialogPane.content = VBox(10.0, stepLabel, instructionLabel, navRow, canvasPane, viewControlsRow)

        applyButton
            .addEventFilter(ActionEvent.ACTION) {
                lastMapCalibration = working
                EventBus.publish(MapCalibrationEvent(lastMapCalibration))
                MapSettingsSerializer.save(lastGridCalibration, lastMapCalibration, lastGridColor, lastBackgroundColor, lastMapRotation)
                confirmed = true
            }

        dialog.setOnHidden {
            EventBus.publish(MapCalibrationModeEvent(active = false))
            EventBus.publish(GridCalibrationModeEvent(active = false))
            if (!confirmed) {
                lastMapCalibration = saved
                EventBus.publish(MapCalibrationEvent(saved))
            }
        }

        dialog.showAndWait()
    }
}
