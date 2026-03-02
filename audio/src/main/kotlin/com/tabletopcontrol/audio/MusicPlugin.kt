package com.tabletopcontrol.audio

import com.tabletopcontrol.core.DmPlugin
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
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

/**
 * DM-panel plugin for layered music control.
 *
 * Provides [TRACK_COUNT] independent music tracks, each with:
 * - .mp3 (and other audio format) file selection
 * - play/pause, stop, and loop controls
 * - per-track volume slider
 *
 * A master volume slider scales all tracks simultaneously.
 */
class MusicPlugin : DmPlugin {

    override val displayName: String = "Music"

    companion object {
        /** Number of independent music layers. */
        const val TRACK_COUNT = 3
    }

    /** Current master volume level in the range 0.0–1.0. */
    private var masterVolume: Double = 1.0

    /** One [MediaPlayer] per track; `null` when no file is loaded for that track. */
    private val players = arrayOfNulls<MediaPlayer>(TRACK_COUNT)

    /** Per-track volume levels in the range 0.0–1.0. */
    private val trackVolumes = DoubleArray(TRACK_COUNT) { 1.0 }

    override fun createView(): Node {
        val root = VBox(8.0).apply { padding = Insets(10.0) }

        root.children.addAll(
            Label("Music Controls"),
            Separator(),
            buildMasterVolumeSection(),
            Separator(),
        )

        for (i in 0 until TRACK_COUNT) {
            root.children.add(buildTrackSection(i))
            if (i < TRACK_COUNT - 1) root.children.add(Separator())
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

    /** Builds the master volume row. */
    private fun buildMasterVolumeSection(): VBox {
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
        val row = HBox(8.0, Label("Master Volume:"), slider).apply {
            HBox.setHgrow(slider, Priority.ALWAYS)
        }
        return VBox(4.0, row)
    }

    /** Builds one track control section for [index]. */
    private fun buildTrackSection(index: Int): VBox {
        val trackLabel = Label("Track ${index + 1}")

        val pathLabel = Label("No file loaded").apply {
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip("No file loaded")
        }

        val playPauseBtn = Button("▶ Play").apply { isDisable = true }
        val stopBtn = Button("⏹ Stop").apply { isDisable = true }
        val loopCheck = CheckBox("Loop").apply { isSelected = true }

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
                    loadTrack(index, file.toURI().toString(), loopCheck.isSelected, playPauseBtn, stopBtn)
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
        val volRow = HBox(8.0, Label("Volume:"), volumeSlider).apply {
            HBox.setHgrow(volumeSlider, Priority.ALWAYS)
        }

        return VBox(4.0, trackLabel, fileRow, controlRow, volRow)
    }

    // -------------------------------------------------------------------------
    // Track management
    // -------------------------------------------------------------------------

    /**
     * Loads a new [MediaPlayer] for [trackIndex] from [uri].
     *
     * Any existing player for this track is stopped and disposed first.
     * [playPauseBtn] and [stopBtn] are enabled once the media is ready.
     */
    private fun loadTrack(
        trackIndex: Int,
        uri: String,
        loop: Boolean,
        playPauseBtn: Button,
        stopBtn: Button,
    ) {
        players[trackIndex]?.dispose()
        players[trackIndex] = null

        // Disable controls while the new media loads.
        playPauseBtn.isDisable = true
        playPauseBtn.text = "▶ Play"
        stopBtn.isDisable = true

        val media = try {
            Media(uri)
        } catch (e: Exception) {
            Platform.runLater {
                playPauseBtn.isDisable = true
                stopBtn.isDisable = true
                playPauseBtn.text = "▶ Play"
            }
            return
        }
        val player = MediaPlayer(media).apply {
            volume = masterVolume * trackVolumes[trackIndex]
            cycleCount = if (loop) MediaPlayer.INDEFINITE else 1

            setOnReady {
                Platform.runLater {
                    playPauseBtn.isDisable = false
                    stopBtn.isDisable = false
                }
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
}
