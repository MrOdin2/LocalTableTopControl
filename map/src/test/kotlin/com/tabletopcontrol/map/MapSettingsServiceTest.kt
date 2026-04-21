package com.tabletopcontrol.map

import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.map.logic.MapSettingsService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapSettingsServiceTest {

    @AfterEach
    fun tearDown() {
        EventBus.clear()
    }

    @Test
    fun `clearMapImage clears the current map and publishes a clear event`() {
        val service = MapSettingsService(savedSettings = emptySavedSettings())
        val clearedEvents = mutableListOf<MapClearEvent>()

        service.applyMapLoad(
            uri = "file:///maps/castle.png",
            displayPath = "C:\\maps\\castle.png",
        )
        EventBus.subscribe<MapClearEvent> { clearedEvents += it }

        service.clearMapImage()

        assertNull(service.currentMapImageUri)
        assertNull(service.currentMapDisplayPath)
        assertEquals(listOf(MapClearEvent), clearedEvents)
    }

    @Test
    fun `publishCurrentSettings does not replay a map image after it has been cleared`() {
        val service = MapSettingsService(savedSettings = emptySavedSettings())
        val replayedEvents = mutableListOf<Any>()

        service.applyMapLoad(
            uri = "file:///maps/castle.png",
            displayPath = "C:\\maps\\castle.png",
        )
        service.clearMapImage()

        EventBus.subscribe<MapLoadEvent> { replayedEvents += it }
        EventBus.subscribe<MapClearEvent> { replayedEvents += it }
        EventBus.subscribe<MapBackgroundEvent> { replayedEvents += it }

        service.publishCurrentSettings()

        assertTrue(replayedEvents.none { it is MapLoadEvent || it is MapClearEvent })
        assertEquals(listOf(MapBackgroundEvent(service.backgroundColor)), replayedEvents.filterIsInstance<MapBackgroundEvent>())
    }

    private fun emptySavedSettings() = MapSavedSettings(
        gridCalibration = null,
        mapCalibration = null,
        gridColor = null,
        backgroundColor = null,
        mapRotation = null,
        tableMapOffset = null,
    )
}
