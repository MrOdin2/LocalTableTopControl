package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.light.LightEffect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdvancedLightTurnCueCoordinatorTest {
    @Test
    fun `timed cues restore once while whole turn cues restore on turn change`() {
        val controller = AdvancedLightController()
        controller.loadFromDevice(
            WledDeviceSnapshot(
                segments = listOf(
                    WledSegmentSnapshot(0, true, false, "#111111", 0.4, LightEffect.NONE, 10, 20),
                    WledSegmentSnapshot(1, true, false, "#222222", 0.5, LightEffect.CANDLE, 30, 40),
                ),
            ),
            AdvancedLightPreferences(
                segments = mapOf(
                    0 to AdvancedLightSegmentPreference(
                        assignedTokenIds = setOf("hero"),
                        turnCue = AdvancedLightTurnCue(
                            effect = LightEffect.BLINK,
                            duration = AdvancedLightTurnCueDuration.TIMED,
                            durationMillis = 900,
                        ),
                    ),
                    1 to AdvancedLightSegmentPreference(
                        assignedTokenIds = setOf("hero"),
                        turnCue = AdvancedLightTurnCue(
                            effect = LightEffect.HEARTBEAT,
                            duration = AdvancedLightTurnCueDuration.WHOLE_TURN,
                        ),
                    ),
                ),
            ),
        )
        val scheduler = RecordingTurnCueScheduler()
        val sent = mutableListOf<List<AdvancedLightSegmentCommand>>()
        val coordinator = AdvancedLightTurnCueCoordinator(controller, sent::add, scheduler)

        coordinator.setEnabled(true)
        coordinator.activeTokenChanged("hero")

        assertEquals(listOf(0, 1), sent.single().map { it.id })
        assertEquals(listOf(LightEffect.BLINK, LightEffect.HEARTBEAT), sent.single().map { it.effect })
        assertEquals(900L, scheduler.scheduled.single().delayMillis)

        scheduler.scheduled.single().run()
        assertEquals(listOf(0), sent[1].map { it.id })
        assertEquals(LightEffect.NONE, sent[1].single().effect)

        coordinator.activeTokenChanged(null)
        assertEquals(listOf(1), sent[2].map { it.id })
        assertEquals(LightEffect.CANDLE, sent[2].single().effect)

        coordinator.shutdown()
    }

    private class RecordingTurnCueScheduler : AdvancedLightTurnCueScheduler {
        val scheduled = mutableListOf<ScheduledAction>()

        override fun schedule(delayMillis: Long, action: () -> Unit): AdvancedLightTurnCueTask {
            val scheduledAction = ScheduledAction(delayMillis, action)
            scheduled += scheduledAction
            return AdvancedLightTurnCueTask { scheduledAction.cancelled = true }
        }

        override fun shutdown() = Unit
    }

    private data class ScheduledAction(
        val delayMillis: Long,
        private val action: () -> Unit,
        var cancelled: Boolean = false,
    ) {
        fun run() {
            if (!cancelled) action()
        }
    }
}
