package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.ui.color.ColorEditorDialog
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.light.LightEffect
import com.tabletopcontrol.light.LightOperationResult
import com.tabletopcontrol.light.WledSerialSender
import com.tabletopcontrol.light.ui.LightOperatorFeedbackPresenter
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.ReadOnlyStringWrapper
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.ContextMenu
import javafx.scene.control.CustomMenuItem
import javafx.scene.control.Label
import javafx.scene.control.MenuButton
import javafx.scene.control.MenuItem
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.control.Slider
import javafx.scene.control.TableCell
import javafx.scene.control.TableColumn
import javafx.scene.control.TableRow
import javafx.scene.control.TableView
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.TextInputDialog
import javafx.scene.control.ToggleButton
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.util.StringConverter
import kotlin.math.roundToInt

class AdvancedLightPlugin : DmPlugin {
    override val displayName: String = "Advanced Light"

    private val controller = AdvancedLightController()
    private val serialCoordinator = AdvancedLightSerialCoordinator()
    private val feedback = LightOperatorFeedbackPresenter()
    private val disposables = mutableListOf<() -> Unit>()

    override fun createView(): Node {
        disposeViewListeners()

        val rows = FXCollections.observableArrayList<AdvancedLightSegmentState>()
        var refreshEditorCallback: () -> Unit = {}
        val table = buildSegmentTable(rows) { refreshEditorCallback() }
        val statusLabel = Label()
        val editorContext = buildEditorControls(table, rows)
        refreshEditorCallback = editorContext.refresh
        val connectionPanel = buildConnectionPanel(table, rows, statusLabel) { editorContext.refresh() }
        val debugPanel = buildDebugPanel()

        fun refreshRows() {
            rows.setAll(controller.currentSegments())
            table.refresh()
            editorContext.refresh()
        }
        refreshRows()

        val root = VBox(8.0).apply {
            padding = Insets(10.0)
            children.addAll(
                HBox(8.0, Label("Advanced WLED Segments"), connectionPanel.toggle).apply {
                    alignment = Pos.CENTER_LEFT
                    HBox.setHgrow(children.first(), Priority.ALWAYS)
                },
                connectionPanel.panel,
                statusLabel,
                table,
                Separator(),
                editorContext.node,
                Separator(),
                debugPanel,
            )
        }

        return ScrollPane(root).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        }
    }

    override fun onShutdown() {
        controller.currentSegments().takeIf { it.isNotEmpty() }?.let(AdvancedLightPreferencesStore::save)
        disposeViewListeners()
        serialCoordinator.shutdown()
    }

    private fun buildSegmentTable(
        rows: javafx.collections.ObservableList<AdvancedLightSegmentState>,
        onRowsChanged: () -> Unit,
    ): TableView<AdvancedLightSegmentState> {
        val table = TableView(rows).apply {
            columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
            placeholder = Label("Connect to WLED to load segments.")
            fixedCellSize = SEGMENT_TABLE_ROW_HEIGHT
            prefHeight = segmentTableHeight(0)
            maxHeight = Region.USE_PREF_SIZE
        }

        val nameColumn = TableColumn<AdvancedLightSegmentState, String>("Name").apply {
            setCellValueFactory { ReadOnlyStringWrapper(it.value.name) }
            minWidth = 0.0
            prefWidth = 70.0
        }
        val powerColumn = TableColumn<AdvancedLightSegmentState, AdvancedLightSegmentState>("On").apply {
            setCellValueFactory { ReadOnlyObjectWrapper(it.value) }
            minWidth = 44.0
            prefWidth = 54.0
            maxWidth = 64.0
            setCellFactory {
                object : TableCell<AdvancedLightSegmentState, AdvancedLightSegmentState>() {
                    private val check = CheckBox().apply {
                        tooltip = Tooltip("Turn this WLED segment on or off")
                        setOnAction {
                            item?.let { segment ->
                                val commands = controller.setSegmentPower(segment.id, isSelected)
                                serialCoordinator.sendSegmentsAsync(commands)
                                refreshTableFromController(table, rows)
                                onRowsChanged()
                            }
                        }
                    }

                    override fun updateItem(item: AdvancedLightSegmentState?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (empty || item == null) {
                            graphic = null
                        } else {
                            check.isSelected = item.on
                            graphic = HBox(check).apply { alignment = Pos.CENTER }
                        }
                    }
                }
            }
        }
        val selectedColumn = TableColumn<AdvancedLightSegmentState, AdvancedLightSegmentState>("Edit").apply {
            setCellValueFactory { ReadOnlyObjectWrapper(it.value) }
            minWidth = 48.0
            prefWidth = 58.0
            maxWidth = 68.0
            setCellFactory {
                object : TableCell<AdvancedLightSegmentState, AdvancedLightSegmentState>() {
                    private val check = CheckBox().apply {
                        tooltip = Tooltip("Include this segment when using the editor controls")
                        setOnAction {
                            item?.let { segment ->
                                controller.setSelectedForEdit(segment.id, isSelected)
                                refreshTableFromController(table, rows)
                                onRowsChanged()
                            }
                        }
                    }

                    override fun updateItem(item: AdvancedLightSegmentState?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (empty || item == null) {
                            graphic = null
                        } else {
                            check.isSelected = item.selectedForEdit
                            graphic = HBox(check).apply { alignment = Pos.CENTER }
                        }
                    }
                }
            }
        }

        table.columns.addAll(nameColumn, powerColumn, selectedColumn)
        table.rowFactory = javafx.util.Callback<TableView<AdvancedLightSegmentState>, TableRow<AdvancedLightSegmentState>> {
            TableRow<AdvancedLightSegmentState>().apply {
                itemProperty().addListener { _, _, segment ->
                    contextMenu = segment?.let { buildRowContextMenu(table, rows, it, onRowsChanged) }
                }
            }
        }
        return table
    }

    private fun buildRowContextMenu(
        table: TableView<AdvancedLightSegmentState>,
        rows: javafx.collections.ObservableList<AdvancedLightSegmentState>,
        segment: AdvancedLightSegmentState,
        onRowsChanged: () -> Unit,
    ): ContextMenu {
        val rename = MenuItem("Rename").apply {
            setOnAction {
                val dialog = TextInputDialog(segment.name).apply {
                    title = "Rename Segment"
                    headerText = "Segment ${segment.id}"
                    contentText = "Name:"
                    table.scene?.window?.let(::initOwner)
                }
                val result = dialog.showAndWait()
                if (result.isPresent) {
                    controller.renameSegment(segment.id, result.get())
                    refreshTableFromController(table, rows)
                    onRowsChanged()
                }
            }
        }
        val brightness = MenuItem("Brightness: ${percentLabel(segment.brightnessScale)}...").apply {
            setOnAction {
                val dialog = TextInputDialog((segment.brightnessScale * 100.0).roundToInt().toString()).apply {
                    title = "Segment Brightness"
                    headerText = segment.name
                    contentText = "Brightness (0-100%):"
                    table.scene?.window?.let(::initOwner)
                }
                val result = dialog.showAndWait()
                if (result.isPresent) {
                    val percent = result.get().trim().replace("%", "").toDoubleOrNull()
                    if (percent != null) {
                        val commands = controller.setSegmentBrightnessScale(
                            segment.id,
                            percent.coerceIn(0.0, 100.0) / 100.0,
                        )
                        serialCoordinator.sendSegmentsAsync(commands)
                        refreshTableFromController(table, rows)
                        onRowsChanged()
                    }
                }
            }
        }
        val moveUp = MenuItem("Move Up").apply {
            isDisable = rows.indexOf(segment) <= 0
            setOnAction {
                controller.moveSegment(segment.id, -1)
                refreshTableFromController(table, rows)
                onRowsChanged()
            }
        }
        val moveDown = MenuItem("Move Down").apply {
            isDisable = rows.indexOf(segment) >= rows.lastIndex
            setOnAction {
                controller.moveSegment(segment.id, 1)
                refreshTableFromController(table, rows)
                onRowsChanged()
            }
        }
        return ContextMenu(rename, brightness, moveUp, moveDown)
    }

    private fun buildConnectionPanel(
        table: TableView<AdvancedLightSegmentState>,
        rows: javafx.collections.ObservableList<AdvancedLightSegmentState>,
        statusLabel: Label,
        refreshEditor: () -> Unit,
    ): ConnectionPanel {
        val toggle = ToggleButton("Connection ▼").apply {
            tooltip = Tooltip("Show or hide serial connection settings")
        }
        val portCombo = ComboBox<String>().apply {
            tooltip = Tooltip("Select the serial port for the WLED device")
            maxWidth = Double.MAX_VALUE
            isEditable = true
            items.setAll(serialCoordinator.availablePorts())
        }
        val refreshButton = Button("Refresh").apply {
            tooltip = Tooltip("Refresh the list of serial ports")
        }
        val baudField = TextField(WledSerialSender.DEFAULT_BAUD_RATE.toString()).apply {
            prefColumnCount = 8
            tooltip = Tooltip("Serial baud rate")
        }
        val connectButton = Button("Connect").apply {
            tooltip = Tooltip("Connect to WLED and load segments")
            maxWidth = Double.MAX_VALUE
        }

        val panel = VBox(
            6.0,
            HBox(6.0, portCombo, refreshButton).apply {
                alignment = Pos.CENTER_LEFT
                HBox.setHgrow(portCombo, Priority.ALWAYS)
            },
            HBox(6.0, Label("Baud:"), baudField).apply { alignment = Pos.CENTER_LEFT },
            connectButton,
        ).apply {
            isVisible = false
            isManaged = false
        }

        fun syncConnectedState() {
            connectButton.text = if (serialCoordinator.isConnected) "Disconnect" else "Connect"
            val portName = serialCoordinator.connectedPortName
            if (serialCoordinator.isConnected) {
                feedback.showSuccess(statusLabel, portName?.let { "Connected: $it" } ?: "Connected")
            } else {
                feedback.showInfo(statusLabel, "Not connected")
            }
        }

        toggle.selectedProperty().addListener { _, _, selected ->
            panel.isVisible = selected
            panel.isManaged = selected
        }
        refreshButton.setOnAction {
            portCombo.items.setAll(serialCoordinator.availablePorts())
        }
        connectButton.setOnAction {
            if (serialCoordinator.isConnected) {
                connectButton.isDisable = true
                feedback.showInfo(statusLabel, "Disconnecting...")
                serialCoordinator.disconnectAsync {
                    connectButton.isDisable = false
                    syncConnectedState()
                }
                return@setOnAction
            }

            connectButton.isDisable = true
            feedback.showInfo(statusLabel, "Connecting and reading WLED segments...")
            val portName = if (portCombo.isEditable) portCombo.editor.text else portCombo.value
            val preferences = if (controller.currentSegments().isEmpty()) {
                AdvancedLightPreferencesStore.load()
            } else {
                controller.preferencesSnapshot()
            }
            serialCoordinator.connectAndQueryAsync(portName, baudField.text) { result, snapshot ->
                connectButton.isDisable = false
                when {
                    result == LightOperationResult.Applied && snapshot != null -> {
                        controller.loadFromDevice(snapshot, preferences)
                        refreshTableFromController(table, rows)
                        refreshEditor()
                        serialCoordinator.sendSegmentsAsync(
                            controller.commandsForAllSegments(),
                            splitCommands = true,
                        )
                        connectButton.text = "Disconnect"
                        feedback.showSuccess(
                            statusLabel,
                            "Connected: ${snapshot.segments.size} segment(s) loaded",
                        )
                    }
                    result is LightOperationResult.Failure -> {
                        syncConnectedState()
                        feedback.showError(statusLabel, result.operatorMessage)
                        feedback.showFailureDialog(
                            owner = statusLabel.scene?.window,
                            title = "Advanced Light Serial Error",
                            failure = result,
                        )
                    }
                }
            }
        }

        val removeFailureListener = serialCoordinator.addFailureListener { failure ->
            feedback.showError(statusLabel, failure.operatorMessage)
            feedback.showFailureDialog(
                owner = statusLabel.scene?.window,
                title = "Advanced Light Serial Error",
                failure = failure,
            )
        }
        disposables += removeFailureListener
        syncConnectedState()

        return ConnectionPanel(toggle, panel)
    }

    private fun buildEditorControls(
        table: TableView<AdvancedLightSegmentState>,
        rows: javafx.collections.ObservableList<AdvancedLightSegmentState>,
    ): EditorContext {
        var updatingControls = false
        val colorSwatch = Region().apply {
            minWidth = 16.0
            minHeight = 16.0
            prefWidth = 16.0
            prefHeight = 16.0
            style = SWATCH_STYLE_BASE
        }
        val colorLabel = Label("No segment")
        val colorButton = Button().apply {
            tooltip = Tooltip("Choose a color for checked segments")
            graphic = HBox(8.0, colorSwatch, colorLabel).apply { alignment = Pos.CENTER_LEFT }
            text = ""
            maxWidth = Double.MAX_VALUE
        }
        val brightnessLabel = Label()
        val brightnessSlider = Slider(0.0, 100.0, 100.0).apply {
            isShowTickMarks = true
            isShowTickLabels = true
            majorTickUnit = 50.0
            blockIncrement = 5.0
            tooltip = Tooltip("Brightness for checked segments")
            maxWidth = Double.MAX_VALUE
        }
        val effectCombo = ComboBox<LightEffect>().apply {
            items.setAll(*LightEffect.entries.toTypedArray())
            converter = effectConverter()
            tooltip = Tooltip("Effect for checked segments")
            maxWidth = Double.MAX_VALUE
        }
        val speedNameLabel = Label("Speed:")
        val speedValueLabel = Label()
        val speedSlider = Slider(0.0, 100.0, 50.0).apply {
            isShowTickMarks = true
            isShowTickLabels = true
            majorTickUnit = 50.0
            blockIncrement = 5.0
            tooltip = Tooltip("WLED sx parameter for checked segments")
            maxWidth = Double.MAX_VALUE
        }
        val intensityNameLabel = Label("Intensity:")
        val intensityValueLabel = Label()
        val intensitySlider = Slider(0.0, 100.0, 50.0).apply {
            isShowTickMarks = true
            isShowTickLabels = true
            majorTickUnit = 50.0
            blockIncrement = 5.0
            tooltip = Tooltip("WLED ix parameter for checked segments")
            maxWidth = Double.MAX_VALUE
        }
        val paramsMenu = MenuButton("Params").apply {
            tooltip = Tooltip("Show effect speed and intensity sliders")
            items.add(
                CustomMenuItem(
                    VBox(
                        6.0,
                        speedNameLabel,
                        HBox(8.0, speedSlider, speedValueLabel).apply {
                            alignment = Pos.CENTER_LEFT
                            HBox.setHgrow(speedSlider, Priority.ALWAYS)
                            prefWidth = 260.0
                        },
                        intensityNameLabel,
                        HBox(8.0, intensitySlider, intensityValueLabel).apply {
                            alignment = Pos.CENTER_LEFT
                            HBox.setHgrow(intensitySlider, Priority.ALWAYS)
                            prefWidth = 260.0
                        },
                    ).apply { padding = Insets(8.0) },
                    false,
                ),
            )
        }

        fun refreshColor(hex: String?) {
            val safeHex = hex?.takeIf(AdvancedLightJson::isValidHexColor) ?: "#FFFFFF"
            colorSwatch.style = "-fx-background-color: $safeHex; $SWATCH_STYLE_BASE"
            colorLabel.text = hex ?: "No segment"
        }

        fun refreshParamLabels(effect: LightEffect) {
            speedNameLabel.text = "${effect.speedName}:"
            intensityNameLabel.text = "${effect.intensityName}:"
        }

        fun refreshEditor() {
            val first = controller.selectedSegments().firstOrNull()
            val hasSelection = first != null
            updatingControls = true
            try {
                listOf(colorButton, brightnessSlider, effectCombo, paramsMenu).forEach {
                    it.isDisable = !hasSelection
                }
                if (first == null) {
                    refreshColor(null)
                    brightnessLabel.text = "0 %"
                    effectCombo.value = null
                    speedValueLabel.text = "0 %"
                    intensityValueLabel.text = "0 %"
                    refreshParamLabels(LightEffect.NONE)
                } else {
                    refreshColor(first.color)
                    brightnessSlider.value = first.brightness * 100.0
                    brightnessLabel.text = percentLabel(first.brightness)
                    effectCombo.value = first.effect
                    speedSlider.value = first.effectSpeed * 100.0 / 255.0
                    speedValueLabel.text = intPercentLabel(first.effectSpeed)
                    intensitySlider.value = first.effectIntensity * 100.0 / 255.0
                    intensityValueLabel.text = intPercentLabel(first.effectIntensity)
                    refreshParamLabels(first.effect)
                }
            } finally {
                updatingControls = false
            }
        }

        fun applyCommands(commands: List<AdvancedLightSegmentCommand>) {
            serialCoordinator.sendSegmentsAsync(
                commands,
                splitCommands = controller.coversAllKnownSegments(commands),
            )
            refreshTableFromController(table, rows)
            refreshEditor()
        }

        colorButton.setOnAction {
            val first = controller.selectedSegments().firstOrNull() ?: return@setOnAction
            val selected = ColorEditorDialog.showDialog(
                owner = colorButton.scene?.window,
                title = "Set Segment Color",
                prompt = "Select the color for checked segments",
                initialColor = ColorHexCodec.parseOrDefault(first.color, Color.WHITE),
            )
            if (selected != null) {
                applyCommands(controller.applyColorToSelected(ColorHexCodec.colorToHex(selected)))
            }
        }
        brightnessSlider.valueProperty().addListener { _, _, value ->
            if (updatingControls) return@addListener
            val brightness = value.toDouble() / 100.0
            brightnessLabel.text = percentLabel(brightness)
            applyCommands(controller.applyBrightnessToSelected(brightness))
        }
        effectCombo.valueProperty().addListener { _, _, effect ->
            if (updatingControls || effect == null) return@addListener
            refreshParamLabels(effect)
            applyCommands(controller.applyEffectToSelected(effect))
        }
        speedSlider.valueProperty().addListener { _, _, value ->
            if (updatingControls) return@addListener
            val speed = (value.toDouble() * 255.0 / 100.0).roundToInt().coerceIn(0, 255)
            speedValueLabel.text = intPercentLabel(speed)
            applyCommands(controller.applyEffectSpeedToSelected(speed))
        }
        intensitySlider.valueProperty().addListener { _, _, value ->
            if (updatingControls) return@addListener
            val intensity = (value.toDouble() * 255.0 / 100.0).roundToInt().coerceIn(0, 255)
            intensityValueLabel.text = intPercentLabel(intensity)
            applyCommands(controller.applyEffectIntensityToSelected(intensity))
        }

        val node = VBox(
            8.0,
            HBox(8.0, Label("Color:"), colorButton).apply {
                alignment = Pos.CENTER_LEFT
                HBox.setHgrow(colorButton, Priority.ALWAYS)
            },
            VBox(
                4.0,
                Label("Brightness:"),
                HBox(8.0, brightnessSlider, brightnessLabel).apply {
                    alignment = Pos.CENTER_LEFT
                    HBox.setHgrow(brightnessSlider, Priority.ALWAYS)
                },
            ),
            HBox(8.0, Label("Effect:"), effectCombo, paramsMenu).apply {
                alignment = Pos.CENTER_LEFT
                HBox.setHgrow(effectCombo, Priority.ALWAYS)
            },
        )

        refreshEditor()
        return EditorContext(node, ::refreshEditor)
    }

    private fun buildDebugPanel(): Node {
        val console = TextArea().apply {
            isEditable = false
            isWrapText = false
            prefRowCount = 7
            promptText = "Serial traffic appears here when debug is enabled."
            isVisible = false
            isManaged = false
        }
        val debugToggle = CheckBox("Debug console").apply {
            isSelected = serialCoordinator.isDebugLoggingEnabled()
            tooltip = Tooltip("Show serial query, command, and error log")
        }
        val clearButton = Button("Clear").apply {
            tooltip = Tooltip("Clear the serial debug console")
        }

        debugToggle.selectedProperty().addListener { _, _, selected ->
            serialCoordinator.setDebugLoggingEnabled(selected)
            console.isVisible = selected
            console.isManaged = selected
        }
        clearButton.setOnAction { console.clear() }
        val removeDebugListener = serialCoordinator.addDebugListener { line ->
            console.appendText("$line\n")
        }
        disposables += removeDebugListener

        return VBox(
            4.0,
            HBox(6.0, debugToggle, clearButton).apply { alignment = Pos.CENTER_LEFT },
            console,
        )
    }

    private fun refreshTableFromController(
        table: TableView<AdvancedLightSegmentState>,
        rows: javafx.collections.ObservableList<AdvancedLightSegmentState>,
    ) {
        rows.setAll(controller.currentSegments())
        table.prefHeight = segmentTableHeight(rows.size)
        table.maxHeight = Region.USE_PREF_SIZE
        table.refresh()
    }

    private fun effectConverter(): StringConverter<LightEffect> =
        object : StringConverter<LightEffect>() {
            override fun toString(effect: LightEffect?): String = effect?.displayName ?: ""

            override fun fromString(value: String?): LightEffect =
                LightEffect.entries.firstOrNull { it.displayName == value } ?: LightEffect.NONE
        }

    private fun percentLabel(value: Double): String = "${(value * 100.0).roundToInt()} %"

    private fun intPercentLabel(value: Int): String = "${(value * 100) / 255} %"

    private fun segmentTableHeight(rowCount: Int): Double {
        val visibleRows = rowCount.coerceAtLeast(1)
        return SEGMENT_TABLE_HEADER_HEIGHT + visibleRows * SEGMENT_TABLE_ROW_HEIGHT + SEGMENT_TABLE_BORDER_HEIGHT
    }

    private fun disposeViewListeners() {
        disposables.forEach { it() }
        disposables.clear()
    }

    private data class EditorContext(
        val node: Node,
        val refresh: () -> Unit,
    )

    private data class ConnectionPanel(
        val toggle: ToggleButton,
        val panel: Node,
    )

    private companion object {
        private const val SWATCH_STYLE_BASE: String =
            "-fx-border-color: -tc-border; -fx-border-radius: 3; -fx-background-radius: 3;"
        private const val SEGMENT_TABLE_ROW_HEIGHT: Double = 30.0
        private const val SEGMENT_TABLE_HEADER_HEIGHT: Double = 34.0
        private const val SEGMENT_TABLE_BORDER_HEIGHT: Double = 4.0
    }
}
