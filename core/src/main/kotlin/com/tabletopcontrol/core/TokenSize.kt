package com.tabletopcontrol.core

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Supported token footprint sizes shared between tracker plugins and the map.
 *
 * [footprintTiles] is the rendered width/height of the token in grid-tile units.
 * Sub-tile sizes are stored as side lengths rather than area fractions, so a
 * quarter-tile token renders at 0.5 tiles wide/high and an eighth-tile token
 * renders at sqrt(1/8) tiles wide/high.
 */
enum class TokenSize(
    val displayName: String,
    val footprintTiles: Double,
    val footprintDescription: String,
) {
    TINY("Tiny", sqrt(0.125), "1/8 tile"),
    SMALL("Small", 0.5, "1/4 tile"),
    MEDIUM("Medium", 1.0, "1 tile"),
    LARGE("Large", 2.0, "2x2 tiles"),
    HUGE("Huge", 3.0, "3x3 tiles"),
    GARGANTUAN("Gargantuan", 4.0, "4x4 tiles"),
    ;

    /**
     * Number of whole grid cells touched by this token in each direction.
     *
     * Tiny and small tokens still belong to one anchor cell for movement, fog
     * checks, and placement calculations.
     */
    val gridSpanCells: Int
        get() = ceil(footprintTiles).toInt().coerceAtLeast(1)

    val menuLabel: String
        get() = "$displayName ($footprintDescription)"

    companion object {
        fun fromPersistence(value: String?): TokenSize =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: MEDIUM
    }
}
