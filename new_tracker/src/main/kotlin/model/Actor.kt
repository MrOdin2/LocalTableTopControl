package com.tabletopcontrol.new_tracker.model

import javafx.scene.paint.Color
import java.util.UUID

data class Actor(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hp: Int = 0,
    val ac: Int = 0,
    val initiative: Int? = null,
    var color: Color = Color.GRAY,
    val imageSettings: ActorImageSettings = ActorImageSettings(),
){
    fun duplicateActor(): Actor = Actor(UUID.randomUUID().toString(), name, hp, ac, initiative, imageSettings = imageSettings)
}

data class ActorImageSettings(
    val uri: String? = null,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
)
