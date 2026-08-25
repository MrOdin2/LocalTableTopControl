package com.tabletopcontrol.hotkey

internal fun interface HotkeyScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit)
}

internal fun interface HotkeyActionSink {
    fun execute(action: HotkeyAction)
}

/** Executes immediate action groups separated by waits and carries ancestry across delays. */
internal class HotkeyExecutor(
    private val definitions: () -> List<HotkeyDefinition>,
    private val scheduler: HotkeyScheduler,
    private val sink: HotkeyActionSink,
) {
    enum class TriggerResult {
        STARTED,
        MISSING,
        CIRCULAR,
    }

    fun trigger(hotkeyId: String): TriggerResult = trigger(hotkeyId, emptySet())

    private fun trigger(hotkeyId: String, ancestry: Set<String>): TriggerResult {
        if (hotkeyId in ancestry) return TriggerResult.CIRCULAR
        val definition = definitions().firstOrNull { it.id == hotkeyId } ?: return TriggerResult.MISSING
        val actions = definition.actions.take(MAX_ACTIONS_PER_HOTKEY)
        executeFrom(actions, 0, ancestry + hotkeyId)
        return TriggerResult.STARTED
    }

    private fun executeFrom(actions: List<HotkeyAction>, startIndex: Int, ancestry: Set<String>) {
        var index = startIndex
        while (index < actions.size) {
            when (val action = actions[index]) {
                is WaitAction -> {
                    val nextIndex = index + 1
                    val delay = action.durationMillis.coerceIn(0, MAX_WAIT_MILLIS)
                    if (delay == 0L) {
                        index = nextIndex
                        continue
                    }
                    scheduler.schedule(delay) { executeFrom(actions, nextIndex, ancestry) }
                    return
                }

                is TriggerHotkeyAction -> trigger(action.hotkeyId, ancestry)
                else -> sink.execute(action)
            }
            index++
        }
    }
}
