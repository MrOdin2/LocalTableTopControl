package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.dynamicmap.runtime.logic.Token
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TokenTest {

    @Test
    fun `imageUri defaults to null`() {
        val token = Token(id = "1", name = "Goblin", col = 0, row = 0, color = Color.RED)
        assertEquals(TokenSize.MEDIUM, token.size)
        assertNull(token.imageUri)
        assertEquals(1.0, token.imageScaleX)
        assertEquals(1.0, token.imageScaleY)
        assertEquals(0.0, token.imageOffsetX)
        assertEquals(0.0, token.imageOffsetY)
    }

    @Test
    fun `imageUri can be set to a file URI`() {
        val uri = "file:///home/dm/goblin.png"
        val token = Token(
            id = "1",
            name = "Goblin",
            col = 0,
            row = 0,
            size = TokenSize.LARGE,
            color = Color.RED,
            imageUri = uri,
            imageScaleX = 1.2,
            imageScaleY = 0.8,
            imageOffsetX = 4.0,
            imageOffsetY = -3.0,
        )
        assertEquals(TokenSize.LARGE, token.size)
        assertEquals(uri, token.imageUri)
        assertEquals(1.2, token.imageScaleX)
        assertEquals(0.8, token.imageScaleY)
        assertEquals(4.0, token.imageOffsetX)
        assertEquals(-3.0, token.imageOffsetY)
    }

    @Test
    fun `copy with new imageUri preserves other fields`() {
        val original = Token(id = "2", name = "Orc", col = 3, row = 5, color = Color.GREEN)
        val uri = "file:///home/dm/orc.png"
        val updated = original.copy(
            size = TokenSize.HUGE,
            imageUri = uri,
            imageScaleX = 1.5,
            imageScaleY = 0.6,
            imageOffsetX = -2.0,
            imageOffsetY = 7.0,
        )
        assertEquals(original.id, updated.id)
        assertEquals(original.name, updated.name)
        assertEquals(original.col, updated.col)
        assertEquals(original.row, updated.row)
        assertEquals(TokenSize.HUGE, updated.size)
        assertEquals(original.color, updated.color)
        assertEquals(uri, updated.imageUri)
        assertEquals(1.5, updated.imageScaleX)
        assertEquals(0.6, updated.imageScaleY)
        assertEquals(-2.0, updated.imageOffsetX)
        assertEquals(7.0, updated.imageOffsetY)
    }

    @Test
    fun `copy can clear imageUri back to null`() {
        val token = Token(
            id = "3", name = "Dragon", col = 1, row = 1,
            color = Color.BLUE, imageUri = "file:///home/dm/dragon.png",
        )
        val cleared = token.copy(
            imageUri = null,
            imageScaleX = 1.0,
            imageScaleY = 1.0,
            imageOffsetX = 0.0,
            imageOffsetY = 0.0,
        )
        assertNull(cleared.imageUri)
        assertEquals(1.0, cleared.imageScaleX)
        assertEquals(1.0, cleared.imageScaleY)
        assertEquals(0.0, cleared.imageOffsetX)
        assertEquals(0.0, cleared.imageOffsetY)
    }
}
