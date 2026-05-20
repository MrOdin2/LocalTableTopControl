package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.SafeConfigIO
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

    fun save(segments: List<AdvancedLightSegmentState>) {
        SafeConfigIO.writeText(
            AppConfigPaths.configFile(CONFIG_NAME),
            serialize(segments),
        )
    }

    internal fun serialize(segments: List<AdvancedLightSegmentState>): String {
        val properties = Properties().apply {
            setProperty("version", VERSION.toString())
            setProperty("order", segments.joinToString(",") { it.id.toString() })
            segments.forEach { segment ->
                val prefix = "segment.${segment.id}."
                setProperty(prefix + "name", segment.name)
                setProperty(prefix + "color", segment.color)
                setProperty(prefix + "effect", segment.effect.name)
                setProperty(prefix + "brightness", segment.brightness.toString())
                setProperty(prefix + "speed", segment.effectSpeed.toString())
                setProperty(prefix + "intensity", segment.effectIntensity.toString())
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
                effectSpeed = properties.getProperty(prefix + "speed")
                    ?.toIntOrNull()
                    ?.coerceIn(0, 255),
                effectIntensity = properties.getProperty(prefix + "intensity")
                    ?.toIntOrNull()
                    ?.coerceIn(0, 255),
            )
        }

        return AdvancedLightPreferences(
            order = order,
            segments = segmentPreferences,
        )
    }
}
