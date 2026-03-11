package com.tabletopcontrol.light

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LightControllerTest {

    private lateinit var controller: LightController

    @BeforeEach
    fun setUp() {
        controller = LightController()
    }

    // ── defaults ─────────────────────────────────────────────────────────────

    @Test
    fun `default power is on`() {
        assertTrue(controller.power)
    }

    @Test
    fun `default color is white`() {
        assertEquals("#FFFFFF", controller.color)
    }

    @Test
    fun `default effect is NONE`() {
        assertEquals(LightEffect.NONE, controller.effect)
    }

    @Test
    fun `default color cycling is disabled`() {
        assertFalse(controller.colorCycling)
    }

    @Test
    fun `default brightness is 1_0`() {
        assertEquals(1.0, controller.brightness)
    }

    // ── setPower ──────────────────────────────────────────────────────────────

    @Test
    fun `setPower turns lights off`() {
        controller.setPower(false)
        assertFalse(controller.power)
    }

    @Test
    fun `setPower turns lights back on`() {
        controller.setPower(false)
        controller.setPower(true)
        assertTrue(controller.power)
    }

    // ── setColor ─────────────────────────────────────────────────────────────

    @Test
    fun `setColor stores a valid six-digit hex color in uppercase`() {
        controller.setColor("#ff5500")
        assertEquals("#FF5500", controller.color)
    }

    @Test
    fun `setColor accepts a three-digit hex shorthand`() {
        controller.setColor("#F0F")
        assertEquals("#F0F", controller.color)
    }

    @Test
    fun `setColor throws on an invalid hex string`() {
        assertThrows<IllegalArgumentException> { controller.setColor("red") }
        assertThrows<IllegalArgumentException> { controller.setColor("#ZZZZZZ") }
        assertThrows<IllegalArgumentException> { controller.setColor("#12345") }
    }

    // ── setEffect ─────────────────────────────────────────────────────────────

    @Test
    fun `setEffect updates the active effect`() {
        controller.setEffect(LightEffect.RAINBOW)
        assertEquals(LightEffect.RAINBOW, controller.effect)
    }

    @Test
    fun `setEffect can be changed back to NONE`() {
        controller.setEffect(LightEffect.FIRE)
        controller.setEffect(LightEffect.NONE)
        assertEquals(LightEffect.NONE, controller.effect)
    }

    // ── setColorCycling ───────────────────────────────────────────────────────

    @Test
    fun `setColorCycling enables cycling`() {
        controller.setColorCycling(true)
        assertTrue(controller.colorCycling)
    }

    @Test
    fun `setColorCycling disables cycling`() {
        controller.setColorCycling(true)
        controller.setColorCycling(false)
        assertFalse(controller.colorCycling)
    }

    // ── setBrightness ─────────────────────────────────────────────────────────

    @Test
    fun `setBrightness updates the brightness`() {
        controller.setBrightness(0.5)
        assertEquals(0.5, controller.brightness)
    }

    @Test
    fun `setBrightness accepts boundary values 0_0 and 1_0`() {
        controller.setBrightness(0.0)
        assertEquals(0.0, controller.brightness)

        controller.setBrightness(1.0)
        assertEquals(1.0, controller.brightness)
    }

    @Test
    fun `setBrightness throws on value below 0`() {
        assertThrows<IllegalArgumentException> { controller.setBrightness(-0.1) }
    }

    @Test
    fun `setBrightness throws on value above 1`() {
        assertThrows<IllegalArgumentException> { controller.setBrightness(1.1) }
    }

    // ── setPreset ─────────────────────────────────────────────────────────────

    @Test
    fun `default preset is null`() {
        assertNull(controller.preset)
    }

    @Test
    fun `setPreset stores a valid preset id`() {
        controller.setPreset(5)
        assertEquals(5, controller.preset)
    }

    @Test
    fun `setPreset accepts boundary values 1 and 250`() {
        controller.setPreset(1)
        assertEquals(1, controller.preset)

        controller.setPreset(250)
        assertEquals(250, controller.preset)
    }

    @Test
    fun `setPreset null clears the active preset`() {
        controller.setPreset(10)
        controller.setPreset(null)
        assertNull(controller.preset)
    }

    @Test
    fun `setPreset throws on id below 1`() {
        assertThrows<IllegalArgumentException> { controller.setPreset(0) }
        assertThrows<IllegalArgumentException> { controller.setPreset(-1) }
    }

    @Test
    fun `setPreset throws on id above 250`() {
        assertThrows<IllegalArgumentException> { controller.setPreset(251) }
    }

    @Test
    fun `change listener is called when preset changes`() {
        var callCount = 0
        controller.addChangeListener { callCount++ }
        controller.setPreset(3)
        assertEquals(1, callCount)
    }

    @Test
    fun `change listener is called when preset is cleared`() {
        controller.setPreset(3)
        var callCount = 0
        controller.addChangeListener { callCount++ }
        controller.setPreset(null)
        assertEquals(1, callCount)
    }

    @Test
    fun `setting preset does not affect other fields`() {
        controller.setColor("#AABBCC")
        controller.setEffect(LightEffect.FIRE)
        controller.setBrightness(0.5)
        controller.setPower(false)

        controller.setPreset(42)

        assertEquals("#AABBCC", controller.color)
        assertEquals(LightEffect.FIRE, controller.effect)
        assertEquals(0.5, controller.brightness)
        assertFalse(controller.power)
    }

    // ── independent state fields ──────────────────────────────────────────────

    @Test
    fun `setting color does not affect other fields`() {
        controller.setEffect(LightEffect.STROBE)
        controller.setColorCycling(true)
        controller.setBrightness(0.7)

        controller.setColor("#123456")

        assertEquals(LightEffect.STROBE, controller.effect)
        assertTrue(controller.colorCycling)
        assertEquals(0.7, controller.brightness)
    }

    // ── change listeners ──────────────────────────────────────────────────────

    @Test
    fun `change listener is called when power changes`() {
        var callCount = 0
        controller.addChangeListener { callCount++ }
        controller.setPower(false)
        assertEquals(1, callCount)
    }

    @Test
    fun `change listener is called when color changes`() {
        var callCount = 0
        controller.addChangeListener { callCount++ }
        controller.setColor("#123456")
        assertEquals(1, callCount)
    }

    @Test
    fun `change listener is called when effect changes`() {
        var callCount = 0
        controller.addChangeListener { callCount++ }
        controller.setEffect(LightEffect.FIRE)
        assertEquals(1, callCount)
    }

    @Test
    fun `change listener is called when color cycling changes`() {
        var callCount = 0
        controller.addChangeListener { callCount++ }
        controller.setColorCycling(true)
        assertEquals(1, callCount)
    }

    @Test
    fun `change listener is called when brightness changes`() {
        var callCount = 0
        controller.addChangeListener { callCount++ }
        controller.setBrightness(0.3)
        assertEquals(1, callCount)
    }

    @Test
    fun `multiple change listeners are all notified`() {
        var a = 0
        var b = 0
        controller.addChangeListener { a++ }
        controller.addChangeListener { b++ }
        controller.setColor("#AABBCC")
        assertEquals(1, a)
        assertEquals(1, b)
    }

    @Test
    fun `removed change listener is not called after removal`() {
        var callCount = 0
        val listener = controller.addChangeListener { callCount++ }
        controller.removeChangeListener(listener)
        controller.setColor("#AABBCC")
        assertEquals(0, callCount)
    }
}
