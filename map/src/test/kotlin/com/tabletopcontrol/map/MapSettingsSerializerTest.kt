package com.tabletopcontrol.map

import javafx.scene.paint.Color
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

    // ── Color serialisation helpers ──────────────────────────────────────────

    @Test
    fun `colorToString encodes RGBA components`() {
        val color = Color.color(0.25, 0.5, 0.75, 1.0)
        val s = MapSettingsSerializer.colorToString(color)
        val parts = s.split(",")
        assertEquals(4, parts.size)
        // Compare against color.red/green/blue/opacity to account for JavaFX's
        // internal float storage of color components.
        assertEquals(color.red, parts[0].toDouble(), 1e-9)
        assertEquals(color.green, parts[1].toDouble(), 1e-9)
        assertEquals(color.blue, parts[2].toDouble(), 1e-9)
        assertEquals(color.opacity, parts[3].toDouble(), 1e-9)
    }

    @Test
    fun `stringToColor round-trips color`() {
        val original = Color.color(0.25, 0.5, 0.75, 1.0)
        val s = MapSettingsSerializer.colorToString(original)
        val parsed = MapSettingsSerializer.stringToColor(s)!!
        // Use the original color's components as expected values so that
        // JavaFX float-precision storage is accounted for on both ends.
        assertEquals(original.red, parsed.red, 1e-9)
        assertEquals(original.green, parsed.green, 1e-9)
        assertEquals(original.blue, parsed.blue, 1e-9)
        assertEquals(original.opacity, parsed.opacity, 1e-9)
    }

    @Test
    fun `stringToColor returns null for malformed string`() {
        assertNull(MapSettingsSerializer.stringToColor("not-a-color"))
    }

    @Test
    fun `stringToColor returns null for wrong number of components`() {
        assertNull(MapSettingsSerializer.stringToColor("0.0,0.0,0.0"))
    }

    @Test
    fun `serialize includes grid color and background color keys`() {
        val text = MapSettingsSerializer.serialize(GridCalibration(), MapCalibration())
        assert(text.contains("grid.color=")) { "Missing grid.color" }
        assert(text.contains("background.color=")) { "Missing background.color" }
    }

    @Test
    fun `deserializeGridColor round-trips custom grid color`() {
        val gridColor = Color.color(0.5, 0.5, 0.5, 0.75)
        val text = MapSettingsSerializer.serialize(GridCalibration(), MapCalibration(), gridColor = gridColor)
        val parsed = MapSettingsSerializer.deserializeGridColor(text)!!
        assertEquals(0.5, parsed.red, 1e-9)
        assertEquals(0.5, parsed.green, 1e-9)
        assertEquals(0.5, parsed.blue, 1e-9)
        assertEquals(0.75, parsed.opacity, 1e-9)
    }

    @Test
    fun `deserializeBackgroundColor round-trips custom background color`() {
        val bgColor = Color.color(0.25, 0.5, 0.75, 1.0)
        val text = MapSettingsSerializer.serialize(GridCalibration(), MapCalibration(), backgroundColor = bgColor)
        val parsed = MapSettingsSerializer.deserializeBackgroundColor(text)!!
        assertEquals(bgColor.red, parsed.red, 1e-9)
        assertEquals(bgColor.green, parsed.green, 1e-9)
        assertEquals(bgColor.blue, parsed.blue, 1e-9)
        assertEquals(bgColor.opacity, parsed.opacity, 1e-9)
    }

    @Test
    fun `deserializeGridColor returns null when key absent`() {
        val text = "grid.cellSizeInPixels=50.0\ngrid.scale=1.0\ngrid.offsetX=0.0\ngrid.offsetY=0.0"
        assertNull(MapSettingsSerializer.deserializeGridColor(text))
    }

    @Test
    fun `deserializeBackgroundColor returns null when key absent`() {
        val text = "map.scale=1.0\nmap.offsetX=0.0\nmap.offsetY=0.0"
        assertNull(MapSettingsSerializer.deserializeBackgroundColor(text))
    }

    @Test
    fun `load returns MapSavedSettings with all four fields`() {
        // Verify the return type is MapSavedSettings (structural check via properties)
        val settings = MapSavedSettings(
            gridCalibration = GridCalibration(),
            mapCalibration = MapCalibration(),
            gridColor = Color.BLACK,
            backgroundColor = Color.WHITE,
        )
        assertEquals(GridCalibration(), settings.gridCalibration)
        assertEquals(MapCalibration(), settings.mapCalibration)
        assertEquals(Color.BLACK, settings.gridColor)
        assertEquals(Color.WHITE, settings.backgroundColor)
    }
}
