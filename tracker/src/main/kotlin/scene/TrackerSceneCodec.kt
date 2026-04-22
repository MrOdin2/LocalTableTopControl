package com.tabletopcontrol.new_tracker.scene

import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorImageSettings
import java.util.Base64

internal data class TrackerSceneState(
    val actors: List<Actor>,
    val activeActorId: String?,
    val roundCount: Int,
)

internal object TrackerSceneCodec {
    private const val VERSION = 1

    fun serialize(state: TrackerSceneState): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()

        fun encode(value: String?): String =
            value?.let { encoder.encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: "-"

        return buildString {
            appendLine("version=$VERSION")
            appendLine("roundCount=${state.roundCount.coerceAtLeast(0)}")
            appendLine("activeActorId=${encode(state.activeActorId)}")
            appendLine("count=${state.actors.size}")
            state.actors.forEach { actor ->
                appendLine(
                    listOf(
                        encode(actor.id),
                        encode(actor.name),
                        actor.hp.toString(),
                        actor.ac.toString(),
                        actor.initiative?.toString() ?: "-",
                        actor.tokenSize.name,
                        ColorHexCodec.colorToHex(actor.color),
                        encode(actor.imageSettings.uri),
                        actor.imageSettings.scaleX.toString(),
                        actor.imageSettings.scaleY.toString(),
                        actor.imageSettings.offsetX.toString(),
                        actor.imageSettings.offsetY.toString(),
                    ).joinToString(separator = "|", prefix = "actor="),
                )
            }
        }
    }

    fun deserialize(text: String): TrackerSceneState? {
        val lines = text.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()
        if (lines.isEmpty()) return null

        val version = lines.firstOrNull { it.startsWith("version=") }
            ?.substringAfter('=')
            ?.toIntOrNull()
            ?: return null
        if (version != VERSION) return null

        val decoder = Base64.getUrlDecoder()

        fun decode(value: String): String? = if (value == "-") {
            null
        } else {
            runCatching { String(decoder.decode(value), Charsets.UTF_8) }.getOrNull()
        }

        val roundCount = lines.firstOrNull { it.startsWith("roundCount=") }
            ?.substringAfter('=')
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0

        val activeActorId = lines.firstOrNull { it.startsWith("activeActorId=") }
            ?.substringAfter('=')
            ?.let(::decode)

        val actors = lines
            .filter { it.startsWith("actor=") }
            .mapNotNull { line ->
                val parts = line.substringAfter("actor=").split('|')
                if (parts.size != 12) return@mapNotNull null

                val id = decode(parts[0]) ?: return@mapNotNull null
                val name = decode(parts[1]) ?: return@mapNotNull null
                val hp = parts[2].toIntOrNull() ?: return@mapNotNull null
                val ac = parts[3].toIntOrNull() ?: return@mapNotNull null
                val tokenSize = runCatching { TokenSize.valueOf(parts[5]) }.getOrNull() ?: TokenSize.MEDIUM
                val color = ColorHexCodec.parseOrNull(parts[6]) ?: return@mapNotNull null

                Actor(
                    id = id,
                    name = name,
                    hp = hp,
                    ac = ac,
                    initiative = parts[4].takeUnless { it == "-" }?.toIntOrNull(),
                    tokenSize = tokenSize,
                    color = color,
                    imageSettings = ActorImageSettings(
                        uri = decode(parts[7]),
                        scaleX = parts[8].toDoubleOrNull() ?: 1.0,
                        scaleY = parts[9].toDoubleOrNull() ?: 1.0,
                        offsetX = parts[10].toDoubleOrNull() ?: 0.0,
                        offsetY = parts[11].toDoubleOrNull() ?: 0.0,
                    ),
                )
            }

        return TrackerSceneState(
            actors = actors,
            activeActorId = activeActorId,
            roundCount = roundCount,
        )
    }
}
