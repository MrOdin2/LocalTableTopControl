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
            runOnFx = { it() },
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
            runOnFx = { it() },
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
            runOnFx = { it() },
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
    fun `dispose clears handlers and disposes player`() {
        val player = FakeManagedMediaPlayer()
        val controller = MediaTrackController(
            runOnFx = { it() },
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

    private class FakeManagedMediaPlayer : ManagedMediaPlayer {
        override var volume: Double = 1.0
        override var cycleCount: Int = 1
        override var status: MediaTrackStatus = MediaTrackStatus.READY
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
            status = MediaTrackStatus.PLAYING
        }

        override fun pause() {
            status = MediaTrackStatus.PAUSED
        }

        override fun stop() {
            stopCalled = true
            status = MediaTrackStatus.STOPPED
        }

        override fun dispose() {
            disposeCalled = true
            status = MediaTrackStatus.DISPOSED
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
