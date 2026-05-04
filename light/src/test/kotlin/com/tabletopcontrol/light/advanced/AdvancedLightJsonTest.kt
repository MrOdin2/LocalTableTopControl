package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.light.LightEffect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdvancedLightJsonTest {
    @Test
    fun `parseDeviceSnapshot ignores ADA prefix and reads segments`() {
        val raw = """
            ADA{"state":{"seg":[
              {"id":0,"on":true,"bri":128,"col":[[255,160,0],[0,0,0]],"fx":45,"sx":64,"ix":200,"sel":false},
              {"id":2,"on":false,"bri":255,"col":[[1,2,3]],"fx":999,"sx":300,"ix":-3}
            ]},"info":{"name":"WLED"}}
        """.trimIndent()

        val snapshot = AdvancedLightJson.parseDeviceSnapshot(raw)

        assertEquals(2, snapshot.segments.size)
        assertEquals(
            WledSegmentSnapshot(
                id = 0,
                on = true,
                selectedForEdit = false,
                color = "#FFA000",
                brightness = 128 / 255.0,
                effect = LightEffect.FIRE,
                effectSpeed = 64,
                effectIntensity = 200,
            ),
            snapshot.segments[0],
        )
        assertEquals(LightEffect.NONE, snapshot.segments[1].effect)
        assertEquals(255, snapshot.segments[1].effectSpeed)
        assertEquals(0, snapshot.segments[1].effectIntensity)
    }

    @Test
    fun `buildSegmentCommand writes WLED segment JSON`() {
        val json = AdvancedLightJson.buildSegmentCommand(
            listOf(
                AdvancedLightSegmentCommand(
                    id = 3,
                    on = true,
                    color = "#FA0",
                    brightness = 0.5,
                    effect = LightEffect.CANDLE,
                    effectSpeed = 12,
                    effectIntensity = 240,
                ),
            ),
        )

        assertEquals(
            """{"seg":[{"id":3,"on":true,"bri":128,"col":[[255,170,0]],"fx":88,"sx":12,"ix":240}]}""",
            json,
        )
    }
}
