package com.tabletopcontrol.audio.shared

import javafx.beans.value.ChangeListener
import javafx.util.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaTrackControllerTest {

    @Test
    fun `load returns false when loader fails`() {
        val controller = MediaTrackController(
            playerLoader = { null },
        )

        val loaded = controller.load("file:///missing.mp3", volume = 1.0, cycleCount = 1)

        assertFalse(loaded)
        assertFalse(controller.hasPlayer())
        assertNull(controller.status())
    }

    @Test
    fun `load applies volume and cycle count`() {
        val player = FakeManagedMediaPlayer()
        val controller = MediaTrackController(
            playerLoader = { player },
        )

        val loaded = controller.load("file:///track.mp3", volume = 0.35, cycleCount = 7)

        assertTrue(loaded)
        assertEquals(0.35, player.volume, 0.0001)
        assertEquals(7, player.cycleCount)
    }

    @Test
    fun `callbacks fire for ready progress end and error`() {
        val player = FakeManagedMediaPlayer().apply {
            duration = Duration.seconds(30.0)
        }
        var ready = 0
        var error = 0
        var ended = 0
        var progressCurrent = Duration.ZERO
        var progressTotal = Duration.ZERO
        val controller = MediaTrackController(
            playerLoader = { player },
        )
        controller.bindCallbacks(
            onReady = { ready++ },
            onProgress = { current, total ->
                progressCurrent = current
                progressTotal = total
            },
            onError = { error++ },
            onEndOfMedia = { ended++ },
        )

        controller.load("file:///track.mp3", volume = 1.0, cycleCount = 1)
        player.fireReady()
        player.fireProgress(Duration.seconds(5.0))
        player.fireEndOfMedia()
        player.fireError()

        assertEquals(1, ready)
        assertEquals(Duration.seconds(5.0), progressCurrent)
        assertEquals(Duration.seconds(30.0), progressTotal)
        assertEquals(1, ended)
        assertEquals(1, error)
    }

    @Test
    fun `bindCallbacks avoids progress listener work when onProgress is unset`() {
        val player = FakeManagedMediaPlayer()
        val controller = MediaTrackController(
            playerLoader = { player },
        )

        controller.bindCallbacks(
            onReady = {},
            onProgress = null,
            onError = {},
            onEndOfMedia = {},
        )
        controller.load("file:///track.mp3", volume = 1.0, cycleCount = 1)

        assertEquals(0, player.listenerCount())

        controller.bindCallbacks(
            onReady = {},
            onProgress = { _, _ -> },
            onError = {},
            onEndOfMedia = {},
        )

        assertEquals(1, player.listenerCount())

        controller.bindCallbacks(
            onReady = {},
            onProgress = null,
            onError = {},
            onEndOfMedia = {},
        )

        assertEquals(0, player.listenerCount())
    }

    @Test
    fun `setVolume updates managed player volume and clamps to bounds`() {
        val player = FakeManagedMediaPlayer()
        val controller = MediaTrackController(
            playerLoader = { player },
        )
        controller.load("file:///track.mp3", volume = 0.2, cycleCount = 1)

        controller.setVolume(0.7)
        assertEquals(0.7, player.volume, 0.0001)

        controller.setVolume(2.0)
        assertEquals(1.0, player.volume, 0.0001)

        controller.setVolume(-1.0)
        assertEquals(0.0, player.volume, 0.0001)
    }

    @Test
    fun `dispose clears handlers and disposes player`() {
        val player = FakeManagedMediaPlayer()
        val controller = MediaTrackController(
            playerLoader = { player },
        )
        controller.load("file:///track.mp3", volume = 1.0, cycleCount = 1)

        controller.dispose()

        assertTrue(player.stopCalled)
        assertTrue(player.disposeCalled)
        assertNull(player.readyHandler)
        assertNull(player.endOfMediaHandler)
        assertNull(player.errorHandler)
        assertEquals(0, player.listenerCount())
        assertFalse(controller.hasPlayer())
    }

    @Test
    fun `play and pause delegate to managed player`() {
        val player = FakeManagedMediaPlayer()
        val controller = MediaTrackController(
            playerLoader = { player },
        )
        controller.load("file:///track.mp3", volume = 1.0, cycleCount = 1)

        controller.play()
        assertEquals(MediaTrackStatus.PLAYING, player.currentStatus)

        controller.pause()
        assertEquals(MediaTrackStatus.PAUSED, player.currentStatus)
    }

    private class FakeManagedMediaPlayer : ManagedMediaPlayer {
        override var volume: Double = 1.0
        override var cycleCount: Int = 1
        override var currentStatus: MediaTrackStatus = MediaTrackStatus.UNKNOWN
        override var currentTime: Duration = Duration.ZERO
        override var duration: Duration = Duration.seconds(10.0)

        private val listeners = mutableSetOf<ChangeListener<Duration>>()

        var readyHandler: (() -> Unit)? = null
        var endOfMediaHandler: (() -> Unit)? = null
        var errorHandler: (() -> Unit)? = null
        var stopCalled = false
        var disposeCalled = false

        override fun addCurrentTimeListener(listener: ChangeListener<Duration>) {
            listeners += listener
        }

        override fun removeCurrentTimeListener(listener: ChangeListener<Duration>) {
            listeners -= listener
        }

        override fun setOnReady(handler: (() -> Unit)?) {
            readyHandler = handler
        }

        override fun setOnEndOfMedia(handler: (() -> Unit)?) {
            endOfMediaHandler = handler
        }

        override fun setOnError(handler: (() -> Unit)?) {
            errorHandler = handler
        }

        override fun play() {
            currentStatus = MediaTrackStatus.PLAYING
        }

        override fun pause() {
            currentStatus = MediaTrackStatus.PAUSED
        }

        override fun stop() {
            stopCalled = true
            currentStatus = MediaTrackStatus.STOPPED
        }

        override fun dispose() {
            disposeCalled = true
            currentStatus = MediaTrackStatus.DISPOSED
        }

        fun listenerCount(): Int = listeners.size

        fun fireReady() {
            readyHandler?.invoke()
        }

        fun fireEndOfMedia() {
            endOfMediaHandler?.invoke()
        }

        fun fireError() {
            errorHandler?.invoke()
        }

        fun fireProgress(current: Duration) {
            currentTime = current
            listeners.forEach { it.changed(null, null, current) }
        }
    }
}
