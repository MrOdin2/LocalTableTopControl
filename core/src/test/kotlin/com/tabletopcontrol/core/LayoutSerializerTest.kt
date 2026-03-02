package com.tabletopcontrol.core

import javafx.geometry.Orientation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LayoutSerializerTest {

    // ── Round-trip tests ─────────────────────────────────────────────────────

    @Test
    fun `serialize and deserialize a single leaf`() {
        val node = PaneNode.Leaf("Map")
        assertEquals(node, LayoutSerializer.deserialize(LayoutSerializer.serialize(node)))
    }

    @Test
    fun `serialize and deserialize a horizontal split`() {
        val node = PaneNode.Split(
            orientation = Orientation.HORIZONTAL,
            dividerPosition = 0.5,
            first = PaneNode.Leaf("Map"),
            second = PaneNode.Leaf("Audio"),
        )
        assertEquals(node, LayoutSerializer.deserialize(LayoutSerializer.serialize(node)))
    }

    @Test
    fun `serialize and deserialize a nested split tree`() {
        val node = PaneNode.Split(
            orientation = Orientation.HORIZONTAL,
            dividerPosition = 0.5,
            first = PaneNode.Leaf("Map"),
            second = PaneNode.Split(
                orientation = Orientation.VERTICAL,
                dividerPosition = 0.3,
                first = PaneNode.Leaf("Audio"),
                second = PaneNode.Leaf("Tracker"),
            ),
        )
        assertEquals(node, LayoutSerializer.deserialize(LayoutSerializer.serialize(node)))
    }

    // ── Serialise output format ──────────────────────────────────────────────

    @Test
    fun `serialize leaf produces expected string`() {
        assertEquals("leaf(Map)", LayoutSerializer.serialize(PaneNode.Leaf("Map")))
    }

    @Test
    fun `serialize horizontal split produces expected string`() {
        val node = PaneNode.Split(Orientation.HORIZONTAL, 0.5, PaneNode.Leaf("A"), PaneNode.Leaf("B"))
        assertEquals("split(HORIZONTAL,0.5,leaf(A),leaf(B))", LayoutSerializer.serialize(node))
    }

    // ── Deserialise edge cases ───────────────────────────────────────────────

    @Test
    fun `deserialize returns null for empty string`() {
        assertNull(LayoutSerializer.deserialize(""))
    }

    @Test
    fun `deserialize returns null for blank string`() {
        assertNull(LayoutSerializer.deserialize("   "))
    }

    @Test
    fun `deserialize returns null for malformed input`() {
        assertNull(LayoutSerializer.deserialize("garbage"))
    }

    @Test
    fun `deserialize handles plugin names with spaces`() {
        val node = PaneNode.Leaf("My Plugin")
        assertEquals(node, LayoutSerializer.deserialize(LayoutSerializer.serialize(node)))
    }

    @Test
    fun `deserialize handles plugin names with balanced parentheses`() {
        val node = PaneNode.Leaf("Plugin (v2)")
        assertEquals(node, LayoutSerializer.deserialize(LayoutSerializer.serialize(node)))
    }

    // ── Tree mutation helpers ────────────────────────────────────────────────

    @Test
    fun `replaceNode swaps the target leaf`() {
        val old = PaneNode.Leaf("A")
        val new = PaneNode.Leaf("B")
        val root = PaneNode.Split(Orientation.HORIZONTAL, 0.5, old, PaneNode.Leaf("C"))
        val result = replaceNode(root, old, new)
        val expected = PaneNode.Split(Orientation.HORIZONTAL, 0.5, new, PaneNode.Leaf("C"))
        assertEquals(expected, result)
    }

    @Test
    fun `replaceNode does not affect unrelated leaves with same name`() {
        val target = PaneNode.Leaf("A")
        val other = PaneNode.Leaf("A")
        val root = PaneNode.Split(Orientation.HORIZONTAL, 0.5, target, other)
        val replacement = PaneNode.Leaf("B")
        val result = replaceNode(root, target, replacement)
        // Only the exact target reference should be replaced.
        val expected = PaneNode.Split(Orientation.HORIZONTAL, 0.5, replacement, other)
        assertEquals(expected, result)
    }

    @Test
    fun `removeNode returns null when removing the only leaf`() {
        val leaf = PaneNode.Leaf("A")
        assertNull(removeNode(leaf, leaf))
    }

    @Test
    fun `removeNode collapses parent split leaving sibling as new root`() {
        val target = PaneNode.Leaf("A")
        val sibling = PaneNode.Leaf("B")
        val root = PaneNode.Split(Orientation.HORIZONTAL, 0.5, target, sibling)
        assertEquals(sibling, removeNode(root, target))
    }

    @Test
    fun `removeNode collapses nested split correctly`() {
        val target = PaneNode.Leaf("A")
        val b = PaneNode.Leaf("B")
        val c = PaneNode.Leaf("C")
        val inner = PaneNode.Split(Orientation.VERTICAL, 0.4, target, b)
        val root = PaneNode.Split(Orientation.HORIZONTAL, 0.5, inner, c)
        val expected = PaneNode.Split(Orientation.HORIZONTAL, 0.5, b, c)
        assertEquals(expected, removeNode(root, target))
    }
}
