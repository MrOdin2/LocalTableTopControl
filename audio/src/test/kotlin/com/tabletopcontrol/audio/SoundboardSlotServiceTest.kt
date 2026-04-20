package com.tabletopcontrol.audio

import com.tabletopcontrol.audio.shared.ManagedMediaPlayer
import com.tabletopcontrol.audio.shared.MediaTrackController
import com.tabletopcontrol.audio.shared.MediaTrackStatus
import javafx.beans.value.ChangeListener
import javafx.util.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SoundboardSlotServiceTest {

    @Test
    fun `restore clears invalid persisted uri when no controller can be created`() {
        val settingsStore = FakeSettingsStore(
            loadResult = SoundboardSettingsLoadResult.Loaded(
                listOf(SoundboardSlotConfig(label = "Broken", uri = "file:///broken.mp3")),
            ),
        )
        val listener = RecordingListener()
        val service = SoundboardSlotService(settingsStore, FakeControllerFactory()::createController).apply {
            this.listener = listener
        }

        service.initializeIfNeeded()
        val slot = service.slots.single()

        val result = service.restoreSlot(slot)

        val failure = assertInstanceOf(SoundboardSlotLoadResult.Failed::class.java, result)
        assertNull(slot.uri)
        assertNull(slot.controller)
        assertEquals(1, settingsStore.saved.size)
        assertNull(settingsStore.saved.last().single().uri)
        assertInstanceOf(SoundboardSlotLoadFailure.UnsupportedSource::class.java, failure.failure)
        assertInstanceOf(
            SoundboardSlotLoadResult.Failed::class.java,
            listener.loadResults.last().second,
        )
    }

    @Test
    fun `toggle playback returns explicit failure when no slot is active`() {
        val service = SoundboardSlotService(
            settingsStore = FakeSettingsStore(),
            controllerFactory = FakeControllerFactory()::createController,
        )

        service.initializeIfNeeded()
        val slot = service.slots.first()

        val result = service.togglePlayback(slot)

        val failure = assertInstanceOf(SoundboardSlotPlaybackResult.Failed::class.java, result)
        assertInstanceOf(SoundboardSlotPlaybackFailure.NoActiveTrack::class.java, failure.failure)
    }

    @Test
    fun `active player errors surface as playback failures through listener`() {
        val controllerFactory = FakeControllerFactory()
        val listener = RecordingListener()
        val service = SoundboardSlotService(FakeSettingsStore(), controllerFactory::createController).apply {
            this.listener = listener
        }

        service.initializeIfNeeded()
        val slot = service.slots.first()
        val player = FakeManagedMediaPlayer()
        controllerFactory.enqueue("file:///track.mp3", player)

        service.loadSelectedFile(slot, requestedLabel = "Track", requestedUri = "file:///track.mp3")
        player.fireError()

        val failure = listener.playbackFailures.last().second
        assertInstanceOf(SoundboardSlotPlaybackFailure.PlayerError::class.java, failure.failure)
    }

    @Test
    fun `save failures are forwarded through listener`() {
        val settingsStore = FakeSettingsStore(
            saveResultFactory = {
                SoundboardSettingsSaveResult.Failed(
                    SoundboardSettingsPersistenceFailure.WriteFailed(File("soundboard.conf")),
                )
            },
        )
        val listener = RecordingListener()
        val service = SoundboardSlotService(settingsStore, FakeControllerFactory()::createController).apply {
            this.listener = listener
        }

        service.initializeIfNeeded()

        assertTrue(service.addSlot())

        assertInstanceOf(
            SoundboardSettingsSaveResult.Failed::class.java,
            listener.persistenceResults.last(),
        )
    }

    private class FakeSettingsStore(
        private val loadResult: SoundboardSettingsLoadResult = SoundboardSettingsLoadResult.Missing,
        private val saveResultFactory: (List<SoundboardSlotConfig>) -> SoundboardSettingsSaveResult = { slots ->
            SoundboardSettingsSaveResult.Saved(slots.size, File("soundboard.conf"))
        },
    ) : SoundboardSettingsStore {
        val saved = mutableListOf<List<SoundboardSlotConfig>>()

        override fun load(): SoundboardSettingsLoadResult = loadResult

        override fun save(slots: List<SoundboardSlotConfig>): SoundboardSettingsSaveResult {
            saved += slots
            return saveResultFactory(slots)
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

    private class RecordingListener : SoundboardSlotService.Listener {
        val snapshots = mutableListOf<Pair<SoundboardSlotState, SoundboardSlotSnapshot>>()
        val loadResults = mutableListOf<Pair<SoundboardSlotState, SoundboardSlotLoadResult>>()
        val playbackFailures = mutableListOf<Pair<SoundboardSlotState, SoundboardSlotPlaybackResult.Failed>>()
        val persistenceResults = mutableListOf<SoundboardSettingsSaveResult>()

        override fun onSlotSnapshotChanged(slot: SoundboardSlotState, snapshot: SoundboardSlotSnapshot) {
            snapshots += slot to snapshot
        }

        override fun onSlotLoadResult(slot: SoundboardSlotState, result: SoundboardSlotLoadResult) {
            loadResults += slot to result
        }

        override fun onSlotPlaybackFailure(
            slot: SoundboardSlotState,
            result: SoundboardSlotPlaybackResult.Failed,
        ) {
            playbackFailures += slot to result
        }

        override fun onConfigPersistenceResult(result: SoundboardSettingsSaveResult) {
            persistenceResults += result
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

        fun fireError() {
            currentStatus = MediaTrackStatus.HALTED
            errorHandler?.invoke()
        }
    }
}
