package com.tabletopcontrol.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ThemeManagerTest {

    @TempDir
    lateinit var tempDir: File

    /** Original value of the `user.home` system property, restored in [tearDown]. */
    private var originalUserHome: String? = null

    /** Redirect ThemeManager's config directory to [tempDir] for test isolation. */
    @BeforeEach
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.absolutePath)
        // Reset ThemeManager state so it reloads from the (empty) temp dir.
        ThemeManager.resetForTesting()
        EventBus.clear()
    }

    @AfterEach
    fun tearDown() {
        EventBus.clear()
        // Restore user.home so this change does not leak into other tests.
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome!!)
        } else {
            System.clearProperty("user.home")
        }
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
    fun `LIGHT_DEFAULTS and DARK_DEFAULTS have distinct accent colours`() {
        assertTrue(ThemeConfig.LIGHT_DEFAULTS.accentColor != ThemeConfig.DARK_DEFAULTS.accentColor)
    }

    // ── Hex color validation ──────────────────────────────────────────────────

    @Test
    fun `isValidHexColor accepts six-digit hex`() {
        assertTrue(ThemeConfig.isValidHexColor("#1565c0"))
    }

    @Test
    fun `isValidHexColor accepts three-digit hex`() {
        assertTrue(ThemeConfig.isValidHexColor("#fff"))
    }

    @Test
    fun `isValidHexColor rejects invalid strings`() {
        assertFalse(ThemeConfig.isValidHexColor("blue"))
        assertFalse(ThemeConfig.isValidHexColor("rgb(0,0,0)"))
        assertFalse(ThemeConfig.isValidHexColor("#gggggg"))
        assertFalse(ThemeConfig.isValidHexColor(""))
    }

    // ── Save / load round-trip ────────────────────────────────────────────────

    @Test
    fun `save and load round-trip preserves LIGHT mode`() {
        val original = ThemeConfig(
            mode = ThemeMode.LIGHT,
            accentColor = "#aabbcc",
            bgColor = "#f4f4f4",
            surfaceColor = "#ffffff",
            borderColor = "#c8c8c8",
        )
        ThemeManager.save(original)
        val loaded = ThemeManager.load()
        assertEquals(original, loaded)
    }

    @Test
    fun `save and load round-trip preserves DARK mode`() {
        val original = ThemeConfig(
            mode = ThemeMode.DARK,
            accentColor = "#82b1ff",
            bgColor = "#1e1e2e",
            surfaceColor = "#2d2d3e",
            borderColor = "#555577",
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
        assertEquals(ThemeConfig.DARK_DEFAULTS.accentColor, loaded.accentColor)
        assertEquals(ThemeConfig.DARK_DEFAULTS.bgColor, loaded.bgColor)
        assertEquals(ThemeConfig.DARK_DEFAULTS.surfaceColor, loaded.surfaceColor)
        assertEquals(ThemeConfig.DARK_DEFAULTS.borderColor, loaded.borderColor)
    }

    @Test
    fun `load falls back to mode default for invalid hex colour`() {
        val dir = File(tempDir, ".tabletopcontrol").also { it.mkdirs() }
        File(dir, "theme.conf").writeText(
            "mode=LIGHT\naccentColor=not-a-color\nbgColor=#f4f4f4\nsurfaceColor=#ffffff\nborderColor=#c8c8c8\n"
        )
        val loaded = ThemeManager.load()
        // Invalid accentColor should fall back to the LIGHT default.
        assertEquals(ThemeConfig.LIGHT_DEFAULTS.accentColor, loaded.accentColor)
        // Valid entries should be preserved.
        assertEquals("#f4f4f4", loaded.bgColor)
    }

    // ── Custom CSS generation ─────────────────────────────────────────────────

    @Test
    fun `writeCustomCss writes a file containing the four colour variables`() {
        val config = ThemeConfig(
            accentColor = "#aabbcc",
            bgColor = "#112233",
            surfaceColor = "#334455",
            borderColor = "#ff0000",
        )
        val url = ThemeManager.writeCustomCss(config)
        assertNotNull(url)
        val cssFile = File(tempDir, ".tabletopcontrol/theme-custom.css")
        assertTrue(cssFile.exists(), "Custom CSS file should exist after writeCustomCss")
        val content = cssFile.readText()
        assertTrue(content.contains("#aabbcc"), "Custom CSS should include accent colour")
        assertTrue(content.contains("#112233"), "Custom CSS should include bg colour")
        assertTrue(content.contains("#334455"), "Custom CSS should include surface colour")
        assertTrue(content.contains("#ff0000"), "Custom CSS should include border colour")
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
        val newTheme = ThemeConfig(mode = ThemeMode.DARK, accentColor = "#123456")
        ThemeManager.setTheme(newTheme)
        assertEquals(newTheme, ThemeManager.currentTheme)
    }
}
