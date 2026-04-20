package com.tabletopcontrol.light.ui

import javafx.scene.Node

internal data class LightSection(
    val node: Node,
    val dispose: () -> Unit = {},
)
