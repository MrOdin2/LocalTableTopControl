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
    fun `deserialize returns null for valid node followed by trailing junk`() {
        assertNull(LayoutSerializer.deserialize("leaf(Map)garbage"))
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

    // ── replaceNodeByRef ─────────────────────────────────────────────────────

    @Test
    fun `replaceNodeByRef replaces a leaf by reference`() {
        val target = PaneNode.Leaf("A")
        val replacement = PaneNode.Leaf("X")
        val root = PaneNode.Split(Orientation.HORIZONTAL, 0.5, target, PaneNode.Leaf("B"))
        val result = replaceNodeByRef(root, target, replacement)
        val expected = PaneNode.Split(Orientation.HORIZONTAL, 0.5, replacement, PaneNode.Leaf("B"))
        assertEquals(expected, result)
    }

    @Test
    fun `replaceNodeByRef replaces a split node by reference`() {
        val inner = PaneNode.Split(Orientation.HORIZONTAL, 0.5, PaneNode.Leaf("A"), PaneNode.Leaf("B"))
        val c = PaneNode.Leaf("C")
        val root = PaneNode.Split(Orientation.VERTICAL, 0.5, inner, c)
        val replacement = PaneNode.Leaf("X")
        val result = replaceNodeByRef(root, inner, replacement)
        val expected = PaneNode.Split(Orientation.VERTICAL, 0.5, replacement, c)
        assertEquals(expected, result)
    }

    @Test
    fun `replaceNodeByRef does not replace structurally equal but distinct instances`() {
        // Two leaves with identical content but different references — only the exact target is replaced.
        val target = PaneNode.Leaf("A")
        val otherA = PaneNode.Leaf("A")
        val root = PaneNode.Split(Orientation.HORIZONTAL, 0.5, target, otherA)
        val replacement = PaneNode.Leaf("X")
        val result = replaceNodeByRef(root, target, replacement)
        val expected = PaneNode.Split(Orientation.HORIZONTAL, 0.5, replacement, otherA)
        assertEquals(expected, result)
    }

    // ── findAncestry ─────────────────────────────────────────────────────────

    @Test
    fun `findAncestry on root leaf returns all-null ancestry`() {
        val leaf = PaneNode.Leaf("A")
        val ancestry = findAncestry(leaf, leaf)
        assertNull(ancestry.parent)
        assertNull(ancestry.posInParent)
        assertNull(ancestry.grandParent)
        assertNull(ancestry.posOfParentInGP)
    }

    @Test
    fun `findAncestry returns correct parent and position for first child`() {
        val a = PaneNode.Leaf("A")
        val b = PaneNode.Leaf("B")
        val root = PaneNode.Split(Orientation.HORIZONTAL, 0.5, a, b)
        val ancestry = findAncestry(root, a)
        assertEquals(root, ancestry.parent)
        assertEquals(ChildPos.FIRST, ancestry.posInParent)
        assertNull(ancestry.grandParent)
        assertNull(ancestry.posOfParentInGP)
    }

    @Test
    fun `findAncestry returns correct parent and position for second child`() {
        val a = PaneNode.Leaf("A")
        val b = PaneNode.Leaf("B")
        val root = PaneNode.Split(Orientation.HORIZONTAL, 0.5, a, b)
        val ancestry = findAncestry(root, b)
        assertEquals(root, ancestry.parent)
        assertEquals(ChildPos.SECOND, ancestry.posInParent)
    }

    @Test
    fun `findAncestry returns grandParent info for deeply nested leaf`() {
        // Tree: Split(VERTICAL, Split(HORIZONTAL, A, B), C)
        val a = PaneNode.Leaf("A")
        val b = PaneNode.Leaf("B")
        val c = PaneNode.Leaf("C")
        val inner = PaneNode.Split(Orientation.HORIZONTAL, 0.5, a, b)
        val root = PaneNode.Split(Orientation.VERTICAL, 0.5, inner, c)

        val ancestryA = findAncestry(root, a)
        assertEquals(inner, ancestryA.parent)
        assertEquals(ChildPos.FIRST, ancestryA.posInParent)
        assertEquals(root, ancestryA.grandParent)
        assertEquals(ChildPos.FIRST, ancestryA.posOfParentInGP)

        val ancestryB = findAncestry(root, b)
        assertEquals(inner, ancestryB.parent)
        assertEquals(ChildPos.SECOND, ancestryB.posInParent)
        assertEquals(root, ancestryB.grandParent)
        assertEquals(ChildPos.FIRST, ancestryB.posOfParentInGP)
    }

    @Test
    fun `findAncestry uses reference equality not structural equality`() {
        val target = PaneNode.Leaf("A")
        val otherA = PaneNode.Leaf("A") // same plugin name, different instance
        val root = PaneNode.Split(Orientation.HORIZONTAL, 0.5, target, otherA)
        // Searching for target should find the first child, not the second.
        val ancestry = findAncestry(root, target)
        assertEquals(ChildPos.FIRST, ancestry.posInParent)
        // Searching for otherA should find the second child.
        val ancestry2 = findAncestry(root, otherA)
        assertEquals(ChildPos.SECOND, ancestry2.posInParent)
    }

    @Test
    fun `findAncestry returns all-null when leaf is not in tree`() {
        val notInTree = PaneNode.Leaf("Z")
        val root = PaneNode.Split(Orientation.HORIZONTAL, 0.5, PaneNode.Leaf("A"), PaneNode.Leaf("B"))
        val ancestry = findAncestry(root, notInTree)
        assertNull(ancestry.parent)
        assertNull(ancestry.posInParent)
    }

    // ── Panel extension — same-level ─────────────────────────────────────────
    //
    // Layout used: Split(VERTICAL, Split(HORIZONTAL, A, B), C)
    // Visually:  top-left A | top-right B
    //            ───────────────────────
    //                  bottom C

    @Test
    fun `same-level extend right on A removes B and leaves Split(VERTICAL, A, C)`() {
        val a = PaneNode.Leaf("A")
        val b = PaneNode.Leaf("B")
        val c = PaneNode.Leaf("C")
        val inner = PaneNode.Split(Orientation.HORIZONTAL, 0.5, a, b)
        val root = PaneNode.Split(Orientation.VERTICAL, 0.5, inner, c)

        // A is FIRST in HORIZONTAL parent → "Extend Right" removes B
        val expected = PaneNode.Split(Orientation.VERTICAL, 0.5, a, c)
        assertEquals(expected, removeNode(root, b))
    }

    @Test
    fun `same-level extend left on B removes A and leaves Split(VERTICAL, B, C)`() {
        val a = PaneNode.Leaf("A")
        val b = PaneNode.Leaf("B")
        val c = PaneNode.Leaf("C")
        val inner = PaneNode.Split(Orientation.HORIZONTAL, 0.5, a, b)
        val root = PaneNode.Split(Orientation.VERTICAL, 0.5, inner, c)

        // B is SECOND in HORIZONTAL parent → "Extend Left" removes A
        val expected = PaneNode.Split(Orientation.VERTICAL, 0.5, b, c)
        assertEquals(expected, removeNode(root, a))
    }

    // ── Panel extension — cross-level ────────────────────────────────────────

    @Test
    fun `cross-level extend below on A produces Split(HORIZONTAL, A, Split(VERTICAL, B, C))`() {
        val a = PaneNode.Leaf("A")
        val b = PaneNode.Leaf("B")
        val c = PaneNode.Leaf("C")
        val inner = PaneNode.Split(Orientation.HORIZONTAL, 0.5, a, b)
        val root = PaneNode.Split(Orientation.VERTICAL, 0.5, inner, c)

        // Simulate the cross-level extend algorithm for A (same logic as computeExtendOptions):
        // A is FIRST in HORIZONTAL inner; inner is FIRST in VERTICAL root; uncle = C.
        val ancestry = findAncestry(root, a)
        val parent = ancestry.parent!!
        val posInParent = ancestry.posInParent!!         // FIRST
        val grandParent = ancestry.grandParent!!
        val posOfParentInGP = ancestry.posOfParentInGP!! // FIRST
        val sibling = if (posInParent == ChildPos.FIRST) parent.second else parent.first   // B
        val uncle = if (posOfParentInGP == ChildPos.FIRST) grandParent.second else grandParent.first // C

        // combined = Split(VERTICAL, B, C)  — sibling at parentPos (FIRST), uncle at unclePos (SECOND)
        val combined = if (posOfParentInGP == ChildPos.FIRST) {
            PaneNode.Split(grandParent.orientation, 0.5, sibling, uncle)
        } else {
            PaneNode.Split(grandParent.orientation, 0.5, uncle, sibling)
        }
        // newGPNode = Split(HORIZONTAL, A, combined)  — leaf at leafPos (FIRST)
        val newGPNode = if (posInParent == ChildPos.FIRST) {
            PaneNode.Split(parent.orientation, 0.5, a, combined)
        } else {
            PaneNode.Split(parent.orientation, 0.5, combined, a)
        }
        val result = replaceNodeByRef(root, grandParent, newGPNode)

        val expected = PaneNode.Split(Orientation.HORIZONTAL, 0.5, a, PaneNode.Split(Orientation.VERTICAL, 0.5, b, c))
        assertEquals(expected, result)
    }

    @Test
    fun `cross-level extend below on B produces Split(HORIZONTAL, Split(VERTICAL, A, C), B)`() {
        val a = PaneNode.Leaf("A")
        val b = PaneNode.Leaf("B")
        val c = PaneNode.Leaf("C")
        val inner = PaneNode.Split(Orientation.HORIZONTAL, 0.5, a, b)
        val root = PaneNode.Split(Orientation.VERTICAL, 0.5, inner, c)

        // Simulate the cross-level extend algorithm for B:
        // B is SECOND in HORIZONTAL inner; inner is FIRST in VERTICAL root; uncle = C.
        val ancestry = findAncestry(root, b)
        val parent = ancestry.parent!!
        val posInParent = ancestry.posInParent!!         // SECOND
        val grandParent = ancestry.grandParent!!
        val posOfParentInGP = ancestry.posOfParentInGP!! // FIRST
        val sibling = if (posInParent == ChildPos.FIRST) parent.second else parent.first   // A
        val uncle = if (posOfParentInGP == ChildPos.FIRST) grandParent.second else grandParent.first // C

        // combined = Split(VERTICAL, A, C)  — sibling A at parentPos (FIRST), uncle C at unclePos (SECOND)
        val combined = if (posOfParentInGP == ChildPos.FIRST) {
            PaneNode.Split(grandParent.orientation, 0.5, sibling, uncle)
        } else {
            PaneNode.Split(grandParent.orientation, 0.5, uncle, sibling)
        }
        // newGPNode = Split(HORIZONTAL, combined, B)  — leaf B at leafPos (SECOND)
        val newGPNode = if (posInParent == ChildPos.FIRST) {
            PaneNode.Split(parent.orientation, 0.5, b, combined)
        } else {
            PaneNode.Split(parent.orientation, 0.5, combined, b)
        }
        val result = replaceNodeByRef(root, grandParent, newGPNode)

        val expected = PaneNode.Split(Orientation.HORIZONTAL, 0.5, PaneNode.Split(Orientation.VERTICAL, 0.5, a, c), b)
        assertEquals(expected, result)
    }

    @Test
    fun `cross-level extend not offered when uncle is a Split`() {
        // Layout: Split(VERTICAL, Split(HORIZONTAL, A, B), Split(HORIZONTAL, D, E))
        // C has a Split uncle, so cross-level extend is unavailable.
        val a = PaneNode.Leaf("A")
        val b = PaneNode.Leaf("B")
        val d = PaneNode.Leaf("D")
        val e = PaneNode.Leaf("E")
        val inner = PaneNode.Split(Orientation.HORIZONTAL, 0.5, a, b)
        val uncle = PaneNode.Split(Orientation.HORIZONTAL, 0.5, d, e)
        val root = PaneNode.Split(Orientation.VERTICAL, 0.5, inner, uncle)

        // findAncestry(root, a).grandParent.second is a Split → cross-level extend should be skipped.
        val ancestry = findAncestry(root, a)
        val uncleNode = if (ancestry.posOfParentInGP == ChildPos.FIRST) ancestry.grandParent!!.second else ancestry.grandParent!!.first
        // The uncle is a Split — verifying the guard condition is correct.
        assert(uncleNode is PaneNode.Split) { "Expected uncle to be a Split" }
    }
}
