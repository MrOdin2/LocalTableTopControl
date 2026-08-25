package com.tabletopcontrol.hotkey

import com.tabletopcontrol.core.MusicControlOperation
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent

internal const val DEFAULT_HOTKEY_COUNT = 12
internal const val MAX_HOTKEY_COUNT = 64
internal const val MAX_ACTIONS_PER_HOTKEY = 16
internal const val MIN_MATRIX_COLUMNS = 2
internal const val MAX_MATRIX_COLUMNS = 8
internal const val MAX_WAIT_MILLIS = 600_000L

internal data class HotkeySettings(
    val columns: Int = 4,
    val hotkeys: List<HotkeyDefinition> = defaultHotkeys(),
)

internal data class HotkeyDefinition(
    val id: String,
    val name: String,
    val icon: String? = null,
    val colorHex: String? = null,
    val binding: KeyBinding? = null,
    val actions: List<HotkeyAction> = emptyList(),
)

internal data class KeyBinding(
    val code: String,
    val shift: Boolean = false,
    val control: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
) {
    val resolvedCode: KeyCode?
        get() = runCatching { KeyCode.valueOf(code) }.getOrNull()

    fun matches(event: KeyEvent): Boolean =
        event.code == resolvedCode &&
            event.isShiftDown == shift &&
            event.isControlDown == control &&
            event.isAltDown == alt &&
            event.isMetaDown == meta

    fun isSuitable(): Boolean {
        val key = resolvedCode ?: return false
        if (key.isModifierKey) return false
        if (key in KeyCode.F1..KeyCode.F24) return true
        return control || alt || meta
    }

    fun displayText(): String = buildList {
        if (control) add("Ctrl")
        if (alt) add("Alt")
        if (shift) add("Shift")
        if (meta) add("Meta")
        add(resolvedCode?.name ?: code)
    }.joinToString("+")

    companion object {
        fun from(event: KeyEvent): KeyBinding = KeyBinding(
            code = event.code.name,
            shift = event.isShiftDown,
            control = event.isControlDown,
            alt = event.isAltDown,
            meta = event.isMetaDown,
        )

        fun functionKey(number: Int): KeyBinding = KeyBinding("F${number.coerceIn(1, 24)}")
    }
}

private val KeyCode.isModifierKey: Boolean
    get() = this == KeyCode.SHIFT || this == KeyCode.CONTROL || this == KeyCode.ALT ||
        this == KeyCode.META || this == KeyCode.COMMAND || this == KeyCode.WINDOWS ||
        this == KeyCode.ALT_GRAPH || this == KeyCode.SHORTCUT

internal sealed interface HotkeyAction

internal enum class HotkeyLightTarget {
    GLOBAL,
    ALL_SEGMENTS,
    SEGMENTS,
}

internal data class LightAction(
    val target: HotkeyLightTarget = HotkeyLightTarget.GLOBAL,
    val segmentIds: Set<Int> = emptySet(),
    val power: Boolean? = null,
    val colorHex: String? = null,
    val effectId: Int? = null,
    val brightness: Double? = null,
    val effectSpeed: Int? = null,
    val effectIntensity: Int? = null,
) : HotkeyAction

internal data class PlaySoundAction(
    val uri: String,
    val volume: Double = 1.0,
) : HotkeyAction

internal data class MusicAction(
    val operation: MusicControlOperation,
    val uri: String? = null,
) : HotkeyAction

internal data class TriggerHotkeyAction(val hotkeyId: String) : HotkeyAction

internal data class WaitAction(val durationMillis: Long) : HotkeyAction

internal data class LightEffectOption(val id: Int, val displayName: String)

internal val HOTKEY_LIGHT_EFFECTS = listOf(
    LightEffectOption(0, "None"),
    LightEffectOption(1, "Blink"),
    LightEffectOption(2, "Breathe"),
    LightEffectOption(3, "Color Wipe"),
    LightEffectOption(8, "Color Loop"),
    LightEffectOption(9, "Rainbow"),
    LightEffectOption(10, "Scan"),
    LightEffectOption(11, "Dual Scan"),
    LightEffectOption(13, "Theater"),
    LightEffectOption(14, "Theater Rainbow"),
    LightEffectOption(15, "Running"),
    LightEffectOption(17, "Twinkle"),
    LightEffectOption(20, "Sparkle"),
    LightEffectOption(23, "Strobe"),
    LightEffectOption(24, "Strobe Rainbow"),
    LightEffectOption(28, "Chase Color"),
    LightEffectOption(33, "Rainbow Runner"),
    LightEffectOption(40, "Scanner"),
    LightEffectOption(42, "Fireworks"),
    LightEffectOption(45, "Fire"),
    LightEffectOption(57, "Lightning"),
    LightEffectOption(76, "Meteor"),
    LightEffectOption(77, "Smooth Meteor"),
    LightEffectOption(87, "Glitter"),
    LightEffectOption(88, "Candle"),
    LightEffectOption(91, "Bouncing Balls"),
    LightEffectOption(92, "Sinelon"),
    LightEffectOption(96, "Drip"),
    LightEffectOption(100, "Heartbeat"),
    LightEffectOption(101, "Ocean"),
    LightEffectOption(102, "Candle Multi"),
)

internal fun defaultHotkeys(): List<HotkeyDefinition> =
    (1..DEFAULT_HOTKEY_COUNT).map { number ->
        HotkeyDefinition(
            id = "default-f$number",
            name = "Hotkey $number",
            binding = KeyBinding.functionKey(number),
        )
    }
