package com.tabletopcontrol.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MapSettingsSerializerTest {

    // ── Round-trip tests ─────────────────────────────────────────────────────

    @Test
    fun `serialize and deserialize grid calibration with default values`() {
        val gridCal = GridCalibration()
        val mapCal = MapCalibration()
        val text = MapSettingsSerializer.serialize(gridCal, mapCal)
        assertEquals(gridCal, MapSettingsSerializer.deserializeGridCalibration(text))
    }

    @Test
    fun `serialize and deserialize map calibration with default values`() {
        val gridCal = GridCalibration()
        val mapCal = MapCalibration()
        val text = MapSettingsSerializer.serialize(gridCal, mapCal)
        assertEquals(mapCal, MapSettingsSerializer.deserializeMapCalibration(text))
    }

    @Test
    fun `serialize and deserialize grid calibration with custom values`() {
        val gridCal = GridCalibration(cellSizeInPixels = 75.0, scale = 1.5, offsetX = 10.0, offsetY = -5.0)
        val mapCal = MapCalibration()
        val text = MapSettingsSerializer.serialize(gridCal, mapCal)
        assertEquals(gridCal, MapSettingsSerializer.deserializeGridCalibration(text))
    }

    @Test
    fun `serialize and deserialize map calibration with custom values`() {
        val gridCal = GridCalibration()
        val mapCal = MapCalibration(scale = 2.0, offsetX = 15.0, offsetY = -20.0)
        val text = MapSettingsSerializer.serialize(gridCal, mapCal)
        assertEquals(mapCal, MapSettingsSerializer.deserializeMapCalibration(text))
    }

    @Test
    fun `serialize and deserialize both calibrations round-trip together`() {
        val gridCal = GridCalibration(cellSizeInPixels = 64.0, scale = 0.8, offsetX = 3.5, offsetY = -7.25)
        val mapCal = MapCalibration(scale = 1.2, offsetX = -12.0, offsetY = 4.0)
        val text = MapSettingsSerializer.serialize(gridCal, mapCal)
        assertEquals(gridCal, MapSettingsSerializer.deserializeGridCalibration(text))
        assertEquals(mapCal, MapSettingsSerializer.deserializeMapCalibration(text))
    }

    // ── Serialised output format ─────────────────────────────────────────────

    @Test
    fun `serialized text contains expected keys`() {
        val text = MapSettingsSerializer.serialize(GridCalibration(), MapCalibration())
        assert(text.contains("grid.cellSizeInPixels=")) { "Missing grid.cellSizeInPixels" }
        assert(text.contains("grid.scale=")) { "Missing grid.scale" }
        assert(text.contains("grid.offsetX=")) { "Missing grid.offsetX" }
        assert(text.contains("grid.offsetY=")) { "Missing grid.offsetY" }
        assert(text.contains("map.scale=")) { "Missing map.scale" }
        assert(text.contains("map.offsetX=")) { "Missing map.offsetX" }
        assert(text.contains("map.offsetY=")) { "Missing map.offsetY" }
    }

    // ── Deserialise edge cases ───────────────────────────────────────────────

    @Test
    fun `deserializeGridCalibration returns null for empty string`() {
        assertNull(MapSettingsSerializer.deserializeGridCalibration(""))
    }

    @Test
    fun `deserializeMapCalibration returns null for empty string`() {
        assertNull(MapSettingsSerializer.deserializeMapCalibration(""))
    }

    @Test
    fun `deserializeGridCalibration returns null for missing keys`() {
        assertNull(MapSettingsSerializer.deserializeGridCalibration("map.scale=1.0"))
    }

    @Test
    fun `deserializeMapCalibration returns null for missing keys`() {
        assertNull(MapSettingsSerializer.deserializeMapCalibration("grid.cellSizeInPixels=50.0"))
    }

    @Test
    fun `deserializeGridCalibration returns null for non-numeric values`() {
        val text = "grid.cellSizeInPixels=abc\ngrid.scale=1.0\ngrid.offsetX=0.0\ngrid.offsetY=0.0"
        assertNull(MapSettingsSerializer.deserializeGridCalibration(text))
    }

    @Test
    fun `deserializeGridCalibration returns null for non-positive cellSizeInPixels`() {
        val text = "grid.cellSizeInPixels=-10.0\ngrid.scale=1.0\ngrid.offsetX=0.0\ngrid.offsetY=0.0"
        assertNull(MapSettingsSerializer.deserializeGridCalibration(text))
    }

    @Test
    fun `deserializeGridCalibration returns null for non-positive scale`() {
        val text = "grid.cellSizeInPixels=50.0\ngrid.scale=0.0\ngrid.offsetX=0.0\ngrid.offsetY=0.0"
        assertNull(MapSettingsSerializer.deserializeGridCalibration(text))
    }

    @Test
    fun `deserializeMapCalibration returns null for non-positive scale`() {
        val text = "map.scale=-1.0\nmap.offsetX=0.0\nmap.offsetY=0.0"
        assertNull(MapSettingsSerializer.deserializeMapCalibration(text))
    }
}
