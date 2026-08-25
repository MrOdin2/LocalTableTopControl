package com.tabletopcontrol.hotkey

import com.tabletopcontrol.core.MusicControlOperation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HotkeySettingsCodecTest {
    @Test
    fun `settings round trip preserves every action type`() {
        val settings = HotkeySettings(
            columns = 5,
            hotkeys = listOf(
                HotkeyDefinition(
                    id = "storm",
                    name = "Sudden Lightning",
                    icon = "⚡",
                    colorHex = "#123ABC",
                    binding = KeyBinding("K", control = true, shift = true),
                    actions = listOf(
                        LightAction(
                            target = HotkeyLightTarget.SEGMENTS,
                            segmentIds = setOf(0, 2),
                            power = true,
                            colorHex = "#FFFFFF",
                            effectId = 57,
                            brightness = 0.8,
                            effectSpeed = 200,
                            effectIntensity = 240,
                        ),
                        PlaySoundAction("file:///thunder.mp3", 0.75),
                        MusicAction(MusicControlOperation.SWITCH, "file:///storm.mp3"),
                        TriggerHotkeyAction("follow-up"),
                        WaitAction(900),
                    ),
                ),
                HotkeyDefinition("follow-up", "Follow Up", binding = KeyBinding("F2")),
            ),
        )

        val parsed = HotkeySettingsCodec.parse(HotkeySettingsCodec.serialize(settings))

        assertEquals(HotkeySettingsCodec.normalize(settings), parsed)
    }

    @Test
    fun `normalization bounds columns waits and action count`() {
        val settings = HotkeySettings(
            columns = 99,
            hotkeys = listOf(
                HotkeyDefinition(
                    id = "bounded",
                    name = "Bounded",
                    actions = (1..20).map { WaitAction(MAX_WAIT_MILLIS + it) },
                ),
            ),
        )

        val normalized = HotkeySettingsCodec.normalize(settings)

        assertEquals(MAX_MATRIX_COLUMNS, normalized.columns)
        assertEquals(MAX_ACTIONS_PER_HOTKEY, normalized.hotkeys.single().actions.size)
        normalized.hotkeys.single().actions.forEach { action ->
            assertEquals(MAX_WAIT_MILLIS, (action as WaitAction).durationMillis)
        }
    }

    @Test
    fun `parse rejects unsupported version`() {
        assertNull(HotkeySettingsCodec.parse("version=99\nhotkey.count=0"))
    }
}
