package com.tabletopcontrol.core.ui

/**
 * A plugin-agnostic description of a single context-menu action.
 *
 * Actions are assembled into a [ContextMenuRenderer]-built [javafx.scene.control.ContextMenu].
 * Multiple [MenuContributor]s may supply actions for the same menu; actions with duplicate
 * [id]s are deduplicated (contributor actions take precedence over base actions).
 *
 * @property id         Stable, unique identifier for this action (e.g. `"tracker.rename"`).
 *                      Used for contributor override/deduplication.
 * @property label      Human-readable label shown in the menu item.
 * @property icon       Optional Unicode glyph or emoji prepended to [label], e.g. `"✏️"`.
 * @property section    Which standard [MenuSection] this action belongs to.
 * @property isEnabled  When `false` the item is shown greyed-out and cannot be triggered.
 * @property isVisible  When `false` the item is excluded from the menu entirely.
 * @property requiresConfirmation
 *                      When `true` the renderer shows a confirmation dialog before
 *                      invoking [onAction].
 * @property confirmationMessage
 *                      The message shown in the confirmation dialog.  Only used when
 *                      [requiresConfirmation] is `true`.  Defaults to a generic phrase
 *                      derived from [label] when `null`.
 * @property onAction   Callback invoked when the user selects this action (and confirms, if
 *                      [requiresConfirmation] is `true`).
 */
data class MenuAction(
    val id: String,
    val label: String,
    val icon: String? = null,
    val section: MenuSection = MenuSection.BASIC,
    val isEnabled: Boolean = true,
    val isVisible: Boolean = true,
    val requiresConfirmation: Boolean = false,
    val confirmationMessage: String? = null,
    val onAction: () -> Unit,
)
