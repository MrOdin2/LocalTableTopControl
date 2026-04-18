package com.tabletopcontrol.tracker

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.TokenImageChangedEvent
import javafx.scene.Node
import javafx.scene.paint.Color

/**
 * DM-panel plugin providing a combined initiative and HP/AC tracker.
 *
 * Each combatant is represented by a **card** that contains:
 * - An editable **name** field.
 * - An editable **AC** (armour class) number field.
 * - An editable **HP** (hit points) number field.
 * - A **×** button to remove that combatant.
 * - A **grab handle** glyph that shows where to click-and-drag when reordering cards.
 *
 * A **toolbar** above the card list contains:
 * - **−** — remove all combatants (with confirmation dialog).
 * - **Next ▶** — advance the active-card pointer to the next combatant.
 * - **Round N** label — displays the current round number.
 *
 * The active combatant's card is highlighted with a coloured border so the DM
 * can see at a glance whose turn it is.
 *
 * The card list adapts its orientation to the available space:
 * - More than twice as wide as tall → cards arranged **horizontally** (left-to-right order).
 * - Otherwise → cards arranged **vertically** (top-to-bottom order).
 *
 * Cards can be **dragged and dropped** to reorder the initiative list by using
 * the grab handle. While dragging, a shared **drop indicator bar** shows the
 * insertion position instead of recolouring the entire target card.
 *
 * GODCLASS audit note:
 * - `TrackerPlugin` is a GODCLASS with UI, domain coordination, and token sync mixed together.
 * - Features that can be moved to helpers/shared components:
 *   - Card/toolbar UI construction and orientation-specific layout strategy.
 *   - Token image upload/edit dialog flow and image normalization/validation.
 *   - EventBus sync adapters for token add/remove/reset/active-change/image-change events.
 *   - Preset dialog/state management and tracker-entry mutation orchestration.
 *   - Drag/drop reorder and style/highlight rendering logic.
 */
class TrackerPlugin : DmPlugin {

    override val displayName: String = "Tracker"

    /** Shared combatant state; persists across pane rebuilds within a session. */
    private val tracker = InitiativeTracker()

    /**
     * Monotonically increasing counter for assigning token colours.  Never resets on
     * removal, so the next added token always gets a colour not already in use among
     * recently added tokens (up to [TOKEN_COLORS].size combatants).
     */
    private var tokenColorIndex: Int = 0

    /**
     * Stable UUIDs for each combatant, parallel to [tracker.entries].
     * `tokenIds[i]` is the id of `tracker.entries[i]`.  Must be kept in sync
     * whenever entries are added, removed, moved, or cleared.
     */
    private val tokenIds: MutableList<String> = mutableListOf()

    /**
     * Maps each combatant's stable id to the token colour that was assigned when it
     * was added.  Keyed by id (not name) so colour lookups survive renames.
     * Used to render the matching colour swatch on each tracker card.
     */
    private val tokenColors: MutableMap<String, Color> = mutableMapOf()

    /**
     * Maps each combatant's stable id to the file URI of its token picture, or `null`
     * when no picture has been uploaded.  Keyed by id so the mapping survives renames.
     * The current value is republished as [TokenImageChangedEvent] whenever the DM
     * picks a new file in the per-card image button.
     */
    internal val tokenImages: MutableMap<String, TokenImageSettings> = mutableMapOf()

    //classes handling all the stuff
    private val trackerPluginUI = TrackerPluginUI(tracker, CONSTANTS, tokenColorIndex,  tokenIds, tokenColors, tokenImages)

    data class TokenImageSettings(
        val uri: String?,
        val scaleX: Double = 1.0,
        val scaleY: Double = 1.0,
        val offsetX: Double = 0.0,
        val offsetY: Double = 0.0,
    )

    override fun createView(): Node {
        return trackerPluginUI.createView();
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object CONSTANTS {
        internal const val HORIZONTAL_LAYOUT_RATIO_THRESHOLD = 2.0
        internal const val SLIDER_VALUE_EPSILON = 1e-9

        val PREVIEW_SIZE = 160.0
        val PREVIEW_RADIUS = 70.0
        val PREVIEW_CENTER = PREVIEW_SIZE / 2

        internal const val CARD_STYLE_NORMAL =
            "-fx-border-color: -tc-card-border; -fx-border-radius: 4; " +
                "-fx-background-color: -tc-card-bg; -fx-background-radius: 4;"

        internal const val CARD_STYLE_ACTIVE =
            "-fx-border-color: -tc-card-active-border; -fx-border-width: 2; -fx-border-radius: 4; " +
                "-fx-background-color: -tc-card-active-bg; -fx-background-radius: 4;"

        /**
         * 64 perceptually distinct token colours generated from 16 evenly spaced hues
         * across the full colour wheel, each at four (saturation × brightness) variants:
         * - Vivid   (s=1.0, b=0.90) — adds 1–16
         * - Light   (s=0.55, b=1.0) — adds 17–32
         * - Dark    (s=1.0, b=0.55) — adds 33–48
         * - Muted   (s=0.45, b=0.80) — adds 49–64
         *
         * Successive adds cycle through all 16 hues within a variant group before
         * moving on to the next variant, maximising perceptual distance between
         * consecutively added combatants.
         */
        internal val TOKEN_COLORS: List<Color> = run {
            val hues = List(16) { it * 22.5 }
            val variants = listOf(
                Pair(1.00, 0.90),   // vivid
                Pair(0.55, 1.00),   // light
                Pair(1.00, 0.55),   // dark
                Pair(0.45, 0.80),   // muted
            )
            List(64) { i -> Color.hsb(hues[i % 16], variants[i / 16].first, variants[i / 16].second) }
        }

    }

}
