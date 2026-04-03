package com.tabletopcontrol.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import javafx.scene.paint.Color

class SoundboardPluginTest {

    @Test
    fun `columnsForWidth returns 2 for zero width`() {
        assertEquals(2, SoundboardPlugin.columnsForWidth(0.0))
    }

    @Test
    fun `columnsForWidth returns 2 for width just below threshold`() {
        assertEquals(2, SoundboardPlugin.columnsForWidth(SoundboardPlugin.WIDE_THRESHOLD - 1.0))
    }

    @Test
    fun `columnsForWidth returns 8 at the threshold`() {
        assertEquals(8, SoundboardPlugin.columnsForWidth(SoundboardPlugin.WIDE_THRESHOLD))
    }

    @Test
    fun `columnsForWidth returns 8 for wide layout`() {
        assertEquals(8, SoundboardPlugin.columnsForWidth(1200.0))
    }

    @Test
    fun `BUTTON_COUNT is 16`() {
        assertEquals(16, SoundboardPlugin.BUTTON_COUNT)
    }

    @Test
    fun `clampButtonCount clamps to max`() {
        assertEquals(SoundboardPlugin.MAX_BUTTON_COUNT, SoundboardPlugin.clampButtonCount(100))
    }

    @Test
    fun `clampButtonCount clamps negative to zero`() {
        assertEquals(0, SoundboardPlugin.clampButtonCount(-1))
    }

    @Test
    fun `shouldShowInlineAddButton true for non-rectangular fill`() {
        assertEquals(true, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 3, columns = 2))
    }

    @Test
    fun `shouldShowInlineAddButton false for perfect rectangle`() {
        assertEquals(false, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 4, columns = 2))
    }

    @Test
    fun `shouldShowInlineAddButton false at max`() {
        assertEquals(
            false,
            SoundboardPlugin.shouldShowInlineAddButton(
                slotCount = SoundboardPlugin.MAX_BUTTON_COUNT,
                columns = 8,
            ),
        )
    }

    @Test
    fun `serialize and parse config round trip`() {
        val input = listOf(
            SoundboardPlugin.SlotConfig(label = "Door Slam", uri = "file:///tmp/door.mp3", colorHex = "#FF0000"),
            SoundboardPlugin.SlotConfig(),
        )

        val text = SoundboardPlugin.serializeConfig(input)
        val parsed = SoundboardPlugin.parseConfig(text)

        assertNotNull(parsed)
        assertEquals(input, parsed)
    }

    @Test
    fun `parseConfig returns null for empty payload`() {
        assertNull(SoundboardPlugin.parseConfig("   \n  "))
    }

    @Test
    fun `serialize and parse preserves color hex values`() {
        val text = SoundboardPlugin.serializeConfig(
            listOf(
                SoundboardPlugin.SlotConfig(label = "Bell", uri = "file:///tmp/bell.mp3", colorHex = "#123ABC"),
            ),
        )

        val parsed = SoundboardPlugin.parseConfig(text)
        assertEquals("#123ABC", parsed?.firstOrNull()?.colorHex)
    }

    @Test
    fun `serialize and parse drops invalid color values`() {
        val malformed = """
            version=1
            count=1
            slot=QmVsbA|ZmlsZTovLy90bXAvYmVsbC5tcDM|bm90LWEtY29sb3I
        """.trimIndent()

        val parsed = SoundboardPlugin.parseConfig(malformed)
        assertNull(parsed?.firstOrNull()?.colorHex)
    }

    @Test
    fun `color web parsing supports output hex format`() {
        val parsed = Color.web("#A1B2C3")
        assertEquals(0xA1 / 255.0, parsed.red, 0.0001)
        assertEquals(0xB2 / 255.0, parsed.green, 0.0001)
        assertEquals(0xC3 / 255.0, parsed.blue, 0.0001)
    }
}
