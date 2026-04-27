package com.tabletopcontrol.new_tracker.model

import com.tabletopcontrol.core.TokenSize
import javafx.scene.paint.Color
import java.util.UUID

data class Actor(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hp: Int = 0,
    val ac: Int = 0,
    val initiative: Int? = null,
    val tokenSize: TokenSize = TokenSize.MEDIUM,
    val actorType: ActorType = ActorType.NPC,
    var color: Color = Color.GRAY,
    val imageSettings: ActorImageSettings = ActorImageSettings(),
) {
    fun duplicateActor(): Actor = copy(id = UUID.randomUUID().toString())
}

enum class ActorType(
    val shortLabel: String,
    val displayName: String,
) {
    PC("PC", "Player Character"),
    NPC("NPC", "Non-player Character"),
    ;

    val menuLabel: String
        get() = "$shortLabel - $displayName"

    companion object {
        fun fromPersistence(value: String?): ActorType {
            val normalized = value?.trim() ?: return NPC
            return entries.firstOrNull { type ->
                type.name.equals(normalized, ignoreCase = true) ||
                    type.shortLabel.equals(normalized, ignoreCase = true) ||
                    type.displayName.equals(normalized, ignoreCase = true) ||
                    type.menuLabel.equals(normalized, ignoreCase = true)
            } ?: NPC
        }
    }
}

data class ActorImageSettings(
    val uri: String? = null,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
)
