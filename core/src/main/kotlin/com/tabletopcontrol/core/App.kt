package com.tabletopcontrol.core

import com.tabletopcontrol.core.ui.color.ColorContrast
import com.tabletopcontrol.core.ui.color.ColorEditorDialog
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.core.ui.dialog.DialogFlows
import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Orientation
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
 * 2. Initialising the two-screen layout: a full-screen table view driven by plugin content
 *    and a DM control panel on a separate [Stage].
 * 3. Managing the DM panel split layout via [DmLayoutManager].
 * 4. Gracefully shutting down all plugins and persisting active layouts when the
 *    application exits.
 */
class App : Application() {

    /** The DM control panel window. */
    private lateinit var dmStage: Stage

    /** All loaded plugins; cached here so [stop] can shut them down cleanly. */
    private lateinit var plugins: List<DmPlugin>

    /** Manages the currently active recursive split-pane layout of the DM panel. */
    private var dmLayoutManager: DmLayoutManager? = null

    override fun start(primaryStage: Stage) {
        primaryStage.initStyle(StageStyle.UNDECORATED)

        plugins = PluginLoader.loadPlugins()
        plugins.forEach { plugin -> println("Loaded plugin: ${plugin.displayName}") }

        val tableScene = buildTableScene(plugins)
        val dmScene = buildDmScene(plugins, primaryStage)

        ThemeManager.registerScene(tableScene)
        ThemeManager.registerScene(dmScene)

        primaryStage.apply {
            title = "TabletopControl - Table View"
            scene = tableScene
            isFullScreen = true
            setOnCloseRequest { dmStage.close() }
            show()
        }

        dmStage = Stage().apply {
            title = "TabletopControl - DM Panel"
            scene = dmScene
            setOnCloseRequest { primaryStage.close() }
            show()
        }
    }

    override fun stop() {
        dmLayoutManager?.saveLayout()
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
            Label("Table View - no plugins providing content")
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
     * The toolbar also exposes workspace switching so specialized tool suites can
     * keep their own independently saved pane arrangement.
     */
    private fun buildDmScene(plugins: List<DmPlugin>, tableStage: Stage): Scene {
        val root = BorderPane()
        root.top = buildDisplayToolbar(tableStage, plugins, root)
        if (root.center == null) {
            switchWorkspace(root, plugins, availableWorkspaces(plugins).first())
        }
        return Scene(root, 1280.0, 720.0)
    }

    /**
     * Builds a slim toolbar at the top of the DM panel that lets the DM switch workspaces
     * and choose which screen the table view is shown on.
     */
    private fun buildDisplayToolbar(
        tableStage: Stage,
        plugins: List<DmPlugin>,
        dmRoot: BorderPane,
    ): ToolBar {
        val screens = Screen.getScreens()
        val workspaces = availableWorkspaces(plugins)

        val screenCombo = ComboBox<Screen?>()
        screenCombo.items.add(null)
        screenCombo.items.addAll(screens)
        screenCombo.converter = object : StringConverter<Screen?>() {
            override fun toString(screen: Screen?): String {
                if (screen == null) return "None (hidden)"
                val idx = screens.indexOf(screen).coerceAtLeast(0)
                val bounds = screen.bounds
                return formatScreenLabel(
                    index = idx,
                    logicalWidth = bounds.width,
                    logicalHeight = bounds.height,
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

        if (screens.isNotEmpty()) {
            screenCombo.selectionModel.select(screens[0])
        } else {
            screenCombo.selectionModel.selectFirst()
        }

        val themeButton = Button("Theme").apply {
            tooltip = Tooltip("Customise the application theme")
            setOnAction {
                val owner = scene?.window
                if (owner is Stage) {
                    showThemeDialog(owner)
                }
            }
        }

        val helpButton = Button("Help").apply {
            tooltip = Tooltip("Open the user documentation in your browser")
            setOnAction { HelpManager.openHelp(hostServices) }
        }

        val workspaceCombo = ComboBox<DmWorkspaceId>().apply {
            items.addAll(workspaces)
            converter = object : StringConverter<DmWorkspaceId>() {
                override fun toString(workspace: DmWorkspaceId?): String = workspace?.displayName.orEmpty()
                override fun fromString(string: String?): DmWorkspaceId? = null
            }
            tooltip = Tooltip("Switch between independently saved DM workspaces")
            selectionModel.selectedItemProperty().addListener { _, _, workspace ->
                if (workspace != null) {
                    switchWorkspace(dmRoot, plugins, workspace)
                }
            }
            selectionModel.select(workspaces.first())
        }

        val spacer = Region().also { HBox.setHgrow(it, Priority.ALWAYS) }
        val workspaceLabel = Label("Workspace:").apply { padding = Insets(0.0, 4.0, 0.0, 0.0) }
        val screenLabel = Label("Table View screen:").apply { padding = Insets(0.0, 4.0, 0.0, 0.0) }

        return ToolBar(
            themeButton,
            helpButton,
            Separator(Orientation.VERTICAL),
            workspaceLabel,
            workspaceCombo,
            spacer,
            screenLabel,
            screenCombo,
            moveButton,
        )
    }

    private fun availableWorkspaces(plugins: List<DmPlugin>): List<DmWorkspaceId> =
        DmWorkspaceId.entries.filter { workspace ->
            workspace == DmWorkspaceId.SESSION || plugins.any { workspace in it.workspaceIds }
        }.ifEmpty { listOf(DmWorkspaceId.SESSION) }

    private fun switchWorkspace(
        root: BorderPane,
        plugins: List<DmPlugin>,
        workspace: DmWorkspaceId,
    ) {
        dmLayoutManager?.saveLayout()
        val workspacePlugins = plugins.filter { workspace in it.workspaceIds }
        val layoutManager = DmLayoutManager(
            plugins = workspacePlugins,
            layoutConfigName = workspace.layoutConfigName,
        )
        dmLayoutManager = layoutManager
        root.center = layoutManager.container
    }

    /**
     * Opens the theme customisation dialog.
     */
    private fun showThemeDialog(owner: Stage) {
        val current = ThemeManager.currentTheme

        val lightBtn = RadioButton("Light")
        val darkBtn = RadioButton("Dark")
        val modeToggleGroup = ToggleGroup()
        lightBtn.toggleGroup = modeToggleGroup
        darkBtn.toggleGroup = modeToggleGroup
        when (current.mode) {
            ThemeMode.DARK -> darkBtn.isSelected = true
            else -> lightBtn.isSelected = true
        }

        fun parseColor(hex: String, fallback: String): Color {
            val safeFallback = ColorHexCodec.parseOrDefault(fallback, Color.GRAY)
            return ColorHexCodec.parseOrDefault(hex, safeFallback)
        }

        val modeDefaults =
            if (current.mode == ThemeMode.DARK) ThemeConfig.DARK_DEFAULTS else ThemeConfig.LIGHT_DEFAULTS

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

        lightBtn.setOnAction { resetDefaults() }
        darkBtn.setOnAction { resetDefaults() }

        val grid = GridPane().apply {
            hgap = 10.0
            vgap = 8.0
            padding = Insets(12.0, 16.0, 8.0, 16.0)
        }
        var row = 0

        grid.add(Label("Mode:"), 0, row)
        grid.add(HBox(8.0, lightBtn, darkBtn), 1, row++)

        grid.add(Separator(), 0, row++, 2, 1)

        grid.add(Label("Background:"), 0, row)
        grid.add(bgButton, 1, row++)
        grid.add(Label("Surface (Buttons):"), 0, row)
        grid.add(surfaceButton, 1, row++)
        grid.add(Label("Border / edges:"), 0, row)
        grid.add(borderButton, 1, row++)

        grid.add(Separator(), 0, row++, 2, 1)

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

/** JVM entry point - delegates to the JavaFX application launcher. */
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
