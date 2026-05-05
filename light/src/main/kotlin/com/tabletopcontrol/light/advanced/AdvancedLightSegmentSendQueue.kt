package com.tabletopcontrol.light.advanced

/**
 * Coalesces per-segment writes while preserving round-robin fairness.
 *
 * Each segment keeps only its latest pending command. Polling walks the known
 * segment order, so all touched segments get a turn before a segment repeats.
 */
internal class AdvancedLightSegmentSendQueue {
    private val order = mutableListOf<Int>()
    private val pendingById = linkedMapOf<Int, AdvancedLightSegmentCommand>()
    private var nextIndex = 0

    fun offer(commands: List<AdvancedLightSegmentCommand>) {
        commands.forEach { command ->
            if (command.id !in order) {
                order += command.id
            }
            pendingById[command.id] = command
        }
        if (nextIndex >= order.size) {
            nextIndex = 0
        }
    }

    fun poll(): AdvancedLightSegmentCommand? {
        if (pendingById.isEmpty() || order.isEmpty()) return null

        repeat(order.size) {
            if (nextIndex >= order.size) {
                nextIndex = 0
            }
            val id = order[nextIndex]
            nextIndex = (nextIndex + 1) % order.size
            pendingById.remove(id)?.let { return it }
        }

        return null
    }

    fun isEmpty(): Boolean = pendingById.isEmpty()

    fun clear() {
        pendingById.clear()
        order.clear()
        nextIndex = 0
    }
}
