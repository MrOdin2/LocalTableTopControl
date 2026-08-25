package com.tabletopcontrol.hotkey

import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer

/** Self-contained sound-effect player; no Soundboard plugin instance is required. */
internal class HotkeySoundPlayer {
    private val activePlayers = mutableSetOf<MediaPlayer>()

    fun play(uri: String, volume: Double) {
        val player = runCatching { MediaPlayer(Media(uri)) }.getOrElse {
            System.err.println("Hotkey sound could not be loaded: $uri")
            return
        }
        activePlayers += player
        player.volume = volume.coerceIn(0.0, 1.0)

        fun release() {
            activePlayers.remove(player)
            player.dispose()
        }
        player.setOnEndOfMedia(::release)
        player.setOnError {
            System.err.println("Hotkey sound playback failed: $uri")
            release()
        }
        player.play()
    }

    fun shutdown() {
        activePlayers.toList().forEach(MediaPlayer::dispose)
        activePlayers.clear()
    }
}
