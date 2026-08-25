package com.tabletopcontrol.hotkey

import com.tabletopcontrol.core.MusicControlOperation
import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.SafeConfigIO
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

internal object HotkeySettingsCodec {
    private const val VERSION = 1

    fun serialize(settings: HotkeySettings): String {
        val normalized = normalize(settings)
        val properties = Properties().apply {
            setProperty("version", VERSION.toString())
            setProperty("columns", normalized.columns.toString())
            setProperty("hotkey.count", normalized.hotkeys.size.toString())
        }

        normalized.hotkeys.forEachIndexed { hotkeyIndex, hotkey ->
            val base = "hotkey.$hotkeyIndex"
            properties.setProperty("$base.id", hotkey.id)
            properties.setProperty("$base.name", hotkey.name)
            hotkey.icon?.let { properties.setProperty("$base.icon", it) }
            hotkey.colorHex?.let { properties.setProperty("$base.color", it) }
            hotkey.binding?.let { binding ->
                properties.setProperty("$base.key.code", binding.code)
                properties.setProperty("$base.key.shift", binding.shift.toString())
                properties.setProperty("$base.key.control", binding.control.toString())
                properties.setProperty("$base.key.alt", binding.alt.toString())
                properties.setProperty("$base.key.meta", binding.meta.toString())
            }
            properties.setProperty("$base.action.count", hotkey.actions.size.toString())
            hotkey.actions.forEachIndexed { actionIndex, action ->
                writeAction(properties, "$base.action.$actionIndex", action)
            }
        }

        return StringWriter().use { writer ->
            properties.store(writer, "TabletopControl hotkey settings")
            writer.toString()
        }
    }

    fun parse(text: String): HotkeySettings? {
        if (text.isBlank()) return null
        val properties = runCatching {
            Properties().also { it.load(StringReader(text)) }
        }.getOrNull() ?: return null
        if (properties.getProperty("version")?.toIntOrNull() != VERSION) return null

        val count = properties.getProperty("hotkey.count")
            ?.toIntOrNull()
            ?.coerceIn(0, MAX_HOTKEY_COUNT)
            ?: return null
        val columns = properties.getProperty("columns")
            ?.toIntOrNull()
            ?.coerceIn(MIN_MATRIX_COLUMNS, MAX_MATRIX_COLUMNS)
            ?: 4

        val hotkeys = (0 until count).mapNotNull { hotkeyIndex ->
            val base = "hotkey.$hotkeyIndex"
            val id = properties.getProperty("$base.id")?.trim()?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val name = properties.getProperty("$base.name")?.trim()?.takeIf(String::isNotBlank)
                ?: "Hotkey ${hotkeyIndex + 1}"
            val binding = properties.getProperty("$base.key.code")?.let { code ->
                KeyBinding(
                    code = code,
                    shift = properties.getProperty("$base.key.shift")?.toBooleanStrictOrNull() ?: false,
                    control = properties.getProperty("$base.key.control")?.toBooleanStrictOrNull() ?: false,
                    alt = properties.getProperty("$base.key.alt")?.toBooleanStrictOrNull() ?: false,
                    meta = properties.getProperty("$base.key.meta")?.toBooleanStrictOrNull() ?: false,
                ).takeIf(KeyBinding::isSuitable)
            }
            val actionCount = properties.getProperty("$base.action.count")
                ?.toIntOrNull()
                ?.coerceIn(0, MAX_ACTIONS_PER_HOTKEY)
                ?: 0
            val actions = (0 until actionCount).mapNotNull { actionIndex ->
                readAction(properties, "$base.action.$actionIndex")
            }
            HotkeyDefinition(
                id = id,
                name = name,
                icon = properties.getProperty("$base.icon")?.takeIf(String::isNotBlank),
                colorHex = validColor(properties.getProperty("$base.color")),
                binding = binding,
                actions = actions,
            )
        }
        return HotkeySettings(columns, hotkeys)
    }

    fun normalize(settings: HotkeySettings): HotkeySettings = HotkeySettings(
        columns = settings.columns.coerceIn(MIN_MATRIX_COLUMNS, MAX_MATRIX_COLUMNS),
        hotkeys = settings.hotkeys.take(MAX_HOTKEY_COUNT).mapIndexed { index, hotkey ->
            hotkey.copy(
                id = hotkey.id.trim().ifBlank { "hotkey-${index + 1}" },
                name = hotkey.name.trim().ifBlank { "Hotkey ${index + 1}" },
                icon = hotkey.icon?.trim()?.takeIf(String::isNotBlank),
                colorHex = validColor(hotkey.colorHex),
                binding = hotkey.binding?.takeIf(KeyBinding::isSuitable),
                actions = hotkey.actions.mapNotNull(::normalizeAction).take(MAX_ACTIONS_PER_HOTKEY),
            )
        },
    )

    private fun normalizeAction(action: HotkeyAction): HotkeyAction? = when (action) {
        is LightAction -> action.copy(
            segmentIds = action.segmentIds.filter { it >= 0 }.toSet(),
            colorHex = validColor(action.colorHex),
            brightness = action.brightness?.coerceIn(0.0, 1.0),
            effectSpeed = action.effectSpeed?.coerceIn(0, 255),
            effectIntensity = action.effectIntensity?.coerceIn(0, 255),
        )

        is PlaySoundAction -> action.uri.trim().takeIf(String::isNotBlank)?.let {
            action.copy(uri = it, volume = action.volume.coerceIn(0.0, 1.0))
        }

        is MusicAction -> {
            val uri = action.uri?.trim()?.takeIf(String::isNotBlank)
            if (action.operation != MusicControlOperation.STOP && uri == null) null else action.copy(uri = uri)
        }

        is TriggerHotkeyAction -> action.hotkeyId.trim().takeIf(String::isNotBlank)?.let {
            action.copy(hotkeyId = it)
        }

        is WaitAction -> action.copy(durationMillis = action.durationMillis.coerceIn(0, MAX_WAIT_MILLIS))
    }

    private fun writeAction(properties: Properties, base: String, action: HotkeyAction) {
        when (action) {
            is LightAction -> {
                properties.setProperty("$base.type", "light")
                properties.setProperty("$base.target", action.target.name)
                properties.setProperty("$base.segments", action.segmentIds.sorted().joinToString(","))
                action.power?.let { properties.setProperty("$base.power", it.toString()) }
                action.colorHex?.let { properties.setProperty("$base.color", it) }
                action.effectId?.let { properties.setProperty("$base.effect", it.toString()) }
                action.brightness?.let { properties.setProperty("$base.brightness", it.toString()) }
                action.effectSpeed?.let { properties.setProperty("$base.speed", it.toString()) }
                action.effectIntensity?.let { properties.setProperty("$base.intensity", it.toString()) }
            }

            is PlaySoundAction -> {
                properties.setProperty("$base.type", "sound")
                properties.setProperty("$base.uri", action.uri)
                properties.setProperty("$base.volume", action.volume.toString())
            }

            is MusicAction -> {
                properties.setProperty("$base.type", "music")
                properties.setProperty("$base.operation", action.operation.name)
                action.uri?.let { properties.setProperty("$base.uri", it) }
            }

            is TriggerHotkeyAction -> {
                properties.setProperty("$base.type", "hotkey")
                properties.setProperty("$base.hotkeyId", action.hotkeyId)
            }

            is WaitAction -> {
                properties.setProperty("$base.type", "wait")
                properties.setProperty("$base.duration", action.durationMillis.toString())
            }
        }
    }

    private fun readAction(properties: Properties, base: String): HotkeyAction? =
        when (properties.getProperty("$base.type")) {
            "light" -> LightAction(
                target = properties.getProperty("$base.target")?.let {
                    runCatching { HotkeyLightTarget.valueOf(it) }.getOrNull()
                } ?: HotkeyLightTarget.GLOBAL,
                segmentIds = properties.getProperty("$base.segments").orEmpty()
                    .split(',')
                    .mapNotNull { it.trim().toIntOrNull()?.takeIf { id -> id >= 0 } }
                    .toSet(),
                power = properties.getProperty("$base.power")?.toBooleanStrictOrNull(),
                colorHex = validColor(properties.getProperty("$base.color")),
                effectId = properties.getProperty("$base.effect")?.toIntOrNull(),
                brightness = properties.getProperty("$base.brightness")?.toDoubleOrNull()?.coerceIn(0.0, 1.0),
                effectSpeed = properties.getProperty("$base.speed")?.toIntOrNull()?.coerceIn(0, 255),
                effectIntensity = properties.getProperty("$base.intensity")?.toIntOrNull()?.coerceIn(0, 255),
            )

            "sound" -> properties.getProperty("$base.uri")?.takeIf(String::isNotBlank)?.let { uri ->
                PlaySoundAction(
                    uri = uri,
                    volume = properties.getProperty("$base.volume")?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0,
                )
            }

            "music" -> {
                val operation = properties.getProperty("$base.operation")?.let {
                    runCatching { MusicControlOperation.valueOf(it) }.getOrNull()
                } ?: return null
                normalizeAction(MusicAction(operation, properties.getProperty("$base.uri")))
            }

            "hotkey" -> properties.getProperty("$base.hotkeyId")?.takeIf(String::isNotBlank)?.let(::TriggerHotkeyAction)
            "wait" -> WaitAction(
                properties.getProperty("$base.duration")?.toLongOrNull()?.coerceIn(0, MAX_WAIT_MILLIS) ?: 0,
            )

            else -> null
        }

    private fun validColor(value: String?): String? =
        value?.trim()?.takeIf { ColorHexCodec.parseOrNull(it) != null }?.uppercase()
}

internal object HotkeySettingsStore {
    private const val CONFIG_NAME = "hotkeys.conf"

    private val configFile
        get() = AppConfigPaths.configFile(CONFIG_NAME)

    fun load(): HotkeySettings {
        val file = configFile
        if (!file.exists()) return HotkeySettings()
        return SafeConfigIO.readTextOrNull(file)?.let(HotkeySettingsCodec::parse) ?: HotkeySettings()
    }

    fun save(settings: HotkeySettings) {
        SafeConfigIO.writeText(configFile, HotkeySettingsCodec.serialize(settings))
    }
}
