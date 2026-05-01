package com.tabletopcontrol.dynamicmap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DynamicMapConstructionSiteStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `construction sites save load list and track the active site`() {
        val store = testStore()
        val document = DynamicMapDocument(
            cols = 18,
            rows = 12,
            walls = listOf(
                DynamicMapWall(
                    id = "wall-1",
                    label = "Gate",
                    start = DynamicMapPoint(1.0, 2.0),
                    end = DynamicMapPoint(3.0, 2.0),
                ),
            ),
        )

        val site = store.createSite("Goblin Cave!", document) ?: error("Expected site to be created")

        assertEquals("goblin-cave", site.id)
        assertEquals("Goblin Cave!", site.name)
        assertEquals(document, store.loadSite(site.id))
        assertEquals(listOf(site.id), store.listSites().map { it.id })

        store.saveActiveSite(site)

        assertEquals(site.id, store.loadActiveSite()?.id)
    }

    @Test
    fun `duplicate construction site names receive unique file ids`() {
        val store = testStore()

        val first = store.createSite("Dungeon Level", DynamicMapDocument()) ?: error("Expected first site")
        val second = store.createSite("Dungeon Level", DynamicMapDocument(cols = 40)) ?: error("Expected second site")

        assertEquals("dungeon-level", first.id)
        assertEquals("dungeon-level-2", second.id)
        assertEquals(listOf("dungeon-level", "dungeon-level-2"), store.listSites().map { it.id })
        assertEquals(40, store.loadSite(second.id)?.cols)
    }

    @Test
    fun `deleting an active site makes active lookup empty`() {
        val store = testStore()
        val site = store.createSite("Temporary Site", DynamicMapDocument()) ?: error("Expected site")
        store.saveActiveSite(site)

        store.deleteSite(site.id)

        assertNull(store.loadSite(site.id))
        assertNull(store.loadActiveSite())
        assertEquals(emptyList<DynamicMapConstructionSiteSummary>(), store.listSites())
    }

    private fun testStore(): DynamicMapConstructionSiteStore =
        DynamicMapConstructionSiteStore(
            sitesDir = tempDir.resolve("sites").toFile(),
            activeSiteFile = tempDir.resolve("active-site.properties").toFile(),
        )
}
