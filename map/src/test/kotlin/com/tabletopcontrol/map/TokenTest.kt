package com.tabletopcontrol.map

import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TokenTest {

    @Test
    fun `imageUri defaults to null`() {
        val token = Token(id = "1", name = "Goblin", col = 0, row = 0, color = Color.RED)
        assertNull(token.imageUri)
    }

    @Test
    fun `imageUri can be set to a file URI`() {
        val uri = "file:///home/dm/goblin.png"
        val token = Token(id = "1", name = "Goblin", col = 0, row = 0, color = Color.RED, imageUri = uri)
        assertEquals(uri, token.imageUri)
    }

    @Test
    fun `copy with new imageUri preserves other fields`() {
        val original = Token(id = "2", name = "Orc", col = 3, row = 5, color = Color.GREEN)
        val uri = "file:///home/dm/orc.png"
        val updated = original.copy(imageUri = uri)
        assertEquals(original.id, updated.id)
        assertEquals(original.name, updated.name)
        assertEquals(original.col, updated.col)
        assertEquals(original.row, updated.row)
        assertEquals(original.color, updated.color)
        assertEquals(uri, updated.imageUri)
    }

    @Test
    fun `copy can clear imageUri back to null`() {
        val token = Token(
            id = "3", name = "Dragon", col = 1, row = 1,
            color = Color.BLUE, imageUri = "file:///home/dm/dragon.png",
        )
        val cleared = token.copy(imageUri = null)
        assertNull(cleared.imageUri)
    }
}
