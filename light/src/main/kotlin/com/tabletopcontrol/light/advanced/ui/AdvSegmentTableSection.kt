package com.tabletopcontrol.light.advanced.ui

import com.tabletopcontrol.light.advanced.AdvancedLightCoordinator
import com.tabletopcontrol.light.advanced.AdvancedSegment
import javafx.collections.ListChangeListener
import javafx.geometry.Pos
import javafx.scene.control.CheckBox
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.TableCell
import javafx.scene.control.TableColumn
import javafx.scene.control.TableRow
import javafx.scene.control.TableView
import javafx.scene.control.TextInputDialog
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

/**
 * Segment table for the Advanced Lighting plugin.
 *
 * Columns:
 * - **Name** — double-click or right-click → Rename to edit.
 * - **On** — checkbox to toggle the segment on/off.
 * - **Edit** — checkbox to include this segment in the control-panel edits below.
 *
 * Right-click context menu on a row:
 * - Rename
 * - Move Up
 * - Move Down
 */
internal class AdvSegmentTableSection(
    private val coordinator: AdvancedLightCoordinator,
    private val onSelectionChanged: () -> Unit,
) {

    fun create(): VBox {
        val table = buildTable()

        val refreshBtn = buildRefreshButton(table)

        val header = HBox(8.0, Label("Segments"), refreshBtn).apply {
            alignment = Pos.CENTER_LEFT
        }

        return VBox(6.0, header, table).also {
            VBox.setVgrow(table, Priority.ALWAYS)
        }
    }

    // ── table construction ────────────────────────────────────────────────────

    private fun buildTable(): TableView<AdvancedSegment> {
        val nameCol = buildNameColumn()
        val onCol = buildOnColumn()
        val editCol = buildEditColumn()

        val table = TableView(coordinator.segments).apply {
            columns.addAll(nameCol, onCol, editCol)
            columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
            prefHeight = 180.0
            placeholder = Label("Connect to WLED to load segments")
            setRowFactory { buildRowFactory() }
        }

        // Notify controls section whenever segment selection changes
        coordinator.segments.addListener(ListChangeListener { onSelectionChanged() })

        return table
    }

    private fun buildNameColumn(): TableColumn<AdvancedSegment, String> {
        val col = TableColumn<AdvancedSegment, String>("Name").apply {
            minWidth = 80.0
        }
        col.setCellValueFactory { cell -> cell.value.nameProperty }
        return col
    }

    private fun buildOnColumn(): TableColumn<AdvancedSegment, Boolean> {
        val col = TableColumn<AdvancedSegment, Boolean>("On").apply {
            prefWidth = 44.0
            minWidth = 44.0
            maxWidth = 60.0
            isSortable = false
        }
        col.setCellValueFactory { cell -> cell.value.onProperty }
        col.setCellFactory {
            object : TableCell<AdvancedSegment, Boolean>() {
                private val check = CheckBox().apply {
                    tooltip = Tooltip("Toggle this segment on or off")
                }

                override fun updateItem(item: Boolean?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        graphic = null
                        return
                    }
                    check.isSelected = item
                    check.selectedProperty().removeListener(::onChecked)
                    check.selectedProperty().addListener(::onChecked)
                    graphic = check
                }

                private fun onChecked(
                    @Suppress("UNUSED_PARAMETER") obs: Any,
                    @Suppress("UNUSED_PARAMETER") old: Boolean,
                    newVal: Boolean,
                ) {
                    val seg = tableRow?.item ?: return
                    if (seg.isOn != newVal) {
                        seg.isOn = newVal
                        coordinator.sendSegmentUpdate(seg)
                    }
                }
            }
        }
        return col
    }

    private fun buildEditColumn(): TableColumn<AdvancedSegment, Boolean> {
        val col = TableColumn<AdvancedSegment, Boolean>("Edit").apply {
            prefWidth = 44.0
            minWidth = 44.0
            maxWidth = 60.0
            isSortable = false
        }
        col.setCellValueFactory { cell -> cell.value.selectedForEditProperty }
        col.setCellFactory {
            object : TableCell<AdvancedSegment, Boolean>() {
                private val check = CheckBox().apply {
                    tooltip = Tooltip("Include in editor controls below")
                }

                override fun updateItem(item: Boolean?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        graphic = null
                        return
                    }
                    check.isSelected = item
                    check.selectedProperty().removeListener(::onChecked)
                    check.selectedProperty().addListener(::onChecked)
                    graphic = check
                }

                private fun onChecked(
                    @Suppress("UNUSED_PARAMETER") obs: Any,
                    @Suppress("UNUSED_PARAMETER") old: Boolean,
                    newVal: Boolean,
                ) {
                    val seg = tableRow?.item ?: return
                    if (seg.isSelectedForEdit != newVal) {
                        seg.isSelectedForEdit = newVal
                        onSelectionChanged()
                    }
                }
            }
        }
        return col
    }

    // ── row factory with context menu ─────────────────────────────────────────

    private fun buildRowFactory(): TableRow<AdvancedSegment> {
        val row = TableRow<AdvancedSegment>()

        val renameItem = MenuItem("Rename")
        val moveUpItem = MenuItem("Move Up")
        val moveDownItem = MenuItem("Move Down")
        val contextMenu = ContextMenu(renameItem, SeparatorMenuItem(), moveUpItem, moveDownItem)

        renameItem.setOnAction { row.item?.let { renameSegment(it) } }
        moveUpItem.setOnAction { row.item?.let { moveSegmentUp(it) } }
        moveDownItem.setOnAction { row.item?.let { moveSegmentDown(it) } }

        row.contextMenuProperty().bind(
            javafx.beans.binding.Bindings.`when`(row.emptyProperty())
                .then<ContextMenu?>(null)
                .otherwise(contextMenu)
        )

        // Double-click on name cell to rename
        row.setOnMouseClicked { event ->
            if (event.clickCount == 2 && !row.isEmpty) {
                row.item?.let { renameSegment(it) }
            }
        }

        return row
    }

    private fun renameSegment(segment: AdvancedSegment) {
        val dialog = TextInputDialog(segment.name).apply {
            title = "Rename Segment"
            headerText = "Enter a new name for segment ${segment.id}:"
            contentText = "Name:"
        }
        dialog.showAndWait().ifPresent { newName ->
            val trimmed = newName.trim()
            if (trimmed.isNotEmpty()) segment.name = trimmed
        }
    }

    private fun moveSegmentUp(segment: AdvancedSegment) {
        val list = coordinator.segments
        val idx = list.indexOf(segment)
        if (idx > 0) {
            list.remove(idx, idx + 1)
            list.add(idx - 1, segment)
        }
    }

    private fun moveSegmentDown(segment: AdvancedSegment) {
        val list = coordinator.segments
        val idx = list.indexOf(segment)
        if (idx >= 0 && idx < list.size - 1) {
            list.remove(idx, idx + 1)
            list.add(idx + 1, segment)
        }
    }

    // ── refresh button ────────────────────────────────────────────────────────

    private fun buildRefreshButton(table: TableView<AdvancedSegment>) =
        javafx.scene.control.Button("↻ Refresh").apply {
            tooltip = Tooltip("Re-query segments from the connected WLED device")
            setOnAction {
                isDisable = true
                coordinator.refreshSegmentsAsync { _ ->
                    isDisable = false
                    table.refresh()
                }
            }
        }
}
