package com.tabletopcontrol.audio.shared

import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration

/**
 * Shared JavaFX media lifecycle controller used by audio plugins.
 *
 * Handles player creation/loading, lifecycle callbacks, progress listener
 * registration, and safe disposal in one place.
 */
internal class MediaTrackController(
    private val runOnFx: ((() -> Unit) -> Unit) = { task -> Platform.runLater { task() } },
    private val playerLoader: (String) -> ManagedMediaPlayer? = ::createJavaFxManagedPlayer,
) {
    private var player: ManagedMediaPlayer? = null
    private var timeListener: ChangeListener<Duration>? = null

    var onReady: (() -> Unit)? = null
    var onProgress: ((current: Duration, total: Duration) -> Unit)? = null
    var onError: (() -> Unit)? = null
    var onEndOfMedia: (() -> Unit)? = null

    fun bindCallbacks(
        onReady: (() -> Unit)? = this.onReady,
        onProgress: ((current: Duration, total: Duration) -> Unit)? = this.onProgress,
        onError: (() -> Unit)? = this.onError,
        onEndOfMedia: (() -> Unit)? = this.onEndOfMedia,
    ) {
        this.onReady = onReady
        this.onProgress = onProgress
        this.onError = onError
        this.onEndOfMedia = onEndOfMedia
        player?.let(::attachHandlers)
    }

    fun load(uri: String, volume: Double, cycleCount: Int): Boolean {
        dispose()
        val loaded = playerLoader(uri) ?: return false
        player = loaded
        loaded.volume = volume.coerceIn(0.0, 1.0)
        loaded.cycleCount = cycleCount
        attachHandlers(loaded)
        return true
    }

    fun hasPlayer(): Boolean = player != null

    fun status(): MediaTrackStatus? = player?.status

    fun isUsable(): Boolean {
        val currentStatus = status() ?: return false
        return currentStatus != MediaTrackStatus.UNKNOWN &&
            currentStatus != MediaTrackStatus.HALTED &&
            currentStatus != MediaTrackStatus.DISPOSED
    }

    fun currentTime(): Duration? = player?.currentTime

    fun duration(): Duration? = player?.duration

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun stop() {
        player?.stop()
    }

    fun setCycleCount(cycleCount: Int) {
        player?.cycleCount = cycleCount
    }

    fun setVolume(volume: Double) {
        player?.volume = volume.coerceIn(0.0, 1.0)
    }

    fun dispose() {
        val currentPlayer = player ?: return
        timeListener?.let(currentPlayer::removeCurrentTimeListener)
        timeListener = null
        currentPlayer.setOnReady(null)
        currentPlayer.setOnEndOfMedia(null)
        currentPlayer.setOnError(null)
        runCatching { currentPlayer.stop() }
        currentPlayer.dispose()
        player = null
    }

    private fun attachHandlers(currentPlayer: ManagedMediaPlayer) {
        timeListener?.let(currentPlayer::removeCurrentTimeListener)
        timeListener = null

        currentPlayer.setOnReady {
            runOnFx { onReady?.invoke() }
        }
        currentPlayer.setOnEndOfMedia {
            runOnFx { onEndOfMedia?.invoke() }
        }
        currentPlayer.setOnError {
            runOnFx { onError?.invoke() }
        }

        val listener = ChangeListener<Duration> { _, _, current ->
            val total = currentPlayer.duration
            if (total.isUnknown || total.isIndefinite) return@ChangeListener
            val totalSeconds = total.toSeconds()
            if (totalSeconds <= 0.0) return@ChangeListener
            runOnFx { onProgress?.invoke(current, total) }
        }
        timeListener = listener
        currentPlayer.addCurrentTimeListener(listener)
    }
}

internal enum class MediaTrackStatus {
    UNKNOWN,
    READY,
    PAUSED,
    PLAYING,
    STOPPED,
    STALLED,
    HALTED,
    DISPOSED,
}

internal interface ManagedMediaPlayer {
    var volume: Double
    var cycleCount: Int

    val status: MediaTrackStatus
    val currentTime: Duration
    val duration: Duration

    fun addCurrentTimeListener(listener: ChangeListener<Duration>)
    fun removeCurrentTimeListener(listener: ChangeListener<Duration>)

    fun setOnReady(handler: (() -> Unit)?)
    fun setOnEndOfMedia(handler: (() -> Unit)?)
    fun setOnError(handler: (() -> Unit)?)

    fun play()
    fun pause()
    fun stop()
    fun dispose()
}

private class JavaFxManagedMediaPlayer(
    private val media: Media,
    private val player: MediaPlayer,
) : ManagedMediaPlayer {
    override var volume: Double
        get() = player.volume
        set(value) {
            player.volume = value
        }

    override var cycleCount: Int
        get() = player.cycleCount
        set(value) {
            player.cycleCount = value
        }

    override val status: MediaTrackStatus
        get() = when (player.status) {
            MediaPlayer.Status.UNKNOWN -> MediaTrackStatus.UNKNOWN
            MediaPlayer.Status.READY -> MediaTrackStatus.READY
            MediaPlayer.Status.PAUSED -> MediaTrackStatus.PAUSED
            MediaPlayer.Status.PLAYING -> MediaTrackStatus.PLAYING
            MediaPlayer.Status.STOPPED -> MediaTrackStatus.STOPPED
            MediaPlayer.Status.STALLED -> MediaTrackStatus.STALLED
            MediaPlayer.Status.HALTED -> MediaTrackStatus.HALTED
            MediaPlayer.Status.DISPOSED -> MediaTrackStatus.DISPOSED
        }

    override val currentTime: Duration
        get() = player.currentTime

    override val duration: Duration
        get() = media.duration

    override fun addCurrentTimeListener(listener: ChangeListener<Duration>) {
        player.currentTimeProperty().addListener(listener)
    }

    override fun removeCurrentTimeListener(listener: ChangeListener<Duration>) {
        player.currentTimeProperty().removeListener(listener)
    }

    override fun setOnReady(handler: (() -> Unit)?) {
        player.setOnReady(handler)
    }

    override fun setOnEndOfMedia(handler: (() -> Unit)?) {
        player.setOnEndOfMedia(handler)
    }

    override fun setOnError(handler: (() -> Unit)?) {
        player.setOnError(handler)
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun stop() {
        player.stop()
    }

    override fun dispose() {
        player.dispose()
    }
}

private fun createJavaFxManagedPlayer(uri: String): ManagedMediaPlayer? {
    val media = try {
        Media(uri)
    } catch (_: Exception) {
        return null
    }
    val player = try {
        MediaPlayer(media)
    } catch (_: Exception) {
        return null
    }
    return JavaFxManagedMediaPlayer(media, player)
}
