package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.light.LightEffect

internal class AdvancedLightController {
    private var segments: List<AdvancedLightSegmentState> = emptyList()

    fun currentSegments(): List<AdvancedLightSegmentState> = segments

    fun loadFromDevice(
        snapshot: WledDeviceSnapshot,
        preferences: AdvancedLightPreferences,
    ) {
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

    fun coversAllKnownSegments(commands: List<AdvancedLightSegmentCommand>): Boolean {
        if (commands.size <= 1 || segments.isEmpty()) return false
        return commands.map { it.id }.toSet() == segments.map { it.id }.toSet()
    }

    fun selectedSegments(): List<AdvancedLightSegmentState> =
        segments.filter { it.selectedForEdit }

    fun preferencesSnapshot(): AdvancedLightPreferences =
        AdvancedLightPreferences(
            order = segments.map { it.id },
            segments = segments.associate { segment ->
                segment.id to AdvancedLightSegmentPreference(
                    name = segment.name,
                    color = segment.color,
                    effect = segment.effect,
                    brightness = segment.brightness,
                    effectSpeed = segment.effectSpeed,
                    effectIntensity = segment.effectIntensity,
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
