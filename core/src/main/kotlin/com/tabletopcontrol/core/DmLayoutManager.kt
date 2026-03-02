package com.tabletopcontrol.core

import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.control.ChoiceDialog
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.control.SplitPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane

/**
 * Manages the DM panel split-pane layout.
 *
 * The layout is represented as an immutable [PaneNode] tree. Structural changes
 * (split, close, change plugin) produce a new tree and trigger a full rebuild of
 * the JavaFX node hierarchy inside [container].
 *
 * **Usage**
 * ```kotlin
 * val mgr = DmLayoutManager(loadedPlugins)
 * dmRoot.center = mgr.container       // plug into the scene graph
 * // on app shutdown:
 * mgr.saveLayout()
 * ```
 *
 * **Layout persistence**
 * Call [saveLayout] before the application exits to write the current tree
 * (including live divider positions) to disk via [LayoutSerializer].
 * On the next start-up the saved layout is restored automatically; if no file
 * is found the default layout (a single pane showing the first plugin) is used.
 *
 * **Right-click context menu** (on any leaf pane)
 * - *Add Panel to Right* — splits the pane horizontally (left / right).
 * - *Add Panel Below* — splits the pane vertically (top / bottom).
 * - *Change Plugin…* — swaps the plugin shown in this pane.
 * - *Close Pane* — removes this pane (disabled when it is the only pane).
 */
class DmLayoutManager(private val plugins: List<DmPlugin>) {

    /** Lookup map from display name to plugin instance. */
    private val pluginMap: Map<String, DmPlugin> = plugins.associateBy { it.displayName }

    /** Current layout tree (immutable; replaced on every structural change). */
    private var layoutRoot: PaneNode = LayoutSerializer.load() ?: defaultLayout()

    /**
     * The top-level [BorderPane] that hosts the split-pane layout.
     * Embed this node into the DM scene.
     */
    val container: BorderPane = BorderPane().also { it.center = buildView(layoutRoot) }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Reads live divider positions from the JavaFX scene graph, updates [layoutRoot],
     * and persists the result to [LayoutSerializer.configFile].
     *
     * Call this method immediately before the application exits.
     */
    fun saveLayout() {
        layoutRoot = syncDividers(layoutRoot, container.center)
        LayoutSerializer.save(layoutRoot)
    }

    // ── Layout building ──────────────────────────────────────────────────────

    /** Returns a [PaneNode.Leaf] for the first loaded plugin, or a placeholder name. */
    private fun defaultLayout(): PaneNode =
        PaneNode.Leaf(plugins.firstOrNull()?.displayName ?: "Map")

    /**
     * Replaces [layoutRoot] with [newRoot] and rebuilds the entire JavaFX sub-tree
     * inside [container].
     */
    private fun rebuild(newRoot: PaneNode) {
        layoutRoot = newRoot
        container.center = buildView(layoutRoot)
    }

    /**
     * Recursively converts a [PaneNode] tree into a JavaFX node tree.
     *
     * - [PaneNode.Leaf] → plugin view wrapped in a [StackPane] with a context menu.
     * - [PaneNode.Split] → a [SplitPane] whose two items are the recursively built children.
     */
    private fun buildView(node: PaneNode): Node = when (node) {
        is PaneNode.Leaf -> buildLeafView(node)
        is PaneNode.Split -> buildSplitView(node)
    }

    private fun buildLeafView(leaf: PaneNode.Leaf): Node {
        val content: Node = pluginMap[leaf.pluginName]?.createView()
            ?: Label("Plugin not found: ${leaf.pluginName}").apply {
                padding = Insets(16.0)
                style = "-fx-text-fill: #cc4444;"
            }
        val wrapper = StackPane(content)
        wrapper.setOnContextMenuRequested { event ->
            buildContextMenu(leaf).show(wrapper, event.screenX, event.screenY)
            event.consume()
        }
        return wrapper
    }

    private fun buildSplitView(split: PaneNode.Split): Node {
        val splitPane = SplitPane()
        splitPane.orientation = split.orientation
        splitPane.items.addAll(buildView(split.first), buildView(split.second))
        splitPane.setDividerPositions(split.dividerPosition)
        return splitPane
    }

    // ── Context menu ─────────────────────────────────────────────────────────

    private fun buildContextMenu(leaf: PaneNode.Leaf): ContextMenu {
        val menu = ContextMenu()

        val splitRight = MenuItem("Add Panel to Right")
        splitRight.setOnAction { promptSplit(leaf, Orientation.HORIZONTAL) }

        val splitBelow = MenuItem("Add Panel Below")
        splitBelow.setOnAction { promptSplit(leaf, Orientation.VERTICAL) }

        val changePlugin = MenuItem("Change Plugin…")
        changePlugin.setOnAction { promptChangePlugin(leaf) }

        val closePane = MenuItem("Close Pane")
        closePane.setOnAction { doClosePane(leaf) }
        // Disable close when this leaf is the only remaining pane.
        closePane.isDisable = layoutRoot is PaneNode.Leaf

        menu.items.addAll(
            splitRight,
            splitBelow,
            SeparatorMenuItem(),
            changePlugin,
            SeparatorMenuItem(),
            closePane,
        )
        return menu
    }

    private fun promptSplit(leaf: PaneNode.Leaf, orientation: Orientation) {
        val names = plugins.map { it.displayName }
        if (names.isEmpty()) return

        val dialog = ChoiceDialog(names.first(), names)
        dialog.title = "Add Plugin Pane"
        dialog.headerText = "Choose a plugin for the new pane:"
        dialog.contentText = "Plugin:"

        dialog.showAndWait().ifPresent { chosenName ->
            val split = PaneNode.Split(orientation, 0.5, leaf, PaneNode.Leaf(chosenName))
            rebuild(replaceNode(layoutRoot, leaf, split))
        }
    }

    private fun promptChangePlugin(leaf: PaneNode.Leaf) {
        val names = plugins.map { it.displayName }
        if (names.isEmpty()) return

        val current = names.firstOrNull { it == leaf.pluginName } ?: names.first()
        val dialog = ChoiceDialog(current, names)
        dialog.title = "Change Plugin"
        dialog.headerText = "Choose the plugin to display in this pane:"
        dialog.contentText = "Plugin:"

        dialog.showAndWait().ifPresent { chosenName ->
            rebuild(replaceNode(layoutRoot, leaf, PaneNode.Leaf(chosenName)))
        }
    }

    private fun doClosePane(leaf: PaneNode.Leaf) {
        val newRoot = removeNode(layoutRoot, leaf) ?: return
        rebuild(newRoot)
    }

    // ── Divider synchronisation ──────────────────────────────────────────────

    /**
     * Walks [node] and [view] in lock-step to read live divider positions from
     * [SplitPane] instances and return an updated [PaneNode] tree.
     *
     * This must be called just before persisting the layout so that the saved
     * divider positions reflect the user's last manual adjustment.
     */
    private fun syncDividers(node: PaneNode, view: Node): PaneNode {
        if (node !is PaneNode.Split) return node
        val sp = view as? SplitPane ?: return node
        val pos = sp.dividerPositions.firstOrNull() ?: node.dividerPosition
        val firstView = sp.items.getOrNull(0) ?: return node
        val secondView = sp.items.getOrNull(1) ?: return node
        return node.copy(
            dividerPosition = pos,
            first = syncDividers(node.first, firstView),
            second = syncDividers(node.second, secondView),
        )
    }
}
