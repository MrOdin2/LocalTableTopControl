package com.tabletopcontrol.new_tracker.scene

import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorFeatures
import com.tabletopcontrol.new_tracker.model.ActorLightSource
import com.tabletopcontrol.new_tracker.model.ActorType
import com.tabletopcontrol.new_tracker.model.DEFAULT_LIGHT_SOURCE_COLOR
import com.tabletopcontrol.new_tracker.model.ActorImageSettings
import com.tabletopcontrol.new_tracker.model.DistanceRange
import com.tabletopcontrol.new_tracker.model.DistanceUnit
import java.io.StringReader
import java.io.StringWriter
import java.util.Base64
import java.util.Properties

internal data class TrackerSceneState(
    val actors: List<Actor>,
    val activeActorId: String?,
    val roundCount: Int,
)

internal object TrackerSceneCodec {
    private const val VERSION = 5
    private const val PROPERTY_VERSION_WITHOUT_ACTOR_TYPE = 2
    private const val LEGACY_VERSION = 1

    fun serialize(state: TrackerSceneState): String {
        val props = Properties().apply {
            setProperty("version", VERSION.toString())
            setProperty("roundCount", state.roundCount.coerceAtLeast(0).toString())
            state.activeActorId?.let { setProperty("activeActorId", it) }
            setProperty("actor.count", state.actors.size.toString())
            state.actors.forEachIndexed { index, actor ->
                val prefix = "actor.$index"
                setProperty("$prefix.id", actor.id)
                setProperty("$prefix.name", actor.name)
                setProperty("$prefix.hp", actor.hp.toString())
                setProperty("$prefix.ac", actor.ac.toString())
                actor.initiative?.let { setProperty("$prefix.initiative", it.toString()) }
                setProperty("$prefix.tokenSize", actor.tokenSize.name)
                setProperty("$prefix.actorType", actor.actorType.name)
                actor.features.darkvisionRange?.let { range ->
                    setProperty("$prefix.darkvisionRange", range.amount.toString())
                    setProperty("$prefix.darkvisionUnit", range.unit.name)
                }
                actor.features.movementRange?.let { range ->
                    setProperty("$prefix.movementRange", range.amount.toString())
                    setProperty("$prefix.movementUnit", range.unit.name)
                }
                actor.features.lightSource?.let { source ->
                    setProperty("$prefix.lightBrightRange", source.brightRange.amount.toString())
                    setProperty("$prefix.lightBrightUnit", source.brightRange.unit.name)
                    setProperty("$prefix.lightDimRange", source.dimRange.amount.toString())
                    setProperty("$prefix.lightDimUnit", source.dimRange.unit.name)
                    setProperty("$prefix.lightColor", ColorHexCodec.colorToHex(source.color))
                }
                setProperty("$prefix.color", ColorHexCodec.colorToHex(actor.color))
                actor.imageSettings.uri?.let { setProperty("$prefix.imageUri", it) }
                setProperty("$prefix.imageScaleX", actor.imageSettings.scaleX.toString())
                setProperty("$prefix.imageScaleY", actor.imageSettings.scaleY.toString())
                setProperty("$prefix.imageOffsetX", actor.imageSettings.offsetX.toString())
                setProperty("$prefix.imageOffsetY", actor.imageSettings.offsetY.toString())
            }
        }

        return StringWriter().use { writer ->
            props.store(writer, "TabletopControl tracker scene")
            writer.toString()
        }
    }

    fun deserialize(text: String): TrackerSceneState? =
        deserializeProperties(text) ?: deserializeLegacy(text)

    private fun deserializeProperties(text: String): TrackerSceneState? {
        val props = runCatching {
            Properties().also { loaded ->
                loaded.load(StringReader(text))
            }
        }.getOrNull() ?: return null

        val version = props.getProperty("version")?.toIntOrNull() ?: return null
        if (version !in PROPERTY_VERSION_WITHOUT_ACTOR_TYPE..VERSION) return null

        val actorCount = props.getProperty("actor.count")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val actors = buildList {
            for (index in 0 until actorCount) {
                val prefix = "actor.$index"
                val id = props.getProperty("$prefix.id") ?: continue
                val name = props.getProperty("$prefix.name") ?: continue
                val color = ColorHexCodec.parseOrNull(props.getProperty("$prefix.color")) ?: continue
                add(
                    Actor(
                        id = id,
                        name = name,
                        hp = props.getProperty("$prefix.hp")?.toIntOrNull() ?: continue,
                        ac = props.getProperty("$prefix.ac")?.toIntOrNull() ?: continue,
                        initiative = props.getProperty("$prefix.initiative")?.toIntOrNull(),
                        tokenSize = runCatching {
                            TokenSize.valueOf(props.getProperty("$prefix.tokenSize"))
                        }.getOrDefault(TokenSize.MEDIUM),
                        actorType = ActorType.fromPersistence(props.getProperty("$prefix.actorType")),
                        features = ActorFeatures(
                            darkvisionRange = readDistanceRange(props, prefix, "darkvision"),
                            movementRange = readDistanceRange(props, prefix, "movement"),
                            lightSource = readLightSource(props, prefix),
                        ),
                        color = color,
                        imageSettings = ActorImageSettings(
                            uri = props.getProperty("$prefix.imageUri"),
                            scaleX = props.getProperty("$prefix.imageScaleX")?.toDoubleOrNull() ?: 1.0,
                            scaleY = props.getProperty("$prefix.imageScaleY")?.toDoubleOrNull() ?: 1.0,
                            offsetX = props.getProperty("$prefix.imageOffsetX")?.toDoubleOrNull() ?: 0.0,
                            offsetY = props.getProperty("$prefix.imageOffsetY")?.toDoubleOrNull() ?: 0.0,
                        ),
                    ),
                )
            }
        }

        return TrackerSceneState(
            actors = actors,
            activeActorId = props.getProperty("activeActorId"),
            roundCount = props.getProperty("roundCount")?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        )
    }

    private fun readLightSource(
        props: Properties,
        prefix: String,
    ): ActorLightSource? {
        val brightRange = readDistanceRange(props, prefix, "lightBright") ?: return null
        val dimRange = readDistanceRange(props, prefix, "lightDim") ?: return null
        return ActorLightSource(
            brightRange = brightRange,
            dimRange = dimRange,
            color = ColorHexCodec.parseOrDefault(
                props.getProperty("$prefix.lightColor"),
                DEFAULT_LIGHT_SOURCE_COLOR,
            ),
        )
    }

    private fun readDistanceRange(
        props: Properties,
        prefix: String,
        key: String,
    ): DistanceRange? {
        val amount = props.getProperty("$prefix.${key}Range")
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: return null
        val unit = DistanceUnit.fromPersistence(props.getProperty("$prefix.${key}Unit"))
        return DistanceRange(amount = amount, unit = unit)
    }

    internal fun serializeLegacy(state: TrackerSceneState): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()

        fun encode(value: String?): String =
            value?.let { encoder.encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: "-"
        return buildString {
            appendLine("version=$LEGACY_VERSION")
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

    private fun deserializeLegacy(text: String): TrackerSceneState? {
        val lines = text.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()
        if (lines.isEmpty()) return null

        val version = lines.firstOrNull { it.startsWith("version=") }
            ?.substringAfter('=')
            ?.toIntOrNull()
            ?: return null
        if (version != LEGACY_VERSION) return null

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
