package com.tabletopcontrol.tracker

/**
 * Tracks initiative order for a combat encounter.
 *
 * Entries are kept in descending initiative order after each [add] call.  The
 * tracker advances through them one at a time; after the last entry the round
 * counter increments and the first entry becomes active again.
 *
 * Manual reordering via [move] and removal via [remove] do not re-sort the list.
 */
class InitiativeTracker {

    /**
     * A single combatant in the initiative order.
     *
     * @property name       the combatant's display name
     * @property initiative the combatant's initiative roll result
     * @property hp         the combatant's current hit points (defaults to 0)
     * @property ac         the combatant's armour class (defaults to 0)
     */
    data class Entry(val name: String, val initiative: Int, val hp: Int = 0, val ac: Int = 0)

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
     * @param hp         the combatant's hit points (defaults to 0)
     * @param ac         the combatant's armour class (defaults to 0)
     */
    fun add(name: String, initiative: Int, hp: Int = 0, ac: Int = 0) {
        _entries.add(Entry(name, initiative, hp, ac))
        _entries.sortByDescending { it.initiative }
        if (currentIndex == -1) currentIndex = 0
    }

    /**
     * Removes the combatant at [index].
     *
     * If the removed entry was the active one, [currentIndex] is clamped to the
     * last valid index.  If the list becomes empty, [currentIndex] is set to `-1`.
     *
     * @param index zero-based position in [entries]
     */
    fun remove(index: Int) {
        if (index < 0 || index >= _entries.size) return
        _entries.removeAt(index)
        currentIndex = when {
            _entries.isEmpty() -> -1
            currentIndex > index -> currentIndex - 1
            currentIndex == index -> currentIndex.coerceAtMost(_entries.size - 1)
            else -> currentIndex
        }
    }

    /**
     * Moves the combatant at [fromIndex] to [toIndex], shifting other entries as
     * needed.  This does **not** re-sort the order.
     *
     * [currentIndex] is updated so the same logical entry remains active after the
     * move.
     *
     * @param fromIndex the current position of the entry to move
     * @param toIndex   the desired new position
     */
    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || fromIndex >= _entries.size) return
        if (toIndex < 0 || toIndex >= _entries.size) return
        if (fromIndex == toIndex) return
        val entry = _entries.removeAt(fromIndex)
        _entries.add(toIndex, entry)
        currentIndex = when (currentIndex) {
            fromIndex -> toIndex
            in (fromIndex + 1)..toIndex -> currentIndex - 1
            in toIndex until fromIndex -> currentIndex + 1
            else -> currentIndex
        }
    }

    /**
     * Updates mutable fields of the entry at [index] without affecting the sort order.
     *
     * Only the parameters that are provided (non-null) are changed; omitted
     * parameters retain their current values.
     *
     * @param index the zero-based position to update
     * @param name  new display name, or `null` to keep the current value
     * @param hp    new hit-point value, or `null` to keep the current value
     * @param ac    new armour-class value, or `null` to keep the current value
     */
    fun updateEntry(index: Int, name: String? = null, hp: Int? = null, ac: Int? = null) {
        if (index < 0 || index >= _entries.size) return
        val current = _entries[index]
        _entries[index] = current.copy(
            name = name ?: current.name,
            hp = hp ?: current.hp,
            ac = ac ?: current.ac,
        )
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
     * Sets the active combatant to [index], clamping to the valid range.
     *
     * This is used to restore the logical active combatant after [add] re-sorts
     * the list.  Does nothing when the tracker is empty.
     *
     * @param index the desired zero-based active index
     */
    fun jumpTo(index: Int) {
        if (_entries.isEmpty()) return
        currentIndex = index.coerceIn(0, _entries.size - 1)
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
