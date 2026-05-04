package com.tabletopcontrol.light.advanced

import com.fazecast.jSerialComm.SerialPort
import com.tabletopcontrol.light.LightEffect
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader

/**
 * Serial transport for the Advanced Lighting plugin.
 *
 * Extends the basic write-only pattern of [com.tabletopcontrol.light.WledSerialSender]
 * with bidirectional support required by the `s` status-query command.
 *
 * The `s` command causes the WLED device to emit a newline-terminated JSON
 * object; this class reads that response via a [BufferedReader] backed by the
 * port's input stream.
 */
internal class AdvancedWledSerial : Closeable {

    private var port: SerialPort? = null

    /** `true` when a serial port is open and ready for I/O. */
    val isConnected: Boolean
        get() = port?.isOpen == true

    /** System port name of the currently open port, or `null` when disconnected. */
    val connectedPortName: String?
        get() = port?.takeIf { it.isOpen }?.systemPortName

    /** Returns the system names of all serial ports detected on this machine. */
    fun availablePorts(): List<String> =
        SerialPort.getCommPorts().map { it.systemPortName }

    /**
     * Opens the named serial port at [baudRate] with both read and write timeouts.
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
        sp.setComPortTimeouts(
            SerialPort.TIMEOUT_READ_BLOCKING or SerialPort.TIMEOUT_WRITE_BLOCKING,
            READ_TIMEOUT_MS,
            WRITE_TIMEOUT_MS,
        )
        if (!sp.openPort()) {
            throw IOException("Failed to open serial port: $portName")
        }
        port = sp
    }

    /** Closes the current serial port connection, if any. */
    fun disconnect() {
        port?.closePort()
        port = null
    }

    /** Alias for [disconnect]; satisfies [Closeable]. */
    override fun close() = disconnect()

    /**
     * Sends the `s` status-query command and returns the full JSON response line.
     *
     * Blocks until a newline-terminated JSON string is received or the read
     * timeout (3 seconds) expires.
     *
     * @throws IOException if not connected, the write fails, or no response is received
     */
    @Throws(IOException::class)
    fun queryStatus(): String {
        val p = port ?: throw IOException("Not connected to a serial port")
        if (!p.isOpen) throw IOException("Serial port is not open")
        // Discard any stale input data before sending the query
        if (p.inputStream.available() > 0) {
            p.inputStream.skip(p.inputStream.available().toLong())
        }
        val cmd = "s\n".toByteArray(Charsets.UTF_8)
        p.outputStream.write(cmd)
        p.outputStream.flush()
        val reader = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8))
        return reader.readLine() ?: throw IOException("No response from WLED device")
    }

    /**
     * Sends a targeted per-segment state update to the WLED device.
     *
     * Only the segment with [segId] is updated; all other segments are left
     * unchanged.  The payload follows the WLED HTTP JSON API segment format.
     *
     * @param segId     WLED segment id
     * @param on        whether the segment should be on
     * @param color     color as a CSS hex string (`#RRGGBB` or `#RGB`)
     * @param effect    the [LightEffect] to apply to this segment
     * @param brightness brightness in the range `0.0`–`1.0`
     * @param speed     effect speed (WLED `sx`), `0`–`255`
     * @param intensity effect intensity (WLED `ix`), `0`–`255`
     * @return the exact JSON string sent to the device
     * @throws IOException if not connected or the write fails
     */
    @Throws(IOException::class)
    fun sendSegmentState(
        segId: Int,
        on: Boolean,
        color: String,
        effect: LightEffect,
        brightness: Double,
        speed: Int = 128,
        intensity: Int = 128,
    ): String {
        val bri = (brightness.coerceIn(0.0, 1.0) * 255).toInt()
        val (r, g, b) = hexToRgb(color)
        val json = buildString {
            append("{\"seg\":[{\"id\":$segId,\"on\":$on,\"bri\":$bri,")
            append("\"col\":[[$r,$g,$b]],\"fx\":${effect.wledEffectId},")
            append("\"sx\":$speed,\"ix\":$intensity}]}")
        }
        writeRaw(json)
        return json
    }

    /**
     * Writes a raw newline-terminated JSON string to the serial port.
     *
     * @throws IOException if not connected or the write fails
     */
    @Throws(IOException::class)
    fun writeRaw(json: String): String {
        val p = port ?: throw IOException("Not connected to a serial port")
        if (!p.isOpen) throw IOException("Serial port is not open")
        val bytes = (json + "\n").toByteArray(Charsets.UTF_8)
        p.outputStream.write(bytes)
        p.outputStream.flush()
        return json
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private fun hexToRgb(hex: String): Triple<Int, Int, Int> {
        require(hex.startsWith("#")) {
            "Color must be a CSS hex string (#RRGGBB or #RGB), was: $hex"
        }
        val clean = hex.substring(1)
        return try {
            when (clean.length) {
                3 -> Triple(
                    clean[0].digitToInt(16) * 17,
                    clean[1].digitToInt(16) * 17,
                    clean[2].digitToInt(16) * 17,
                )
                6 -> Triple(
                    clean.substring(0, 2).toInt(16),
                    clean.substring(2, 4).toInt(16),
                    clean.substring(4, 6).toInt(16),
                )
                else -> throw IllegalArgumentException("Bad hex color: $hex")
            }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Bad hex color: $hex", e)
        }
    }

    companion object {
        /** Default baud rate (matches WLED default). */
        const val DEFAULT_BAUD_RATE: Int = 115_200

        private const val WRITE_TIMEOUT_MS: Int = 2_000
        private const val READ_TIMEOUT_MS: Int = 3_000
    }
}
