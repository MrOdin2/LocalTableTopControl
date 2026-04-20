package com.tabletopcontrol.audio

import com.tabletopcontrol.core.ui.color.ColorHexCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.tabletopcontrol.core.ui.reorder.ReorderSupport
import java.io.File
import java.net.URI

class SoundboardPluginTest {

    @Test
    fun `columnsForWidth returns 2 for zero width`() {
        assertEquals(2, SoundboardPlugin.columnsForWidth(0.0))
    }

    @Test
    fun `columnsForWidth returns 2 for narrow width`() {
        assertEquals(2, SoundboardPlugin.columnsForWidth(180.0))
    }

    @Test
    fun `columnsForWidth scales to 4 for medium width`() {
        assertEquals(4, SoundboardPlugin.columnsForWidth(380.0))
    }

    @Test
    fun `columnsForWidth uses tile pane padding when computing fit`() {
        assertEquals(4, SoundboardPlugin.columnsForWidth(473.0))
        assertEquals(5, SoundboardPlugin.columnsForWidth(474.0))
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
    fun `shouldShowInlineAddButton true when empty`() {
        assertEquals(true, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 0, columns = 2))
    }

    @Test
    fun `shouldShowInlineAddButton covers pane-size rectangle combinations`() {
        // Perfect rectangles for varying pane-driven column counts (2..8): hide inline add.
        assertEquals(false, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 8, columns = 2)) // 2x4
        assertEquals(false, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 12, columns = 3)) // 3x4
        assertEquals(false, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 20, columns = 4)) // 4x5
        assertEquals(false, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 18, columns = 6)) // 6x3
        assertEquals(false, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 16, columns = 8)) // 8x2
        assertEquals(false, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 24, columns = 4)) // 4x6

        // Same slot counts under different pane widths (different columns) may be imperfect: show inline add.
        assertEquals(true, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 18, columns = 4)) // 4x5 imperfect
        assertEquals(true, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 20, columns = 6)) // 6x4 imperfect
        assertEquals(true, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 10, columns = 3)) // 3x4 imperfect
        assertEquals(true, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 24, columns = 5)) // 5x5 imperfect
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
    fun `parseConfig returns null when version header is missing`() {
        val noVersion = """
            count=1
            slot=QmVsbA|ZmlsZTovLy90bXAvYmVsbC5tcDM|IzEyM0FCQw
        """.trimIndent()

        assertNull(SoundboardPlugin.parseConfig(noVersion))
    }

    @Test
    fun `parseConfig returns null for unsupported version`() {
        val unsupportedVersion = """
            version=999
            count=1
            slot=QmVsbA|ZmlsZTovLy90bXAvYmVsbC5tcDM|IzEyM0FCQw
        """.trimIndent()

        assertNull(SoundboardPlugin.parseConfig(unsupportedVersion))
    }

    @Test
    fun `color web parsing supports output hex format`() {
        val parsed = ColorHexCodec.hexToColor("#A1B2C3")
        assertEquals(0xA1 / 255.0, parsed.red, 0.0001)
        assertEquals(0xB2 / 255.0, parsed.green, 0.0001)
        assertEquals(0xC3 / 255.0, parsed.blue, 0.0001)
    }

    @Test
    fun `reorder support plans append target`() {
        assertEquals(ReorderSupport.Plan(fromIndex = 1, toIndex = 4), ReorderSupport.planDropReorder(5, 1, 5))
        assertEquals(ReorderSupport.Plan(fromIndex = 0, toIndex = 4), ReorderSupport.planDropReorder(5, 0, 5))
        assertNull(ReorderSupport.planDropReorder(5, 4, 5))
    }

    @Test
    fun `reorder support plans downward moves`() {
        assertEquals(ReorderSupport.Plan(fromIndex = 1, toIndex = 2), ReorderSupport.planDropReorder(5, 1, 3))
    }

    @Test
    fun `reorder support keeps upward target index`() {
        assertEquals(ReorderSupport.Plan(fromIndex = 3, toIndex = 1), ReorderSupport.planDropReorder(5, 3, 1))
    }

    @Test
    fun `reorder support returns null for invalid indexes`() {
        assertNull(ReorderSupport.planDropReorder(5, -1, 2))
        assertNull(ReorderSupport.planDropReorder(5, 1, 6))
        assertNull(ReorderSupport.planDropReorder(5, 2, 2))
    }

    @Test
    fun `tooltipTextForUri uses absolute path for file uri`() {
        val uri = "file:///tmp/soundboard-test.mp3"
        val tooltip = SoundboardPlugin.tooltipTextForUri(uri)
        assertEquals(File(URI(uri)).absolutePath, tooltip)
    }

    @Test
    fun `tooltipTextForUri falls back to uri when not file uri`() {
        val uri = "http://example.com/audio.mp3"
        assertEquals(uri, SoundboardPlugin.tooltipTextForUri(uri))
    }

    @Test
    fun `tooltipTextForUri returns empty slot text for null`() {
        assertEquals(SoundboardPlugin.EMPTY_SLOT_TOOLTIP, SoundboardPlugin.tooltipTextForUri(null))
    }

    @Test
    fun `buttonVisualState uses stop label when playing`() {
        val state = SoundboardPlugin.buttonVisualState(isPlaying = true, label = "Slot 2")
        assertEquals("⏹ Slot 2", state.text)
        assertEquals(true, state.isPlaying)
    }

    @Test
    fun `buttonVisualState uses idle label when not playing`() {
        val state = SoundboardPlugin.buttonVisualState(isPlaying = false, label = "Slot 2")
        assertEquals("Slot 2", state.text)
        assertEquals(false, state.isPlaying)
    }
}
