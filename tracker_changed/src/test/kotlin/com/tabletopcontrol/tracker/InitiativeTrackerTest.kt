package com.tabletopcontrol.tracker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InitiativeTrackerTest {

    private lateinit var tracker: InitiativeTracker

    @BeforeEach
    fun setUp() {
        tracker = InitiativeTracker()
    }

    @Test
    fun `entries are sorted in descending initiative order`() {
        tracker.add("Goblin", 5)
        tracker.add("Paladin", 18)
        tracker.add("Wizard", 12)

        assertEquals(listOf("Paladin", "Wizard", "Goblin"), tracker.entries.map { it.name })
    }

    @Test
    fun `first entry is active after the first add`() {
        tracker.add("Hero", 10)

        assertEquals(0, tracker.currentIndex)
        assertEquals("Hero", tracker.currentEntry?.name)
    }

    @Test
    fun `next advances the current index`() {
        tracker.add("A", 10)
        tracker.add("B", 5)

        tracker.next()

        assertEquals(1, tracker.currentIndex)
    }

    @Test
    fun `next wraps around to the first entry and increments the round`() {
        tracker.add("A", 10)
        tracker.add("B", 5)

        tracker.next() // index → 1
        tracker.next() // wraps → index 0, round 2

        assertEquals(0, tracker.currentIndex)
        assertEquals(2, tracker.round)
    }

    @Test
    fun `next on an empty tracker does nothing`() {
        tracker.next()

        assertEquals(-1, tracker.currentIndex)
        assertEquals(1, tracker.round)
    }

    @Test
    fun `reset clears all state`() {
        tracker.add("A", 10)
        tracker.next()

        tracker.reset()

        assertTrue(tracker.entries.isEmpty())
        assertEquals(-1, tracker.currentIndex)
        assertEquals(1, tracker.round)
        assertNull(tracker.currentEntry)
    }

    // ── HP / AC fields ───────────────────────────────────────────────────────

    @Test
    fun `add stores hp and ac on the entry`() {
        tracker.add("Dragon", 15, hp = 200, ac = 18)

        val entry = tracker.entries.first()
        assertEquals(200, entry.hp)
        assertEquals(18, entry.ac)
    }

    @Test
    fun `hp and ac default to zero when not supplied`() {
        tracker.add("Goblin", 5)

        val entry = tracker.entries.first()
        assertEquals(0, entry.hp)
        assertEquals(0, entry.ac)
    }

    // ── remove ───────────────────────────────────────────────────────────────

    @Test
    fun `remove deletes the entry at the given index`() {
        tracker.add("A", 10)
        tracker.add("B", 5)

        tracker.remove(1)

        assertEquals(listOf("A"), tracker.entries.map { it.name })
    }

    @Test
    fun `remove shifts currentIndex left when a preceding entry is removed`() {
        tracker.add("A", 10)
        tracker.add("B", 5)
        tracker.next() // currentIndex → 1 (B)

        tracker.remove(0) // A removed; B shifts to index 0

        assertEquals(0, tracker.currentIndex)
        assertEquals("B", tracker.currentEntry?.name)
    }

    @Test
    fun `remove clamps currentIndex when the active entry is removed`() {
        tracker.add("A", 10)
        tracker.add("B", 5)
        tracker.next() // currentIndex → 1 (B, the last entry)

        tracker.remove(1) // B removed; clamp to last index (0)

        assertEquals(0, tracker.currentIndex)
        assertEquals("A", tracker.currentEntry?.name)
    }

    @Test
    fun `remove sets currentIndex to minus one when the list becomes empty`() {
        tracker.add("A", 10)

        tracker.remove(0)

        assertEquals(-1, tracker.currentIndex)
        assertNull(tracker.currentEntry)
    }

    @Test
    fun `remove ignores an out-of-bounds index`() {
        tracker.add("A", 10)

        tracker.remove(5)

        assertEquals(1, tracker.entries.size)
    }

    // ── move ─────────────────────────────────────────────────────────────────

    @Test
    fun `move reorders entries without re-sorting`() {
        tracker.add("A", 0)
        tracker.add("B", 0)
        tracker.add("C", 0)

        tracker.move(0, 2) // A moves from first to last

        assertEquals(listOf("B", "C", "A"), tracker.entries.map { it.name })
    }

    @Test
    fun `move updates currentIndex when the active entry is moved`() {
        tracker.add("A", 0)
        tracker.add("B", 0)
        tracker.add("C", 0)
        // currentIndex = 0 (A)

        tracker.move(0, 2)

        assertEquals(2, tracker.currentIndex)
        assertEquals("A", tracker.currentEntry?.name)
    }

    @Test
    fun `move updates currentIndex when an earlier entry is moved past the active one`() {
        tracker.add("A", 0)
        tracker.add("B", 0)
        tracker.add("C", 0)
        tracker.next() // currentIndex → 1 (B)

        tracker.move(0, 2) // A moves past B; B shifts left

        assertEquals(0, tracker.currentIndex)
        assertEquals("B", tracker.currentEntry?.name)
    }

    @Test
    fun `move ignores out-of-bounds indices`() {
        tracker.add("A", 0)
        tracker.add("B", 0)

        tracker.move(0, 5)
        tracker.move(-1, 1)

        assertEquals(listOf("A", "B"), tracker.entries.map { it.name })
    }

    // ── updateEntry ──────────────────────────────────────────────────────────

    @Test
    fun `updateEntry changes only the fields that are supplied`() {
        tracker.add("Hero", 10, hp = 30, ac = 15)

        tracker.updateEntry(0, hp = 25)

        val entry = tracker.entries[0]
        assertEquals("Hero", entry.name)
        assertEquals(25, entry.hp)
        assertEquals(15, entry.ac)
    }

    @Test
    fun `updateEntry can set hp to zero`() {
        tracker.add("Hero", 10, hp = 30)

        tracker.updateEntry(0, hp = 0)

        assertEquals(0, tracker.entries[0].hp)
    }

    @Test
    fun `updateEntry ignores an out-of-bounds index`() {
        tracker.add("Hero", 10)

        tracker.updateEntry(5, name = "Ghost") // should not throw

        assertEquals("Hero", tracker.entries[0].name)
    }
}
