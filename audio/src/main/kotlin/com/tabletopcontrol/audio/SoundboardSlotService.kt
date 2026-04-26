package com.tabletopcontrol.audio

import com.tabletopcontrol.audio.shared.MediaTrackController
import com.tabletopcontrol.audio.shared.MediaTrackStatus
import com.tabletopcontrol.core.ui.reorder.ReorderSupport

/**
 * Owns soundboard slot state, media lifecycle wiring, and config persistence.
 *
 * The UI plugin stays focused on creating buttons and forwarding user actions,
 * while this service manages the slot state machine and explicit result flows.
 */
internal class SoundboardSlotService(
    private val settingsStore: SoundboardSettingsStore = SerializerSoundboardSettingsStore,
    private val controllerFactory: () -> MediaTrackController = ::MediaTrackController,
) {
    interface Listener {
        fun onSlotSnapshotChanged(slot: SoundboardSlotState, snapshot: SoundboardSlotSnapshot) {}

        fun onSlotLoadResult(slot: SoundboardSlotState, result: SoundboardSlotLoadResult) {}

        fun onSlotPlaybackFailure(slot: SoundboardSlotState, result: SoundboardSlotPlaybackResult.Failed) {}

        fun onConfigPersistenceResult(result: SoundboardSettingsSaveResult) {}
    }

    var listener: Listener? = null

    private val slotStates = mutableListOf<SoundboardSlotState>()
    val slots: List<SoundboardSlotState> get() = slotStates

    private var initialized = false

    fun initializeIfNeeded(): SoundboardSettingsLoadResult {
        if (initialized) {
            return SoundboardSettingsLoadResult.Loaded(slotStates.map(::toConfig))
        }

        val loadResult = settingsStore.load()
        val initialSlots = when (loadResult) {
            is SoundboardSettingsLoadResult.Loaded -> loadResult.slots
            SoundboardSettingsLoadResult.Missing -> List(DEFAULT_SOUNDBOARD_BUTTON_COUNT) { SoundboardSlotConfig() }
            is SoundboardSettingsLoadResult.Failed -> List(DEFAULT_SOUNDBOARD_BUTTON_COUNT) { SoundboardSlotConfig() }
        }

        slotStates.clear()
        slotStates += initialSlots.map { config ->
            SoundboardSlotState(
                customLabel = config.label,
                uri = config.uri,
                colorHex = config.colorHex,
            )
        }
        initialized = true
        return loadResult
    }

    fun snapshotOf(slot: SoundboardSlotState): SoundboardSlotSnapshot {
        val index = indexOfSlot(slot) ?: 0
        return SoundboardSlotVisuals.snapshotOf(slot, index)
    }

    fun restoreSlot(slot: SoundboardSlotState): SoundboardSlotLoadResult? {
        val assignedUri = slot.uri ?: return null
        if (slot.controller?.hasPlayer() == true) return null
        return loadSlot(
            slot = slot,
            requestedLabel = slot.customLabel,
            requestedUri = assignedUri,
            persistOnSuccess = false,
            persistOnFailure = true,
        )
    }

    fun loadSelectedFile(
        slot: SoundboardSlotState,
        requestedLabel: String,
        requestedUri: String,
    ): SoundboardSlotLoadResult = loadSlot(
        slot = slot,
        requestedLabel = requestedLabel,
        requestedUri = requestedUri,
        persistOnSuccess = true,
        persistOnFailure = true,
    )

    fun togglePlayback(slot: SoundboardSlotState): SoundboardSlotPlaybackResult {
        val controller = slot.controller ?: return playbackFailure(
            slot = slot,
            failure = SoundboardSlotPlaybackFailure.NoActiveTrack(slot.uri),
        )

        return when (controller.status()) {
            MediaTrackStatus.PLAYING -> {
                controller.stop()
                playbackSuccess(slot, SoundboardSlotPlaybackAction.STOP)
            }

            else -> {
                if (!controller.isUsable()) {
                    return playbackFailure(
                        slot = slot,
                        failure = SoundboardSlotPlaybackFailure.Unavailable(slot.uri, controller.status()),
                    )
                }

                controller.play()
                playbackSuccess(slot, SoundboardSlotPlaybackAction.PLAY)
            }
        }
    }

    fun clearSlot(slot: SoundboardSlotState) {
        slot.controller?.dispose()
        slot.controller = null
        slot.uri = null
        slot.customLabel = null
        emitSnapshotChanged(slot)
        emitConfigPersistenceResult(persistSettings())
    }

    fun setSlotColor(slot: SoundboardSlotState, colorHex: String?) {
        slot.colorHex = colorHex?.takeIf { it.isNotBlank() }
        emitSnapshotChanged(slot)
        emitConfigPersistenceResult(persistSettings())
    }

    fun addSlot(): Boolean {
        if (slotStates.size >= MAX_SOUNDBOARD_BUTTON_COUNT) return false
        slotStates += SoundboardSlotState()
        emitConfigPersistenceResult(persistSettings())
        return true
    }

    fun removeSlot(slot: SoundboardSlotState): Boolean {
        val index = indexOfSlot(slot) ?: return false
        disposeSlot(slotStates.removeAt(index))
        emitConfigPersistenceResult(persistSettings())
        return true
    }

    fun reorderSlots(fromIndex: Int, toIndex: Int): Boolean {
        if (!ReorderSupport.reorderMutableListFromDrop(slotStates, fromIndex, toIndex)) return false
        emitConfigPersistenceResult(persistSettings())
        return true
    }

    fun shutdown() {
        slotStates.forEach(::disposeSlot)
    }

    fun exportSlots(): List<SoundboardSlotConfig> {
        initializeIfNeeded()
        return slotStates.map(::toConfig)
    }

    fun replaceSlots(slots: List<SoundboardSlotConfig>) {
        initializeIfNeeded()
        slotStates.forEach(::disposeSlot)
        slotStates.clear()
        slotStates += slots
            .take(MAX_SOUNDBOARD_BUTTON_COUNT)
            .map { config ->
                SoundboardSlotState(
                    customLabel = config.label,
                    uri = config.uri,
                    colorHex = config.colorHex,
                )
            }
        persistSettings()
    }

    private fun loadSlot(
        slot: SoundboardSlotState,
        requestedLabel: String?,
        requestedUri: String,
        persistOnSuccess: Boolean,
        persistOnFailure: Boolean,
    ): SoundboardSlotLoadResult {
        slot.customLabel = requestedLabel
        slot.uri = requestedUri

        val controller = slot.controller ?: controllerFactory().also { slot.controller = it }
        bindActiveController(slot, controller)

        val loaded = controller.load(
            uri = requestedUri,
            volume = 1.0,
            cycleCount = 1,
        )

        if (!loaded) {
            controller.dispose()
            slot.controller = null
            slot.uri = null
            slot.customLabel = null

            val snapshot = snapshotOf(slot)
            emitSnapshotChanged(slot, snapshot)
            if (persistOnFailure) {
                emitConfigPersistenceResult(persistSettings())
            }

            val result = SoundboardSlotLoadResult.Failed(
                requestedUri = requestedUri,
                requestedLabel = requestedLabel,
                failure = SoundboardSlotLoadFailure.UnsupportedSource(requestedUri),
                snapshot = snapshot,
                clearedAssignedSlot = true,
            )
            listener?.onSlotLoadResult(slot, result)
            return result
        }

        val snapshot = snapshotOf(slot)
        emitSnapshotChanged(slot, snapshot)
        if (persistOnSuccess) {
            emitConfigPersistenceResult(persistSettings())
        }

        val result = SoundboardSlotLoadResult.Loaded(
            requestedUri = requestedUri,
            requestedLabel = requestedLabel,
            snapshot = snapshot,
        )
        listener?.onSlotLoadResult(slot, result)
        return result
    }

    private fun bindActiveController(slot: SoundboardSlotState, controller: MediaTrackController) {
        controller.bindCallbacks(
            onError = {
                if (slot.controller !== controller) return@bindCallbacks
                val result = playbackFailure(
                    slot = slot,
                    failure = SoundboardSlotPlaybackFailure.PlayerError(slot.uri),
                    notifyListener = false,
                )
                emitSnapshotChanged(slot, result.snapshot)
                listener?.onSlotPlaybackFailure(slot, result)
            },
            onEndOfMedia = {
                if (slot.controller !== controller) return@bindCallbacks
                controller.stop()
                emitSnapshotChanged(slot)
            },
        )
    }

    private fun playbackSuccess(
        slot: SoundboardSlotState,
        action: SoundboardSlotPlaybackAction,
    ): SoundboardSlotPlaybackResult.Success {
        val result = SoundboardSlotPlaybackResult.Success(
            action = action,
            snapshot = snapshotOf(slot),
        )
        emitSnapshotChanged(slot, result.snapshot)
        return result
    }

    private fun playbackFailure(
        slot: SoundboardSlotState,
        failure: SoundboardSlotPlaybackFailure,
        notifyListener: Boolean = true,
    ): SoundboardSlotPlaybackResult.Failed {
        val result = SoundboardSlotPlaybackResult.Failed(
            failure = failure,
            snapshot = snapshotOf(slot),
        )
        if (notifyListener) {
            listener?.onSlotPlaybackFailure(slot, result)
        }
        return result
    }

    private fun emitSnapshotChanged(
        slot: SoundboardSlotState,
        snapshot: SoundboardSlotSnapshot = snapshotOf(slot),
    ) {
        listener?.onSlotSnapshotChanged(slot, snapshot)
    }

    private fun emitConfigPersistenceResult(result: SoundboardSettingsSaveResult) {
        listener?.onConfigPersistenceResult(result)
    }

    private fun persistSettings(): SoundboardSettingsSaveResult = settingsStore.save(slotStates.map(::toConfig))

    private fun toConfig(slot: SoundboardSlotState): SoundboardSlotConfig =
        SoundboardSlotConfig(
            label = slot.customLabel,
            uri = slot.uri,
            colorHex = slot.colorHex,
        )

    private fun indexOfSlot(slot: SoundboardSlotState): Int? = slotStates.indexOf(slot).takeIf { it >= 0 }

    private fun disposeSlot(slot: SoundboardSlotState) {
        slot.controller?.dispose()
        slot.controller = null
    }
}
