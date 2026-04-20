package com.tabletopcontrol.map

import com.tabletopcontrol.map.logic.GridCalibration
import com.tabletopcontrol.map.logic.MapCalibration
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        assertTrue(text.contains("grid.color="), "Missing grid.color")
        assertTrue(text.contains("background.color="), "Missing background.color")
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
    fun `deserializeAll round-trips all four settings`() {
        val gridCal = GridCalibration(cellSizeInPixels = 64.0, scale = 1.5, offsetX = 3.0, offsetY = -3.0)
        val mapCal = MapCalibration(scale = 2.0, offsetX = 10.0, offsetY = -5.0)
        val gridColor = Color.color(0.25, 0.5, 0.75, 1.0)
        val bgColor = Color.color(0.0, 0.0, 0.5, 1.0)
        val text = MapSettingsSerializer.serialize(gridCal, mapCal, gridColor, bgColor)

        val settings = MapSettingsSerializer.deserializeAll(text)

        assertEquals(gridCal, settings.gridCalibration)
        assertEquals(mapCal, settings.mapCalibration)
        assertEquals(gridColor.red, settings.gridColor!!.red, 1e-9)
        assertEquals(gridColor.green, settings.gridColor.green, 1e-9)
        assertEquals(gridColor.blue, settings.gridColor.blue, 1e-9)
        assertEquals(gridColor.opacity, settings.gridColor.opacity, 1e-9)
        assertEquals(bgColor.red, settings.backgroundColor!!.red, 1e-9)
        assertEquals(bgColor.green, settings.backgroundColor.green, 1e-9)
        assertEquals(bgColor.blue, settings.backgroundColor.blue, 1e-9)
        assertEquals(bgColor.opacity, settings.backgroundColor.opacity, 1e-9)
    }

    @Test
    fun `deserializeAll returns nulls for missing color keys (backward compatibility)`() {
        // Old-format config without colour keys.
        val oldText = "grid.cellSizeInPixels=50.0\ngrid.scale=1.0\ngrid.offsetX=0.0\ngrid.offsetY=0.0\n" +
            "map.scale=1.0\nmap.offsetX=0.0\nmap.offsetY=0.0"
        val settings = MapSettingsSerializer.deserializeAll(oldText)
        assertEquals(GridCalibration(), settings.gridCalibration)
        assertEquals(MapCalibration(), settings.mapCalibration)
        assertNull(settings.gridColor)
        assertNull(settings.backgroundColor)
    }

    // ── Map rotation ─────────────────────────────────────────────────────────

    @Test
    fun `serialize includes map rotation key`() {
        val text = MapSettingsSerializer.serialize(GridCalibration(), MapCalibration(), mapRotation = 90)
        assertTrue(text.contains("map.rotation="), "Missing map.rotation")
    }

    @Test
    fun `deserializeMapRotation round-trips all valid rotations`() {
        for (degrees in listOf(0, 90, 180, 270)) {
            val text = MapSettingsSerializer.serialize(GridCalibration(), MapCalibration(), mapRotation = degrees)
            assertEquals(degrees, MapSettingsSerializer.deserializeMapRotation(text))
        }
    }

    @Test
    fun `deserializeMapRotation returns null when key absent`() {
        val text = "map.scale=1.0\nmap.offsetX=0.0\nmap.offsetY=0.0"
        assertNull(MapSettingsSerializer.deserializeMapRotation(text))
    }

    @Test
    fun `deserializeMapRotation returns null for non-multiple of 90`() {
        val text = "map.rotation=45"
        assertNull(MapSettingsSerializer.deserializeMapRotation(text))
    }

    @Test
    fun `deserializeMapRotation returns null for non-numeric value`() {
        val text = "map.rotation=abc"
        assertNull(MapSettingsSerializer.deserializeMapRotation(text))
    }

    @Test
    fun `deserializeMapRotation normalises negative rotation`() {
        // -90 degrees should normalise to 270 degrees.
        val text = "map.rotation=-90"
        assertEquals(270, MapSettingsSerializer.deserializeMapRotation(text))
    }

    @Test
    fun `deserializeMapRotation normalises rotation greater than 360`() {
        // 450 degrees (= 90 + 360) should normalise to 90 degrees.
        val text = "map.rotation=450"
        assertEquals(90, MapSettingsSerializer.deserializeMapRotation(text))
    }

    @Test
    fun `deserializeAll round-trips all five settings including rotation`() {
        val gridCal = GridCalibration(cellSizeInPixels = 64.0, scale = 1.5, offsetX = 3.0, offsetY = -3.0)
        val mapCal = MapCalibration(scale = 2.0, offsetX = 10.0, offsetY = -5.0)
        val gridColor = Color.color(0.25, 0.5, 0.75, 1.0)
        val bgColor = Color.color(0.0, 0.0, 0.5, 1.0)
        val text = MapSettingsSerializer.serialize(gridCal, mapCal, gridColor, bgColor, mapRotation = 180)

        val settings = MapSettingsSerializer.deserializeAll(text)

        assertEquals(gridCal, settings.gridCalibration)
        assertEquals(mapCal, settings.mapCalibration)
        assertEquals(180, settings.mapRotation)
    }

    @Test
    fun `deserializeAll returns null mapRotation for old config without rotation key (backward compatibility)`() {
        // Old-format config without map.rotation key.
        val oldText = "grid.cellSizeInPixels=50.0\ngrid.scale=1.0\ngrid.offsetX=0.0\ngrid.offsetY=0.0\n" +
            "map.scale=1.0\nmap.offsetX=0.0\nmap.offsetY=0.0"
        val settings = MapSettingsSerializer.deserializeAll(oldText)
        assertNull(settings.mapRotation)
    }
}
