package com.tabletopcontrol.core.ui.reorder

/**
 * Shared helpers for drag-drop reorder orchestration above UI primitives.
 *
 * Use [planDropReorder] to validate source/target indices and convert a drop-target
 * index (which may be an append target at `listSize`) into the post-removal insertion
 * index expected by list mutation APIs. For APIs that already accept final direct target
 * indices (for example `move(from, to)`), use [planDirectReorder].
 *
 * Example:
 * ```kotlin
 * val plan = ReorderSupport.planDropReorder(items.size, fromIndex = 1, dropTargetIndex = 4)
 * if (plan != null) {
 *     ReorderSupport.reorderMutableList(items, plan)
 * }
 * ```
 */
object ReorderSupport {

    /**
     * Immutable reorder plan computed from drag-drop indices.
     *
     * @property fromIndex source item index in the pre-move list.
     * @property toIndex insertion index in the post-removal list.
     */
    data class Plan(
        val fromIndex: Int,
        val toIndex: Int,
    )

    /**
     * Validates drag-drop indices and computes a [Plan] for list mutation.
     *
     * Returns `null` when indices are out of range or the operation is a no-op.
     * Supports append targets by accepting `dropTargetIndex == listSize`.
     */
    fun planDropReorder(listSize: Int, fromIndex: Int, dropTargetIndex: Int): Plan? {
        if (fromIndex !in 0 until listSize || dropTargetIndex !in 0..listSize) return null
        val adjustedToIndex = if (fromIndex < dropTargetIndex) dropTargetIndex - 1 else dropTargetIndex
        return if (adjustedToIndex == fromIndex) null else Plan(fromIndex, adjustedToIndex)
    }

    /**
     * Validates and plans a direct index move where [targetIndex] is already the final
     * post-move index (for APIs like `move(from, to)`).
     *
     * Returns `null` when indices are out of range or the operation is a no-op.
     */
    fun planDirectReorder(listSize: Int, fromIndex: Int, targetIndex: Int): Plan? {
        if (fromIndex !in 0 until listSize || targetIndex !in 0 until listSize) return null
        return if (targetIndex == fromIndex) null else Plan(fromIndex, targetIndex)
    }

    /**
     * Applies a validated [plan] to [items].
     */
    fun <T> reorderMutableList(items: MutableList<T>, plan: Plan) {
        val moved = items.removeAt(plan.fromIndex)
        items.add(plan.toIndex, moved)
    }

    /**
     * Convenience helper that plans and applies a reorder in one call.
     *
     * Returns `true` when a reorder was applied, `false` when the input was invalid
     * or represented a no-op.
     */
    fun <T> reorderMutableListFromDrop(items: MutableList<T>, fromIndex: Int, dropTargetIndex: Int): Boolean {
        val plan = planDropReorder(items.size, fromIndex, dropTargetIndex) ?: return false
        reorderMutableList(items, plan)
        return true
    }
}
