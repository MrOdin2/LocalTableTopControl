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
    val features: ActorFeatures = ActorFeatures(),
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

data class ActorFeatures(
    val darkvisionRange: DistanceRange? = null,
    val movementRange: DistanceRange? = null,
    val lightSource: ActorLightSource? = null,
) {
    val hasAny: Boolean
        get() = darkvisionRange != null || movementRange != null || lightSource != null

    val summaryText: String
        get() = buildList {
            darkvisionRange?.let { add("DV ${it.displayText}") }
            movementRange?.let { add("Move ${it.displayText}") }
            lightSource?.let { add("Light ${it.displayText}") }
        }.joinToString(" | ").ifBlank { "No features set" }
}

data class ActorLightSource(
    val brightRange: DistanceRange,
    val dimRange: DistanceRange,
    val color: Color = DEFAULT_LIGHT_SOURCE_COLOR,
) {
    val displayText: String
        get() = "${brightRange.displayText}/${dimRange.displayText}"
}

data class DistanceRange(
    val amount: Int,
    val unit: DistanceUnit = DistanceUnit.FEET,
) {
    init {
        require(amount >= 0) { "Distance range cannot be negative." }
    }

    val displayText: String
        get() = "$amount ${unit.abbreviation}"
}

enum class DistanceUnit(
    val displayName: String,
    val abbreviation: String,
) {
    FEET("Feet", "ft"),
    METERS("Meters", "m"),
    ;

    override fun toString(): String = displayName

    companion object {
        fun fromPersistence(value: String?): DistanceUnit {
            val normalized = value?.trim()?.lowercase() ?: return FEET
            return when (normalized) {
                "feet", "foot", "ft" -> FEET
                "meters", "meter", "metres", "metre", "m" -> METERS
                else -> entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) } ?: FEET
            }
        }
    }
}

val DEFAULT_LIGHT_SOURCE_COLOR: Color = Color.web("#FFD37A")

data class ActorImageSettings(
    val uri: String? = null,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
)
