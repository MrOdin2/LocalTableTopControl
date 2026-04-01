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
