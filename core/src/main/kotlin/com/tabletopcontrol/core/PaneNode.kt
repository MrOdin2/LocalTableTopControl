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
