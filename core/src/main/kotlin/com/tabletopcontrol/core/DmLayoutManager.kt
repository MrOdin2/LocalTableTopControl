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
 * - *Extend Left / Right / Above / Below* — expands this pane to absorb exactly one
 *   neighbouring panel (only shown when such an expansion is possible without
 *   overwriting more than one panel).
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
     * and persists the result using [LayoutSerializer.save].
     *
     * Call this method immediately before the application exits.
     */
    fun saveLayout() {
        layoutRoot = syncDividers(layoutRoot, container.center)
        LayoutSerializer.save(layoutRoot)
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
        val extendOptions = computeExtendOptions(leaf)
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
     * Computes the extend options available for [leaf].
     *
     * A leaf can extend in a direction if doing so covers exactly one neighbouring panel:
     * - **Same-level**: the sibling within the same parent split is a single [PaneNode.Leaf].
     *   The leaf grows to absorb the sibling, equivalent to closing the sibling.
     * - **Cross-level**: the uncle (parent's sibling within the grandparent split) is a
     *   single [PaneNode.Leaf].  The leaf grows across the grandparent split boundary,
     *   and the displaced sibling is recombined with the uncle on the other side.
     *
     * Directions that would overwrite more than one panel (sibling or uncle is a [PaneNode.Split])
     * are not included.  When both same-level and cross-level would produce the same direction
     * label the same-level option takes priority.
     *
     * @return A [LinkedHashMap] mapping each direction label (e.g. `"Extend Right"`) to the
     *   resulting layout tree.  The map preserves insertion order so the menu items appear in
     *   a consistent left / right / above / below sequence.
     */
    private fun computeExtendOptions(leaf: PaneNode.Leaf): Map<String, PaneNode> {
        val ancestry = findAncestry(layoutRoot, leaf)
        val options = LinkedHashMap<String, PaneNode>()

        val parent = ancestry.parent ?: return options
        val posInParent = ancestry.posInParent ?: return options
        val sibling = if (posInParent == ChildPos.FIRST) parent.second else parent.first

        // Same-level extension: leaf expands into its sibling (only when sibling is a single Leaf).
        if (sibling is PaneNode.Leaf) {
            val label = when {
                parent.orientation == Orientation.HORIZONTAL && posInParent == ChildPos.FIRST  -> "Extend Right"
                parent.orientation == Orientation.HORIZONTAL && posInParent == ChildPos.SECOND -> "Extend Left"
                parent.orientation == Orientation.VERTICAL   && posInParent == ChildPos.FIRST  -> "Extend Below"
                else                                                                            -> "Extend Above"
            }
            // Removing the sibling collapses the parent, leaving the leaf in the parent's place.
            removeNode(layoutRoot, sibling)?.let { options[label] = it }
        }

        // Cross-level extension: leaf expands across the grandparent split into the uncle
        // (only when uncle is a single Leaf).
        val grandParent = ancestry.grandParent ?: return options
        val posOfParentInGP = ancestry.posOfParentInGP ?: return options
        val uncle = if (posOfParentInGP == ChildPos.FIRST) grandParent.second else grandParent.first

        if (uncle is PaneNode.Leaf) {
            val label = when {
                grandParent.orientation == Orientation.HORIZONTAL && posOfParentInGP == ChildPos.FIRST  -> "Extend Right"
                grandParent.orientation == Orientation.HORIZONTAL && posOfParentInGP == ChildPos.SECOND -> "Extend Left"
                grandParent.orientation == Orientation.VERTICAL   && posOfParentInGP == ChildPos.FIRST  -> "Extend Below"
                else                                                                                     -> "Extend Above"
            }
            if (!options.containsKey(label)) {
                // The sibling and the uncle are recombined using the grandParent's orientation so
                // their relative positions (sibling where parent was, uncle where uncle was) are kept.
                val combined = if (posOfParentInGP == ChildPos.FIRST) {
                    PaneNode.Split(grandParent.orientation, 0.5, sibling, uncle)
                } else {
                    PaneNode.Split(grandParent.orientation, 0.5, uncle, sibling)
                }
                // The leaf and the combined node replace the grandParent, using the parent's
                // orientation so the leaf stays on the same visual side it occupied before.
                val newGPNode = if (posInParent == ChildPos.FIRST) {
                    PaneNode.Split(parent.orientation, 0.5, leaf, combined)
                } else {
                    PaneNode.Split(parent.orientation, 0.5, combined, leaf)
                }
                options[label] = replaceNodeByRef(layoutRoot, grandParent, newGPNode)
            }
        }

        return options
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
