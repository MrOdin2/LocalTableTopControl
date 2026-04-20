package com.tabletopcontrol.core.ui.dialog

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties

class FileChooserHistoryStoreTest {

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
    fun `rememberSelection stores the selected file parent directory`() {
        val directory = File(tempDir, "maps").also { it.mkdirs() }
        val selectedFile = File(directory, "dungeon.png").also { it.writeText("x") }

        FileChooserHistoryStore.rememberSelection("map.load", selectedFile)

        assertEquals(directory.absolutePath, FileChooserHistoryStore.initialDirectoryFor("map.load")?.absolutePath)
    }

    @Test
    fun `initialDirectoryFor falls back to the current selection parent when nothing is remembered`() {
        val directory = File(tempDir, "audio").also { it.mkdirs() }
        val selectedFile = File(directory, "ambience.mp3").also { it.writeText("x") }

        val resolved = FileChooserHistoryStore.initialDirectoryFor("music.browser", selectedFile)

        assertEquals(directory.absolutePath, resolved?.absolutePath)
    }

    @Test
    fun `initialDirectoryFor ignores missing remembered directories and uses fallback instead`() {
        val missingDirectory = File(tempDir, "missing")
        val fallbackDirectory = File(tempDir, "tokens").also { it.mkdirs() }
        val fallbackFile = File(fallbackDirectory, "goblin.png").also { it.writeText("x") }

        val configDir = File(tempDir, ".tabletopcontrol").also { it.mkdirs() }
        File(configDir, FileChooserHistoryStore.CONFIG_NAME).writer().use { writer ->
            Properties().apply {
                setProperty("tracker.token-image", missingDirectory.absolutePath)
            }.store(writer, "test")
        }

        val resolved = FileChooserHistoryStore.initialDirectoryFor("tracker.token-image", fallbackFile)

        assertEquals(fallbackDirectory.absolutePath, resolved?.absolutePath)
    }

    @Test
    fun `fileFromUri resolves local file uris`() {
        val directory = File(tempDir, "soundboard").also { it.mkdirs() }
        val selectedFile = File(directory, "hit.wav").also { it.writeText("x") }

        val resolved = FileChooserHistoryStore.fileFromUri(selectedFile.toURI().toString())

        assertEquals(selectedFile.absolutePath, resolved?.absolutePath)
    }

    @Test
    fun `fileFromUri rejects non local uris`() {
        assertNull(FileChooserHistoryStore.fileFromUri("https://example.com/theme.mp3"))
    }
}
