package com.tabletopcontrol.core.scene

/**
 * Optional extension point for plugins that want to participate in saved scenes.
 *
 * Implementations provide a stable [sceneKey] plus an opaque string payload that
 * captures the plugin's current state. The core scene browser persists these
 * payloads without needing compile-time knowledge of any plugin-specific models.
 */
interface SceneParticipant {
    /** Stable persistence key for this participant, e.g. `"map"` or `"tracker"`. */
    val sceneKey: String

    /** Human-readable participant name used in user-facing feedback. */
    val sceneDisplayName: String
        get() = sceneKey

    /**
     * Lower values are applied first when loading a scene.
     *
     * This allows dependencies such as tracker actors being restored before map
     * token positions are replayed.
     */
    val sceneLoadOrder: Int
        get() = 0

    /** Captures the current plugin state as an opaque payload string. */
    fun captureSceneState(): String?

    /** Applies a previously captured payload string. */
    fun applySceneState(payload: String)
}

data class SceneSection(
    val key: String,
    val payload: String,
)

data class SavedScene(
    val name: String,
    val sections: List<SceneSection>,
)

data class SceneParticipantFailure(
    val key: String,
    val displayName: String,
    val cause: Throwable,
)

data class SceneSaveResult(
    val scene: SavedScene,
    val failures: List<SceneParticipantFailure>,
)

data class SceneLoadResult(
    val scene: SavedScene,
    val failures: List<SceneParticipantFailure>,
)
