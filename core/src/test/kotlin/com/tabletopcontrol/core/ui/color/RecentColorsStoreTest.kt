package com.tabletopcontrol.core.ui.color

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Tests for [RecentColorsStore]. */
class RecentColorsStoreTest {

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
    fun `load returns empty when config is missing`() {
        assertTrue(RecentColorsStore.load().isEmpty())
    }

    @Test
    fun `remember prepends and deduplicates by hex`() {
        RecentColorsStore.remember(ColorHexCodec.hexToColor("#112233"))
        RecentColorsStore.remember(ColorHexCodec.hexToColor("#445566"))
        RecentColorsStore.remember(ColorHexCodec.hexToColor("#112233"))

        val loaded = RecentColorsStore.load().map(ColorHexCodec::colorToHex)
        assertEquals(listOf("#112233", "#445566"), loaded)
    }

    @Test
    fun `load ignores invalid lines and enforces max size`() {
        val configDir = File(tempDir, ".tabletopcontrol").apply { mkdirs() }
        val configFile = File(configDir, RecentColorsStore.CONFIG_NAME)
        val lines = buildList {
            add("not-a-color")
            repeat(RecentColorsStore.MAX_RECENT_COLORS + 4) { index ->
                add("#%06X".format(index + 1))
            }
        }
        configFile.writeText(lines.joinToString(separator = "\n", postfix = "\n"))

        val loaded = RecentColorsStore.load().map(ColorHexCodec::colorToHex)
        assertEquals(RecentColorsStore.MAX_RECENT_COLORS, loaded.size)
        assertEquals("#000001", loaded.first())
        val expectedLastIndex = RecentColorsStore.MAX_RECENT_COLORS
        assertEquals("#%06X".format(expectedLastIndex), loaded.last())
    }
}

