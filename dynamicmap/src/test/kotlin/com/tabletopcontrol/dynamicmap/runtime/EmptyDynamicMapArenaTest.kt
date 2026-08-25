package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicLightMask
import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicSightlineGeometry
import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicSightlineMesh
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmptyDynamicMapArenaTest {

    @Test
    fun `empty arena is wall-free and lit across its full bounds`() {
        val bundle = EmptyDynamicMapArena.bundle
        val geometry = DynamicSightlineGeometry.forMap(bundle.cols, bundle.rows, bundle.walls)
        val lightMask = DynamicLightMask.fromBundle(bundle, geometry)

        assertTrue(bundle.isFallbackArena)
        assertTrue(bundle.walls.isEmpty())
        assertTrue(bundle.lights.isEmpty())
        assertEquals(1, bundle.sunlightAreas.size)
        assertTrue(lightMask.lightingActive)
        assertTrue(lightMask.containsPoint(0.5, 0.5))
        assertTrue(lightMask.containsPoint(bundle.cols - 0.5, bundle.rows - 0.5))
    }

    @Test
    fun `fully visible mesh leaves no hidden arena area`() {
        val mesh = DynamicSightlineMesh.fullyVisible(EmptyDynamicMapArena.COLS, EmptyDynamicMapArena.ROWS)

        assertTrue(mesh.containsPoint(0.5, 0.5))
        assertTrue(mesh.containsPoint(EmptyDynamicMapArena.COLS - 0.5, EmptyDynamicMapArena.ROWS - 0.5))
    }
}
