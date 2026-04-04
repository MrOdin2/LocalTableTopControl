package com.tabletopcontrol.core.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for the merge/override logic inside [ContextMenuRenderer].
 *
 * JavaFX display objects ([ContextMenu], [Alert]) are **not** exercised here — those
 * require a running JavaFX runtime and are not needed to verify merge semantics.
 */
class ContextMenuRendererLogicTest {

    private fun action(id: String, section: MenuSection = MenuSection.BASIC) =
        MenuAction(id = id, label = id.uppercase(), section = section, onAction = {})

    private fun contributor(vararg actions: MenuAction) = object : MenuContributor {
        override fun contributeActions(context: Any?) = actions.toList()
    }

    // ── mergeActions ─────────────────────────────────────────────────────────

    @Test
    fun `no contributors returns base actions unchanged`() {
        val base = listOf(action("a"), action("b"), action("c"))
        val merged = ContextMenuRenderer.mergeActions(base, emptyList(), null)
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun `contributor adds new actions after base actions`() {
        val base = listOf(action("a"), action("b"))
        val extra = contributor(action("c"))
        val merged = ContextMenuRenderer.mergeActions(base, listOf(extra), null)
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun `override preserves base order`() {
        val base = listOf(action("a"), action("b"), action("c"))
        val override = contributor(action("b").copy(label = "OVERRIDDEN"))
        val merged = ContextMenuRenderer.mergeActions(base, listOf(override), null)
        // "b" is replaced in-place; "a" stays first, "c" stays last
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        assertEquals("OVERRIDDEN", merged[1].label)
    }

    @Test
    fun `contributor overrides base action with same id`() {
        val base = listOf(action("a"), action("b"))
        val override = contributor(action("a").copy(label = "OVERRIDDEN"))
        val merged = ContextMenuRenderer.mergeActions(base, listOf(override), null)
        // "a" is replaced in-place; ordering is preserved: a first, then b.
        assertEquals(listOf("a", "b"), merged.map { it.id })
        assertEquals("OVERRIDDEN", merged.first { it.id == "a" }.label)
    }

    @Test
    fun `multiple contributors are all merged`() {
        val base = listOf(action("a"))
        val c1 = contributor(action("b"))
        val c2 = contributor(action("c"))
        val merged = ContextMenuRenderer.mergeActions(base, listOf(c1, c2), null)
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun `later contributor overrides earlier contributor for same id`() {
        val c1 = contributor(action("x").copy(label = "from c1"))
        val c2 = contributor(action("x").copy(label = "from c2"))
        val merged = ContextMenuRenderer.mergeActions(emptyList(), listOf(c1, c2), null)
        // Both contributors supply "x"; last-write wins → only the c2 version survives.
        assertEquals(1, merged.size)
        assertEquals("x", merged[0].id)
        assertEquals("from c2", merged[0].label)
    }

    @Test
    fun `context is forwarded to contributors`() {
        var receivedContext: Any? = "initial"
        val c = object : MenuContributor {
            override fun contributeActions(context: Any?): List<MenuAction> {
                receivedContext = context
                return emptyList()
            }
        }
        val sentContext = object {}
        ContextMenuRenderer.mergeActions(emptyList(), listOf(c), sentContext)
        assertEquals(sentContext, receivedContext)
    }

    @Test
    fun `empty base and no contributors produces empty result`() {
        val merged = ContextMenuRenderer.mergeActions(emptyList(), emptyList(), null)
        assertTrue(merged.isEmpty())
    }
}
