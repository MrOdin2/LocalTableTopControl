package com.tabletopcontrol.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TokenImageChangedEventTest {

    @AfterEach
    fun tearDown() = EventBus.clear()

    @Test
    fun `event carries id and imageUri`() {
        val event = TokenImageChangedEvent(
            id = "abc-123",
            imageUri = "file:///images/wizard.png",
            imageScaleX = 1.2,
            imageScaleY = 0.9,
            imageOffsetX = 3.0,
            imageOffsetY = -2.0,
        )
        assertEquals("abc-123", event.id)
        assertEquals("file:///images/wizard.png", event.imageUri)
        assertEquals(1.2, event.imageScaleX)
        assertEquals(0.9, event.imageScaleY)
        assertEquals(3.0, event.imageOffsetX)
        assertEquals(-2.0, event.imageOffsetY)
    }

    @Test
    fun `event allows null imageUri to clear a token picture`() {
        val event = TokenImageChangedEvent(id = "abc-123", imageUri = null)
        assertEquals("abc-123", event.id)
        assertNull(event.imageUri)
        assertEquals(1.0, event.imageScaleX)
        assertEquals(1.0, event.imageScaleY)
        assertEquals(0.0, event.imageOffsetX)
        assertEquals(0.0, event.imageOffsetY)
    }

    @Test
    fun `subscriber receives TokenImageChangedEvent via EventBus`() {
        var received: TokenImageChangedEvent? = null
        EventBus.subscribe<TokenImageChangedEvent> { received = it }

        val event = TokenImageChangedEvent(
            id = "xyz",
            imageUri = "file:///token.png",
            imageScaleX = 0.8,
            imageScaleY = 1.1,
            imageOffsetX = -4.0,
            imageOffsetY = 6.0,
        )
        EventBus.publish(event)

        assertEquals(event, received)
    }
}
