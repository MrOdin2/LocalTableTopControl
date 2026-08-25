package com.tabletopcontrol.hotkey

import com.tabletopcontrol.core.MusicControlOperation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HotkeyExecutorTest {
    @Test
    fun `actions before a wait execute together and remaining actions continue later`() {
        val immediateActions = mutableListOf<HotkeyAction>()
        val scheduled = mutableListOf<ScheduledAction>()
        val light = LightAction(power = true)
        val sound = PlaySoundAction("file:///thunder.mp3")
        val music = MusicAction(MusicControlOperation.START, "file:///battle.mp3")
        val definition = HotkeyDefinition(
            id = "storm",
            name = "Storm",
            actions = listOf(light, sound, WaitAction(750), music),
        )
        val executor = HotkeyExecutor(
            definitions = { listOf(definition) },
            scheduler = HotkeyScheduler { delay, action -> scheduled += ScheduledAction(delay, action) },
            sink = HotkeyActionSink(immediateActions::add),
        )

        assertEquals(HotkeyExecutor.TriggerResult.STARTED, executor.trigger("storm"))
        assertEquals(listOf(light, sound), immediateActions)
        assertEquals(1, scheduled.size)
        assertEquals(750, scheduled.single().delayMillis)

        scheduled.single().action()
        assertEquals(listOf(light, sound, music), immediateActions)
    }

    @Test
    fun `circular hotkey references stop at the repeated definition`() {
        val executed = mutableListOf<HotkeyAction>()
        val sound = PlaySoundAction("file:///safe.mp3")
        val definitions = listOf(
            HotkeyDefinition("a", "A", actions = listOf(TriggerHotkeyAction("b"))),
            HotkeyDefinition("b", "B", actions = listOf(TriggerHotkeyAction("a"), sound)),
        )
        val executor = HotkeyExecutor(
            definitions = { definitions },
            scheduler = HotkeyScheduler { _, action -> action() },
            sink = HotkeyActionSink(executed::add),
        )

        executor.trigger("a")

        assertEquals(listOf(sound), executed)
    }

    @Test
    fun `executor enforces the sixteen action limit`() {
        val executed = mutableListOf<HotkeyAction>()
        val actions = (1..20).map { PlaySoundAction("file:///$it.mp3") }
        val executor = HotkeyExecutor(
            definitions = { listOf(HotkeyDefinition("limited", "Limited", actions = actions)) },
            scheduler = HotkeyScheduler { _, action -> action() },
            sink = HotkeyActionSink(executed::add),
        )

        executor.trigger("limited")

        assertEquals(MAX_ACTIONS_PER_HOTKEY, executed.size)
    }

    @Test
    fun `missing hotkey reports missing without executing`() {
        val executor = HotkeyExecutor(
            definitions = { emptyList() },
            scheduler = HotkeyScheduler { _, action -> action() },
            sink = HotkeyActionSink { error("Nothing should execute") },
        )

        assertEquals(HotkeyExecutor.TriggerResult.MISSING, executor.trigger("missing"))
    }

    private data class ScheduledAction(val delayMillis: Long, val action: () -> Unit)
}
