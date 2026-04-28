package com.tabletopcontrol.dynamicmap.runtime.rendering

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.glutils.HdpiMode
import java.util.Locale

/**
 * Shared LWJGL3 configuration for any future hosted DynamicMap LibGDX surface.
 *
 * Stencil bits are required for sightline masks and circular token-image clipping.
 * The renderer itself still disables continuous rendering in [LibGdxDynamicMapRenderer.create].
 */
internal object LibGdxDynamicMapHostConfig {
    fun create(
        title: String = "TabletopControl DynamicMap",
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
    ): Lwjgl3ApplicationConfiguration {
        if (isMacOs()) {
            Lwjgl3ApplicationConfiguration.useGlfwAsync()
        }
        return Lwjgl3ApplicationConfiguration().apply {
            setTitle(title)
            setWindowedMode(width, height)
            setResizable(true)
            setDecorated(false)
            setInitialVisible(false)
            setAutoIconify(false)
            setBackBufferConfig(
                8,
                8,
                8,
                8,
                16,
                8,
                0,
            )
            disableAudio(true)
            setHdpiMode(HdpiMode.Logical)
            setPauseWhenLostFocus(false)
            setPauseWhenMinimized(false)
            useVsync(true)
            setForegroundFPS(60)
            setIdleFPS(5)
        }
    }

    private fun isMacOs(): Boolean =
        System.getProperty("os.name").lowercase(Locale.ROOT).contains("mac")

    private const val DEFAULT_WIDTH = 1280
    private const val DEFAULT_HEIGHT = 720
}
