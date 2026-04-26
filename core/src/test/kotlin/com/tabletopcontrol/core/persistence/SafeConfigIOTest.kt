package com.tabletopcontrol.core.persistence

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SafeConfigIOTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `readOrElse returns fallback when block throws`() {
        val value = SafeConfigIO.readOrElse("fallback") { error("boom") }
        assertEquals("fallback", value)
    }

    @Test
    fun `readTextOrNull returns null when file does not exist`() {
        val missing = File(tempDir, "missing.conf")
        assertNull(SafeConfigIO.readTextOrNull(missing))
    }

    @Test
    fun `writeText is non fatal when write fails`() {
        val unwritableTarget = File(tempDir, "missing-dir/file.conf")
        assertDoesNotThrow { SafeConfigIO.writeText(unwritableTarget, "data") }
    }

    @Test
    fun `writeTextAtomically creates parent directories and writes file contents`() {
        val target = File(tempDir, "nested/scenes/test.scene")

        ConfigFiles.writeTextAtomically(target, "scene-data")

        assertEquals("scene-data", target.readText())
    }

    @Test
    fun `sanitizeFilename replaces unsupported characters`() {
        assertEquals("Goblin_Boss_1", ConfigFiles.sanitizeFilename("Goblin/Boss:1"))
    }
}
