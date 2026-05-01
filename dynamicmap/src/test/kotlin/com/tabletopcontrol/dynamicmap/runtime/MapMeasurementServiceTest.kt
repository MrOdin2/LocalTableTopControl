package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.dynamicmap.runtime.logic.MapMeasurementService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapMeasurementServiceTest {

    @AfterEach
    fun tearDown() {
        EventBus.clear()
    }

    @Test
    fun `create and update measurement publishes events with current defaults`() {
        val service = MapMeasurementService()
        val publishedEvents = mutableListOf<Any>()
        EventBus.subscribe<MeasurementAddedEvent> { publishedEvents += it }
        EventBus.subscribe<MeasurementUpdatedEvent> { publishedEvents += it }

        service.setMeasurementUnits("m")
        service.setConeAngleDegrees(90.0)
        service.setDefaultMirrorToTable(true)

        val created = service.createMeasurement(MeasurementType.CONE, Pair(2, 3))
        assertTrue(created is MapResult.Success)
        val overlay = (created as MapResult.Success).value

        service.updateMeasurement(overlay.id, Pair(5, 7))

        assertEquals(2, publishedEvents.size)
        val added = publishedEvents[0] as MeasurementAddedEvent
        val updated = publishedEvents[1] as MeasurementUpdatedEvent
        assertEquals("m", added.overlay.unitsSuffix)
        assertEquals(true, added.overlay.mirroredToTable)
        assertEquals(90.0, added.overlay.coneAngleDegrees)
        assertEquals(5, updated.overlay.endCol)
        assertEquals(7, updated.overlay.endRow)
    }

    @Test
    fun `unsupported measurement units return typed error`() {
        val service = MapMeasurementService()

        val result = service.setMeasurementUnits("yards")

        assertTrue(result is MapResult.Failure)
        assertEquals(
            MapOperationError.UnsupportedMeasurementUnits("yards"),
            (result as MapResult.Failure).error,
        )
        assertEquals(
            "Measurement units must be one of: ft, m.",
            result.error.toUserMessage(),
        )
    }
}
