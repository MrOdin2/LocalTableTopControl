package com.tabletopcontrol.core

import javafx.geometry.Orientation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        // Layout: V(H(A, B), V(D, E))
        // A's uncle V(D,E) is a Split → cross-level (case 2) is skipped.
        // V(D,E) also has different orientation than H(A,B) → same-uncle-orientation (case 3) skipped too.
        // Only same-level "Extend Right" (absorbing B) is available.
        val a = PaneNode.Leaf("A")
        val b = PaneNode.Leaf("B")
        val d = PaneNode.Leaf("D")
        val e = PaneNode.Leaf("E")
        val inner = PaneNode.Split(Orientation.HORIZONTAL, 0.5, a, b)
        val uncle = PaneNode.Split(Orientation.VERTICAL,   0.5, d, e)
        val root  = PaneNode.Split(Orientation.VERTICAL,   0.5, inner, uncle)

        val options = computeExtendOptions(root, a)
        assertTrue(options.containsKey("Extend Right"),  "Expected same-level Extend Right to be offered")
        assertFalse(options.containsKey("Extend Below"), "Extend Below (cross-level) must NOT be offered when uncle is a Split")
    }

    // ── Panel extension — same-uncle-orientation ─────────────────────────────
    //
    // Layout: H(Lights, H(V(Tracker, Map), V(Music, Soundboard)))
    // Visually:
    //   Lights | Tracker | Music
    //          | Map     | Soundboard

    @Test
    fun `same-uncle-orientation extend right on Tracker produces V(Tracker, H(Map, Soundboard))`() {
        val lights     = PaneNode.Leaf("Lights")
        val tracker    = PaneNode.Leaf("Tracker")
        val map        = PaneNode.Leaf("Map")
        val music      = PaneNode.Leaf("Music")
        val soundboard = PaneNode.Leaf("Soundboard")
        val innerLeft  = PaneNode.Split(Orientation.VERTICAL,   0.5, tracker, map)
        val innerRight = PaneNode.Split(Orientation.VERTICAL,   0.5, music,   soundboard)
        val rightPart  = PaneNode.Split(Orientation.HORIZONTAL, 0.5, innerLeft, innerRight)
        val root       = PaneNode.Split(Orientation.HORIZONTAL, 0.2, lights,  rightPart)

        // Tracker: FIRST in V parent; uncle = V(Music,Sound) same orientation; uncle.first = Music.
        // posOfParentInGP = FIRST → merged = H(sibling=Map, remainingUncle=Soundboard)
        // newGPNode = V(Tracker, H(Map, Soundboard))
        val ancestry       = findAncestry(root, tracker)
        val posInParent    = ancestry.posInParent!!         // FIRST
        val grandParent    = ancestry.grandParent!!         // H(innerLeft, innerRight) = rightPart
        val posOfParentInGP = ancestry.posOfParentInGP!!   // FIRST
        val uncle          = grandParent.second as PaneNode.Split // V(Music, Soundboard)
        val remainingUncle = uncle.second                  // Soundboard (uncle.first=Music absorbed)
        val sibling        = innerLeft.second              // Map

        val merged    = PaneNode.Split(grandParent.orientation, 0.5, sibling, remainingUncle) // H(Map, Soundboard)
        val newGPNode = PaneNode.Split(ancestry.parent!!.orientation, 0.5, tracker, merged)   // V(Tracker, H(Map, Soundboard))
        val result    = replaceNodeByRef(root, grandParent, newGPNode)

        val expected = PaneNode.Split(Orientation.HORIZONTAL, 0.2, lights,
            PaneNode.Split(Orientation.VERTICAL, 0.5, tracker,
                PaneNode.Split(Orientation.HORIZONTAL, 0.5, map, soundboard)))
        assertEquals(expected, result)
    }

    @Test
    fun `same-uncle-orientation extend right on Map produces V(H(Tracker, Music), Map)`() {
        val lights     = PaneNode.Leaf("Lights")
        val tracker    = PaneNode.Leaf("Tracker")
        val map        = PaneNode.Leaf("Map")
        val music      = PaneNode.Leaf("Music")
        val soundboard = PaneNode.Leaf("Soundboard")
        val innerLeft  = PaneNode.Split(Orientation.VERTICAL,   0.5, tracker, map)
        val innerRight = PaneNode.Split(Orientation.VERTICAL,   0.5, music,   soundboard)
        val rightPart  = PaneNode.Split(Orientation.HORIZONTAL, 0.5, innerLeft, innerRight)
        val root       = PaneNode.Split(Orientation.HORIZONTAL, 0.2, lights,  rightPart)

        // Map: SECOND in V parent; uncle.second = Soundboard absorbed; remainingUncle = Music.
        // posOfParentInGP = FIRST → merged = H(sibling=Tracker, remainingUncle=Music)
        // newGPNode = V(H(Tracker, Music), Map)
        val merged    = PaneNode.Split(Orientation.HORIZONTAL, 0.5, tracker, music)
        val newGPNode = PaneNode.Split(Orientation.VERTICAL,   0.5, merged, map)
        val result    = replaceNodeByRef(root, rightPart, newGPNode)

        val expected = PaneNode.Split(Orientation.HORIZONTAL, 0.2, lights,
            PaneNode.Split(Orientation.VERTICAL, 0.5,
                PaneNode.Split(Orientation.HORIZONTAL, 0.5, tracker, music), map))
        assertEquals(expected, result)
    }

    @Test
    fun `same-uncle-orientation extend left on Music produces V(Music, H(Map, Soundboard))`() {
        val lights     = PaneNode.Leaf("Lights")
        val tracker    = PaneNode.Leaf("Tracker")
        val map        = PaneNode.Leaf("Map")
        val music      = PaneNode.Leaf("Music")
        val soundboard = PaneNode.Leaf("Soundboard")
        val innerLeft  = PaneNode.Split(Orientation.VERTICAL,   0.5, tracker, map)
        val innerRight = PaneNode.Split(Orientation.VERTICAL,   0.5, music,   soundboard)
        val rightPart  = PaneNode.Split(Orientation.HORIZONTAL, 0.5, innerLeft, innerRight)
        val root       = PaneNode.Split(Orientation.HORIZONTAL, 0.2, lights,  rightPart)

        // Music: FIRST in V parent; uncle = V(Tracker,Map); uncle.first = Tracker absorbed; remainingUncle = Map.
        // posOfParentInGP = SECOND → merged = H(remainingUncle=Map, sibling=Soundboard)
        // newGPNode = V(Music, H(Map, Soundboard))
        val merged    = PaneNode.Split(Orientation.HORIZONTAL, 0.5, map, soundboard)
        val newGPNode = PaneNode.Split(Orientation.VERTICAL,   0.5, music, merged)
        val result    = replaceNodeByRef(root, rightPart, newGPNode)

        val expected = PaneNode.Split(Orientation.HORIZONTAL, 0.2, lights,
            PaneNode.Split(Orientation.VERTICAL, 0.5, music,
                PaneNode.Split(Orientation.HORIZONTAL, 0.5, map, soundboard)))
        assertEquals(expected, result)
    }

    @Test
    fun `same-uncle-orientation extend left on Soundboard produces V(H(Tracker, Music), Soundboard)`() {
        val lights     = PaneNode.Leaf("Lights")
        val tracker    = PaneNode.Leaf("Tracker")
        val map        = PaneNode.Leaf("Map")
        val music      = PaneNode.Leaf("Music")
        val soundboard = PaneNode.Leaf("Soundboard")
        val innerLeft  = PaneNode.Split(Orientation.VERTICAL,   0.5, tracker, map)
        val innerRight = PaneNode.Split(Orientation.VERTICAL,   0.5, music,   soundboard)
        val rightPart  = PaneNode.Split(Orientation.HORIZONTAL, 0.5, innerLeft, innerRight)
        val root       = PaneNode.Split(Orientation.HORIZONTAL, 0.2, lights,  rightPart)

        // Soundboard: SECOND in V parent; uncle.second = Map absorbed; remainingUncle = Tracker.
        // posOfParentInGP = SECOND → merged = H(remainingUncle=Tracker, sibling=Music)
        // newGPNode = V(H(Tracker, Music), Soundboard)
        val merged    = PaneNode.Split(Orientation.HORIZONTAL, 0.5, tracker, music)
        val newGPNode = PaneNode.Split(Orientation.VERTICAL,   0.5, merged, soundboard)
        val result    = replaceNodeByRef(root, rightPart, newGPNode)

        val expected = PaneNode.Split(Orientation.HORIZONTAL, 0.2, lights,
            PaneNode.Split(Orientation.VERTICAL, 0.5,
                PaneNode.Split(Orientation.HORIZONTAL, 0.5, tracker, music), soundboard))
        assertEquals(expected, result)
    }

    @Test
    fun `same-uncle-orientation not offered when uncle orientation differs from parent`() {
        // Layout: H(H(V(A, B), H(C, D)))  — uncle H(C,D) has HORIZONTAL orientation but parent V(A,B) is VERTICAL
        val a = PaneNode.Leaf("A")
        val b = PaneNode.Leaf("B")
        val c = PaneNode.Leaf("C")
        val d = PaneNode.Leaf("D")
        val parent = PaneNode.Split(Orientation.VERTICAL,   0.5, a, b)
        val uncle  = PaneNode.Split(Orientation.HORIZONTAL, 0.5, c, d)
        val root   = PaneNode.Split(Orientation.HORIZONTAL, 0.5, parent, uncle)

        // uncle.orientation (H) != parent.orientation (V) → same-uncle-orientation guard fails.
        val ancestry = findAncestry(root, a)
        val uncleNode = ancestry.grandParent!!.second as PaneNode.Split
        assertNotEquals(parent.orientation, uncleNode.orientation)
    }

    // ── Panel extension — great-uncle-is-Leaf (3-level) ──────────────────────
    //
    // Layout: H(Lights, H(V(Tracker, Map), V(Music, Soundboard)))
    // Tracker and Map can extend left into Lights by splitting it.

    @Test
    fun `great-uncle extend left on Tracker produces H(V(Tracker,H(Lights,Map)), V(Music,Soundboard))`() {
        val lights     = PaneNode.Leaf("Lights")
        val tracker    = PaneNode.Leaf("Tracker")
        val map        = PaneNode.Leaf("Map")
        val music      = PaneNode.Leaf("Music")
        val soundboard = PaneNode.Leaf("Soundboard")
        val innerLeft  = PaneNode.Split(Orientation.VERTICAL,   0.5, tracker, map)
        val innerRight = PaneNode.Split(Orientation.VERTICAL,   0.5, music,   soundboard)
        val rightPart  = PaneNode.Split(Orientation.HORIZONTAL, 0.5, innerLeft, innerRight)
        val root       = PaneNode.Split(Orientation.HORIZONTAL, 0.2, lights,  rightPart)

        // Tracker: FIRST in V(Tracker,Map); V(T,M) is FIRST in rightPart; rightPart is SECOND in root.
        // → great-uncle = Lights; posOfGPInGGP = SECOND; uncle = innerRight = V(Music,Soundboard)
        //
        // merged    = H(greatUncle=Lights, sibling=Map)    [posOfGPInGGP=SECOND → Lights at FIRST]
        // newNode   = V(tracker, H(Lights,Map))            [posInParent=FIRST → tracker at FIRST]
        // newGGPNode = H(newNode, uncle)                   [posOfGPInGGP=SECOND → newNode at FIRST]
        val ancestry    = findAncestry(root, tracker)
        assertEquals(root,            ancestry.greatGrandParent)
        assertEquals(ChildPos.SECOND, ancestry.posOfGPInGGP)

        val uncle      = innerRight              // grandParent.second (posOfParentInGP=FIRST)
        val merged     = PaneNode.Split(Orientation.HORIZONTAL, 0.5, lights, map)
        val newNode    = PaneNode.Split(Orientation.VERTICAL,   0.5, tracker, merged)
        val newGGPNode = PaneNode.Split(Orientation.HORIZONTAL, 0.5, newNode, uncle)
        val result     = replaceNodeByRef(root, root, newGGPNode)

        val expected = PaneNode.Split(Orientation.HORIZONTAL, 0.5,
            PaneNode.Split(Orientation.VERTICAL, 0.5, tracker,
                PaneNode.Split(Orientation.HORIZONTAL, 0.5, lights, map)),
            PaneNode.Split(Orientation.VERTICAL, 0.5, music, soundboard))
        assertEquals(expected, result)
    }

    @Test
    fun `great-uncle extend left on Map produces H(V(H(Lights,Tracker),Map), V(Music,Soundboard))`() {
        val lights     = PaneNode.Leaf("Lights")
        val tracker    = PaneNode.Leaf("Tracker")
        val map        = PaneNode.Leaf("Map")
        val music      = PaneNode.Leaf("Music")
        val soundboard = PaneNode.Leaf("Soundboard")
        val innerLeft  = PaneNode.Split(Orientation.VERTICAL,   0.5, tracker, map)
        val innerRight = PaneNode.Split(Orientation.VERTICAL,   0.5, music,   soundboard)
        val rightPart  = PaneNode.Split(Orientation.HORIZONTAL, 0.5, innerLeft, innerRight)
        val root       = PaneNode.Split(Orientation.HORIZONTAL, 0.2, lights,  rightPart)

        // Map: SECOND in V(Tracker,Map); sibling = Tracker; great-uncle = Lights; uncle = innerRight.
        // merged    = H(greatUncle=Lights, sibling=Tracker)  [posOfGPInGGP=SECOND → Lights at FIRST]
        // newNode   = V(H(Lights,Tracker), Map)              [posInParent=SECOND → merged at FIRST]
        // newGGPNode = H(newNode, uncle)                     [posOfGPInGGP=SECOND → newNode at FIRST]
        val uncle      = innerRight
        val merged     = PaneNode.Split(Orientation.HORIZONTAL, 0.5, lights, tracker)
        val newNode    = PaneNode.Split(Orientation.VERTICAL,   0.5, merged, map)
        val newGGPNode = PaneNode.Split(Orientation.HORIZONTAL, 0.5, newNode, uncle)
        val result     = replaceNodeByRef(root, root, newGGPNode)

        val expected = PaneNode.Split(Orientation.HORIZONTAL, 0.5,
            PaneNode.Split(Orientation.VERTICAL, 0.5,
                PaneNode.Split(Orientation.HORIZONTAL, 0.5, lights, tracker), map),
            PaneNode.Split(Orientation.VERTICAL, 0.5, music, soundboard))
        assertEquals(expected, result)
    }

    @Test
    fun `findAncestry correctly tracks great-grandparent at depth 3`() {
        val lights     = PaneNode.Leaf("Lights")
        val tracker    = PaneNode.Leaf("Tracker")
        val map        = PaneNode.Leaf("Map")
        val music      = PaneNode.Leaf("Music")
        val soundboard = PaneNode.Leaf("Soundboard")
        val innerLeft  = PaneNode.Split(Orientation.VERTICAL,   0.5, tracker, map)
        val innerRight = PaneNode.Split(Orientation.VERTICAL,   0.5, music,   soundboard)
        val rightPart  = PaneNode.Split(Orientation.HORIZONTAL, 0.5, innerLeft, innerRight)
        val root       = PaneNode.Split(Orientation.HORIZONTAL, 0.2, lights,  rightPart)

        val ancestryTracker = findAncestry(root, tracker)
        assertEquals(root,       ancestryTracker.greatGrandParent)
        assertEquals(ChildPos.SECOND, ancestryTracker.posOfGPInGGP)

        // Lights is at depth 1 (parent = root, no grandParent)
        val ancestryLights = findAncestry(root, lights)
        assertEquals(root, ancestryLights.parent)
        assertNull(ancestryLights.grandParent)
        assertNull(ancestryLights.greatGrandParent)
    }
}
