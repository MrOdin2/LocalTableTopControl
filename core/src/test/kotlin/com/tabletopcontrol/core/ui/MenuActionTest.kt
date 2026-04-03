package com.tabletopcontrol.core.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuActionTest {

    @Test
    fun `default section is BASIC`() {
        val action = MenuAction(id = "a", label = "A", onAction = {})
        assertEquals(MenuSection.BASIC, action.section)
    }

    @Test
    fun `default isEnabled is true`() {
        val action = MenuAction(id = "a", label = "A", onAction = {})
        assertTrue(action.isEnabled)
    }

    @Test
    fun `default isVisible is true`() {
        val action = MenuAction(id = "a", label = "A", onAction = {})
        assertTrue(action.isVisible)
    }

    @Test
    fun `default requiresConfirmation is false`() {
        val action = MenuAction(id = "a", label = "A", onAction = {})
        assertFalse(action.requiresConfirmation)
    }

    @Test
    fun `default icon is null`() {
        val action = MenuAction(id = "a", label = "A", onAction = {})
        assertNull(action.icon)
    }

    @Test
    fun `default confirmationMessage is null`() {
        val action = MenuAction(id = "a", label = "A", onAction = {})
        assertNull(action.confirmationMessage)
    }

    @Test
    fun `explicit values are preserved`() {
        var called = false
        val action = MenuAction(
            id = "test.delete",
            label = "Delete",
            icon = "🗑️",
            section = MenuSection.DANGER_ZONE,
            isEnabled = false,
            isVisible = false,
            requiresConfirmation = true,
            confirmationMessage = "Really delete?",
            onAction = { called = true },
        )

        assertEquals("test.delete", action.id)
        assertEquals("Delete", action.label)
        assertEquals("🗑️", action.icon)
        assertEquals(MenuSection.DANGER_ZONE, action.section)
        assertFalse(action.isEnabled)
        assertFalse(action.isVisible)
        assertTrue(action.requiresConfirmation)
        assertEquals("Really delete?", action.confirmationMessage)

        action.onAction()
        assertTrue(called)
    }

    @Test
    fun `copy creates independent instance`() {
        val original = MenuAction(id = "orig", label = "Original", onAction = {})
        val copy = original.copy(id = "copy", label = "Copy")

        assertEquals("orig", original.id)
        assertEquals("copy", copy.id)
        assertEquals("Copy", copy.label)
        // defaults inherited
        assertEquals(MenuSection.BASIC, copy.section)
        assertTrue(copy.isEnabled)
    }

    @Test
    fun `two actions with same non-lambda properties match`() {
        val a1 = MenuAction(id = "x", label = "X", onAction = {})
        val a2 = MenuAction(id = "x", label = "X", onAction = {})
        // Compare fields manually because the onAction lambdas are different instances,
        // so data class equality would include them and not consider these objects equal.
        assertEquals(a1.id, a2.id)
        assertEquals(a1.label, a2.label)
        assertEquals(a1.section, a2.section)
    }
}
