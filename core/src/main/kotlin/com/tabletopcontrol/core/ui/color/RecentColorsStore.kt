package com.tabletopcontrol.core.ui.color

import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.SafeConfigIO
import javafx.scene.paint.Color

/**
 * Persists a bounded newest-first list of recently used colors.
 */
internal object RecentColorsStore {
    internal const val CONFIG_NAME = "recent-colors.conf"
    internal const val MAX_RECENT_COLORS = 8

    private val configFile
        get() = AppConfigPaths.configFile(CONFIG_NAME)

    /**
     * Loads persisted recent colors.
     *
     * Invalid lines are ignored and duplicates are removed by RGB hex value.
     */
    fun load(): List<Color> = SafeConfigIO.readOrElse(emptyList()) {
        val uniqueHexes = linkedSetOf<String>()
        configFile.bufferedReader().useLines { lines ->
            for (line in lines) {
                val hex = ColorHexCodec.parseOrNull(line)?.let(ColorHexCodec::colorToHex) ?: continue
                uniqueHexes.add(hex)
                if (uniqueHexes.size >= MAX_RECENT_COLORS) {
                    break
                }
            }
        }
        uniqueHexes.map(ColorHexCodec::hexToColor)
    }

    /**
     * Records [color] as most recently used and persists the updated list.
     */
    fun remember(color: Color) {
        val newest = ColorHexCodec.colorToHex(color)
        val merged = buildList {
            add(newest)
            load().map(ColorHexCodec::colorToHex)
                .filterNot { it == newest }
                .take(MAX_RECENT_COLORS - 1)
                .forEach(::add)
        }
        SafeConfigIO.writeText(configFile, merged.joinToString(separator = "\n", postfix = "\n"))
    }
}

