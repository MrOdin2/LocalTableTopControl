package com.tabletopcontrol.audio

import com.tabletopcontrol.audio.shared.MediaTrackController
import com.tabletopcontrol.audio.shared.MediaTrackStatus
import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.ui.ContextMenuRenderer
import com.tabletopcontrol.core.ui.DragDropContext
import com.tabletopcontrol.core.ui.DragDropSupport
import com.tabletopcontrol.core.ui.DropIndicator
import com.tabletopcontrol.core.ui.GrabHandle
import com.tabletopcontrol.core.ui.MenuAction
import com.tabletopcontrol.core.ui.MenuSection
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
import javafx.scene.media.MediaPlayer
import javafx.stage.FileChooser
import javafx.util.Duration
import java.io.File
import java.net.URI
import java.net.URISyntaxException

/**
 * DM-panel plugin for layered music control.
 *
 * Provides an ordered list of independent music tracks, each with:
 * - .mp3 (and other audio format) file selection
 * - play/pause, stop, and loop controls
 * - per-track volume slider
 * - per-track progress bar and remaining-time display
 *
 * A master row contains a volume slider and a "Stop All" button that halts
 * every track simultaneously. Track cards can be added, reordered via drag/drop,
 * and removed from a right-click context menu.
 *
 * GODCLASS audit note:
 * - `MusicPlugin` shows mixed responsibilities and is a near-god-class candidate.
 * - Features that can be moved to helpers/shared components:
 *   - Track persistence and settings serialization orchestration (`saveSettings`, load in `createView`).
 *   - Media player lifecycle/binding (`loadTrack`, `bindPlayerToControls`, dispose helpers).
 *   - Reorder/add/remove orchestration for tracks and drag-drop setup.
 *   - Reusable track-card UI construction and section composition.
 */
class MusicPlugin : DmPlugin {

    override val displayName: String = "Music"

    companion object {
        /** Maximum number of simultaneous music tracks. */
        const val MAX_TRACK_COUNT = MAX_MUSIC_TRACKS

        private const val TIME_UNKNOWN = "--:--"
    }

    /** Current master volume level in the range 0.0–1.0. */
    private var masterVolume: Double = 1.0

    /** Ordered list of track states. */
    private val tracks = mutableListOf<TrackState>()

    /** Container that holds reorderable track cards. */
    private lateinit var tracksContainer: VBox

    /** "Add Track" button (disabled when [MAX_TRACK_COUNT] is reached). */
    private lateinit var addTrackButton: Button

    /** Shared drop indicator for drag/drop reordering visuals. */
    private lateinit var dropIndicator: DropIndicator

    /** Guard flag: state is loaded from disk and players are created only on the first [createView] call. */
    private var initialized = false

    override fun createView(): Node {
        if (!initialized) {
            val loaded = MusicSettingsSerializer.load()
            masterVolume = loaded.masterVolume
            tracks.clear()
            tracks += loaded.tracks
                .take(MAX_TRACK_COUNT)
                .ifEmpty { listOf(PersistedMusicTrack()) }
                .map { TrackState(uri = it.uri, volume = it.volume, loop = it.loop) }
            initialized = true
        }

        val root = VBox(6.0).apply { padding = Insets(8.0) }
        tracksContainer = VBox(6.0)
        dropIndicator = DropIndicator()
        addTrackButton = Button("+ Add Track").apply {
            tooltip = Tooltip("Add another music track (up to $MAX_TRACK_COUNT)")
            setOnAction { addTrack() }
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
        tracks.forEach(::disposeTrackPlayer)
        saveSettings()
    }

    // -------------------------------------------------------------------------
    // Section builders
    // -------------------------------------------------------------------------

    /**
     * Builds the master row: a volume slider that scales all tracks, and a
     * "Stop All" button that stops every active player.
     */
    private fun buildMasterSection(): HBox {
        val slider = Slider(0.0, 1.0, masterVolume).apply {
            isShowTickMarks = false
            tooltip = Tooltip("Master volume – scales all tracks proportionally")
            maxWidth = Double.MAX_VALUE
            configureSliderDeferredSave(this) { newValue ->
                masterVolume = newValue.toDouble()
                tracks.forEach { track -> track.controller?.setVolume(masterVolume * track.volume) }
            }
        }

        val stopAllBtn = Button("⏹ Stop All").apply {
            tooltip = Tooltip("Stop all currently playing tracks")
            setOnAction {
                tracks.forEach { track ->
                    track.controller?.stop()
                    track.playPauseBtn?.text = "▶ Play"
                }
            }
        }

        return HBox(8.0, Label("Master Vol:"), slider, stopAllBtn).apply {
            HBox.setHgrow(slider, Priority.ALWAYS)
        }
    }

    /** Builds the control content for a single track. */
    private fun buildTrackCard(index: Int, track: TrackState): TrackCardNodes {
        val pathLabel = Label(track.uri?.let(::fileNameFromUri) ?: "No file loaded").apply {
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip(track.uri ?: "No file loaded")
        }

        val playPauseBtn = Button("▶ Play").apply { isDisable = track.uri == null }
        val stopBtn = Button("⏹ Stop").apply { isDisable = track.uri == null }
        val loopCheck = CheckBox("Loop").apply { isSelected = track.loop }
        track.playPauseBtn = playPauseBtn

        val progressBar = ProgressBar(0.0).apply {
            maxWidth = Double.MAX_VALUE
            prefHeight = 12.0
            isDisable = track.uri == null
        }
        val timeLabel = Label("$TIME_UNKNOWN / $TIME_UNKNOWN").apply {
            style = "-fx-font-size: 10;"
            tooltip = Tooltip("Elapsed / remaining")
        }

        val volumeSlider = Slider(0.0, 1.0, track.volume).apply {
            tooltip = Tooltip("Volume for ${index + 1}")
            maxWidth = Double.MAX_VALUE
            configureSliderDeferredSave(this) { newValue ->
                track.volume = newValue.toDouble()
                track.controller?.setVolume(masterVolume * track.volume)
            }
        }

        val browseBtn = Button("Browse…").apply {
            setOnAction { evt ->
                val chooser = FileChooser().apply {
                    title = "Select audio file for card ${index + 1}"
                    extensionFilters.addAll(
                        FileChooser.ExtensionFilter("MP3 files", "*.mp3"),
                        FileChooser.ExtensionFilter(
                            "Audio files", "*.mp3", "*.wav", "*.aac", "*.m4a", "*.ogg",
                        ),
                        FileChooser.ExtensionFilter("All files", "*.*"),
                    )
                }
                val owner = (evt.source as? Button)?.scene?.window
                val file = chooser.showOpenDialog(owner)
                if (file != null) {
                    val selectedUri = file.toURI().toString()
                    val loaded = loadTrack(
                        track = track,
                        uri = selectedUri,
                        playPauseBtn = playPauseBtn,
                        stopBtn = stopBtn,
                        progressBar = progressBar,
                        timeLabel = timeLabel,
                    )
                    if (loaded) {
                        pathLabel.text = file.name
                        pathLabel.tooltip = Tooltip(file.absolutePath)
                        saveSettings()
                    } else {
                        pathLabel.text = "${file.name} (load failed)"
                        pathLabel.tooltip = Tooltip(
                            "Failed to load audio file:\n${file.absolutePath}",
                        )
                    }
                }
            }
        }

        playPauseBtn.setOnAction {
            val controller = track.controller ?: return@setOnAction
            when (controller.status()) {
                MediaTrackStatus.PLAYING -> {
                    controller.pause()
                    playPauseBtn.text = "▶ Play"
                }
                else -> {
                    controller.play()
                    playPauseBtn.text = "⏸ Pause"
                }
            }
        }

        stopBtn.setOnAction {
            track.controller?.stop()
            playPauseBtn.text = "▶ Play"
        }

        loopCheck.setOnAction {
            track.loop = loopCheck.isSelected
            track.controller?.setCycleCount(if (track.loop) MediaPlayer.INDEFINITE else 1)
            saveSettings()
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
                    isEnabled = tracks.size > 1,
                    requiresConfirmation = true,
                    confirmationMessage = "Remove this music track?",
                    onAction = { removeTrack(index) },
                ),
            ),
        )
        card.setOnContextMenuRequested { event ->
            contextMenu.show(card, event.screenX, event.screenY)
            event.consume()
        }

        if (track.uri != null) {
            if (track.controller?.hasPlayer() != true) {
                val existingUri = track.uri!!
                val loaded = loadTrack(
                    track = track,
                    uri = existingUri,
                    playPauseBtn = playPauseBtn,
                    stopBtn = stopBtn,
                    progressBar = progressBar,
                    timeLabel = timeLabel,
                )
                if (!loaded) {
                    track.controller?.dispose()
                    track.controller = null
                    track.uri = null
                    pathLabel.text = "No file loaded"
                    pathLabel.tooltip = Tooltip("No file loaded")
                    playPauseBtn.isDisable = true
                    stopBtn.isDisable = true
                    progressBar.progress = 0.0
                    progressBar.isDisable = true
                    timeLabel.text = ""
                    saveSettings()
                }
            } else {
                bindPlayerToControls(track, playPauseBtn, stopBtn, progressBar, timeLabel)
            }
        }

        return TrackCardNodes(card = card, grabHandle = grabHandle)
    }

    // -------------------------------------------------------------------------
    // Track management
    // -------------------------------------------------------------------------

    /**
     * Loads media for [track] through the shared [MediaTrackController].
     *
     * Any existing player for this track is disposed first.
     * While loading, controls are disabled and progress/time are reset.
     * On success, lifecycle handlers are (re)bound so status/progress/error/end
     * updates continue to drive [playPauseBtn], [stopBtn], [progressBar], and [timeLabel].
     */
    private fun loadTrack(
        track: TrackState,
        uri: String,
        playPauseBtn: Button,
        stopBtn: Button,
        progressBar: ProgressBar,
        timeLabel: Label,
    ): Boolean {
        val previousUri = track.uri
        val controller = track.controller ?: MediaTrackController()

        // Disable controls while the new media loads.
        resetTrackControls(playPauseBtn, stopBtn, progressBar, timeLabel)

        bindTrackCallbacks(track, controller, playPauseBtn, stopBtn, progressBar, timeLabel)
        val loaded = controller.load(
            uri = uri,
            volume = masterVolume * track.volume,
            cycleCount = if (track.loop) MediaPlayer.INDEFINITE else 1,
        )
        if (!loaded) {
            controller.dispose()
            track.controller = null
            track.uri = previousUri
            resetTrackControls(playPauseBtn, stopBtn, progressBar, timeLabel)
            return false
        }
        track.controller = controller
        bindPlayerToControls(track, playPauseBtn, stopBtn, progressBar, timeLabel)
        track.uri = uri
        return true
    }

    private fun resetTrackControls(
        playPauseBtn: Button,
        stopBtn: Button,
        progressBar: ProgressBar,
        timeLabel: Label,
    ) {
        playPauseBtn.isDisable = true
        playPauseBtn.text = "▶ Play"
        stopBtn.isDisable = true
        progressBar.progress = 0.0
        progressBar.isDisable = true
        timeLabel.text = "$TIME_UNKNOWN / $TIME_UNKNOWN"
    }

    private fun bindPlayerToControls(
        track: TrackState,
        playPauseBtn: Button,
        stopBtn: Button,
        progressBar: ProgressBar,
        timeLabel: Label,
    ) {
        val controller = track.controller ?: return
        val isUsable = controller.isUsable()
        playPauseBtn.isDisable = !isUsable
        stopBtn.isDisable = !isUsable
        progressBar.isDisable = !isUsable
        playPauseBtn.text = if (controller.status() == MediaTrackStatus.PLAYING) "⏸ Pause" else "▶ Play"
        val totalDuration = controller.duration()
        if (isUsable && totalDuration != null && !totalDuration.isUnknown && !totalDuration.isIndefinite) {
            val current = controller.currentTime() ?: Duration.ZERO
            val totalSeconds = totalDuration.toSeconds()
            if (totalSeconds > 0.0) {
                progressBar.progress = (current.toSeconds() / totalSeconds).coerceIn(0.0, 1.0)
            }
            val remaining = totalDuration.subtract(current)
            timeLabel.text = "${formatDuration(current)} / -${formatDuration(remaining)}"
        }
    }

    /**
     * Binds controller callbacks before loading so READY/ERROR events can't be missed.
     */
    private fun bindTrackCallbacks(
        track: TrackState,
        controller: MediaTrackController,
        playPauseBtn: Button,
        stopBtn: Button,
        progressBar: ProgressBar,
        timeLabel: Label,
    ) {
        controller.bindCallbacks(
            onReady = {
                playPauseBtn.isDisable = false
                stopBtn.isDisable = false
                progressBar.isDisable = false
                val total = controller.duration()
                timeLabel.text = if (total != null && !total.isUnknown && !total.isIndefinite && total.toSeconds() > 0.0) {
                    "0:00 / -${formatDuration(total)}"
                } else {
                    "0:00 / $TIME_UNKNOWN"
                }
            },
            onProgress = { current, total ->
                val totalSeconds = total.toSeconds()
                val frac = (current.toSeconds() / totalSeconds).coerceIn(0.0, 1.0)
                val remaining = total.subtract(current)
                progressBar.progress = frac
                timeLabel.text = "${formatDuration(current)} / -${formatDuration(remaining)}"
            },
            onError = {
                playPauseBtn.isDisable = true
                stopBtn.isDisable = true
                playPauseBtn.text = "▶ Play"
            },
            onEndOfMedia = {
                if (!track.loop) {
                    playPauseBtn.text = "▶ Play"
                }
            },
        )
    }

    private fun rebuildTrackCards() {
        tracks.forEach { it.playPauseBtn = null }
        tracksContainer.children.clear()
        tracksContainer.children.add(dropIndicator)

        val dragContext = DragDropContext(
            dataFormat = "tabletopcontrol/music-track-card",
            onReorder = { from, to -> reorderTracks(from, to) },
        )

        tracks.forEachIndexed { index, track ->
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
        DragDropSupport.installDropTarget(endDropTarget, tracks.size, dragContext, dropIndicator)
        tracksContainer.children.add(endDropTarget)

        addTrackButton.isDisable = tracks.size >= MAX_TRACK_COUNT
    }

    private fun addTrack() {
        if (tracks.size >= MAX_TRACK_COUNT) return
        tracks += TrackState()
        rebuildTrackCards()
        saveSettings()
    }

    private fun removeTrack(index: Int) {
        if (tracks.size <= 1 || index !in tracks.indices) return
        disposeTrackPlayer(tracks.removeAt(index))
        rebuildTrackCards()
        saveSettings()
    }

    private fun reorderTracks(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in tracks.indices || toIndex !in 0..tracks.size || fromIndex == toIndex) return
        val moved = tracks.removeAt(fromIndex)
        val adjustedToIndex = if (fromIndex < toIndex) toIndex - 1 else toIndex
        tracks.add(adjustedToIndex, moved)
        rebuildTrackCards()
        saveSettings()
    }

    /**
     * Configures deferred settings persistence for a volume [slider].
     *
     * [onValueChanged] receives the new slider value as [Number] and is invoked
     * immediately for runtime updates on every value change. [saveSettings] is
     * deferred and committed once interaction ends (drag release or focus loss)
     * to avoid frequent disk writes.
     */
    private fun configureSliderDeferredSave(slider: Slider, onValueChanged: (Number) -> Unit) {
        // Tracks whether the slider value changed during interaction so settings
        // are persisted once after interaction completes instead of every step.
        var pendingSave = false

        // Persists only when there is a pending change from slider interaction.
        fun persistIfChanged() {
            if (pendingSave) {
                pendingSave = false
                saveSettings()
            }
        }

        slider.valueProperty().addListener { _, _, newValue ->
            onValueChanged(newValue)
            pendingSave = true
        }
        slider.valueChangingProperty().addListener { _, wasChanging, isChanging ->
            if (wasChanging && !isChanging) {
                persistIfChanged()
            }
        }
        slider.focusedProperty().addListener { _, wasFocused, isFocused ->
            if (wasFocused && !isFocused) {
                persistIfChanged()
            }
        }
    }

    private fun saveSettings() {
        MusicSettingsSerializer.save(
            MusicSettings(
                masterVolume = masterVolume,
                tracks = tracks.map { track ->
                    PersistedMusicTrack(uri = track.uri, volume = track.volume, loop = track.loop)
                },
            ),
        )
    }

    private fun disposeTrackPlayer(track: TrackState) {
        track.controller?.dispose()
        track.controller = null
    }

    /** Formats a [Duration] as `M:SS`, or [TIME_UNKNOWN] for unknown/indefinite durations. */
    private fun formatDuration(duration: Duration?): String {
        if (duration == null || duration.isUnknown || duration.isIndefinite) return TIME_UNKNOWN
        val totalSecs = duration.toSeconds().toLong().coerceAtLeast(0L)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return "$mins:${secs.toString().padStart(2, '0')}"
    }

    private fun fileNameFromUri(uri: String): String = try {
        File(URI(uri)).name.ifBlank { "Loaded track" }
    } catch (_: URISyntaxException) {
        uri.substringAfterLast('/').ifBlank { "Loaded track" }
    } catch (_: IllegalArgumentException) {
        uri.substringAfterLast('/').ifBlank { "Loaded track" }
    }

    private data class TrackState(
        var uri: String? = null,
        var volume: Double = 1.0,
        var loop: Boolean = true,
        var controller: MediaTrackController? = null,
        var playPauseBtn: Button? = null,
    )

    private data class TrackCardNodes(
        val card: VBox,
        val grabHandle: GrabHandle,
    )
}
