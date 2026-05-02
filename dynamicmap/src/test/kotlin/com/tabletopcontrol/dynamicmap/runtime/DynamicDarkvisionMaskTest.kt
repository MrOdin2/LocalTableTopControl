package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicPcSightlineContribution
import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicSightlineGeometry
import com.tabletopcontrol.dynamicmap.runtime.logic.Token
import com.tabletopcontrol.dynamicmap.runtime.logic.darkvisionMeshFrom
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicDarkvisionMaskTest {
    @Test
    fun `darkvision reveals only within token radius`() {
        val geometry = DynamicSightlineGeometry.forMap(cols = 6, rows = 5, walls = emptyList())
        val pc = pcToken(col = 0, row = 1, darkvisionRangeCells = 2.0)

        val mesh = geometry.darkvisionMeshFrom(listOf(pcContribution(geometry, pc)))

        assertTrue(mesh.containsPoint(1.5, 1.5))
        assertFalse(mesh.containsPoint(4.0, 1.5))
    }

    @Test
    fun `walls block darkvision`() {
        val wall = DynamicMapRuntimeWall(
            start = DynamicMapRuntimePoint(2.0, 0.0),
            end = DynamicMapRuntimePoint(2.0, 5.0),
        )
        val geometry = DynamicSightlineGeometry.forMap(cols = 6, rows = 5, walls = listOf(wall))
        val pc = pcToken(col = 0, row = 1, darkvisionRangeCells = 5.0)

        val mesh = geometry.darkvisionMeshFrom(listOf(pcContribution(geometry, pc)))

        assertTrue(mesh.containsPoint(1.5, 1.5))
        assertFalse(mesh.containsPoint(3.0, 1.5))
    }

    @Test
    fun `tokens without darkvision do not contribute darkvision`() {
        val geometry = DynamicSightlineGeometry.forMap(cols = 6, rows = 5, walls = emptyList())
        val pc = pcToken(col = 0, row = 1, darkvisionRangeCells = null)

        val mesh = geometry.darkvisionMeshFrom(listOf(pcContribution(geometry, pc)))

        assertFalse(mesh.hasVisibleArea())
    }

    private fun pcContribution(
        geometry: DynamicSightlineGeometry,
        token: Token,
    ): DynamicPcSightlineContribution =
        DynamicPcSightlineContribution(
            token = token,
            contribution = geometry.computeContribution(token)!!,
        )

    private fun pcToken(
        col: Int,
        row: Int,
        darkvisionRangeCells: Double?,
    ): Token =
        Token(
            id = "pc-$col-$row",
            name = "Hero",
            col = col,
            row = row,
            color = Color.BLUE,
            isPlayerCharacter = true,
            darkvisionRangeCells = darkvisionRangeCells,
        )
}
