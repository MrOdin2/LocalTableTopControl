package com.tabletopcontrol.dynamicmap

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.DmWorkspaceId
import javafx.scene.Node

class DynamicMapBuilderPlugin : DmPlugin {
    private val controller = DynamicMapBuilderController()

    override val displayName: String = "Dynamic Map Builder"

    override val workspaceIds: Set<DmWorkspaceId> = setOf(DmWorkspaceId.DYNAMIC_MAP_BUILDER)

    override fun createView(): Node = DynamicMapBuilderView(controller).root

    override fun createToolbarView(workspace: DmWorkspaceId): Node? =
        if (workspace == DmWorkspaceId.DYNAMIC_MAP_BUILDER) {
            DynamicMapConstructionSiteToolbar().createView()
        } else {
            null
        }

    override fun onShutdown() {
        controller.onShutdown()
    }
}
