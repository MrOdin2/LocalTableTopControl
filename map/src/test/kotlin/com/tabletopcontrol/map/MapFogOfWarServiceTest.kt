package com.tabletopcontrol.map

import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.map.logic.MapFogOfWarService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MapFogOfWarServiceTest {

    @AfterEach
    fun tearDown() {
        EventBus.clear()
    }

    @Test
    fun `replay republishes fog setup and revealed cells`() {
        val service = MapFogOfWarService(halfFogCells = 2)
        val replayedEvents = mutableListOf<Any>()

        service.ensureInitialized()
        service.paintCell(Pair(1, 1), revealed = true)
        service.paintCell(Pair(2, 0), revealed = true)

        EventBus.subscribe<FogOfWarSetupEvent> { replayedEvents += it }
        EventBus.subscribe<FogOfWarCellEvent> { replayedEvents += it }

        service.replayState()

        assertEquals(3, replayedEvents.size)
        assertEquals(FogOfWarSetupEvent(cols = 4, rows = 4, colOffset = -2, rowOffset = -2), replayedEvents[0])
        assertEquals(FogOfWarCellEvent(col = 1, row = 1, revealed = true), replayedEvents[1])
        assertEquals(FogOfWarCellEvent(col = 2, row = 0, revealed = true), replayedEvents[2])
    }
}
