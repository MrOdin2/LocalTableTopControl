package com.tabletopcontrol.map

import com.tabletopcontrol.core.ActiveTokenChangedEvent
import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.TokenAddedEvent
import com.tabletopcontrol.core.TokenEffect
import com.tabletopcontrol.core.TokenEffectsChangedEvent
import com.tabletopcontrol.core.TokenImageChangedEvent
import com.tabletopcontrol.core.TokenMoveDirection
import com.tabletopcontrol.core.TokenMoveRequestedEvent
import com.tabletopcontrol.core.TokenMovedEvent
import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.core.TokensResetEvent
import com.tabletopcontrol.map.logic.MapTokenSyncService
import com.tabletopcontrol.map.logic.Token
import javafx.scene.paint.Color
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class MapTokenSyncServiceTest {

    @AfterEach
    fun tearDown() {
        EventBus.clear()
    }

    @Test
    fun `replay republishes tracked token state`() {
        val service = MapTokenSyncService()

        EventBus.publish(TokenAddedEvent("1", "Goblin", Color.RED, TokenSize.LARGE))
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
        assertEquals(TokenAddedEvent("1", "Goblin", Color.RED, TokenSize.LARGE), replayedEvents[0])
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
    fun `token effects are retained and replayed with token state`() {
        val service = MapTokenSyncService()
        val effects = listOf(
            TokenEffect(id = "poisoned", name = "Poisoned", icon = "☠", durationRounds = 2),
        )
        EventBus.publish(TokenAddedEvent("1", "Goblin", Color.RED))
        EventBus.publish(TokenEffectsChangedEvent("1", effects))
        val replayedEffects = mutableListOf<TokenEffectsChangedEvent>()
        EventBus.subscribe<TokenEffectsChangedEvent> { replayedEffects += it }

        service.replayState()
        service.dispose()

        assertEquals(effects, service.snapshotTokens().single().effects)
        assertEquals(listOf(TokenEffectsChangedEvent("1", effects)), replayedEffects)
    }

    @Test
    fun `dragging a large token preserves the grabbed footprint cell`() {
        val service = MapTokenSyncService()
        val publishedMoves = mutableListOf<TokenMovedEvent>()

        EventBus.publish(TokenAddedEvent("1", "Ogre", Color.DARKRED, TokenSize.LARGE))
        EventBus.subscribe<TokenMovedEvent> { publishedMoves += it }

        service.beginDrag(
            Token(id = "1", name = "Ogre", col = 4, row = 6, size = TokenSize.LARGE, color = Color.DARKRED),
            grabbedCell = Pair(5, 7),
        )
        service.publishDraggedTokenMove(Pair(9, 10))
        service.endDrag()
        service.dispose()

        assertEquals(listOf(TokenMovedEvent("1", "Ogre", 8, 9)), publishedMoves)
    }

    @Test
    fun `relative move request publishes absolute move from current token position`() {
        val service = MapTokenSyncService()
        val mirrorService = MapTokenSyncService()
        val publishedMoves = mutableListOf<TokenMovedEvent>()

        EventBus.publish(TokenAddedEvent("1", "Goblin", Color.RED))
        EventBus.publish(TokenMovedEvent("1", "Goblin", 4, 6))
        EventBus.subscribe<TokenMovedEvent> { publishedMoves += it }

        EventBus.publish(TokenMoveRequestedEvent("1", "Goblin", TokenMoveDirection.SOUTH))
        mirrorService.dispose()
        service.dispose()

        assertEquals(listOf(TokenMovedEvent("1", "Goblin", 4, 7)), publishedMoves)
    }

    @Test
    fun `replace state republishes restored tokens after reset`() {
        val service = MapTokenSyncService()
        val existingImageUri = Files.createTempFile("goblin-token", ".png").toUri().toString()

        EventBus.publish(TokenAddedEvent("old", "Old Token", Color.GRAY))
        EventBus.publish(TokenMovedEvent("old", "Old Token", 9, 9))

        val replayedEvents = mutableListOf<Any>()
        EventBus.subscribe<TokensResetEvent> { replayedEvents += it }
        EventBus.subscribe<TokenAddedEvent> { replayedEvents += it }
        EventBus.subscribe<TokenMovedEvent> { replayedEvents += it }
        EventBus.subscribe<TokenImageChangedEvent> { replayedEvents += it }
        EventBus.subscribe<ActiveTokenChangedEvent> { replayedEvents += it }

        val restoredTokens = listOf(
            Token(
                id = "1",
                name = "Goblin",
                col = 4,
                row = 6,
                size = TokenSize.LARGE,
                color = Color.RED,
                imageUri = existingImageUri,
                imageScaleX = 1.2,
                imageScaleY = 0.8,
                imageOffsetX = 3.0,
                imageOffsetY = -2.0,
            ),
        )

        service.replaceState(restoredTokens, "1")
        service.dispose()

        assertEquals(restoredTokens, service.snapshotTokens())
        assertEquals("1", service.snapshotActiveTokenId())
        assertEquals(5, replayedEvents.size)
        assertTrue(replayedEvents[0] is TokensResetEvent)
        assertEquals(TokenAddedEvent("1", "Goblin", Color.RED, TokenSize.LARGE), replayedEvents[1])
        assertEquals(TokenMovedEvent("1", "Goblin", 4, 6), replayedEvents[2])
        assertEquals(
            TokenImageChangedEvent(
                id = "1",
                imageUri = existingImageUri,
                imageScaleX = 1.2,
                imageScaleY = 0.8,
                imageOffsetX = 3.0,
                imageOffsetY = -2.0,
            ),
            replayedEvents[3],
        )
        assertEquals(ActiveTokenChangedEvent("1", null), replayedEvents[4])
    }

    @Test
    fun `replace state keeps existing image settings when restored map token has none`() {
        val service = MapTokenSyncService()
        val existingImageUri = Files.createTempFile("goblin-token", ".png").toUri().toString()

        EventBus.publish(TokenAddedEvent("1", "Goblin", Color.RED, TokenSize.LARGE))
        EventBus.publish(
            TokenImageChangedEvent(
                id = "1",
                imageUri = existingImageUri,
                imageScaleX = 1.2,
                imageScaleY = 0.8,
                imageOffsetX = 3.0,
                imageOffsetY = -2.0,
            ),
        )

        service.replaceState(
            nextTokens = listOf(
                Token(
                    id = "1",
                    name = "Goblin",
                    col = 7,
                    row = 9,
                    size = TokenSize.LARGE,
                    color = Color.RED,
                ),
            ),
            nextActiveTokenId = "1",
        )
        service.dispose()

        assertEquals(
            listOf(
                Token(
                    id = "1",
                    name = "Goblin",
                    col = 7,
                    row = 9,
                    size = TokenSize.LARGE,
                    color = Color.RED,
                    imageUri = existingImageUri,
                    imageScaleX = 1.2,
                    imageScaleY = 0.8,
                    imageOffsetX = 3.0,
                    imageOffsetY = -2.0,
                ),
            ),
            service.snapshotTokens(),
        )
    }

    @Test
    fun `replace state keeps existing image settings when restored image path is unusable`() {
        val service = MapTokenSyncService()
        val existingImageUri = Files.createTempFile("goblin-token", ".png").toUri().toString()
        val missingImage = Files.createTempFile("missing-token-image", ".png").also { Files.deleteIfExists(it) }

        EventBus.publish(TokenAddedEvent("1", "Goblin", Color.RED, TokenSize.LARGE))
        EventBus.publish(
            TokenImageChangedEvent(
                id = "1",
                imageUri = existingImageUri,
                imageScaleX = 1.2,
                imageScaleY = 0.8,
                imageOffsetX = 3.0,
                imageOffsetY = -2.0,
            ),
        )

        service.replaceState(
            nextTokens = listOf(
                Token(
                    id = "1",
                    name = "Goblin",
                    col = 7,
                    row = 9,
                    size = TokenSize.LARGE,
                    color = Color.RED,
                    imageUri = missingImage.toUri().toString(),
                ),
            ),
            nextActiveTokenId = "1",
        )
        service.dispose()

        assertEquals(existingImageUri, service.snapshotTokens().single().imageUri)
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
