package com.tabletopcontrol.audio

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.SafeConfigIO
import com.tabletopcontrol.core.ui.ContextMenuRenderer
import com.tabletopcontrol.core.ui.color.ColorContrast
import com.tabletopcontrol.core.ui.color.ColorEditorPopover
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.core.ui.DragDropContext
import com.tabletopcontrol.core.ui.DragDropSupport
import com.tabletopcontrol.core.ui.DropIndicator
import com.tabletopcontrol.core.ui.MenuAction
import com.tabletopcontrol.core.ui.MenuSection
import com.tabletopcontrol.core.ui.dialog.DialogFlows
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.geometry.Pos
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.TilePane
import javafx.scene.layout.VBox
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import java.io.File
import java.net.URI
import java.util.Base64

/**
 * DM-panel plugin for a simple soundboard.
 *
 * Provides configurable soundboard buttons arranged in a responsive grid that fits
 * the number of columns to the available width, using between 2 and 8 columns
 * inclusive as space allows.
 *
 * - **Right-click** a button to load an MP3 (or other supported audio) file.
 * - **Right-click** a button to set a custom colour.
 * - **Right-click** a button to remove it.
 * - **Left-click** a loaded button to toggle playback on/off.
 * - Buttons can be reordered via drag-and-drop.
 * - When a sound finishes naturally, the button resets to its idle state.
 *
 * GODCLASS audit note:
 * - `SoundboardPlugin` is currently a GODCLASS.
 * - Features that can be moved to helpers/shared components:
 *   - Slot config persistence (`serializeConfig`, `parseConfig`, `saveConfig`, `loadConfig`).
 *   - Media/player lifecycle handling (`loadSlot`, `playSlot`, `stopSlot`, disposal/reset).
 *   - Context-menu and color-picker UI flows.
 *   - Button styling/state transitions (`setPlayingStyle`, `setIdleStyle`, `applyCurrentStyle`).
 *   - Soundboard grid rendering/reorder orchestration (`renderButtons`, drag/drop wiring).
 */
class SoundboardPlugin : DmPlugin {

    override val displayName: String = "Soundboard"

    internal data class SlotConfig(
        val label: String? = null,
        val uri: String? = null,
        val colorHex: String? = null,
    )

    companion object {
        /** Default number of soundboard buttons. */
        const val BUTTON_COUNT = 16
        const val DEFAULT_BUTTON_COUNT = BUTTON_COUNT
        const val MAX_BUTTON_COUNT = 32
        private const val MIN_COLUMN_COUNT = 2
        private const val MAX_COLUMN_COUNT = 8
        private const val TILE_WIDTH = 90.0
        private const val TILE_GAP = 4.0
        private const val TILE_PANE_PADDING = 4.0
        private const val END_DROP_TARGET_OPACITY = 0.7

        private const val CONFIG_VERSION = 1
        private const val DRAG_FORMAT = "tabletopcontrol/soundboard-slot"

        /** Returns the preferred column count for a given available [width]. */
        fun columnsForWidth(width: Double): Int {
            val usableWidth = (width - (TILE_PANE_PADDING * 2)).coerceAtLeast(0.0)
            val columns = ((usableWidth + TILE_GAP) / (TILE_WIDTH + TILE_GAP)).toInt()
            return columns.coerceIn(MIN_COLUMN_COUNT, MAX_COLUMN_COUNT)
        }

        internal fun clampButtonCount(requested: Int): Int = requested.coerceIn(0, MAX_BUTTON_COUNT)

        internal fun shouldShowInlineAddButton(slotCount: Int, columns: Int): Boolean {
            val safeColumns = columns.coerceAtLeast(1)
            if (slotCount >= MAX_BUTTON_COUNT) return false
            if (slotCount <= 0) return true
            return slotCount % safeColumns != 0
        }

        /**
         * Computes the post-removal insertion index for a reorder operation.
         *
         * Returns `null` when indices are out of range or when the reorder would be a no-op.
         * Supports append targets by accepting `toIndex == listSize`.
         */
        internal fun adjustedDropInsertIndex(listSize: Int, fromIndex: Int, toIndex: Int): Int? {
            if (fromIndex !in 0 until listSize || toIndex !in 0..listSize) return null
            val adjusted = if (fromIndex < toIndex) toIndex - 1 else toIndex
            return adjusted.takeUnless { it == fromIndex }
        }

        internal fun serializeConfig(slots: List<SlotConfig>): String {
            val b64 = Base64.getUrlEncoder().withoutPadding()
            fun enc(v: String?): String = v?.let { b64.encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: "-"

            return buildString {
                appendLine("version=$CONFIG_VERSION")
                appendLine("count=${clampButtonCount(slots.size)}")
                slots.take(MAX_BUTTON_COUNT).forEach { slot ->
                    appendLine("slot=${enc(slot.label)}|${enc(slot.uri)}|${enc(slot.colorHex)}")
                }
            }
        }

        internal fun parseConfig(text: String): List<SlotConfig>? {
            val lines = text.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
            if (lines.isEmpty()) return null
            val version = lines.firstOrNull { it.startsWith("version=") }
                ?.substringAfter('=')
                ?.toIntOrNull()
                ?: return null
            if (version != CONFIG_VERSION) return null

            val decoder = Base64.getUrlDecoder()
            fun dec(v: String): String? = if (v == "-") {
                null
            } else {
                runCatching { String(decoder.decode(v), Charsets.UTF_8) }.getOrNull()
            }

            val count = lines.firstOrNull { it.startsWith("count=") }
                ?.substringAfter('=')
                ?.toIntOrNull()
                ?.let { clampButtonCount(it) }
                ?: DEFAULT_BUTTON_COUNT

            val parsed = lines
                .filter { it.startsWith("slot=") }
                .mapNotNull { line ->
                    val payload = line.substringAfter("slot=", "")
                    val parts = payload.split('|')
                    if (parts.size != 3) return@mapNotNull null
                    SlotConfig(
                        label = dec(parts[0]),
                        uri = dec(parts[1]),
                        colorHex = dec(parts[2])?.takeIf { ColorHexCodec.parseOrNull(it) != null },
                    )
                }
                .toMutableList()

            while (parsed.size < count) parsed.add(SlotConfig())
            return parsed.take(count)
        }

        internal const val EMPTY_SLOT_TOOLTIP = "Right-click to load a sound file"

        internal fun tooltipTextForUri(uri: String?): String {
            if (uri.isNullOrBlank()) return EMPTY_SLOT_TOOLTIP
            return runCatching { File(URI(uri)).absolutePath }.getOrDefault(uri)
        }

        internal data class ButtonVisualState(
            val text: String,
            val isPlaying: Boolean,
        )

        internal fun buttonVisualState(isPlaying: Boolean, label: String): ButtonVisualState =
            if (isPlaying) {
                ButtonVisualState(text = "⏹ $label", isPlaying = true)
            } else {
                ButtonVisualState(text = label, isPlaying = false)
            }
    }

    private class SlotState(
        var customLabel: String? = null,
        var uri: String? = null,
        var colorHex: String? = null,
        var player: MediaPlayer? = null,
        var button: Button? = null,
    ) {
        fun displayLabel(index: Int): String = customLabel ?: "Slot ${index + 1}"
    }

    private val slots = mutableListOf<SlotState>()
    private var initialized = false

    private lateinit var tilePane: TilePane
    private lateinit var endDropTarget: Label
    private lateinit var addButton: Button
    private lateinit var countLabel: Label
    private lateinit var scrollPane: ScrollPane

    private val configFile: File
        get() = AppConfigPaths.configFile("soundboard.conf")

    override fun createView(): Node {
        if (!initialized) {
            val persisted = loadConfig()
            val initialCount = clampButtonCount(persisted?.size ?: DEFAULT_BUTTON_COUNT)
            val initial = persisted ?: List(initialCount) { SlotConfig() }
            slots.clear()
            slots.addAll(initial.take(initialCount).map { SlotState(it.label, it.uri, it.colorHex) })
            initialized = true
        }

        tilePane = TilePane(TILE_GAP, TILE_GAP).apply {
            prefTileWidth = TILE_WIDTH
            prefTileHeight = 48.0
            prefColumns = columnsForWidth(0.0)
            style = "-fx-padding: $TILE_PANE_PADDING;"
            widthProperty().addListener { _, _, newWidth ->
                val cols = columnsForWidth(newWidth.toDouble())
                if (prefColumns != cols) {
                    prefColumns = cols
                    renderButtons()
                }
            }
        }
        endDropTarget = Label("⇣ Drag here to move to end").apply {
            prefWidth = TILE_WIDTH
            minHeight = 48.0
            maxWidth = Double.MAX_VALUE
            opacity = END_DROP_TARGET_OPACITY
            alignment = Pos.CENTER
            tooltip = Tooltip("Drop a dragged soundboard button here to move it to the end")
        }

        addButton = Button().apply {
            tooltip = Tooltip("Add a soundboard button (up to $MAX_BUTTON_COUNT)")
            setOnAction { addSlot() }
        }
        countLabel = Label()

        val controls = HBox(8.0, addButton, countLabel).apply {
            HBox.setHgrow(countLabel, Priority.ALWAYS)
        }

        val content = VBox(8.0, controls, tilePane, endDropTarget).apply { padding = Insets(8.0) }
        scrollPane = ScrollPane(content).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        }

        renderButtons()
        return scrollPane
    }

    override fun onShutdown() {
        slots.forEach { it.player?.dispose() }
    }

    private fun indexOfSlot(slot: SlotState): Int? = slots.indexOf(slot).takeIf { it >= 0 }

    private fun displayLabelFor(slot: SlotState): String =
        indexOfSlot(slot)?.let { slot.displayLabel(it) } ?: (slot.customLabel ?: "Slot")

    private fun renderButtons() {
        tilePane.children.clear()

        val indicator = DropIndicator()
        val dragContext = DragDropContext(
            dataFormat = DRAG_FORMAT,
            autoScrollPane = scrollPane,
            onReorder = { fromIndex, toIndex ->
                val adjustedToIndex = adjustedDropInsertIndex(slots.size, fromIndex, toIndex) ?: return@DragDropContext
                val moved = slots.removeAt(fromIndex)
                slots.add(adjustedToIndex, moved)
                renderButtons()
                saveConfig()
            },
        )

        slots.indices.forEach { index ->
            val btn = buildButton(index)
            DragDropSupport.installDragSource(btn, index, dragContext)
            DragDropSupport.installDropTarget(btn, index, dragContext, indicator)
            tilePane.children.add(btn)
        }

        val endDropIndicator = DropIndicator()
        (endDropTarget.parent as? VBox)?.let { parent ->
            val existingIndicator = endDropTarget.properties["soundboardEndDropIndicator"] as? DropIndicator
            if (existingIndicator != null) {
                parent.children.remove(existingIndicator)
            }

            val endDropTargetIndex = parent.children.indexOf(endDropTarget)
            if (endDropTargetIndex >= 0) {
                parent.children.add(endDropTargetIndex, endDropIndicator)
            }
            endDropTarget.properties["soundboardEndDropIndicator"] = endDropIndicator
        }
        DragDropSupport.installDropTarget(endDropTarget, slots.size, dragContext, endDropIndicator)
        if (shouldShowInlineAddButton(slots.size, tilePane.prefColumns)) {
            tilePane.children.add(
                Button("+").apply {
                    prefWidth = TILE_WIDTH
                    prefHeight = 48.0
                    maxWidth = Double.MAX_VALUE
                    tooltip = Tooltip("Add a soundboard button (up to $MAX_BUTTON_COUNT)")
                    setOnAction { addSlot() }
                },
            )
        }
        tilePane.children.add(indicator)

        addButton.isDisable = slots.size >= MAX_BUTTON_COUNT
        addButton.text = "+ Add Button"
        countLabel.text = "${slots.size}/$MAX_BUTTON_COUNT"
    }

    private fun buildButton(index: Int): Button {
        val slot = slots[index]
        val btn = Button(slot.displayLabel(index)).apply {
            prefWidth = TILE_WIDTH
            prefHeight = 48.0
            maxWidth = Double.MAX_VALUE
            isWrapText = true
            tooltip = if (slot.uri != null) {
                Tooltip(tooltipTextForUri(slot.uri))
            } else {
                Tooltip(EMPTY_SLOT_TOOLTIP)
            }
        }
        slot.button = btn

        if (slot.uri != null && slot.player == null && !loadSlot(slot)) {
            slot.uri = null
            slot.customLabel = null
            btn.text = slot.displayLabel(index)
            btn.tooltip = Tooltip(EMPTY_SLOT_TOOLTIP)
            saveConfig()
        }
        val visualState = buttonVisualState(
            isPlaying = slot.player?.status == MediaPlayer.Status.PLAYING,
            label = slot.displayLabel(index),
        )
        btn.text = visualState.text
        if (visualState.isPlaying) {
            setPlayingStyle(slot)
        } else {
            setIdleStyle(slot)
        }

        btn.setOnAction {
            val player = slot.player ?: return@setOnAction
            when (player.status) {
                MediaPlayer.Status.PLAYING -> stopSlot(slot)
                else -> playSlot(slot)
            }
        }

        val actions = listOf(
            MenuAction(
                id = "soundboard.load",
                label = "Load Sound…",
                icon = "📂",
                section = MenuSection.BASIC,
                onAction = { showFileChooser(slot, btn) },
            ),
            MenuAction(
                id = "soundboard.clear",
                label = "Clear",
                icon = "🗑️",
                section = MenuSection.BASIC,
                onAction = { clearSlot(slot) },
            ),
            MenuAction(
                id = "soundboard.setColor",
                label = "Set Color",
                icon = "🎨",
                section = MenuSection.APPEARANCE,
                onAction = { showColorPicker(slot, btn) },
            ),
            MenuAction(
                id = "soundboard.remove",
                label = "Remove Button",
                icon = "➖",
                section = MenuSection.DANGER_ZONE,
                requiresConfirmation = true,
                onAction = { removeSlot(slot) },
            ),
        )
        btn.setOnContextMenuRequested { e ->
            ContextMenuRenderer.build(actions).show(btn, e.screenX, e.screenY)
            e.consume()
        }

        return btn
    }

    private fun showFileChooser(slot: SlotState, btn: Button) {
        val slotIndex = indexOfSlot(slot) ?: return
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

        slot.customLabel = file.nameWithoutExtension
        slot.uri = file.toURI().toString()

        if (!loadSlot(slot)) {
            slot.customLabel = null
            slot.uri = null
            btn.text = "⚠ Load Error"
            btn.tooltip = Tooltip("Failed to load: ${file.absolutePath}")
            setIdleStyle(slot)
            saveConfig()
            return
        }

        btn.text = slot.customLabel
        btn.tooltip = Tooltip(tooltipTextForUri(slot.uri))
        setIdleStyle(slot)
        saveConfig()
    }

    private fun loadSlot(slot: SlotState): Boolean {
        slot.player?.dispose()
        slot.player = null

        val uri = slot.uri ?: return false
        val media = try {
            Media(uri)
        } catch (_: Exception) {
            return false
        }

        val player = try {
            MediaPlayer(media)
        } catch (_: Exception) {
            return false
        }

        slot.player = player.apply {
            cycleCount = 1

            setOnEndOfMedia {
                Platform.runLater {
                    this@apply.stop()
                    resetButtonToIdle(slot)
                }
            }

            setOnError {
                Platform.runLater { resetButtonToIdle(slot) }
            }
        }
        return true
    }

    private fun playSlot(slot: SlotState) {
        val label = displayLabelFor(slot)
        slot.player?.play()
        slot.button?.let {
            it.text = buttonVisualState(isPlaying = true, label = label).text
            setPlayingStyle(slot)
        }
    }

    private fun stopSlot(slot: SlotState) {
        slot.player?.stop()
        resetButtonToIdle(slot)
    }

    private fun clearSlot(slot: SlotState) {
        slot.player?.dispose()
        slot.player = null
        slot.uri = null
        slot.customLabel = null

        slot.button?.let {
            it.text = displayLabelFor(slot)
            it.tooltip = Tooltip(EMPTY_SLOT_TOOLTIP)
            setIdleStyle(slot)
        }
        saveConfig()
    }

    private fun resetButtonToIdle(slot: SlotState) {
        slot.button?.let {
            it.text = displayLabelFor(slot)
            setIdleStyle(slot)
        }
    }

    private fun setPlayingStyle(slot: SlotState) {
        val btn = slot.button ?: return
        val color = slot.colorHex
        btn.style = if (color == null) {
            "-fx-base: -tc-accent; -fx-text-fill: -tc-on-accent;"
        } else {
            "-fx-background-color: $color; -fx-text-fill: ${ColorContrast.textColorHexForBackgroundHex(color)}; -fx-border-color: -tc-accent; -fx-border-width: 2;"
        }
    }

    private fun setIdleStyle(slot: SlotState) {
        val btn = slot.button ?: return
        val color = slot.colorHex
        btn.style = if (color == null) "" else "-fx-background-color: $color; -fx-text-fill: ${ColorContrast.textColorHexForBackgroundHex(color)};"
    }

    private fun showColorPicker(slot: SlotState, btn: Button) {
        val initial = ColorHexCodec.parseOrDefault(slot.colorHex, Color.GRAY)
        val selected = ColorEditorPopover.showDialog(
            owner = btn.scene?.window,
            title = "Set Button Color",
            prompt = "Choose a color for this button",
            initialColor = initial,
        )
        if (selected != null) {
            slot.colorHex = ColorHexCodec.colorToHex(selected)
            applyCurrentStyle(slot)
            saveConfig()
        }
    }

    private fun applyCurrentStyle(slot: SlotState) {
        if (slot.player?.status == MediaPlayer.Status.PLAYING) {
            setPlayingStyle(slot)
        } else {
            setIdleStyle(slot)
        }
    }
    private fun addSlot() {
        if (slots.size >= MAX_BUTTON_COUNT) return
        slots.add(SlotState())
        renderButtons()
        saveConfig()
    }

    private fun removeSlot(slot: SlotState) {
        val slotIndex = indexOfSlot(slot) ?: return
        val removed = slots.removeAt(slotIndex)
        removed.player?.let { player ->
            runCatching { player.stop() }
            player.onEndOfMedia = null
            player.onReady = null
            player.onPlaying = null
            player.onPaused = null
            player.onStopped = null
            player.onError = null
            player.dispose()
        }
        removed.player = null
        removed.button = null
        renderButtons()
        saveConfig()
    }

    private fun saveConfig() {
        val payload = slots.map { SlotConfig(it.customLabel, it.uri, it.colorHex) }
        SafeConfigIO.writeText(configFile, serializeConfig(payload))
    }

    private fun loadConfig(): List<SlotConfig>? {
        if (!configFile.exists()) return null

        return SafeConfigIO.readOrElse(null) {
            parseConfig(configFile.readText())
        }
    }
}
