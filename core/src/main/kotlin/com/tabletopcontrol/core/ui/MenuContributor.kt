package com.tabletopcontrol.core.ui

/**
 * Extension interface that allows plugins to contribute or override actions in shared
 * context menus built by [ContextMenuRenderer].
 *
 * Implementors are passed to [ContextMenuRenderer.build] alongside the host's base
 * action list.  The renderer merges all contributions: if a contributor returns an
 * action whose [MenuAction.id] matches a base action, the contributor's version wins.
 *
 * **Registration** — a plugin that wishes to contribute to menus should implement this
 * interface (in addition to [com.tabletopcontrol.core.DmPlugin]) and pass `this` (or
 * a dedicated contributor instance) to whatever component hosts the menu.
 *
 * **Example:**
 * ```kotlin
 * class MyPlugin : DmPlugin, MenuContributor {
 *
 *     override val displayName = "My Plugin"
 *     override fun createView(): Node = buildUi()
 *
 *     override fun contributeActions(context: Any?): List<MenuAction> = listOf(
 *         MenuAction(
 *             id        = "myplugin.refresh",
 *             label     = "Refresh",
 *             icon      = "🔄",
 *             section   = MenuSection.BASIC,
 *             onAction  = { doRefresh() },
 *         ),
 *     )
 * }
 * ```
 */
interface MenuContributor {

    /**
     * Returns the list of [MenuAction]s this contributor wants to add or override.
     *
     * @param context Caller-supplied context object describing what was right-clicked
     *                (e.g. a domain item, its index, …), or `null` when no item-specific
     *                context is available.
     * @return List of actions; may be empty.
     */
    fun contributeActions(context: Any?): List<MenuAction>
}
