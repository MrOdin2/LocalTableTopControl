package com.tabletopcontrol.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.lang.reflect.Field

class ThemeManagerTest {

    @TempDir
    lateinit var tempDir: File

    /** Redirect ThemeManager's config directory to [tempDir] for test isolation. */
    @BeforeEach
    fun setUp() {
        System.setProperty("user.home", tempDir.absolutePath)
        // Reset ThemeManager state by reloading from the (empty) temp dir.
        resetThemeManager()
        EventBus.clear()
    }

    @AfterEach
    fun tearDown() {
        EventBus.clear()
    }

    // ── ThemeConfig tests ─────────────────────────────────────────────────────

    @Test
    fun `ThemeConfig default mode is LIGHT`() {
        assertEquals(ThemeMode.LIGHT, ThemeConfig().mode)
    }

    @Test
    fun `LIGHT_DEFAULTS has LIGHT mode`() {
        assertEquals(ThemeMode.LIGHT, ThemeConfig.LIGHT_DEFAULTS.mode)
    }

    @Test
    fun `DARK_DEFAULTS has DARK mode`() {
        assertEquals(ThemeMode.DARK, ThemeConfig.DARK_DEFAULTS.mode)
    }

    @Test
    fun `LIGHT_DEFAULTS and DARK_DEFAULTS have distinct primary colours`() {
        assertTrue(ThemeConfig.LIGHT_DEFAULTS.primaryColor != ThemeConfig.DARK_DEFAULTS.primaryColor)
    }

    // ── Save / load round-trip ────────────────────────────────────────────────

    @Test
    fun `save and load round-trip preserves LIGHT mode`() {
        val original = ThemeConfig(
            mode = ThemeMode.LIGHT,
            primaryColor = "#aabbcc",
            secondaryColor = "#112233",
            tertiaryColor = "#ff0000",
        )
        ThemeManager.save(original)
        val loaded = ThemeManager.load()
        assertEquals(original, loaded)
    }

    @Test
    fun `save and load round-trip preserves DARK mode`() {
        val original = ThemeConfig(
            mode = ThemeMode.DARK,
            primaryColor = "#82b1ff",
            secondaryColor = "#69f0ae",
            tertiaryColor = "#ffd740",
        )
        ThemeManager.save(original)
        val loaded = ThemeManager.load()
        assertEquals(original, loaded)
    }

    @Test
    fun `load returns default ThemeConfig when no file exists`() {
        val loaded = ThemeManager.load()
        assertEquals(ThemeConfig(), loaded)
    }

    @Test
    fun `load returns LIGHT defaults when file has unknown mode value`() {
        val dir = File(tempDir, ".tabletopcontrol").also { it.mkdirs() }
        File(dir, "theme.conf").writeText("mode=NONSENSE\n")
        val loaded = ThemeManager.load()
        assertEquals(ThemeMode.LIGHT, loaded.mode)
    }

    @Test
    fun `load uses theme-specific defaults for missing colour keys`() {
        val dir = File(tempDir, ".tabletopcontrol").also { it.mkdirs() }
        File(dir, "theme.conf").writeText("mode=DARK\n")
        val loaded = ThemeManager.load()
        assertEquals(ThemeMode.DARK, loaded.mode)
        assertEquals(ThemeConfig.DARK_DEFAULTS.primaryColor, loaded.primaryColor)
        assertEquals(ThemeConfig.DARK_DEFAULTS.secondaryColor, loaded.secondaryColor)
        assertEquals(ThemeConfig.DARK_DEFAULTS.tertiaryColor, loaded.tertiaryColor)
    }

    // ── Custom CSS generation ─────────────────────────────────────────────────

    @Test
    fun `writeCustomCss writes a file containing the three accent colour variables`() {
        val config = ThemeConfig(
            primaryColor = "#aabbcc",
            secondaryColor = "#112233",
            tertiaryColor = "#ff0000",
        )
        val url = ThemeManager.writeCustomCss(config)
        assertNotNull(url)
        val cssFile = File(tempDir, ".tabletopcontrol/theme-custom.css")
        assertTrue(cssFile.exists(), "Custom CSS file should exist after writeCustomCss")
        val content = cssFile.readText()
        assertTrue(content.contains("#aabbcc"), "Custom CSS should include primary colour")
        assertTrue(content.contains("#112233"), "Custom CSS should include secondary colour")
        assertTrue(content.contains("#ff0000"), "Custom CSS should include tertiary colour")
    }

    @Test
    fun `buildCssUrls returns two entries — base theme and custom override`() {
        val config = ThemeConfig(mode = ThemeMode.LIGHT)
        val urls = ThemeManager.buildCssUrls(config)
        assertEquals(2, urls.size, "Expected base CSS URL and custom override URL")
    }

    @Test
    fun `buildCssUrls base entry references theme-light for LIGHT mode`() {
        val urls = ThemeManager.buildCssUrls(ThemeConfig(mode = ThemeMode.LIGHT))
        assertTrue(urls[0].contains("theme-light"), "First URL should reference theme-light.css")
    }

    @Test
    fun `buildCssUrls base entry references theme-dark for DARK mode`() {
        val urls = ThemeManager.buildCssUrls(ThemeConfig(mode = ThemeMode.DARK))
        assertTrue(urls[0].contains("theme-dark"), "First URL should reference theme-dark.css")
    }

    // ── Event publishing ──────────────────────────────────────────────────────

    @Test
    fun `setTheme publishes ThemeChangedEvent on EventBus`() {
        var received: ThemeChangedEvent? = null
        EventBus.subscribe<ThemeChangedEvent> { received = it }

        val newTheme = ThemeConfig(mode = ThemeMode.DARK)
        ThemeManager.setTheme(newTheme)

        assertNotNull(received, "ThemeChangedEvent should have been published")
        assertEquals(newTheme, received?.theme)
    }

    @Test
    fun `setTheme updates currentTheme`() {
        val newTheme = ThemeConfig(mode = ThemeMode.DARK, primaryColor = "#123456")
        ThemeManager.setTheme(newTheme)
        assertEquals(newTheme, ThemeManager.currentTheme)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resets the `currentTheme` field on the [ThemeManager] singleton so each
     * test starts from a clean state without re-loading from disk prematurely.
     */
    private fun resetThemeManager() {
        // Re-invoke load() to pick up the (empty) temp directory.
        val field: Field = ThemeManager::class.java.getDeclaredField("currentTheme")
        field.isAccessible = true
        field.set(ThemeManager, ThemeManager.load())
    }
}
