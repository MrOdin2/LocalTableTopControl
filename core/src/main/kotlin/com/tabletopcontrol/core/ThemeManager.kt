package com.tabletopcontrol.core

import javafx.scene.Scene
import java.io.File

/**
 * Central singleton managing the application's visual theme.
 *
 * ### Responsibilities
 * - Loads and saves [ThemeConfig] from/to `~/.tabletopcontrol/theme.conf`.
 * - Maintains a registry of all active [Scene] instances.
 * - Applies the current theme (base CSS + custom colour overrides) to every
 *   registered scene whenever the theme changes.
 * - Publishes a [ThemeChangedEvent] on [EventBus] after each theme change so
 *   that plugins can react (e.g. Canvas-based renderers that cannot use CSS).
 *
 * ### CSS Looked-Up Colour Variables
 *
 * Themes expose the following CSS variables on `.root`.  Any descendant node
 * can reference them in inline `style` strings or CSS class rules:
 *
 * **User-configurable (via the Theme dialog):**
 *
 * | Variable       | Purpose                            |
 * |----------------|------------------------------------|
 * | `-tc-accent`   | Buttons and interactive highlights |
 * | `-tc-bg`       | Window / scene background          |
 * | `-tc-surface`  | Panel and card surfaces            |
 * | `-tc-border`   | Panel edges and control borders    |
 *
 * **Fixed semantic (defined in the base theme CSS):**
 *
 * | Variable         | Light default | Dark default |
 * |------------------|---------------|--------------|
 * | `-tc-text`       | `#212121`     | `#e0e0e0`    |
 * | `-tc-text-muted` | `#888888`     | `#9e9e9e`    |
 * | `-tc-success`    | `#00aa00`     | `#66bb6a`    |
 * | `-tc-error`      | `#cc0000`     | `#ef9a9a`    |
 *
 * **Tracker card colours (derived from the base variables above):**
 *
 * | Variable                    | Derived from            |
 * |-----------------------------|-------------------------|
 * | `-tc-card-bg`               | `-tc-surface`           |
 * | `-tc-card-border`           | `-tc-border`            |
 * | `-tc-card-active-border`    | `-tc-accent`            |
 * | `-tc-card-active-bg`        | accent-tinted surface   |
 * | `-tc-card-dragover-border`  | `-tc-accent`            |
 * | `-tc-card-dragover-bg`      | accent-tinted surface   |
 *
 * ### Usage
 * ```kotlin
 * // Register scenes before calling show() so the first frame uses the saved theme:
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
     * A small CSS file written to disk that overrides the four user-configurable
     * colour variables.  Using a file URL (rather than a `data:` URI) ensures
     * compatibility across all JavaFX versions.
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
                appendLine("accentColor=${theme.accentColor}")
                appendLine("bgColor=${theme.bgColor}")
                appendLine("surfaceColor=${theme.surfaceColor}")
                appendLine("borderColor=${theme.borderColor}")
            }
            configFile.writeText(text)
        } catch (_: Exception) {
            // non-fatal — proceed without persistence
        }
    }

    /**
     * Loads the saved [ThemeConfig] from disk.
     *
     * Any colour value that is missing or is not a valid `#RGB` / `#RRGGBB` hex
     * string is replaced with the mode-specific default so that a user-edited or
     * corrupted config file never crashes the application.
     *
     * @return The saved layout tree, or a default [ThemeConfig] if the file is
     *         absent, unreadable, or fully invalid.
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

            fun validColor(key: String, fallback: String): String {
                val v = props[key] ?: return fallback
                return if (ThemeConfig.isValidHexColor(v)) v else fallback
            }

            ThemeConfig(
                mode = mode,
                accentColor = validColor("accentColor", defaults.accentColor),
                bgColor = validColor("bgColor", defaults.bgColor),
                surfaceColor = validColor("surfaceColor", defaults.surfaceColor),
                borderColor = validColor("borderColor", defaults.borderColor),
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
     * 2. A small per-user override file that sets the four user-configurable
     *    colour variables.
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
     * Writes a minimal CSS snippet that overrides the four user-configurable
     * colour variables with the user's choices, and returns the file URL.
     *
     * Returns `null` on I/O failure; the base theme defaults are used instead.
     */
    internal fun writeCustomCss(theme: ThemeConfig): String? {
        val css = """
            .root {
                -tc-accent:  ${theme.accentColor};
                -tc-bg:      ${theme.bgColor};
                -tc-surface: ${theme.surfaceColor};
                -tc-border:  ${theme.borderColor};
            }
        """.trimIndent()
        return try {
            customCssFile.writeText(css)
            customCssFile.toURI().toURL().toExternalForm()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resets internal state by reloading the theme from disk and clearing the
     * registered scene list.
     *
     * **For use in unit tests only.** Call this in `@BeforeEach` after redirecting
     * `user.home` to a temp directory so each test starts from a clean state.
     */
    internal fun resetForTesting() {
        currentTheme = load()
        scenes.clear()
    }

    private fun applyToScene(scene: Scene, theme: ThemeConfig) {
        scene.stylesheets.setAll(buildCssUrls(theme))
    }
}
