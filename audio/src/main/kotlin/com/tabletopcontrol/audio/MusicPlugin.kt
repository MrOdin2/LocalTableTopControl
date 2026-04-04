package com.tabletopcontrol.audio

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.ui.ContextMenuRenderer
import com.tabletopcontrol.core.ui.DragDropContext
import com.tabletopcontrol.core.ui.DragDropSupport
import com.tabletopcontrol.core.ui.DropIndicator
import com.tabletopcontrol.core.ui.GrabHandle
import com.tabletopcontrol.core.ui.MenuAction
import com.tabletopcontrol.core.ui.MenuSection
import javafx.beans.value.ChangeListener
import javafx.application.Platform
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
import javafx.scene.media.Media
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

    override fun createView(): Node {
        tracks.forEach(::disposeTrackPlayer)
        val loaded = MusicSettingsSerializer.load()
        masterVolume = loaded.masterVolume
        tracks.clear()
        tracks += loaded.tracks
            .take(MAX_TRACK_COUNT)
            .ifEmpty { listOf(PersistedMusicTrack()) }
            .map { TrackState(uri = it.uri, volume = it.volume, loop = it.loop) }

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
                tracks.forEach { track -> track.player?.volume = masterVolume * track.volume }
            }
        }

        val stopAllBtn = Button("⏹ Stop All").apply {
            tooltip = Tooltip("Stop all currently playing tracks")
            setOnAction {
                tracks.forEach { track ->
                    track.player?.stop()
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
                track.player?.volume = masterVolume * track.volume
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
            val player = track.player ?: return@setOnAction
            when (player.status) {
                MediaPlayer.Status.PLAYING -> {
                    player.pause()
                    playPauseBtn.text = "▶ Play"
                }
                else -> {
                    player.play()
                    playPauseBtn.text = "⏸ Pause"
                }
            }
        }

        stopBtn.setOnAction {
            track.player?.stop()
            playPauseBtn.text = "▶ Play"
        }

        loopCheck.setOnAction {
            track.loop = loopCheck.isSelected
            track.player?.cycleCount = if (track.loop) MediaPlayer.INDEFINITE else 1
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
            if (track.player == null) {
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
                    track.player = null
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
     * Loads a new [MediaPlayer] for [track] from its configured URI.
     *
     * Any existing player for this track is stopped and disposed first.
     * Controls are re-enabled once the media reports [MediaPlayer.Status.READY].
     * The [progressBar] and [timeLabel] are updated in real time via
     * [MediaPlayer.currentTimeProperty].
     */
    private fun loadTrack(
        track: TrackState,
        uri: String,
        playPauseBtn: Button,
        stopBtn: Button,
        progressBar: ProgressBar,
        timeLabel: Label,
    ): Boolean {
        val media = try {
            Media(uri)
        } catch (_: Exception) {
            return false
        }

        disposeTrackPlayer(track)
        track.uri = uri

        // Disable controls while the new media loads.
        playPauseBtn.isDisable = true
        playPauseBtn.text = "▶ Play"
        stopBtn.isDisable = true
        progressBar.progress = 0.0
        progressBar.isDisable = true
        timeLabel.text = "$TIME_UNKNOWN / $TIME_UNKNOWN"

        val player = MediaPlayer(media).apply {
            volume = masterVolume * track.volume
            cycleCount = if (track.loop) MediaPlayer.INDEFINITE else 1
        }

        track.player = player
        bindPlayerToControls(track, playPauseBtn, stopBtn, progressBar, timeLabel)
        return true
    }

    private fun bindPlayerToControls(
        track: TrackState,
        playPauseBtn: Button,
        stopBtn: Button,
        progressBar: ProgressBar,
        timeLabel: Label,
    ) {
        val player = track.player ?: return
        val media = player.media ?: return
        val status = player.status
        val isUsable = status != MediaPlayer.Status.UNKNOWN &&
            status != MediaPlayer.Status.HALTED &&
            status != MediaPlayer.Status.DISPOSED
        playPauseBtn.isDisable = !isUsable
        stopBtn.isDisable = !isUsable
        progressBar.isDisable = !isUsable
        playPauseBtn.text = if (player.status == MediaPlayer.Status.PLAYING) "⏸ Pause" else "▶ Play"
        if (isUsable && !media.duration.isUnknown && !media.duration.isIndefinite) {
            val current = player.currentTime
            val totalSeconds = media.duration.toSeconds()
            if (totalSeconds > 0.0) {
                progressBar.progress = (current.toSeconds() / totalSeconds).coerceIn(0.0, 1.0)
            }
            val remaining = media.duration.subtract(current)
            timeLabel.text = "${formatDuration(current)} / -${formatDuration(remaining)}"
        }

        player.setOnReady {
            Platform.runLater {
                playPauseBtn.isDisable = false
                stopBtn.isDisable = false
                progressBar.isDisable = false
                val total = media.duration
                timeLabel.text = if (!total.isUnknown && !total.isIndefinite && total.toSeconds() > 0.0) {
                    "0:00 / -${formatDuration(total)}"
                } else {
                    "0:00 / $TIME_UNKNOWN"
                }
            }
        }

        // currentTimeProperty fires on the FX thread; no Platform.runLater needed.
        // Early firings (before media is READY) return immediately via the isUnknown guard.
        track.timeListener?.let(player.currentTimeProperty()::removeListener)
        val timeListener = ChangeListener<Duration> { _, _, current ->
            val total = media.duration
            if (total.isUnknown || total.isIndefinite) return@ChangeListener
            val totalSeconds = total.toSeconds()
            if (totalSeconds <= 0.0) {
                progressBar.progress = 0.0
                timeLabel.text = "${formatDuration(current)} / $TIME_UNKNOWN"
                return@ChangeListener
            }
            val frac = (current.toSeconds() / totalSeconds).coerceIn(0.0, 1.0)
            val remaining = total.subtract(current)
            progressBar.progress = frac
            timeLabel.text = "${formatDuration(current)} / -${formatDuration(remaining)}"
        }
        track.timeListener = timeListener
        player.currentTimeProperty().addListener(timeListener)

        player.setOnError {
            Platform.runLater {
                playPauseBtn.isDisable = true
                stopBtn.isDisable = true
                playPauseBtn.text = "▶ Play"
            }
        }

        player.setOnEndOfMedia {
            if (player.cycleCount != MediaPlayer.INDEFINITE) {
                Platform.runLater { playPauseBtn.text = "▶ Play" }
            }
        }
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
        val player = track.player ?: return
        track.timeListener?.let(player.currentTimeProperty()::removeListener)
        track.timeListener = null
        player.dispose()
        track.player = null
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
        var player: MediaPlayer? = null,
        var playPauseBtn: Button? = null,
        var timeListener: ChangeListener<Duration>? = null,
    )

    private data class TrackCardNodes(
        val card: VBox,
        val grabHandle: GrabHandle,
    )
}
