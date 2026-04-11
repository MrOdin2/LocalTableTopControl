package com.tabletopcontrol.light

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.ui.color.ColorEditorDialog
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.control.Slider
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.util.StringConverter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * DM-panel plugin for controlling physical ambient lighting via WLED.
 *
 * Provides a control panel with:
 * - **Serial connection** — port selector, baud-rate field, connect / disconnect button,
 *   and a live connection-status label.
 * - **Power** — a toggle to turn the LEDs on or off.
 * - **Color select** — a button that opens a color dialog with wheel and direct inputs.
 * - **Effect select** — a [ComboBox] to pick from the available [LightEffect]s.
 * - **Color cycling** — a [CheckBox] to enable automatic color cycling.
 * - **Brightness** — a [Slider] to set output brightness (0 – 100 %).
 * - **Preset** — a field and Apply / Clear buttons to activate a WLED preset by ID
 *   (1–250), letting the microcontroller run animations autonomously.
 * - **Debug console** — optional live log of serial commands sent to WLED.
 *
 * All state is held by a [LightController]; this class only handles the JavaFX
 * binding between controls and the controller.  Whenever the controller state
 * changes the current settings are forwarded to a [WledSerialSender] if a
 * serial connection is active.
 *
 * When a preset is active the plugin sends `{"ps":N}` instead of the full
 * color/effect/brightness state, so the WLED device handles the animation
 * without further updates from the host.
 *
 * Serial writes are dispatched to a dedicated background thread so the JavaFX
 * Application Thread is never blocked by I/O or serial timeouts.  Rapid bursts
 * of state changes (e.g. dragging the brightness slider) are coalesced: only
 * the most-recent state is sent once the background thread becomes free.
 *
 * GODCLASS audit note:
 * - `LightPlugin` is a GODCLASS with extensive UI, coordination, and transport flow in one class.
 * - Features that can be moved to helpers/shared components:
 *   - Section-specific UI builders (serial/power/color/effects/preset/debug) into focused view components.
 *   - Color conversion/parsing/recent-color management utilities.
 *   - Serial update scheduling/coalescing and retry/error-throttle policy.
 *   - Debug-console formatting and append/log policy.
 *   - Controller-to-sender adapter that transforms controller snapshots into WLED commands.
 */
class LightPlugin : DmPlugin {
    override val displayName: String = "Lights"

    /** Pure-Kotlin state controller; no JavaFX dependencies. */
    private val controller = LightController()

    /** Sends WLED JSON commands over the active serial port. */
    private val sender = WledSerialSender()

    /**
     * Single-threaded executor that performs all blocking serial writes off
     * the JavaFX Application Thread.  Daemon threads are used so the JVM can
     * exit cleanly even if the executor has not been shut down explicitly.
     */
    private val serialExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "wled-serial").also { it.isDaemon = true }
    }

    /**
     * Guards against queueing multiple redundant write tasks.
     *
     * When `true`, a task is already queued (or running) on [serialExecutor]
     * that will send the latest state — there is no benefit in queueing
     * another.  Set to `false` again just before the actual write so that a
     * state change arriving during the write will queue one more task.
     */
    private val pendingWrite = AtomicBoolean(false)
    private val latestSnapshot = AtomicReference<ControllerSnapshot?>(null)
    private val debugLoggingEnabled = AtomicBoolean(false)
    private var debugConsole: TextArea? = null
    private val lastSerialErrorLogNanos = AtomicReference(0L)
    private val serialWriteErrorLoggedSinceSuccess = AtomicBoolean(false)

    /**
     * Tracks the effect-params label-update listener registered by [buildEffectParamsRow]
     * so it can be removed before a new listener is added on subsequent [createView] calls.
     */
    private var effectParamsListener: (() -> Unit)? = null
    init {
        // Forward every state change to the WLED device if connected.
        controller.addChangeListener { scheduleStateUpdate() }
    }

    override fun createView(): Node {
        val root = VBox(8.0).apply { padding = Insets(10.0) }

        root.children.addAll(
            Label("Ambient Light Controls"),
            Separator(),
            buildSerialSection(),
            Separator(),
            buildPowerRow(),
            buildColorRow(),
            buildEffectRow(),
            buildEffectParamsRow(),
            buildColorCyclingRow(),
            buildBrightnessRow(),
            Separator(),
            buildPresetRow(),
            Separator(),
            buildDebugConsoleRow(),
        )

        return ScrollPane(root).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        }
    }

    override fun onShutdown() {
        // Initiate shutdown of the executor on the FX thread, but perform any
        // blocking waits in a background thread so we don't stall JavaFX
        // application shutdown.
        serialExecutor.shutdown()

        Thread({
            try {
                // Give any in-flight serial write time to finish.
                if (!serialExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    // Still running after 3 s — interrupt and wait a little longer.
                    serialExecutor.shutdownNow()
                    serialExecutor.awaitTermination(1, TimeUnit.SECONDS)
                }
            } catch (_: InterruptedException) {
                serialExecutor.shutdownNow()
                Thread.currentThread().interrupt()
            } finally {
                sender.disconnect()
            }
        }, "wled-serial-shutdown").apply {
            isDaemon = true
            start()
        }
    }

    // -------------------------------------------------------------------------
    // Section builders
    // -------------------------------------------------------------------------

    /**
     * Builds the WLED serial-connection configuration section.
     *
     * Contains:
     * - A [ComboBox] listing available serial ports (refreshed on click).
     * - A [TextField] for the baud rate (pre-filled with [WledSerialSender.DEFAULT_BAUD_RATE]).
     * - A Connect / Disconnect [Button].
     * - A status [Label] showing the current connection state.
     */
    private fun buildSerialSection(): VBox {
        val statusLabel = Label("Not connected").apply {
            style = "-fx-text-fill: -tc-text-muted;"
        }

        val portCombo = ComboBox<String>().apply {
            tooltip = Tooltip("Select the serial port for the WLED device")
            maxWidth = Double.MAX_VALUE
            isEditable = true
            // Populate immediately; user can refresh via the ↺ button.
            items.setAll(sender.availablePorts())
        }

        val baudField = TextField(WledSerialSender.DEFAULT_BAUD_RATE.toString()).apply {
            prefColumnCount = 8
            tooltip = Tooltip("Serial baud rate (WLED default: 115200)")
        }

        val connectBtn = Button("Connect").apply {
            tooltip = Tooltip("Open the selected serial port")
            maxWidth = Double.MAX_VALUE
        }

        // Initialize button label and status from the current connection state.
        if (sender.isConnected) {
            connectBtn.text = "Disconnect"
            statusLabel.text = sender.connectedPortName?.let { "Connected: $it" } ?: "Connected"
            statusLabel.style = "-fx-text-fill: -tc-success;"
        } else {
            connectBtn.text = "Connect"
            statusLabel.text = "Not connected"
            statusLabel.style = "-fx-text-fill: -tc-text-muted;"
        }
        val refreshBtn = Button("↺").apply {
            tooltip = Tooltip("Refresh the list of available serial ports")
        }

        connectBtn.setOnAction {
            if (sender.isConnected) {
                // Disable button immediately to prevent double-clicks.
                connectBtn.isDisable = true
                serialExecutor.execute {
                    sender.disconnect()
                    resetSerialErrorState()
                    Platform.runLater {
                        connectBtn.text = "Connect"
                        connectBtn.isDisable = false
                        statusLabel.text = "Disconnected"
                        statusLabel.style = "-fx-text-fill: -tc-text-muted;"
                    }
                }
            } else {
                val rawPortName = if (portCombo.isEditable) {
                    portCombo.editor.text
                } else {
                    portCombo.value
                }
                val portName = rawPortName?.trim() ?: return@setOnAction
                if (portName.isBlank()) return@setOnAction
                val baudRate = baudField.text.trim().toIntOrNull()
                    ?: WledSerialSender.DEFAULT_BAUD_RATE
                // Disable button and show interim status while connecting.
                connectBtn.isDisable = true
                statusLabel.text = "Connecting…"
                statusLabel.style = "-fx-text-fill: -tc-text-muted;"
                serialExecutor.execute {
                    try {
                        sender.connect(portName, baudRate)
                        resetSerialErrorState()
                        Platform.runLater {
                            connectBtn.text = "Disconnect"
                            connectBtn.isDisable = false
                            statusLabel.text = "Connected: $portName"
                            statusLabel.style = "-fx-text-fill: -tc-success;"
                            // Push the current state immediately after connecting.
                            scheduleStateUpdate()
                        }
                    } catch (e: Exception) {
                        Platform.runLater {
                            connectBtn.isDisable = false
                            val message = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
                            statusLabel.text = "Error: $message"
                            statusLabel.style = "-fx-text-fill: -tc-error;"
                        }
                    }
                }
            }
        }

        refreshBtn.setOnAction {
            val ports = sender.availablePorts()
            portCombo.items.setAll(ports)
        }

        val portRow = HBox(6.0, portCombo, refreshBtn).apply {
            HBox.setHgrow(portCombo, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }

        val baudRow = HBox(6.0, Label("Baud:"), baudField).apply {
            alignment = Pos.CENTER_LEFT
        }

        val btnRow = HBox(6.0, connectBtn).apply {
            HBox.setHgrow(connectBtn, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }

        return VBox(4.0,
            Label("WLED Serial Connection"),
            portRow,
            baudRow,
            btnRow,
            statusLabel,
        )
    }

    /**
     * Builds the power on/off toggle row.
     *
     * When unchecked the LEDs are switched off; the controller retains all
     * other settings so they take effect when power is restored.
     */
    private fun buildPowerRow(): HBox {
        val check = CheckBox("Power on").apply {
            isSelected = controller.power
            tooltip = Tooltip("Turn the WLED device on or off")
            selectedProperty().addListener { _, _, on ->
                controller.setPower(on)
            }
        }
        return HBox(8.0, check).apply { alignment = Pos.CENTER_LEFT }
    }

    /**
     * Builds the color-picker row.
     */
    private fun buildColorRow(): HBox {
        var appliedColor = ColorHexCodec.parseOrDefault(controller.color, Color.WHITE)
        val colorButton = Button().apply {
            tooltip = Tooltip("Select the ambient light color")
            maxWidth = Double.MAX_VALUE
            alignment = Pos.CENTER_LEFT
        }

        val swatch = Region().apply {
            minWidth = 16.0
            minHeight = 16.0
            prefWidth = 16.0
            prefHeight = 16.0
            style = SWATCH_STYLE_BASE
        }
        val valueLabel = Label()
        fun refreshButtonLabel(color: Color) {
            val hex = ColorHexCodec.colorToHex(color)
            swatch.style = "-fx-background-color: $hex; $SWATCH_STYLE_BASE"
            valueLabel.text = hex
        }
        refreshButtonLabel(appliedColor)
        colorButton.graphic = HBox(8.0, swatch, valueLabel).apply { alignment = Pos.CENTER_LEFT }
        colorButton.text = ""
        colorButton.setOnAction {
            val selected = ColorEditorDialog.showDialog(
                owner = colorButton.scene?.window,
                title = "Set Light Color",
                prompt = "Select the ambient light color",
                initialColor = appliedColor,
            )
            if (selected != null) {
                appliedColor = selected
                controller.setColor(ColorHexCodec.colorToHex(appliedColor))
                refreshButtonLabel(appliedColor)
            }
        }

        return HBox(8.0, Label("Color:"), colorButton).apply {
            HBox.setHgrow(colorButton, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }
    }

    /**
     * Builds the effect-selector row.
     *
     * The [ComboBox] lists all [LightEffect] values by their [LightEffect.displayName]
     * and writes the selection back to the controller.
     */
    private fun buildEffectRow(): HBox {
        val combo = ComboBox<LightEffect>().apply {
            items.setAll(*LightEffect.entries.toTypedArray())
            value = controller.effect
            converter = object : StringConverter<LightEffect>() {
                override fun toString(e: LightEffect?) = e?.displayName ?: ""
                override fun fromString(s: String?) =
                    LightEffect.entries.firstOrNull { it.displayName == s } ?: LightEffect.NONE
            }
            tooltip = Tooltip("Select a lighting effect")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newEffect ->
                if (newEffect != null) controller.setEffect(newEffect)
            }
        }
        return HBox(8.0, Label("Effect:"), combo).apply {
            HBox.setHgrow(combo, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }
    }

    /**
     * Builds the effect speed and intensity sliders.
     *
     * Both [Slider]s expose a 0–100% range in the UI, which is linearly mapped
     * to the WLED `sx` (speed) and `ix` (intensity) segment parameters in the
     * 0–255 range. A midpoint of ~50% corresponds to a value of ~128 and is
     * used as the default. The labels above each slider update automatically
     * when the active [LightEffect] changes to reflect the effect-specific
     * parameter names (e.g. "Cooling" / "Sparking" for Fire).
     */
    private fun buildEffectParamsRow(): VBox {
        val speedValueLabel = Label("${(controller.effectSpeed * 100) / 255} %")
        val intensityValueLabel = Label("${(controller.effectIntensity * 100) / 255} %")

        val speedNameLabel = Label("${controller.effect.speedName}:")
        val intensityNameLabel = Label("${controller.effect.intensityName}:")

        val speedSlider = Slider(0.0, 100.0, controller.effectSpeed * 100.0 / 255.0).apply {
            isShowTickMarks = true
            isShowTickLabels = true
            majorTickUnit = 50.0
            blockIncrement = 5.0
            tooltip = Tooltip("Effect speed parameter (WLED sx) — meaning depends on the selected effect")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newValue ->
                val speed = (newValue.toDouble() * 255.0 / 100.0).roundToInt().coerceIn(0, 255)
                controller.setEffectSpeed(speed)
                speedValueLabel.text = "${(speed * 100) / 255} %"
            }
        }

        val intensitySlider = Slider(0.0, 100.0, controller.effectIntensity * 100.0 / 255.0).apply {
            isShowTickMarks = true
            isShowTickLabels = true
            majorTickUnit = 50.0
            blockIncrement = 5.0
            tooltip = Tooltip("Effect intensity parameter (WLED ix) — meaning depends on the selected effect")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newValue ->
                val intensity = (newValue.toDouble() * 255.0 / 100.0).roundToInt().coerceIn(0, 255)
                controller.setEffectIntensity(intensity)
                intensityValueLabel.text = "${(intensity * 100) / 255} %"
            }
        }

        // Update the parameter-name labels only when the active effect actually changes.
        // Remove any listener registered by a previous createView() call to prevent accumulation.
        effectParamsListener?.let { controller.removeChangeListener(it) }
        val lastEffect = AtomicReference(controller.effect)
        effectParamsListener = controller.addChangeListener {
            val currentEffect = controller.effect
            if (currentEffect != lastEffect.getAndSet(currentEffect)) {
                Platform.runLater {
                    speedNameLabel.text = "${currentEffect.speedName}:"
                    intensityNameLabel.text = "${currentEffect.intensityName}:"
                }
            }
        }

        val speedRow = HBox(8.0, speedSlider, speedValueLabel).apply {
            HBox.setHgrow(speedSlider, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }
        val intensityRow = HBox(8.0, intensitySlider, intensityValueLabel).apply {
            HBox.setHgrow(intensitySlider, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }

        return VBox(4.0,
            speedNameLabel, speedRow,
            intensityNameLabel, intensityRow,
        )
    }

    /**
     * Builds the color-cycling toggle row.
     *
     * When the [CheckBox] is selected, automatic color cycling is enabled in
     * the controller; the WLED device will use the Rainbow Cycle effect.
     */
    private fun buildColorCyclingRow(): HBox {
        val check = CheckBox("Enable color cycling").apply {
            isSelected = controller.colorCycling
            tooltip = Tooltip("Automatically cycle through colors (uses WLED Rainbow effect)")
            selectedProperty().addListener { _, _, selected ->
                controller.setColorCycling(selected)
            }
        }
        return HBox(8.0, check).apply {
            alignment = Pos.CENTER_LEFT
        }
    }

    /**
     * Builds the brightness-slider row.
     *
     * The [Slider] ranges from 0 to 100 (percent) and is displayed with major
     * tick marks at 0, 50, and 100.  Values are written to the controller
     * normalised to the range `0.0–1.0`.
     */
    private fun buildBrightnessRow(): VBox {
        val valueLabel = Label(brightnessLabel(controller.brightness))

        val slider = Slider(0.0, 100.0, controller.brightness * 100.0).apply {
            isShowTickMarks = true
            isShowTickLabels = true
            majorTickUnit = 50.0
            blockIncrement = 5.0
            tooltip = Tooltip("Adjust the output brightness (0 – 100 %)")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newValue ->
                val normalised = newValue.toDouble() / 100.0
                controller.setBrightness(normalised)
                valueLabel.text = brightnessLabel(normalised)
            }
        }

        val sliderRow = HBox(8.0, slider, valueLabel).apply {
            HBox.setHgrow(slider, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }

        return VBox(4.0, Label("Brightness:"), sliderRow)
    }

    /**
     * Builds the WLED preset control section.
     *
     * Contains:
     * - A [TextField] for entering a preset ID (`1–250`).
     * - An **Apply** [Button] that activates the preset on the controller;
     *   any connected WLED device will be updated to use that preset.
     * - A **Clear** [Button] that clears the active preset in the controller and
     *   returns the device to manual color / effect / brightness control.
     * - A status [Label] showing the currently active preset, if any.
     *
     * When a preset is active, the WLED microcontroller runs the animation
     * stored in that preset slot autonomously — the host does not need to
     * continuously send state updates.
     */
    private fun buildPresetRow(): VBox {
        val activeLabel = Label(
            controller.preset?.let { "Active preset: $it" } ?: "No preset active"
        ).apply {
            style = "-fx-text-fill: -tc-text-muted;"
        }

        val presetField = TextField().apply {
            promptText = "Preset ID (1–250)"
            prefColumnCount = 10
            tooltip = Tooltip("Enter a WLED preset ID (1–250) to activate it on the device")
            maxWidth = Double.MAX_VALUE
        }

        val applyBtn = Button("Apply Preset").apply {
            tooltip = Tooltip("Activate the entered preset on the WLED device")
            maxWidth = Double.MAX_VALUE
        }

        val clearBtn = Button("Clear").apply {
            tooltip = Tooltip("Clear the active preset and return to manual control")
        }

        applyBtn.setOnAction {
            val id = presetField.text.trim().toIntOrNull()
            if (id == null || id !in 1..250) {
                activeLabel.text = "Invalid preset ID (must be 1–250)"
                activeLabel.style = "-fx-text-fill: -tc-error;"
                return@setOnAction
            }
            try {
                controller.setPreset(id)
                activeLabel.text = "Active preset: $id"
                activeLabel.style = "-fx-text-fill: -tc-success;"
            } catch (e: IllegalArgumentException) {
                activeLabel.text = "Error: ${e.message}"
                activeLabel.style = "-fx-text-fill: -tc-error;"
            }
        }

        clearBtn.setOnAction {
            controller.setPreset(null)
            presetField.clear()
            activeLabel.text = "No preset active"
            activeLabel.style = "-fx-text-fill: -tc-text-muted;"
        }

        val inputRow = HBox(6.0, presetField, applyBtn, clearBtn).apply {
            HBox.setHgrow(presetField, Priority.ALWAYS)
            alignment = Pos.CENTER_LEFT
        }

        return VBox(4.0,
            Label("WLED Preset"),
            inputRow,
            activeLabel,
        )
    }

    /**
     * Builds a small debug console showing serial commands sent to WLED.
     *
     * Logging is disabled by default and can be toggled by the user.
     */
    private fun buildDebugConsoleRow(): VBox {
        val enabledCheck = CheckBox("Show sent serial commands (debug)").apply {
            isSelected = debugLoggingEnabled.get()
            tooltip = Tooltip("When enabled, logs each JSON command sent to WLED")
            selectedProperty().addListener { _, _, enabled ->
                debugLoggingEnabled.set(enabled)
            }
        }
        val clearBtn = Button("Clear").apply {
            tooltip = Tooltip("Clear the debug command console")
        }
        val console = TextArea().apply {
            isEditable = false
            isWrapText = false
            prefRowCount = 6
            promptText = "Sent serial commands will appear here when debug logging is enabled."
        }
        debugConsole = console
        clearBtn.setOnAction { console.clear() }
        val topRow = HBox(6.0, enabledCheck, clearBtn).apply {
            alignment = Pos.CENTER_LEFT
        }
        return VBox(4.0,
            Label("Serial Debug Console"),
            topRow,
            console,
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * An immutable snapshot of all [LightController] fields, captured atomically
     * on the JavaFX Application Thread before the write task is submitted so the
     * background thread never races against UI mutations.
     */
    private data class ControllerSnapshot(
        val power: Boolean,
        val color: String,
        val effect: LightEffect,
        val brightness: Double,
        val colorCycling: Boolean,
        val effectSpeed: Int,
        val effectIntensity: Int,
        val preset: Int?,
    )

    /**
     * Schedules a serial state update on the background [serialExecutor].
     *
     * The controller snapshot is always captured on the JavaFX Application
     * Thread, then published via [latestSnapshot]. Calls are coalesced: at most
     * one background drain task runs at a time, and that task repeatedly sends
     * the latest available snapshot.
     *
     * Routing rules (in priority order):
     * 1. If [ControllerSnapshot.power] is `false` → always send an explicit
     *    power-off state command, even when a preset is active.
     * 2. If a [ControllerSnapshot.preset] ID is set → send `{"ps":N}` so the
     *    device runs its stored animation autonomously.
     * 3. Otherwise → send the full color / effect / brightness state.
     *
     * This method is safe to call from any thread.
     */
    private fun scheduleStateUpdate() {
        if (Platform.isFxApplicationThread()) {
            publishSnapshotAndScheduleWrite()
        } else {
            Platform.runLater { publishSnapshotAndScheduleWrite() }
        }
    }

    /** Captures controller state on FX thread and starts the write drain if needed. */
    private fun publishSnapshotAndScheduleWrite() {
        val snapshot = ControllerSnapshot(
            power          = controller.power,
            color          = controller.color,
            effect         = controller.effect,
            brightness     = controller.brightness,
            colorCycling   = controller.colorCycling,
            effectSpeed    = controller.effectSpeed,
            effectIntensity = controller.effectIntensity,
            preset         = controller.preset,
        )
        latestSnapshot.set(snapshot)
        if (!pendingWrite.compareAndSet(false, true)) return

        serialExecutor.execute {
            while (true) {
                val next = latestSnapshot.getAndSet(null)
                if (next == null) {
                    pendingWrite.set(false)
                    if (latestSnapshot.get() != null && pendingWrite.compareAndSet(false, true)) {
                        continue
                    }
                    return@execute
                }

                if (!sender.isConnected) continue

                try {
                    val sentJson = when {
                        // Power-off always wins — even over an active preset.
                        !next.power -> sender.sendStateJson(
                            on           = false,
                            color        = next.color,
                            effect       = next.effect,
                            brightness   = next.brightness,
                            colorCycling = next.colorCycling,
                            speed        = next.effectSpeed,
                            intensity    = next.effectIntensity,
                        )
                        // Preset mode: let the microcontroller run the animation.
                        next.preset != null -> sender.sendPresetJson(next.preset)
                        // Manual mode: send full color / effect / brightness state.
                        else -> sender.sendStateJson(
                            on           = true,
                            color        = next.color,
                            effect       = next.effect,
                            brightness   = next.brightness,
                            colorCycling = next.colorCycling,
                            speed        = next.effectSpeed,
                            intensity    = next.effectIntensity,
                        )
                    }
                    appendDebugCommand(sentJson)
                    serialWriteErrorLoggedSinceSuccess.set(false)
                } catch (e: Exception) {
                    reportSerialWriteFailure(e)
                }
            }
        }
    }

    /**
     * Appends a single serial command line to the debug console when enabled.
     */
    private fun appendDebugCommand(commandJson: String) {
        if (!debugLoggingEnabled.get()) return
        Platform.runLater {
            val console = debugConsole ?: return@runLater
            val time = currentLogTimestamp()
            console.appendText("[$time] $commandJson\n")
        }
    }

    /**
     * Logs serial-write failures with full exception context and mirrors a concise
     * error line to the debug console when enabled.
     */
    private fun reportSerialWriteFailure(error: Exception) {
        val message = error.message?.takeIf { it.isNotBlank() } ?: error.toString()
        val nowNanos = System.nanoTime()
        val previousLogNanos = lastSerialErrorLogNanos.get()
        val shouldLog = previousLogNanos == 0L || nowNanos - previousLogNanos >= SERIAL_ERROR_LOG_THROTTLE_NANOS
        if (shouldLog && lastSerialErrorLogNanos.compareAndSet(previousLogNanos, nowNanos)) {
            System.err.println("WLED serial write failed: $message")
            error.printStackTrace()
        }

        if (serialWriteErrorLoggedSinceSuccess.compareAndSet(false, true)) {
            appendDebugCommand("ERROR serial write failed: $message")
        }
    }

    /** Clears failure-throttling state after successful writes or reconnects. */
    private fun resetSerialErrorState() {
        lastSerialErrorLogNanos.set(0L)
        serialWriteErrorLoggedSinceSuccess.set(false)
    }

    /** Returns a compact `HH:mm:ss` timestamp used by debug console entries. */
    private fun currentLogTimestamp(): java.time.LocalTime =
        java.time.LocalTime.now().withNano(0)

    /** Formats a normalised brightness value as a percentage label. */
    private fun brightnessLabel(value: Double): String = "${(value * 100).toInt()} %"

    private companion object {
        private val SERIAL_ERROR_LOG_THROTTLE_NANOS: Long = TimeUnit.SECONDS.toNanos(2)
        private const val SWATCH_STYLE_BASE: String =
            "-fx-border-color: -tc-border; -fx-border-radius: 3; -fx-background-radius: 3;"
    }
}
