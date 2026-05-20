package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.light.LightEffect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AdvancedLightSegmentSendQueueTest {
    @Test
    fun `polls pending commands in round robin order`() {
        val queue = AdvancedLightSegmentSendQueue()
        queue.offer(listOf(command(0, 10), command(1, 10), command(2, 10)))

        assertEquals(0, queue.poll()?.id)
        queue.offer(listOf(command(0, 20), command(1, 20), command(2, 20)))

        assertEquals(1, queue.poll()?.id)
        assertEquals(2, queue.poll()?.id)
        assertEquals(0, queue.poll()?.id)
    }

    @Test
    fun `keeps only the latest pending command per segment`() {
        val queue = AdvancedLightSegmentSendQueue()
        queue.offer(listOf(command(0, 254), command(1, 254)))
        assertEquals(0, queue.poll()?.id)

        queue.offer(listOf(command(0, 253)))
        queue.offer(listOf(command(0, 252)))
        queue.offer(listOf(command(1, 252)))

        assertEquals(command(1, 252), queue.poll())
        assertEquals(command(0, 252), queue.poll())
        assertNull(queue.poll())
    }

    private fun command(id: Int, brightness: Int): AdvancedLightSegmentCommand =
        AdvancedLightSegmentCommand(
            id = id,
            on = true,
            color = "#FFFFFF",
            brightness = brightness / 255.0,
            effect = LightEffect.NONE,
            effectSpeed = 128,
            effectIntensity = 128,
        )
}
