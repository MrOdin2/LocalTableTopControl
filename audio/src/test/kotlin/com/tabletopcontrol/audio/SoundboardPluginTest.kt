package com.tabletopcontrol.audio

import com.tabletopcontrol.core.ui.reorder.ReorderSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

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
        assertEquals(false, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 8, columns = 2))
        assertEquals(false, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 12, columns = 3))
        assertEquals(false, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 20, columns = 4))
        assertEquals(false, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 18, columns = 6))
        assertEquals(false, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 16, columns = 8))
        assertEquals(false, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 24, columns = 4))

        assertEquals(true, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 18, columns = 4))
        assertEquals(true, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 20, columns = 6))
        assertEquals(true, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 10, columns = 3))
        assertEquals(true, SoundboardPlugin.shouldShowInlineAddButton(slotCount = 24, columns = 5))
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
}
