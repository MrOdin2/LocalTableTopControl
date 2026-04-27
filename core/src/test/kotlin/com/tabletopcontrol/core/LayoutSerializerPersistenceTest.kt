package com.tabletopcontrol.core

import javafx.geometry.Orientation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LayoutSerializerPersistenceTest {
    @TempDir
    lateinit var tempDir: File

    private var originalUserHome: String? = null

    @BeforeEach
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.absolutePath)
    }

    @AfterEach
    fun tearDown() {
        originalUserHome?.let { System.setProperty("user.home", it) } ?: System.clearProperty("user.home")
    }

    @Test
    fun `save and load use the provided custom config name`() {
        val node = PaneNode.Split(
            orientation = Orientation.HORIZONTAL,
            dividerPosition = 0.5,
            first = PaneNode.Leaf("Dynamic Map Builder"),
            second = PaneNode.Leaf("Light Browser"),
        )

        LayoutSerializer.save(node, configName = "custom-layout.conf")

        assertEquals(node, LayoutSerializer.load("custom-layout.conf"))
        assertNull(LayoutSerializer.load("other-layout.conf"))
    }
}
