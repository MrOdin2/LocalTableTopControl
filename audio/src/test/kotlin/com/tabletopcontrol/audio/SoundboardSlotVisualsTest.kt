package com.tabletopcontrol.audio

import com.tabletopcontrol.audio.shared.ManagedMediaPlayer
import com.tabletopcontrol.audio.shared.MediaTrackController
import com.tabletopcontrol.audio.shared.MediaTrackStatus
import javafx.beans.value.ChangeListener
import javafx.util.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI

class SoundboardSlotVisualsTest {

    @Test
    fun `tooltipTextForUri uses absolute path for file uri`() {
        val uri = "file:///tmp/soundboard-test.mp3"
        val tooltip = SoundboardSlotVisuals.tooltipTextForUri(uri)
        assertEquals(File(URI(uri)).absolutePath, tooltip)
    }

    @Test
    fun `tooltipTextForUri falls back to uri when not file uri`() {
        val uri = "http://example.com/audio.mp3"
        assertEquals(uri, SoundboardSlotVisuals.tooltipTextForUri(uri))
    }

    @Test
    fun `tooltipTextForUri returns empty slot text for null`() {
        assertEquals(SoundboardSlotVisuals.EMPTY_SLOT_TOOLTIP, SoundboardSlotVisuals.tooltipTextForUri(null))
    }

    @Test
    fun `snapshot uses stop label when playing`() {
        val controller = controllerWithStatus(MediaTrackStatus.PLAYING)
        val slot = SoundboardSlotState(customLabel = "Slot 2", uri = "file:///slot-2.mp3").apply {
            this.controller = controller
        }

        val snapshot = SoundboardSlotVisuals.snapshotOf(slot, index = 1)

        assertEquals("⏹ Slot 2", snapshot.buttonText)
        assertEquals(SoundboardSlotPlaybackPhase.PLAYING, snapshot.playbackPhase)
    }

    @Test
    fun `snapshot uses idle label when not playing`() {
        val controller = controllerWithStatus(MediaTrackStatus.STOPPED)
        val slot = SoundboardSlotState(customLabel = "Slot 2", uri = "file:///slot-2.mp3").apply {
            this.controller = controller
        }

        val snapshot = SoundboardSlotVisuals.snapshotOf(slot, index = 1)

        assertEquals("Slot 2", snapshot.buttonText)
        assertEquals(SoundboardSlotPlaybackPhase.STOPPED, snapshot.playbackPhase)
    }

    private fun controllerWithStatus(status: MediaTrackStatus): MediaTrackController {
        val player = FakeManagedMediaPlayer().apply {
            currentStatus = status
        }
        return MediaTrackController(playerLoader = { player }).apply {
            load("file:///slot-2.mp3", volume = 1.0, cycleCount = 1)
        }
    }

    private class FakeManagedMediaPlayer : ManagedMediaPlayer {
        override var volume: Double = 1.0
        override var cycleCount: Int = 1
        override var currentStatus: MediaTrackStatus = MediaTrackStatus.UNKNOWN
        override var currentTime: Duration = Duration.ZERO
        override var duration: Duration = Duration.seconds(10.0)

        override fun addCurrentTimeListener(listener: ChangeListener<Duration>) = Unit

        override fun removeCurrentTimeListener(listener: ChangeListener<Duration>) = Unit

        override fun setOnReady(handler: (() -> Unit)?) = Unit

        override fun setOnEndOfMedia(handler: (() -> Unit)?) = Unit

        override fun setOnError(handler: (() -> Unit)?) = Unit

        override fun play() = Unit

        override fun pause() = Unit

        override fun stop() = Unit

        override fun dispose() = Unit
    }
}
