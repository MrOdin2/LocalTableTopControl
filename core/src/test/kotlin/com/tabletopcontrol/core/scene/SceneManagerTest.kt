package com.tabletopcontrol.core.scene

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class SceneManagerTest {
    private lateinit var tempDir: java.nio.file.Path

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("tc-scene-manager")
        SceneLibrary.scenesDirForTest = tempDir.toFile()
    }

    @AfterEach
    fun tearDown() {
        SceneLibrary.scenesDirForTest = null
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `loadScene applies sections in ascending load order`() {
        val applied = mutableListOf<String>()
        val manager = SceneManager(
            participants = listOf(
                testParticipant(key = "second", order = 200, applied = applied),
                testParticipant(key = "first", order = 100, applied = applied),
            ),
        )

        manager.loadScene(
            SavedScene(
                name = "Encounter",
                sections = listOf(
                    SceneSection("second", "b"),
                    SceneSection("first", "a"),
                ),
            ),
        )

        assertEquals(listOf("first:a", "second:b"), applied)
    }

    @Test
    fun `saveScene collects participant payloads`() {
        val manager = SceneManager(
            participants = listOf(
                object : SceneParticipant {
                    override val sceneKey: String = "tracker"
                    override fun captureSceneState(): String = "tracker-payload"
                    override fun applySceneState(payload: String) = Unit
                },
                object : SceneParticipant {
                    override val sceneKey: String = "map"
                    override fun captureSceneState(): String? = null
                    override fun applySceneState(payload: String) = Unit
                },
            ),
        )

        val result = manager.saveScene("Battle")

        assertTrue(result.failures.isEmpty())
        assertEquals(
            listOf(SceneSection("tracker", "tracker-payload")),
            result.scene.sections,
        )
    }

    private fun testParticipant(
        key: String,
        order: Int,
        applied: MutableList<String>,
    ): SceneParticipant =
        object : SceneParticipant {
            override val sceneKey: String = key
            override val sceneLoadOrder: Int = order

            override fun captureSceneState(): String = key

            override fun applySceneState(payload: String) {
                applied += "$key:$payload"
            }
        }
}
