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
}
