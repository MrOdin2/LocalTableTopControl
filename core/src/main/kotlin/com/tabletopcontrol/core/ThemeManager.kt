package com.tabletopcontrol.core

import javafx.scene.Scene
import java.io.File

/**
 * Central singleton managing the application's visual theme.
 *
 * ### Responsibilities
 * - Loads and saves [ThemeConfig] from/to `~/.tabletopcontrol/theme.conf`.
 * - Maintains a registry of all active [Scene] instances.
 * - Applies the current theme (base CSS + custom accent overrides) to every
 *   registered scene whenever the theme changes.
 * - Publishes a [ThemeChangedEvent] on [EventBus] after each theme change so
 *   that plugins can react (e.g. Canvas-based renderers that cannot use CSS).
 *
 * ### CSS Looked-Up Colour Variables
 *
 * Themes expose the following CSS variables on `.root`.  Any descendant node
 * can reference them in inline `style` strings or CSS class rules:
 *
 * | Variable                    | Light default | Dark default |
 * |-----------------------------|---------------|--------------|
 * | `-tc-primary`               | `#1565c0`     | `#82b1ff`    |
 * | `-tc-secondary`             | `#2e7d32`     | `#69f0ae`    |
 * | `-tc-tertiary`              | `#e65100`     | `#ffd740`    |
 * | `-tc-success`               | `#00aa00`     | `#66bb6a`    |
 * | `-tc-error`                 | `#cc0000`     | `#ef9a9a`    |
 * | `-tc-text-muted`            | `#888888`     | `#9e9e9e`    |
 * | `-tc-card-bg`               | `#f5f5f5`     | `#2d2d3e`    |
 * | `-tc-card-border`           | `#888888`     | `#555577`    |
 * | `-tc-card-active-bg`        | `#fff3e0`     | `#2a2010`    |
 * | `-tc-card-active-border`    | `#e67e00`     | `#ffb300`    |
 * | `-tc-card-dragover-bg`      | `#e8f0ff`     | `#0d1a33`    |
 * | `-tc-card-dragover-border`  | `#4488ff`     | `#64b5f6`    |
 *
 * ### Usage
 * ```kotlin
 * // Register scenes during App.start() so the theme is applied immediately:
 * ThemeManager.registerScene(tableScene)
 * ThemeManager.registerScene(dmScene)
 *
 * // Change the theme at runtime (e.g. from a settings dialog):
 * ThemeManager.setTheme(ThemeConfig(mode = ThemeMode.DARK))
 * ```
 */
object ThemeManager {

    private val configFile: File
        get() {
            val dir = File(System.getProperty("user.home"), ".tabletopcontrol")
            dir.mkdirs()
            return File(dir, "theme.conf")
        }

    /**
     * A small CSS file written to disk that overrides the three accent colour
     * variables with the user's current choices.  Using a file URL (rather than
     * a `data:` URI) ensures compatibility across all JavaFX versions.
     */
    private val customCssFile: File
        get() {
            val dir = File(System.getProperty("user.home"), ".tabletopcontrol")
            dir.mkdirs()
            return File(dir, "theme-custom.css")
        }

    /** Currently active theme configuration. */
    var currentTheme: ThemeConfig = load()
        private set

    /** All scenes registered via [registerScene]. */
    private val scenes: MutableList<Scene> = mutableListOf()

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Registers [scene] and immediately applies the current theme to it.
     *
     * The scene will also receive future theme updates when [setTheme] is called.
     * This method must be called on the JavaFX Application Thread.
     */
    fun registerScene(scene: Scene) {
        scenes.add(scene)
        applyToScene(scene, currentTheme)
    }

    /**
     * Changes the active theme to [theme], persists it to disk, applies it to
     * all registered scenes, and publishes a [ThemeChangedEvent] on [EventBus].
     *
     * Must be called on the JavaFX Application Thread.
     */
    fun setTheme(theme: ThemeConfig) {
        currentTheme = theme
        save(theme)
        val urls = buildCssUrls(theme)
        scenes.forEach { it.stylesheets.setAll(urls) }
        EventBus.publish(ThemeChangedEvent(theme))
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    /**
     * Serialises [theme] to the config file as simple `key=value` lines.
     *
     * I/O failures are silently swallowed so that a missing or read-only config
     * directory never crashes the application.
     */
    fun save(theme: ThemeConfig) {
        try {
            val text = buildString {
                appendLine("mode=${theme.mode.name}")
                appendLine("primaryColor=${theme.primaryColor}")
                appendLine("secondaryColor=${theme.secondaryColor}")
                appendLine("tertiaryColor=${theme.tertiaryColor}")
            }
            configFile.writeText(text)
        } catch (_: Exception) {
            // non-fatal — proceed without persistence
        }
    }

    /**
     * Loads the saved [ThemeConfig] from disk.
     *
     * Returns a default [ThemeConfig] if the file is absent or contains
     * unrecognised values.
     */
    fun load(): ThemeConfig {
        return try {
            val props = configFile.readLines()
                .filter { it.contains('=') }
                .associate { line ->
                    val idx = line.indexOf('=')
                    line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                }
            val mode = runCatching { ThemeMode.valueOf(props["mode"] ?: "LIGHT") }
                .getOrDefault(ThemeMode.LIGHT)
            val defaults = if (mode == ThemeMode.DARK) ThemeConfig.DARK_DEFAULTS else ThemeConfig.LIGHT_DEFAULTS
            ThemeConfig(
                mode = mode,
                primaryColor = props["primaryColor"] ?: defaults.primaryColor,
                secondaryColor = props["secondaryColor"] ?: defaults.secondaryColor,
                tertiaryColor = props["tertiaryColor"] ?: defaults.tertiaryColor,
            )
        } catch (_: Exception) {
            ThemeConfig()
        }
    }

    // ── CSS helpers ──────────────────────────────────────────────────────────

    /**
     * Builds the ordered list of CSS stylesheet URLs to apply to a scene for
     * the given [theme]:
     *
     * 1. The base theme stylesheet (`theme-light.css` or `theme-dark.css`).
     * 2. A small per-user override file that sets the three accent colour variables.
     */
    internal fun buildCssUrls(theme: ThemeConfig): List<String> {
        val urls = mutableListOf<String>()

        val basePath = if (theme.mode == ThemeMode.DARK) {
            "/com/tabletopcontrol/core/theme-dark.css"
        } else {
            "/com/tabletopcontrol/core/theme-light.css"
        }
        ThemeManager::class.java.getResource(basePath)?.toExternalForm()?.let { urls.add(it) }

        writeCustomCss(theme)?.let { urls.add(it) }

        return urls
    }

    /**
     * Writes a minimal CSS snippet that overrides the three accent colour variables
     * with the user's choices, and returns the file URL.
     *
     * Returns `null` on I/O failure; the base theme defaults are used instead.
     */
    internal fun writeCustomCss(theme: ThemeConfig): String? {
        val css = """
            .root {
                -tc-primary:   ${theme.primaryColor};
                -tc-secondary: ${theme.secondaryColor};
                -tc-tertiary:  ${theme.tertiaryColor};
            }
        """.trimIndent()
        return try {
            customCssFile.writeText(css)
            customCssFile.toURI().toURL().toExternalForm()
        } catch (_: Exception) {
            null
        }
    }

    private fun applyToScene(scene: Scene, theme: ThemeConfig) {
        scene.stylesheets.setAll(buildCssUrls(theme))
    }
}
