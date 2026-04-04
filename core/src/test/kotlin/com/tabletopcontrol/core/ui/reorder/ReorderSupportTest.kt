package com.tabletopcontrol.core.ui.reorder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReorderSupportTest {

    @Test
    fun `planDropReorder supports append target`() {
        assertEquals(ReorderSupport.Plan(fromIndex = 1, toIndex = 4), ReorderSupport.planDropReorder(5, 1, 5))
        assertEquals(ReorderSupport.Plan(fromIndex = 0, toIndex = 4), ReorderSupport.planDropReorder(5, 0, 5))
        assertNull(ReorderSupport.planDropReorder(5, 4, 5))
    }

    @Test
    fun `planDropReorder adjusts downward moves`() {
        assertEquals(ReorderSupport.Plan(fromIndex = 1, toIndex = 2), ReorderSupport.planDropReorder(5, 1, 3))
    }

    @Test
    fun `planDropReorder keeps upward moves at drop index`() {
        assertEquals(ReorderSupport.Plan(fromIndex = 3, toIndex = 1), ReorderSupport.planDropReorder(5, 3, 1))
    }

    @Test
    fun `planDropReorder returns null for invalid indexes and no-op`() {
        assertNull(ReorderSupport.planDropReorder(5, -1, 2))
        assertNull(ReorderSupport.planDropReorder(5, 1, 6))
        assertNull(ReorderSupport.planDropReorder(5, 2, 2))
    }

    @Test
    fun `reorderMutableList applies plan`() {
        val items = mutableListOf("A", "B", "C", "D")
        ReorderSupport.reorderMutableList(items, ReorderSupport.Plan(fromIndex = 1, toIndex = 3))
        assertEquals(listOf("A", "C", "D", "B"), items)
    }

    @Test
    fun `reorderMutableListFromDrop returns false for invalid no-op input`() {
        val items = mutableListOf("A", "B", "C")
        assertFalse(ReorderSupport.reorderMutableListFromDrop(items, fromIndex = 1, dropTargetIndex = 1))
        assertEquals(listOf("A", "B", "C"), items)
    }

    @Test
    fun `reorderMutableListFromDrop reorders and returns true`() {
        val items = mutableListOf("A", "B", "C")
        assertTrue(ReorderSupport.reorderMutableListFromDrop(items, fromIndex = 0, dropTargetIndex = 3))
        assertEquals(listOf("B", "C", "A"), items)
    }
}
