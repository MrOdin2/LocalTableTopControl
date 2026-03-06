package com.tabletopcontrol.light

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
}
