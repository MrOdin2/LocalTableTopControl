package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.light.LightEffect

internal class AdvancedLightController {
    private var segments: List<AdvancedLightSegmentState> = emptyList()
    private var trackerTurnCuesEnabled: Boolean = false

    fun currentSegments(): List<AdvancedLightSegmentState> = segments

    fun trackerTurnCuesEnabled(): Boolean = trackerTurnCuesEnabled

    fun setTrackerTurnCuesEnabled(enabled: Boolean) {
        trackerTurnCuesEnabled = enabled
    }

    fun loadFromDevice(
        snapshot: WledDeviceSnapshot,
        preferences: AdvancedLightPreferences,
    ) {
        trackerTurnCuesEnabled = preferences.trackerTurnCuesEnabled
        val byId = snapshot.segments.associateBy { it.id }
        val orderedIds = preferences.order.filter { it in byId } +
            snapshot.segments.map { it.id }.filterNot { it in preferences.order }

        segments = orderedIds.mapNotNull { id ->
            byId[id]?.let { mergeDeviceSegment(it, preferences.segments[id]) }
        }
    }

    fun renameSegment(id: Int, name: String) {
        val cleanName = name.trim().ifBlank { id.toString() }
        replaceSegment(id) { it.copy(name = cleanName) }
    }

    fun moveSegment(id: Int, delta: Int): Boolean {
        val currentIndex = segments.indexOfFirst { it.id == id }
        if (currentIndex < 0) return false
        val targetIndex = (currentIndex + delta).coerceIn(0, segments.lastIndex)
        if (currentIndex == targetIndex) return false

        segments = segments.toMutableList().also { list ->
            val row = list.removeAt(currentIndex)
            list.add(targetIndex, row)
        }
        return true
    }

    fun setSelectedForEdit(id: Int, selected: Boolean) {
        replaceSegment(id) { it.copy(selectedForEdit = selected) }
    }

    fun setSegmentPower(id: Int, on: Boolean): List<AdvancedLightSegmentCommand> =
        replaceSegmentAndCommand(id) { it.copy(on = on) }

    fun setSegmentBrightnessScale(id: Int, scale: Double): List<AdvancedLightSegmentCommand> =
        replaceSegmentAndCommand(id) { it.copy(brightnessScale = scale.coerceIn(0.0, 1.0)) }

    fun assignTokens(id: Int, tokenIds: Set<String>) {
        val normalizedIds = tokenIds.map(String::trim).filter(String::isNotBlank).toSet()
        replaceSegment(id) { it.copy(assignedTokenIds = normalizedIds) }
    }

    fun unassignTokens(id: Int, tokenIds: Set<String>) {
        replaceSegment(id) { segment ->
            segment.copy(assignedTokenIds = segment.assignedTokenIds - tokenIds)
        }
    }

    fun configureTurnCue(id: Int, turnCue: AdvancedLightTurnCue) {
        val normalizedCue = turnCue.copy(
            color = turnCue.color.takeIf(AdvancedLightJson::isValidHexColor)?.uppercase() ?: "#FFD37A",
            brightness = turnCue.brightness.coerceIn(0.0, 1.0),
            effectSpeed = turnCue.effectSpeed.coerceIn(0, 255),
            effectIntensity = turnCue.effectIntensity.coerceIn(0, 255),
            durationMillis = turnCue.durationMillis.coerceIn(
                AdvancedLightTurnCue.MIN_DURATION_MILLIS,
                AdvancedLightTurnCue.MAX_DURATION_MILLIS,
            ),
        )
        replaceSegment(id) { it.copy(turnCue = normalizedCue) }
    }

    fun applyColorToSelected(hex: String): List<AdvancedLightSegmentCommand> {
        if (!AdvancedLightJson.isValidHexColor(hex)) return emptyList()
        return replaceSelectedAndCommand { it.copy(color = hex.uppercase()) }
    }

    fun applyBrightnessToSelected(brightness: Double): List<AdvancedLightSegmentCommand> =
        replaceSelectedAndCommand { it.copy(brightness = brightness.coerceIn(0.0, 1.0)) }

    fun applyEffectToSelected(effect: LightEffect): List<AdvancedLightSegmentCommand> =
        replaceSelectedAndCommand { it.copy(effect = effect) }

    fun applyEffectSpeedToSelected(speed: Int): List<AdvancedLightSegmentCommand> =
        replaceSelectedAndCommand { it.copy(effectSpeed = speed.coerceIn(0, 255)) }

    fun applyEffectIntensityToSelected(intensity: Int): List<AdvancedLightSegmentCommand> =
        replaceSelectedAndCommand { it.copy(effectIntensity = intensity.coerceIn(0, 255)) }

    fun commandsForAllSegments(): List<AdvancedLightSegmentCommand> =
        segments.map { it.toCommand() }

    fun commandsForSegments(ids: Set<Int>): List<AdvancedLightSegmentCommand> =
        segments.filter { it.id in ids }.map { it.toCommand() }

    fun turnCueSegmentsForToken(tokenId: String): List<AdvancedLightSegmentState> =
        segments.filter { tokenId in it.assignedTokenIds }

    fun applyControl(
        ids: Set<Int>,
        power: Boolean?,
        color: String?,
        effect: LightEffect?,
        brightness: Double?,
        effectSpeed: Int?,
        effectIntensity: Int?,
    ): List<AdvancedLightSegmentCommand> {
        val targetAll = ids.isEmpty()
        val commands = mutableListOf<AdvancedLightSegmentCommand>()
        segments = segments.map { segment ->
            if (!targetAll && segment.id !in ids) return@map segment

            segment.copy(
                on = power ?: segment.on,
                color = color?.takeIf(AdvancedLightJson::isValidHexColor)?.uppercase() ?: segment.color,
                effect = effect ?: segment.effect,
                brightness = brightness?.coerceIn(0.0, 1.0) ?: segment.brightness,
                effectSpeed = effectSpeed?.coerceIn(0, 255) ?: segment.effectSpeed,
                effectIntensity = effectIntensity?.coerceIn(0, 255) ?: segment.effectIntensity,
            ).also { commands += it.toCommand() }
        }
        return commands
    }

    fun coversAllKnownSegments(commands: List<AdvancedLightSegmentCommand>): Boolean {
        if (commands.size <= 1 || segments.isEmpty()) return false
        return commands.map { it.id }.toSet() == segments.map { it.id }.toSet()
    }

    fun selectedSegments(): List<AdvancedLightSegmentState> =
        segments.filter { it.selectedForEdit }

    fun preferencesSnapshot(): AdvancedLightPreferences =
        AdvancedLightPreferences(
            trackerTurnCuesEnabled = trackerTurnCuesEnabled,
            order = segments.map { it.id },
            segments = segments.associate { segment ->
                segment.id to AdvancedLightSegmentPreference(
                    name = segment.name,
                    color = segment.color,
                    effect = segment.effect,
                    brightness = segment.brightness,
                    brightnessScale = segment.brightnessScale,
                    effectSpeed = segment.effectSpeed,
                    effectIntensity = segment.effectIntensity,
                    assignedTokenIds = segment.assignedTokenIds,
                    turnCue = segment.turnCue,
                )
            },
        )

    private fun replaceSegment(
        id: Int,
        transform: (AdvancedLightSegmentState) -> AdvancedLightSegmentState,
    ) {
        segments = segments.map { segment ->
            if (segment.id == id) transform(segment) else segment
        }
    }

    private fun replaceSegmentAndCommand(
        id: Int,
        transform: (AdvancedLightSegmentState) -> AdvancedLightSegmentState,
    ): List<AdvancedLightSegmentCommand> {
        var changed: AdvancedLightSegmentState? = null
        segments = segments.map { segment ->
            if (segment.id == id) {
                transform(segment).also { changed = it }
            } else {
                segment
            }
        }
        return changed?.let { listOf(it.toCommand()) }.orEmpty()
    }

    private fun replaceSelectedAndCommand(
        transform: (AdvancedLightSegmentState) -> AdvancedLightSegmentState,
    ): List<AdvancedLightSegmentCommand> {
        val changed = mutableListOf<AdvancedLightSegmentCommand>()
        segments = segments.map { segment ->
            if (segment.selectedForEdit) {
                transform(segment).also { changed += it.toCommand() }
            } else {
                segment
            }
        }
        return changed
    }
}
