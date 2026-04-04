package com.tabletopcontrol.core

import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import javafx.scene.layout.Pane
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DmLayoutManagerContextMenuTest {

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
        assertSame(second, manager.activeContextMenuForTesting())
    }

    @Test
    fun `clear active menu only clears matching menu`() {
        val manager = managerWithPlugin()
        val active = mockk<DmLayoutManager.ManagedMenu>(relaxed = true)
        val other = mockk<DmLayoutManager.ManagedMenu>(relaxed = true)

        manager.registerAndPrepareMenu(active)
        manager.clearActiveMenuIf(other)
        assertSame(active, manager.activeContextMenuForTesting())

        manager.clearActiveMenuIf(active)
        assertNull(manager.activeContextMenuForTesting())
    }
}
