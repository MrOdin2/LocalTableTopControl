package com.tabletopcontrol.audio

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.ui.ContextMenuRenderer
import com.tabletopcontrol.core.ui.DragDropContext
import com.tabletopcontrol.core.ui.DragDropSupport
import com.tabletopcontrol.core.ui.DropIndicator
import com.tabletopcontrol.core.ui.GrabHandle
import com.tabletopcontrol.core.ui.MenuAction
import com.tabletopcontrol.core.ui.MenuSection
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
 */
class MusicPlugin : DmPlugin {

    override val displayName: String = "Music"

    companion object {
        /** Maximum number of simultaneous music tracks. */
        const val MAX_TRACK_COUNT = 16

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
        tracks.forEach { it.player?.dispose() }
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
        tracks.forEach { it.player?.dispose() }
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
            valueProperty().addListener { _, _, newValue ->
                masterVolume = newValue.toDouble()
                tracks.forEach { track -> track.player?.volume = masterVolume * track.volume }
                saveSettings()
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
    private fun buildTrackCard(index: Int, track: TrackState): VBox {
        val trackNumberLabel = Label("Track ${index + 1}")
        val pathLabel = Label(track.uri?.let(::fileNameFromUri) ?: "No file loaded").apply {
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip(track.uri ?: "No file loaded")
        }

        val playPauseBtn = Button("▶ Play").apply { isDisable = track.uri == null }
        val stopBtn = Button("⏹ Stop").apply { isDisable = track.uri == null }
        val loopCheck = CheckBox("Loop").apply { isSelected = track.loop }
        track.playPauseBtn = playPauseBtn

        val grabHandle = GrabHandle()
        val headerRow = HBox(6.0, grabHandle, trackNumberLabel).apply {
            padding = Insets(2.0, 0.0, 0.0, 0.0)
        }

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
            tooltip = Tooltip("Volume for Track ${index + 1}")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newValue ->
                track.volume = newValue.toDouble()
                track.player?.volume = masterVolume * track.volume
                saveSettings()
            }
        }

        val browseBtn = Button("Browse…").apply {
            setOnAction { evt ->
                val chooser = FileChooser().apply {
                    title = "Select audio file for Track ${index + 1}"
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
                    track.uri = file.toURI().toString()
                    pathLabel.text = file.name
                    pathLabel.tooltip = Tooltip(file.absolutePath)
                    loadTrack(
                        track = track,
                        playPauseBtn, stopBtn, progressBar, timeLabel,
                    )
                    saveSettings()
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

        val fileRow = HBox(4.0, browseBtn, pathLabel).apply {
            HBox.setHgrow(pathLabel, Priority.ALWAYS)
        }
        val controlRow = HBox(4.0, playPauseBtn, stopBtn, loopCheck)
        val progressRow = HBox(6.0, progressBar, timeLabel).apply {
            HBox.setHgrow(progressBar, Priority.ALWAYS)
        }
        val volRow = HBox(8.0, Label("Volume:"), volumeSlider).apply {
            HBox.setHgrow(volumeSlider, Priority.ALWAYS)
        }

        val card = VBox(4.0, headerRow, fileRow, controlRow, progressRow, volRow).apply {
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
                loadTrack(track, playPauseBtn, stopBtn, progressBar, timeLabel)
            } else {
                bindPlayerToControls(track, playPauseBtn, stopBtn, progressBar, timeLabel)
            }
        }

        return card
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
        playPauseBtn: Button,
        stopBtn: Button,
        progressBar: ProgressBar,
        timeLabel: Label,
    ) {
        val uri = track.uri ?: return
        track.player?.dispose()
        track.player = null

        // Disable controls while the new media loads.
        playPauseBtn.isDisable = true
        playPauseBtn.text = "▶ Play"
        stopBtn.isDisable = true
        progressBar.progress = 0.0
        progressBar.isDisable = true
        timeLabel.text = "$TIME_UNKNOWN / $TIME_UNKNOWN"

        val media = try {
            Media(uri)
        } catch (e: Exception) {
            return
        }

        val player = MediaPlayer(media).apply {
            volume = masterVolume * track.volume
            cycleCount = if (track.loop) MediaPlayer.INDEFINITE else 1
        }

        track.player = player
        bindPlayerToControls(track, playPauseBtn, stopBtn, progressBar, timeLabel)
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
        playPauseBtn.isDisable = false
        stopBtn.isDisable = false
        progressBar.isDisable = false
        playPauseBtn.text = if (player.status == MediaPlayer.Status.PLAYING) "⏸ Pause" else "▶ Play"
        if (!media.duration.isUnknown && !media.duration.isIndefinite) {
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
                timeLabel.text = "0:00 / -${formatDuration(media.duration)}"
            }
        }

        // currentTimeProperty fires on the FX thread; no Platform.runLater needed.
        // Early firings (before media is READY) return immediately via the isUnknown guard.
        player.currentTimeProperty().addListener { _, _, current ->
            val total = media.duration ?: return@addListener
            if (total.isUnknown || total.isIndefinite) return@addListener
            val frac = (current.toSeconds() / total.toSeconds()).coerceIn(0.0, 1.0)
            val remaining = total.subtract(current)
            progressBar.progress = frac
            timeLabel.text = "${formatDuration(current)} / -${formatDuration(remaining)}"
        }

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
            val card = buildTrackCard(index, track)
            DragDropSupport.installDragSource(card, index, dragContext)
            DragDropSupport.installDropTarget(card, index, dragContext, dropIndicator)
            tracksContainer.children.add(card)
        }

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
        tracks.removeAt(index).player?.dispose()
        rebuildTrackCards()
        saveSettings()
    }

    private fun reorderTracks(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in tracks.indices || toIndex !in tracks.indices || fromIndex == toIndex) return
        val moved = tracks.removeAt(fromIndex)
        tracks.add(toIndex, moved)
        rebuildTrackCards()
        saveSettings()
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
    )
}
