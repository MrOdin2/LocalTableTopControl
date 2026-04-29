package com.tabletopcontrol.core.scene

class SceneManager(
    participants: List<SceneParticipant>,
    private val onSceneLoaded: () -> Unit = {},
) {
    private val participants: List<SceneParticipant> = participants
        .distinctBy { it.sceneKey }
        .sortedWith(compareBy(SceneParticipant::sceneLoadOrder, SceneParticipant::sceneKey))

    fun loadAllScenes(): List<SavedScene> = SceneLibrary.loadAll()

    fun hasScene(name: String): Boolean = SceneLibrary.hasScene(name)

    fun openScenesFolder() {
        SceneLibrary.openScenesFolder()
    }

    fun deleteScene(name: String) {
        SceneLibrary.delete(name)
    }

    fun saveScene(name: String): SceneSaveResult {
        val failures = mutableListOf<SceneParticipantFailure>()
        val sections = buildList {
            participants.forEach { participant ->
                val payload = runCatching { participant.captureSceneState() }
                    .onFailure { error ->
                        failures += SceneParticipantFailure(
                            key = participant.sceneKey,
                            displayName = participant.sceneDisplayName,
                            cause = error,
                        )
                    }
                    .getOrNull()
                if (!payload.isNullOrBlank()) {
                    add(SceneSection(participant.sceneKey, payload))
                }
            }
        }

        val scene = SavedScene(name = name.trim(), sections = sections)
        SceneLibrary.save(scene)
        return SceneSaveResult(scene = scene, failures = failures)
    }

    fun loadScene(scene: SavedScene): SceneLoadResult {
        val sectionsByKey = scene.sections.associateBy(SceneSection::key)
        val failures = mutableListOf<SceneParticipantFailure>()

        participants.forEach { participant ->
            val section = sectionsByKey[participant.sceneKey] ?: return@forEach
            runCatching {
                participant.applySceneState(section.payload)
            }.onFailure { error ->
                failures += SceneParticipantFailure(
                    key = participant.sceneKey,
                    displayName = participant.sceneDisplayName,
                    cause = error,
                )
            }
        }

        onSceneLoaded()
        return SceneLoadResult(scene = scene, failures = failures)
    }
}
