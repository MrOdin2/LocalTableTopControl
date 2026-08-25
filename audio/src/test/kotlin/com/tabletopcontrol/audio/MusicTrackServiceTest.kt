package com.tabletopcontrol.audio

import com.tabletopcontrol.audio.shared.ManagedMediaPlayer
import com.tabletopcontrol.audio.shared.MediaTrackController
import com.tabletopcontrol.audio.shared.MediaTrackStatus
import javafx.beans.value.ChangeListener
import javafx.util.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MusicTrackServiceTest {

    @Test
    fun `selected track persists only after replacement controller becomes ready`() {
        val settingsStore = FakeSettingsStore()
        val controllerFactory = FakeControllerFactory()
        val listener = RecordingListener()
        val service = MusicTrackService(settingsStore, controllerFactory::createController).apply {
            this.listener = listener
        }

        service.initializeIfNeeded()
        val track = service.tracks.single()
        val originalPlayer = FakeManagedMediaPlayer()
        controllerFactory.enqueue("file:///original.mp3", originalPlayer)

        service.loadSelectedTrack(track, "file:///original.mp3")
        originalPlayer.fireReady()
        val activeController = track.controller

        val replacementPlayer = FakeManagedMediaPlayer()
        controllerFactory.enqueue("file:///replacement.mp3", replacementPlayer)

        val result = service.loadSelectedTrack(track, "file:///replacement.mp3")

        assertInstanceOf(MusicTrackLoadResult.Started::class.java, result)
        assertFalse(originalPlayer.disposeCalled)
        assertSame(activeController, track.controller)
        assertEquals("file:///original.mp3", track.uri)

        replacementPlayer.fireReady()

        assertTrue(originalPlayer.disposeCalled)
        assertEquals("file:///replacement.mp3", track.uri)
        assertEquals("file:///replacement.mp3", settingsStore.saved.last().tracks.single().uri)
        assertInstanceOf(
            MusicTrackLoadResult.Activated::class.java,
            listener.loadResults.last().second,
        )
    }

    @Test
    fun `failed replacement load restores previously active controller`() {
        val settingsStore = FakeSettingsStore()
        val controllerFactory = FakeControllerFactory()
        val listener = RecordingListener()
        val service = MusicTrackService(settingsStore, controllerFactory::createController).apply {
            this.listener = listener
        }

        service.initializeIfNeeded()
        val track = service.tracks.single()
        val originalPlayer = FakeManagedMediaPlayer()
        controllerFactory.enqueue("file:///original.mp3", originalPlayer)
        service.loadSelectedTrack(track, "file:///original.mp3")
        originalPlayer.fireReady()
        val activeController = track.controller

        val failingPlayer = FakeManagedMediaPlayer()
        controllerFactory.enqueue("file:///replacement.mp3", failingPlayer)
        service.loadSelectedTrack(track, "file:///replacement.mp3")

        failingPlayer.fireError()

        assertSame(activeController, track.controller)
        assertEquals("file:///original.mp3", track.uri)
        assertFalse(originalPlayer.disposeCalled)
        assertTrue(failingPlayer.disposeCalled)
        assertEquals(1, settingsStore.saved.size)

        val failure = assertInstanceOf(
            MusicTrackLoadResult.Failed::class.java,
            listener.loadResults.last().second,
        )
        assertTrue(failure.restoredPreviousTrack)
        assertInstanceOf(MusicTrackLoadFailure.PlayerError::class.java, failure.failure)
    }

    @Test
    fun `restore clears invalid persisted uri when no controller can be created`() {
        val settingsStore = FakeSettingsStore(
            loaded = MusicSettings(
                tracks = listOf(
                    PersistedMusicTrack(uri = "file:///broken.mp3"),
                ),
            ),
        )
        val controllerFactory = FakeControllerFactory()
        val listener = RecordingListener()
        val service = MusicTrackService(settingsStore, controllerFactory::createController).apply {
            this.listener = listener
        }

        service.initializeIfNeeded()
        val track = service.tracks.single()

        val result = service.restoreTrack(track)

        val failure = assertInstanceOf(MusicTrackLoadResult.Failed::class.java, result)
        assertNull(track.uri)
        assertNull(track.controller)
        assertEquals(1, settingsStore.saved.size)
        assertNull(settingsStore.saved.last().tracks.single().uri)
        assertInstanceOf(MusicTrackLoadFailure.UnsupportedSource::class.java, failure.failure)
        assertInstanceOf(
            MusicTrackLoadResult.Failed::class.java,
            listener.loadResults.last().second,
        )
    }

    @Test
    fun `toggle playback returns explicit failure when no track is active`() {
        val service = MusicTrackService(
            settingsStore = FakeSettingsStore(),
            controllerFactory = FakeControllerFactory()::createController,
        )

        service.initializeIfNeeded()
        val track = service.tracks.single()

        val result = service.togglePlayback(track)

        val failure = assertInstanceOf(MusicTrackPlaybackResult.Failed::class.java, result)
        assertInstanceOf(MusicTrackPlaybackFailure.NoActiveTrack::class.java, failure.failure)
    }

    @Test
    fun `active player errors surface as playback failures through the listener`() {
        val controllerFactory = FakeControllerFactory()
        val listener = RecordingListener()
        val service = MusicTrackService(FakeSettingsStore(), controllerFactory::createController).apply {
            this.listener = listener
        }

        service.initializeIfNeeded()
        val track = service.tracks.single()
        val player = FakeManagedMediaPlayer()
        controllerFactory.enqueue("file:///track.mp3", player)

        service.loadSelectedTrack(track, "file:///track.mp3")
        player.fireReady()
        player.fireError()

        val failure = listener.playbackFailures.last().second
        assertInstanceOf(MusicTrackPlaybackFailure.PlayerError::class.java, failure.failure)
    }

    @Test
    fun `play uri loads and starts a track when it becomes ready`() {
        val controllerFactory = FakeControllerFactory()
        val service = MusicTrackService(FakeSettingsStore(), controllerFactory::createController)
        val player = FakeManagedMediaPlayer()
        controllerFactory.enqueue("file:///battle.mp3", player)

        assertTrue(service.playUri("file:///battle.mp3"))
        assertEquals(MediaTrackStatus.UNKNOWN, player.currentStatus)

        player.fireReady()

        assertEquals(MediaTrackStatus.PLAYING, player.currentStatus)
        assertEquals("file:///battle.mp3", service.tracks.single().uri)
    }

    private class FakeSettingsStore(
        private val loaded: MusicSettings = MusicSettings(),
    ) : MusicTrackSettingsStore {
        val saved = mutableListOf<MusicSettings>()

        override fun load(): MusicSettings = loaded

        override fun save(settings: MusicSettings) {
            saved += settings
        }
    }

    private class FakeControllerFactory {
        private val queuedPlayers = mutableMapOf<String, ArrayDeque<FakeManagedMediaPlayer>>()

        fun enqueue(uri: String, player: FakeManagedMediaPlayer) {
            queuedPlayers.getOrPut(uri) { ArrayDeque() }.addLast(player)
        }

        fun createController(): MediaTrackController = MediaTrackController(
            playerLoader = { uri ->
                queuedPlayers[uri]
                    ?.takeIf { it.isNotEmpty() }
                    ?.removeFirst()
            },
        )
    }

    private class RecordingListener : MusicTrackService.Listener {
        val snapshots = mutableListOf<Pair<MusicTrackState, MusicTrackSnapshot>>()
        val loadResults = mutableListOf<Pair<MusicTrackState, MusicTrackLoadResult>>()
        val playbackFailures = mutableListOf<Pair<MusicTrackState, MusicTrackPlaybackResult.Failed>>()

        override fun onTrackSnapshotChanged(track: MusicTrackState, snapshot: MusicTrackSnapshot) {
            snapshots += track to snapshot
        }

        override fun onTrackLoadResult(track: MusicTrackState, result: MusicTrackLoadResult) {
            loadResults += track to result
        }

        override fun onTrackPlaybackFailure(track: MusicTrackState, result: MusicTrackPlaybackResult.Failed) {
            playbackFailures += track to result
        }
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

        fun fireReady() {
            currentStatus = MediaTrackStatus.READY
            readyHandler?.invoke()
        }

        fun fireError() {
            currentStatus = MediaTrackStatus.HALTED
            errorHandler?.invoke()
        }
    }
}
