package com.tabletopcontrol.dynamicmap

import java.util.UUID

data class DynamicMapDocument(
    val cols: Int = 30,
    val rows: Int = 20,
    val backgroundImageUri: String? = null,
    val backgroundDisplayPath: String? = null,
    val visibility: DynamicMapLayerVisibility = DynamicMapLayerVisibility(),
    val walls: List<DynamicMapWall> = emptyList(),
    val lights: List<DynamicMapLight> = emptyList(),
)

data class DynamicMapLayerVisibility(
    val background: Boolean = true,
    val walls: Boolean = true,
    val lights: Boolean = true,
    val grid: Boolean = true,
)

enum class DynamicMapLayer {
    BACKGROUND,
    WALLS,
    LIGHTS,
    GRID,
}

enum class DynamicMapTool {
    WALL_LINE,
    WALL_RECT,
    LIGHT,
}

data class DynamicMapPoint(
    val x: Double,
    val y: Double,
)

data class DynamicMapWall(
    val id: String = UUID.randomUUID().toString(),
    val start: DynamicMapPoint,
    val end: DynamicMapPoint,
)

data class DynamicMapLight(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val position: DynamicMapPoint,
    val brightRadius: Double,
    val dimRadius: Double,
    val colorHex: String,
    val enabled: Boolean = true,
)

data class DynamicMapLightPreset(
    val id: String,
    val displayName: String,
    val brightRadius: Double,
    val dimRadius: Double,
    val colorHex: String,
    val description: String,
)

object DynamicMapLightPresets {
    val presets: List<DynamicMapLightPreset> = listOf(
        DynamicMapLightPreset(
            id = "torch",
            displayName = "Torch",
            brightRadius = 4.0,
            dimRadius = 8.0,
            colorHex = "#ffb347",
            description = "Warm handheld light for most dungeon scenes.",
        ),
        DynamicMapLightPreset(
            id = "candle",
            displayName = "Candle",
            brightRadius = 1.0,
            dimRadius = 2.0,
            colorHex = "#ffd27f",
            description = "Tiny pool of light for altars, desks, or mood lighting.",
        ),
        DynamicMapLightPreset(
            id = "lantern",
            displayName = "Lantern",
            brightRadius = 6.0,
            dimRadius = 12.0,
            colorHex = "#ffe082",
            description = "Reliable exploration light with a broader dim edge.",
        ),
        DynamicMapLightPreset(
            id = "campfire",
            displayName = "Campfire",
            brightRadius = 5.0,
            dimRadius = 10.0,
            colorHex = "#ff8a65",
            description = "Strong orange ambience for outdoor or cave encounters.",
        ),
        DynamicMapLightPreset(
            id = "moonbeam",
            displayName = "Moonbeam",
            brightRadius = 3.0,
            dimRadius = 6.0,
            colorHex = "#90caf9",
            description = "Cool magical light for spells or nighttime set pieces.",
        ),
    )

    val defaultPreset: DynamicMapLightPreset = presets.first()

    fun byId(id: String?): DynamicMapLightPreset =
        presets.firstOrNull { it.id == id } ?: defaultPreset
}
