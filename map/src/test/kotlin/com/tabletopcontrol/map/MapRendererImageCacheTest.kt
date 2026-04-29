package com.tabletopcontrol.map

import javafx.scene.image.Image
import javafx.scene.image.WritableImage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class MapRendererImageCacheTest {
    @Test
    fun `cache token image when ready caches already loaded image immediately`() {
        val uri = "file:///tokens/hero.png"
        val imageCache = mutableMapOf<String, Image>()
        val readyCallbacks = mutableListOf<String>()
        val image = WritableImage(1, 1)

        cacheTokenImageWhenReady(uri, image, imageCache) {
            readyCallbacks += uri
        }

        assertSame(image, imageCache[uri])
        assertEquals(1, readyCallbacks.size)
        assertEquals(uri, readyCallbacks.single())
    }
}
