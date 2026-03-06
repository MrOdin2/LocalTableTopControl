package com.tabletopcontrol.map

import java.io.File
import java.util.Properties

/**
 * Serialises and deserialises map calibration settings to/from a plain-text config file.
 *
 * Both [GridCalibration] and [MapCalibration] are stored together in a single Java
 * properties file at `~/.tabletopcontrol/map-settings.conf`.
 *
 * **On-disk format** — standard Java `.properties` key=value pairs:
 * ```
 * grid.cellSizeInPixels=50.0
 * grid.scale=1.0
 * grid.offsetX=0.0
 * grid.offsetY=0.0
 * map.scale=1.0
 * map.offsetX=0.0
 * map.offsetY=0.0
 * ```
 *
 * I/O failures are silently swallowed so that a missing or read-only config directory
 * never crashes the application.
 */
object MapSettingsSerializer {

    private val configFile: File
        get() {
            val dir = File(System.getProperty("user.home"), ".tabletopcontrol")
            dir.mkdirs()
            return File(dir, "map-settings.conf")
        }

    // ── Serialisation ────────────────────────────────────────────────────────

    /**
     * Converts [gridCalibration] and [mapCalibration] into a properties-format string.
     *
     * The returned string can be written directly to a file and later parsed by
     * [deserializeGridCalibration] and [deserializeMapCalibration].
     */
    fun serialize(gridCalibration: GridCalibration, mapCalibration: MapCalibration): String {
        val sb = StringBuilder()
        sb.appendLine("grid.cellSizeInPixels=${gridCalibration.cellSizeInPixels}")
        sb.appendLine("grid.scale=${gridCalibration.scale}")
        sb.appendLine("grid.offsetX=${gridCalibration.offsetX}")
        sb.appendLine("grid.offsetY=${gridCalibration.offsetY}")
        sb.appendLine("map.scale=${mapCalibration.scale}")
        sb.appendLine("map.offsetX=${mapCalibration.offsetX}")
        sb.appendLine("map.offsetY=${mapCalibration.offsetY}")
        return sb.toString()
    }

    // ── Deserialisation ──────────────────────────────────────────────────────

    /**
     * Parses a [GridCalibration] from a properties-format [text].
     *
     * @return The parsed calibration, or `null` if [text] is empty, malformed, or
     *         contains invalid (e.g. non-positive) values.
     */
    fun deserializeGridCalibration(text: String): GridCalibration? {
        return try {
            val props = Properties()
            props.load(text.reader())
            val cellSize = props.getProperty("grid.cellSizeInPixels")?.toDoubleOrNull() ?: return null
            val scale = props.getProperty("grid.scale")?.toDoubleOrNull() ?: return null
            val offsetX = props.getProperty("grid.offsetX")?.toDoubleOrNull() ?: return null
            val offsetY = props.getProperty("grid.offsetY")?.toDoubleOrNull() ?: return null
            if (cellSize <= 0 || scale <= 0) return null
            GridCalibration(cellSize, scale, offsetX, offsetY)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses a [MapCalibration] from a properties-format [text].
     *
     * @return The parsed calibration, or `null` if [text] is empty, malformed, or
     *         contains invalid (e.g. non-positive scale) values.
     */
    fun deserializeMapCalibration(text: String): MapCalibration? {
        return try {
            val props = Properties()
            props.load(text.reader())
            val scale = props.getProperty("map.scale")?.toDoubleOrNull() ?: return null
            val offsetX = props.getProperty("map.offsetX")?.toDoubleOrNull() ?: return null
            val offsetY = props.getProperty("map.offsetY")?.toDoubleOrNull() ?: return null
            if (scale <= 0) return null
            MapCalibration(scale, offsetX, offsetY)
        } catch (_: Exception) {
            null
        }
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    /**
     * Writes [gridCalibration] and [mapCalibration] to [configFile].
     *
     * I/O failures are silently swallowed so that a missing or read-only config
     * directory never crashes the application.
     */
    fun save(gridCalibration: GridCalibration, mapCalibration: MapCalibration) {
        try {
            configFile.writeText(serialize(gridCalibration, mapCalibration))
        } catch (_: Exception) {
            // non-fatal — proceed without persistence
        }
    }

    /**
     * Reads and parses both calibrations from the config file.
     *
     * @return A [Pair] where the first element is the saved [GridCalibration] (or `null`
     *         if absent or unparseable) and the second is the saved [MapCalibration]
     *         (or `null` if absent or unparseable).
     */
    fun load(): Pair<GridCalibration?, MapCalibration?> {
        return try {
            val text = configFile.readText()
            Pair(deserializeGridCalibration(text), deserializeMapCalibration(text))
        } catch (_: Exception) {
            Pair(null, null)
        }
    }
}
