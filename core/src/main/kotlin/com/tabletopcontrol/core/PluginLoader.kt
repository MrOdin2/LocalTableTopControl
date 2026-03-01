package com.tabletopcontrol.core

import java.util.ServiceLoader

/**
 * Discovers and loads [DmPlugin] implementations via Java's [ServiceLoader] mechanism.
 *
 * To register a plugin, create a file at:
 *   `META-INF/services/com.tabletopcontrol.core.DmPlugin`
 * containing the fully-qualified class name of the implementation (one per line).
 */
object PluginLoader {

    /**
     * Loads all [DmPlugin] implementations found on the class-path.
     *
     * @return an immutable list of loaded plugin instances; empty when none are registered.
     */
    fun loadPlugins(): List<DmPlugin> = ServiceLoader.load(DmPlugin::class.java).toList()
}
