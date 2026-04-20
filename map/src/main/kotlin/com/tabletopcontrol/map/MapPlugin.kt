package com.tabletopcontrol.map

import com.tabletopcontrol.core.DmPlugin
import javafx.scene.Node

class MapPlugin : DmPlugin {
    private val controller = MapUiController()

    override val displayName: String = "Map"

    override val iconPath: String? = null

    override fun createView(): Node = controller.createView()

    override fun createTableView(): Node = controller.createTableView()

    override fun onShutdown() {
        controller.onShutdown()
    }
}
