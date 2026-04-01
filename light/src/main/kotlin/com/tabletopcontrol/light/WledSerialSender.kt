package com.tabletopcontrol.light

import com.fazecast.jSerialComm.SerialPort
import java.io.Closeable
import java.io.IOException

/**
 * Sends [WLED JSON API](https://kno.wled.ge/interfaces/json-api/) commands to a
 * WLED device connected over a serial port.
 *
 * WLED accepts newline-terminated JSON objects on its serial interface, for example:
 * ```json
 * {"on":true,"bri":255,"seg":[{"col":[[255,255,255]],"fx":0}]}
 * ```
 *
 * Usage:
 * ```kotlin
 * val sender = WledSerialSender()
 * sender.connect("/dev/ttyUSB0")          // default 115200 baud
 * sender.sendState(
 *     on           = true,
 *     color        = "#FF4400",
 *     effect       = LightEffect.FIRE,
 *     brightness   = 0.8,
 *     colorCycling = false,
 *     speed        = 128,
 *     intensity    = 128,
 * )
 * sender.disconnect()
 * ```
 *
 * The class implements [Closeable] so it can be used in a `use` block or closed
 * automatically when the owning plugin shuts down via [LightPlugin.onShutdown].
 *
 * @see LightEffect
 */
class WledSerialSender : Closeable {

    private var port: SerialPort? = null

    /** `true` when a serial port is open and ready to accept writes. */
    val isConnected: Boolean
        get() = port?.isOpen == true

    /** System port name of the currently connected serial port, if any. */
    val connectedPortName: String?
        get() = port?.takeIf { it.isOpen }?.systemPortName

    /**
     * Returns the system-level names of all serial ports detected on this machine.
     *
     * The list is suitable for populating a UI combo-box so the user can pick a
     * port.  The returned values are the raw system names
     * (e.g. `"/dev/ttyUSB0"` on Linux, `"COM3"` on Windows).
     */
    fun availablePorts(): List<String> =
        SerialPort.getCommPorts().map { it.systemPortName }

    /**
     * Opens the named serial port at [baudRate].
     *
     * Any previously open port is closed first.
     *
     * @param portName system port name, e.g. `"/dev/ttyUSB0"` or `"COM3"`
     * @param baudRate serial baud rate; WLED defaults to `115200`
     * @throws IOException if the port cannot be opened
     */
    @Throws(IOException::class)
    fun connect(portName: String, baudRate: Int = DEFAULT_BAUD_RATE) {
        disconnect()
        val sp = SerialPort.getCommPort(portName)
        sp.baudRate = baudRate
        sp.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 0, WRITE_TIMEOUT_MS)
        if (!sp.openPort()) {
            throw IOException("Failed to open serial port: $portName")
        }
        port = sp
    }

    /**
     * Closes the current serial port connection, if any.
     *
     * Safe to call even when not connected.
     */
    fun disconnect() {
        port?.closePort()
        port = null
    }

    /** Alias for [disconnect]; satisfies [Closeable]. */
    override fun close() = disconnect()

    /**
     * Sends a WLED JSON state command over the open serial connection.
     *
     * Constructs and transmits a newline-terminated JSON object that WLED
     * parses and applies immediately.  The full state is sent on every call, so
     * callers do not need to track deltas.
     *
     * When [colorCycling] is `true`, the Rainbow Cycle effect
     * ([LightEffect.RAINBOW]) is used regardless of the [effect] parameter.
     *
     * @param on           `true` to turn the LEDs on, `false` to turn them off
     * @param color        light color as a CSS hex string (`#RRGGBB` or `#RGB`)
     * @param effect       the [LightEffect] to apply
     * @param brightness   brightness in the range `0.0` (off) to `1.0` (full)
     * @param colorCycling when `true` the Rainbow effect overrides [effect]
     * @param speed        effect speed in the range `0` (slowest) to `255` (fastest)
     * @param intensity    effect intensity in the range `0` (least) to `255` (most)
     * @throws IOException              if the port is not connected or the write fails
     * @throws IllegalArgumentException if [color] is not a valid CSS hex string,
     *                                  [brightness] is outside `0.0..1.0`, or
     *                                  [speed] / [intensity] are outside `0..255`
     */
    @Throws(IOException::class)
    fun sendState(
        on: Boolean,
        color: String,
        effect: LightEffect,
        brightness: Double,
        colorCycling: Boolean,
        speed: Int = DEFAULT_EFFECT_SPEED,
        intensity: Int = DEFAULT_EFFECT_INTENSITY,
    ) {
        sendStateJson(on, color, effect, brightness, colorCycling, speed, intensity)
    }

    /**
     * Sends a WLED preset-recall command over the open serial connection.
     *
     * Activating a preset tells the WLED device to run the preset's stored
     * configuration (colors, effects, speed, palette) autonomously.  The host
     * does not need to send any further state updates while the preset is
     * active.
     *
     * @param id the WLED preset ID to activate (typically `1–250`)
     * @throws IOException              if the port is not connected or the write fails
     * @throws IllegalArgumentException if [id] is outside `1..250`
     */
    @Throws(IOException::class)
    fun sendPreset(id: Int) {
        sendPresetJson(id)
    }

    // -------------------------------------------------------------------------
    // Internal helpers (internal visibility allows unit-testing without hardware)
    // -------------------------------------------------------------------------

    /**
     * Constructs the WLED JSON command string from the supplied parameters.
     *
     * The resulting JSON follows the
     * [WLED state object](https://kno.wled.ge/interfaces/json-api/#state-object)
     * format, targeting the first segment (`seg[0]`).
     *
     * @param speed     effect speed mapped to `sx` (0–255)
     * @param intensity effect intensity mapped to `ix` (0–255)
     */
    internal fun buildJson(
        on: Boolean,
        color: String,
        effect: LightEffect,
        brightness: Double,
        colorCycling: Boolean,
        speed: Int = DEFAULT_EFFECT_SPEED,
        intensity: Int = DEFAULT_EFFECT_INTENSITY,
    ): String {
        require(brightness in 0.0..1.0) { "Brightness must be 0.0–1.0, was $brightness" }
        require(speed in 0..255) { "Speed must be 0–255, was $speed" }
        require(intensity in 0..255) { "Intensity must be 0–255, was $intensity" }
        val bri = (brightness * 255).toInt()
        val (r, g, b) = hexToRgb(color)
        val fxId = if (colorCycling) LightEffect.RAINBOW.wledEffectId else effect.wledEffectId
        return """{"on":$on,"bri":$bri,"seg":[{"col":[[$r,$g,$b]],"fx":$fxId,"sx":$speed,"ix":$intensity}]}"""
    }

    /**
     * Constructs a WLED JSON preset-recall command.
     *
     * Sending this command activates the WLED preset with the given [id],
     * for example:
     * ```json
     * {"ps":5}
     * ```
     *
     * @param id the WLED preset ID to activate; must be in `1..250`
     * @throws IllegalArgumentException if [id] is outside `1..250`
     */
    internal fun buildPresetJson(id: Int): String {
        require(id in 1..250) { "Preset ID must be between 1 and 250, was $id" }
        return """{"ps":$id}"""
    }

    /**
     * Sends a full WLED state update and returns the exact JSON string written.
     */
    @Throws(IOException::class)
    internal fun sendStateJson(
        on: Boolean,
        color: String,
        effect: LightEffect,
        brightness: Double,
        colorCycling: Boolean,
        speed: Int = DEFAULT_EFFECT_SPEED,
        intensity: Int = DEFAULT_EFFECT_INTENSITY,
    ): String {
        val json = buildJson(on, color, effect, brightness, colorCycling, speed, intensity)
        writeJson(json)
        return json
    }

    /**
     * Sends a preset-recall command and returns the exact JSON string written.
     */
    @Throws(IOException::class)
    internal fun sendPresetJson(id: Int): String {
        val json = buildPresetJson(id)
        writeJson(json)
        return json
    }

    @Throws(IOException::class)
    private fun writeJson(json: String) {
        val p = port ?: throw IOException("Not connected to a serial port")
        if (!p.isOpen) throw IOException("Serial port is not open")
        val bytes = (json + "\n").toByteArray(Charsets.UTF_8)
        p.outputStream.write(bytes)
        p.outputStream.flush()
    }

    /**
     * Parses a `#RRGGBB` or `#RGB` CSS hex string into an `(r, g, b)` triple.
     *
     * @throws IllegalArgumentException if the string is not a valid hex color
     */
    private fun hexToRgb(hex: String): Triple<Int, Int, Int> {
        if (!hex.startsWith("#")) {
            throw IllegalArgumentException(
                "Color must be a CSS hex string (#RRGGBB or #RGB), was: $hex"
            )
        }

        val clean = hex.substring(1)

        return try {
            when (clean.length) {
                3 -> {
                    val r = clean[0].digitToInt(16) * 17
                    val g = clean[1].digitToInt(16) * 17
                    val b = clean[2].digitToInt(16) * 17
                    Triple(r, g, b)
                }
                6 -> {
                    val r = clean.substring(0, 2).toInt(16)
                    val g = clean.substring(2, 4).toInt(16)
                    val b = clean.substring(4, 6).toInt(16)
                    Triple(r, g, b)
                }
                else -> throw IllegalArgumentException(
                    "Color must be a CSS hex string (#RRGGBB or #RGB), was: $hex"
                )
            }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException(
                "Color must be a CSS hex string (#RRGGBB or #RGB), was: $hex",
                e
            )
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Color must be a CSS hex string (#RRGGBB or #RGB), was: $hex",
                e
            )
        }
    }

    companion object {
        /** Default baud rate used by WLED's serial interface. */
        const val DEFAULT_BAUD_RATE: Int = 115_200

        /** Default effect speed sent to WLED (midpoint of the 0–255 range). */
        const val DEFAULT_EFFECT_SPEED: Int = 128

        /** Default effect intensity sent to WLED (midpoint of the 0–255 range). */
        const val DEFAULT_EFFECT_INTENSITY: Int = 128

        private const val WRITE_TIMEOUT_MS: Int = 2_000
    }
}
