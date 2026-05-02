package com.tabletopcontrol.canvas

import com.tabletopcontrol.core.EventBus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CanvasModelTest {

    private lateinit var model: CanvasModel
    private val published = mutableListOf<CanvasItemsChangedEvent>()

    @BeforeEach
    fun setUp() {
        EventBus.clear()
        model = CanvasModel()
        EventBus.subscribe<CanvasItemsChangedEvent> { published.add(it) }
    }

    @AfterEach
    fun tearDown() {
        EventBus.clear()
        published.clear()
    }

    @Test
    fun `addItem appends item and publishes event`() {
        val item = CanvasItem(filePath = "/tmp/test.png")
        model.addItem(item)

        assertEquals(1, model.items.size)
        assertEquals(item, model.items.first())
        assertEquals(1, published.size)
        assertEquals(listOf(item), published.last().items)
    }

    @Test
    fun `removeItem deletes matching item and publishes event`() {
        val item = CanvasItem(filePath = "/tmp/a.png")
        model.addItem(item)
        published.clear()

        model.removeItem(item.id)

        assertTrue(model.items.isEmpty())
        assertEquals(1, published.size)
        assertTrue(published.last().items.isEmpty())
    }

    @Test
    fun `removeItem with unknown id is a no-op`() {
        val item = CanvasItem(filePath = "/tmp/a.png")
        model.addItem(item)
        val countBefore = published.size

        model.removeItem("non-existent-id")

        assertEquals(1, model.items.size)
        assertEquals(countBefore, published.size) // no new event
    }

    @Test
    fun `updateItem replaces matching item and publishes event`() {
        val item = CanvasItem(filePath = "/tmp/a.png", x = 0.1)
        model.addItem(item)
        published.clear()

        val updated = item.copy(x = 0.5)
        model.updateItem(updated)

        assertEquals(1, model.items.size)
        assertEquals(0.5, model.items.first().x)
        assertEquals(1, published.size)
    }

    @Test
    fun `updateItem with unknown id is a no-op`() {
        val item = CanvasItem(filePath = "/tmp/a.png")
        model.addItem(item)
        val countBefore = published.size

        model.updateItem(item.copy(id = "ghost", x = 0.9))

        assertEquals(item.x, model.items.first().x)
        assertEquals(countBefore, published.size)
    }

    @Test
    fun `setShareAll sets all items shared and publishes event`() {
        model.addItem(CanvasItem(filePath = "/tmp/a.png", isShared = false))
        model.addItem(CanvasItem(filePath = "/tmp/b.png", isShared = false))
        published.clear()

        model.setShareAll(true)

        assertTrue(model.items.all { it.isShared })
        assertEquals(1, published.size)
    }

    @Test
    fun `setShareAll false un-shares all items`() {
        model.addItem(CanvasItem(filePath = "/tmp/a.png", isShared = true))
        model.addItem(CanvasItem(filePath = "/tmp/b.png", isShared = true))
        published.clear()

        model.setShareAll(false)

        assertFalse(model.items.any { it.isShared })
    }

    @Test
    fun `items returns immutable snapshot`() {
        val item = CanvasItem(filePath = "/tmp/a.png")
        model.addItem(item)

        val snapshot = model.items
        model.addItem(CanvasItem(filePath = "/tmp/b.png"))

        // The snapshot captured before the second add should still have just one element.
        assertEquals(1, snapshot.size)
        assertEquals(2, model.items.size)
    }

    @Test
    fun `bringToFront moves item to end of list and publishes event`() {
        val a = CanvasItem(filePath = "/tmp/a.png")
        val b = CanvasItem(filePath = "/tmp/b.png")
        val c = CanvasItem(filePath = "/tmp/c.png")
        model.addItem(a); model.addItem(b); model.addItem(c)
        published.clear()

        model.bringToFront(a.id)

        assertEquals(listOf(b.id, c.id, a.id), model.items.map { it.id })
        assertEquals(1, published.size)
    }

    @Test
    fun `bringToFront on already-front item is a no-op`() {
        val a = CanvasItem(filePath = "/tmp/a.png")
        val b = CanvasItem(filePath = "/tmp/b.png")
        model.addItem(a); model.addItem(b)
        val countBefore = published.size

        model.bringToFront(b.id)

        assertEquals(listOf(a.id, b.id), model.items.map { it.id })
        assertEquals(countBefore, published.size)
    }

    @Test
    fun `sendToBack moves item to beginning of list and publishes event`() {
        val a = CanvasItem(filePath = "/tmp/a.png")
        val b = CanvasItem(filePath = "/tmp/b.png")
        val c = CanvasItem(filePath = "/tmp/c.png")
        model.addItem(a); model.addItem(b); model.addItem(c)
        published.clear()

        model.sendToBack(c.id)

        assertEquals(listOf(c.id, a.id, b.id), model.items.map { it.id })
        assertEquals(1, published.size)
    }

    @Test
    fun `sendToBack on already-back item is a no-op`() {
        val a = CanvasItem(filePath = "/tmp/a.png")
        val b = CanvasItem(filePath = "/tmp/b.png")
        model.addItem(a); model.addItem(b)
        val countBefore = published.size

        model.sendToBack(a.id)

        assertEquals(listOf(a.id, b.id), model.items.map { it.id })
        assertEquals(countBefore, published.size)
    }
}
