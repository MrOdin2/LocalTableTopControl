package com.tabletopcontrol.core

import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import javafx.scene.layout.Pane
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DmLayoutManagerContextMenuTest {
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
        originalUserHome?.let {
            System.setProperty("user.home", it)
        } ?: run {
            System.clearProperty("user.home")
        }
    }

    private fun managerWithPlugin(): DmLayoutManager =
        DmLayoutManager(
            listOf(
                object : DmPlugin {
                    override val displayName: String = "Test Plugin"
                    override fun createView() = Pane()
                },
            ),
        )

    @Test
    fun `registering a new context menu hides the previous menu`() {
        val manager = managerWithPlugin()
        val first = mockk<DmLayoutManager.ManagedMenu>()
        justRun { first.hide() }
        manager.registerAndPrepareMenu(first)

        val second = mockk<DmLayoutManager.ManagedMenu>()
        justRun { second.hide() }
        manager.registerAndPrepareMenu(second)

        verify(exactly = 1) { first.hide() }
        assertSame(second, manager.activeMenuForTesting())
    }

    @Test
    fun `clear active menu only clears matching menu`() {
        val manager = managerWithPlugin()
        val active = mockk<DmLayoutManager.ManagedMenu>(relaxed = true)
        val other = mockk<DmLayoutManager.ManagedMenu>(relaxed = true)

        manager.registerAndPrepareMenu(active)
        manager.clearActiveMenuIf(other)
        assertSame(active, manager.activeMenuForTesting())

        manager.clearActiveMenuIf(active)
        assertNull(manager.activeMenuForTesting())
    }
}
