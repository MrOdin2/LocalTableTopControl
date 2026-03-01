package com.tabletopcontrol.core

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.stage.Stage

/**
 * Application entry point and top-level JavaFX lifecycle manager.
 *
 * Responsibilities:
 * 1. Initialising the two-screen layout — a full-screen **table view** and a
 *    **DM control panel** on a separate [Stage].
 * 2. Discovering and loading plugins via [PluginLoader].
 * 3. Gracefully shutting down all plugins when the application exits.
 */
class App : Application() {

    /** The DM control panel window. */
    private lateinit var dmStage: Stage

    override fun start(primaryStage: Stage) {
        // Table screen — displayed on the external monitor / projector
        primaryStage.apply {
            title = "TabletopControl — Table View"
            scene = buildTableScene()
            show()
        }

        // DM control panel — displayed on the DM's own monitor
        dmStage = Stage().apply {
            title = "TabletopControl — DM Panel"
            scene = buildDmScene()
            show()
        }

        // Discover and load all registered plugins
        val plugins = PluginLoader.loadPlugins()
        plugins.forEach { plugin ->
            // Future: add each plugin's view to the DM panel tab strip
            println("Loaded plugin: ${plugin.displayName}")
        }
    }

    override fun stop() {
        // Give every plugin the chance to release its resources
        PluginLoader.loadPlugins().forEach { it.onShutdown() }
    }

    /** Builds the table-screen [Scene] with a placeholder for the map canvas. */
    private fun buildTableScene(): Scene {
        val placeholder = Label("Table View — map will render here")
        return Scene(StackPane(placeholder), 1280.0, 720.0)
    }

    /** Builds the DM control panel [Scene] with a placeholder for plugin tabs. */
    private fun buildDmScene(): Scene {
        val placeholder = Label("DM Panel — plugin tabs will appear here")
        return Scene(StackPane(placeholder), 800.0, 600.0)
    }
}

/** JVM entry point — delegates to the JavaFX application launcher. */
fun main(args: Array<String>) = Application.launch(App::class.java, *args)
