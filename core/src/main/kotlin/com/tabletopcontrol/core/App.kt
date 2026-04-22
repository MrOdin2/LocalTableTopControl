package com.tabletopcontrol.core

import com.tabletopcontrol.core.scene.SceneBrowserDialog
import com.tabletopcontrol.core.scene.SceneManager
import com.tabletopcontrol.core.scene.SceneParticipant
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.core.ui.color.ColorContrast
import com.tabletopcontrol.core.ui.color.ColorEditorDialog
import com.tabletopcontrol.core.ui.dialog.DialogFlows
import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.ComboBox
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
import kotlin.math.abs
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

    /** Coordinates cross-plugin scene save/load actions. */
    private lateinit var sceneManager: SceneManager

    override fun start(primaryStage: Stage) {
        // Set primaryStage style FIRST, before any other operations.
        // In JavaFX, initStyle() must be called before the stage is shown or scene is set.
        primaryStage.initStyle(StageStyle.UNDECORATED)

        // Discover plugins first so both scenes can reference them.
        plugins = PluginLoader.loadPlugins()
        plugins.forEach { plugin -> println("Loaded plugin: ${plugin.displayName}") }
        sceneManager = SceneManager(
            participants = plugins.filterIsInstance<SceneParticipant>(),
            onSceneLoaded = { dmLayoutManager?.refreshViews() },
        )

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
        root.top = buildDisplayToolbar(tableStage, sceneManager)
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
    private fun buildDisplayToolbar(tableStage: Stage, sceneManager: SceneManager): ToolBar {
        val screens = Screen.getScreens()

        // null represents the "None (hidden)" option — no table view is shown.
        val screenCombo = ComboBox<Screen?>()
        screenCombo.items.add(null)
        screenCombo.items.addAll(screens)
        screenCombo.converter = object : StringConverter<Screen?>() {
            override fun toString(screen: Screen?): String {
                if (screen == null) return "None (hidden)"
                val idx = screens.indexOf(screen).coerceAtLeast(0)
                val b = screen.bounds
                return formatScreenLabel(
                    index = idx,
                    logicalWidth = b.width,
                    logicalHeight = b.height,
                    outputScaleX = screen.outputScaleX,
                    outputScaleY = screen.outputScaleY,
                )
            }
            override fun fromString(string: String?): Screen? = null
        }

        val moveButton = Button("Move Table View")
        moveButton.setOnAction {
            val selected = screenCombo.selectionModel.selectedItem ?: return@setOnAction
            moveTableStageToScreen(tableStage, selected)
        }

        // Register the listener before setting the initial selection so the
        // button's initial enabled/disabled state is driven by the same logic.
        screenCombo.selectionModel.selectedItemProperty().addListener { _, _, newScreen ->
            if (newScreen == null) {
                tableStage.hide()
                moveButton.isDisable = true
            } else {
                moveButton.isDisable = false
                if (!tableStage.isShowing && tableStage.scene != null) {
                    moveTableStageToScreen(tableStage, newScreen)
                }
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
        val scenesButton = Button("Scenes...").apply {
            tooltip = Tooltip("Browse, save, and load reusable encounter scenes")
            setOnAction {
                SceneBrowserDialog(sceneManager).show(scene?.window)
            }
        }

        val spacer = Region().also { HBox.setHgrow(it, Priority.ALWAYS) }
        val label = Label("Table View screen:").apply { padding = Insets(0.0, 4.0, 0.0, 0.0) }

        return ToolBar(themeButton, scenesButton, helpButton, spacer, label, screenCombo, moveButton)
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
        fun parseColor(hex: String, fallback: String): Color {
            val safeFallback = ColorHexCodec.parseOrDefault(fallback, Color.GRAY)
            return ColorHexCodec.parseOrDefault(hex, safeFallback)
        }

        val modeDefaults = if (current.mode == ThemeMode.DARK) ThemeConfig.DARK_DEFAULTS else ThemeConfig.LIGHT_DEFAULTS

        var accentColor = parseColor(current.accentColor, modeDefaults.accentColor)
        var bgColor = parseColor(current.bgColor, modeDefaults.bgColor)
        var surfaceColor = parseColor(current.surfaceColor, modeDefaults.surfaceColor)
        var borderColor = parseColor(current.borderColor, modeDefaults.borderColor)

        fun styleColorButton(button: Button, color: Color) {
            val bgHex = ColorHexCodec.colorToHex(color)
            val fgHex = ColorContrast.textColorHexForBackground(color)
            button.text = bgHex
            button.style = "-fx-background-color: $bgHex; -fx-text-fill: $fgHex;"
        }

        fun createColorButton(
            title: String,
            prompt: String,
            getColor: () -> Color,
            setColor: (Color) -> Unit,
        ): Button = Button().apply {
            maxWidth = Double.MAX_VALUE
            styleColorButton(this, getColor())
            setOnAction {
                val selected = ColorEditorDialog.showDialog(
                    owner = owner,
                    title = title,
                    prompt = prompt,
                    initialColor = getColor(),
                )
                if (selected != null) {
                    setColor(selected)
                    styleColorButton(this, selected)
                }
            }
        }

        val accentButton = createColorButton(
            title = "Select Accent Colour",
            prompt = "Choose the accent colour used for highlighted controls.",
            getColor = { accentColor },
            setColor = { accentColor = it },
        )
        val bgButton = createColorButton(
            title = "Select Background Colour",
            prompt = "Choose the base background colour for scenes and windows.",
            getColor = { bgColor },
            setColor = { bgColor = it },
        )
        val surfaceButton = createColorButton(
            title = "Select Surface Colour",
            prompt = "Choose the surface colour used for panels and cards.",
            getColor = { surfaceColor },
            setColor = { surfaceColor = it },
        )
        val borderButton = createColorButton(
            title = "Select Border Colour",
            prompt = "Choose the border colour used for separators and outlines.",
            getColor = { borderColor },
            setColor = { borderColor = it },
        )

        // Helper: reset dialog buttons to defaults for the currently selected mode.
        fun resetDefaults() {
            val defaults = if (darkBtn.isSelected) ThemeConfig.DARK_DEFAULTS else ThemeConfig.LIGHT_DEFAULTS
            accentColor = ColorHexCodec.hexToColor(defaults.accentColor)
            bgColor = ColorHexCodec.hexToColor(defaults.bgColor)
            surfaceColor = ColorHexCodec.hexToColor(defaults.surfaceColor)
            borderColor = ColorHexCodec.hexToColor(defaults.borderColor)
            styleColorButton(accentButton, accentColor)
            styleColorButton(bgButton, bgColor)
            styleColorButton(surfaceButton, surfaceColor)
            styleColorButton(borderButton, borderColor)
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
        grid.add(bgButton, 1, row++)
        grid.add(Label("Surface (Buttons):"), 0, row)
        grid.add(surfaceButton, 1, row++)
        grid.add(Label("Border / edges:"), 0, row)
        grid.add(borderButton, 1, row++)

        grid.add(Separator(), 0, row++, 2, 1)

        // Group 2: Interactive / Accent
        grid.add(Label("Accent:"), 0, row)
        grid.add(accentButton, 1, row++)

        grid.add(Separator(), 0, row++, 2, 1)

        val resetDefBtn = Button("Reset to Defaults").apply {
            setOnAction { resetDefaults() }
        }
        grid.add(resetDefBtn, 1, row)

        val newTheme = DialogFlows.showResultDialog(
            owner = owner,
            title = "Theme Settings",
            headerText = "Choose a color mode and customize colors.",
            content = grid,
            buttonTypes = listOf(ButtonType.OK, ButtonType.CANCEL),
        ) { btn ->
            DialogFlows.resultForButton(btn) {
                val mode = if (darkBtn.isSelected) ThemeMode.DARK else ThemeMode.LIGHT
                ThemeConfig(
                    mode = mode,
                    accentColor = ColorHexCodec.colorToHex(accentColor),
                    bgColor = ColorHexCodec.colorToHex(bgColor),
                    surfaceColor = ColorHexCodec.colorToHex(surfaceColor),
                    borderColor = ColorHexCodec.colorToHex(borderColor),
                )
            }
        }

        if (newTheme != null) {
            ThemeManager.setTheme(newTheme)
        }
    }
}

/** JVM entry point — delegates to the JavaFX application launcher. */
fun main(args: Array<String>) = Application.launch(App::class.java, *args)

private fun moveTableStageToScreen(tableStage: Stage, screen: Screen) {
    val bounds = screen.bounds
    tableStage.isIconified = false
    tableStage.isFullScreen = false
    tableStage.x = bounds.minX
    tableStage.y = bounds.minY
    tableStage.width = bounds.width
    tableStage.height = bounds.height
    if (!tableStage.isShowing) {
        tableStage.show()
    }
    tableStage.toFront()

    // Mixed-DPI Windows setups can report the correct screen in JavaFX but still
    // leave the stage at its old windowed size for one pulse after moving.
    Platform.runLater {
        tableStage.x = bounds.minX
        tableStage.y = bounds.minY
        tableStage.width = bounds.width
        tableStage.height = bounds.height
        Platform.runLater {
            tableStage.isFullScreen = true
            tableStage.toFront()
        }
    }
}

internal fun formatScreenLabel(
    index: Int,
    logicalWidth: Double,
    logicalHeight: Double,
    outputScaleX: Double,
    outputScaleY: Double,
): String {
    val pixelWidth = effectivePixelSpan(logicalWidth, outputScaleX)
    val pixelHeight = effectivePixelSpan(logicalHeight, outputScaleY)
    val scaleSuffix = formatScaleSuffix(outputScaleX, outputScaleY)
    return buildString {
        append("Screen ")
        append(index + 1)
        append(": ")
        append(pixelWidth)
        append(" x ")
        append(pixelHeight)
        if (scaleSuffix != null) {
            append(" (")
            append(scaleSuffix)
            append(")")
        }
    }
}

internal fun effectivePixelSpan(logicalSpan: Double, outputScale: Double): Int {
    if (!logicalSpan.isFinite() || logicalSpan <= 0.0) return 0
    val safeScale = if (outputScale.isFinite() && outputScale > 0.0) outputScale else 1.0
    return (logicalSpan * safeScale).roundToInt().coerceAtLeast(1)
}

private fun formatScaleSuffix(outputScaleX: Double, outputScaleY: Double): String? {
    val xPercent = scalePercent(outputScaleX)
    val yPercent = scalePercent(outputScaleY)
    if (xPercent == 100 && yPercent == 100) return null
    return if (abs(outputScaleX - outputScaleY) < 0.001) {
        "Windows scale ${xPercent}%"
    } else {
        "Windows scale ${xPercent}%/${yPercent}%"
    }
}

private fun scalePercent(outputScale: Double): Int {
    val safeScale = if (outputScale.isFinite() && outputScale > 0.0) outputScale else 1.0
    return (safeScale * 100.0).roundToInt()
}
