package com.tabletopcontrol.core

import javafx.scene.Node

/**
 * Contract that every DM-panel plugin must implement.
 *
 * A plugin contributes:
 * - [displayName]: human-readable name shown in pane pickers.
 * - [iconPath]: optional classpath resource path to a 24x24 icon.
 * - [workspaceIds]: the DM workspaces in which the plugin can appear.
 * - [createView]: factory for the JavaFX [Node] placed in the DM panel.
 *
 * Plugins must communicate exclusively through [EventBus] and must never
 * hold direct references to other plugins or core singletons.
 */
interface DmPlugin {

    /** Human-readable name for this plugin, displayed in the DM panel. */
    val displayName: String

    /** Optional classpath resource path to a 24x24 icon, or `null` if none. */
    val iconPath: String?
        get() = null

    /**
     * Declares which DM workspaces this plugin is available in.
     *
     * Most plugins belong to the regular [DmWorkspaceId.SESSION] workspace.
     * Specialized tools can opt into builder-focused workspaces without appearing
     * in the default session layout.
     */
    val workspaceIds: Set<DmWorkspaceId>
        get() = setOf(DmWorkspaceId.SESSION)

    /**
     * Creates and returns the JavaFX [Node] that this plugin contributes to the DM panel.
     *
     * This method may be called more than once; implementations should return a
     * fresh node on each invocation.
     */
    fun createView(): Node

    /**
     * Creates and returns the JavaFX [Node] that this plugin contributes to the table
     * (player-facing) screen, or `null` if the plugin has no table-screen presence.
     *
     * The returned node will be displayed on the external monitor / projector.
     * This method may be called more than once; implementations should return a
     * fresh node on each invocation.
     *
     * The default implementation returns `null`.
     */
    fun createTableView(): Node? = null

    /**
     * Called when the application is shutting down.
     *
     * Plugins should release any resources (audio handles, file handles, etc.) here.
     * The default implementation is a no-op.
     */
    fun onShutdown() {}
}
