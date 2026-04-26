package com.tabletopcontrol.audio

import com.tabletopcontrol.audio.shared.MediaTrackStatus
import com.tabletopcontrol.core.persistence.LocalFiles
import com.tabletopcontrol.core.ui.color.ColorContrast

internal object SoundboardSlotVisuals {
    const val EMPTY_SLOT_TOOLTIP: String = "Right-click to load a sound file"

    fun snapshotOf(slot: SoundboardSlotState, index: Int): SoundboardSlotSnapshot {
        val displayLabel = displayLabel(slot, index)
        val playbackPhase = playbackPhaseOf(slot)
        return SoundboardSlotSnapshot(
            displayLabel = displayLabel,
            buttonText = buttonTextFor(playbackPhase, displayLabel),
            tooltipText = tooltipTextForUri(slot.uri),
            playbackPhase = playbackPhase,
            styleState = styleStateFor(slot.colorHex, playbackPhase),
            uri = slot.uri,
        )
    }

    fun displayLabel(slot: SoundboardSlotState, index: Int): String = slot.customLabel ?: "Slot ${index + 1}"

    fun tooltipTextForUri(uri: String?): String {
        if (uri.isNullOrBlank()) return EMPTY_SLOT_TOOLTIP
        return LocalFiles.absolutePath(uri) ?: uri
    }

    fun cssFor(styleState: SoundboardSlotStyleState): String = when (styleState) {
        SoundboardSlotStyleState.IdleDefault -> ""
        is SoundboardSlotStyleState.IdleColored ->
            "-fx-background-color: ${styleState.colorHex}; " +
                "-fx-text-fill: ${ColorContrast.textColorHexForBackgroundHex(styleState.colorHex)};"

        SoundboardSlotStyleState.PlayingDefault ->
            "-fx-base: -tc-accent; -fx-text-fill: -tc-on-accent;"

        is SoundboardSlotStyleState.PlayingColored ->
            "-fx-background-color: ${styleState.colorHex}; " +
                "-fx-text-fill: ${ColorContrast.textColorHexForBackgroundHex(styleState.colorHex)}; " +
                "-fx-border-color: -tc-accent; -fx-border-width: 2;"
    }

    private fun buttonTextFor(
        playbackPhase: SoundboardSlotPlaybackPhase,
        displayLabel: String,
    ): String = if (playbackPhase == SoundboardSlotPlaybackPhase.PLAYING) {
        "⏹ $displayLabel"
    } else {
        displayLabel
    }

    private fun playbackPhaseOf(slot: SoundboardSlotState): SoundboardSlotPlaybackPhase {
        val assignedUri = slot.uri ?: return SoundboardSlotPlaybackPhase.EMPTY
        val controller = slot.controller ?: return SoundboardSlotPlaybackPhase.UNAVAILABLE

        return when (controller.status()) {
            MediaTrackStatus.PLAYING -> SoundboardSlotPlaybackPhase.PLAYING
            MediaTrackStatus.READY -> SoundboardSlotPlaybackPhase.READY
            MediaTrackStatus.PAUSED, MediaTrackStatus.STOPPED -> SoundboardSlotPlaybackPhase.STOPPED
            else -> if (assignedUri.isBlank()) {
                SoundboardSlotPlaybackPhase.EMPTY
            } else {
                SoundboardSlotPlaybackPhase.UNAVAILABLE
            }
        }
    }

    private fun styleStateFor(
        colorHex: String?,
        playbackPhase: SoundboardSlotPlaybackPhase,
    ): SoundboardSlotStyleState {
        val resolvedColor = colorHex?.takeIf { it.isNotBlank() }
        return when {
            playbackPhase == SoundboardSlotPlaybackPhase.PLAYING && resolvedColor == null ->
                SoundboardSlotStyleState.PlayingDefault

            playbackPhase == SoundboardSlotPlaybackPhase.PLAYING ->
                SoundboardSlotStyleState.PlayingColored(requireNotNull(resolvedColor))

            resolvedColor == null ->
                SoundboardSlotStyleState.IdleDefault

            else ->
                SoundboardSlotStyleState.IdleColored(resolvedColor)
        }
    }
}
