package com.tabletopcontrol.audio

import com.tabletopcontrol.audio.shared.MediaTrackController
import com.tabletopcontrol.audio.shared.MediaTrackStatus

internal class SoundboardSlotState internal constructor(
    var customLabel: String? = null,
    var uri: String? = null,
    var colorHex: String? = null,
) {
    internal var controller: MediaTrackController? = null
}

internal data class SoundboardSlotSnapshot(
    val displayLabel: String,
    val buttonText: String,
    val tooltipText: String,
    val playbackPhase: SoundboardSlotPlaybackPhase,
    val styleState: SoundboardSlotStyleState,
    val uri: String?,
)

internal enum class SoundboardSlotPlaybackPhase {
    EMPTY,
    READY,
    PLAYING,
    STOPPED,
    UNAVAILABLE,
}

internal sealed interface SoundboardSlotStyleState {
    data object IdleDefault : SoundboardSlotStyleState

    data class IdleColored(val colorHex: String) : SoundboardSlotStyleState

    data object PlayingDefault : SoundboardSlotStyleState

    data class PlayingColored(val colorHex: String) : SoundboardSlotStyleState
}

internal sealed interface SoundboardSlotLoadResult {
    val requestedUri: String
    val requestedLabel: String?

    data class Loaded(
        override val requestedUri: String,
        override val requestedLabel: String?,
        val snapshot: SoundboardSlotSnapshot,
    ) : SoundboardSlotLoadResult

    data class Failed(
        override val requestedUri: String,
        override val requestedLabel: String?,
        val failure: SoundboardSlotLoadFailure,
        val snapshot: SoundboardSlotSnapshot,
        val clearedAssignedSlot: Boolean,
    ) : SoundboardSlotLoadResult
}

internal sealed interface SoundboardSlotLoadFailure {
    val uri: String

    data class UnsupportedSource(override val uri: String) : SoundboardSlotLoadFailure
}

internal sealed interface SoundboardSlotPlaybackResult {
    data class Success(
        val action: SoundboardSlotPlaybackAction,
        val snapshot: SoundboardSlotSnapshot,
    ) : SoundboardSlotPlaybackResult

    data class Failed(
        val failure: SoundboardSlotPlaybackFailure,
        val snapshot: SoundboardSlotSnapshot,
    ) : SoundboardSlotPlaybackResult
}

internal enum class SoundboardSlotPlaybackAction {
    PLAY,
    STOP,
}

internal sealed interface SoundboardSlotPlaybackFailure {
    data class NoActiveTrack(val uri: String?) : SoundboardSlotPlaybackFailure

    data class Unavailable(
        val uri: String?,
        val status: MediaTrackStatus?,
    ) : SoundboardSlotPlaybackFailure

    data class PlayerError(val uri: String?) : SoundboardSlotPlaybackFailure
}
