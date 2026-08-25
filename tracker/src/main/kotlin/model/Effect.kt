package com.tabletopcontrol.new_tracker.model

import com.tabletopcontrol.core.TokenEffect
import java.util.UUID

/** A named condition or custom status currently affecting an [Actor]. */
data class Effect(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String? = null,
    val durationRounds: Int? = null,
    val description: String? = null,
    val visibleToPlayers: Boolean = true,
) {
    init {
        require(name.isNotBlank()) { "Effect name cannot be blank." }
        require(durationRounds == null || durationRounds > 0) {
            "Effect duration must be positive when present."
        }
    }

    val displayIcon: String
        get() = icon?.trim()?.takeIf(String::isNotEmpty) ?: name.trim().take(1).uppercase()

    val durationLabel: String
        get() = durationRounds?.let { "$it round${if (it == 1) "" else "s"}" } ?: "Until removed"

    val badgeText: String
        get() = "$displayIcon $name${durationRounds?.let { " ($it)" }.orEmpty()}"

    fun toTokenEffect(): TokenEffect =
        TokenEffect(
            id = id,
            name = name,
            icon = icon,
            durationRounds = durationRounds,
            visibleToPlayers = visibleToPlayers,
        )
}

/** Reusable, common tabletop conditions offered by the Effects dialog. */
data class EffectTemplate(
    val name: String,
    val icon: String,
    val description: String,
) {
    fun createEffect(): Effect = Effect(name = name, icon = icon, description = description)

    override fun toString(): String = "$icon $name"
}

object EffectLibrary {
    val commonConditions: List<EffectTemplate> = listOf(
        EffectTemplate("Blinded", "◉", "Cannot see normally."),
        EffectTemplate("Charmed", "♥", "Cannot harm the charmer or target them with harmful abilities."),
        EffectTemplate("Deafened", "◌", "Cannot hear normally."),
        EffectTemplate("Frightened", "!", "Has difficulty acting while the source of fear is in sight."),
        EffectTemplate("Grappled", "⊗", "Speed is reduced while the grapple persists."),
        EffectTemplate("Incapacitated", "×", "Cannot take actions or reactions."),
        EffectTemplate("Invisible", "◈", "Cannot be seen without a special sense or magic."),
        EffectTemplate("Paralyzed", "∥", "Cannot move or speak voluntarily."),
        EffectTemplate("Poisoned", "☠", "Has reduced effectiveness while poisoned."),
        EffectTemplate("Prone", "↘", "Lying on the ground."),
        EffectTemplate("Restrained", "⌘", "Speed is reduced and movement is constrained."),
        EffectTemplate("Stunned", "✦", "Cannot act normally."),
        EffectTemplate("Unconscious", "☾", "Unaware and incapacitated."),
    )
}
