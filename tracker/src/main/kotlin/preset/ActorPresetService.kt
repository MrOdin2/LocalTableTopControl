package com.tabletopcontrol.new_tracker.preset

import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorImageSettings
import com.tabletopcontrol.new_tracker.model.ActorTracker
import com.tabletopcontrol.new_tracker.scene.TrackerSceneState
import javafx.application.Platform

class ActorPresetService(
    private val actorTracker: ActorTracker,
) {

    fun saveActor(
        actor: Actor,
        confirmOverwrite: (String) -> Boolean,
    ): Boolean {
        val presetWithoutThumbnail = actor.toPreset()
        if (
            PresetLibrary.hasPreset(presetWithoutThumbnail.name, presetWithoutThumbnail.folder) &&
            !confirmOverwrite(presetWithoutThumbnail.name)
        ) {
            return false
        }
        PresetLibrary.savePreset(presetWithoutThumbnail)

        val imageUri = actor.imageSettings.uri ?: return true
        Thread {
            val base64 = PresetLibrary.loadAndScaleImage(imageUri) ?: return@Thread
            val onDiskFile = PresetLibrary.fileFor(
                presetWithoutThumbnail.name,
                presetWithoutThumbnail.folder,
            )
            val latest = runCatching {
                PresetLibrary.deserialize(onDiskFile.readText())
            }.getOrNull() ?: return@Thread
            if (latest.imageUri != imageUri) {
                return@Thread
            }
            PresetLibrary.savePreset(latest.copy(imageBase64 = base64))
        }.also { it.isDaemon = true }.start()
        return true
    }

    fun loadAll(): List<PresetLibrary.Preset> = PresetLibrary.loadAll()

    fun deleteByName(name: String) {
        PresetLibrary.delete(name)
    }

    fun openPresetsFolder() {
        PresetLibrary.openPresetsFolder()
    }

    fun loadPreset(
        preset: PresetLibrary.Preset,
        onRefresh: () -> Unit,
    ) {
        val actor = preset.toActor()
        val initialImageUri = actor.imageSettings.uri
        actorTracker.addActor(actor)
        onRefresh()
        restoreEmbeddedImage(actor.id, preset, initialImageUri, onRefresh)
    }

    private fun restoreEmbeddedImage(
        actorId: String,
        preset: PresetLibrary.Preset,
        initialImageUri: String?,
        onRefresh: () -> Unit,
    ) {
        val imageBase64 = preset.imageBase64 ?: return
        Thread {
            val decodedUri = PresetLibrary.base64ToCachedUri(imageBase64) ?: return@Thread
            Platform.runLater {
                val currentActor = actorTracker.findActor(actorId) ?: return@runLater
                if (currentActor.imageSettings.uri != initialImageUri) {
                    return@runLater
                }
                actorTracker.updateActor(
                    currentActor.copy(
                        imageSettings = currentActor.imageSettings.copy(
                            uri = decodedUri,
                            scaleX = preset.imageScaleX,
                            scaleY = preset.imageScaleY,
                            offsetX = preset.imageOffsetX,
                            offsetY = preset.imageOffsetY,
                        ),
                    ),
                )
                onRefresh()
            }
        }.also { it.isDaemon = true }.start()
    }

    internal fun recoverSceneActors(sceneState: TrackerSceneState): TrackerSceneState {
        val presets = PresetLibrary.loadAll()
        return sceneState.copy(
            actors = sceneState.actors.map { actor ->
                val recoveredUri = PresetLibrary.recoverImageUriFor(actor, presets) ?: actor.imageSettings.uri
                if (recoveredUri == actor.imageSettings.uri) {
                    actor
                } else {
                    actor.copy(imageSettings = actor.imageSettings.copy(uri = recoveredUri))
                }
            },
        )
    }
}

internal fun Actor.toPreset(): PresetLibrary.Preset =
    PresetLibrary.Preset(
        name = name,
        hp = hp,
        ac = ac,
        initiative = 0,
        initiativeEnabled = false,
        tokenSize = tokenSize,
        actorType = actorType,
        features = features,
        effects = effects,
        imageUri = imageSettings.uri,
        imageScaleX = imageSettings.scaleX,
        imageScaleY = imageSettings.scaleY,
        imageOffsetX = imageSettings.offsetX,
        imageOffsetY = imageSettings.offsetY,
    )

internal fun PresetLibrary.Preset.toActor(): Actor =
    Actor(
        name = name,
        hp = hp,
        ac = ac,
        initiative = null,
        tokenSize = tokenSize,
        actorType = actorType,
        features = features,
        effects = effects,
        imageSettings = ActorImageSettings(
            uri = imageUri,
            scaleX = imageScaleX,
            scaleY = imageScaleY,
            offsetX = imageOffsetX,
            offsetY = imageOffsetY,
        ),
    )
