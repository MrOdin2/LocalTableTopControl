package com.tabletopcontrol.new_tracker

import com.tabletopcontrol.core.DmPlugin
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.VBox

class NewTrackerPlugin : DmPlugin {

    override val displayName: String = "NEWTracker"

    override fun createView(): Node {
        val label = Label().apply {
            text = "Tracker plugin is under construction"
        }
        val root = VBox(0.0, label)
        return root
    }

}