package com.tabletopcontrol.core

import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
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
 *    plugin-provided content and a **DM control panel** on a separate [Stage] with
 *    one tab per plugin.
 * 3. Gracefully shutting down all plugins when the application exits.
 */
class App : Application() {

    /** The DM control panel window. */
    private lateinit var dmStage: Stage

    /** All loaded plugins; cached here so [stop] can shut them down cleanly. */
    private lateinit var plugins: List<DmPlugin>

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
        // Give every plugin the chance to release its resources
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
     * Each loaded plugin gets its own [Tab] (non-closable) containing the node
     * returned by [DmPlugin.createView].  A built-in **Display** tab is always
     * appended at the end, allowing the DM to choose which screen the Table View
     * is shown on.
     */
    private fun buildDmScene(plugins: List<DmPlugin>, tableStage: Stage): Scene {
        val tabPane = TabPane()
        if (plugins.isEmpty()) {
            val placeholder = Tab("—", StackPane(Label("DM Panel — no plugins loaded")))
            placeholder.isClosable = false
            tabPane.tabs.add(placeholder)
        } else {
            plugins.forEach { plugin ->
                val tab = Tab(plugin.displayName, plugin.createView())
                tab.isClosable = false
                tabPane.tabs.add(tab)
            }
        }
        tabPane.tabs.add(buildDisplayTab(tableStage))
        return Scene(tabPane, 800.0, 600.0)
    }

    /**
     * Builds the built-in **Display** tab that lets the DM choose which screen
     * the Table View is shown on.
     *
     * Moving the Table View to another screen temporarily exits fullscreen,
     * repositions the window to the target screen's origin, then re-enters
     * fullscreen so it fills that display.
     */
    private fun buildDisplayTab(tableStage: Stage): Tab {
        val screens = Screen.getScreens()

        val screenCombo = ComboBox<Screen>()
        screenCombo.items.setAll(screens)
        screenCombo.converter = object : StringConverter<Screen>() {
            override fun toString(screen: Screen?): String {
                if (screen == null) return ""
                val idx = screens.indexOf(screen)
                val b = screen.bounds
                return "Screen ${idx + 1}: ${b.width.toInt()} × ${b.height.toInt()}"
            }
            override fun fromString(string: String?): Screen? = null
        }
        screenCombo.selectionModel.selectFirst()

        val moveButton = Button("Move Table View")
        moveButton.setOnAction {
            val selected = screenCombo.selectionModel.selectedItem ?: return@setOnAction
            val bounds = selected.bounds
            tableStage.isFullScreen = false
            Platform.runLater {
                tableStage.x = bounds.minX
                tableStage.y = bounds.minY
                tableStage.isFullScreen = true
            }
        }

        val content = VBox(8.0, Label("Table View screen:"), screenCombo, moveButton)
        content.padding = Insets(16.0)

        val tab = Tab("Display", content)
        tab.isClosable = false
        return tab
    }
}

/** JVM entry point — delegates to the JavaFX application launcher. */
fun main(args: Array<String>) = Application.launch(App::class.java, *args)
