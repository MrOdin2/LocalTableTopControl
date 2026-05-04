package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.light.LightEffect
import com.tabletopcontrol.light.LightOperationResult
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orchestrates serial communication and segment state for the Advanced Lighting plugin.
 *
 * Responsibilities:
 * - Async connect / disconnect via a background serial executor.
 * - On connect: queries the WLED device with the `s` command, merges the response
 *   with the persisted configuration, and populates [segments].
 * - Coalesced background writes when a segment's state changes.
 * - Debug-log delivery and failure notification on the JavaFX thread.
 * - Persistence save on [shutdown].
 */
internal class AdvancedLightCoordinator(
    private val serial: AdvancedWledSerial = AdvancedWledSerial(),
) {
    /** Observable list of segments — mutated on the JavaFX application thread. */
    val segments: ObservableList<AdvancedSegment> = FXCollections.observableArrayList()

    private val serialExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "adv-wled-serial").also { it.isDaemon = true }
    }

    private val debugLoggingEnabled = AtomicBoolean(false)
    private val debugListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val failureListeners =
        CopyOnWriteArrayList<(LightOperationResult.SerialFailure) -> Unit>()

    /** Persisted configuration loaded at startup. */
    private var config: AdvancedLightConfig = AdvancedLightPersistence.load()

    val isConnected: Boolean get() = serial.isConnected
    val connectedPortName: String? get() = serial.connectedPortName
    fun availablePorts(): List<String> = serial.availablePorts()

    fun isDebugLoggingEnabled(): Boolean = debugLoggingEnabled.get()
    fun setDebugLoggingEnabled(enabled: Boolean) { debugLoggingEnabled.set(enabled) }

    fun addDebugListener(listener: (String) -> Unit): () -> Unit {
        debugListeners += listener
        return { debugListeners -= listener }
    }

    fun addFailureListener(listener: (LightOperationResult.SerialFailure) -> Unit): () -> Unit {
        failureListeners += listener
        return { failureListeners -= listener }
    }

    // ── connect / disconnect ──────────────────────────────────────────────────

    fun connectAsync(
        portNameInput: String?,
        baudRateInput: String,
        onComplete: (LightOperationResult) -> Unit,
    ) {
        val portName = portNameInput?.trim().orEmpty()
        if (portName.isBlank()) {
            deliverOnFx { onComplete(LightOperationResult.MissingSerialPort) }
            return
        }
        val baudRate = baudRateInput.trim().toIntOrNull()
            ?: AdvancedWledSerial.DEFAULT_BAUD_RATE.also {
                if (baudRateInput.isNotBlank()) {
                    deliverOnFx {
                        onComplete(LightOperationResult.InvalidBaudRate(baudRateInput))
                    }
                    return
                }
            }

        serialExecutor.execute {
            val result = try {
                serial.connect(portName, baudRate)
                // Query segments after successful connection
                val queryResult = queryAndApplySegments()
                if (queryResult is LightOperationResult.SerialFailure) {
                    // Connection succeeded but query failed — still report connected
                    deliverOnFx {
                        failureListeners.forEach { it(queryResult) }
                    }
                }
                LightOperationResult.Applied
            } catch (error: Exception) {
                LightOperationResult.SerialConnectionFailed(
                    portName = portName,
                    details = error.messageOrClassName(),
                )
            }
            deliverOnFx { onComplete(result) }
        }
    }

    fun disconnectAsync(onComplete: (LightOperationResult) -> Unit) {
        serialExecutor.execute {
            serial.disconnect()
            deliverOnFx { onComplete(LightOperationResult.Applied) }
        }
    }

    /**
     * Sends a state update for a single segment to the device.
     *
     * Call from the JavaFX thread; the write is dispatched to the serial executor.
     */
    fun sendSegmentUpdate(segment: AdvancedSegment) {
        if (!serial.isConnected) return
        val id = segment.id
        val on = segment.isOn
        val color = segment.color
        val effect = segment.effect
        val brightness = segment.brightness
        val speed = segment.effectSpeed
        val intensity = segment.effectIntensity

        serialExecutor.execute {
            runCatching {
                val json = serial.sendSegmentState(id, on, color, effect, brightness, speed, intensity)
                appendDebugLine(json)
            }.onFailure { error ->
                notifySerialWriteFailure(error)
            }
        }
    }

    /**
     * Refreshes the segment list by querying the device.
     *
     * Must be called from the JavaFX thread; the query runs on the serial executor.
     */
    fun refreshSegmentsAsync(onComplete: (LightOperationResult) -> Unit) {
        if (!serial.isConnected) {
            deliverOnFx { onComplete(LightOperationResult.MissingSerialPort) }
            return
        }
        serialExecutor.execute {
            val result = queryAndApplySegments()
            deliverOnFx { onComplete(result) }
        }
    }

    /** Saves configuration and shuts down the serial executor. */
    fun shutdown() {
        saveConfig()
        serialExecutor.shutdown()
        Thread({
            try {
                if (!serialExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    serialExecutor.shutdownNow()
                    serialExecutor.awaitTermination(1, TimeUnit.SECONDS)
                }
            } catch (_: InterruptedException) {
                serialExecutor.shutdownNow()
                Thread.currentThread().interrupt()
            } finally {
                serial.disconnect()
            }
        }, "adv-wled-shutdown").apply {
            isDaemon = true
            start()
        }
    }

    // ── internal helpers ──────────────────────────────────────────────────────

    /**
     * Sends the `s` command, parses the response, and updates [segments] on
     * the FX thread.  Must be called from the serial executor thread.
     *
     * @return [LightOperationResult.Applied] on success, or a [LightOperationResult.SerialFailure]
     */
    private fun queryAndApplySegments(): LightOperationResult {
        return try {
            val json = serial.queryStatus()
            appendDebugLine(">>> s")
            appendDebugLine(json)
            val parsed = WledJsonParser.parseSegments(json)
            if (parsed.isEmpty()) {
                return LightOperationResult.SerialWriteFailed("No segments found in WLED response")
            }
            deliverOnFx { applyParsedSegments(parsed) }
            LightOperationResult.Applied
        } catch (error: Exception) {
            val failure = LightOperationResult.SerialConnectionFailed(
                portName = serial.connectedPortName ?: "?",
                details = error.messageOrClassName(),
            )
            appendDebugLine("ERROR querying segments: ${error.messageOrClassName()}")
            failure
        }
    }

    /**
     * Merges [parsedSegments] with persisted config and updates [segments].
     * Must be called on the JavaFX application thread.
     */
    private fun applyParsedSegments(parsedSegments: List<WledJsonParser.WledSegment>) {
        // Build a lookup from the device response
        val byId = parsedSegments.associateBy { it.id }

        // Determine display order: persisted order first, then any new ids appended
        val persistedOrder = config.segmentOrder.filter { it in byId }
        val newIds = parsedSegments.map { it.id }.filter { it !in persistedOrder }
        val orderedIds = persistedOrder + newIds

        val newSegments = orderedIds.mapNotNull { id ->
            val raw = byId[id] ?: return@mapNotNull null
            AdvancedSegment(
                id = raw.id,
                startLed = raw.start,
                stopLed = raw.stop,
                ledCount = raw.len,
            ).also { seg ->
                // Apply persisted name, or default to "Segment $id"
                seg.name = config.segmentNames[id] ?: "Segment ${seg.id}"

                // Apply persisted state, falling back to what the device reported
                val saved = config.segmentStates[id]
                if (saved != null) {
                    seg.color = saved.color
                    seg.effect = saved.effect
                    seg.brightness = saved.brightness
                    seg.effectSpeed = saved.effectSpeed
                    seg.effectIntensity = saved.effectIntensity
                } else {
                    seg.brightnessRaw = raw.bri
                    seg.color = rawRgbToHex(raw.r, raw.g, raw.b)
                    seg.effect = LightEffect.entries.firstOrNull { it.wledEffectId == raw.fx }
                        ?: LightEffect.NONE
                    seg.effectSpeed = raw.sx
                    seg.effectIntensity = raw.ix
                }
                seg.isOn = raw.on
            }
        }

        segments.setAll(newSegments)
    }

    private fun saveConfig() {
        // Snapshot on calling thread (may or may not be FX thread at shutdown)
        val snapshotSegments = try {
            Platform.runLater {} // flush pending FX tasks
            segments.toList()
        } catch (_: Exception) {
            emptyList()
        }

        val names = snapshotSegments.associate { it.id to it.name }
        val order = snapshotSegments.map { it.id }
        val states = snapshotSegments.associate { seg ->
            seg.id to SegmentSavedState(
                color = seg.color,
                effect = seg.effect,
                brightness = seg.brightness,
                effectSpeed = seg.effectSpeed,
                effectIntensity = seg.effectIntensity,
            )
        }
        AdvancedLightPersistence.save(
            AdvancedLightConfig(
                segmentNames = names,
                segmentOrder = order,
                segmentStates = states,
            )
        )
    }

    private fun appendDebugLine(line: String) {
        if (!debugLoggingEnabled.get()) return
        val ts = java.time.LocalTime.now().withNano(0)
        val formatted = "[$ts] $line"
        deliverOnFx {
            debugListeners.forEach { it(formatted) }
        }
    }

    private fun notifySerialWriteFailure(error: Throwable) {
        val message = error.messageOrClassName()
        System.err.println("ADV-WLED serial write failed: $message")
        appendDebugLine("ERROR write failed: $message")
        val failure = LightOperationResult.SerialWriteFailed(message)
        deliverOnFx {
            failureListeners.forEach { it(failure) }
        }
    }

    private fun deliverOnFx(action: () -> Unit) {
        if (Platform.isFxApplicationThread()) action() else Platform.runLater(action)
    }

    private fun Throwable.messageOrClassName(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private fun rawRgbToHex(r: Int, g: Int, b: Int): String =
        "#%02X%02X%02X".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
}
