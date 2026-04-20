package com.tabletopcontrol.map

import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.map.logic.Token
import com.tabletopcontrol.map.logic.draggedTokenOrigin
import com.tabletopcontrol.map.logic.nextAvailableTokenPlacement
import com.tabletopcontrol.map.logic.tokenDragAnchor
import com.tabletopcontrol.map.logic.tokenDrawBounds
import com.tabletopcontrol.map.logic.tokenFootprintsOverlap
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenFootprintsTest {

    @Test
    fun `small token draw bounds are centered inside the anchor cell`() {
        val token = Token(id = "1", name = "Sprite", col = 2, row = 3, size = TokenSize.SMALL, color = Color.GREEN)

        val bounds = tokenDrawBounds(token, originX = 10.0, originY = 20.0, cellPx = 40.0)

        assertEquals(100.0, bounds.left, 1e-9)
        assertEquals(150.0, bounds.top, 1e-9)
        assertEquals(20.0, bounds.size, 1e-9)
    }

    @Test
    fun `tiny token draw bounds stay centered with eighth-tile footprint`() {
        val token = Token(id = "1", name = "Pixie", col = 2, row = 3, size = TokenSize.TINY, color = Color.PINK)

        val bounds = tokenDrawBounds(token, originX = 10.0, originY = 20.0, cellPx = 40.0)

        assertEquals(102.92893218813452, bounds.left, 1e-9)
        assertEquals(152.92893218813452, bounds.top, 1e-9)
        assertEquals(14.142135623730951, bounds.size, 1e-9)
    }

    @Test
    fun `next placement skips the full footprint of larger tokens`() {
        val existing = listOf(
            Token(id = "1", name = "Dragon", col = 0, row = 0, size = TokenSize.HUGE, color = Color.RED),
            Token(id = "2", name = "Knight", col = 4, row = 0, color = Color.BLUE),
        )

        val placement = nextAvailableTokenPlacement(existing, TokenSize.LARGE)

        assertEquals(Pair(5, 0), placement)
    }

    @Test
    fun `footprint overlap uses token span not just anchor cell`() {
        val dragon = Token(id = "1", name = "Dragon", col = 2, row = 2, size = TokenSize.HUGE, color = Color.RED)
        val rider = Token(id = "2", name = "Rider", col = 4, row = 4, color = Color.BLUE)
        val scout = Token(id = "3", name = "Scout", col = 5, row = 5, color = Color.GREEN)

        assertTrue(tokenFootprintsOverlap(dragon, rider))
        assertFalse(tokenFootprintsOverlap(dragon, scout))
    }

    @Test
    fun `drag anchor preserves the grabbed cell within a large token`() {
        val token = Token(id = "1", name = "Ogre", col = 4, row = 6, size = TokenSize.LARGE, color = Color.BROWN)

        val anchor = tokenDragAnchor(token, grabbedCell = Pair(5, 7))
        val movedOrigin = draggedTokenOrigin(Pair(9, 10), anchor)

        assertEquals(Pair(1, 1), anchor)
        assertEquals(Pair(8, 9), movedOrigin)
    }
}
