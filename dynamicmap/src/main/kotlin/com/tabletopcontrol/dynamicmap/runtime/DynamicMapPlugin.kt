package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.core.DmPlugin
import com.tabletopcontrol.core.scene.SceneParticipant
import javafx.scene.Node

class DynamicMapPlugin : DmPlugin, SceneParticipant {
    private val controller = MapUiController()

    override val displayName: String = "DynamicMap"
    override val sceneKey: String = "dynamicmap"
    override val sceneDisplayName: String = displayName
    override val sceneLoadOrder: Int = 200

    override val iconPath: String? = null

    override fun createView(): Node = controller.createView()

    override fun createTableView(): Node = controller.createTableView()

    override fun captureSceneState(): String = controller.captureSceneState()

    override fun applySceneState(payload: String) {
        controller.applySceneState(payload)
    }

    override fun onShutdown() {
        controller.onShutdown()
    }
}
