package com.tabletopcontrol.core.ui

import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem

/**
 * Builds a JavaFX [ContextMenu] from a list of [MenuAction]s according to the standard
 * TabletopControl section ordering:
 *
 * 1. [MenuSection.BASIC]       — primary, most-used actions
 * 2. [MenuSection.APPEARANCE]  — visual / style changes
 * 3. [MenuSection.ARRANGE]     — ordering / structural changes
 * 4. [MenuSection.DANGER_ZONE] — destructive actions (with optional confirmation dialog)
 *
 * Sections are separated by [SeparatorMenuItem]s; sections that contain no visible
 * actions are omitted so no orphaned separators appear.
 *
 * Additional actions contributed by [MenuContributor]s are merged with the base list
 * before rendering: contributor actions override base actions that share the same
 * [MenuAction.id].
 */
object ContextMenuRenderer {

    /**
     * Builds a [ContextMenu] from [actions].
     *
     * @param actions      Base list of actions provided by the menu host.
     * @param contributors Optional [MenuContributor]s; their contributions are merged by
     *                     [MenuAction.id] (contributors take precedence over base actions).
     * @param context      Forwarded verbatim to [MenuContributor.contributeActions].
     * @return The assembled [ContextMenu], ready to be shown.
     */
    fun build(
        actions: List<MenuAction>,
        contributors: List<MenuContributor> = emptyList(),
        context: Any? = null,
    ): ContextMenu {
        val merged = mergeActions(actions, contributors, context)
        val menu = ContextMenu()
        var lastSectionHadItems = false

        MenuSection.entries
            .sortedBy { it.displayOrder }
            .forEach { section ->
                val sectionItems = merged.filter { it.section == section && it.isVisible }
                if (sectionItems.isEmpty()) return@forEach

                if (lastSectionHadItems) menu.items.add(SeparatorMenuItem())

                sectionItems.forEach { action ->
                    val item = MenuItem(buildLabel(action))
                    item.isDisable = !action.isEnabled
                    item.setOnAction { handleAction(action) }
                    menu.items.add(item)
                }
                lastSectionHadItems = true
            }

        return menu
    }

    // ── Internal helpers — package-internal for unit tests ────────────────────

    /**
     * Merges [base] actions with contributions from [contributors].
     *
     * Contributor actions override base actions that share the same [MenuAction.id]
     * by replacing them in-place so the original base ordering is preserved.
     * When multiple contributors supply the same [MenuAction.id], the last contributor's
     * action wins (last-write semantics). Contributed actions whose ids do not exist in
     * [base] are appended after the base actions in last-write order.
     */
    internal fun mergeActions(
        base: List<MenuAction>,
        contributors: List<MenuContributor>,
        context: Any?,
    ): List<MenuAction> {
        // Build a map of id → action from all contributors; later entries overwrite earlier
        // ones for the same id.  LinkedHashMap preserves last-write insertion order for the
        // new-action append step below.
        val contributedById = linkedMapOf<String, MenuAction>()
        contributors
            .asSequence()
            .flatMap { it.contributeActions(context).asSequence() }
            .forEach { action ->
                // Remove first to update insertion position so last-write order is preserved
                contributedById.remove(action.id)
                contributedById[action.id] = action
            }

        val baseIds = base.map { it.id }.toSet()
        // Replace matching base actions in-place; non-overridden base actions are kept as-is.
        val merged = base.map { action -> contributedById[action.id] ?: action }.toMutableList()
        // Append contributed actions whose ids are not present in base, in last-write order.
        contributedById
            .filterKeys { it !in baseIds }
            .values
            .forEach { merged.add(it) }

        return merged
    }

    private fun buildLabel(action: MenuAction): String =
        if (action.icon != null) "${action.icon} ${action.label}" else action.label

    private fun handleAction(action: MenuAction) {
        if (action.requiresConfirmation) {
            val message = action.confirmationMessage
                ?: "Are you sure you want to ${action.label}?"
            val alert = Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO)
            alert.title = "Confirm"
            alert.headerText = null
            val result = alert.showAndWait()
            if (result.orElse(ButtonType.NO) == ButtonType.YES) {
                action.onAction()
            }
        } else {
            action.onAction()
        }
    }
}
