package com.tabletopcontrol.light

import javafx.application.Platform
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Coordinates serial connection lifecycle and coalesced background writes.
 *
 * This keeps the plugin focused on composing UI sections while the service
 * handles connection validation, state snapshot capture, and serial error flow.
 */
class LightSerialCoordinator(
    private val controller: LightController,
    private val sender: WledSerialSender = WledSerialSender(),
) {
    private val serialExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "wled-serial").also { it.isDaemon = true }
    }
    private val pendingWrite = AtomicBoolean(false)
    private val latestSnapshot = AtomicReference<LightStateSnapshot?>(null)
    private val debugLoggingEnabled = AtomicBoolean(false)
    private val lastSerialErrorLogNanos = AtomicReference(0L)
    private val serialWriteFailureReportedSinceSuccess = AtomicBoolean(false)
    private val debugListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val failureListeners =
        CopyOnWriteArrayList<(LightOperationResult.SerialFailure) -> Unit>()

    init {
        controller.addChangeListener { scheduleStateUpdate() }
    }

    val isConnected: Boolean
        get() = sender.isConnected

    val connectedPortName: String?
        get() = sender.connectedPortName

    fun availablePorts(): List<String> = sender.availablePorts()

    fun isDebugLoggingEnabled(): Boolean = debugLoggingEnabled.get()

    fun setDebugLoggingEnabled(enabled: Boolean) {
        debugLoggingEnabled.set(enabled)
    }

    fun addDebugListener(listener: (String) -> Unit): () -> Unit {
        debugListeners += listener
        return { debugListeners -= listener }
    }

    fun addFailureListener(listener: (LightOperationResult.SerialFailure) -> Unit): () -> Unit {
        failureListeners += listener
        return { failureListeners -= listener }
    }

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

        val baudRate = parseBaudRate(baudRateInput)
        if (baudRate == null) {
            deliverOnFx { onComplete(LightOperationResult.InvalidBaudRate(baudRateInput)) }
            return
        }

        serialExecutor.execute {
            val result = try {
                sender.connect(portName, baudRate)
                resetSerialErrorState()
                LightOperationResult.Applied
            } catch (error: Exception) {
                LightOperationResult.SerialConnectionFailed(
                    portName = portName,
                    details = error.messageOrClassName(),
                )
            }

            deliverOnFx {
                onComplete(result)
                if (result == LightOperationResult.Applied) {
                    scheduleStateUpdate()
                }
            }
        }
    }

    fun disconnectAsync(onComplete: (LightOperationResult) -> Unit) {
        serialExecutor.execute {
            sender.disconnect()
            resetSerialErrorState()
            deliverOnFx { onComplete(LightOperationResult.Applied) }
        }
    }

    fun shutdown() {
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
                sender.disconnect()
            }
        }, "wled-serial-shutdown").apply {
            isDaemon = true
            start()
        }
    }

    private fun scheduleStateUpdate() {
        if (Platform.isFxApplicationThread()) {
            publishSnapshotAndScheduleWrite()
        } else {
            Platform.runLater { publishSnapshotAndScheduleWrite() }
        }
    }

    private fun publishSnapshotAndScheduleWrite() {
        latestSnapshot.set(controller.snapshot())
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
                    val sentJson = next.toSerialCommand().sendWith(sender)
                    appendDebugCommand(sentJson)
                    resetSerialErrorState()
                } catch (error: Exception) {
                    reportSerialWriteFailure(error)
                }
            }
        }
    }

    private fun appendDebugCommand(commandJson: String) {
        if (!debugLoggingEnabled.get()) return

        val line = "[${currentLogTimestamp()}] $commandJson"
        deliverOnFx {
            debugListeners.forEach { it(line) }
        }
    }

    private fun reportSerialWriteFailure(error: Exception) {
        val message = error.messageOrClassName()
        val nowNanos = System.nanoTime()
        val previousLogNanos = lastSerialErrorLogNanos.get()
        val shouldLog = previousLogNanos == 0L ||
            nowNanos - previousLogNanos >= SERIAL_ERROR_LOG_THROTTLE_NANOS
        if (shouldLog && lastSerialErrorLogNanos.compareAndSet(previousLogNanos, nowNanos)) {
            System.err.println("WLED serial write failed: $message")
            error.printStackTrace()
        }

        if (serialWriteFailureReportedSinceSuccess.compareAndSet(false, true)) {
            val failure = LightOperationResult.SerialWriteFailed(message)
            appendDebugCommand("ERROR serial write failed: $message")
            deliverOnFx {
                failureListeners.forEach { it(failure) }
            }
        }
    }

    private fun resetSerialErrorState() {
        lastSerialErrorLogNanos.set(0L)
        serialWriteFailureReportedSinceSuccess.set(false)
    }

    private fun parseBaudRate(input: String): Int? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return WledSerialSender.DEFAULT_BAUD_RATE
        return trimmed.toIntOrNull()
    }

    private fun currentLogTimestamp(): java.time.LocalTime =
        java.time.LocalTime.now().withNano(0)

    private fun deliverOnFx(action: () -> Unit) {
        if (Platform.isFxApplicationThread()) {
            action()
        } else {
            Platform.runLater(action)
        }
    }

    private fun Throwable.messageOrClassName(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private companion object {
        private val SERIAL_ERROR_LOG_THROTTLE_NANOS: Long = TimeUnit.SECONDS.toNanos(2)
    }
}
