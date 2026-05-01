package com.tabletopcontrol.canvas

import com.tabletopcontrol.core.DmPlugin
import javafx.scene.Node

/**
 * DM-panel plugin for the Canvas picture board.
 *
 * The Canvas plugin lets the DM load pictures from the file system, arrange
 * them freely (drag, resize, rotate) on a virtual board that mirrors the
 * player-facing table view, and selectively share them with players.
 *
 * ### DM view
 * An interactive pane with a dashed outline representing the table-view bounds.
 * Pictures can be loaded via right-click or the toolbar "Add picture…" button.
 *
 * ### Table view (overlay)
 * A transparent overlay rendered on top of the active primary table view
 * (Map, DynamicMap, etc.). Only pictures whose "Share with Table" flag is
 * enabled are visible on the overlay.
 */
class CanvasPlugin : DmPlugin {

    override val displayName: String = "Canvas"

    /**
     * The Canvas table view is an overlay — always rendered on top of the primary
     * table view (Map / DynamicMap) without affecting the "Table content" selector.
     */
    override val isTableOverlay: Boolean = true

    private val model = CanvasModel()
    private var tableOverlay: CanvasTableOverlay? = null

    override fun createView(): Node = CanvasDmView(model).createView()

    override fun createTableView(): Node {
        tableOverlay?.dispose()
        val overlay = CanvasTableOverlay()
        tableOverlay = overlay
        return overlay.createView()
    }

    override fun onShutdown() {
        tableOverlay?.dispose()
        tableOverlay = null
    }
}
