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
    fun `contributor overrides base action with same id`() {
        val base = listOf(action("a"), action("b"))
        val override = contributor(action("a").copy(label = "OVERRIDDEN"))
        val merged = ContextMenuRenderer.mergeActions(base, listOf(override), null)
        // "a" from base is replaced; result contains contributor's version plus "b"
        assertEquals(2, merged.size)
        val ids = merged.map { it.id }
        assertTrue("a" in ids)
        assertTrue("b" in ids)
        val overriddenA = merged.first { it.id == "a" }
        assertEquals("OVERRIDDEN", overriddenA.label)
    }

    @Test
    fun `multiple contributors are all merged`() {
        val base = listOf(action("a"))
        val c1 = contributor(action("b"))
        val c2 = contributor(action("c"))
        val merged = ContextMenuRenderer.mergeActions(base, listOf(c1, c2), null)
        assertEquals(setOf("a", "b", "c"), merged.map { it.id }.toSet())
    }

    @Test
    fun `later contributor overrides earlier contributor for same id`() {
        val c1 = contributor(action("x").copy(label = "from c1"))
        val c2 = contributor(action("x").copy(label = "from c2"))
        val merged = ContextMenuRenderer.mergeActions(emptyList(), listOf(c1, c2), null)
        // Both c1 and c2 provide "x"; flatMap puts c1 first, c2 second.
        // overrideIds is built from ALL contributed ids, so base "x" (if any) is excluded.
        // The remaining list is remaining(base \ overrideIds) + contributed.
        // contributed = [c1.x, c2.x] — both survive since no dedup between contributors.
        assertEquals(2, merged.size)
        assertEquals("x", merged[0].id)
        assertEquals("from c1", merged[0].label)
        assertEquals("x", merged[1].id)
        assertEquals("from c2", merged[1].label)
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
