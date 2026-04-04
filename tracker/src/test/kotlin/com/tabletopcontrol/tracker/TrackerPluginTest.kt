package com.tabletopcontrol.tracker

import javafx.geometry.Orientation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrackerPluginTest {
    private val plugin = TrackerPlugin()

    @Test
    fun `preferred orientation stays vertical when width is less than twice the height`() {
        assertEquals(Orientation.VERTICAL, plugin.preferredOrientationForBounds(width = 399.0, height = 200.0))
    }

    @Test
    fun `preferred orientation stays vertical when width is exactly twice the height`() {
        assertEquals(Orientation.VERTICAL, plugin.preferredOrientationForBounds(width = 400.0, height = 200.0))
    }

    @Test
    fun `preferred orientation switches to horizontal only when width is more than twice the height`() {
        assertEquals(Orientation.HORIZONTAL, plugin.preferredOrientationForBounds(width = 401.0, height = 200.0))
    }

    @Test
    fun `preferred orientation stays vertical for non-positive heights`() {
        assertEquals(Orientation.VERTICAL, plugin.preferredOrientationForBounds(width = 500.0, height = 0.0))
    }
}
