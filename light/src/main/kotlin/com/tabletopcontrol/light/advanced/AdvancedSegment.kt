package com.tabletopcontrol.light.advanced

import com.tabletopcontrol.light.LightEffect
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty

/**
 * Observable mutable state for a single WLED segment.
 *
 * JavaFX properties are used so the segment table can bind
 * directly to name, on/off, and selection state.
 *
 * @param id        WLED segment id (immutable, identifies the segment on the device)
 * @param startLed  First LED index in this segment (immutable)
 * @param stopLed   One-past-last LED index (immutable)
 * @param ledCount  Number of LEDs in this segment (immutable)
 */
class AdvancedSegment(
    val id: Int,
    val startLed: Int,
    val stopLed: Int,
    val ledCount: Int,
) {
    /** User-editable display name.  Defaults to "Segment $id". */
    val nameProperty = SimpleStringProperty("Segment $id")
    var name: String
        get() = nameProperty.get()
        set(v) = nameProperty.set(v)

    /** Whether this segment is switched on. */
    val onProperty = SimpleBooleanProperty(true)
    var isOn: Boolean
        get() = onProperty.get()
        set(v) = onProperty.set(v)

    /** Whether this segment is selected for editing by the control panel below the table. */
    val selectedForEditProperty = SimpleBooleanProperty(false)
    var isSelectedForEdit: Boolean
        get() = selectedForEditProperty.get()
        set(v) = selectedForEditProperty.set(v)

    /** Current color as a CSS hex string, e.g. `"#FF4400"`. */
    val colorProperty = SimpleStringProperty("#FFFFFF")
    var color: String
        get() = colorProperty.get()
        set(v) = colorProperty.set(v)

    /** Currently selected effect for this segment. */
    val effectProperty = SimpleObjectProperty<LightEffect>(LightEffect.NONE)
    var effect: LightEffect
        get() = effectProperty.get()
        set(v) = effectProperty.set(v)

    /**
     * Brightness in the range `0.0` (off) to `1.0` (full).
     *
     * Stored as an integer property (0–255) for easy conversion to the
     * WLED `bri` field while still supporting fine-grained slider binding.
     */
    val brightnessRawProperty = SimpleIntegerProperty(255)
    /** WLED `bri` value (0–255). */
    var brightnessRaw: Int
        get() = brightnessRawProperty.get()
        set(v) = brightnessRawProperty.set(v.coerceIn(0, 255))

    /** Convenience property that converts raw brightness to 0.0–1.0. */
    var brightness: Double
        get() = brightnessRaw / 255.0
        set(v) = run { brightnessRaw = (v.coerceIn(0.0, 1.0) * 255).toInt() }

    /** Effect speed in the range `0`–`255` (WLED `sx`). */
    val effectSpeedProperty = SimpleIntegerProperty(128)
    var effectSpeed: Int
        get() = effectSpeedProperty.get()
        set(v) = effectSpeedProperty.set(v.coerceIn(0, 255))

    /** Effect intensity in the range `0`–`255` (WLED `ix`). */
    val effectIntensityProperty = SimpleIntegerProperty(128)
    var effectIntensity: Int
        get() = effectIntensityProperty.get()
        set(v) = effectIntensityProperty.set(v.coerceIn(0, 255))
}
