package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicSightlineMesh
import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicSightlineGeometry
import com.tabletopcontrol.dynamicmap.runtime.logic.Token
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `token is visible when any part of its circle intersects the sight mesh`() {
        val wall = DynamicMapRuntimeWall(
            start = DynamicMapRuntimePoint(2.4, 0.0),
            end = DynamicMapRuntimePoint(2.4, 3.0),
        )
        val tokenMostlyBehindWall = Token(
            id = "npc",
            name = "Guard",
            col = 2,
            row = 1,
            color = Color.RED,
        )

        val mesh = DynamicSightlineMesh.compute(
            cols = 5,
            rows = 3,
            walls = listOf(wall),
            tokens = listOf(pcToken(col = 0, row = 1)),
        )

        assertFalse(mesh.containsPoint(2.5, 1.5))
        assertTrue(mesh.intersectsToken(tokenMostlyBehindWall))
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
    fun `combined per pc sightline contributions match full mesh computation`() {
        val wall = DynamicMapRuntimeWall(
            start = DynamicMapRuntimePoint(2.0, 0.0),
            end = DynamicMapRuntimePoint(2.0, 3.0),
        )
        val pcs = listOf(
            pcToken(col = 0, row = 1),
            pcToken(col = 4, row = 1),
        )
        val geometry = DynamicSightlineGeometry.forMap(
            cols = 5,
            rows = 3,
            walls = listOf(wall),
        )

        val combined = geometry.combine(pcs.mapNotNull(geometry::computeContribution))
        val full = DynamicSightlineMesh.compute(
            cols = 5,
            rows = 3,
            walls = listOf(wall),
            tokens = pcs,
        )

        assertEquals(full.triangles.size, combined.triangles.size)
        assertEquals(full.containsPoint(1.5, 1.5), combined.containsPoint(1.5, 1.5))
        assertEquals(full.containsPoint(3.5, 1.5), combined.containsPoint(3.5, 1.5))
        assertEquals(full.containsPoint(2.5, 1.5), combined.containsPoint(2.5, 1.5))
    }

    @Test
    fun `mesh from accumulated visible area remembers previous pc sightlines`() {
        val wall = DynamicMapRuntimeWall(
            start = DynamicMapRuntimePoint(2.0, 0.0),
            end = DynamicMapRuntimePoint(2.0, 3.0),
        )
        val leftView = DynamicSightlineMesh.compute(
            cols = 5,
            rows = 3,
            walls = listOf(wall),
            tokens = listOf(pcToken(col = 0, row = 1)),
        )
        val rightView = DynamicSightlineMesh.compute(
            cols = 5,
            rows = 3,
            walls = listOf(wall),
            tokens = listOf(pcToken(col = 4, row = 1)),
        )
        val accumulatedVisible = leftView.copyVisibleArea().apply {
            add(rightView.copyVisibleArea())
        }

        val remembered = DynamicSightlineMesh.fromVisibleArea(
            cols = 5,
            rows = 3,
            visibleArea = accumulatedVisible,
        )

        assertTrue(remembered.copyVisibleArea().contains(1.5, 1.5))
        assertTrue(remembered.copyVisibleArea().contains(3.5, 1.5))
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
