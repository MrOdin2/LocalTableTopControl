package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.dynamicmap.runtime.logic.MapSettingsService
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
    fun `publishCurrentSettings replays the empty arena after a map has been cleared`() {
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

        assertEquals(listOf(MapClearEvent), replayedEvents.filterIsInstance<MapClearEvent>())
        assertTrue(replayedEvents.none { it is MapLoadEvent })
        assertEquals(listOf(MapBackgroundEvent(service.backgroundColor)), replayedEvents.filterIsInstance<MapBackgroundEvent>())
    }

    @Test
    fun `publishCurrentSettings replays dynamic map render mode`() {
        val service = MapSettingsService(
            savedSettings = emptySavedSettings().copy(dynamicMapRenderMode = DynamicMapRenderMode.DEBUG),
        )
        val events = mutableListOf<DynamicMapRenderModeEvent>()
        EventBus.subscribe<DynamicMapRenderModeEvent> { events += it }

        service.publishCurrentSettings()

        assertEquals(listOf(DynamicMapRenderModeEvent(DynamicMapRenderMode.DEBUG)), events)
    }

    @Test
    fun `force PC token visibility publishes setting event`() {
        val service = MapSettingsService(savedSettings = emptySavedSettings())
        val events = mutableListOf<ForcePcTokensVisibleEvent>()
        EventBus.subscribe<ForcePcTokensVisibleEvent> { events += it }

        service.setForcePcTokensVisible(true)
        service.publishCurrentSettings()

        assertTrue(service.forcePcTokensVisible)
        assertEquals(
            listOf(
                ForcePcTokensVisibleEvent(true),
                ForcePcTokensVisibleEvent(true),
            ),
            events,
        )
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
