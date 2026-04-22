package com.tabletopcontrol.core.scene

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class SceneLibraryTest {
    private lateinit var tempDir: java.nio.file.Path

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("tc-scenes")
        SceneLibrary.scenesDirForTest = tempDir.toFile()
    }

    @AfterEach
    fun tearDown() {
        SceneLibrary.scenesDirForTest = null
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `save and load preserve opaque scene sections`() {
        val scene = SavedScene(
            name = "Goblin Ambush",
            sections = listOf(
                SceneSection("tracker", "tracker-payload"),
                SceneSection("map", "line one\nline two"),
            ),
        )

        SceneLibrary.save(scene)

        val loaded = SceneLibrary.loadAll()
        assertEquals(1, loaded.size)
        assertEquals(scene.name, loaded.single().name)
        assertEquals(scene.sections.sortedBy(SceneSection::key), loaded.single().sections)
        assertTrue(SceneLibrary.hasScene("Goblin Ambush"))
    }

    @Test
    fun `delete removes matching scene by name`() {
        SceneLibrary.save(SavedScene(name = "Keep", sections = emptyList()))
        SceneLibrary.save(SavedScene(name = "Delete Me", sections = emptyList()))

        SceneLibrary.delete("Delete Me")

        assertEquals(listOf("Keep"), SceneLibrary.loadAll().map(SavedScene::name))
        assertFalse(SceneLibrary.hasScene("Delete Me"))
    }
}
