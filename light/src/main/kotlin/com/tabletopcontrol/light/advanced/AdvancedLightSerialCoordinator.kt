package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.light.LightOperationResult
import com.tabletopcontrol.light.WledSerialSender
import javafx.application.Platform
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class AdvancedLightSerialCoordinator(
    private val sender: WledSerialSender = WledSerialSender(),
) {
    private val serialExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "advanced-wled-serial").also { it.isDaemon = true }
    }
    private val debugListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val failureListeners =
        CopyOnWriteArrayList<(LightOperationResult.SerialFailure) -> Unit>()
    private val queuedSegmentCommands = AdvancedLightSegmentSendQueue()
    private val queueLock = Any()
    private val queueDrainScheduled = AtomicBoolean(false)
    @Volatile
    private var debugLoggingEnabled = false

    val isConnected: Boolean
        get() = sender.isConnected

    val connectedPortName: String?
        get() = sender.connectedPortName

    fun availablePorts(): List<String> = sender.availablePorts()

    fun isDebugLoggingEnabled(): Boolean = debugLoggingEnabled

    fun setDebugLoggingEnabled(enabled: Boolean) {
        debugLoggingEnabled = enabled
    }

    fun addDebugListener(listener: (String) -> Unit): () -> Unit {
        debugListeners += listener
        return { debugListeners -= listener }
    }

    fun addFailureListener(listener: (LightOperationResult.SerialFailure) -> Unit): () -> Unit {
        failureListeners += listener
        return { failureListeners -= listener }
    }

    fun connectAndQueryAsync(
        portNameInput: String?,
        baudRateInput: String,
        onComplete: (LightOperationResult, WledDeviceSnapshot?) -> Unit,
    ) {
        val portName = portNameInput?.trim().orEmpty()
        if (portName.isBlank()) {
            deliverOnFx { onComplete(LightOperationResult.MissingSerialPort, null) }
            return
        }

        val baudRate = parseBaudRate(baudRateInput)
        if (baudRate == null) {
            deliverOnFx { onComplete(LightOperationResult.InvalidBaudRate(baudRateInput), null) }
            return
        }

        serialExecutor.execute {
            val connected = try {
                sender.connect(portName, baudRate)
                appendDebug("CONNECTED $portName @ $baudRate")
                true
            } catch (error: Exception) {
                val failure = LightOperationResult.SerialConnectionFailed(
                    portName = portName,
                    details = error.messageOrClassName(),
                )
                deliverOnFx { onComplete(failure, null) }
                false
            }
            if (!connected) return@execute

            val queryResult = queryDeviceSnapshot()
            deliverOnFx {
                onComplete(queryResult.result, queryResult.snapshot)
            }
        }
    }

    fun disconnectAsync(onComplete: (LightOperationResult) -> Unit) {
        clearQueuedSegmentCommands()
        serialExecutor.execute {
            clearQueuedSegmentCommands()
            sender.disconnect()
            appendDebug("DISCONNECTED")
            deliverOnFx { onComplete(LightOperationResult.Applied) }
        }
    }

    fun querySegmentsAsync(onComplete: (LightOperationResult, WledDeviceSnapshot?) -> Unit) {
        serialExecutor.execute {
            val queryResult = queryDeviceSnapshot()
            deliverOnFx { onComplete(queryResult.result, queryResult.snapshot) }
        }
    }

    fun sendSegmentsAsync(
        commands: List<AdvancedLightSegmentCommand>,
        splitCommands: Boolean = false,
    ) {
        if (commands.isEmpty()) return
        if (splitCommands) {
            enqueueSegmentCommands(commands)
            return
        }

        serialExecutor.execute {
            if (!sender.isConnected) return@execute
            try {
                val sentJson = sender.sendSegmentJson(commands)
                appendDebug("TX $sentJson")
            } catch (error: Exception) {
                reportFailure(LightOperationResult.SerialWriteFailed(error.messageOrClassName()))
            }
        }
    }

    fun shutdown() {
        clearQueuedSegmentCommands()
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
                clearQueuedSegmentCommands()
                sender.disconnect()
            }
        }, "advanced-wled-serial-shutdown").apply {
            isDaemon = true
            start()
        }
    }

    private fun queryDeviceSnapshot(): QueryResult {
        if (!sender.isConnected) {
            return QueryResult(
                result = LightOperationResult.SerialQueryFailed("Not connected to a serial port"),
                snapshot = null,
            )
        }

        return try {
            appendDebug("""TX {"v":true}""")
            val responseJson = sender.queryDeviceStateJson()
            appendDebug("RX $responseJson")
            QueryResult(
                result = LightOperationResult.Applied,
                snapshot = AdvancedLightJson.parseDeviceSnapshot(responseJson),
            )
        } catch (error: Exception) {
            QueryResult(
                result = LightOperationResult.SerialQueryFailed(error.messageOrClassName()),
                snapshot = null,
            )
        }
    }

    private fun enqueueSegmentCommands(commands: List<AdvancedLightSegmentCommand>) {
        synchronized(queueLock) {
            queuedSegmentCommands.offer(commands)
        }
        scheduleQueuedSegmentDrain()
    }

    private fun scheduleQueuedSegmentDrain() {
        if (queueDrainScheduled.compareAndSet(false, true)) {
            serialExecutor.execute { drainQueuedSegmentCommands() }
        }
    }

    private fun drainQueuedSegmentCommands() {
        while (true) {
            val command = synchronized(queueLock) {
                queuedSegmentCommands.poll()
            }

            if (command == null) {
                queueDrainScheduled.set(false)
                val shouldRestart = synchronized(queueLock) { !queuedSegmentCommands.isEmpty() }
                if (shouldRestart && queueDrainScheduled.compareAndSet(false, true)) {
                    continue
                }
                return
            }

            if (!sender.isConnected) {
                clearQueuedSegmentCommands()
                queueDrainScheduled.set(false)
                return
            }

            try {
                val sentJson = sender.sendSegmentJson(listOf(command))
                appendDebug("TX $sentJson")
            } catch (error: Exception) {
                reportFailure(LightOperationResult.SerialWriteFailed(error.messageOrClassName()))
            }

            try {
                Thread.sleep(INTER_SEGMENT_SEND_DELAY_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                clearQueuedSegmentCommands()
                queueDrainScheduled.set(false)
                return
            }
        }
    }

    private fun clearQueuedSegmentCommands() {
        synchronized(queueLock) {
            queuedSegmentCommands.clear()
        }
    }

    private fun reportFailure(failure: LightOperationResult.SerialFailure) {
        appendDebug("ERROR ${failure.details ?: failure.operatorMessage}")
        deliverOnFx {
            failureListeners.forEach { it(failure) }
        }
    }

    private fun appendDebug(line: String) {
        if (!debugLoggingEnabled) return

        val fullLine = "[${java.time.LocalTime.now().withNano(0)}] $line"
        deliverOnFx {
            debugListeners.forEach { it(fullLine) }
        }
    }

    private fun parseBaudRate(input: String): Int? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return WledSerialSender.DEFAULT_BAUD_RATE
        return trimmed.toIntOrNull()
    }

    private fun deliverOnFx(action: () -> Unit) {
        if (Platform.isFxApplicationThread()) {
            action()
        } else {
            Platform.runLater(action)
        }
    }

    private fun Throwable.messageOrClassName(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private data class QueryResult(
        val result: LightOperationResult,
        val snapshot: WledDeviceSnapshot?,
    )

    private companion object {
        private const val INTER_SEGMENT_SEND_DELAY_MS: Long = 100
    }
}
