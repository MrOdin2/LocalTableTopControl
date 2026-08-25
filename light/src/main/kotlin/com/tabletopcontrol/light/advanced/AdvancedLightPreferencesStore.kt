package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.SafeConfigIO
import com.tabletopcontrol.light.LightController
import com.tabletopcontrol.light.LightEffect
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

internal object AdvancedLightPreferencesStore {
    const val CONFIG_NAME: String = "advanced-light.properties"
    private const val VERSION = 1

    fun load(): AdvancedLightPreferences =
        SafeConfigIO.readOrElse(AdvancedLightPreferences()) {
            val text = AppConfigPaths.configFile(CONFIG_NAME).readText()
            deserialize(text)
        }

    fun save(preferences: AdvancedLightPreferences) {
        SafeConfigIO.writeText(
            AppConfigPaths.configFile(CONFIG_NAME),
            serialize(preferences),
        )
    }

    fun save(segments: List<AdvancedLightSegmentState>) =
        save(preferencesFor(segments))

    internal fun serialize(segments: List<AdvancedLightSegmentState>): String {
        return serialize(preferencesFor(segments))
    }

    internal fun serialize(preferences: AdvancedLightPreferences): String {
        val properties = Properties().apply {
            setProperty("version", VERSION.toString())
            setProperty("trackerTurnCuesEnabled", preferences.trackerTurnCuesEnabled.toString())
            setProperty("order", preferences.order.joinToString(","))
            preferences.segments.forEach { (id, segment) ->
                val prefix = "segment.$id."
                segment.name?.let { setProperty(prefix + "name", it) }
                segment.color?.let { setProperty(prefix + "color", it) }
                segment.effect?.let { setProperty(prefix + "effect", it.name) }
                segment.brightness?.let { setProperty(prefix + "brightness", it.toString()) }
                segment.brightnessScale?.let { setProperty(prefix + "brightnessScale", it.toString()) }
                segment.effectSpeed?.let { setProperty(prefix + "speed", it.toString()) }
                segment.effectIntensity?.let { setProperty(prefix + "intensity", it.toString()) }
                setProperty(
                    prefix + "flags.tabletopcontrol.assignedTokens",
                    segment.assignedTokenIds.joinToString(","),
                )
                segment.turnCue?.let { cue ->
                    setProperty(prefix + "turnCue.effect", cue.effect.name)
                    setProperty(prefix + "turnCue.color", cue.color)
                    setProperty(prefix + "turnCue.brightness", cue.brightness.toString())
                    setProperty(prefix + "turnCue.speed", cue.effectSpeed.toString())
                    setProperty(prefix + "turnCue.intensity", cue.effectIntensity.toString())
                    setProperty(prefix + "turnCue.duration", cue.duration.name)
                    setProperty(prefix + "turnCue.durationMillis", cue.durationMillis.toString())
                }
            }
        }
        return StringWriter().use { writer ->
            properties.store(writer, "TabletopControl advanced light preferences")
            writer.toString()
        }
    }

    internal fun deserialize(text: String): AdvancedLightPreferences {
        val properties = Properties().apply {
            load(StringReader(text))
        }
        val version = properties.getProperty("version")?.toIntOrNull() ?: return AdvancedLightPreferences()
        if (version != VERSION) return AdvancedLightPreferences()

        val trackerTurnCuesEnabled = properties.getProperty("trackerTurnCuesEnabled")
            ?.toBooleanStrictOrNull()
            ?: false

        val order = properties.getProperty("order")
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            .orEmpty()

        val segmentIds = properties.stringPropertyNames()
            .mapNotNull { key ->
                Regex("""^segment\.(\d+)\.""").find(key)?.groupValues?.get(1)?.toIntOrNull()
            }
            .toSet()

        val segmentPreferences = segmentIds.associateWith { id ->
            val prefix = "segment.$id."
            AdvancedLightSegmentPreference(
                name = properties.getProperty(prefix + "name")?.takeIf { it.isNotBlank() },
                color = properties.getProperty(prefix + "color")
                    ?.takeIf(AdvancedLightJson::isValidHexColor)
                    ?.uppercase(),
                effect = properties.getProperty(prefix + "effect")
                    ?.let { value -> runCatching { LightEffect.valueOf(value) }.getOrNull() },
                brightness = properties.getProperty(prefix + "brightness")
                    ?.toDoubleOrNull()
                    ?.coerceIn(0.0, 1.0),
                brightnessScale = properties.getProperty(prefix + "brightnessScale")
                    ?.toDoubleOrNull()
                    ?.coerceIn(0.0, 1.0),
                effectSpeed = properties.getProperty(prefix + "speed")
                    ?.toIntOrNull()
                    ?.coerceIn(0, 255),
                effectIntensity = properties.getProperty(prefix + "intensity")
                    ?.toIntOrNull()
                    ?.coerceIn(0, 255),
                assignedTokenIds = (
                    properties.getProperty(prefix + "flags.tabletopcontrol.assignedTokens")
                        ?: properties.getProperty(prefix + "assignedTokens")
                )
                    ?.split(',')
                    ?.map(String::trim)
                    ?.filter(String::isNotBlank)
                    ?.toSet()
                    .orEmpty(),
                turnCue = deserializeTurnCue(properties, prefix),
            )
        }

        return AdvancedLightPreferences(
            trackerTurnCuesEnabled = trackerTurnCuesEnabled,
            order = order,
            segments = segmentPreferences,
        )
    }

    private fun preferencesFor(segments: List<AdvancedLightSegmentState>): AdvancedLightPreferences =
        AdvancedLightPreferences(
            order = segments.map(AdvancedLightSegmentState::id),
            segments = segments.associate { segment ->
                segment.id to AdvancedLightSegmentPreference(
                    name = segment.name,
                    color = segment.color,
                    effect = segment.effect,
                    brightness = segment.brightness,
                    brightnessScale = segment.brightnessScale,
                    effectSpeed = segment.effectSpeed,
                    effectIntensity = segment.effectIntensity,
                    assignedTokenIds = segment.assignedTokenIds,
                    turnCue = segment.turnCue,
                )
            },
        )

    private fun deserializeTurnCue(
        properties: Properties,
        prefix: String,
    ): AdvancedLightTurnCue? {
        val effect = properties.getProperty(prefix + "turnCue.effect")
            ?.let { value -> runCatching { LightEffect.valueOf(value) }.getOrNull() }
            ?: return null
        return AdvancedLightTurnCue(
            effect = effect,
            color = properties.getProperty(prefix + "turnCue.color")
                ?.takeIf(AdvancedLightJson::isValidHexColor)
                ?.uppercase()
                ?: "#FFD37A",
            brightness = properties.getProperty(prefix + "turnCue.brightness")
                ?.toDoubleOrNull()
                ?.coerceIn(0.0, 1.0)
                ?: 1.0,
            effectSpeed = properties.getProperty(prefix + "turnCue.speed")
                ?.toIntOrNull()
                ?.coerceIn(0, 255)
                ?: LightController.DEFAULT_EFFECT_SPEED,
            effectIntensity = properties.getProperty(prefix + "turnCue.intensity")
                ?.toIntOrNull()
                ?.coerceIn(0, 255)
                ?: LightController.DEFAULT_EFFECT_INTENSITY,
            duration = properties.getProperty(prefix + "turnCue.duration")
                ?.let { value -> runCatching { AdvancedLightTurnCueDuration.valueOf(value) }.getOrNull() }
                ?: AdvancedLightTurnCueDuration.WHOLE_TURN,
            durationMillis = properties.getProperty(prefix + "turnCue.durationMillis")
                ?.toLongOrNull()
                ?.coerceIn(
                    AdvancedLightTurnCue.MIN_DURATION_MILLIS,
                    AdvancedLightTurnCue.MAX_DURATION_MILLIS,
                )
                ?: AdvancedLightTurnCue.DEFAULT_DURATION_MILLIS,
        )
    }
}
