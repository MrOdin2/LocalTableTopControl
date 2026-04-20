package com.tabletopcontrol.map

import com.tabletopcontrol.map.logic.TableMapOffset
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapRendererHelpersTest {

    @Test
    fun `table viewport scene bounds are centered and invert shared table offset`() {
        val bounds = tableViewportSceneBounds(
            viewportWidth = 1920.0,
            viewportHeight = 1080.0,
            tableMapOffset = TableMapOffset(offsetX = 120.0, offsetY = -80.0),
        )

        assertNotNull(bounds)
        assertEquals(-1080.0, bounds!![0], 1e-9)
        assertEquals(840.0, bounds[1], 1e-9)
        assertEquals(-460.0, bounds[2], 1e-9)
        assertEquals(620.0, bounds[3], 1e-9)
    }

    @Test
    fun `table viewport scene bounds reject missing sizes`() {
        assertNull(
            tableViewportSceneBounds(
                viewportWidth = 0.0,
                viewportHeight = 1080.0,
                tableMapOffset = TableMapOffset(),
            ),
        )
    }

    @Test
    fun `table viewport outline color shifts hue and stays faint`() {
        val base = Color.color(0.2, 0.6, 0.3, 0.8)

        val outline = tableViewportOutlineColor(base)

        assertNotEquals(base.hue, outline.hue)
        assertTrue(outline.opacity < base.opacity)
        assertTrue(outline.opacity in 0.22..0.5)
    }
}
