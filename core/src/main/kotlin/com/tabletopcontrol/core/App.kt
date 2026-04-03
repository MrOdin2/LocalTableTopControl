package com.tabletopcontrol.core

import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.ColorPicker
import javafx.scene.control.ComboBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.RadioButton
import javafx.scene.control.Separator
import javafx.scene.control.ToggleGroup
import javafx.scene.control.ToolBar
import javafx.scene.control.Tooltip
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.util.StringConverter
import kotlin.math.roundToInt

/**
 * Application entry point and top-level JavaFX lifecycle manager.
 *
 * Responsibilities:
 * 1. Discovering and loading plugins via [PluginLoader].
 * 2. Initialising the two-screen layout — a full-screen **table view** driven by
 *    plugin-provided content and a **DM control panel** on a separate [Stage].
 * 3. Managing the DM panel's recursive split-pane layout via [DmLayoutManager].
 * 4. Gracefully shutting down all plugins and persisting the layout when the
 *    application exits.
 */
class App : Application() {

    /** The DM control panel window. */
    private lateinit var dmStage: Stage

    /** All loaded plugins; cached here so [stop] can shut them down cleanly. */
    private lateinit var plugins: List<DmPlugin>

    /** Manages the recursive split-pane layout of the DM panel. */
    private var dmLayoutManager: DmLayoutManager? = null

    override fun start(primaryStage: Stage) {
        // Set primaryStage style FIRST, before any other operations.
        // In JavaFX, initStyle() must be called before the stage is shown or scene is set.
        primaryStage.initStyle(StageStyle.UNDECORATED)

        // Discover plugins first so both scenes can reference them.
        plugins = PluginLoader.loadPlugins()
        plugins.forEach { plugin -> println("Loaded plugin: ${plugin.displayName}") }

        // Build both scenes and register them with the ThemeManager BEFORE
        // calling show() so the very first frame is already styled — avoids a
        // visible flash of the default Modena theme on slower devices.
        val tableScene = buildTableScene(plugins)
        val dmScene = buildDmScene(plugins, primaryStage)

        ThemeManager.registerScene(tableScene)
        ThemeManager.registerScene(dmScene)

        // Table screen — displayed on the external monitor / projector
        primaryStage.apply {
            title = "TabletopControl — Table View"
            scene = tableScene
            isFullScreen = true
            setOnCloseRequest { dmStage.close() }
            show()
        }

        // DM control panel — displayed on the DM's own monitor
        dmStage = Stage().apply {
            title = "TabletopControl — DM Panel"
            scene = dmScene
            setOnCloseRequest { primaryStage.close() }
            show()
        }
    }

    override fun stop() {
        // Persist the current split-pane layout before shutting down.
        dmLayoutManager?.saveLayout()
        // Give every plugin the chance to release its resources.
        plugins.forEach { it.onShutdown() }
    }

    /**
     * Builds the table-screen [Scene].
     *
     * If any loaded plugin provides a table view via [DmPlugin.createTableView], that
     * node is placed in the centre of a [BorderPane] so it fills the entire screen.
     * When no plugin contributes a table view, a placeholder label is shown instead.
     */
    private fun buildTableScene(plugins: List<DmPlugin>): Scene {
        val tableViews = plugins.mapNotNull { it.createTableView() }
        val root = BorderPane()
        root.center = if (tableViews.isEmpty()) {
            Label("Table View — no plugins providing content")
        } else {
            if (tableViews.size > 1) {
                println("WARNING: ${tableViews.size} plugins provide a table view; only the first will be displayed.")
            }
            tableViews.first()
        }
        return Scene(root, 1280.0, 720.0)
    }

    /**
     * Builds the DM panel [Scene].
     *
     * The panel uses a recursive split-pane layout managed by [DmLayoutManager].
     * On the very first run the layout contains a single pane showing the first
     * loaded plugin; subsequent runs restore the previously saved layout from disk.
     *
     * A toolbar at the top allows the DM to move the table view to any connected screen.
     */
    private fun buildDmScene(plugins: List<DmPlugin>, tableStage: Stage): Scene {
        val layoutManager = DmLayoutManager(plugins).also { dmLayoutManager = it }

        val root = BorderPane()
        root.top = buildDisplayToolbar(tableStage)
        root.center = layoutManager.container

        return Scene(root, 1280.0, 720.0)
    }



    /**
     * Builds a slim toolbar at the top of the DM panel that lets the DM choose
     * which screen the Table View is shown on and move it there.
     *
     * The first entry in the combo box is **None (hidden)** — selecting it hides
     * the table stage entirely so no map is displayed for players. Selecting any
     * real screen while the stage is hidden makes the stage visible again.
     *
     * Moving the Table View to another screen temporarily exits fullscreen,
     * repositions the window to the target screen's origin, then re-enters
     * fullscreen so it fills that display.
     *
     * A **Theme** button on the left opens the [showThemeDialog] to let the DM
     * switch between light/dark modes and customise the theme colour roles
     * (accent, background, surface, border).
     */
    private fun buildDisplayToolbar(tableStage: Stage): ToolBar {
        val screens = Screen.getScreens()

        // null represents the "None (hidden)" option — no table view is shown.
        val screenCombo = ComboBox<Screen?>()
        screenCombo.items.add(null)
        screenCombo.items.addAll(screens)
        screenCombo.converter = object : StringConverter<Screen?>() {
            override fun toString(screen: Screen?): String {
                if (screen == null) return "None (hidden)"
                val idx = screens.indexOf(screen)
                val b = screen.bounds
                return "Screen ${idx + 1}: ${b.width.toInt()} × ${b.height.toInt()}"
            }
            override fun fromString(string: String?): Screen? = null
        }

        val moveButton = Button("Move Table View")
        moveButton.setOnAction {
            val selected = screenCombo.selectionModel.selectedItem ?: return@setOnAction
            val bounds = selected.bounds
            if (!tableStage.isShowing) tableStage.show()
            tableStage.isFullScreen = false
            Platform.runLater {
                tableStage.x = bounds.minX
                tableStage.y = bounds.minY
                tableStage.isFullScreen = true
            }
        }

        // Register the listener before setting the initial selection so the
        // button's initial enabled/disabled state is driven by the same logic.
        screenCombo.selectionModel.selectedItemProperty().addListener { _, _, newScreen ->
            if (newScreen == null) {
                tableStage.hide()
                moveButton.isDisable = true
            } else {
                if (!tableStage.isShowing) tableStage.show()
                moveButton.isDisable = false
            }
        }

        // Default to the first real screen to preserve existing behaviour.
        // The listener above fires immediately and sets the button state correctly.
        if (screens.isNotEmpty()) {
            screenCombo.selectionModel.select(screens[0])
        } else {
            screenCombo.selectionModel.selectFirst()
        }

        // Theme button on the left — opens the theme customisation dialog.
        val themeButton = Button("🎨 Theme").apply {
            tooltip = Tooltip("Customise the application theme (light/dark mode and accent colours)")
            setOnAction {
                val owner = scene?.window
                if (owner is Stage) {
                    showThemeDialog(owner)
                }
            }
        }

        // Help button — extracts bundled docs and opens the index page in the system browser.
        val helpButton = Button("❓ Help").apply {
            tooltip = Tooltip("Open the user documentation in your browser")
            setOnAction { HelpManager.openHelp(hostServices) }
        }

        // Spacer pushes screen controls to the right so the layout area is uncluttered.
        val spacer = Region().also { HBox.setHgrow(it, Priority.ALWAYS) }
        val label = Label("Table View screen:").apply { padding = Insets(0.0, 4.0, 0.0, 0.0) }

        return ToolBar(themeButton, helpButton, spacer, label, screenCombo, moveButton)
    }

    /**
     * Opens the theme customisation dialog.
     *
     * The dialog lets the DM:
     * - Switch between **Light** and **Dark** mode.
     * - Customise four concrete colour roles: **Button/Accent**, **Background**,
     *   **Surface** (panels/cards), and **Border** (panel edges).
     *
     * Clicking **OK** immediately applies and persists the chosen theme.
     * Clicking **Cancel** leaves the current theme unchanged.
     */
    private fun showThemeDialog(owner: Stage) {
        val current = ThemeManager.currentTheme

        // Mode selection
        val lightBtn = RadioButton("Light")
        val darkBtn = RadioButton("Dark")
        val modeToggleGroup = ToggleGroup()
        lightBtn.toggleGroup = modeToggleGroup
        darkBtn.toggleGroup = modeToggleGroup
        when (current.mode) {
            ThemeMode.DARK -> darkBtn.isSelected = true
            else -> lightBtn.isSelected = true
        }

        // Helper: parse a hex color string safely, falling back to [fallback] on error.
        fun parseColor(hex: String, fallback: String): Color =
            runCatching { Color.web(hex) }.getOrElse { Color.web(fallback) }

        val modeDefaults = if (current.mode == ThemeMode.DARK) ThemeConfig.DARK_DEFAULTS else ThemeConfig.LIGHT_DEFAULTS

        // Colour pickers — Group 2: Interactive / Accent
        val accentPicker = ColorPicker(
            parseColor(current.accentColor, modeDefaults.accentColor)
        ).apply { maxWidth = Double.MAX_VALUE }

        // Colour pickers — Group 1: Background & Surfaces
        val bgPicker = ColorPicker(
            parseColor(current.bgColor, modeDefaults.bgColor)
        ).apply { maxWidth = Double.MAX_VALUE }
        val surfacePicker = ColorPicker(
            parseColor(current.surfaceColor, modeDefaults.surfaceColor)
        ).apply { maxWidth = Double.MAX_VALUE }
        val borderPicker = ColorPicker(
            parseColor(current.borderColor, modeDefaults.borderColor)
        ).apply { maxWidth = Double.MAX_VALUE }

        // Helper: reset pickers to defaults for the currently selected mode.
        fun resetDefaults() {
            val defaults = if (darkBtn.isSelected) ThemeConfig.DARK_DEFAULTS else ThemeConfig.LIGHT_DEFAULTS
            accentPicker.value = Color.web(defaults.accentColor)
            bgPicker.value = Color.web(defaults.bgColor)
            surfacePicker.value = Color.web(defaults.surfaceColor)
            borderPicker.value = Color.web(defaults.borderColor)
        }

        // Update pickers to mode defaults whenever the mode radio changes.
        lightBtn.setOnAction { resetDefaults() }
        darkBtn.setOnAction { resetDefaults() }

        // Layout using a GridPane with logical groupings.
        val grid = GridPane().apply {
            hgap = 10.0
            vgap = 8.0
            padding = Insets(12.0, 16.0, 8.0, 16.0)
        }
        var row = 0

        // Mode row
        grid.add(Label("Mode:"), 0, row)
        grid.add(HBox(8.0, lightBtn, darkBtn), 1, row++)

        grid.add(Separator(), 0, row++, 2, 1)

        // Group 1: Background & Surfaces
        grid.add(Label("Background:"), 0, row)
        grid.add(bgPicker, 1, row++)
        grid.add(Label("Surface (Buttons):"), 0, row)
        grid.add(surfacePicker, 1, row++)
        grid.add(Label("Border / edges:"), 0, row)
        grid.add(borderPicker, 1, row++)

        grid.add(Separator(), 0, row++, 2, 1)

        // Group 2: Interactive / Accent
        grid.add(Label("Accent:"), 0, row)
        grid.add(accentPicker, 1, row++)

        grid.add(Separator(), 0, row++, 2, 1)

        val resetDefBtn = Button("Reset to Defaults").apply {
            setOnAction { resetDefaults() }
        }
        grid.add(resetDefBtn, 1, row)

        val dialog = Dialog<ThemeConfig>().apply {
            title = "Theme Settings"
            headerText = "Choose a color mode and customize colors."
            initOwner(owner)
            dialogPane.buttonTypes.setAll(ButtonType.OK, ButtonType.CANCEL)
            dialogPane.content = grid
            isResizable = false
        }

        dialog.setResultConverter { btn ->
            if (btn == ButtonType.OK) {
                val mode = if (darkBtn.isSelected) ThemeMode.DARK else ThemeMode.LIGHT
                ThemeConfig(
                    mode = mode,
                    accentColor = colorToHex(accentPicker.value),
                    bgColor = colorToHex(bgPicker.value),
                    surfaceColor = colorToHex(surfacePicker.value),
                    borderColor = colorToHex(borderPicker.value),
                )
            } else null
        }

        dialog.showAndWait().ifPresent { newTheme ->
            ThemeManager.setTheme(newTheme)
        }
    }

    /**
     * Converts a JavaFX [Color] to a CSS hex string such as `"#1565c0"`.
     *
     * Uses [kotlin.math.roundToInt] with a 0–255 clamp to avoid off-by-one
     * errors from floating-point truncation.
     */
    private fun colorToHex(color: Color): String {
        fun channel(v: Double) = (v * 255).roundToInt().coerceIn(0, 255)
        return "#%02x%02x%02x".format(channel(color.red), channel(color.green), channel(color.blue))
    }
}

/** JVM entry point — delegates to the JavaFX application launcher. */
fun main(args: Array<String>) = Application.launch(App::class.java, *args)
