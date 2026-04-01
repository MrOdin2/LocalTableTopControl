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
     * A leaf can extend in a direction if doing so covers exactly one neighbouring panel.
     * Four kinds of extension are recognised, evaluated in priority order (higher priority
     * wins when two would produce the same direction label):
     *
     * 1. **Same-level**: sibling within the parent split is a single [PaneNode.Leaf].
     *    The leaf absorbs the sibling — equivalent to closing it.
     * 2. **Cross-level**: the uncle (parent's sibling within the grandparent split) is a
     *    single [PaneNode.Leaf].  The leaf grows across the grandparent boundary;
     *    the displaced sibling recombines with the uncle on the other side.
     * 3. **Same-uncle-orientation**: the uncle is a [PaneNode.Split] with the *same*
     *    orientation as the parent, and the uncle's child at the leaf's position is a
     *    single [PaneNode.Leaf].  Removing that one child from the uncle is equivalent
     *    to the leaf "extending through" its horizontal or vertical row.
     * 4. **Great-uncle-is-Leaf**: the great-grandparent exists and its other child
     *    (the great-uncle) is a single [PaneNode.Leaf].  The leaf grows to split
     *    the great-uncle's space, taking the slice that matches its own position within
     *    its parent split.
     *
     * Directions that would require overwriting more than one panel are not offered.
     *
     * @return A [LinkedHashMap] mapping each direction label (e.g. `"Extend Right"`) to the
     *   resulting layout tree. The map preserves insertion order, which reflects the
     *   discovery and priority rules described above (same-level before cross-level, etc.),
     *   rather than enforcing a fixed left / right / above / below direction sequence.
     */
    private fun computeExtendOptions(leaf: PaneNode.Leaf): Map<String, PaneNode> {
        val ancestry = findAncestry(layoutRoot, leaf)

        val parent = ancestry.parent ?: return emptyMap()
        val posInParent = ancestry.posInParent ?: return emptyMap()
        val sibling = if (posInParent == ChildPos.FIRST) parent.second else parent.first

        val options = LinkedHashMap<String, PaneNode>()

        // ── 1. Same-level ────────────────────────────────────────────────────
        // Leaf expands into its sibling (only when sibling is a single Leaf).
        if (sibling is PaneNode.Leaf) {
            val label = directionLabel(parent.orientation, posInParent)
            // Removing the sibling collapses the parent, leaving the leaf in the parent's place.
            removeNode(layoutRoot, sibling)?.let { options[label] = it }
        }

        val grandParent = ancestry.grandParent ?: return options
        val posOfParentInGP = ancestry.posOfParentInGP ?: return options
        val uncle = if (posOfParentInGP == ChildPos.FIRST) grandParent.second else grandParent.first

        // ── 2. Cross-level ───────────────────────────────────────────────────
        // Uncle is a single Leaf: leaf grows across the grandparent split boundary.
        if (uncle is PaneNode.Leaf) {
            val label = directionLabel(grandParent.orientation, posOfParentInGP)
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

        // ── 3. Same-uncle-orientation ────────────────────────────────────────
        // Uncle is a Split whose orientation matches the parent's orientation, and the
        // uncle's child at posInParent (the "parallel slot") is a single Leaf.
        //
        // The entire grandParent is restructured: the leaf takes its row/column slice
        // across the full grandParent width/height; the displaced sibling and the uncle's
        // surviving child are merged into the other half using the grandParent's orientation
        // (preserving left/right or top/bottom positions).
        //
        // Example — H(V(Tracker,Map), V(Music,Soundboard)), Tracker extends right:
        //   merged = H(Map, Soundboard)      — sibling left, remainingUncle right (posOfParentInGP=FIRST)
        //   newGPNode = V(Tracker, H(Map,Soundboard))
        if (uncle is PaneNode.Split && uncle.orientation == parent.orientation) {
            val correspondingUncleChild =
                if (posInParent == ChildPos.FIRST) uncle.first else uncle.second
            if (correspondingUncleChild is PaneNode.Leaf) {
                val label = directionLabel(grandParent.orientation, posOfParentInGP)
                if (!options.containsKey(label)) {
                    // The uncle's child NOT being absorbed: it merges with the displaced sibling.
                    val remainingUncle = if (posInParent == ChildPos.FIRST) uncle.second else uncle.first
                    // Merged pair: sibling inherits the parent's visual side; remainingUncle inherits
                    // the uncle's visual side, both relative to the grandParent's orientation.
                    val merged = if (posOfParentInGP == ChildPos.FIRST) {
                        PaneNode.Split(grandParent.orientation, 0.5, sibling, remainingUncle)
                    } else {
                        PaneNode.Split(grandParent.orientation, 0.5, remainingUncle, sibling)
                    }
                    // Leaf and merged replace the grandParent, using the parent's orientation so
                    // the leaf stays in its own row/column position.
                    val newGPNode = if (posInParent == ChildPos.FIRST) {
                        PaneNode.Split(parent.orientation, 0.5, leaf, merged)
                    } else {
                        PaneNode.Split(parent.orientation, 0.5, merged, leaf)
                    }
                    options[label] = replaceNodeByRef(layoutRoot, grandParent, newGPNode)
                }
            }
        }

        // ── 4. Great-uncle-is-Leaf ───────────────────────────────────────────
        // The leaf's great-grandparent exists and its other child (the great-uncle) is a
        // single Leaf.  The entire great-grandParent is restructured: the leaf claims a new
        // column/row that spans the great-uncle's space; the displaced sibling is merged
        // with the great-uncle using the great-grandParent's orientation; the uncle
        // (grandParent's surviving child) stays unchanged in the grandParent's former slot.
        //
        // Example — H(Lights, H(V(Tracker,Map), V(Music,Soundboard))), Tracker extends left:
        //   merged    = H(Lights, Map)      — great-uncle left, sibling right (posOfGPInGGP=SECOND)
        //   newNode   = V(Tracker, H(Lights,Map))
        //   uncle     = V(Music, Soundboard)
        //   newGGPNode = H(V(Tracker,H(Lights,Map)), V(Music,Soundboard))
        val greatGrandParent = ancestry.greatGrandParent ?: return options
        val posOfGPInGGP = ancestry.posOfGPInGGP ?: return options
        val greatUncle =
            if (posOfGPInGGP == ChildPos.FIRST) greatGrandParent.second else greatGrandParent.first

        if (greatUncle is PaneNode.Leaf) {
            val label = directionLabel(greatGrandParent.orientation, posOfGPInGGP)
            if (!options.containsKey(label)) {
                // Merged pair: great-uncle keeps its visual side; displaced sibling inherits
                // the grandParent's former side, both relative to the great-grandParent's orientation.
                val merged = if (posOfGPInGGP == ChildPos.FIRST) {
                    // grandParent was FIRST → sibling at FIRST; great-uncle was SECOND → at SECOND
                    PaneNode.Split(greatGrandParent.orientation, 0.5, sibling, greatUncle)
                } else {
                    // great-uncle was FIRST → at FIRST; grandParent was SECOND → sibling at SECOND
                    PaneNode.Split(greatGrandParent.orientation, 0.5, greatUncle, sibling)
                }
                // Leaf and merged are placed in a new node using the parent's orientation,
                // so the leaf stays in its own row/column position.
                val newNode = if (posInParent == ChildPos.FIRST) {
                    PaneNode.Split(parent.orientation, 0.5, leaf, merged)
                } else {
                    PaneNode.Split(parent.orientation, 0.5, merged, leaf)
                }
                // newNode goes where the great-uncle was; uncle takes the grandParent's former slot.
                val newGGPNode = if (posOfGPInGGP == ChildPos.FIRST) {
                    // grandParent was FIRST → uncle at FIRST; great-uncle was SECOND → newNode at SECOND
                    PaneNode.Split(greatGrandParent.orientation, 0.5, uncle, newNode)
                } else {
                    // great-uncle was FIRST → newNode at FIRST; grandParent was SECOND → uncle at SECOND
                    PaneNode.Split(greatGrandParent.orientation, 0.5, newNode, uncle)
                }
                options[label] = replaceNodeByRef(layoutRoot, greatGrandParent, newGGPNode)
            }
        }

        return options
    }

    /**
     * Maps a split [orientation] and [childPos] to the direction the child would extend
     * toward the other child (its sibling or uncle).
     *
     * - Horizontal split, first child → "Extend Right"
     * - Horizontal split, second child → "Extend Left"
     * - Vertical split, first child → "Extend Below"
     * - Vertical split, second child → "Extend Above"
     */
    private fun directionLabel(orientation: Orientation, childPos: ChildPos): String = when {
        orientation == Orientation.HORIZONTAL && childPos == ChildPos.FIRST  -> "Extend Right"
        orientation == Orientation.HORIZONTAL && childPos == ChildPos.SECOND -> "Extend Left"
        orientation == Orientation.VERTICAL   && childPos == ChildPos.FIRST  -> "Extend Below"
        else                                                                  -> "Extend Above"
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
