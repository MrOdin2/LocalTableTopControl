package com.tabletopcontrol.core.ui

/**
 * Standard sections for plugin context menus.
 *
 * Sections appear in [displayOrder] order, separated by dividers.  Sections that
 * contain no visible actions are omitted entirely so no empty separators are shown.
 *
 * | Section        | Purpose                                                        |
 * |----------------|----------------------------------------------------------------|
 * | [BASIC]        | Primary, most-used actions (open, rename, view details, …)    |
 * | [APPEARANCE]   | Visual / style changes (colour, icon, label, …)               |
 * | [ARRANGE]      | Ordering and structural changes (move up, group, …)           |
 * | [DANGER_ZONE]  | Destructive actions (delete, reset, …) — rendered last and    |
 * |                | typically shown with a confirmation dialog.                    |
 */
enum class MenuSection(val displayOrder: Int) {
    BASIC(0),
    APPEARANCE(1),
    ARRANGE(2),
    DANGER_ZONE(3),
}
