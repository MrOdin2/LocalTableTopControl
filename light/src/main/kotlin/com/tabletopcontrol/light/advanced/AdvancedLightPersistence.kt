package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.SafeConfigIO
import com.tabletopcontrol.light.LightEffect
import java.util.Properties

/**
 * Loads and saves the Advanced Lighting plugin configuration.
 *
 * Data is persisted to `~/.tabletopcontrol/adv-light.properties` as a
 * standard Java [Properties] file.
 *
 * ### Key format
 * - `segOrder`               — comma-separated list of segment ids in display order
 * - `seg.NAME.<id>`          — user-assigned display name for segment `id`
 * - `seg.COLOR.<id>`         — CSS hex color for segment `id`
 * - `seg.EFFECT.<id>`        — [LightEffect] enum name for segment `id`
 * - `seg.BRI.<id>`           — brightness 0.0–1.0 for segment `id`
 * - `seg.SPEED.<id>`         — effect speed 0–255 for segment `id`
 * - `seg.INTENSITY.<id>`     — effect intensity 0–255 for segment `id`
 */
internal object AdvancedLightPersistence {

    private const val CONFIG_FILE = "adv-light.properties"
    private const val VERSION = 1

    /** Loads the persisted configuration, returning a default-empty config on failure. */
    fun load(): AdvancedLightConfig = SafeConfigIO.readOrElse(AdvancedLightConfig()) {
        val file = AppConfigPaths.configFile(CONFIG_FILE)
        if (!file.exists()) return@readOrElse AdvancedLightConfig()
        val text = SafeConfigIO.readTextOrNull(file) ?: return@readOrElse AdvancedLightConfig()
        parse(text)
    }

    /** Persists [config] to disk.  Errors are swallowed non-fatally. */
    fun save(config: AdvancedLightConfig) {
        SafeConfigIO.run {
            val props = Properties()
            props.setProperty("version", VERSION.toString())

            // Segment display order
            props.setProperty("segOrder", config.segmentOrder.joinToString(","))

            // Per-segment names and state
            config.segmentNames.forEach { (id, name) ->
                props.setProperty("seg.NAME.$id", name)
            }
            config.segmentStates.forEach { (id, state) ->
                props.setProperty("seg.COLOR.$id", state.color)
                props.setProperty("seg.EFFECT.$id", state.effect.name)
                props.setProperty("seg.BRI.$id", state.brightness.toString())
                props.setProperty("seg.SPEED.$id", state.effectSpeed.toString())
                props.setProperty("seg.INTENSITY.$id", state.effectIntensity.toString())
            }

            val text = java.io.StringWriter().use { writer ->
                props.store(writer, "TabletopControl Advanced Lighting")
                writer.toString()
            }
            SafeConfigIO.writeText(AppConfigPaths.configFile(CONFIG_FILE), text)
        }
    }

    // ── private ───────────────────────────────────────────────────────────────

    private fun parse(text: String): AdvancedLightConfig {
        val props = Properties().apply { text.reader().use { load(it) } }

        val version = props.getProperty("version")?.toIntOrNull() ?: 0
        if (version != VERSION) return AdvancedLightConfig()

        val segmentOrder = props.getProperty("segOrder")
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: emptyList()

        // Collect segment ids from all seg.* keys
        val allIds = props.stringPropertyNames()
            .filter { it.startsWith("seg.") }
            .mapNotNull { key ->
                key.substringAfterLast('.').toIntOrNull()
            }
            .toSet()

        val names = mutableMapOf<Int, String>()
        val states = mutableMapOf<Int, SegmentSavedState>()

        for (id in allIds) {
            props.getProperty("seg.NAME.$id")?.let { names[id] = it }

            val color = props.getProperty("seg.COLOR.$id") ?: "#FFFFFF"
            val effect = props.getProperty("seg.EFFECT.$id")
                ?.let { runCatching { LightEffect.valueOf(it) }.getOrNull() }
                ?: LightEffect.NONE
            val bri = props.getProperty("seg.BRI.$id")?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0
            val speed = props.getProperty("seg.SPEED.$id")?.toIntOrNull()?.coerceIn(0, 255) ?: 128
            val intensity = props.getProperty("seg.INTENSITY.$id")?.toIntOrNull()?.coerceIn(0, 255) ?: 128
            states[id] = SegmentSavedState(color, effect, bri, speed, intensity)
        }

        return AdvancedLightConfig(
            segmentNames = names,
            segmentOrder = segmentOrder,
            segmentStates = states,
        )
    }
}
