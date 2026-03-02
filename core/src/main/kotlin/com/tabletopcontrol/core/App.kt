package com.tabletopcontrol.core

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import javafx.stage.Stage

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
            scene = buildTableScene(plugins)
            show()
        }

        // DM control panel — displayed on the DM's own monitor
        dmStage = Stage().apply {
            title = "TabletopControl — DM Panel"
            scene = buildDmScene(plugins)
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
     * returned by [DmPlugin.createView].  When no plugins are loaded a placeholder
     * label is shown instead.
     */
    private fun buildDmScene(plugins: List<DmPlugin>): Scene {
        if (plugins.isEmpty()) {
            return Scene(StackPane(Label("DM Panel — no plugins loaded")), 800.0, 600.0)
        }
        val tabPane = TabPane()
        plugins.forEach { plugin ->
            val tab = Tab(plugin.displayName, plugin.createView())
            tab.isClosable = false
            tabPane.tabs.add(tab)
        }
        return Scene(tabPane, 800.0, 600.0)
    }
}

/** JVM entry point — delegates to the JavaFX application launcher. */
fun main(args: Array<String>) = Application.launch(App::class.java, *args)
