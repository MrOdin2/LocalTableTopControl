package com.tabletopcontrol.map

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.EventBus
import javafx.event.ActionEvent
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import javafx.stage.Window
import kotlin.math.floor

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
     * The central element is a [Canvas] minimap that mirrors the table screen at a
     * reduced scale.  Each control publishes an event on the [EventBus]; the
     * [MapRenderer] instances (table view and minimap) subscribe to those events.
     */
    override fun createView(): Node {
        val vbox = VBox(8.0).apply { padding = Insets(10.0) }

        vbox.children.addAll(
            Label("Map Controls"),
            Separator(),
            buildMinimapSection(),
            Separator(),
            buildLoadSection(),
            Separator(),
            buildMapCalibrationSection(),
            Separator(),
            buildGridSection(),
            Separator(),
            buildFogOfWarSection(),
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
     * A [MapRenderer] is created for the minimap canvas and automatically subscribes
     * to all map events, so the preview stays in sync with the table screen.
     */
    private fun buildMinimapSection(): VBox {
        val minimapCanvas = Canvas(320.0, 180.0).apply {
            style = "-fx-border-color: gray;"
        }
        // The renderer subscribes to all map events in its init block.
        MapRenderer(minimapCanvas)
        return VBox(4.0, Label("Preview"), minimapCanvas)
    }

    /** Builds the "Load map" control row. */
    private fun buildLoadSection(): VBox {
        val pathField = TextField().apply {
            isEditable = false
            promptText = "No map loaded"
            tooltip = Tooltip("Path to the currently loaded map image")
        }

        val loadBtn = Button("Load map…").apply {
            tooltip = Tooltip("Open a map image file")
            setOnAction {
                val chooser = FileChooser().apply {
                    title = "Select map image"
                    extensionFilters.addAll(
                        FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
                        FileChooser.ExtensionFilter("All files", "*.*"),
                    )
                }
                // Use the button's own window as owner so the dialog is modal to the DM panel.
                val owner = (it.source as? Button)?.scene?.window
                val file = chooser.showOpenDialog(owner)
                if (file != null) {
                    pathField.text = file.absolutePath
                    EventBus.publish(MapLoadEvent(file.toURI().toString()))
                }
            }
        }

        return VBox(4.0, Label("Map image"), loadBtn, pathField)
    }

    /**
     * Builds the map-calibration section.
     *
     * Instead of inline fields, calibration is handled through a pop-up dialog so
     * the DM can focus on aligning the map image while the live table view (or
     * minimap) shows the calibration overlay.
     */
    private fun buildMapCalibrationSection(): VBox {
        val calibrateBtn = Button("Calibrate Map…").apply {
            tooltip = Tooltip("Adjust map image scale and centre position")
            setOnAction {
                val owner = (it.source as? Button)?.scene?.window
                showMapCalibrationDialog(owner)
            }
        }
        return VBox(4.0, Label("Map Calibration"), calibrateBtn)
    }

    /** Builds the grid overlay control section. */
    private fun buildGridSection(): VBox {
        val visibleCheck = CheckBox("Show grid").apply { isSelected = false }

        val applyBtn = Button("Apply grid").apply {
            setOnAction {
                if (!visibleCheck.isSelected) {
                    EventBus.publish(GridUpdateEvent(null))
                } else {
                    EventBus.publish(GridUpdateEvent(GridConfig()))
                }
            }
        }

        val calibrateBtn = Button("Calibrate Grid…").apply {
            tooltip = Tooltip("Adjust grid cell size and centre position")
            setOnAction {
                val owner = (it.source as? Button)?.scene?.window
                showGridCalibrationDialog(owner)
            }
        }

        return VBox(4.0, Label("Grid"), visibleCheck, applyBtn, calibrateBtn)
    }

    /** Builds the fog-of-war control section. */
    private fun buildFogOfWarSection(): VBox {
        val revealAllBtn = Button("Reveal all").apply {
            tooltip = Tooltip("Remove fog from the entire map")
            setOnAction { EventBus.publish(FogOfWarResetEvent(revealAll = true)) }
        }

        val hideAllBtn = Button("Hide all").apply {
            tooltip = Tooltip("Cover the entire map with fog")
            setOnAction { EventBus.publish(FogOfWarResetEvent(revealAll = false)) }
        }

        return VBox(4.0, Label("Fog of war"), revealAllBtn, hideAllBtn)
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
