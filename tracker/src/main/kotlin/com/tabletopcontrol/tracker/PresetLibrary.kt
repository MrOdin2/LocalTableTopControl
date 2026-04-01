package com.tabletopcontrol.tracker

import java.io.File

/**
 * Manages a persistent library of combatant presets for the initiative tracker.
 *
 * Presets are stored in `~/.tabletopcontrol/tracker-presets.conf`, one per line,
 * using a tab-separated format: `name<TAB>hp<TAB>ac<TAB>initiative`.
 *
 * Tab and newline characters in names are replaced with a space on save so that
 * the file format is always unambiguous.
 *
 * I/O failures in [savePreset] and [delete] are silently swallowed — consistent
 * with the rest of the persistence layer — so that a missing or read-only config
 * directory never crashes the application.
 */
object PresetLibrary {

    /**
     * A saved combatant template.
     *
     * @property name       the combatant's display name
     * @property hp         the combatant's hit points
     * @property ac         the combatant's armour class
     * @property initiative the combatant's default initiative value (defaults to 0)
     */
    data class Preset(val name: String, val hp: Int, val ac: Int, val initiative: Int = 0)

    /**
     * Overrideable in tests; when non-null, replaces the default config file path so
     * tests can target an isolated temporary file instead of `~/.tabletopcontrol/`.
     */
    internal var configFileForTest: File? = null

    private val configFile: File
        get() = configFileForTest ?: run {
            val dir = File(System.getProperty("user.home"), ".tabletopcontrol")
            dir.mkdirs()
            File(dir, "tracker-presets.conf")
        }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Saves [preset] to the library.  If a preset with the same [Preset.name]
     * already exists it is replaced; otherwise [preset] is appended.
     */
    fun savePreset(preset: Preset) {
        try {
            val existing = loadAll().toMutableList()
            val idx = existing.indexOfFirst { it.name == preset.name }
            if (idx >= 0) existing[idx] = preset else existing.add(preset)
            configFile.writeText(existing.joinToString("\n") { serialize(it) })
        } catch (_: Exception) {
            // non-fatal — proceed without persistence
        }
    }

    /**
     * Returns all presets stored in the library.
     *
     * @return a list of saved presets, or an empty list when the file is absent
     *         or contains no parseable lines.
     */
    fun loadAll(): List<Preset> =
        try {
            configFile.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { deserialize(it) }
        } catch (_: Exception) {
            emptyList()
        }

    /**
     * Removes the preset whose [Preset.name] equals [name].
     *
     * Does nothing when no such preset exists.
     */
    fun delete(name: String) {
        try {
            val remaining = loadAll().filter { it.name != name }
            configFile.writeText(remaining.joinToString("\n") { serialize(it) })
        } catch (_: Exception) {
            // non-fatal
        }
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    /**
     * Serialises [preset] to a single tab-separated line.
     *
     * Tab and newline characters in [Preset.name] are replaced with a space so
     * the four-field format is always unambiguous on deserialisation.
     */
    internal fun serialize(preset: Preset): String {
        val safeName = preset.name
            .replace('\t', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
        return "$safeName\t${preset.hp}\t${preset.ac}\t${preset.initiative}"
    }

    /**
     * Parses one tab-separated [line] into a [Preset].
     *
     * @return the parsed [Preset], or `null` when [line] does not have exactly
     *         four tab-separated fields or any numeric field fails to parse.
     */
    internal fun deserialize(line: String): Preset? {
        val parts = line.split('\t')
        if (parts.size != 4) return null
        val name = parts[0]
        val hp = parts[1].toIntOrNull() ?: return null
        val ac = parts[2].toIntOrNull() ?: return null
        val initiative = parts[3].toIntOrNull() ?: return null
        return Preset(name, hp, ac, initiative)
    }
}
