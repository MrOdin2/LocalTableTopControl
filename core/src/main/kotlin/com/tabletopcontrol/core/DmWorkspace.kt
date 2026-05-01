package com.tabletopcontrol.core

/**
 * Top-level DM workspaces available inside the application.
 *
 * Each workspace has its own independently persisted pane layout so specialized
 * editing flows can live alongside the standard session view without fighting
 * over the same split-pane arrangement.
 */
enum class DmWorkspaceId(
    val displayName: String,
    val layoutConfigName: String,
) {
    SESSION(
        displayName = "Session",
        layoutConfigName = LayoutSerializer.DEFAULT_CONFIG_NAME,
    ),
    DYNAMIC_MAP_BUILDER(
        displayName = "Dynamic Map Builder",
        layoutConfigName = "dynamic-map-builder-layout.conf",
    ),
}
