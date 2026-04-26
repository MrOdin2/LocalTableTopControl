package com.tabletopcontrol.audio

import com.tabletopcontrol.core.persistence.AppConfigPaths
import com.tabletopcontrol.core.persistence.SafeConfigIO
import java.io.StringWriter
import java.util.Properties

/**
 * Persisted state for one music track.
 *
 * @property uri    File URI for the loaded track, or `null` when no file is assigned.
 * @property volume Per-track volume in the range 0.0–1.0.
 * @property loop   Whether the track loops indefinitely.
 */
data class PersistedMusicTrack(
    val uri: String? = null,
    val volume: Double = 1.0,
    val loop: Boolean = true,
)

/**
 * Persisted state for the music plugin.
 *
 * @property masterVolume Global master-volume multiplier in the range 0.0–1.0.
 * @property tracks       Ordered list of track states.
 */
data class MusicSettings(
    val masterVolume: Double = 1.0,
    val tracks: List<PersistedMusicTrack> = listOf(PersistedMusicTrack()),
)

/**
 * Handles loading and saving music-plugin settings from `~/.tabletopcontrol/music.conf`.
 *
 * Current format (version 2) stores:
 * - `masterVolume`
 * - `track.count`
 * - `track.{index}.uri`
 * - `track.{index}.volume`
 * - `track.{index}.loop`
 *
 * Legacy migration is supported for earlier fixed 3-track key patterns.
 */
object MusicSettingsSerializer {

    internal const val CONFIG_NAME = "music.conf"
    private const val CURRENT_VERSION = 2

    private val configFile
        get() = AppConfigPaths.configFile(CONFIG_NAME)

    /**
     * Saves [settings] to disk.
     *
     * I/O errors are intentionally swallowed so persistence never crashes the app.
     */
    fun save(settings: MusicSettings) {
        SafeConfigIO.run { configFile.writeText(serialize(settings)) }
    }

    /**
     * Loads settings from disk, migrating legacy 3-track formats when present.
     */
    fun load(): MusicSettings {
        val text = SafeConfigIO.readOrElse(null) { configFile.readText() } ?: run {
            return MusicSettings()
        }
        return deserialize(text) ?: MusicSettings()
    }

    fun serialize(settings: MusicSettings): String {
        val master = settings.masterVolume.coerceIn(0.0, 1.0)
        val tracks = settings.tracks
            .take(MAX_MUSIC_TRACKS)
            .ifEmpty { listOf(PersistedMusicTrack()) }
            .map { track ->
                PersistedMusicTrack(
                    uri = track.uri?.takeIf { it.isNotBlank() },
                    volume = track.volume.coerceIn(0.0, 1.0),
                    loop = track.loop,
                )
            }

        val props = Properties().apply {
            setProperty("version", CURRENT_VERSION.toString())
            setProperty("masterVolume", master.toString())
            setProperty("track.count", tracks.size.toString())
            tracks.forEachIndexed { index, track ->
                val base = "track.$index"
                track.uri?.let { setProperty("$base.uri", it) }
                setProperty("$base.volume", track.volume.toString())
                setProperty("$base.loop", track.loop.toString())
            }
        }

        return StringWriter().use { writer ->
            props.store(writer, "TabletopControl music settings")
            writer.toString()
        }
    }

    fun deserialize(text: String): MusicSettings? {
        val props = runCatching {
            Properties().also { loaded ->
                text.reader().use { loaded.load(it) }
            }
        }.getOrNull() ?: return null

        val master = props.getProperty("masterVolume")?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0
        val tracks = loadCurrentFormat(props)
            .ifEmpty { loadLegacyFormat(props) }
            .ifEmpty { listOf(PersistedMusicTrack()) }
            .take(MAX_MUSIC_TRACKS)

        return MusicSettings(masterVolume = master, tracks = tracks)
    }

    private fun loadCurrentFormat(props: Properties): List<PersistedMusicTrack> {
        val count = props.getProperty("track.count")?.toIntOrNull() ?: return emptyList()
        if (count <= 0) return emptyList()
        return (0 until count.coerceIn(1, MAX_MUSIC_TRACKS)).map { index ->
            parseTrack(
                uri = props.getProperty("track.$index.uri"),
                volumeRaw = props.getProperty("track.$index.volume"),
                loopRaw = props.getProperty("track.$index.loop"),
            )
        }
    }

    private fun loadLegacyFormat(props: Properties): List<PersistedMusicTrack> {
        val indexedPattern = Regex("""^track\.(\d+)\.(uri|volume|loop)$""")
        val discovered = props.stringPropertyNames()
            .mapNotNull { key -> indexedPattern.matchEntire(key)?.groupValues?.get(1)?.toIntOrNull() }
            .distinct()
            .sorted()
        if (discovered.isNotEmpty()) {
            return discovered.take(MAX_MUSIC_TRACKS).map { index ->
                parseTrack(
                    uri = props.getProperty("track.$index.uri"),
                    volumeRaw = props.getProperty("track.$index.volume"),
                    loopRaw = props.getProperty("track.$index.loop"),
                )
            }
        }

        val oneBasedTracks = mutableListOf<PersistedMusicTrack>()
        for (index in 1..MAX_MUSIC_TRACKS) {
            val uri = props.getProperty("track$index.uri")
            val volume = props.getProperty("track$index.volume")
            val loop = props.getProperty("track$index.loop")
            if (uri != null || volume != null || loop != null) {
                oneBasedTracks += parseTrack(uri = uri, volumeRaw = volume, loopRaw = loop)
            }
        }
        return oneBasedTracks
    }

    private fun parseTrack(uri: String?, volumeRaw: String?, loopRaw: String?): PersistedMusicTrack {
        return PersistedMusicTrack(
            uri = uri?.takeIf { it.isNotBlank() },
            volume = volumeRaw?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0,
            loop = loopRaw?.toBooleanStrictOrNull() ?: true,
        )
    }
}
