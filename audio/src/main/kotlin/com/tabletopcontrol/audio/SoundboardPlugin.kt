package com.tabletopcontrol.audio

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.ui.ContextMenuRenderer
import com.tabletopcontrol.core.ui.DragDropContext
import com.tabletopcontrol.core.ui.DragDropSupport
import com.tabletopcontrol.core.ui.DropIndicator
import com.tabletopcontrol.core.ui.MenuAction
import com.tabletopcontrol.core.ui.MenuSection
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Slider
import javafx.scene.control.Tooltip
import javafx.scene.image.PixelWriter
import javafx.scene.image.WritableImage
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.TilePane
import javafx.scene.layout.VBox
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.stage.FileChooser
import java.io.File
import java.util.Base64

/**
 * DM-panel plugin for a simple soundboard.
 *
 * Provides configurable soundboard buttons arranged in a responsive grid that adapts
 * between 8 columns (wide layout) and 2 columns (narrow layout) based on
 * the available width.
 *
 * - **Right-click** a button to load an MP3 (or other supported audio) file.
 * - **Right-click** a button to set a custom colour.
 * - **Right-click** a button to remove it.
 * - **Left-click** a loaded button to toggle playback on/off.
 * - Buttons can be reordered via drag-and-drop.
 * - When a sound finishes naturally, the button resets to its idle state.
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
                        colorHex = dec(parts[2])?.takeIf { runCatching { Color.web(it) }.isSuccess },
                    )
                }
                .toMutableList()

            while (parsed.size < count) parsed.add(SlotConfig())
            return parsed.take(count)
        }
    }

    private data class SlotState(
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
    private lateinit var addButton: Button
    private lateinit var countLabel: Label
    private lateinit var scrollPane: ScrollPane

    private val configFile: File
        get() {
            val dir = File(System.getProperty("user.home"), ".tabletopcontrol")
            dir.mkdirs()
            return File(dir, "soundboard.conf")
        }

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

        addButton = Button().apply {
            tooltip = Tooltip("Add a soundboard button (up to $MAX_BUTTON_COUNT)")
            setOnAction { addSlot() }
        }
        countLabel = Label()

        val controls = HBox(8.0, addButton, countLabel).apply {
            HBox.setHgrow(countLabel, Priority.ALWAYS)
        }

        val content = VBox(8.0, controls, tilePane).apply { padding = Insets(8.0) }
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
                if (fromIndex !in slots.indices || toIndex !in slots.indices) return@DragDropContext
                val moved = slots.removeAt(fromIndex)
                val adjustedToIndex = if (fromIndex < toIndex) toIndex - 1 else toIndex
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
                Tooltip(slot.uri)
            } else {
                Tooltip("Right-click to load a sound file")
            }
        }
        slot.button = btn

        if (slot.uri != null && slot.player == null && !loadSlot(slot)) {
            slot.uri = null
            slot.customLabel = null
            btn.text = slot.displayLabel(index)
            btn.tooltip = Tooltip("Right-click to load a sound file")
            saveConfig()
        }
        setIdleStyle(slot)

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
                section = MenuSection.ARRANGE,
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
            saveConfig()
            return
        }

        btn.text = slot.customLabel
        btn.tooltip = Tooltip(file.absolutePath)
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

        slot.player = MediaPlayer(media).apply {
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
            it.text = "⏹ $label"
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
            it.tooltip = Tooltip("Right-click to load a sound file")
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
            "-fx-background-color: $color; -fx-text-fill: ${textColorFor(color)}; -fx-border-color: -tc-accent; -fx-border-width: 2;"
        }
    }

    private fun setIdleStyle(slot: SlotState) {
        val btn = slot.button ?: return
        val color = slot.colorHex
        btn.style = if (color == null) "" else "-fx-background-color: $color; -fx-text-fill: ${textColorFor(color)};"
    }

    private fun showColorPicker(slot: SlotState, btn: Button) {
        val initial = runCatching { slot.colorHex?.let { Color.web(it) } ?: Color.GRAY }
            .getOrDefault(Color.GRAY)
        val wheelSize = 220
        val wheelImage = WritableImage(wheelSize, wheelSize)
        val wheelPreview = javafx.scene.image.ImageView(wheelImage)
        val markerOuter = Circle(7.0).apply {
            fill = Color.TRANSPARENT
            stroke = Color.BLACK
            strokeWidth = 2.0
            isMouseTransparent = true
        }
        val markerInner = Circle(5.0).apply {
            fill = Color.TRANSPARENT
            stroke = Color.WHITE
            strokeWidth = 2.0
            isMouseTransparent = true
        }
        val wheelContainer = StackPane(wheelPreview, markerOuter, markerInner)
        val preview = Circle(14.0, initial)
        val brightnessSlider = Slider(0.0, 1.0, initial.brightness).apply {
            tooltip = Tooltip("Brightness")
        }
        var selectedColor = initial

        fun drawWheel() {
            val writer: PixelWriter = wheelImage.pixelWriter
            val center = wheelSize / 2.0
            val radius = center - 2.0
            val brightness = brightnessSlider.value
            for (y in 0 until wheelSize) {
                for (x in 0 until wheelSize) {
                    val dx = x - center
                    val dy = y - center
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    val pixelColor = if (distance <= radius) {
                        val saturation = (distance / radius).coerceIn(0.0, 1.0)
                        val hue = ((kotlin.math.atan2(dy, dx) * 180 / kotlin.math.PI) + 360.0) % 360.0
                        Color.hsb(hue, saturation, brightness)
                    } else {
                        Color.TRANSPARENT
                    }
                    writer.setColor(x, y, pixelColor)
                }
            }
        }

        fun updateMarkerPosition(color: Color) {
            val center = wheelSize / 2.0
            val radius = center - 2.0
            val angle = Math.toRadians(color.hue)
            val distance = color.saturation * radius
            val markerX = center + kotlin.math.cos(angle) * distance
            val markerY = center + kotlin.math.sin(angle) * distance
            markerOuter.translateX = markerX - center
            markerOuter.translateY = markerY - center
            markerInner.translateX = markerX - center
            markerInner.translateY = markerY - center
        }

        fun updateSelectionFrom(x: Double, y: Double) {
            val center = wheelSize / 2.0
            val radius = center - 2.0
            val dx = x - center
            val dy = y - center
            val distance = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtMost(radius)
            val saturation = (distance / radius).coerceIn(0.0, 1.0)
            val hue = ((kotlin.math.atan2(dy, dx) * 180 / kotlin.math.PI) + 360.0) % 360.0
            selectedColor = Color.hsb(hue, saturation, brightnessSlider.value)
            preview.fill = selectedColor
            updateMarkerPosition(selectedColor)
        }

        drawWheel()
        updateMarkerPosition(selectedColor)
        wheelContainer.setOnMousePressed { updateSelectionFrom(it.x, it.y) }
        wheelContainer.setOnMouseDragged { updateSelectionFrom(it.x, it.y) }
        brightnessSlider.valueProperty().addListener { _, _, newValue ->
            selectedColor = Color.hsb(selectedColor.hue, selectedColor.saturation, newValue.toDouble())
            preview.fill = selectedColor
            updateMarkerPosition(selectedColor)
        }
        brightnessSlider.valueChangingProperty().addListener { _, _, isChanging ->
            if (!isChanging) {
                drawWheel()
            }
        }
        brightnessSlider.setOnMouseReleased {
            drawWheel()
        }

        val dialog = Dialog<Color>().apply {
            title = "Set Button Color"
            dialogPane.content = VBox(
                8.0,
                Label("Choose a color for this button"),
                wheelContainer,
                HBox(8.0, Label("Brightness"), brightnessSlider),
                HBox(8.0, Label("Preview"), preview),
            ).apply {
                padding = Insets(8.0)
            }
            dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
            initOwner(btn.scene?.window)
            setResultConverter { buttonType -> if (buttonType == ButtonType.OK) selectedColor else null }
        }

        dialog.showAndWait().ifPresent { selected ->
            slot.colorHex = colorToHex(selected)
            setIdleStyle(slot)
            saveConfig()
        }
    }

    private fun addSlot() {
        if (slots.size >= MAX_BUTTON_COUNT) return
        slots.add(SlotState())
        renderButtons()
        saveConfig()
    }

    private fun removeSlot(slot: SlotState) {
        slot.player?.dispose()
        slots.remove(slot)
        renderButtons()
        saveConfig()
    }

    /**
     * Chooses black or white text for [hex] button backgrounds using the ITU-R BT.601
     * luma approximation (`0.299R + 0.587G + 0.114B`). A threshold of `0.55` keeps
     * labels readable across the brighter custom colours users commonly pick.
     */
    private fun textColorFor(hex: String): String {
        val color = runCatching { Color.web(hex) }.getOrDefault(Color.GRAY)
        val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
        return if (luminance > 0.55) "#000000" else "#FFFFFF"
    }

    private fun colorToHex(color: Color): String {
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }

    private fun saveConfig() {
        val payload = slots.map { SlotConfig(it.customLabel, it.uri, it.colorHex) }
        runCatching {
            configFile.writeText(serializeConfig(payload))
        }
    }

    private fun loadConfig(): List<SlotConfig>? =
        runCatching {
            if (!configFile.exists()) return null
            parseConfig(configFile.readText())
        }.getOrNull()
}
