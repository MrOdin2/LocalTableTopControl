package com.tabletopcontrol.audio

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.scene.SceneParticipant
import com.tabletopcontrol.core.ui.ContextMenuRenderer
import com.tabletopcontrol.core.ui.DragDropContext
import com.tabletopcontrol.core.ui.DragDropSupport
import com.tabletopcontrol.core.ui.DropIndicator
import com.tabletopcontrol.core.ui.GrabHandle
import com.tabletopcontrol.core.ui.InputHelpers.Companion.configureSliderDeferredCommit
import com.tabletopcontrol.core.ui.MenuAction
import com.tabletopcontrol.core.ui.MenuSection
import com.tabletopcontrol.core.ui.dialog.AudioFileChooserDialog
import com.tabletopcontrol.core.ui.dialog.FileChooserHistoryStore
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.control.Slider
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.util.Duration
import java.io.File
import java.net.URI
import java.net.URISyntaxException

/**
 * DM-panel plugin for layered music control.
 *
 * The plugin stays responsible for building the music cards and wiring UI events, while
 * [MusicTrackService] owns track persistence plus media-load lifecycle orchestration.
 */
class MusicPlugin : DmPlugin, SceneParticipant {

    override val displayName: String = "Music"
    override val sceneKey: String = "music"
    override val sceneDisplayName: String = displayName
    override val sceneLoadOrder: Int = 300

    companion object {
        /** Maximum number of simultaneous music tracks. */
        const val MAX_TRACK_COUNT = MAX_MUSIC_TRACKS

        private const val TIME_UNKNOWN = "--:--"
        private const val MUSIC_BROWSER_HISTORY_KEY = "music.browser"
    }

    private val trackService = MusicTrackService()
    private val trackBindings = mutableMapOf<MusicTrackState, TrackCardBindings>()
    private val trackListener = object : MusicTrackService.Listener {
        override fun onTrackSnapshotChanged(track: MusicTrackState, snapshot: MusicTrackSnapshot) {
            applyTrackSnapshot(track, snapshot)
        }

        override fun onTrackLoadResult(track: MusicTrackState, result: MusicTrackLoadResult) {
            if (result is MusicTrackLoadResult.Failed) {
                applyLoadFailureTooltip(track, result)
            }
        }

        override fun onTrackPlaybackFailure(track: MusicTrackState, result: MusicTrackPlaybackResult.Failed) {
            applyPlaybackFailureTooltip(track, result.failure)
        }
    }

    /** Container that holds reorderable track cards. */
    private lateinit var tracksContainer: VBox

    /** "Add Track" button (disabled when [MAX_TRACK_COUNT] is reached). */
    private lateinit var addTrackButton: Button

    /** Shared drop indicator for drag/drop reordering visuals. */
    private lateinit var dropIndicator: DropIndicator

    override fun createView(): Node {
        trackService.listener = trackListener
        trackService.initializeIfNeeded()

        val root = VBox(6.0).apply { padding = Insets(8.0) }
        tracksContainer = VBox(6.0)
        dropIndicator = DropIndicator()
        addTrackButton = Button("+ Add Track").apply {
            tooltip = Tooltip("Add another music track (up to $MAX_TRACK_COUNT)")
            setOnAction {
                if (trackService.addTrack()) {
                    rebuildTrackCards()
                }
            }
        }

        root.children.addAll(
            Label("Music Controls"),
            Separator(),
            buildMasterSection(),
            Separator(),
            HBox(addTrackButton),
            tracksContainer,
        )

        rebuildTrackCards()

        return ScrollPane(root).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        }
    }

    override fun onShutdown() {
        trackService.listener = null
        trackBindings.clear()
        trackService.shutdown()
    }

    override fun captureSceneState(): String = MusicSettingsSerializer.serialize(trackService.exportSettings())

    override fun applySceneState(payload: String) {
        val settings = requireNotNull(MusicSettingsSerializer.deserialize(payload)) {
            "Invalid music scene payload"
        }
        trackService.replaceSettings(settings)
    }

    // -------------------------------------------------------------------------
    // Section builders
    // -------------------------------------------------------------------------

    /**
     * Builds the master row: a volume slider that scales all tracks, and a
     * "Stop All" button that halts every active player.
     */
    private fun buildMasterSection(): HBox {
        val slider = Slider(0.0, 1.0, trackService.masterVolume).apply {
            isShowTickMarks = false
            tooltip = Tooltip("Master volume – scales all tracks proportionally")
            maxWidth = Double.MAX_VALUE
            configureSliderDeferredCommit(
                slider = this,
                onValueChanged = { newValue ->
                    trackService.setMasterVolume(newValue.toDouble())
                },
                onCommit = trackService::persistSettings,
            )
        }

        val stopAllBtn = Button("⏹ Stop All").apply {
            tooltip = Tooltip("Stop all currently playing tracks")
            setOnAction {
                trackService.stopAll()
            }
        }

        return HBox(8.0, Label("Master Vol:"), slider, stopAllBtn).apply {
            HBox.setHgrow(slider, Priority.ALWAYS)
        }
    }

    /** Builds the control content for a single track. */
    private fun buildTrackCard(index: Int, track: MusicTrackState): TrackCardNodes {
        val pathLabel = Label("No file loaded").apply {
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip("No file loaded")
        }

        val playPauseBtn = Button("▶ Play")
        val stopBtn = Button("⏹ Stop")
        val loopCheck = CheckBox("Loop").apply { isSelected = track.loop }

        val progressBar = ProgressBar(0.0).apply {
            maxWidth = Double.MAX_VALUE
            prefHeight = 12.0
        }
        val timeLabel = Label("$TIME_UNKNOWN / $TIME_UNKNOWN").apply {
            style = "-fx-font-size: 10;"
            tooltip = Tooltip("Elapsed / remaining")
        }

        val volumeSlider = Slider(0.0, 1.0, track.volume).apply {
            tooltip = Tooltip("Volume for ${index + 1}")
            maxWidth = Double.MAX_VALUE
            configureSliderDeferredCommit(
                slider = this,
                onValueChanged = { newValue ->
                    trackService.setTrackVolume(track, newValue.toDouble())
                },
                onCommit = trackService::persistSettings,
            )
        }

        val browseBtn = Button("Browse…").apply {
            setOnAction { evt ->
                val owner = (evt.source as? Button)?.scene?.window
                val file = AudioFileChooserDialog.showOpenDialog(
                    owner = owner,
                    title = "Select audio file for card ${index + 1}",
                    audioPatterns = listOf("*.mp3", "*.wav", "*.aac", "*.m4a", "*.ogg"),
                    historyKey = MUSIC_BROWSER_HISTORY_KEY,
                    fallbackSelection = FileChooserHistoryStore.fileFromUri(track.uri),
                ) ?: return@setOnAction
                trackService.loadSelectedTrack(track, file.toURI().toString())
            }
        }

        playPauseBtn.setOnAction {
            handlePlaybackResult(track, trackService.togglePlayback(track))
        }

        stopBtn.setOnAction {
            handlePlaybackResult(track, trackService.stop(track))
        }

        loopCheck.setOnAction {
            trackService.setTrackLoop(track, loopCheck.isSelected)
        }

        val grabHandle = GrabHandle()
        val fileRow = HBox(4.0, grabHandle, browseBtn, pathLabel).apply {
            HBox.setHgrow(pathLabel, Priority.ALWAYS)
        }
        val controlRow = HBox(4.0, playPauseBtn, stopBtn, loopCheck)
        val progressRow = HBox(6.0, progressBar, timeLabel).apply {
            HBox.setHgrow(progressBar, Priority.ALWAYS)
        }
        val volRow = HBox(8.0, Label("Volume:"), volumeSlider).apply {
            HBox.setHgrow(volumeSlider, Priority.ALWAYS)
        }

        val card = VBox(4.0, fileRow, controlRow, progressRow, volRow).apply {
            padding = Insets(6.0)
            style = "-fx-border-color: -tc-border; -fx-border-radius: 6; -fx-background-radius: 6;"
        }

        val contextMenu = ContextMenuRenderer.build(
            actions = listOf(
                MenuAction(
                    id = "music.remove-track",
                    label = "Remove Track",
                    icon = "🗑",
                    section = MenuSection.DANGER_ZONE,
                    isEnabled = trackService.tracks.size > 1,
                    requiresConfirmation = true,
                    confirmationMessage = "Remove this music track?",
                    onAction = {
                        if (trackService.removeTrack(index)) {
                            rebuildTrackCards()
                        }
                    },
                ),
            ),
        )
        card.setOnContextMenuRequested { event ->
            contextMenu.show(card, event.screenX, event.screenY)
            event.consume()
        }

        trackBindings[track] = TrackCardBindings(
            pathLabel = pathLabel,
            playPauseBtn = playPauseBtn,
            stopBtn = stopBtn,
            progressBar = progressBar,
            timeLabel = timeLabel,
        )
        applyTrackSnapshot(track, trackService.snapshotOf(track))
        trackService.restoreTrack(track)

        return TrackCardNodes(card = card, grabHandle = grabHandle)
    }

    // -------------------------------------------------------------------------
    // Track management
    // -------------------------------------------------------------------------

    private fun rebuildTrackCards() {
        trackBindings.clear()
        tracksContainer.children.clear()
        tracksContainer.children.add(dropIndicator)

        val dragContext = DragDropContext(
            dataFormat = "tabletopcontrol/music-track-card",
            onReorder = { from, to ->
                if (trackService.reorderTracks(from, to)) {
                    rebuildTrackCards()
                }
            },
        )

        trackService.tracks.forEachIndexed { index, track ->
            val cardNodes = buildTrackCard(index, track)
            DragDropSupport.installDragSource(cardNodes.grabHandle, index, dragContext)
            DragDropSupport.installDropTarget(cardNodes.card, index, dragContext, dropIndicator)
            tracksContainer.children.add(cardNodes.card)
        }

        val endDropTarget = Region().apply {
            minHeight = 18.0
            prefHeight = 18.0
            maxWidth = Double.MAX_VALUE
            isPickOnBounds = true
        }
        DragDropSupport.installDropTarget(endDropTarget, trackService.tracks.size, dragContext, dropIndicator)
        tracksContainer.children.add(endDropTarget)

        addTrackButton.isDisable = trackService.tracks.size >= MAX_TRACK_COUNT
    }

    private fun applyTrackSnapshot(track: MusicTrackState, snapshot: MusicTrackSnapshot) {
        val bindings = trackBindings[track] ?: return
        val activeUri = snapshot.uri

        bindings.pathLabel.text = activeUri?.let(trackService::fileNameFromUri) ?: "No file loaded"
        bindings.pathLabel.tooltip = Tooltip(activeUri?.let(::filePathOrRaw) ?: "No file loaded")

        if (!snapshot.isUsable) {
            bindings.playPauseBtn.isDisable = true
            bindings.playPauseBtn.text = "▶ Play"
            bindings.stopBtn.isDisable = true
            bindings.progressBar.progress = 0.0
            bindings.progressBar.isDisable = true
            bindings.timeLabel.text = "$TIME_UNKNOWN / $TIME_UNKNOWN"
            return
        }

        bindings.playPauseBtn.isDisable = false
        bindings.stopBtn.isDisable = false
        bindings.progressBar.isDisable = false
        bindings.playPauseBtn.text = if (snapshot.phase == MusicTrackPlaybackPhase.PLAYING) "⏸ Pause" else "▶ Play"

        val totalDuration = snapshot.totalDuration
        if (totalDuration != null && !totalDuration.isUnknown && !totalDuration.isIndefinite) {
            val current = snapshot.currentTime ?: Duration.ZERO
            val totalSeconds = totalDuration.toSeconds()
            if (totalSeconds > 0.0) {
                bindings.progressBar.progress = (current.toSeconds() / totalSeconds).coerceIn(0.0, 1.0)
                val remaining = totalDuration.subtract(current)
                bindings.timeLabel.text = "${formatDuration(current)} / -${formatDuration(remaining)}"
            } else {
                bindings.progressBar.progress = 0.0
                bindings.timeLabel.text = "${formatDuration(current)} / $TIME_UNKNOWN"
            }
        } else {
            val current = snapshot.currentTime ?: Duration.ZERO
            bindings.progressBar.progress = 0.0
            bindings.timeLabel.text = "${formatDuration(current)} / $TIME_UNKNOWN"
        }
    }

    private fun handlePlaybackResult(track: MusicTrackState, result: MusicTrackPlaybackResult) {
        when (result) {
            is MusicTrackPlaybackResult.Success -> applyTrackSnapshot(track, result.snapshot)
            is MusicTrackPlaybackResult.Failed -> {
                applyTrackSnapshot(track, result.snapshot)
                applyPlaybackFailureTooltip(track, result.failure)
            }
        }
    }

    private fun applyLoadFailureTooltip(track: MusicTrackState, result: MusicTrackLoadResult.Failed) {
        val bindings = trackBindings[track] ?: return
        val attemptedPath = filePathOrRaw(result.requestedUri)
        val activePath = result.snapshot.uri?.let(::filePathOrRaw)

        val message = when {
            activePath != null && result.restoredPreviousTrack ->
                "Failed to load audio file:\n$attemptedPath\n\nStill loaded:\n$activePath"

            activePath != null ->
                "Failed to load audio file:\n$attemptedPath\n\nCurrent track:\n$activePath"

            else ->
                "Failed to load audio file:\n$attemptedPath\n\nNo track currently loaded"
        }
        bindings.pathLabel.tooltip = Tooltip(message)
    }

    private fun applyPlaybackFailureTooltip(track: MusicTrackState, failure: MusicTrackPlaybackFailure) {
        val bindings = trackBindings[track] ?: return
        val message = when (failure) {
            is MusicTrackPlaybackFailure.NoActiveTrack ->
                "No audio file is currently loaded for this track."

            is MusicTrackPlaybackFailure.PlayerError -> {
                val path = failure.uri?.let(::filePathOrRaw) ?: "this track"
                "Playback failed for:\n$path"
            }

            is MusicTrackPlaybackFailure.Unavailable -> {
                val path = failure.uri?.let(::filePathOrRaw) ?: "this track"
                "Track is unavailable for playback:\n$path"
            }
        }
        bindings.pathLabel.tooltip = Tooltip(message)
    }

    /** Formats a [Duration] as `M:SS`, or [TIME_UNKNOWN] for unknown/indefinite durations. */
    private fun formatDuration(duration: Duration?): String {
        if (duration == null || duration.isUnknown || duration.isIndefinite) return TIME_UNKNOWN
        val totalSecs = duration.toSeconds().toLong().coerceAtLeast(0L)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return "$mins:${secs.toString().padStart(2, '0')}"
    }

    private fun filePathOrRaw(uri: String): String = try {
        File(URI(uri)).absolutePath
    } catch (_: URISyntaxException) {
        uri
    } catch (_: IllegalArgumentException) {
        uri
    }

    private data class TrackCardBindings(
        val pathLabel: Label,
        val playPauseBtn: Button,
        val stopBtn: Button,
        val progressBar: ProgressBar,
        val timeLabel: Label,
    )

    private data class TrackCardNodes(
        val card: VBox,
        val grabHandle: GrabHandle,
    )
}
