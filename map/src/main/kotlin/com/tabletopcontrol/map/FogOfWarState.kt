package com.tabletopcontrol.map

/**
 * Tracks which grid cells are revealed and which are hidden by fog of war.
 *
 * The grid is defined by [cols] columns and [rows] rows.  Initially every
 * cell is hidden.  Reveal and hide operations are performed per cell using
 * zero-based (column, row) indices.
 *
 * [cellSizeInUnits] defines the size of each fog cell in game units and must
 * match the [GridConfig.cellSizeInUnits] used by the renderer so that fog
 * cells align with the visible grid.
 *
 * @property cols             number of columns in the grid; must be positive.
 * @property rows             number of rows in the grid; must be positive.
 * @property cellSizeInUnits  size of each fog cell in game units; must be positive.
 */
class FogOfWarState(val cols: Int, val rows: Int, val cellSizeInUnits: Double = 1.0) {

    init {
        require(cols > 0) { "cols must be positive, was $cols" }
        require(rows > 0) { "rows must be positive, was $rows" }
        require(cellSizeInUnits > 0) { "cellSizeInUnits must be positive, was $cellSizeInUnits" }
    }

    /** Internal backing store: `true` means the cell is revealed. */
    private val revealed = Array(cols) { BooleanArray(rows) { false } }

    /**
     * Returns `true` if the cell at ([col], [row]) is currently revealed.
     *
     * @throws IndexOutOfBoundsException if the coordinates are out of range
     */
    fun isRevealed(col: Int, row: Int): Boolean {
        checkBounds(col, row)
        return revealed[col][row]
    }

    /**
     * Reveals the cell at ([col], [row]), making it visible on the map.
     *
     * @throws IndexOutOfBoundsException if the coordinates are out of range
     */
    fun revealCell(col: Int, row: Int) {
        checkBounds(col, row)
        revealed[col][row] = true
    }

    /**
     * Hides the cell at ([col], [row]), covering it with fog.
     *
     * @throws IndexOutOfBoundsException if the coordinates are out of range
     */
    fun hideCell(col: Int, row: Int) {
        checkBounds(col, row)
        revealed[col][row] = false
    }

    /** Reveals every cell in the grid. */
    fun revealAll() {
        for (c in 0 until cols) for (r in 0 until rows) revealed[c][r] = true
    }

    /** Hides every cell in the grid (resets fog of war). */
    fun hideAll() {
        for (c in 0 until cols) for (r in 0 until rows) revealed[c][r] = false
    }

    /**
     * Returns the number of currently revealed cells.
     *
     * Useful for UI feedback and testing.
     */
    fun revealedCount(): Int = revealed.sumOf { col -> col.count { it } }

    private fun checkBounds(col: Int, row: Int) {
        if (col < 0 || col >= cols || row < 0 || row >= rows) {
            throw IndexOutOfBoundsException("Cell ($col, $row) is out of bounds for grid ${cols}x${rows}")
        }
    }
}
