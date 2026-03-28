package com.tabletopcontrol.core

import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ToolBar
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.util.StringConverter

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
        // Discover plugins first so both scenes can reference them.
        plugins = PluginLoader.loadPlugins()
        plugins.forEach { plugin -> println("Loaded plugin: ${plugin.displayName}") }

        // Table screen — displayed on the external monitor / projector
        primaryStage.apply {
            title = "TabletopControl — Table View"
            initStyle(StageStyle.UNDECORATED)
            scene = buildTableScene(plugins)
            isFullScreen = true
            setOnCloseRequest { dmStage.close() }
            show()
        }

        // DM control panel — displayed on the DM's own monitor
        dmStage = Stage().apply {
            title = "TabletopControl — DM Panel"
            scene = buildDmScene(plugins, primaryStage)
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

        // Spacer pushes screen controls to the right so the layout area is uncluttered.
        val spacer = Region().also { HBox.setHgrow(it, Priority.ALWAYS) }
        val label = Label("Table View screen:").apply { padding = Insets(0.0, 4.0, 0.0, 0.0) }

        return ToolBar(spacer, label, screenCombo, moveButton)
    }
}

/** JVM entry point — delegates to the JavaFX application launcher. */
fun main(args: Array<String>) = Application.launch(App::class.java, *args)
