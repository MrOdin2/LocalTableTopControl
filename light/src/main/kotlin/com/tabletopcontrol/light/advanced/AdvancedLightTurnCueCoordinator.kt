package com.tabletopcontrol.light.advanced

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal fun interface AdvancedLightTurnCueTask {
    fun cancel()
}

internal interface AdvancedLightTurnCueScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): AdvancedLightTurnCueTask

    fun shutdown()
}

internal class ExecutorAdvancedLightTurnCueScheduler : AdvancedLightTurnCueScheduler {
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "advanced-light-turn-cue").also { it.isDaemon = true }
    }

    override fun schedule(delayMillis: Long, action: () -> Unit): AdvancedLightTurnCueTask {
        val future = executor.schedule(action, delayMillis, TimeUnit.MILLISECONDS)
        return AdvancedLightTurnCueTask { future.cancel(false) }
    }

    override fun shutdown() {
        executor.shutdownNow()
    }
}

/**
 * Applies tracker-driven WLED commands without mutating the segment's remembered base state.
 * This lets a cue restore the latest manual settings when it ends.
 */
internal class AdvancedLightTurnCueCoordinator(
    private val controller: AdvancedLightController,
    private val sendCommands: (List<AdvancedLightSegmentCommand>) -> Unit,
    private val scheduler: AdvancedLightTurnCueScheduler = ExecutorAdvancedLightTurnCueScheduler(),
) {
    private val lock = Any()
    private val activeSegmentIds = mutableSetOf<Int>()
    private val scheduledTasks = mutableListOf<AdvancedLightTurnCueTask>()
    private var enabled: Boolean = false
    private var activeTokenId: String? = null
    private var generation: Long = 0L

    fun setEnabled(enabled: Boolean) {
        synchronized(lock) {
            if (this.enabled == enabled) return
            this.enabled = enabled
            restartCueLocked()
        }
    }

    fun activeTokenChanged(tokenId: String?) {
        synchronized(lock) {
            activeTokenId = tokenId
            restartCueLocked()
        }
    }

    fun refresh() {
        synchronized(lock) {
            restartCueLocked()
        }
    }

    fun shutdown() {
        synchronized(lock) {
            enabled = false
            generation++
            cancelScheduledTasksLocked()
            restoreActiveSegmentsLocked()
        }
        scheduler.shutdown()
    }

    private fun restartCueLocked() {
        generation++
        cancelScheduledTasksLocked()
        restoreActiveSegmentsLocked()

        val tokenId = activeTokenId?.takeIf { enabled } ?: return
        val cueSegments = controller.turnCueSegmentsForToken(tokenId)
        if (cueSegments.isEmpty()) return

        activeSegmentIds += cueSegments.map(AdvancedLightSegmentState::id)
        sendCommands(cueSegments.map(AdvancedLightSegmentState::toTurnCueCommand))

        val cueGeneration = generation
        cueSegments
            .filter { it.turnCue.duration == AdvancedLightTurnCueDuration.TIMED }
            .groupBy { it.turnCue.durationMillis }
            .forEach { (durationMillis, segments) ->
                val segmentIds = segments.map(AdvancedLightSegmentState::id).toSet()
                scheduledTasks += scheduler.schedule(durationMillis) {
                    completeTimedCue(cueGeneration, segmentIds)
                }
            }
    }

    private fun completeTimedCue(
        cueGeneration: Long,
        segmentIds: Set<Int>,
    ) {
        synchronized(lock) {
            if (cueGeneration != generation) return
            val idsToRestore = segmentIds intersect activeSegmentIds
            if (idsToRestore.isEmpty()) return
            sendCommands(controller.commandsForSegments(idsToRestore))
            activeSegmentIds.removeAll(idsToRestore)
        }
    }

    private fun restoreActiveSegmentsLocked() {
        if (activeSegmentIds.isEmpty()) return
        sendCommands(controller.commandsForSegments(activeSegmentIds))
        activeSegmentIds.clear()
    }

    private fun cancelScheduledTasksLocked() {
        scheduledTasks.forEach(AdvancedLightTurnCueTask::cancel)
        scheduledTasks.clear()
    }
}
