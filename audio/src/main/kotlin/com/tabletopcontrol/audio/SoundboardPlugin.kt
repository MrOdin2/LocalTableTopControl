package com.tabletopcontrol.audio

import com.tabletopcontrol.core.DmPlugin
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.TilePane
import javafx.scene.layout.VBox
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.stage.FileChooser

/**
 * DM-panel plugin for a simple soundboard.
 *
 * Provides [BUTTON_COUNT] buttons arranged in a responsive grid that adapts
 * between 8 columns (wide layout) and 2 columns (narrow layout) based on
 * the available width.
 *
 * - **Right-click** a button to load an MP3 (or other supported audio) file.
 * - **Left-click** a loaded button to toggle playback on/off.
 * - When a sound finishes naturally, the button resets to its idle state.
 */
class SoundboardPlugin : DmPlugin {

    override val displayName: String = "Soundboard"

    companion object {
        /** Total number of soundboard buttons. */
        const val BUTTON_COUNT = 16

        /** Pixel width at or above which 8 columns are used; below it 2 columns are used. */
        const val WIDE_THRESHOLD = 550.0

        /**
         * Returns the preferred column count for a given available [width].
         *
         * - `>= WIDE_THRESHOLD` → 8 columns (2 rows × 8 cols)
         * - `< WIDE_THRESHOLD` → 2 columns (8 rows × 2 cols)
         */
        fun columnsForWidth(width: Double): Int = if (width >= WIDE_THRESHOLD) 8 else 2
    }

    /** Per-slot display labels; defaults to "Slot N" until a file is loaded. */
    private val slotLabels = Array(BUTTON_COUNT) { "Slot ${it + 1}" }

    /** Per-slot file URIs; `null` when no file has been loaded. */
    private val slotUris = arrayOfNulls<String>(BUTTON_COUNT)

    /** Per-slot [MediaPlayer] instances; `null` when no file is loaded. */
    private val players = arrayOfNulls<MediaPlayer>(BUTTON_COUNT)

    /** Stored button references so state can be reset from background threads. */
    private val buttons = arrayOfNulls<Button>(BUTTON_COUNT)

    // -------------------------------------------------------------------------
    // DmPlugin implementation
    // -------------------------------------------------------------------------

    override fun createView(): Node {
        val tilePane = TilePane(4.0, 4.0).apply {
            prefTileWidth = 90.0
            prefTileHeight = 48.0
            prefColumns = columnsForWidth(0.0) // start narrow; listener corrects it
            style = "-fx-padding: 4;"
            // Dynamically switch between 2 and 8 columns based on available width.
            widthProperty().addListener { _, _, newWidth ->
                val cols = columnsForWidth(newWidth.toDouble())
                if (prefColumns != cols) prefColumns = cols
            }
        }

        for (i in 0 until BUTTON_COUNT) {
            val btn = buildButton(i)
            buttons[i] = btn
            tilePane.children.add(btn)
        }

        return ScrollPane(VBox(tilePane).apply { padding = Insets(8.0) }).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        }
    }

    override fun onShutdown() {
        players.forEach { it?.dispose() }
    }

    // -------------------------------------------------------------------------
    // Button construction
    // -------------------------------------------------------------------------

    /** Builds a single soundboard button for [index] with click and context-menu handlers. */
    private fun buildButton(index: Int): Button {
        val btn = Button(slotLabels[index]).apply {
            prefWidth = 90.0
            prefHeight = 48.0
            maxWidth = Double.MAX_VALUE
            isWrapText = true
            tooltip = Tooltip("Right-click to load a sound file")
        }

        // Left-click: toggle playback.
        btn.setOnAction {
            val player = players[index] ?: return@setOnAction
            when (player.status) {
                MediaPlayer.Status.PLAYING -> stopSlot(index)
                else -> playSlot(index)
            }
        }

        // Right-click context menu.
        val loadItem = MenuItem("Load Sound…").apply {
            setOnAction { showFileChooser(index, btn) }
        }
        val clearItem = MenuItem("Clear").apply {
            setOnAction { clearSlot(index) }
        }
        btn.contextMenu = ContextMenu(loadItem, clearItem)

        return btn
    }

    // -------------------------------------------------------------------------
    // Slot management
    // -------------------------------------------------------------------------

    /**
     * Opens a [FileChooser] so the user can pick an audio file for [slotIndex].
     * On confirmation the slot is loaded and the button label is updated.
     */
    private fun showFileChooser(slotIndex: Int, btn: Button) {
        val chooser = FileChooser().apply {
            title = "Load sound for Slot ${slotIndex + 1}"
            extensionFilters.addAll(
                FileChooser.ExtensionFilter("MP3 files", "*.mp3"),
                FileChooser.ExtensionFilter(
                    "Audio files", "*.mp3", "*.wav", "*.aac", "*.m4a",
                ),
                FileChooser.ExtensionFilter("All files", "*.*"),
            )
        }
        val owner = btn.scene?.window
        val file = chooser.showOpenDialog(owner) ?: return

        val label = file.nameWithoutExtension
        slotLabels[slotIndex] = label
        slotUris[slotIndex] = file.toURI().toString()

        // Load new MediaPlayer for this slot; revert to empty state on failure.
        if (!loadSlot(slotIndex)) {
            slotLabels[slotIndex] = "Slot ${slotIndex + 1}"
            slotUris[slotIndex] = null
            btn.text = "⚠ Load Error"
            btn.tooltip = Tooltip("Failed to load: ${file.absolutePath}")
            return
        }

        // Update button appearance.
        btn.text = label
        btn.tooltip = Tooltip(file.absolutePath)
        setIdleStyle(btn)
    }

    /**
     * Creates and stores a new [MediaPlayer] for [slotIndex] using the URI
     * previously saved in [slotUris].  Any existing player is disposed first.
     *
     * @return `true` if the [MediaPlayer] was created successfully, `false` otherwise.
     */
    private fun loadSlot(slotIndex: Int): Boolean {
        players[slotIndex]?.dispose()
        players[slotIndex] = null

        val uri = slotUris[slotIndex] ?: return false
        val media = try {
            Media(uri)
        } catch (_: Exception) {
            return false
        }

        players[slotIndex] = MediaPlayer(media).apply {
            cycleCount = 1

            // When the sound ends naturally, reset button to idle state.
            setOnEndOfMedia {
                Platform.runLater {
                    this@apply.stop() // rewind so the slot can play again
                    resetButtonToIdle(slotIndex)
                }
            }

            setOnError {
                Platform.runLater { resetButtonToIdle(slotIndex) }
            }
        }
        return true
    }

    /** Starts playback for [slotIndex] and marks the button as playing. */
    private fun playSlot(slotIndex: Int) {
        players[slotIndex]?.play()
        buttons[slotIndex]?.let {
            it.text = "⏹ ${slotLabels[slotIndex]}"
            setPlayingStyle(it)
        }
    }

    /** Stops playback for [slotIndex] and resets the button to idle. */
    private fun stopSlot(slotIndex: Int) {
        players[slotIndex]?.stop()
        resetButtonToIdle(slotIndex)
    }

    /** Disposes the player for [slotIndex] and resets all slot state to defaults. */
    private fun clearSlot(slotIndex: Int) {
        players[slotIndex]?.dispose()
        players[slotIndex] = null
        slotUris[slotIndex] = null
        slotLabels[slotIndex] = "Slot ${slotIndex + 1}"
        buttons[slotIndex]?.let {
            it.text = slotLabels[slotIndex]
            it.tooltip = Tooltip("Right-click to load a sound file")
            setIdleStyle(it)
        }
    }

    /** Resets the button text and style for [slotIndex] to the idle (non-playing) state. */
    private fun resetButtonToIdle(slotIndex: Int) {
        buttons[slotIndex]?.let {
            it.text = slotLabels[slotIndex]
            setIdleStyle(it)
        }
    }

    // -------------------------------------------------------------------------
    // Button styling helpers
    // -------------------------------------------------------------------------

    /** Applies the "playing" highlight style to [btn]. */
    private fun setPlayingStyle(btn: Button) {
        btn.style = "-fx-base: #4CAF50; -fx-text-fill: white;"
    }

    /** Removes any custom style from [btn] (returns to default theme). */
    private fun setIdleStyle(btn: Button) {
        btn.style = ""
    }
}
