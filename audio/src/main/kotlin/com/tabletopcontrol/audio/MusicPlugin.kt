package com.tabletopcontrol.audio

import com.tabletopcontrol.core.DmPlugin
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
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.stage.FileChooser
import javafx.util.Duration

/**
 * DM-panel plugin for layered music control.
 *
 * Provides [TRACK_COUNT] independent music tracks, each with:
 * - .mp3 (and other audio format) file selection
 * - play/pause, stop, and loop controls
 * - per-track volume slider
 * - per-track progress bar and remaining-time display
 *
 * A master row contains a volume slider and a "Stop All" button that halts
 * every track simultaneously.  Each track is wrapped in a collapsible
 * [TitledPane] so the DM can hide inactive tracks to reclaim pane space.
 */
class MusicPlugin : DmPlugin {

    override val displayName: String = "Music"

    companion object {
        /** Number of independent music layers. */
        const val TRACK_COUNT = 3

        private const val TIME_UNKNOWN = "--:--"
    }

    /** Current master volume level in the range 0.0–1.0. */
    private var masterVolume: Double = 1.0

    /** One [MediaPlayer] per track; `null` when no file is loaded for that track. */
    private val players = arrayOfNulls<MediaPlayer>(TRACK_COUNT)

    /** Per-track volume levels in the range 0.0–1.0. */
    private val trackVolumes = DoubleArray(TRACK_COUNT) { 1.0 }

    /**
     * Per-track play/pause buttons – stored so the "Stop All" handler can reset
     * their labels without needing a direct reference to each track's closure.
     */
    private val playPauseBtns = arrayOfNulls<Button>(TRACK_COUNT)

    override fun createView(): Node {
        val root = VBox(6.0).apply { padding = Insets(8.0) }

        root.children.addAll(
            Label("Music Controls"),
            Separator(),
            buildMasterSection(),
            Separator(),
        )

        for (i in 0 until TRACK_COUNT) {
            root.children.add(buildTrackPane(i))
        }

        return ScrollPane(root).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        }
    }

    override fun onShutdown() {
        players.forEach { it?.dispose() }
    }

    // -------------------------------------------------------------------------
    // Section builders
    // -------------------------------------------------------------------------

    /**
     * Builds the master row: a volume slider that scales all tracks, and a
     * "Stop All" button that stops every active player.
     */
    private fun buildMasterSection(): HBox {
        val slider = Slider(0.0, 1.0, 1.0).apply {
            isShowTickMarks = false
            tooltip = Tooltip("Master volume – scales all tracks proportionally")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newValue ->
                masterVolume = newValue.toDouble()
                for (i in players.indices) {
                    players[i]?.volume = masterVolume * trackVolumes[i]
                }
            }
        }

        val stopAllBtn = Button("⏹ Stop All").apply {
            tooltip = Tooltip("Stop all currently playing tracks")
            setOnAction {
                for (i in players.indices) {
                    players[i]?.stop()
                    playPauseBtns[i]?.text = "▶ Play"
                }
            }
        }

        return HBox(8.0, Label("Master Vol:"), slider, stopAllBtn).apply {
            HBox.setHgrow(slider, Priority.ALWAYS)
        }
    }

    /**
     * Wraps one track's controls in a collapsible [TitledPane] so the DM can
     * hide tracks that are not currently in use and recover vertical space.
     */
    private fun buildTrackPane(index: Int): TitledPane =
        TitledPane("Track ${index + 1}", buildTrackContent(index)).apply {
            isCollapsible = true
            isExpanded = true
        }

    /** Builds the control content for a single track. */
    private fun buildTrackContent(index: Int): VBox {
        val pathLabel = Label("No file loaded").apply {
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip("No file loaded")
        }

        val playPauseBtn = Button("▶ Play").apply { isDisable = true }
        val stopBtn = Button("⏹ Stop").apply { isDisable = true }
        val loopCheck = CheckBox("Loop").apply { isSelected = true }

        // Store reference so "Stop All" can reset this button's label.
        playPauseBtns[index] = playPauseBtn

        val progressBar = ProgressBar(0.0).apply {
            maxWidth = Double.MAX_VALUE
            prefHeight = 12.0
            isDisable = true
        }
        val timeLabel = Label("$TIME_UNKNOWN / $TIME_UNKNOWN").apply {
            style = "-fx-font-size: 10;"
            tooltip = Tooltip("Elapsed / remaining")
        }

        val volumeSlider = Slider(0.0, 1.0, 1.0).apply {
            tooltip = Tooltip("Volume for Track ${index + 1}")
            maxWidth = Double.MAX_VALUE
            valueProperty().addListener { _, _, newValue ->
                trackVolumes[index] = newValue.toDouble()
                players[index]?.volume = masterVolume * trackVolumes[index]
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
                    pathLabel.text = file.name
                    pathLabel.tooltip = Tooltip(file.absolutePath)
                    loadTrack(
                        index, file.toURI().toString(), loopCheck.isSelected,
                        playPauseBtn, stopBtn, progressBar, timeLabel,
                    )
                }
            }
        }

        playPauseBtn.setOnAction {
            val player = players[index] ?: return@setOnAction
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
            players[index]?.stop()
            playPauseBtn.text = "▶ Play"
        }

        loopCheck.setOnAction {
            players[index]?.cycleCount = if (loopCheck.isSelected) MediaPlayer.INDEFINITE else 1
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

        return VBox(4.0, fileRow, controlRow, progressRow, volRow).apply {
            padding = Insets(4.0, 2.0, 4.0, 2.0)
        }
    }

    // -------------------------------------------------------------------------
    // Track management
    // -------------------------------------------------------------------------

    /**
     * Loads a new [MediaPlayer] for [trackIndex] from [uri].
     *
     * Any existing player for this track is stopped and disposed first.
     * Controls are re-enabled once the media reports [MediaPlayer.Status.READY].
     * The [progressBar] and [timeLabel] are updated in real time via
     * [MediaPlayer.currentTimeProperty].
     */
    private fun loadTrack(
        trackIndex: Int,
        uri: String,
        loop: Boolean,
        playPauseBtn: Button,
        stopBtn: Button,
        progressBar: ProgressBar,
        timeLabel: Label,
    ) {
        players[trackIndex]?.dispose()
        players[trackIndex] = null

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
            volume = masterVolume * trackVolumes[trackIndex]
            cycleCount = if (loop) MediaPlayer.INDEFINITE else 1

            setOnReady {
                Platform.runLater {
                    playPauseBtn.isDisable = false
                    stopBtn.isDisable = false
                    progressBar.isDisable = false
                    timeLabel.text = "0:00 / -${formatDuration(media.duration)}"
                }
            }

            // currentTimeProperty fires on the FX thread; no Platform.runLater needed.
            // Early firings (before media is READY) return immediately via the isUnknown guard.
            currentTimeProperty().addListener { _, _, current ->
                val total = media.duration ?: return@addListener
                if (total.isUnknown || total.isIndefinite) return@addListener
                val frac = (current.toSeconds() / total.toSeconds()).coerceIn(0.0, 1.0)
                val remaining = total.subtract(current)
                progressBar.progress = frac
                timeLabel.text = "${formatDuration(current)} / -${formatDuration(remaining)}"
            }

            setOnError {
                Platform.runLater {
                    playPauseBtn.isDisable = true
                    stopBtn.isDisable = true
                    playPauseBtn.text = "▶ Play"
                }
            }

            setOnEndOfMedia {
                if (cycleCount != MediaPlayer.INDEFINITE) {
                    Platform.runLater { playPauseBtn.text = "▶ Play" }
                }
            }
        }

        players[trackIndex] = player
    }

    /** Formats a [Duration] as `M:SS`, or [TIME_UNKNOWN] for unknown/indefinite durations. */
    private fun formatDuration(duration: Duration?): String {
        if (duration == null || duration.isUnknown || duration.isIndefinite) return TIME_UNKNOWN
        val totalSecs = duration.toSeconds().toLong().coerceAtLeast(0L)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return "$mins:${secs.toString().padStart(2, '0')}"
    }
}
