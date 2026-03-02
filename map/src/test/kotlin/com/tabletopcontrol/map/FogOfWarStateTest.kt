package com.tabletopcontrol.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FogOfWarStateTest {

    private lateinit var fow: FogOfWarState

    @BeforeEach
    fun setUp() {
        fow = FogOfWarState(cols = 4, rows = 3)
    }

    @Test
    fun `all cells are hidden initially`() {
        assertEquals(0, fow.revealedCount())
    }

    @Test
    fun `revealCell makes the cell revealed`() {
        fow.revealCell(1, 2)
        assertTrue(fow.isRevealed(1, 2))
    }

    @Test
    fun `hideCell makes the cell hidden`() {
        fow.revealCell(0, 0)
        fow.hideCell(0, 0)
        assertFalse(fow.isRevealed(0, 0))
    }

    @Test
    fun `revealAll reveals every cell`() {
        fow.revealAll()
        assertEquals(4 * 3, fow.revealedCount())
    }

    @Test
    fun `hideAll hides every cell`() {
        fow.revealAll()
        fow.hideAll()
        assertEquals(0, fow.revealedCount())
    }

    @Test
    fun `revealCell increments revealedCount`() {
        fow.revealCell(0, 0)
        fow.revealCell(2, 1)
        assertEquals(2, fow.revealedCount())
    }

    @Test
    fun `revealing an already revealed cell does not double count`() {
        fow.revealCell(1, 1)
        fow.revealCell(1, 1)
        assertEquals(1, fow.revealedCount())
    }

    @Test
    fun `isRevealed throws for out-of-bounds column`() {
        assertThrows<IndexOutOfBoundsException> {
            fow.isRevealed(4, 0)
        }
    }

    @Test
    fun `isRevealed throws for out-of-bounds row`() {
        assertThrows<IndexOutOfBoundsException> {
            fow.isRevealed(0, 3)
        }
    }

    @Test
    fun `revealCell throws for negative column`() {
        assertThrows<IndexOutOfBoundsException> {
            fow.revealCell(-1, 0)
        }
    }

    @Test
    fun `constructor throws for zero cols`() {
        assertThrows<IllegalArgumentException> {
            FogOfWarState(cols = 0, rows = 1)
        }
    }

    @Test
    fun `constructor throws for zero rows`() {
        assertThrows<IllegalArgumentException> {
            FogOfWarState(cols = 1, rows = 0)
        }
    }

    @Test
    fun `constructor throws for zero cellSizeInUnits`() {
        assertThrows<IllegalArgumentException> {
            FogOfWarState(cols = 1, rows = 1, cellSizeInUnits = 0.0)
        }
    }

    @Test
    fun `cellSizeInUnits defaults to one`() {
        assertEquals(1.0, fow.cellSizeInUnits)
    }
}
