package com.tabletopcontrol.map

import java.io.File
import java.util.Properties
import javafx.scene.paint.Color

/**
 * All settings persisted to the map settings file.
 *
 * @property gridCalibration saved grid calibration, or `null` if absent or unparseable.
 * @property mapCalibration  saved map-image calibration, or `null` if absent or unparseable.
 * @property gridColor       saved grid line colour, or `null` if absent or unparseable.
 * @property backgroundColor saved plain-colour background, or `null` if absent or unparseable.
 */
data class MapSavedSettings(
    val gridCalibration: GridCalibration?,
    val mapCalibration: MapCalibration?,
    val gridColor: Color?,
    val backgroundColor: Color?,
)

/**
 * Serialises and deserialises map calibration settings to/from a plain-text config file.
 *
 * [GridCalibration], [MapCalibration], the grid line colour, and the plain-colour
 * background are all stored together in a single Java properties file at
 * `~/.tabletopcontrol/map-settings.conf`.
 *
 * **On-disk format** — standard Java `.properties` key=value pairs:
 * ```
 * grid.cellSizeInPixels=50.0
 * grid.scale=1.0
 * grid.offsetX=0.0
 * grid.offsetY=0.0
 * grid.color=0.0,0.0,0.0,0.5
 * map.scale=1.0
 * map.offsetX=0.0
 * map.offsetY=0.0
 * background.color=0.0,0.0,0.0,1.0
 * ```
 *
 * Colours are stored as `red,green,blue,opacity` with all components in the
 * range [0.0, 1.0].  The `grid.color` and `background.color` keys are optional
 * for backward compatibility with older config files.
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

    // ── Color helpers ────────────────────────────────────────────────────────

    /**
     * Encodes [color] as a `"red,green,blue,opacity"` string with all components
     * in the range [0.0, 1.0].
     */
    internal fun colorToString(color: Color): String =
        "${color.red},${color.green},${color.blue},${color.opacity}"

    /**
     * Parses a [Color] from a `"red,green,blue,opacity"` string.
     *
     * @return the parsed [Color], or `null` if [s] is malformed or contains
     *         out-of-range values.
     */
    internal fun stringToColor(s: String): Color? = try {
        val parts = s.split(",")
        if (parts.size == 4) {
            val r = parts[0].toDouble()
            val g = parts[1].toDouble()
            val b = parts[2].toDouble()
            val a = parts[3].toDouble()
            if (listOf(r, g, b, a).all { it.isFinite() && it in 0.0..1.0 }) {
                Color.color(r, g, b, a)
            } else {
                null
            }
        } else null
    } catch (_: Exception) { null }

    // ── Serialisation ────────────────────────────────────────────────────────

    /**
     * Converts [gridCalibration], [mapCalibration], [gridColor], and
     * [backgroundColor] into a properties-format string.
     *
     * The returned string can be written directly to a file and later parsed by
     * [deserializeGridCalibration], [deserializeMapCalibration],
     * [deserializeGridColor], and [deserializeBackgroundColor].
     *
     * @param gridColor       grid line colour to persist; defaults to [GridConfig.color].
     * @param backgroundColor canvas background colour to persist; defaults to [Color.BLACK].
     */
    fun serialize(
        gridCalibration: GridCalibration,
        mapCalibration: MapCalibration,
        gridColor: Color = GridConfig().color,
        backgroundColor: Color = Color.BLACK,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("grid.cellSizeInPixels=${gridCalibration.cellSizeInPixels}")
        sb.appendLine("grid.scale=${gridCalibration.scale}")
        sb.appendLine("grid.offsetX=${gridCalibration.offsetX}")
        sb.appendLine("grid.offsetY=${gridCalibration.offsetY}")
        sb.appendLine("grid.color=${colorToString(gridColor)}")
        sb.appendLine("map.scale=${mapCalibration.scale}")
        sb.appendLine("map.offsetX=${mapCalibration.offsetX}")
        sb.appendLine("map.offsetY=${mapCalibration.offsetY}")
        sb.appendLine("background.color=${colorToString(backgroundColor)}")
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

    /**
     * Parses the grid line [Color] from a properties-format [text].
     *
     * @return The parsed colour, or `null` if the `grid.color` key is absent or
     *         the value is malformed.
     */
    fun deserializeGridColor(text: String): Color? {
        return try {
            val props = Properties()
            props.load(text.reader())
            val colorStr = props.getProperty("grid.color") ?: return null
            stringToColor(colorStr)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses the background [Color] from a properties-format [text].
     *
     * @return The parsed colour, or `null` if the `background.color` key is absent or
     *         the value is malformed.
     */
    fun deserializeBackgroundColor(text: String): Color? {
        return try {
            val props = Properties()
            props.load(text.reader())
            val colorStr = props.getProperty("background.color") ?: return null
            stringToColor(colorStr)
        } catch (_: Exception) {
            null
        }
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    /**
     * Writes all settings to [configFile].
     *
     * I/O failures are silently swallowed so that a missing or read-only config
     * directory never crashes the application.
     *
     * @param gridColor       grid line colour to persist; defaults to [GridConfig.color].
     * @param backgroundColor canvas background colour to persist; defaults to [Color.BLACK].
     */
    fun save(
        gridCalibration: GridCalibration,
        mapCalibration: MapCalibration,
        gridColor: Color = GridConfig().color,
        backgroundColor: Color = Color.BLACK,
    ) {
        try {
            configFile.writeText(serialize(gridCalibration, mapCalibration, gridColor, backgroundColor))
        } catch (_: Exception) {
            // non-fatal — proceed without persistence
        }
    }

    /**
     * Reads and parses all settings from the config file.
     *
     * @return A [MapSavedSettings] containing each value, or `null` for any field
     *         that is absent or unparseable.
     */
    fun load(): MapSavedSettings {
        return try {
            val text = configFile.readText()
            MapSavedSettings(
                deserializeGridCalibration(text),
                deserializeMapCalibration(text),
                deserializeGridColor(text),
                deserializeBackgroundColor(text),
            )
        } catch (_: Exception) {
            MapSavedSettings(null, null, null, null)
        }
    }
}
