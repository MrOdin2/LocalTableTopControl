package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicSightlineMesh
import com.tabletopcontrol.dynamicmap.runtime.logic.Token
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicSightlineMeshTest {

    @Test
    fun `pc token creates triangle mesh for unblocked sight`() {
        val mesh = DynamicSightlineMesh.compute(
            cols = 4,
            rows = 3,
            walls = emptyList(),
            tokens = listOf(pcToken(col = 0, row = 1)),
        )

        assertTrue(mesh.triangles.isNotEmpty())
        assertTrue(mesh.containsPoint(0.5, 1.5))
        assertTrue(mesh.containsPoint(3.5, 2.5))
    }

    @Test
    fun `wall blocks points behind it`() {
        val wall = DynamicMapRuntimeWall(
            start = DynamicMapRuntimePoint(2.0, 0.0),
            end = DynamicMapRuntimePoint(2.0, 3.0),
        )

        val mesh = DynamicSightlineMesh.compute(
            cols = 5,
            rows = 3,
            walls = listOf(wall),
            tokens = listOf(pcToken(col = 0, row = 1)),
        )

        assertTrue(mesh.containsPoint(1.5, 1.5))
        assertFalse(mesh.containsPoint(3.5, 1.5))
    }

    @Test
    fun `multiple pc tokens combine their visible meshes`() {
        val wall = DynamicMapRuntimeWall(
            start = DynamicMapRuntimePoint(2.0, 0.0),
            end = DynamicMapRuntimePoint(2.0, 3.0),
        )

        val mesh = DynamicSightlineMesh.compute(
            cols = 5,
            rows = 3,
            walls = listOf(wall),
            tokens = listOf(
                pcToken(col = 0, row = 1),
                pcToken(col = 4, row = 1),
            ),
        )

        assertTrue(mesh.containsPoint(1.5, 1.5))
        assertTrue(mesh.containsPoint(3.5, 1.5))
    }

    @Test
    fun `npc tokens do not reveal sightline triangles`() {
        val mesh = DynamicSightlineMesh.compute(
            cols = 4,
            rows = 3,
            walls = emptyList(),
            tokens = listOf(Token(id = "npc", name = "Guard", col = 0, row = 1, color = Color.RED)),
        )

        assertTrue(mesh.triangles.isEmpty())
        assertFalse(mesh.containsPoint(0.5, 1.5))
        assertFalse(mesh.containsPoint(3.5, 2.5))
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
