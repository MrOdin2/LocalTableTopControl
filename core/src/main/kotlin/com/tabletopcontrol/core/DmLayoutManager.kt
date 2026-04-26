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
 * - *Add Panel to Left*  — splits the pane horizontally; new pane appears on the left.
 * - *Add Panel to Right* — splits the pane horizontally; new pane appears on the right.
 * - *Add Panel Above*    — splits the pane vertically; new pane appears above.
 * - *Add Panel Below*    — splits the pane vertically; new pane appears below.
 * - *Change Plugin…* — swaps the plugin shown in this pane.
 * - *Close Pane* — removes this pane (disabled when it is the only pane).
 * - *Extend Left / Right / Above / Below* — expands this pane into the space of
 *   exactly one neighbouring panel (potentially by splitting that panel’s area)
 *   without affecting any other panels; only shown when such an expansion is possible.
 */
class DmLayoutManager(private val plugins: List<DmPlugin>) {
    /**
     * SAM wrapper around the single menu lifecycle operation this manager needs: [hide].
     *
     * Using this narrow abstraction keeps lifecycle tracking decoupled from the full
     * JavaFX [ContextMenu] API while still allowing tests to validate replacement logic.
     * This exists because the manager must reliably hide any previously open menu before
     * showing a new one so only one context menu is visible at a time.
     */
    internal fun interface ManagedMenu {
        fun hide()
    }

    /** Lookup map from display name to plugin instance. */
    private val pluginMap: Map<String, DmPlugin> = plugins.associateBy { it.displayName }

    /** Current layout tree (immutable; replaced on every structural change). */
    private var layoutRoot: PaneNode = LayoutSerializer.load() ?: defaultLayout()
    private var activeContextMenu: ManagedMenu? = null

    /**
     * The top-level [BorderPane] that hosts the split-pane layout.
     * Embed this node into the DM scene.
     */
    val container: BorderPane = BorderPane().also { it.center = buildView(layoutRoot) }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Reads live divider positions from the JavaFX scene graph, updates [layoutRoot],
     * and persists the result using [LayoutSerializer.save].
     *
     * Call this method immediately before the application exits.
     */
    fun saveLayout() {
        layoutRoot = syncDividers(layoutRoot, container.center)
        LayoutSerializer.save(layoutRoot)
    }

    /**
     * Rebuilds every visible plugin view while preserving the current split layout.
     */
    fun refreshViews() {
        rebuild(syncDividers(layoutRoot, container.center))
    }

    // ── Layout building ──────────────────────────────────────────────────────

    /**
     * Returns the initial layout tree to use when no saved layout file exists.
     *
     * When plugins are available, returns a [PaneNode.Leaf] for the first loaded plugin.
     * When the plugin list is empty, returns a sentinel [PaneNode.Leaf] with an empty name
     * so [buildLeafView] can display a dedicated "no plugins loaded" placeholder instead of
     * the generic "Plugin not found" error message.
     */
    private fun defaultLayout(): PaneNode =
        if (plugins.isEmpty()) PaneNode.Leaf("") else PaneNode.Leaf(plugins.first().displayName)

    /**
     * Builds the placeholder node shown in the DM panel when no plugins are loaded.
     * This is a distinct path from the generic "plugin not found" error message.
     */
    private fun buildEmptyPlaceholder(): Node =
        StackPane(Label("DM Panel — no plugins loaded").apply { padding = Insets(16.0) })

    /**
     * Replaces [layoutRoot] with [newRoot] and rebuilds the entire JavaFX sub-tree
     * inside [container].
     */
    private fun rebuild(newRoot: PaneNode) {
        activeContextMenu?.hide()
        activeContextMenu = null
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
        val content: Node = when {
            leaf.pluginName.isEmpty() -> buildEmptyPlaceholder()
            else -> pluginMap[leaf.pluginName]?.createView()
                ?: Label("Plugin not found: ${leaf.pluginName}").apply {
                    padding = Insets(16.0)
                    style = "-fx-text-fill: -tc-error;"
                }
        }
        val wrapper = StackPane(content)
        wrapper.setOnContextMenuRequested { event ->
            val menu = buildContextMenu(leaf)
            val managedMenu = registerAndPrepareMenu(
                ManagedMenu {
                    menu.hide()
                },
            )
            menu.setOnHidden {
                clearActiveMenuIf(managedMenu)
            }
            menu.show(wrapper, event.screenX, event.screenY)
            event.consume()
        }
        return wrapper
    }

    /**
     * Hides the currently tracked menu (if any), then stores and returns [menu] as active.
     *
     * Call this immediately before showing a newly built context menu.
     */
    internal fun registerAndPrepareMenu(menu: ManagedMenu): ManagedMenu {
        activeContextMenu?.hide()
        activeContextMenu = menu
        return menu
    }

    /**
     * Clears active menu tracking only when [menu] is still the tracked instance.
     *
     * This is intended for `onHidden` callbacks so stale events from older menus do not
     * clear a newer active menu.
     */
    internal fun clearActiveMenuIf(menu: ManagedMenu) {
        if (activeContextMenu === menu) activeContextMenu = null
    }

    /** Exposes the currently tracked active menu for focused unit tests. */
    internal fun activeMenuForTesting(): ManagedMenu? = activeContextMenu

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

        val splitLeft = MenuItem("Add Panel to Left")
        splitLeft.setOnAction { promptSplit(leaf, Orientation.HORIZONTAL, newPaneFirst = true) }

        val splitRight = MenuItem("Add Panel to Right")
        splitRight.setOnAction { promptSplit(leaf, Orientation.HORIZONTAL, newPaneFirst = false) }

        val splitAbove = MenuItem("Add Panel Above")
        splitAbove.setOnAction { promptSplit(leaf, Orientation.VERTICAL, newPaneFirst = true) }

        val splitBelow = MenuItem("Add Panel Below")
        splitBelow.setOnAction { promptSplit(leaf, Orientation.VERTICAL, newPaneFirst = false) }

        val changePlugin = MenuItem("Change Plugin…")
        changePlugin.setOnAction { promptChangePlugin(leaf) }

        val closePane = MenuItem("Close Pane")
        closePane.setOnAction { doClosePane(leaf) }
        // Disable close when this leaf is the only remaining pane.
        closePane.isDisable = layoutRoot is PaneNode.Leaf

        menu.items.addAll(
            splitLeft,
            splitRight,
            splitAbove,
            splitBelow,
            SeparatorMenuItem(),
            changePlugin,
            SeparatorMenuItem(),
            closePane,
        )

        // Extend options — only shown when there are valid directions to expand into.
        // Sync live SplitPane divider positions back into the layout model so that
        // extend options are computed from the current visual state.
        syncDividers(layoutRoot, container.center)
        val extendOptions = computeExtendOptions(layoutRoot, leaf)
        if (extendOptions.isNotEmpty()) {
            menu.items.add(SeparatorMenuItem())
            extendOptions.forEach { (label, newRoot) ->
                val item = MenuItem(label)
                item.setOnAction { rebuild(newRoot) }
                menu.items.add(item)
            }
        }

        return menu
    }

    /**
     * Opens a plugin-chooser dialog and splits [leaf] along [orientation].
     *
     * When [newPaneFirst] is `true` the new pane is placed before [leaf] (i.e. to
     * the left for a horizontal split, or above for a vertical split).  When
     * [newPaneFirst] is `false` the new pane is placed after [leaf] (right / below).
     */
    private fun promptSplit(leaf: PaneNode.Leaf, orientation: Orientation, newPaneFirst: Boolean) {
        val names = plugins.map { it.displayName }
        if (names.isEmpty()) return

        val dialog = ChoiceDialog(names.first(), names)
        dialog.title = "Add Plugin Pane"
        dialog.headerText = "Choose a plugin for the new pane:"
        dialog.contentText = "Plugin:"

        dialog.showAndWait().ifPresent { chosenName ->
            val newLeaf = PaneNode.Leaf(chosenName)
            val split = if (newPaneFirst) {
                PaneNode.Split(orientation, 0.5, newLeaf, leaf)
            } else {
                PaneNode.Split(orientation, 0.5, leaf, newLeaf)
            }
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
