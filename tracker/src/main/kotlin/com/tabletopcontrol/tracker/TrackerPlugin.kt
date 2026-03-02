package com.tabletopcontrol.tracker

import com.tabletopcontrol.core.DmPlugin
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.input.ClipboardContent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

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
                    refresh()
                }
            }
        }

        // "Next ▶" button — advance the active-card pointer.
        val nextBtn = Button("Next ▶").apply {
            tooltip = Tooltip("Advance to the next combatant")
            setOnAction {
                tracker.next()
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
                tracker.add("Combatant ${tracker.entries.size + 1}", 0)
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
     * - **Name row**: `[Name field (grows)] [×]`
     * - **Stats row**: `AC: [field]  HP: [field]`
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
                tracker.remove(index)
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

        val nameRow = HBox(4.0, nameField, removeBtn).also {
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
                refresh()
            }
            e.isDropCompleted = true
            e.consume()
        }

        return card
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private companion object {
        private const val CARD_STYLE_NORMAL =
            "-fx-border-color: #888888; -fx-border-radius: 4; " +
                "-fx-background-color: #f5f5f5; -fx-background-radius: 4;"

        private const val CARD_STYLE_ACTIVE =
            "-fx-border-color: #e67e00; -fx-border-width: 2; -fx-border-radius: 4; " +
                "-fx-background-color: #fff3e0; -fx-background-radius: 4;"

        private const val CARD_STYLE_DRAG_OVER =
            "-fx-border-color: #4488ff; -fx-border-radius: 4; " +
                "-fx-background-color: #e8f0ff; -fx-background-radius: 4;"
    }

    /** Returns the resting style for a card at [index] based on whether it is the active combatant. */
    private fun cardStyle(index: Int): String =
        if (index == tracker.currentIndex) CARD_STYLE_ACTIVE else CARD_STYLE_NORMAL

    /** Formats the round counter text from the current tracker state. */
    private fun roundText(): String = "Round: ${tracker.round}"
}
