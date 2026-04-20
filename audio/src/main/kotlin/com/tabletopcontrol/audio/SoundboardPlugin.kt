package com.tabletopcontrol.audio

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.ui.ContextMenuRenderer
import com.tabletopcontrol.core.ui.DragDropContext
import com.tabletopcontrol.core.ui.DragDropSupport
import com.tabletopcontrol.core.ui.DropIndicator
import com.tabletopcontrol.core.ui.MenuAction
import com.tabletopcontrol.core.ui.MenuSection
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.TilePane
import javafx.scene.layout.VBox

/**
 * DM-panel plugin for a simple soundboard.
 *
 * The plugin now focuses on JavaFX view wiring while [SoundboardSlotService]
 * owns slot lifecycle, persistence, and playback result flows.
 */
class SoundboardPlugin(
) : DmPlugin {
    private val slotService = SoundboardSlotService()

    override val displayName: String = "Soundboard"

    companion object {
        /** Default number of soundboard buttons. */
        const val BUTTON_COUNT = DEFAULT_SOUNDBOARD_BUTTON_COUNT
        const val DEFAULT_BUTTON_COUNT = BUTTON_COUNT
        const val MAX_BUTTON_COUNT = MAX_SOUNDBOARD_BUTTON_COUNT

        private const val MIN_COLUMN_COUNT = 2
        private const val MAX_COLUMN_COUNT = 8
        private const val TILE_WIDTH = 90.0
        private const val TILE_GAP = 4.0
        private const val TILE_PANE_PADDING = 4.0
        private const val END_DROP_TARGET_OPACITY = 0.7
        private const val DRAG_FORMAT = "tabletopcontrol/soundboard-slot"

        /** Returns the preferred column count for a given available [width]. */
        fun columnsForWidth(width: Double): Int {
            val usableWidth = (width - (TILE_PANE_PADDING * 2)).coerceAtLeast(0.0)
            val columns = ((usableWidth + TILE_GAP) / (TILE_WIDTH + TILE_GAP)).toInt()
            return columns.coerceIn(MIN_COLUMN_COUNT, MAX_COLUMN_COUNT)
        }

        internal fun clampButtonCount(requested: Int): Int = SoundboardSettingsCodec.clampSlotCount(requested)

        internal fun shouldShowInlineAddButton(slotCount: Int, columns: Int): Boolean {
            val safeColumns = columns.coerceAtLeast(1)
            if (slotCount >= MAX_BUTTON_COUNT) return false
            if (slotCount <= 0) return true
            return slotCount % safeColumns != 0
        }
    }

    private val slotButtons = mutableMapOf<SoundboardSlotState, Button>()
    private val slotListener = object : SoundboardSlotService.Listener {
        override fun onSlotSnapshotChanged(slot: SoundboardSlotState, snapshot: SoundboardSlotSnapshot) {
            applySnapshot(slot, snapshot)
        }

        override fun onSlotLoadResult(slot: SoundboardSlotState, result: SoundboardSlotLoadResult) {
            if (result is SoundboardSlotLoadResult.Failed) {
                applyLoadFailure(slot, result)
            }
        }

        override fun onSlotPlaybackFailure(
            slot: SoundboardSlotState,
            result: SoundboardSlotPlaybackResult.Failed,
        ) {
            applyPlaybackFailure(slot, result.failure)
        }

        override fun onConfigPersistenceResult(result: SoundboardSettingsSaveResult) {
            applyConfigSaveResult(result)
        }
    }

    private lateinit var tilePane: TilePane
    private lateinit var endDropTarget: Label
    private lateinit var addButton: Button
    private lateinit var countLabel: Label
    private lateinit var scrollPane: ScrollPane

    override fun createView(): Node {
        slotService.listener = slotListener
        val loadResult = slotService.initializeIfNeeded()

        tilePane = TilePane(TILE_GAP, TILE_GAP).apply {
            prefTileWidth = TILE_WIDTH
            prefTileHeight = 48.0
            prefColumns = columnsForWidth(0.0)
            style = "-fx-padding: $TILE_PANE_PADDING;"
            widthProperty().addListener { _, _, newWidth ->
                val columns = columnsForWidth(newWidth.toDouble())
                if (prefColumns != columns) {
                    prefColumns = columns
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
        addButton = Button("+ Add Button").apply {
            tooltip = Tooltip("Add a soundboard button (up to $MAX_BUTTON_COUNT)")
            setOnAction { addSlot() }
        }
        countLabel = Label()

        val controls = HBox(8.0, addButton, countLabel).apply {
            HBox.setHgrow(countLabel, Priority.ALWAYS)
        }
        val content = VBox(8.0, controls, tilePane, endDropTarget).apply {
            padding = Insets(8.0)
        }
        scrollPane = ScrollPane(content).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        }

        renderButtons()
        applyConfigLoadResult(loadResult)
        return scrollPane
    }

    override fun onShutdown() {
        slotButtons.clear()
        slotService.listener = null
        slotService.shutdown()
    }

    private fun renderButtons() {
        slotButtons.clear()
        tilePane.children.clear()

        val indicator = DropIndicator()
        val dragContext = DragDropContext(
            dataFormat = DRAG_FORMAT,
            autoScrollPane = scrollPane,
            onReorder = { fromIndex, toIndex ->
                if (!slotService.reorderSlots(fromIndex, toIndex)) return@DragDropContext
                renderButtons()
            },
        )

        slotService.slots.forEachIndexed { index, slot ->
            val button = buildButton(index, slot)
            DragDropSupport.installDragSource(button, index, dragContext)
            DragDropSupport.installDropTarget(button, index, dragContext, indicator)
            tilePane.children.add(button)
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
        DragDropSupport.installDropTarget(endDropTarget, slotService.slots.size, dragContext, endDropIndicator)

        if (shouldShowInlineAddButton(slotService.slots.size, tilePane.prefColumns)) {
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
        refreshControls()
    }

    private fun buildButton(index: Int, slot: SoundboardSlotState): Button {
        val button = Button().apply {
            prefWidth = TILE_WIDTH
            prefHeight = 48.0
            maxWidth = Double.MAX_VALUE
            isWrapText = true
        }
        slotButtons[slot] = button
        applySnapshot(slot, slotService.snapshotOf(slot))

        button.setOnAction {
            slotService.togglePlayback(slot)
        }

        val actions = listOf(
            MenuAction(
                id = "soundboard.load",
                label = "Load Sound…",
                icon = "📂",
                section = MenuSection.BASIC,
                onAction = { showLoadDialog(slot, button) },
            ),
            MenuAction(
                id = "soundboard.clear",
                label = "Clear",
                icon = "🗑️",
                section = MenuSection.BASIC,
                onAction = { slotService.clearSlot(slot) },
            ),
            MenuAction(
                id = "soundboard.setColor",
                label = "Set Color",
                icon = "🎨",
                section = MenuSection.APPEARANCE,
                onAction = { showColorDialog(slot, button) },
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
        button.setOnContextMenuRequested { event ->
            ContextMenuRenderer.build(actions).show(button, event.screenX, event.screenY)
            event.consume()
        }

        slotService.restoreSlot(slot)
        return button
    }

    private fun showLoadDialog(slot: SoundboardSlotState, button: Button) {
        val slotNumber = slotNumberFor(slot) ?: return
        val file = SoundboardSlotDialogs.chooseAudioFile(
            owner = button.scene?.window,
            slotNumber = slotNumber,
        ) ?: return

        slotService.loadSelectedFile(
            slot = slot,
            requestedLabel = file.nameWithoutExtension,
            requestedUri = file.toURI().toString(),
        )
    }

    private fun showColorDialog(slot: SoundboardSlotState, button: Button) {
        val selectedColor = SoundboardSlotDialogs.chooseColor(
            owner = button.scene?.window,
            currentColorHex = slot.colorHex,
        ) ?: return

        slotService.setSlotColor(slot, selectedColor)
    }

    private fun addSlot() {
        if (slotService.addSlot()) {
            renderButtons()
        }
    }

    private fun removeSlot(slot: SoundboardSlotState) {
        if (slotService.removeSlot(slot)) {
            renderButtons()
        }
    }

    private fun applySnapshot(slot: SoundboardSlotState, snapshot: SoundboardSlotSnapshot) {
        val button = slotButtons[slot] ?: return
        button.text = snapshot.buttonText
        button.tooltip = Tooltip(snapshot.tooltipText)
        button.style = SoundboardSlotVisuals.cssFor(snapshot.styleState)
    }

    private fun applyLoadFailure(slot: SoundboardSlotState, result: SoundboardSlotLoadResult.Failed) {
        val button = slotButtons[slot] ?: return
        val attemptedPath = SoundboardSlotVisuals.tooltipTextForUri(result.requestedUri)
        val message = buildString {
            append("Failed to load audio file:\n")
            append(attemptedPath)
            append("\n\n")
            if (result.clearedAssignedSlot || result.snapshot.uri == null) {
                append("No sound is currently loaded for this slot.")
            } else {
                append("Current sound:\n")
                append(SoundboardSlotVisuals.tooltipTextForUri(result.snapshot.uri))
            }
        }
        button.text = "⚠ Load Error"
        button.tooltip = Tooltip(message)
        button.style = SoundboardSlotVisuals.cssFor(result.snapshot.styleState)
    }

    private fun applyPlaybackFailure(slot: SoundboardSlotState, failure: SoundboardSlotPlaybackFailure) {
        val button = slotButtons[slot] ?: return
        val message = when (failure) {
            is SoundboardSlotPlaybackFailure.NoActiveTrack -> {
                if (failure.uri == null) return
                "No audio file is currently loaded for:\n${SoundboardSlotVisuals.tooltipTextForUri(failure.uri)}"
            }

            is SoundboardSlotPlaybackFailure.Unavailable -> {
                val path = failure.uri?.let(SoundboardSlotVisuals::tooltipTextForUri) ?: "this slot"
                "Track is unavailable for playback:\n$path"
            }

            is SoundboardSlotPlaybackFailure.PlayerError -> {
                val path = failure.uri?.let(SoundboardSlotVisuals::tooltipTextForUri) ?: "this slot"
                "Playback failed for:\n$path"
            }
        }
        button.tooltip = Tooltip(message)
    }

    private fun applyConfigLoadResult(result: SoundboardSettingsLoadResult) {
        countLabel.tooltip = when (result) {
            is SoundboardSettingsLoadResult.Failed -> Tooltip(configFailureMessage(result.failure, duringLoad = true))
            else -> null
        }
    }

    private fun applyConfigSaveResult(result: SoundboardSettingsSaveResult) {
        countLabel.tooltip = when (result) {
            is SoundboardSettingsSaveResult.Failed -> Tooltip(configFailureMessage(result.failure, duringLoad = false))
            is SoundboardSettingsSaveResult.Saved -> null
        }
    }

    private fun configFailureMessage(
        failure: SoundboardSettingsPersistenceFailure,
        duringLoad: Boolean,
    ): String = when (failure) {
        is SoundboardSettingsPersistenceFailure.ReadFailed ->
            "Soundboard settings could not be read from ${failure.configFile.absolutePath}. " +
                "Default buttons are being used for this session."

        is SoundboardSettingsPersistenceFailure.InvalidFormat ->
            "Soundboard settings in ${failure.configFile.absolutePath} are invalid. " +
                "Default buttons are being used for this session."

        is SoundboardSettingsPersistenceFailure.WriteFailed ->
            if (duringLoad) {
                "Soundboard settings could not be updated at ${failure.configFile.absolutePath}."
            } else {
                "Soundboard changes could not be saved to ${failure.configFile.absolutePath}. " +
                    "Your current buttons still work for this session."
            }
    }

    private fun refreshControls() {
        addButton.isDisable = slotService.slots.size >= MAX_BUTTON_COUNT
        countLabel.text = "${slotService.slots.size}/$MAX_BUTTON_COUNT"
    }

    private fun slotNumberFor(slot: SoundboardSlotState): Int? =
        slotService.slots.indexOf(slot).takeIf { it >= 0 }?.plus(1)
}
