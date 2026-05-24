package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.light.LightEffect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdvancedLightControllerTest {
    @Test
    fun `loadFromDevice applies persisted order and segment preferences`() {
        val controller = AdvancedLightController()
        val snapshot = WledDeviceSnapshot(
            segments = listOf(
                WledSegmentSnapshot(
                    id = 0,
                    on = true,
                    selectedForEdit = true,
                    color = "#FFFFFF",
                    brightness = 1.0,
                    effect = LightEffect.NONE,
                    effectSpeed = 128,
                    effectIntensity = 128,
                ),
                WledSegmentSnapshot(
                    id = 2,
                    on = false,
                    selectedForEdit = true,
                    color = "#000000",
                    brightness = 0.2,
                    effect = LightEffect.FIRE,
                    effectSpeed = 30,
                    effectIntensity = 40,
                ),
            ),
        )
        val preferences = AdvancedLightPreferences(
            order = listOf(2, 0),
            segments = mapOf(
                2 to AdvancedLightSegmentPreference(
                    name = "Door",
                    color = "#123456",
                    effect = LightEffect.CANDLE,
                    brightness = 0.7,
                    brightnessScale = 0.5,
                    effectSpeed = 90,
                    effectIntensity = 91,
                ),
            ),
        )

        controller.loadFromDevice(snapshot, preferences)

        assertEquals(listOf(2, 0), controller.currentSegments().map { it.id })
        assertEquals("Door", controller.currentSegments().first().name)
        assertEquals("#123456", controller.currentSegments().first().color)
        assertEquals(LightEffect.CANDLE, controller.currentSegments().first().effect)
        assertEquals(0.7, controller.currentSegments().first().brightness)
        assertEquals(0.5, controller.currentSegments().first().brightnessScale)
    }

    @Test
    fun `segment brightness scale changes only WLED command brightness`() {
        val controller = AdvancedLightController()
        controller.loadFromDevice(
            WledDeviceSnapshot(
                segments = listOf(
                    WledSegmentSnapshot(0, true, true, "#FFFFFF", 0.8, LightEffect.NONE, 128, 128),
                ),
            ),
            AdvancedLightPreferences(),
        )

        val scaleCommands = controller.setSegmentBrightnessScale(0, 0.5)

        assertEquals(0.8, controller.currentSegments().single().brightness)
        assertEquals(0.5, controller.currentSegments().single().brightnessScale)
        assertEquals(0.4, scaleCommands.single().brightness, 0.0001)

        val sliderCommands = controller.applyBrightnessToSelected(0.6)

        assertEquals(0.6, controller.currentSegments().single().brightness)
        assertEquals(0.3, sliderCommands.single().brightness, 0.0001)
    }

    @Test
    fun `shared edits only command selected segments`() {
        val controller = AdvancedLightController()
        controller.loadFromDevice(
            WledDeviceSnapshot(
                segments = listOf(
                    WledSegmentSnapshot(0, true, true, "#FFFFFF", 1.0, LightEffect.NONE, 128, 128),
                    WledSegmentSnapshot(1, true, false, "#FFFFFF", 1.0, LightEffect.NONE, 128, 128),
                ),
            ),
            AdvancedLightPreferences(),
        )

        val commands = controller.applyEffectToSelected(LightEffect.FIRE)

        assertEquals(listOf(0), commands.map { it.id })
        assertEquals(LightEffect.FIRE, controller.currentSegments()[0].effect)
        assertEquals(LightEffect.NONE, controller.currentSegments()[1].effect)
    }

    @Test
    fun `coversAllKnownSegments detects full segment updates`() {
        val controller = AdvancedLightController()
        controller.loadFromDevice(
            WledDeviceSnapshot(
                segments = listOf(
                    WledSegmentSnapshot(0, true, true, "#FFFFFF", 1.0, LightEffect.NONE, 128, 128),
                    WledSegmentSnapshot(1, true, true, "#FFFFFF", 1.0, LightEffect.NONE, 128, 128),
                ),
            ),
            AdvancedLightPreferences(),
        )

        val fullUpdate = controller.applyColorToSelected("#AA5500")
        val partialUpdate = listOf(fullUpdate.first())

        assertEquals(true, controller.coversAllKnownSegments(fullUpdate))
        assertEquals(false, controller.coversAllKnownSegments(partialUpdate))
    }
}
