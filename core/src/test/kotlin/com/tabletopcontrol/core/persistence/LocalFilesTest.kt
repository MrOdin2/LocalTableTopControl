package com.tabletopcontrol.core.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LocalFilesTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `fileFromUriOrPath supports local file uris and raw paths`() {
        val target = File(tempDir, "goblin.png").also { it.writeText("x") }

        assertEquals(target.absolutePath, LocalFiles.fileFromUriOrPath(target.absolutePath)?.absolutePath)
        assertEquals(target.absolutePath, LocalFiles.fileFromUriOrPath(target.toURI().toString())?.absolutePath)
    }

    @Test
    fun `fileFromUriOrPath rejects non local uris`() {
        assertNull(LocalFiles.fileFromUriOrPath("https://example.com/token.png"))
    }

    @Test
    fun `normalizeLocalFileUri canonicalizes local file references`() {
        val target = File(tempDir, "wolf.png").also { it.writeText("x") }

        assertEquals(target.canonicalFile.toURI().toString(), LocalFiles.normalizeLocalFileUri(target.absolutePath))
        assertEquals(target.canonicalFile.toURI().toString(), LocalFiles.normalizeLocalFileUri(target.toURI().toString()))
    }

    @Test
    fun `exists and display helpers use local file references`() {
        val target = File(tempDir, "ambience.mp3").also { it.writeText("x") }
        val uri = target.toURI().toString()

        assertTrue(LocalFiles.exists(uri))
        assertFalse(LocalFiles.exists("file:///missing/audio.mp3"))
        assertEquals(target.absolutePath, LocalFiles.absolutePath(uri))
        assertEquals("ambience.mp3", LocalFiles.fileName(uri))
        assertNotNull(LocalFiles.absolutePath(target.absolutePath))
    }
}
