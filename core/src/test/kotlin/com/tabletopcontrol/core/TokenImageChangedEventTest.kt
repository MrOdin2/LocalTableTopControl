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
        val event = TokenImageChangedEvent(id = "abc-123", imageUri = "file:///images/wizard.png")
        assertEquals("abc-123", event.id)
        assertEquals("file:///images/wizard.png", event.imageUri)
    }

    @Test
    fun `event allows null imageUri to clear a token picture`() {
        val event = TokenImageChangedEvent(id = "abc-123", imageUri = null)
        assertEquals("abc-123", event.id)
        assertNull(event.imageUri)
    }

    @Test
    fun `subscriber receives TokenImageChangedEvent via EventBus`() {
        var received: TokenImageChangedEvent? = null
        EventBus.subscribe<TokenImageChangedEvent> { received = it }

        val event = TokenImageChangedEvent(id = "xyz", imageUri = "file:///token.png")
        EventBus.publish(event)

        assertEquals(event, received)
    }
}
