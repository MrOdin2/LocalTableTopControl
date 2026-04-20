package com.tabletopcontrol.map

import com.tabletopcontrol.core.ActiveTokenChangedEvent
import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.TokenAddedEvent
import com.tabletopcontrol.core.TokenImageChangedEvent
import com.tabletopcontrol.core.TokenMovedEvent
import com.tabletopcontrol.map.logic.MapTokenSyncService
import com.tabletopcontrol.map.logic.Token
import javafx.scene.paint.Color
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapTokenSyncServiceTest {

    @AfterEach
    fun tearDown() {
        EventBus.clear()
    }

    @Test
    fun `replay republishes tracked token state`() {
        val service = MapTokenSyncService()

        EventBus.publish(TokenAddedEvent("1", "Goblin", Color.RED))
        EventBus.publish(TokenMovedEvent("1", "Goblin", 4, 6))
        EventBus.publish(
            TokenImageChangedEvent(
                id = "1",
                imageUri = "file:///tmp/goblin.png",
                imageScaleX = 1.2,
                imageScaleY = 0.8,
                imageOffsetX = 3.0,
                imageOffsetY = -2.0,
            ),
        )
        EventBus.publish(ActiveTokenChangedEvent("1", "Goblin"))

        val replayedEvents = mutableListOf<Any>()
        EventBus.subscribe<TokenAddedEvent> { replayedEvents += it }
        EventBus.subscribe<TokenMovedEvent> { replayedEvents += it }
        EventBus.subscribe<TokenImageChangedEvent> { replayedEvents += it }
        EventBus.subscribe<ActiveTokenChangedEvent> { replayedEvents += it }

        service.replayState()
        service.dispose()

        assertEquals(4, replayedEvents.size)
        assertEquals(TokenAddedEvent("1", "Goblin", Color.RED), replayedEvents[0])
        assertEquals(TokenMovedEvent("1", "Goblin", 4, 6), replayedEvents[1])
        assertEquals(
            TokenImageChangedEvent(
                id = "1",
                imageUri = "file:///tmp/goblin.png",
                imageScaleX = 1.2,
                imageScaleY = 0.8,
                imageOffsetX = 3.0,
                imageOffsetY = -2.0,
            ),
            replayedEvents[2],
        )
        assertEquals(ActiveTokenChangedEvent("1", null), replayedEvents[3])
    }

    @Test
    fun `drag publishing skips duplicate cells`() {
        val service = MapTokenSyncService()
        val publishedMoves = mutableListOf<TokenMovedEvent>()

        EventBus.publish(TokenAddedEvent("1", "Goblin", Color.RED))
        EventBus.subscribe<TokenMovedEvent> { publishedMoves += it }

        service.beginDrag(Token(id = "1", name = "Goblin", col = 0, row = 0, color = Color.RED))
        service.publishDraggedTokenMove(Pair(2, 2))
        service.publishDraggedTokenMove(Pair(2, 2))
        service.endDrag()
        service.dispose()

        assertEquals(1, publishedMoves.size)
        assertEquals(TokenMovedEvent("1", "Goblin", 2, 2), publishedMoves.single())
    }

    @Test
    fun `drag publishing without active token returns typed error`() {
        val service = MapTokenSyncService()

        val result = service.publishDraggedTokenMove(Pair(1, 1))
        service.dispose()

        assertTrue(result is MapResult.Failure)
        assertEquals(
            MapOperationError.TokenDragNotActive,
            (result as MapResult.Failure).error,
        )
    }
}
