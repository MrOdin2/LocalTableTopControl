package com.tabletopcontrol.audio

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MusicSettingsSerializerTest {

    @TempDir
    lateinit var tempDir: File

    private var originalUserHome: String? = null

    @BeforeEach
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.absolutePath)
    }

    @AfterEach
    fun tearDown() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome!!)
        } else {
            System.clearProperty("user.home")
        }
    }

    @Test
    fun `load returns defaults when config does not exist`() {
        val loaded = MusicSettingsSerializer.load()
        assertEquals(1.0, loaded.masterVolume)
        assertEquals(1, loaded.tracks.size)
        assertNull(loaded.tracks[0].uri)
        assertEquals(1.0, loaded.tracks[0].volume)
        assertTrue(loaded.tracks[0].loop)
    }

    @Test
    fun `save and load round trip preserves order and values`() {
        val settings = MusicSettings(
            masterVolume = 0.35,
            tracks = listOf(
                PersistedMusicTrack(uri = "file:///music/one.mp3", volume = 0.8, loop = true),
                PersistedMusicTrack(uri = "file:///music/two.mp3", volume = 0.2, loop = false),
            ),
        )

        MusicSettingsSerializer.save(settings)
        val loaded = MusicSettingsSerializer.load()

        assertEquals(0.35, loaded.masterVolume)
        assertEquals(2, loaded.tracks.size)
        assertEquals("file:///music/one.mp3", loaded.tracks[0].uri)
        assertEquals(0.8, loaded.tracks[0].volume)
        assertTrue(loaded.tracks[0].loop)
        assertEquals("file:///music/two.mp3", loaded.tracks[1].uri)
        assertEquals(0.2, loaded.tracks[1].volume)
        assertEquals(false, loaded.tracks[1].loop)
    }

    @Test
    fun `load migrates legacy one-based three-track keys`() {
        val configDir = File(tempDir, ".tabletopcontrol").also { it.mkdirs() }
        File(configDir, "music.conf").writeText(
            """
            masterVolume=0.7
            track1.uri=file:///legacy/one.mp3
            track1.volume=0.4
            track1.loop=true
            track2.uri=file:///legacy/two.mp3
            track2.volume=0.9
            track2.loop=false
            track3.volume=0.5
            """.trimIndent(),
        )

        val loaded = MusicSettingsSerializer.load()
        assertEquals(0.7, loaded.masterVolume)
        assertEquals(3, loaded.tracks.size)
        assertEquals("file:///legacy/one.mp3", loaded.tracks[0].uri)
        assertEquals(0.4, loaded.tracks[0].volume)
        assertTrue(loaded.tracks[0].loop)
        assertEquals("file:///legacy/two.mp3", loaded.tracks[1].uri)
        assertEquals(0.9, loaded.tracks[1].volume)
        assertEquals(false, loaded.tracks[1].loop)
        assertNull(loaded.tracks[2].uri)
        assertEquals(0.5, loaded.tracks[2].volume)
        assertTrue(loaded.tracks[2].loop)
    }
}
