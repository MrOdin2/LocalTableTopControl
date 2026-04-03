package com.tabletopcontrol.light

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [WledSerialSender].
 *
 * These tests exercise [WledSerialSender.buildJson] (the JSON-serialisation
 * logic) without requiring a real serial port or WLED hardware.
 */
class WledSerialSenderTest {

    private lateinit var sender: WledSerialSender

    @BeforeEach
    fun setUp() {
        sender = WledSerialSender()
    }

    // ── isConnected ───────────────────────────────────────────────────────────

    @Test
    fun `isConnected is false when no port has been opened`() {
        assertFalse(sender.isConnected)
    }

    @Test
    fun `sender default speed and intensity stay in sync with controller defaults`() {
        assertEquals(LightController.DEFAULT_EFFECT_SPEED, WledSerialSender.DEFAULT_EFFECT_SPEED)
        assertEquals(LightController.DEFAULT_EFFECT_INTENSITY, WledSerialSender.DEFAULT_EFFECT_INTENSITY)
    }

    // ── buildJson — basic structure ───────────────────────────────────────────

    @Test
    fun `buildJson includes on field`() {
        val json = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false,
        )
        assertTrue(json.contains("\"on\":true"), "Expected on:true in $json")
    }

    @Test
    fun `buildJson uses on=false when power is off`() {
        val json = sender.buildJson(
            on = false, color = "#FFFFFF", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false,
        )
        assertTrue(json.contains("\"on\":false"), "Expected on:false in $json")
    }

    @Test
    fun `buildJson maps full brightness to bri 255`() {
        val json = sender.buildJson(
            on = true, color = "#000000", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false,
        )
        assertTrue(json.contains("\"bri\":255"), "Expected bri:255 in $json")
    }

    @Test
    fun `buildJson maps zero brightness to bri 0`() {
        val json = sender.buildJson(
            on = true, color = "#000000", effect = LightEffect.NONE,
            brightness = 0.0, colorCycling = false,
        )
        assertTrue(json.contains("\"bri\":0"), "Expected bri:0 in $json")
    }

    @Test
    fun `buildJson maps half brightness to bri 127`() {
        val json = sender.buildJson(
            on = true, color = "#000000", effect = LightEffect.NONE,
            brightness = 0.5, colorCycling = false,
        )
        assertTrue(json.contains("\"bri\":127"), "Expected bri:127 in $json")
    }

    // ── buildJson — color encoding ────────────────────────────────────────────

    @Test
    fun `buildJson encodes white as 255,255,255`() {
        val json = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false,
        )
        assertTrue(json.contains("[255,255,255]"), "Expected [255,255,255] in $json")
    }

    @Test
    fun `buildJson encodes black as 0,0,0`() {
        val json = sender.buildJson(
            on = true, color = "#000000", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false,
        )
        assertTrue(json.contains("[0,0,0]"), "Expected [0,0,0] in $json")
    }

    @Test
    fun `buildJson encodes a three-digit hex shorthand correctly`() {
        // #F00 expands to #FF0000 → (255, 0, 0)
        val json = sender.buildJson(
            on = true, color = "#F00", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false,
        )
        assertTrue(json.contains("[255,0,0]"), "Expected [255,0,0] in $json")
    }

    @Test
    fun `buildJson encodes arbitrary rgb color`() {
        // #1A2B3C → r=26, g=43, b=60
        val json = sender.buildJson(
            on = true, color = "#1A2B3C", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false,
        )
        assertTrue(json.contains("[26,43,60]"), "Expected [26,43,60] in $json")
    }

    // ── buildJson — effect mapping ────────────────────────────────────────────

    @Test
    fun `buildJson uses WLED effect id 0 for NONE`() {
        val json = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false,
        )
        assertTrue(json.contains("\"fx\":0"), "Expected fx:0 in $json")
    }

    @Test
    fun `buildJson uses WLED effect id for FADE`() {
        val json = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.FADE,
            brightness = 1.0, colorCycling = false,
        )
        assertTrue(
            json.contains("\"fx\":${LightEffect.FADE.wledEffectId}"),
            "Expected fx:${LightEffect.FADE.wledEffectId} in $json",
        )
    }

    @Test
    fun `buildJson uses WLED effect id for STROBE`() {
        val json = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.STROBE,
            brightness = 1.0, colorCycling = false,
        )
        assertTrue(
            json.contains("\"fx\":${LightEffect.STROBE.wledEffectId}"),
            "Expected fx:${LightEffect.STROBE.wledEffectId} in $json",
        )
    }

    @Test
    fun `buildJson uses Rainbow effect id when colorCycling is enabled`() {
        val json = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = true,
        )
        val expectedFx = LightEffect.RAINBOW.wledEffectId
        assertTrue(
            json.contains("\"fx\":$expectedFx"),
            "Expected fx:$expectedFx (Rainbow) when colorCycling=true, got: $json",
        )
    }

    @Test
    fun `buildJson colorCycling overrides explicit effect selection`() {
        val json = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.FIRE,
            brightness = 1.0, colorCycling = true,
        )
        val rainbowFx = LightEffect.RAINBOW.wledEffectId
        val fireFx = LightEffect.FIRE.wledEffectId
        assertTrue(
            json.contains("\"fx\":$rainbowFx"),
            "Expected Rainbow fx:$rainbowFx when colorCycling=true, got: $json",
        )
        assertFalse(
            json.contains("\"fx\":$fireFx"),
            "Did not expect Fire fx:$fireFx when colorCycling=true, got: $json",
        )
    }

    // ── buildJson — speed and intensity ──────────────────────────────────────

    @Test
    fun `buildJson includes sx field for speed`() {
        val json = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false, speed = 200,
        )
        assertTrue(json.contains("\"sx\":200"), "Expected sx:200 in $json")
    }

    @Test
    fun `buildJson includes ix field for intensity`() {
        val json = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false, intensity = 50,
        )
        assertTrue(json.contains("\"ix\":50"), "Expected ix:50 in $json")
    }

    @Test
    fun `buildJson uses default speed of 128 when not specified`() {
        val json = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false,
        )
        assertTrue(json.contains("\"sx\":128"), "Expected default sx:128 in $json")
    }

    @Test
    fun `buildJson uses default intensity of 128 when not specified`() {
        val json = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false,
        )
        assertTrue(json.contains("\"ix\":128"), "Expected default ix:128 in $json")
    }

    @Test
    fun `buildJson accepts boundary speed values 0 and 255`() {
        val jsonMin = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false, speed = 0,
        )
        assertTrue(jsonMin.contains("\"sx\":0"), "Expected sx:0 in $jsonMin")

        val jsonMax = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false, speed = 255,
        )
        assertTrue(jsonMax.contains("\"sx\":255"), "Expected sx:255 in $jsonMax")
    }

    @Test
    fun `buildJson accepts boundary intensity values 0 and 255`() {
        val jsonMin = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false, intensity = 0,
        )
        assertTrue(jsonMin.contains("\"ix\":0"), "Expected ix:0 in $jsonMin")

        val jsonMax = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false, intensity = 255,
        )
        assertTrue(jsonMax.contains("\"ix\":255"), "Expected ix:255 in $jsonMax")
    }

    @Test
    fun `buildJson snapshot with custom speed and intensity`() {
        val json = sender.buildJson(
            on = true, color = "#FF0000", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false, speed = 200, intensity = 50,
        )
        assertEquals(
            """{"on":true,"bri":255,"seg":[{"col":[[255,0,0]],"fx":0,"sx":200,"ix":50}]}""",
            json,
        )
    }

    // ── buildJson — output format ─────────────────────────────────────────────

    @Test
    fun `buildJson output contains a segment array`() {
        val json = sender.buildJson(
            on = true, color = "#FFFFFF", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false,
        )
        assertTrue(json.contains("\"seg\":["), "Expected seg array in $json")
    }

    @Test
    fun `buildJson produces valid-looking JSON object`() {
        val json = sender.buildJson(
            on = true, color = "#FF5500", effect = LightEffect.RAINBOW,
            brightness = 0.75, colorCycling = false,
        )
        assertTrue(json.startsWith("{"), "JSON should start with {")
        assertTrue(json.endsWith("}"), "JSON should end with }")
    }

    @Test
    fun `buildJson snapshot for known inputs`() {
        val json = sender.buildJson(
            on = true, color = "#FF0000", effect = LightEffect.NONE,
            brightness = 1.0, colorCycling = false,
        )
        assertEquals("""{"on":true,"bri":255,"seg":[{"col":[[255,0,0]],"fx":0,"sx":128,"ix":128}]}""", json)
    }

    // ── buildJson — validation ────────────────────────────────────────────────

    @Test
    fun `buildJson throws on brightness below 0`() {
        assertThrows<IllegalArgumentException> {
            sender.buildJson(
                on = true, color = "#FFFFFF", effect = LightEffect.NONE,
                brightness = -0.1, colorCycling = false,
            )
        }
    }

    @Test
    fun `buildJson throws on brightness above 1`() {
        assertThrows<IllegalArgumentException> {
            sender.buildJson(
                on = true, color = "#FFFFFF", effect = LightEffect.NONE,
                brightness = 1.1, colorCycling = false,
            )
        }
    }

    @Test
    fun `buildJson throws on speed below 0`() {
        assertThrows<IllegalArgumentException> {
            sender.buildJson(
                on = true, color = "#FFFFFF", effect = LightEffect.NONE,
                brightness = 1.0, colorCycling = false, speed = -1,
            )
        }
    }

    @Test
    fun `buildJson throws on speed above 255`() {
        assertThrows<IllegalArgumentException> {
            sender.buildJson(
                on = true, color = "#FFFFFF", effect = LightEffect.NONE,
                brightness = 1.0, colorCycling = false, speed = 256,
            )
        }
    }

    @Test
    fun `buildJson throws on intensity below 0`() {
        assertThrows<IllegalArgumentException> {
            sender.buildJson(
                on = true, color = "#FFFFFF", effect = LightEffect.NONE,
                brightness = 1.0, colorCycling = false, intensity = -1,
            )
        }
    }

    @Test
    fun `buildJson throws on intensity above 255`() {
        assertThrows<IllegalArgumentException> {
            sender.buildJson(
                on = true, color = "#FFFFFF", effect = LightEffect.NONE,
                brightness = 1.0, colorCycling = false, intensity = 256,
            )
        }
    }

    @Test
    fun `buildJson throws on invalid hex color`() {
        assertThrows<IllegalArgumentException> {
            sender.buildJson(
                on = true, color = "red", effect = LightEffect.NONE,
                brightness = 1.0, colorCycling = false,
            )
        }
    }

    @Test
    fun `buildJson throws on double-hash hex color`() {
        // "##FFF" used to be silently accepted via trimStart; removePrefix rejects it.
        assertThrows<IllegalArgumentException> {
            sender.buildJson(
                on = true, color = "##FFF", effect = LightEffect.NONE,
                brightness = 1.0, colorCycling = false,
            )
        }
    }

    // ── disconnect when not connected ─────────────────────────────────────────

    @Test
    fun `disconnect is safe to call when not connected`() {
        // Should not throw
        sender.disconnect()
        assertFalse(sender.isConnected)
    }

    @Test
    fun `close is safe to call when not connected`() {
        sender.close()
        assertFalse(sender.isConnected)
    }

    // ── buildPresetJson ───────────────────────────────────────────────────────

    @Test
    fun `buildPresetJson produces correct JSON for a valid preset id`() {
        assertEquals("""{"ps":5}""", sender.buildPresetJson(5))
    }

    @Test
    fun `buildPresetJson uses the exact id in the output`() {
        val json = sender.buildPresetJson(42)
        assertTrue(json.contains("\"ps\":42"), "Expected ps:42 in $json")
    }

    @Test
    fun `buildPresetJson accepts boundary value 1`() {
        val json = sender.buildPresetJson(1)
        assertTrue(json.contains("\"ps\":1"), "Expected ps:1 in $json")
    }

    @Test
    fun `buildPresetJson accepts boundary value 250`() {
        val json = sender.buildPresetJson(250)
        assertTrue(json.contains("\"ps\":250"), "Expected ps:250 in $json")
    }

    @Test
    fun `buildPresetJson throws on id below 1`() {
        assertThrows<IllegalArgumentException> { sender.buildPresetJson(0) }
        assertThrows<IllegalArgumentException> { sender.buildPresetJson(-1) }
    }

    @Test
    fun `buildPresetJson throws on id above 250`() {
        assertThrows<IllegalArgumentException> { sender.buildPresetJson(251) }
    }

    @Test
    fun `buildPresetJson output is a JSON object`() {
        val json = sender.buildPresetJson(10)
        assertTrue(json.startsWith("{"), "JSON should start with {")
        assertTrue(json.endsWith("}"), "JSON should end with }")
    }
}
