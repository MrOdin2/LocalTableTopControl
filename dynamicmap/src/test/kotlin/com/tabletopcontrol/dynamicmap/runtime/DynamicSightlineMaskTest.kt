package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicSightlineMask
import com.tabletopcontrol.dynamicmap.runtime.logic.Token
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicSightlineMaskTest {

    @Test
    fun `pc token reveals cells with an unblocked sightline`() {
        val mask = DynamicSightlineMask.compute(
            cols = 4,
            rows = 3,
            walls = emptyList(),
            tokens = listOf(pcToken(col = 0, row = 1)),
        )

        assertTrue(mask.isVisible(0, 1))
        assertTrue(mask.isVisible(3, 2))
    }

    @Test
    fun `wall blocks cells behind it`() {
        val wall = DynamicMapRuntimeWall(
            start = DynamicMapRuntimePoint(2.0, 0.0),
            end = DynamicMapRuntimePoint(2.0, 3.0),
        )

        val mask = DynamicSightlineMask.compute(
            cols = 5,
            rows = 3,
            walls = listOf(wall),
            tokens = listOf(pcToken(col = 0, row = 1)),
        )

        assertTrue(mask.isVisible(1, 1))
        assertFalse(mask.isVisible(3, 1))
    }

    @Test
    fun `multiple pc tokens combine their visible cells`() {
        val wall = DynamicMapRuntimeWall(
            start = DynamicMapRuntimePoint(2.0, 0.0),
            end = DynamicMapRuntimePoint(2.0, 3.0),
        )

        val mask = DynamicSightlineMask.compute(
            cols = 5,
            rows = 3,
            walls = listOf(wall),
            tokens = listOf(
                pcToken(col = 0, row = 1),
                pcToken(col = 4, row = 1),
            ),
        )

        assertTrue(mask.isVisible(1, 1))
        assertTrue(mask.isVisible(3, 1))
    }

    @Test
    fun `npc tokens do not reveal sightline cells`() {
        val mask = DynamicSightlineMask.compute(
            cols = 4,
            rows = 3,
            walls = emptyList(),
            tokens = listOf(Token(id = "npc", name = "Guard", col = 0, row = 1, color = Color.RED)),
        )

        assertFalse(mask.isVisible(0, 1))
        assertFalse(mask.isVisible(3, 2))
    }

    private fun pcToken(col: Int, row: Int): Token =
        Token(
            id = "pc-$col-$row",
            name = "Hero",
            col = col,
            row = row,
            color = Color.BLUE,
            isPlayerCharacter = true,
        )
}
