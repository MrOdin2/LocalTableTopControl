package com.tabletopcontrol.light

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LightSceneCodecTest {
    @Test
    fun `serialize and deserialize preserve manual light scene state`() {
        val state = LightSceneState(
            power = false,
            color = "#12AB34",
            effect = LightEffect.TWINKLE,
            colorCycling = true,
            brightness = 0.42,
            effectSpeed = 201,
            effectIntensity = 33,
        )

        val restored = LightSceneCodec.deserialize(LightSceneCodec.serialize(state))

        assertEquals(state, restored)
    }
}
