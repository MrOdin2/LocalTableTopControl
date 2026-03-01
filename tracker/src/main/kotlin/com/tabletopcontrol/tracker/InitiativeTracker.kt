package com.tabletopcontrol.tracker

/**
 * Tracks initiative order for a combat encounter.
 *
 * Entries are kept in descending initiative order.  The tracker advances
 * through them one at a time; after the last entry the round counter increments
 * and the first entry becomes active again.
 */
class InitiativeTracker {

    /**
     * A single combatant in the initiative order.
     *
     * @property name       the combatant's display name
     * @property initiative the combatant's initiative roll result
     */
    data class Entry(val name: String, val initiative: Int)

    private val _entries = mutableListOf<Entry>()

    /** Current round number, starting at `1`. */
    var round: Int = 1
        private set

    /** Index of the currently active entry, or `-1` when the tracker is empty. */
    var currentIndex: Int = -1
        private set

    /** Immutable snapshot of the current initiative order (descending initiative). */
    val entries: List<Entry> get() = _entries.toList()

    /** The currently active [Entry], or `null` when the tracker is empty. */
    val currentEntry: Entry? get() = _entries.getOrNull(currentIndex)

    /**
     * Adds a combatant and re-sorts the initiative order by descending initiative value.
     *
     * @param name       the combatant's display name
     * @param initiative the combatant's initiative roll result
     */
    fun add(name: String, initiative: Int) {
        _entries.add(Entry(name, initiative))
        _entries.sortByDescending { it.initiative }
        if (currentIndex == -1) currentIndex = 0
    }

    /**
     * Advances to the next combatant.
     *
     * Wraps back to the first entry at the end of the list and increments [round].
     * Does nothing when the tracker is empty.
     */
    fun next() {
        if (_entries.isEmpty()) return
        currentIndex++
        if (currentIndex >= _entries.size) {
            currentIndex = 0
            round++
        }
    }

    /**
     * Resets the tracker to its initial empty state.
     *
     * Clears all entries, sets [currentIndex] to `-1`, and resets [round] to `1`.
     */
    fun reset() {
        _entries.clear()
        currentIndex = -1
        round = 1
    }
}
