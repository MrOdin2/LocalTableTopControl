package com.tabletopcontrol.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EventBusTest {

    data class SampleEvent(val value: Int)
    data class OtherEvent(val text: String)

    @AfterEach
    fun tearDown() = EventBus.clear()

    @Test
    fun `subscriber receives published event`() {
        var received: SampleEvent? = null
        EventBus.subscribe<SampleEvent> { received = it }

        EventBus.publish(SampleEvent(42))

        assertEquals(SampleEvent(42), received)
    }

    @Test
    fun `subscriber does not receive events of a different type`() {
        var received: SampleEvent? = null
        EventBus.subscribe<SampleEvent> { received = it }

        EventBus.publish(OtherEvent("hello"))

        assertNull(received)
    }

    @Test
    fun `multiple subscribers all receive the same event`() {
        val results = mutableListOf<Int>()
        EventBus.subscribe<SampleEvent> { results.add(it.value * 1) }
        EventBus.subscribe<SampleEvent> { results.add(it.value * 2) }

        EventBus.publish(SampleEvent(3))

        assertEquals(listOf(3, 6), results)
    }

    @Test
    fun `clear removes all subscriptions`() {
        var received = false
        EventBus.subscribe<SampleEvent> { received = true }

        EventBus.clear()
        EventBus.publish(SampleEvent(1))

        assertFalse(received)
    }

    @Test
    fun `unsubscribe stops handler from receiving future events`() {
        var callCount = 0
        val subscription = EventBus.subscribe<SampleEvent> { callCount++ }

        EventBus.publish(SampleEvent(1))
        subscription.unsubscribe()
        EventBus.publish(SampleEvent(2))

        assertEquals(1, callCount)
    }

    @Test
    fun `unsubscribe only removes the specific handler`() {
        val results = mutableListOf<Int>()
        val sub1 = EventBus.subscribe<SampleEvent> { results.add(1) }
        EventBus.subscribe<SampleEvent> { results.add(2) }

        sub1.unsubscribe()
        EventBus.publish(SampleEvent(0))

        assertEquals(listOf(2), results)
    }
}
