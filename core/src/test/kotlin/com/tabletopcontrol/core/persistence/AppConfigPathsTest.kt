package com.tabletopcontrol.core.persistence

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AppConfigPathsTest {

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
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome!!)
        } else {
            System.clearProperty("user.home")
        }
    }

    @Test
    fun `configFile resolves under tabletopcontrol directory and creates it`() {
        val file = AppConfigPaths.configFile("theme.conf")
        assertEquals(File(tempDir, ".tabletopcontrol/theme.conf").absolutePath, file.absolutePath)
        assertTrue(File(tempDir, ".tabletopcontrol").isDirectory)
    }

    @Test
    fun `configSubDir creates and returns requested subdirectory`() {
        val dir = AppConfigPaths.configSubDir("presets")
        assertEquals(File(tempDir, ".tabletopcontrol/presets").absolutePath, dir.absolutePath)
        assertTrue(dir.isDirectory)
    }
}
