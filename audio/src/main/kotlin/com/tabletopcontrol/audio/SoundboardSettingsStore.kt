package com.tabletopcontrol.audio

import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.SafeConfigIO
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import java.io.File
import java.util.Base64

internal data class SoundboardSlotConfig(
    val label: String? = null,
    val uri: String? = null,
    val colorHex: String? = null,
)

internal sealed interface SoundboardSettingsLoadResult {
    data class Loaded(val slots: List<SoundboardSlotConfig>) : SoundboardSettingsLoadResult

    data object Missing : SoundboardSettingsLoadResult

    data class Failed(val failure: SoundboardSettingsPersistenceFailure) : SoundboardSettingsLoadResult
}

internal sealed interface SoundboardSettingsSaveResult {
    data class Saved(
        val slotCount: Int,
        val configFile: File,
    ) : SoundboardSettingsSaveResult

    data class Failed(val failure: SoundboardSettingsPersistenceFailure) : SoundboardSettingsSaveResult
}

internal sealed interface SoundboardSettingsPersistenceFailure {
    val configFile: File

    data class ReadFailed(override val configFile: File) : SoundboardSettingsPersistenceFailure

    data class InvalidFormat(override val configFile: File) : SoundboardSettingsPersistenceFailure

    data class WriteFailed(override val configFile: File) : SoundboardSettingsPersistenceFailure
}

internal interface SoundboardSettingsStore {
    fun load(): SoundboardSettingsLoadResult

    fun save(slots: List<SoundboardSlotConfig>): SoundboardSettingsSaveResult
}

internal object SoundboardSettingsCodec {
    private const val CONFIG_VERSION = 1

    fun clampSlotCount(requested: Int): Int = requested.coerceIn(0, MAX_SOUNDBOARD_BUTTON_COUNT)

    fun serialize(slots: List<SoundboardSlotConfig>): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()

        fun encode(value: String?): String =
            value?.let { encoder.encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: "-"

        return buildString {
            appendLine("version=$CONFIG_VERSION")
            appendLine("count=${clampSlotCount(slots.size)}")
            slots.take(MAX_SOUNDBOARD_BUTTON_COUNT).forEach { slot ->
                appendLine("slot=${encode(slot.label)}|${encode(slot.uri)}|${encode(slot.colorHex)}")
            }
        }
    }

    fun parse(text: String): List<SoundboardSlotConfig>? {
        val lines = text.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()
        if (lines.isEmpty()) return null

        val version = lines.firstOrNull { it.startsWith("version=") }
            ?.substringAfter('=')
            ?.toIntOrNull()
            ?: return null
        if (version != CONFIG_VERSION) return null

        val decoder = Base64.getUrlDecoder()

        fun decode(value: String): String? = if (value == "-") {
            null
        } else {
            runCatching { String(decoder.decode(value), Charsets.UTF_8) }.getOrNull()
        }

        val count = lines.firstOrNull { it.startsWith("count=") }
            ?.substringAfter('=')
            ?.toIntOrNull()
            ?.let(::clampSlotCount)
            ?: DEFAULT_SOUNDBOARD_BUTTON_COUNT

        val parsed = lines
            .filter { it.startsWith("slot=") }
            .mapNotNull { line ->
                val payload = line.substringAfter("slot=", "")
                val parts = payload.split('|')
                if (parts.size != 3) return@mapNotNull null

                SoundboardSlotConfig(
                    label = decode(parts[0]),
                    uri = decode(parts[1]),
                    colorHex = decode(parts[2])?.takeIf { ColorHexCodec.parseOrNull(it) != null },
                )
            }
            .toMutableList()

        while (parsed.size < count) {
            parsed += SoundboardSlotConfig()
        }
        return parsed.take(count)
    }
}

internal object SerializerSoundboardSettingsStore : SoundboardSettingsStore {
    private const val CONFIG_NAME = "soundboard.conf"

    private val configFile: File
        get() = AppConfigPaths.configFile(CONFIG_NAME)

    override fun load(): SoundboardSettingsLoadResult {
        val targetFile = configFile
        if (!targetFile.exists()) return SoundboardSettingsLoadResult.Missing

        var text: String? = null
        var readSucceeded = false
        SafeConfigIO.run {
            text = targetFile.readText()
            readSucceeded = true
        }
        if (!readSucceeded) {
            return SoundboardSettingsLoadResult.Failed(
                SoundboardSettingsPersistenceFailure.ReadFailed(targetFile),
            )
        }

        val parsed = text?.let(SoundboardSettingsCodec::parse)
            ?: return SoundboardSettingsLoadResult.Failed(
                SoundboardSettingsPersistenceFailure.InvalidFormat(targetFile),
            )

        return SoundboardSettingsLoadResult.Loaded(parsed)
    }

    override fun save(slots: List<SoundboardSlotConfig>): SoundboardSettingsSaveResult {
        val targetFile = configFile
        val payload = SoundboardSettingsCodec.serialize(slots)

        var writeSucceeded = false
        SafeConfigIO.run {
            targetFile.writeText(payload)
            writeSucceeded = true
        }

        return if (writeSucceeded) {
            SoundboardSettingsSaveResult.Saved(
                slotCount = SoundboardSettingsCodec.clampSlotCount(slots.size),
                configFile = targetFile,
            )
        } else {
            SoundboardSettingsSaveResult.Failed(
                SoundboardSettingsPersistenceFailure.WriteFailed(targetFile),
            )
        }
    }
}
