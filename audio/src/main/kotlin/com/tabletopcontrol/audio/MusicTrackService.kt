package com.tabletopcontrol.audio

import com.tabletopcontrol.audio.shared.MediaTrackController
import com.tabletopcontrol.audio.shared.MediaTrackStatus
import com.tabletopcontrol.core.persistence.LocalFiles
import com.tabletopcontrol.core.ui.reorder.ReorderSupport
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns persisted track state plus orchestration around the shared [MediaTrackController].
 *
 * The service keeps media lifecycle wiring and persistence decisions out of [MusicPlugin] so
 * the plugin can focus on building cards and forwarding user actions.
 */
internal class MusicTrackService(
    private val settingsStore: MusicTrackSettingsStore = SerializerMusicTrackSettingsStore,
    private val controllerFactory: () -> MediaTrackController = ::MediaTrackController,
) {
    interface Listener {
        fun onTrackSnapshotChanged(track: MusicTrackState, snapshot: MusicTrackSnapshot) {}

        fun onTrackLoadResult(track: MusicTrackState, result: MusicTrackLoadResult) {}

        fun onTrackPlaybackFailure(track: MusicTrackState, result: MusicTrackPlaybackResult.Failed) {}
    }

    var listener: Listener? = null

    var masterVolume: Double = 1.0
        private set

    private val trackStates = mutableListOf<MusicTrackState>()
    val tracks: List<MusicTrackState> get() = trackStates

    private var initialized = false

    fun initializeIfNeeded() {
        if (initialized) return

        val loaded = settingsStore.load()
        masterVolume = loaded.masterVolume
        trackStates.clear()
        trackStates += loaded.tracks
            .take(MusicPlugin.MAX_TRACK_COUNT)
            .ifEmpty { listOf(PersistedMusicTrack()) }
            .map { persisted ->
                MusicTrackState(
                    uri = persisted.uri,
                    volume = persisted.volume,
                    loop = persisted.loop,
                )
            }
        initialized = true
    }

    fun snapshotOf(track: MusicTrackState): MusicTrackSnapshot {
        val controller = track.controller
        val status = controller?.status()
        val phase = when {
            track.uri == null -> MusicTrackPlaybackPhase.UNLOADED
            track.reachedEnd && !track.loop -> MusicTrackPlaybackPhase.ENDED
            controller == null -> MusicTrackPlaybackPhase.UNAVAILABLE
            status == MediaTrackStatus.PLAYING -> MusicTrackPlaybackPhase.PLAYING
            status == MediaTrackStatus.PAUSED -> MusicTrackPlaybackPhase.PAUSED
            status == MediaTrackStatus.STOPPED -> MusicTrackPlaybackPhase.STOPPED
            status == MediaTrackStatus.READY -> MusicTrackPlaybackPhase.READY
            else -> MusicTrackPlaybackPhase.UNAVAILABLE
        }
        return MusicTrackSnapshot(
            uri = track.uri,
            phase = phase,
            status = status,
            isUsable = controller?.isUsable() == true,
            isLoading = track.pendingController != null,
            currentTime = controller?.currentTime(),
            totalDuration = controller?.duration(),
        )
    }

    fun setMasterVolume(volume: Double) {
        masterVolume = volume.coerceIn(0.0, 1.0)
        trackStates.forEach { track ->
            track.controller?.setVolume(effectiveVolume(track))
        }
    }

    fun setTrackVolume(track: MusicTrackState, volume: Double) {
        track.volume = volume.coerceIn(0.0, 1.0)
        track.controller?.setVolume(effectiveVolume(track))
    }

    fun setTrackLoop(track: MusicTrackState, loop: Boolean) {
        track.loop = loop
        track.controller?.setCycleCount(cycleCountFor(track))
        persistSettings()
        emitSnapshotChanged(track)
    }

    fun loadSelectedTrack(track: MusicTrackState, uri: String): MusicTrackLoadResult =
        startTrackLoad(
            track = track,
            uri = uri,
            clearAssignedTrackOnFailure = false,
            persistOnActivation = true,
        )

    fun restoreTrack(track: MusicTrackState): MusicTrackLoadResult? {
        val assignedUri = track.uri ?: return null
        if (track.pendingController != null || track.controller?.hasPlayer() == true) return null

        return startTrackLoad(
            track = track,
            uri = assignedUri,
            clearAssignedTrackOnFailure = true,
            persistOnActivation = false,
        )
    }

    fun togglePlayback(track: MusicTrackState): MusicTrackPlaybackResult {
        val controller = track.controller ?: return playbackFailure(
            track = track,
            failure = MusicTrackPlaybackFailure.NoActiveTrack(track.uri),
        )

        return when (controller.status()) {
            MediaTrackStatus.PLAYING -> {
                track.reachedEnd = false
                controller.pause()
                playbackSuccess(track, MusicTrackPlaybackAction.PAUSE)
            }

            else -> {
                if (!controller.isUsable()) {
                    return playbackFailure(
                        track = track,
                        failure = MusicTrackPlaybackFailure.Unavailable(track.uri, controller.status()),
                    )
                }
                track.reachedEnd = false
                controller.play()
                playbackSuccess(track, MusicTrackPlaybackAction.PLAY)
            }
        }
    }

    fun stop(track: MusicTrackState): MusicTrackPlaybackResult {
        val controller = track.controller ?: return playbackFailure(
            track = track,
            failure = MusicTrackPlaybackFailure.NoActiveTrack(track.uri),
        )

        if (!controller.isUsable()) {
            return playbackFailure(
                track = track,
                failure = MusicTrackPlaybackFailure.Unavailable(track.uri, controller.status()),
            )
        }

        track.reachedEnd = false
        controller.stop()
        return playbackSuccess(track, MusicTrackPlaybackAction.STOP)
    }

    fun stopAll() {
        trackStates.forEach { track ->
            if (track.controller?.hasPlayer() == true) {
                stop(track)
            }
        }
    }

    fun addTrack(): Boolean {
        if (trackStates.size >= MusicPlugin.MAX_TRACK_COUNT) return false
        trackStates += MusicTrackState()
        persistSettings()
        return true
    }

    fun removeTrack(index: Int): Boolean {
        if (trackStates.size <= 1 || index !in trackStates.indices) return false
        disposeTrack(trackStates.removeAt(index))
        persistSettings()
        return true
    }

    fun reorderTracks(fromIndex: Int, toIndex: Int): Boolean {
        if (!ReorderSupport.reorderMutableListFromDrop(trackStates, fromIndex, toIndex)) return false
        persistSettings()
        return true
    }

    fun persistSettings() {
        settingsStore.save(exportSettings())
    }

    fun shutdown() {
        trackStates.forEach(::disposeTrack)
        persistSettings()
    }

    fun exportSettings(): MusicSettings {
        initializeIfNeeded()
        return MusicSettings(
            masterVolume = masterVolume,
            tracks = trackStates.map { track ->
                PersistedMusicTrack(
                    uri = track.uri,
                    volume = track.volume,
                    loop = track.loop,
                )
            },
        )
    }

    fun replaceSettings(settings: MusicSettings) {
        initializeIfNeeded()
        trackStates.forEach(::disposeTrack)
        masterVolume = settings.masterVolume.coerceIn(0.0, 1.0)
        trackStates.clear()
        trackStates += settings.tracks
            .take(MusicPlugin.MAX_TRACK_COUNT)
            .ifEmpty { listOf(PersistedMusicTrack()) }
            .map { persisted ->
                MusicTrackState(
                    uri = persisted.uri,
                    volume = persisted.volume.coerceIn(0.0, 1.0),
                    loop = persisted.loop,
                )
            }
        persistSettings()
    }

    fun fileNameFromUri(uri: String): String =
        LocalFiles.fileName(uri)
            ?: uri.substringAfterLast('/')
                .ifBlank { "Loaded track" }

    private fun startTrackLoad(
        track: MusicTrackState,
        uri: String,
        clearAssignedTrackOnFailure: Boolean,
        persistOnActivation: Boolean,
    ): MusicTrackLoadResult {
        val previousUri = track.uri
        val previousController = track.controller
        val previousReachedEnd = track.reachedEnd
        val loadGeneration = ++track.loadGeneration

        track.pendingController?.dispose()
        track.pendingController = null

        val controller = controllerFactory()
        track.pendingController = controller
        track.reachedEnd = false

        val preserveActiveTrack = previousController?.hasPlayer() == true
        if (!preserveActiveTrack) {
            emitSnapshotChanged(track)
        }

        val hasResolved = AtomicBoolean(false)
        controller.bindCallbacks(
            onReady = {
                if (!hasResolved.compareAndSet(false, true)) return@bindCallbacks
                if (!isLatestPendingLoad(track, loadGeneration, controller)) {
                    controller.dispose()
                    return@bindCallbacks
                }

                track.pendingController = null
                track.controller = controller
                track.uri = uri
                track.reachedEnd = false
                previousController?.dispose()
                bindActiveController(track, controller)
                if (persistOnActivation) {
                    persistSettings()
                }

                val result = MusicTrackLoadResult.Activated(
                    requestedUri = uri,
                    preservedActiveTrack = preserveActiveTrack,
                    snapshot = snapshotOf(track),
                )
                emitSnapshotChanged(track, result.snapshot)
                listener?.onTrackLoadResult(track, result)
            },
            onError = {
                if (!hasResolved.compareAndSet(false, true)) return@bindCallbacks
                if (!isLatestPendingLoad(track, loadGeneration, controller)) {
                    controller.dispose()
                    return@bindCallbacks
                }

                finalizeLoadFailure(
                    track = track,
                    requestedUri = uri,
                    previousUri = previousUri,
                    previousController = previousController,
                    previousReachedEnd = previousReachedEnd,
                    pendingController = controller,
                    failure = MusicTrackLoadFailure.PlayerError(uri),
                    preserveActiveTrack = preserveActiveTrack,
                    clearAssignedTrackOnFailure = clearAssignedTrackOnFailure,
                )
            },
        )

        val loaded = controller.load(
            uri = uri,
            volume = effectiveVolume(track),
            cycleCount = cycleCountFor(track),
        )
        if (loaded) {
            return MusicTrackLoadResult.Started(
                requestedUri = uri,
                preservedActiveTrack = preserveActiveTrack,
            )
        }

        if (!isLatestPendingLoad(track, loadGeneration, controller)) {
            controller.dispose()
            return MusicTrackLoadResult.Failed(
                requestedUri = uri,
                preservedActiveTrack = preserveActiveTrack,
                restoredPreviousTrack = preserveActiveTrack,
                failure = MusicTrackLoadFailure.UnsupportedSource(uri),
                snapshot = snapshotOf(track),
            )
        }

        return finalizeLoadFailure(
            track = track,
            requestedUri = uri,
            previousUri = previousUri,
            previousController = previousController,
            previousReachedEnd = previousReachedEnd,
            pendingController = controller,
            failure = MusicTrackLoadFailure.UnsupportedSource(uri),
            preserveActiveTrack = preserveActiveTrack,
            clearAssignedTrackOnFailure = clearAssignedTrackOnFailure,
        )
    }

    private fun finalizeLoadFailure(
        track: MusicTrackState,
        requestedUri: String,
        previousUri: String?,
        previousController: MediaTrackController?,
        previousReachedEnd: Boolean,
        pendingController: MediaTrackController,
        failure: MusicTrackLoadFailure,
        preserveActiveTrack: Boolean,
        clearAssignedTrackOnFailure: Boolean,
    ): MusicTrackLoadResult.Failed {
        track.pendingController = null
        pendingController.dispose()

        val restoredPreviousTrack = previousController?.hasPlayer() == true
        track.controller = previousController
        track.reachedEnd = previousReachedEnd

        if (restoredPreviousTrack || !clearAssignedTrackOnFailure) {
            track.uri = previousUri
        } else {
            track.uri = null
            track.reachedEnd = false
            persistSettings()
        }

        val result = MusicTrackLoadResult.Failed(
            requestedUri = requestedUri,
            preservedActiveTrack = preserveActiveTrack,
            restoredPreviousTrack = restoredPreviousTrack,
            failure = failure,
            snapshot = snapshotOf(track),
        )
        emitSnapshotChanged(track, result.snapshot)
        listener?.onTrackLoadResult(track, result)
        return result
    }

    private fun bindActiveController(track: MusicTrackState, controller: MediaTrackController) {
        controller.bindCallbacks(
            onReady = {
                track.reachedEnd = false
                emitSnapshotChanged(track)
            },
            onProgress = { _, _ ->
                emitSnapshotChanged(track)
            },
            onError = {
                track.reachedEnd = false
                val result = playbackFailure(
                    track = track,
                    failure = MusicTrackPlaybackFailure.PlayerError(track.uri),
                    notifyListener = false,
                )
                emitSnapshotChanged(track, result.snapshot)
                listener?.onTrackPlaybackFailure(track, result)
            },
            onEndOfMedia = {
                if (!track.loop) {
                    track.reachedEnd = true
                }
                emitSnapshotChanged(track)
            },
        )
    }

    private fun playbackSuccess(
        track: MusicTrackState,
        action: MusicTrackPlaybackAction,
    ): MusicTrackPlaybackResult.Success {
        val result = MusicTrackPlaybackResult.Success(
            action = action,
            snapshot = snapshotOf(track),
        )
        emitSnapshotChanged(track, result.snapshot)
        return result
    }

    private fun playbackFailure(
        track: MusicTrackState,
        failure: MusicTrackPlaybackFailure,
        notifyListener: Boolean = true,
    ): MusicTrackPlaybackResult.Failed {
        val result = MusicTrackPlaybackResult.Failed(
            failure = failure,
            snapshot = snapshotOf(track),
        )
        if (notifyListener) {
            listener?.onTrackPlaybackFailure(track, result)
        }
        return result
    }

    private fun emitSnapshotChanged(track: MusicTrackState, snapshot: MusicTrackSnapshot = snapshotOf(track)) {
        listener?.onTrackSnapshotChanged(track, snapshot)
    }

    private fun effectiveVolume(track: MusicTrackState): Double = masterVolume * track.volume

    private fun cycleCountFor(track: MusicTrackState): Int = if (track.loop) MediaPlayer.INDEFINITE else 1

    private fun isLatestPendingLoad(
        track: MusicTrackState,
        loadGeneration: Long,
        controller: MediaTrackController,
    ): Boolean = loadGeneration == track.loadGeneration && track.pendingController === controller

    private fun disposeTrack(track: MusicTrackState) {
        track.pendingController?.dispose()
        track.pendingController = null
        track.controller?.dispose()
        track.controller = null
        track.reachedEnd = false
    }
}

internal class MusicTrackState internal constructor(
    var uri: String? = null,
    var volume: Double = 1.0,
    var loop: Boolean = true,
) {
    internal var controller: MediaTrackController? = null
    internal var pendingController: MediaTrackController? = null
    internal var loadGeneration: Long = 0
    internal var reachedEnd: Boolean = false
}

internal data class MusicTrackSnapshot(
    val uri: String?,
    val phase: MusicTrackPlaybackPhase,
    val status: MediaTrackStatus?,
    val isUsable: Boolean,
    val isLoading: Boolean,
    val currentTime: Duration?,
    val totalDuration: Duration?,
)

internal enum class MusicTrackPlaybackPhase {
    UNLOADED,
    READY,
    PLAYING,
    PAUSED,
    STOPPED,
    ENDED,
    UNAVAILABLE,
}

internal sealed interface MusicTrackLoadResult {
    val requestedUri: String
    val preservedActiveTrack: Boolean

    data class Started(
        override val requestedUri: String,
        override val preservedActiveTrack: Boolean,
    ) : MusicTrackLoadResult

    data class Activated(
        override val requestedUri: String,
        override val preservedActiveTrack: Boolean,
        val snapshot: MusicTrackSnapshot,
    ) : MusicTrackLoadResult

    data class Failed(
        override val requestedUri: String,
        override val preservedActiveTrack: Boolean,
        val restoredPreviousTrack: Boolean,
        val failure: MusicTrackLoadFailure,
        val snapshot: MusicTrackSnapshot,
    ) : MusicTrackLoadResult
}

internal sealed interface MusicTrackLoadFailure {
    val uri: String

    data class UnsupportedSource(override val uri: String) : MusicTrackLoadFailure

    data class PlayerError(override val uri: String) : MusicTrackLoadFailure
}

internal sealed interface MusicTrackPlaybackResult {
    data class Success(
        val action: MusicTrackPlaybackAction,
        val snapshot: MusicTrackSnapshot,
    ) : MusicTrackPlaybackResult

    data class Failed(
        val failure: MusicTrackPlaybackFailure,
        val snapshot: MusicTrackSnapshot,
    ) : MusicTrackPlaybackResult
}

internal enum class MusicTrackPlaybackAction {
    PLAY,
    PAUSE,
    STOP,
}

internal sealed interface MusicTrackPlaybackFailure {
    data class NoActiveTrack(val uri: String?) : MusicTrackPlaybackFailure

    data class Unavailable(val uri: String?, val status: MediaTrackStatus?) : MusicTrackPlaybackFailure

    data class PlayerError(val uri: String?) : MusicTrackPlaybackFailure
}

internal interface MusicTrackSettingsStore {
    fun load(): MusicSettings

    fun save(settings: MusicSettings)
}

internal object SerializerMusicTrackSettingsStore : MusicTrackSettingsStore {
    override fun load(): MusicSettings = MusicSettingsSerializer.load()

    override fun save(settings: MusicSettings) {
        MusicSettingsSerializer.save(settings)
    }
}
