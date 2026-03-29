package com.tabletopcontrol.tracker

import com.tabletopcontrol.core.ActiveTokenChangedEvent
import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.TokenAddedEvent
import com.tabletopcontrol.core.TokenImageChangedEvent
import com.tabletopcontrol.core.TokenRemovedEvent
import com.tabletopcontrol.core.TokensResetEvent
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.input.ClipboardContent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.shape.Rectangle
import javafx.scene.shape.Shape
import javafx.scene.transform.Scale
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.stage.FileChooser
import java.io.File
import java.net.URI
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale
import java.util.UUID

/**
 * DM-panel plugin providing a combined initiative and HP/AC tracker.
 *
 * Each combatant is represented by a **card** that contains:
 * - An editable **name** field.
 * - An editable **AC** (armour class) number field.
 * - An editable **HP** (hit points) number field.
 * - A **×** button to remove that combatant.
 *
 * A **toolbar** above the card list contains:
 * - **−** — remove all combatants (with confirmation dialog).
 * - **Next ▶** — advance the active-card pointer to the next combatant.
 * - **Round N** label — displays the current round number.
 *
 * The active combatant's card is highlighted with a coloured border so the DM
 * can see at a glance whose turn it is.
 *
 * The card list adapts its orientation to the available space:
 * - Wider than tall → cards arranged **horizontally** (left-to-right order).
 * - Taller than wide → cards arranged **vertically** (top-to-bottom order).
 *
 * Cards can be **dragged and dropped** to reorder the initiative list.
 */
class TrackerPlugin : DmPlugin {

    override val displayName: String = "Tracker"

    /** Shared combatant state; persists across pane rebuilds within a session. */
    private val tracker = InitiativeTracker()

    /**
     * Monotonically increasing counter for assigning token colours.  Never resets on
     * removal, so the next added token always gets a colour not already in use among
     * recently added tokens (up to [TOKEN_COLORS].size combatants).
     */
    private var tokenColorIndex: Int = 0

    /**
     * Stable UUIDs for each combatant, parallel to [tracker.entries].
     * `tokenIds[i]` is the id of `tracker.entries[i]`.  Must be kept in sync
     * whenever entries are added, removed, moved, or cleared.
     */
    private val tokenIds: MutableList<String> = mutableListOf()

    /**
     * Maps each combatant's stable id to the token colour that was assigned when it
     * was added.  Keyed by id (not name) so colour lookups survive renames.
     * Used to render the matching colour swatch on each tracker card.
     */
    private val tokenColors: MutableMap<String, Color> = mutableMapOf()

    /**
     * Maps each combatant's stable id to the file URI of its token picture, or `null`
     * when no picture has been uploaded.  Keyed by id so the mapping survives renames.
     * The current value is republished as [TokenImageChangedEvent] whenever the DM
     * picks a new file in the per-card image button.
     */
    private val tokenImages: MutableMap<String, TokenImageSettings> = mutableMapOf()

    private data class TokenImageSettings(
        val uri: String?,
        val scaleX: Double = 1.0,
        val scaleY: Double = 1.0,
        val offsetX: Double = 0.0,
        val offsetY: Double = 0.0,
    )

    override fun createView(): Node {
        val roundLabel = Label(roundText()).apply {
            style = "-fx-font-weight: bold;"
        }

        val scroll = ScrollPane().apply {
            isFitToWidth = true
            isFitToHeight = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        }

        var orientation = Orientation.VERTICAL

        fun refresh() {
            roundLabel.text = roundText()
            scroll.content = buildCardPane(orientation) { refresh() }
        }

        // "−" Remove-all button.
        val removeAllBtn = Button("−").apply {
            tooltip = Tooltip("Remove all combatants")
            setOnAction {
                val alert = Alert(Alert.AlertType.CONFIRMATION).apply {
                    title = "Remove All"
                    headerText = "Remove all combatants?"
                    contentText = "This will clear the entire initiative order."
                    buttonTypes.setAll(ButtonType.YES, ButtonType.NO)
                }
                if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                    tracker.reset()
                    tokenColorIndex = 0
                    tokenIds.clear()
                    tokenColors.clear()
                    tokenImages.clear()
                    EventBus.publish(TokensResetEvent())
                    refresh()
                }
            }
        }

        // "Next ▶" button — advance the active-card pointer.
        val nextBtn = Button("Next ▶").apply {
            tooltip = Tooltip("Advance to the next combatant")
            setOnAction {
                tracker.next()
                EventBus.publish(
                    ActiveTokenChangedEvent(
                        tokenIds.getOrNull(tracker.currentIndex),
                        tracker.currentEntry?.name,
                    ),
                )
                refresh()
            }
        }

        // Toolbar: [−]  [Next ▶]  Round N
        val toolbar = HBox(8.0, removeAllBtn, nextBtn, roundLabel).apply {
            padding = Insets(4.0, 8.0, 4.0, 8.0)
            alignment = javafx.geometry.Pos.CENTER_LEFT
        }

        // Root: toolbar on top, scrollable card list below.
        val root = VBox(0.0, toolbar, scroll).apply {
            VBox.setVgrow(scroll, Priority.ALWAYS)
        }

        // Re-evaluate orientation whenever the viewport is resized.
        scroll.viewportBoundsProperty().addListener { _, _, bounds ->
            val newOri = if (bounds.width > bounds.height && bounds.height > 0)
                Orientation.HORIZONTAL else Orientation.VERTICAL
            if (newOri != orientation) {
                orientation = newOri
                refresh()
            }
        }

        refresh()
        return root
    }

    // ── UI builders ──────────────────────────────────────────────────────────

    /**
     * Builds the scrollable card pane for the given [orientation].
     *
     * - [Orientation.HORIZONTAL]: an [HBox] → cards … `[+]` (left-to-right).
     * - [Orientation.VERTICAL]:   a [VBox]  → cards … `[+]` (top-to-bottom).
     *
     * The **−** and **Next ▶** controls live in the toolbar above and are
     * not duplicated here.
     *
     * @param orientation the current layout direction.
     * @param refresh     callback invoked after any structural model change.
     */
    private fun buildCardPane(orientation: Orientation, refresh: () -> Unit): Pane {
        val container: Pane = when (orientation) {
            Orientation.HORIZONTAL -> HBox(8.0)
            else -> VBox(8.0)
        }
        container.padding = Insets(8.0)

        // One card per combatant.
        for (i in tracker.entries.indices) {
            container.children.add(buildCard(i, orientation, refresh))
        }

        // "+" Add button at the end of the order.
        val addBtn = Button("+").apply {
            tooltip = Tooltip("Add combatant")
            setOnAction {
                val name = "Combatant ${tracker.entries.size + 1}"
                val color = TOKEN_COLORS[tokenColorIndex++ % TOKEN_COLORS.size]
                val id = UUID.randomUUID().toString()
                val previousActiveId = tokenIds.getOrNull(tracker.currentIndex)
                tracker.add(name, 0)
                // tracker.add() sorts by initiative; with all initiatives equal (0) the
                // new entry goes to the end (stable sort), so appending the id is correct.
                tokenIds.add(id)
                tokenColors[id] = color
                tokenImages[id] = TokenImageSettings(uri = null)
                EventBus.publish(TokenAddedEvent(id, name, color))
                // When the tracker was empty before, currentIndex advances from -1 to 0.
                if (tokenIds.getOrNull(tracker.currentIndex) != previousActiveId) {
                    EventBus.publish(
                        ActiveTokenChangedEvent(
                            tokenIds.getOrNull(tracker.currentIndex),
                            tracker.currentEntry?.name,
                        ),
                    )
                }
                refresh()
            }
        }
        container.children.add(addBtn)

        return container
    }

    /**
     * Builds a single combatant card for the entry at [index].
     *
     * The card is a [VBox] with two rows:
     * - **Name row**: `[color swatch] [Name field (grows)] [×]`
     * - **Stats row**: `AC: [field]  HP: [field]`
     *
     * The color swatch is a small filled circle whose color matches the combatant's
     * map token, making it easy to pair cards with tokens at a glance.
     *
     * The card is both a drag source and a drop target; dropping another card
     * onto this card reorders the two in the initiative list.
     *
     * @param index       zero-based position in [tracker.entries].
     * @param orientation current list orientation (used for sizing hints).
     * @param refresh     callback invoked after any structural model change.
     */
    private fun buildCard(index: Int, orientation: Orientation, refresh: () -> Unit): VBox {
        val entry = tracker.entries[index]

        // Name field — updates the model on every keystroke.
        val nameField = TextField(entry.name).apply {
            promptText = "Name"
            textProperty().addListener { _, _, new ->
                tracker.updateEntry(index, name = new)
            }
            if (orientation == Orientation.HORIZONTAL) prefWidth = 110.0
            else HBox.setHgrow(this, Priority.ALWAYS)
        }

        // "×" Remove button.
        val removeBtn = Button("×").apply {
            tooltip = Tooltip("Remove this combatant")
            setOnAction {
                val id = tokenIds[index]
                val name = tracker.entries[index].name
                tracker.remove(index)
                tokenIds.removeAt(index)
                tokenColors.remove(id)
                tokenImages.remove(id)
                EventBus.publish(TokenRemovedEvent(id, name))
                EventBus.publish(
                    ActiveTokenChangedEvent(
                        tokenIds.getOrNull(tracker.currentIndex),
                        tracker.currentEntry?.name,
                    ),
                )
                refresh()
            }
        }

        // AC field — updates the model when a valid integer is entered.
        val acField = TextField(if (entry.ac == 0) "" else entry.ac.toString()).apply {
            promptText = "AC"
            prefColumnCount = 4
            textProperty().addListener { _, _, new ->
                val v = new.toIntOrNull()
                if (v != null) tracker.updateEntry(index, ac = v)
            }
        }

        // HP field — updates the model when a valid integer is entered.
        val hpField = TextField(if (entry.hp == 0) "" else entry.hp.toString()).apply {
            promptText = "HP"
            prefColumnCount = 4
            textProperty().addListener { _, _, new ->
                val v = new.toIntOrNull()
                if (v != null) tracker.updateEntry(index, hp = v)
            }
        }

        // Color swatch — a small circle whose fill matches the combatant's map token.
        val swatchColor = tokenIds.getOrNull(index)?.let { tokenColors[it] }
        val swatch = Region().apply {
            minWidth = 14.0; maxWidth = 14.0
            minHeight = 14.0; maxHeight = 14.0
            val hex = swatchColor?.let { colorToHex(it) } ?: "#cccccc"
            style = "-fx-background-color: $hex; -fx-background-radius: 7;"
            Tooltip.install(this, Tooltip("Map token colour"))
        }

        // Image button — lets the DM assign a picture to this token.
        val tokenId = tokenIds.getOrNull(index)
        val currentImageSettings = tokenId?.let { tokenImages[it] } ?: TokenImageSettings(uri = null)
        val hasImage = currentImageSettings.uri != null
        val imgBtn = Button(if (hasImage) "🖼✓" else "🖼").apply {
            accessibleText = if (hasImage) "Token image set" else "No token image set"
            tooltip = Tooltip(
                if (hasImage) "Token has a custom picture — click to change it"
                else "Upload a picture for this token",
            )
            style = "-fx-min-width: 32px; -fx-max-width: 32px;"
            setOnAction { e ->
                val id = tokenIds.getOrNull(index) ?: return@setOnAction
                val owner = (e.source as? Button)?.scene?.window
                val chosen = showTokenImageDialog(owner, tokenImages[id] ?: TokenImageSettings(uri = null))
                if (chosen != null) {
                    tokenImages[id] = chosen
                    EventBus.publish(
                        TokenImageChangedEvent(
                            id = id,
                            imageUri = chosen.uri,
                            imageScaleX = chosen.scaleX,
                            imageScaleY = chosen.scaleY,
                            imageOffsetX = chosen.offsetX,
                            imageOffsetY = chosen.offsetY,
                        ),
                    )
                    // Refresh the card so the button label updates.
                    refresh()
                }
            }
        }

        val nameRow = HBox(4.0, swatch, nameField, imgBtn, removeBtn).also {
            HBox.setHgrow(nameField, Priority.ALWAYS)
        }
        val statsRow = HBox(4.0, Label("AC:"), acField, Label("HP:"), hpField)

        val card = VBox(4.0, nameRow, statsRow).apply {
            padding = Insets(6.0)
            style = cardStyle(index)
            minWidth = if (orientation == Orientation.HORIZONTAL) 160.0 else 180.0
            if (orientation == Orientation.VERTICAL) maxWidth = Double.MAX_VALUE
        }

        // ── Drag source ───────────────────────────────────────────────────────
        card.setOnDragDetected { e ->
            val db = card.startDragAndDrop(TransferMode.MOVE)
            val content = ClipboardContent()
            content.putString(index.toString())
            db.setContent(content)
            e.consume()
        }

        // ── Drag target ───────────────────────────────────────────────────────
        card.setOnDragOver { e ->
            if (e.gestureSource !== card && e.dragboard.hasString()) {
                e.acceptTransferModes(TransferMode.MOVE)
                card.style = CARD_STYLE_DRAG_OVER
            }
            e.consume()
        }

        card.setOnDragExited { e ->
            card.style = cardStyle(index)
            e.consume()
        }

        card.setOnDragDropped { e ->
            val fromIdx = e.dragboard.getString().toIntOrNull()
            if (fromIdx != null && fromIdx != index) {
                tracker.move(fromIdx, index)
                // Keep the id list in sync with the reordered entries.
                val movedId = tokenIds.removeAt(fromIdx)
                tokenIds.add(index, movedId)
                refresh()
            }
            e.isDropCompleted = true
            e.consume()
        }

        return card
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private companion object {
        private const val SLIDER_VALUE_EPSILON = 1e-9

        private const val CARD_STYLE_NORMAL =
            "-fx-border-color: #888888; -fx-border-radius: 4; " +
                "-fx-background-color: #f5f5f5; -fx-background-radius: 4;"

        private const val CARD_STYLE_ACTIVE =
            "-fx-border-color: #e67e00; -fx-border-width: 2; -fx-border-radius: 4; " +
                "-fx-background-color: #fff3e0; -fx-background-radius: 4;"

        private const val CARD_STYLE_DRAG_OVER =
            "-fx-border-color: #4488ff; -fx-border-radius: 4; " +
                "-fx-background-color: #e8f0ff; -fx-background-radius: 4;"

        /**
         * 64 perceptually distinct token colours generated from 16 evenly spaced hues
         * across the full colour wheel, each at four (saturation × brightness) variants:
         * - Vivid   (s=1.0, b=0.90) — adds 1–16
         * - Light   (s=0.55, b=1.0) — adds 17–32
         * - Dark    (s=1.0, b=0.55) — adds 33–48
         * - Muted   (s=0.45, b=0.80) — adds 49–64
         *
         * Successive adds cycle through all 16 hues within a variant group before
         * moving on to the next variant, maximising perceptual distance between
         * consecutively added combatants.
         */
        private val TOKEN_COLORS: List<Color> = run {
            val hues = List(16) { it * 22.5 }
            val variants = listOf(
                Pair(1.00, 0.90),   // vivid
                Pair(0.55, 1.00),   // light
                Pair(1.00, 0.55),   // dark
                Pair(0.45, 0.80),   // muted
            )
            List(64) { i -> Color.hsb(hues[i % 16], variants[i / 16].first, variants[i / 16].second) }
        }

        /**
         * Converts a JavaFX [Color] to a CSS hex string (e.g. `"#ff8800"`).
         */
        fun colorToHex(color: Color): String =
            "#%02x%02x%02x".format(
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt(),
            )
    }

    private fun normalizeSupportedTokenImageUri(uri: String): String? {
        return try {
            val trimmed = uri.trim()
            if (trimmed.isEmpty()) {
                return null
            }

            val parsed = URI(trimmed)
            val scheme = parsed.scheme?.lowercase(Locale.ROOT)

            when {
                // No scheme → treat as a local file system path, return canonical file: URI.
                scheme == null || scheme.isEmpty() ->
                    File(trimmed).canonicalFile.toURI().toString()

                // Explicit file: URI → normalize the URI representation and return it.
                scheme == "file" ->
                    parsed.normalize().toString()

                // Any other scheme (http, https, etc.) is not supported.
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Returns the resting style for a card at [index] based on whether it is the active combatant. */
    private fun cardStyle(index: Int): String =
        if (index == tracker.currentIndex) CARD_STYLE_ACTIVE else CARD_STYLE_NORMAL

    /** Formats the round counter text from the current tracker state. */
    private fun roundText(): String = "Round: ${tracker.round}"

    // ── Token image dialog ─────────────────────────────────────────────────────

    private fun showTokenImageDialog(owner: javafx.stage.Window?, initial: TokenImageSettings): TokenImageSettings? {
        val dialog = Dialog<TokenImageSettings>().apply {
            title = "Token Image"
            headerText = "Select a picture and adjust scale/position"
            owner?.let { initOwner(it) }
            dialogPane.buttonTypes.setAll(ButtonType.OK, ButtonType.CANCEL)
        }

        var working = initial

        val previewSize = 160.0
        val previewRadius = 70.0
        val previewCenter = previewSize / 2

        val imageView = ImageView().apply {
            fitWidth = previewRadius * 2
            fitHeight = previewRadius * 2
            isPreserveRatio = true
        }
        val scaleTransform = Scale(working.scaleX, working.scaleY, previewRadius, previewRadius)
        imageView.transforms.setAll(scaleTransform)

        val tokenCircle = Circle(previewCenter, previewCenter, previewRadius).apply {
            fill = Color.TRANSPARENT
            stroke = Color.web("#bbbbbb")
            strokeWidth = 1.5
        }

        val outsideOverlay = Shape.subtract(
            Rectangle(0.0, 0.0, previewSize, previewSize),
            Circle(previewCenter, previewCenter, previewRadius),
        ).apply {
            fill = Color.gray(0.4, 0.35)
        }

        val previewPane = StackPane(
            Rectangle(previewSize, previewSize, Color.web("#f7f7f7")).apply {
                stroke = Color.web("#dddddd")
            },
            imageView,
            outsideOverlay,
            tokenCircle,
        ).apply {
            minWidth = previewSize
            maxWidth = previewSize
            minHeight = previewSize
            maxHeight = previewSize
        }

        fun loadImage(uri: String?): Boolean {
            if (uri == null) {
                imageView.image = null
                return true
            }
            val normalizedUri = normalizeSupportedTokenImageUri(uri)
            if (normalizedUri == null) {
                Alert(Alert.AlertType.ERROR).apply {
                    title = "Image load failed"
                    headerText = "Unsupported image location"
                    contentText = "Only local file images are supported."
                }.showAndWait()
                return false
            }

            val image = try {
                Image(normalizedUri, false)
            } catch (e: Exception) {
                Alert(Alert.AlertType.ERROR).apply {
                    title = "Image load failed"
                    headerText = "Could not load image"
                    contentText = buildString {
                        appendLine("Failed to load image from:")
                        appendLine(uri)
                        val message = e.message
                        if (!message.isNullOrBlank()) {
                            appendLine()
                            append("Details: ")
                            append(message)
                        }
                    }
                }.showAndWait()
                return false
            }

            if (image.isError) {
                val message = image.exception?.message
                Alert(Alert.AlertType.ERROR).apply {
                    title = "Image load failed"
                    headerText = "Could not load image"
                    contentText = buildString {
                        appendLine("Failed to load image from:")
                        appendLine(uri)
                        if (!message.isNullOrBlank()) {
                            appendLine()
                            append("Details: ")
                            append(message)
                        }
                    }
                }.showAndWait()
                return false
            }

            imageView.image = image
            return true
        }

        fun applyTransforms(settings: TokenImageSettings) {
            scaleTransform.x = settings.scaleX
            scaleTransform.y = settings.scaleY
            imageView.translateX = settings.offsetX
            imageView.translateY = settings.offsetY
        }

        loadImage(working.uri)
        applyTransforms(working)

        val scaleXSlider = Slider(0.3, 3.0, working.scaleX).apply { isShowTickLabels = true }
        val scaleYSlider = Slider(0.3, 3.0, working.scaleY).apply { isShowTickLabels = true }
        val offsetXSlider = Slider(-80.0, 80.0, working.offsetX).apply { isShowTickLabels = true }
        val offsetYSlider = Slider(-80.0, 80.0, working.offsetY).apply { isShowTickLabels = true }

        fun bindSliderToField(slider: Slider, field: TextField, decimals: Int = 2) {
            val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = decimals
                maximumFractionDigits = decimals
                isGroupingUsed = false
            }
            fun formatValue(value: Double): String = numberFormat.format(value)
            fun parseValue(text: String): Double? {
                val raw = text.trim()
                if (raw.isEmpty()) return null
                val parsePosition = ParsePosition(0)
                val parsed = numberFormat.parse(raw, parsePosition) ?: return null
                if (parsePosition.index != raw.length) return null
                return parsed.toDouble()
            }
            slider.valueProperty().addListener { _, _, v ->
                val value = v.toDouble()
                if (!field.isFocused) field.text = formatValue(value)
            }
            field.text = formatValue(slider.value)
            field.textProperty().addListener { _, _, text ->
                // Only apply typed values while the field is focused to prevent
                // programmatic slider->text updates from snapping slider precision.
                if (!field.isFocused) return@addListener
                val parsed = parseValue(text) ?: return@addListener
                val clamped = parsed.coerceIn(slider.min, slider.max)
                if (kotlin.math.abs(clamped - slider.value) > SLIDER_VALUE_EPSILON) {
                    slider.value = clamped
                }
            }
            field.focusedProperty().addListener { _, _, focused ->
                if (!focused) {
                    val parsed = parseValue(field.text)
                    if (parsed == null) {
                        // Revert to the current slider value if the text is not a valid number.
                        field.text = formatValue(slider.value)
                    } else {
                        val clamped = parsed.coerceIn(slider.min, slider.max)
                        if (kotlin.math.abs(clamped - slider.value) > SLIDER_VALUE_EPSILON) {
                            // Update the slider; its listener will refresh the text because the field is not focused.
                            slider.value = clamped
                        } else {
                            // Just normalize the text formatting to the effective value.
                            field.text = formatValue(slider.value)
                        }
                    }
                }
            }
        }

        val scaleXField = TextField().apply { prefColumnCount = 6 }
        val scaleYField = TextField().apply { prefColumnCount = 6 }
        val offsetXField = TextField().apply { prefColumnCount = 6 }
        val offsetYField = TextField().apply { prefColumnCount = 6 }
        bindSliderToField(scaleXSlider, scaleXField, 2)
        bindSliderToField(scaleYSlider, scaleYField, 2)
        bindSliderToField(offsetXSlider, offsetXField, 1)
        bindSliderToField(offsetYSlider, offsetYField, 1)

        scaleXSlider.valueProperty().addListener { _, _, v ->
            working = working.copy(scaleX = v.toDouble())
            applyTransforms(working)
        }
        scaleYSlider.valueProperty().addListener { _, _, v ->
            working = working.copy(scaleY = v.toDouble())
            applyTransforms(working)
        }
        offsetXSlider.valueProperty().addListener { _, _, v ->
            working = working.copy(offsetX = v.toDouble())
            applyTransforms(working)
        }
        offsetYSlider.valueProperty().addListener { _, _, v ->
            working = working.copy(offsetY = v.toDouble())
            applyTransforms(working)
        }

        val clearBtn = Button("Clear Image").apply {
            isDisable = working.uri == null
            setOnAction {
                working = working.copy(uri = null)
                loadImage(null)
                isDisable = true
            }
        }

        val chooseBtn = Button("Choose Image…").apply {
            setOnAction {
                val chooser = FileChooser().apply {
                    title = "Select token image"
                    extensionFilters.addAll(
                        FileChooser.ExtensionFilter(
                            "Image files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif",
                        ),
                        FileChooser.ExtensionFilter("All files", "*.*"),
                    )
                }
                val file = chooser.showOpenDialog(owner)
                if (file != null) {
                    val uri = file.toURI().toString()
                    if (loadImage(uri)) {
                        working = working.copy(uri = uri)
                        applyTransforms(working)
                        clearBtn.isDisable = false
                    }
                }
            }
        }

        val content = VBox(
            10.0,
            previewPane,
            HBox(8.0, chooseBtn, clearBtn),
            Label("Scale X"), HBox(8.0, scaleXSlider, scaleXField).apply { HBox.setHgrow(scaleXSlider, Priority.ALWAYS) },
            Label("Scale Y"), HBox(8.0, scaleYSlider, scaleYField).apply { HBox.setHgrow(scaleYSlider, Priority.ALWAYS) },
            Label("Offset X"), HBox(8.0, offsetXSlider, offsetXField).apply { HBox.setHgrow(offsetXSlider, Priority.ALWAYS) },
            Label("Offset Y"), HBox(8.0, offsetYSlider, offsetYField).apply { HBox.setHgrow(offsetYSlider, Priority.ALWAYS) },
        ).apply { padding = Insets(10.0) }

        dialog.dialogPane.content = content
        dialog.setResultConverter { button ->
            if (button == ButtonType.OK) working else null
        }

        return dialog.showAndWait().orElse(null)
    }
}
