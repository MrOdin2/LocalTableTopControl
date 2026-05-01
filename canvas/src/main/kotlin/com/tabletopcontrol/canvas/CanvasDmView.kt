package com.tabletopcontrol.canvas

import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.ui.dialog.FileChooserHistoryStore
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.ToggleButton
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.shape.Circle
import javafx.scene.shape.Rectangle
import javafx.scene.transform.Rotate
import javafx.stage.FileChooser
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val CANVAS_HISTORY_KEY = "canvas.load"
private const val CORNER_HANDLE_SIZE = 9.0
private const val EDGE_HANDLE_RADIUS = 5.0

/**
 * Creates the DM-side pane for the Canvas plugin.
 *
 * The DM can:
 * - Load pictures via right-click on the workspace or the toolbar button
 * - Drag pictures to reposition them
 * - Resize pictures by dragging the four corner handles
 * - Rotate pictures by dragging the four mid-edge handles
 * - Toggle per-picture sharing via the right-click menu
 * - Toggle sharing of all pictures at once via the "Share All" button
 * - Remove pictures via the right-click menu
 *
 * The workspace pane uses a dashed border to indicate it represents the
 * player-facing table view.
 */
class CanvasDmView(private val model: CanvasModel) {

    private val itemNodes = mutableMapOf<String, CanvasItemNode>()
    private var selectedNode: CanvasItemNode? = null

    /** The interactive workspace that represents the player table view. */
    private val workspace: Pane = object : Pane() {
        override fun layoutChildren() {
            super.layoutChildren()
            if (width > 0 && height > 0) {
                itemNodes.values.forEach { it.layout(width, height) }
            }
        }
    }.apply {
        minWidth = 100.0
        minHeight = 80.0
        style = "-fx-border-color: -tc-border; -fx-border-style: dashed; " +
            "-fx-border-width: 2; -fx-background-color: -tc-bg;"

        setOnMousePressed { event ->
            if (event.button == MouseButton.PRIMARY && event.target == this) {
                selectNode(null)
            }
        }

        setOnContextMenuRequested { event ->
            val menu = buildWorkspaceContextMenu(event.x / width, event.y / height)
            menu.show(this, event.screenX, event.screenY)
            event.consume()
        }
    }

    /** Creates and returns the DM panel node. */
    fun createView(): Node {
        val shareToggle = ToggleButton("Share All").apply {
            tooltip = Tooltip("Project all pictures onto the player table view")
            selectedProperty().addListener { _, _, on -> model.setShareAll(on) }
        }

        val addButton = javafx.scene.control.Button("Add picture…").apply {
            tooltip = Tooltip("Load a picture from disk")
            setOnAction {
                loadPicture(0.1, 0.1, workspace.scene?.window)
            }
        }

        val spacer = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }

        val hint = Label("Right-click workspace to add pictures").apply {
            style = "-fx-text-fill: -tc-text-muted; -fx-font-size: 10;"
        }

        val toolbar = HBox(6.0, shareToggle, addButton, spacer, hint).apply {
            padding = Insets(4.0)
            alignment = Pos.CENTER_LEFT
            style = "-fx-background-color: -tc-surface;"
        }

        val tableLabel = Label("Table View").apply {
            style = "-fx-text-fill: -tc-text-muted; -fx-font-size: 10; -fx-padding: 2 4 0 4;"
        }

        val workspaceWrapper = BorderPane().apply {
            top = tableLabel
            center = workspace
            BorderPane.setMargin(workspace, Insets(0.0, 4.0, 4.0, 4.0))
        }
        VBox.setVgrow(workspaceWrapper, Priority.ALWAYS)

        EventBus.subscribe<CanvasItemsChangedEvent> { event ->
            syncItems(event.items)
        }

        return VBox().apply {
            children.addAll(toolbar, workspaceWrapper)
        }
    }

    // ─── Context menu ────────────────────────────────────────────────────────

    private fun buildWorkspaceContextMenu(normX: Double, normY: Double): ContextMenu {
        val load = MenuItem("Load picture…")
        load.setOnAction { loadPicture(normX, normY, workspace.scene?.window) }
        return ContextMenu(load)
    }

    // ─── File loading ────────────────────────────────────────────────────────

    private fun loadPicture(normX: Double, normY: Double, owner: Any?) {
        val chooser = FileChooser().apply {
            title = "Load Picture"
            extensionFilters.addAll(
                FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
                FileChooser.ExtensionFilter("All Files", "*.*"),
            )
            FileChooserHistoryStore.configureInitialDirectory(this, CANVAS_HISTORY_KEY)
        }
        val window = owner as? javafx.stage.Window ?: return
        val file = chooser.showOpenDialog(window) ?: return
        FileChooserHistoryStore.rememberSelection(CANVAS_HISTORY_KEY, file)

        val item = CanvasItem(
            filePath = file.absolutePath,
            x = normX.coerceIn(0.0, 0.7),
            y = normY.coerceIn(0.0, 0.8),
            width = 0.3,
            height = 0.2,
        )
        model.addItem(item)
    }

    // ─── Item synchronisation ────────────────────────────────────────────────

    private fun syncItems(items: List<CanvasItem>) {
        val currentIds = items.map { it.id }.toSet()

        // Remove nodes for deleted items.
        itemNodes.keys.filter { it !in currentIds }.forEach { id ->
            val node = itemNodes.remove(id) ?: return@forEach
            workspace.children.remove(node.group)
            if (selectedNode == node) selectedNode = null
        }

        // Add or update nodes.
        for (item in items) {
            val existing = itemNodes[item.id]
            if (existing != null) {
                existing.item = item
                if (workspace.width > 0 && workspace.height > 0) {
                    existing.layout(workspace.width, workspace.height)
                }
            } else {
                val node = CanvasItemNode(
                    item = item,
                    onUpdate = { model.updateItem(it) },
                    onRemove = { model.removeItem(item.id) },
                    onSelect = { selectNode(it) },
                )
                itemNodes[item.id] = node
                workspace.children.add(node.group)
                if (workspace.width > 0 && workspace.height > 0) {
                    node.layout(workspace.width, workspace.height)
                }
            }
        }
    }

    private fun selectNode(node: CanvasItemNode?) {
        selectedNode?.setSelected(false)
        selectedNode = node
        node?.setSelected(true)
    }
}

// ─── Item node ───────────────────────────────────────────────────────────────

/**
 * Manages the visual representation of a single [CanvasItem] inside the
 * DM workspace pane.
 *
 * The [group] is added directly to the workspace [Pane]. It contains:
 * - An [ImageView] showing the picture.
 * - Four corner [Rectangle] handles for resizing.
 * - Four mid-edge [Circle] handles for rotating.
 *
 * Handles are only shown when the item is selected.
 */
internal class CanvasItemNode(
    item: CanvasItem,
    private val onUpdate: (CanvasItem) -> Unit,
    private val onRemove: () -> Unit,
    private val onSelect: (CanvasItemNode) -> Unit,
) {
    var item: CanvasItem = item
        set(value) {
            field = value
            loadImageIfNeeded()
        }

    val group = javafx.scene.Group()

    private val imageView = ImageView().apply {
        isPreserveRatio = false
        isSmooth = true
    }

    // Corner handles (TL, TR, BR, BL).
    private val handleTL = Rectangle(CORNER_HANDLE_SIZE, CORNER_HANDLE_SIZE)
    private val handleTR = Rectangle(CORNER_HANDLE_SIZE, CORNER_HANDLE_SIZE)
    private val handleBR = Rectangle(CORNER_HANDLE_SIZE, CORNER_HANDLE_SIZE)
    private val handleBL = Rectangle(CORNER_HANDLE_SIZE, CORNER_HANDLE_SIZE)

    // Mid-edge handles (Top, Right, Bottom, Left).
    private val handleET = Circle(EDGE_HANDLE_RADIUS) // edge-top
    private val handleER = Circle(EDGE_HANDLE_RADIUS) // edge-right
    private val handleEB = Circle(EDGE_HANDLE_RADIUS) // edge-bottom
    private val handleEL = Circle(EDGE_HANDLE_RADIUS) // edge-left

    private val cornerHandles = listOf(handleTL, handleTR, handleBR, handleBL)
    private val edgeHandles = listOf(handleET, handleER, handleEB, handleEL)
    private val allHandles: List<javafx.scene.shape.Shape> = cornerHandles + edgeHandles

    private val rotateTransform = Rotate()

    // Drag state.
    private var dragStartSceneX = 0.0
    private var dragStartSceneY = 0.0
    private var dragItemX = 0.0
    private var dragItemY = 0.0
    private var dragItemW = 0.0
    private var dragItemH = 0.0
    private var dragPaneW = 1.0
    private var dragPaneH = 1.0

    private var rotStartAngle = 0.0
    private var rotItemRot = 0.0

    private var loadedPath: String? = null

    init {
        styleHandles()
        setHandlesVisible(false)
        group.transforms.add(rotateTransform)
        group.children.addAll(
            imageView,
            handleTL, handleTR, handleBR, handleBL,
            handleET, handleER, handleEB, handleEL,
        )
        setupDragMove()
        setupCornerResize()
        setupEdgeRotate()
        setupSelect()
        setupContextMenu()
        loadImageIfNeeded()
    }

    fun setSelected(value: Boolean) {
        setHandlesVisible(value)
    }

    /** Recomputes pixel layout from normalised item coordinates and pane dimensions. */
    fun layout(paneW: Double, paneH: Double) {
        dragPaneW = paneW
        dragPaneH = paneH

        val x = item.x * paneW
        val y = item.y * paneH
        val w = item.width * paneW
        val h = item.height * paneH

        imageView.fitWidth = w
        imageView.fitHeight = h

        val hs = CORNER_HANDLE_SIZE / 2.0
        val hr = EDGE_HANDLE_RADIUS

        handleTL.x = -hs; handleTL.y = -hs
        handleTR.x = w - hs; handleTR.y = -hs
        handleBR.x = w - hs; handleBR.y = h - hs
        handleBL.x = -hs; handleBL.y = h - hs

        handleET.centerX = w / 2; handleET.centerY = -hr - 1.0
        handleER.centerX = w + hr + 1.0; handleER.centerY = h / 2
        handleEB.centerX = w / 2; handleEB.centerY = h + hr + 1.0
        handleEL.centerX = -hr - 1.0; handleEL.centerY = h / 2

        group.layoutX = x
        group.layoutY = y
        rotateTransform.angle = item.rotation
        rotateTransform.pivotX = w / 2
        rotateTransform.pivotY = h / 2
    }

    // ─── Image loading ───────────────────────────────────────────────────────

    private fun loadImageIfNeeded() {
        val path = item.filePath
        if (path == loadedPath) return
        loadedPath = path
        try {
            imageView.image = Image("file:$path", true)
        } catch (_: Exception) {
            imageView.image = null
        }
    }

    // ─── Styling ─────────────────────────────────────────────────────────────

    private fun styleHandles() {
        cornerHandles.forEach { h ->
            h.style = "-fx-fill: -tc-surface; -fx-stroke: -tc-border; -fx-stroke-width: 1;"
            h.arcWidth = 2.0
            h.arcHeight = 2.0
            h.cursor = javafx.scene.Cursor.CROSSHAIR
        }
        edgeHandles.forEach { h ->
            h.style = "-fx-fill: -tc-surface; -fx-stroke: -tc-accent; -fx-stroke-width: 1.5;"
            h.cursor = javafx.scene.Cursor.CROSSHAIR
        }
    }

    private fun setHandlesVisible(visible: Boolean) {
        allHandles.forEach { it.isVisible = visible }
    }

    // ─── Interactions ────────────────────────────────────────────────────────

    private fun setupSelect() {
        imageView.setOnMousePressed { event ->
            if (event.button == MouseButton.PRIMARY) {
                onSelect(this)
                event.consume()
            }
        }
    }

    private fun setupContextMenu() {
        val removeItem = MenuItem("Remove")
        removeItem.setOnAction { onRemove() }

        val shareCheck = CheckMenuItem("Share with Table")

        val menu = ContextMenu(shareCheck, SeparatorMenuItem(), removeItem)

        shareCheck.setOnAction {
            onUpdate(item.copy(isShared = shareCheck.isSelected))
        }

        imageView.setOnContextMenuRequested { event ->
            shareCheck.isSelected = item.isShared
            menu.show(imageView, event.screenX, event.screenY)
            event.consume()
        }
    }

    private fun setupDragMove() {
        imageView.setOnMousePressed { event ->
            if (event.button == MouseButton.PRIMARY) {
                dragStartSceneX = event.sceneX
                dragStartSceneY = event.sceneY
                dragItemX = item.x
                dragItemY = item.y
                updatePaneDimensions()
                event.consume()
            }
        }
        imageView.setOnMouseDragged { event ->
            if (event.button == MouseButton.PRIMARY) {
                val dx = (event.sceneX - dragStartSceneX) / dragPaneW
                val dy = (event.sceneY - dragStartSceneY) / dragPaneH
                val newX = (dragItemX + dx).coerceIn(0.0, max(0.0, 1.0 - item.width))
                val newY = (dragItemY + dy).coerceIn(0.0, max(0.0, 1.0 - item.height))
                onUpdate(item.copy(x = newX, y = newY))
                event.consume()
            }
        }
    }

    private fun setupCornerResize() {
        /** cornerX: -1 = left anchor, +1 = right anchor; cornerY: -1 = top, +1 = bottom. */
        fun install(handle: Rectangle, cornerX: Int, cornerY: Int) {
            handle.setOnMousePressed { event ->
                if (event.button == MouseButton.PRIMARY) {
                    dragStartSceneX = event.sceneX
                    dragStartSceneY = event.sceneY
                    dragItemX = item.x
                    dragItemY = item.y
                    dragItemW = item.width
                    dragItemH = item.height
                    updatePaneDimensions()
                    event.consume()
                }
            }
            handle.setOnMouseDragged { event ->
                if (event.button == MouseButton.PRIMARY) {
                    val dxScene = event.sceneX - dragStartSceneX
                    val dyScene = event.sceneY - dragStartSceneY

                    // Un-rotate scene delta to item-local space.
                    val rad = Math.toRadians(-item.rotation)
                    val localDx = (dxScene * cos(rad) - dyScene * sin(rad)) / dragPaneW
                    val localDy = (dxScene * sin(rad) + dyScene * cos(rad)) / dragPaneH

                    var newX = dragItemX
                    var newY = dragItemY
                    var newW = dragItemW
                    var newH = dragItemH

                    if (cornerX < 0) {
                        // Left corner: moving left anchor shrinks from the left.
                        val maxMove = dragItemW - 0.02
                        newX = (dragItemX + localDx).coerceIn(0.0, dragItemX + maxMove)
                        newW = dragItemW - (newX - dragItemX)
                    } else {
                        newW = max(0.02, dragItemW + localDx)
                    }
                    if (cornerY < 0) {
                        val maxMove = dragItemH - 0.02
                        newY = (dragItemY + localDy).coerceIn(0.0, dragItemY + maxMove)
                        newH = dragItemH - (newY - dragItemY)
                    } else {
                        newH = max(0.02, dragItemH + localDy)
                    }

                    onUpdate(item.copy(x = newX, y = newY, width = newW, height = newH))
                    event.consume()
                }
            }
        }

        install(handleTL, -1, -1)
        install(handleTR, +1, -1)
        install(handleBR, +1, +1)
        install(handleBL, -1, +1)
    }

    private fun setupEdgeRotate() {
        fun install(handle: Circle) {
            handle.setOnMousePressed { event ->
                if (event.button == MouseButton.PRIMARY) {
                    updatePaneDimensions()
                    val center = group.localToScene(
                        item.width * dragPaneW / 2,
                        item.height * dragPaneH / 2,
                    )
                    rotStartAngle = Math.toDegrees(
                        atan2(event.sceneY - center.y, event.sceneX - center.x),
                    )
                    rotItemRot = item.rotation
                    event.consume()
                }
            }
            handle.setOnMouseDragged { event ->
                if (event.button == MouseButton.PRIMARY) {
                    val center = group.localToScene(
                        item.width * dragPaneW / 2,
                        item.height * dragPaneH / 2,
                    )
                    val currentAngle = Math.toDegrees(
                        atan2(event.sceneY - center.y, event.sceneX - center.x),
                    )
                    val newRot = (rotItemRot + currentAngle - rotStartAngle) % 360.0
                    onUpdate(item.copy(rotation = newRot))
                    event.consume()
                }
            }
        }

        listOf(handleET, handleER, handleEB, handleEL).forEach { install(it) }
    }

    private fun updatePaneDimensions() {
        val pane = group.parent as? Pane ?: return
        dragPaneW = pane.width
        dragPaneH = pane.height
    }
}
