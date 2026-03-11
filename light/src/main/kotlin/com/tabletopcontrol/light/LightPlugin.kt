package com.tabletopcontrol.light

import com.tabletopcontrol.core.DmPlugin
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.util.StringConverter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DM-panel plugin for controlling physical ambient lighting via WLED.
 *
 * Provides a control panel with:
 * - **Serial connection** — port selector, baud-rate field, connect / disconnect button,
 *   and a live connection-status label.
 * - **Power** — a toggle to turn the LEDs on or off.
 * - **Color select** — a [ColorPicker] to choose the light color.
 * - **Effect select** — a [ComboBox] to pick from the available [LightEffect]s.
 * - **Color cycling** — a [CheckBox] to enable automatic color cycling.
 * - **Brightness** — a [Slider] to set output brightness (0 – 100 %).
 * - **Preset** — a field and Apply / Clear buttons to activate a WLED preset by ID
 *   (1–250), letting the microcontroller run animations autonomously.
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
            buildColorCyclingRow(),
            buildBrightnessRow(),
            Separator(),
            buildPresetRow(),
        )

        return ScrollPane(root).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        }
    }

    override fun onShutdown() {
        serialExecutor.shutdown()
        try {
            // Give any in-flight serial write time to finish before closing the port.
            serialExecutor.awaitTermination(3, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        sender.disconnect()
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
            style = "-fx-text-fill: #888888;"
        }

        val portCombo = ComboBox<String>().apply {
            tooltip = Tooltip("Select the serial port for the WLED device")
            maxWidth = Double.MAX_VALUE
            isEditable = true
            // Populate on first show so the list is current.
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

        val refreshBtn = Button("↺").apply {
            tooltip = Tooltip("Refresh the list of available serial ports")
        }

        connectBtn.setOnAction {
            if (sender.isConnected) {
                sender.disconnect()
                connectBtn.text = "Connect"
                statusLabel.text = "Disconnected"
                statusLabel.style = "-fx-text-fill: #888888;"
            } else {
                val portName = portCombo.value?.trim() ?: return@setOnAction
                if (portName.isBlank()) return@setOnAction
                val baudRate = baudField.text.trim().toIntOrNull()
                    ?: WledSerialSender.DEFAULT_BAUD_RATE
                try {
                    sender.connect(portName, baudRate)
                    connectBtn.text = "Disconnect"
                    statusLabel.text = "Connected: $portName"
                    statusLabel.style = "-fx-text-fill: #00aa00;"
                    // Push the current state immediately after connecting.
                    scheduleStateUpdate()
                } catch (e: Exception) {
                    statusLabel.text = "Error: ${e.message}"
                    statusLabel.style = "-fx-text-fill: #cc0000;"
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
     *
     * The [ColorPicker] is pre-seeded with the controller's current color and
     * writes back to the controller on every selection change.
     */
    private fun buildColorRow(): HBox {
        val picker = ColorPicker(hexToColor(controller.color)).apply {
            tooltip = Tooltip("Select the ambient light color")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newColor ->
                controller.setColor(colorToHex(newColor))
            }
        }
        return HBox(8.0, Label("Color:"), picker).apply {
            HBox.setHgrow(picker, Priority.ALWAYS)
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
     * - An **Apply** [Button] that activates the preset on the controller and
     *   immediately sends `{"ps":N}` to the WLED device.
     * - A **Clear** [Button] that clears the active preset and returns the
     *   device to manual color / effect / brightness control.
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
            style = "-fx-text-fill: #888888;"
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
                activeLabel.style = "-fx-text-fill: #cc0000;"
                return@setOnAction
            }
            try {
                controller.setPreset(id)
                activeLabel.text = "Active preset: $id"
                activeLabel.style = "-fx-text-fill: #00aa00;"
            } catch (e: IllegalArgumentException) {
                activeLabel.text = "Error: ${e.message}"
                activeLabel.style = "-fx-text-fill: #cc0000;"
            }
        }

        clearBtn.setOnAction {
            controller.setPreset(null)
            presetField.clear()
            activeLabel.text = "No preset active"
            activeLabel.style = "-fx-text-fill: #888888;"
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Schedules a serial state update on the background [serialExecutor].
     *
     * Uses [pendingWrite] to coalesce rapid bursts of state changes: if a
     * write task is already queued, no additional task is submitted.  The
     * running task always reads the latest state from [controller], so it
     * naturally sends the final value of a burst (e.g. the resting position
     * of a dragged brightness slider).
     *
     * When [LightController.preset] is non-null, the task sends a preset-recall
     * command (`{"ps":N}`) instead of the full color/effect/brightness state,
     * allowing the WLED device to run the animation autonomously.
     *
     * This method is safe to call from any thread.
     */
    private fun scheduleStateUpdate() {
        if (!pendingWrite.compareAndSet(false, true)) return
        serialExecutor.execute {
            // Check connection first; if not connected, reset the flag so that
            // a state change arriving while we are disconnected can still queue
            // a new task the next time the user connects.
            if (!sender.isConnected) {
                pendingWrite.set(false)
                return@execute
            }
            // Clear the flag just before the write so any state change that
            // arrives during the write queues a follow-up task and is not dropped.
            pendingWrite.set(false)
            try {
                val presetId = controller.preset
                if (presetId != null) {
                    sender.sendPreset(presetId)
                } else {
                    sender.sendState(
                        on           = controller.power,
                        color        = controller.color,
                        effect       = controller.effect,
                        brightness   = controller.brightness,
                        colorCycling = controller.colorCycling,
                    )
                }
            } catch (e: Exception) {
                System.err.println("WLED serial write failed: ${e.message}")
            }
        }
    }

    /** Converts a JavaFX [Color] to an uppercase `#RRGGBB` hex string. */
    private fun colorToHex(color: Color): String {
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }

    /** Parses a `#RRGGBB` or `#RGB` hex string into a JavaFX [Color]. */
    private fun hexToColor(hex: String): Color = Color.web(hex)

    /** Formats a normalised brightness value as a percentage label. */
    private fun brightnessLabel(value: Double): String = "${(value * 100).toInt()} %"
}
