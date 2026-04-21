package com.tabletopcontrol.light

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LightControllerTest {

    private lateinit var controller: LightController

    @BeforeEach
    fun setUp() {
        controller = LightController()
    }

    @Test
    fun `default state is initialized`() {
        assertTrue(controller.power)
        assertEquals("#FFFFFF", controller.color)
        assertEquals(LightEffect.NONE, controller.effect)
        assertFalse(controller.colorCycling)
        assertEquals(LightController.DEFAULT_EFFECT_SPEED, controller.effectSpeed)
        assertEquals(LightController.DEFAULT_EFFECT_INTENSITY, controller.effectIntensity)
        assertEquals(1.0, controller.brightness)
        assertNull(controller.preset)
    }

    @Test
    fun `setPower returns applied and updates state`() {
        assertEquals(LightOperationResult.Applied, controller.setPower(false))
        assertFalse(controller.power)
    }

    @Test
    fun `setColor uppercases valid hex`() {
        assertEquals(LightOperationResult.Applied, controller.setColor("#ff5500"))
        assertEquals("#FF5500", controller.color)
    }

    @Test
    fun `setColor rejects invalid hex without mutating state`() {
        val result = controller.setColor("red")

        assertTrue(result is LightOperationResult.InvalidColor)
        assertEquals("#FFFFFF", controller.color)
    }

    @Test
    fun `setEffect updates the active effect`() {
        assertEquals(LightOperationResult.Applied, controller.setEffect(LightEffect.FIRE))
        assertEquals(LightEffect.FIRE, controller.effect)
    }

    @Test
    fun `setColorCycling updates the flag`() {
        assertEquals(LightOperationResult.Applied, controller.setColorCycling(true))
        assertTrue(controller.colorCycling)
    }

    @Test
    fun `setEffectSpeed accepts boundary values`() {
        assertEquals(LightOperationResult.Applied, controller.setEffectSpeed(0))
        assertEquals(0, controller.effectSpeed)

        assertEquals(LightOperationResult.Applied, controller.setEffectSpeed(255))
        assertEquals(255, controller.effectSpeed)
    }

    @Test
    fun `setEffectSpeed rejects out of range values`() {
        val result = controller.setEffectSpeed(256)

        assertTrue(result is LightOperationResult.InvalidEffectSpeed)
        assertEquals(LightController.DEFAULT_EFFECT_SPEED, controller.effectSpeed)
    }

    @Test
    fun `setEffectIntensity accepts boundary values`() {
        assertEquals(LightOperationResult.Applied, controller.setEffectIntensity(0))
        assertEquals(0, controller.effectIntensity)

        assertEquals(LightOperationResult.Applied, controller.setEffectIntensity(255))
        assertEquals(255, controller.effectIntensity)
    }

    @Test
    fun `setEffectIntensity rejects out of range values`() {
        val result = controller.setEffectIntensity(-1)

        assertTrue(result is LightOperationResult.InvalidEffectIntensity)
        assertEquals(LightController.DEFAULT_EFFECT_INTENSITY, controller.effectIntensity)
    }

    @Test
    fun `setBrightness accepts boundary values`() {
        assertEquals(LightOperationResult.Applied, controller.setBrightness(0.0))
        assertEquals(0.0, controller.brightness)

        assertEquals(LightOperationResult.Applied, controller.setBrightness(1.0))
        assertEquals(1.0, controller.brightness)
    }

    @Test
    fun `setBrightness rejects out of range values`() {
        val result = controller.setBrightness(1.1)

        assertTrue(result is LightOperationResult.InvalidBrightness)
        assertEquals(1.0, controller.brightness)
    }

    @Test
    fun `setPreset accepts valid ids and null clear`() {
        assertEquals(LightOperationResult.Applied, controller.setPreset(42))
        assertEquals(42, controller.preset)

        assertEquals(LightOperationResult.Applied, controller.setPreset(null))
        assertNull(controller.preset)
    }

    @Test
    fun `setPreset rejects invalid ids without mutating state`() {
        val result = controller.setPreset(251)

        assertTrue(result is LightOperationResult.InvalidPreset)
        assertNull(controller.preset)
    }

    @Test
    fun `applyPresetInput parses numeric text`() {
        assertEquals(LightOperationResult.Applied, controller.applyPresetInput(" 15 "))
        assertEquals(15, controller.preset)
    }

    @Test
    fun `applyPresetInput rejects non numeric text`() {
        val result = controller.applyPresetInput("torch")

        assertTrue(result is LightOperationResult.InvalidPresetText)
        assertNull(controller.preset)
    }

    @Test
    fun `change listener is called when a valid update succeeds`() {
        var callCount = 0
        controller.addChangeListener { callCount++ }

        controller.setBrightness(0.3)

        assertEquals(1, callCount)
    }

    @Test
    fun `change listener is not called when an update is rejected`() {
        var callCount = 0
        controller.addChangeListener { callCount++ }

        controller.setBrightness(1.5)

        assertEquals(0, callCount)
    }

    @Test
    fun `removed change listener is not called`() {
        var callCount = 0
        val listener = controller.addChangeListener { callCount++ }
        controller.removeChangeListener(listener)

        controller.setColor("#AABBCC")

        assertEquals(0, callCount)
    }

    @Test
    fun `listener can remove itself during notification`() {
        var callCount = 0
        lateinit var self: () -> Unit
        self = {
            callCount++
            controller.removeChangeListener(self)
        }
        controller.addChangeListener(self)

        controller.setPower(false)
        controller.setPower(true)

        assertEquals(1, callCount)
    }

    @Test
    fun `snapshot uses preset command when preset is active and power is on`() {
        controller.setPreset(7)

        val command = controller.snapshot().toSerialCommand()

        assertEquals(LightSerialCommand.Preset(7), command)
    }

    @Test
    fun `snapshot uses explicit off state when power is disabled even with preset`() {
        controller.setColor("#AABBCC")
        controller.setPreset(7)
        controller.setPower(false)

        val command = controller.snapshot().toSerialCommand()

        assertTrue(command is LightSerialCommand.State)
        val state = command as LightSerialCommand.State
        assertFalse(state.on)
        assertEquals("#AABBCC", state.color)
    }

    @Test
    fun `snapshot uses manual state command when no preset is active`() {
        controller.setColor("#AABBCC")
        controller.setEffect(LightEffect.LIGHTNING)
        controller.setBrightness(0.5)
        controller.setColorCycling(true)
        controller.setEffectSpeed(200)
        controller.setEffectIntensity(50)

        val command = controller.snapshot().toSerialCommand()

        assertEquals(
            LightSerialCommand.State(
                on = true,
                color = "#AABBCC",
                effect = LightEffect.LIGHTNING,
                brightness = 0.5,
                colorCycling = true,
                speed = 200,
                intensity = 50,
            ),
            command,
        )
    }
}
