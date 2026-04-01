package com.tabletopcontrol.core

import javafx.geometry.Orientation

/**
 * Immutable tree model representing the DM panel split-pane layout.
 *
 * - A [Leaf] displays one plugin identified by [pluginName].
 * - A [Split] divides the available space between two child nodes along
 *   the given [orientation].
 *
 * The tree is immutable; modifications produce a new tree using the
 * [replaceNode] and [removeNode] helpers.
 */
sealed class PaneNode {

    /**
     * A leaf pane that displays the plugin identified by [pluginName].
     *
     * [pluginName] must match [DmPlugin.displayName] for the desired plugin.
     */
    data class Leaf(val pluginName: String) : PaneNode()

    /**
     * A pane split into two child panes.
     *
     * @param orientation    [Orientation.HORIZONTAL] places children side-by-side (left/right);
     *                       [Orientation.VERTICAL] stacks them (top/bottom).
     * @param dividerPosition Fractional position of the divider in [0.0, 1.0].
     * @param first           Left (or top) child.
     * @param second          Right (or bottom) child.
     */
    data class Split(
        val orientation: Orientation,
        val dividerPosition: Double,
        val first: PaneNode,
        val second: PaneNode,
    ) : PaneNode()
}

/**
 * Returns a new tree identical to [root] except that the exact [target] leaf instance
 * is replaced by [replacement].
 *
 * Reference equality (`===`) is used so that two leaves sharing the same plugin name
 * are never confused.
 */
fun replaceNode(root: PaneNode, target: PaneNode.Leaf, replacement: PaneNode): PaneNode =
    when (root) {
        is PaneNode.Leaf -> if (root === target) replacement else root
        is PaneNode.Split -> root.copy(
            first = replaceNode(root.first, target, replacement),
            second = replaceNode(root.second, target, replacement),
        )
    }

/**
 * Returns a new tree identical to [root] except that the exact [target] node (identified
 * by reference equality, `===`) is replaced by [replacement].
 *
 * Unlike [replaceNode], this works for any [PaneNode] — not just leaves — which is needed
 * when restructuring a grandparent [PaneNode.Split] during a cross-level panel extension.
 */
fun replaceNodeByRef(root: PaneNode, target: PaneNode, replacement: PaneNode): PaneNode =
    when {
        root === target -> replacement
        root is PaneNode.Split -> root.copy(
            first = replaceNodeByRef(root.first, target, replacement),
            second = replaceNodeByRef(root.second, target, replacement),
        )
        else -> root
    }

// ── Ancestry helpers ─────────────────────────────────────────────────────────

/** Position of a child within its parent [PaneNode.Split]. */
enum class ChildPos { FIRST, SECOND }

/**
 * Records the three-level ancestry of a [PaneNode.Leaf] within a tree.
 *
 * @property parent           The immediate parent [PaneNode.Split], or `null` if the leaf is the root.
 * @property posInParent      Whether the leaf is [ChildPos.FIRST] or [ChildPos.SECOND] in [parent].
 * @property grandParent      The parent of [parent], or `null` if [parent] is the root.
 * @property posOfParentInGP  Whether [parent] is [ChildPos.FIRST] or [ChildPos.SECOND] in [grandParent].
 * @property greatGrandParent The parent of [grandParent], or `null` if [grandParent] is the root.
 * @property posOfGPInGGP     Whether [grandParent] is [ChildPos.FIRST] or [ChildPos.SECOND] in [greatGrandParent].
 */
data class LeafAncestry(
    val parent: PaneNode.Split?,
    val posInParent: ChildPos?,
    val grandParent: PaneNode.Split?,
    val posOfParentInGP: ChildPos?,
    val greatGrandParent: PaneNode.Split? = null,
    val posOfGPInGGP: ChildPos? = null,
)

/**
 * Searches [root] for [target] using reference equality (`===`) and returns its three-level ancestry.
 *
 * Returns [LeafAncestry] with all-`null` fields when [target] is the root itself
 * or is not present in the tree.
 */
fun findAncestry(root: PaneNode, target: PaneNode.Leaf): LeafAncestry {
    fun search(
        node: PaneNode,
        parent: PaneNode.Split?,
        posInParent: ChildPos?,
        grandParent: PaneNode.Split?,
        posOfParentInGP: ChildPos?,
        greatGrandParent: PaneNode.Split?,
        posOfGPInGGP: ChildPos?,
    ): LeafAncestry? = when (node) {
        is PaneNode.Leaf ->
            if (node === target) LeafAncestry(parent, posInParent, grandParent, posOfParentInGP, greatGrandParent, posOfGPInGGP)
            else null
        is PaneNode.Split ->
            search(node.first, node, ChildPos.FIRST, parent, posInParent, grandParent, posOfParentInGP)
                ?: search(node.second, node, ChildPos.SECOND, parent, posInParent, grandParent, posOfParentInGP)
    }
    return search(root, null, null, null, null, null, null) ?: LeafAncestry(null, null, null, null)
}

/**
 * Maps a split [orientation] and [childPos] to the direction a child would extend
 * toward its sibling (or uncle).
 *
 * - Horizontal split, first child  → "Extend Right"
 * - Horizontal split, second child → "Extend Left"
 * - Vertical split,   first child  → "Extend Below"
 * - Vertical split,   second child → "Extend Above"
 */
fun directionLabel(orientation: Orientation, childPos: ChildPos): String = when {
    orientation == Orientation.HORIZONTAL && childPos == ChildPos.FIRST  -> "Extend Right"
    orientation == Orientation.HORIZONTAL && childPos == ChildPos.SECOND -> "Extend Left"
    orientation == Orientation.VERTICAL   && childPos == ChildPos.FIRST  -> "Extend Below"
    else                                                                  -> "Extend Above"
}

/**
 * Computes the extend options available for [leaf] within the layout tree rooted at [root].
 *
 * A leaf can extend in a direction if doing so restructures exactly one neighbouring panel.
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
 *    single [PaneNode.Leaf].  The grandparent is restructured: the leaf takes a
 *    full-width/height row/column; the displaced sibling and the uncle's surviving
 *    child are merged into the other half.
 * 4. **Same-grandparent-orientation uncle**: the uncle is a [PaneNode.Split] with
 *    the same orientation as the grandparent (which differs from the parent's
 *    orientation), and the uncle's child on the side adjacent to the parent is a
 *    single [PaneNode.Leaf].  The adjacent child's space is shared with the leaf
 *    using the parent's orientation (leaf at its own row/column position, adjacent
 *    child in the other half); the uncle is rebuilt with that new shared node; the
 *    displaced sibling takes the parent's former slot in the grandparent.
 * 5. **Great-uncle-is-Leaf**: the great-grandparent exists and its other child
 *    (the great-uncle) is a single [PaneNode.Leaf].  The great-grandparent is
 *    restructured: the leaf claims the great-uncle's former space (sharing it with
 *    a merge of the great-uncle and the displaced sibling); the uncle takes the
 *    grandparent's former slot.
 *
 * Directions that would require overwriting more than one panel are not offered.
 *
 * @return A [LinkedHashMap] mapping each direction label (e.g. `"Extend Right"`) to the
 *   resulting layout tree. The map preserves insertion order, which reflects the
 *   discovery and priority rules described above (same-level before cross-level, etc.),
 *   rather than enforcing a fixed left / right / above / below direction sequence.
 */
fun computeExtendOptions(root: PaneNode, leaf: PaneNode.Leaf): Map<String, PaneNode> {
    val ancestry = findAncestry(root, leaf)

    val parent = ancestry.parent ?: return emptyMap()
    val posInParent = ancestry.posInParent ?: return emptyMap()
    val sibling = if (posInParent == ChildPos.FIRST) parent.second else parent.first

    val options = LinkedHashMap<String, PaneNode>()

    // ── 1. Same-level ────────────────────────────────────────────────────
    // Leaf expands into its sibling (only when sibling is a single Leaf).
    if (sibling is PaneNode.Leaf) {
        val label = directionLabel(parent.orientation, posInParent)
        // Removing the sibling collapses the parent, leaving the leaf in the parent's place.
        removeNode(root, sibling)?.let { options[label] = it }
    }

    // ── 1.5. Sibling is Split with same orientation ──────────────────────
    // Sibling is a Split whose orientation matches the parent's orientation, and the
    // sibling's child adjacent to the leaf is a single Leaf.
    // The leaf absorbs the adjacent child, which is equivalent to closing it.
    if (sibling is PaneNode.Split && sibling.orientation == parent.orientation) {
        val adjacentSiblingChild = if (posInParent == ChildPos.FIRST) sibling.first else sibling.second
        if (adjacentSiblingChild is PaneNode.Leaf) {
            val label = directionLabel(parent.orientation, posInParent)
            if (!options.containsKey(label)) {
                val remainingSibling = if (posInParent == ChildPos.FIRST) sibling.second else sibling.first
                val newDivider = if (posInParent == ChildPos.FIRST) {
                    parent.dividerPosition + sibling.dividerPosition * (1.0 - parent.dividerPosition)
                } else {
                    parent.dividerPosition * sibling.dividerPosition
                }
                val newParentNode = if (posInParent == ChildPos.FIRST) {
                    PaneNode.Split(parent.orientation, newDivider, leaf, remainingSibling)
                } else {
                    PaneNode.Split(parent.orientation, newDivider, remainingSibling, leaf)
                }
                options[label] = replaceNodeByRef(root, parent, newParentNode)
            }
        }
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
                PaneNode.Split(grandParent.orientation, grandParent.dividerPosition, sibling, uncle)
            } else {
                PaneNode.Split(grandParent.orientation, grandParent.dividerPosition, uncle, sibling)
            }
            // The leaf and the combined node replace the grandParent, using the parent's
            // orientation so the leaf stays on the same visual side it occupied before.
            val newGPNode = if (posInParent == ChildPos.FIRST) {
                PaneNode.Split(parent.orientation, parent.dividerPosition, leaf, combined)
            } else {
                PaneNode.Split(parent.orientation, parent.dividerPosition, combined, leaf)
            }
            options[label] = replaceNodeByRef(root, grandParent, newGPNode)
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
                    PaneNode.Split(grandParent.orientation, grandParent.dividerPosition, sibling, remainingUncle)
                } else {
                    PaneNode.Split(grandParent.orientation, grandParent.dividerPosition, remainingUncle, sibling)
                }
                // Leaf and merged replace the grandParent, using the parent's orientation so
                // the leaf stays in its own row/column position.
                val newGPNode = if (posInParent == ChildPos.FIRST) {
                    PaneNode.Split(parent.orientation, parent.dividerPosition, leaf, merged)
                } else {
                    PaneNode.Split(parent.orientation, parent.dividerPosition, merged, leaf)
                }
                options[label] = replaceNodeByRef(root, grandParent, newGPNode)
            }
        }
    }

    // ── 4. Same-grandparent-orientation uncle ────────────────────────────
    // Uncle is a Split whose orientation matches the grandParent's orientation
    // (but differs from the parent's), and the uncle's child on the side adjacent
    // to the parent (i.e. at posOfParentInGP) is a single Leaf.
    //
    // The adjacent uncle child's space is merged with the displaced sibling,
    // and the leaf shares the parent's split orientation with that merged node.
    // The uncle is then rebuilt with that new shared node in place of its
    // original adjacent child.
    if (uncle is PaneNode.Split && uncle.orientation == grandParent.orientation) {
        val adjacentUncleChild =
            if (posOfParentInGP == ChildPos.FIRST) uncle.first else uncle.second
        if (adjacentUncleChild is PaneNode.Leaf) {
            val label = directionLabel(grandParent.orientation, posOfParentInGP)
            if (!options.containsKey(label)) {
                val merged = if (posOfParentInGP == ChildPos.FIRST) {
                    PaneNode.Split(grandParent.orientation, grandParent.dividerPosition, sibling, adjacentUncleChild)
                } else {
                    PaneNode.Split(grandParent.orientation, grandParent.dividerPosition, adjacentUncleChild, sibling)
                }
                val newSharedNode = if (posInParent == ChildPos.FIRST) {
                    PaneNode.Split(parent.orientation, parent.dividerPosition, leaf, merged)
                } else {
                    PaneNode.Split(parent.orientation, parent.dividerPosition, merged, leaf)
                }
                val newGPNode = if (posOfParentInGP == ChildPos.FIRST) {
                    PaneNode.Split(uncle.orientation, uncle.dividerPosition, newSharedNode, uncle.second)
                } else {
                    PaneNode.Split(uncle.orientation, uncle.dividerPosition, uncle.first, newSharedNode)
                }
                options[label] = replaceNodeByRef(root, grandParent, newGPNode)
            }
        }
    }

    // ── 5. Great-uncle-is-Leaf ───────────────────────────────────────────
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
                PaneNode.Split(greatGrandParent.orientation, greatGrandParent.dividerPosition, sibling, greatUncle)
            } else {
                // great-uncle was FIRST → at FIRST; grandParent was SECOND → sibling at SECOND
                PaneNode.Split(greatGrandParent.orientation, greatGrandParent.dividerPosition, greatUncle, sibling)
            }
            // Leaf and merged are placed in a new node using the parent's orientation,
            // so the leaf stays in its own row/column position.
            val newNode = if (posInParent == ChildPos.FIRST) {
                PaneNode.Split(parent.orientation, parent.dividerPosition, leaf, merged)
            } else {
                PaneNode.Split(parent.orientation, parent.dividerPosition, merged, leaf)
            }
            // newNode goes where the great-uncle was; uncle takes the grandParent's former slot.
            val newGGPNode = if (posOfGPInGGP == ChildPos.FIRST) {
                // grandParent was FIRST → uncle at FIRST; great-uncle was SECOND → newNode at SECOND
                PaneNode.Split(grandParent.orientation, grandParent.dividerPosition, uncle, newNode)
            } else {
                // great-uncle was FIRST → newNode at FIRST; grandParent was SECOND → uncle at SECOND
                PaneNode.Split(grandParent.orientation, grandParent.dividerPosition, newNode, uncle)
            }
            options[label] = replaceNodeByRef(root, greatGrandParent, newGGPNode)
        }
    }

    return options
}

/**
 * Returns a new tree identical to [root] with [target] removed, collapsing the parent
 * split so the sibling takes its place.
 *
 * Returns `null` when [target] is the root itself (i.e. the only remaining pane),
 * so the caller can avoid leaving the layout empty.
 */
fun removeNode(root: PaneNode, target: PaneNode.Leaf): PaneNode? =
    when (root) {
        is PaneNode.Leaf -> if (root === target) null else root
        is PaneNode.Split -> {
            val newFirst = removeNode(root.first, target)
            val newSecond = removeNode(root.second, target)
            when {
                newFirst == null -> newSecond
                newSecond == null -> newFirst
                else -> root.copy(first = newFirst, second = newSecond)
            }
        }
    }
