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

        val savedText = tempDir.resolve("Goblin Ambush.scene").toFile().readText()
        assertTrue(savedText.trimStart().startsWith("<scene"))
        assertTrue(savedText.contains("<section key=\"map\"><![CDATA[line one\nline two]]></section>"))
        assertTrue(savedText.contains("<section key=\"tracker\"><![CDATA[tracker-payload]]></section>"))
    }

    @Test
    fun `load all migrates legacy scene files to readable xml`() {
        val scene = SavedScene(
            name = "Legacy Scene",
            sections = listOf(
                SceneSection("tracker", "legacy-tracker"),
                SceneSection("map", "legacy-map"),
            ),
        )
        val file = tempDir.resolve("Legacy Scene.scene").toFile()
        file.writeText(SceneLibrary.serializeLegacy(scene))

        val loaded = SceneLibrary.loadAll()

        assertEquals(listOf(scene.copy(sections = scene.sections.sortedBy(SceneSection::key))), loaded)
        val migratedText = file.readText()
        assertTrue(migratedText.trimStart().startsWith("<scene"))
        assertTrue(migratedText.contains("legacy-map"))
        assertTrue(migratedText.contains("legacy-tracker"))
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
