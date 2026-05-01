package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.core.TokenLightSource
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.dynamicmap.runtime.logic.GridCalibration
import com.tabletopcontrol.dynamicmap.runtime.logic.GridConfig
import com.tabletopcontrol.dynamicmap.runtime.logic.MapCalibration
import com.tabletopcontrol.dynamicmap.runtime.logic.TableMapOffset
import com.tabletopcontrol.dynamicmap.runtime.logic.Token
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties
import javafx.scene.paint.Color

internal data class MapSceneState(
    val dynamicMapBundlePath: String?,
    val dynamicMapDisplayPath: String?,
    val mapImageUri: String?,
    val mapDisplayPath: String?,
    val mapCalibration: MapCalibration,
    val gridCalibration: GridCalibration,
    val gridColor: Color,
    val backgroundColor: Color,
    val mapRotation: Int,
    val tableMapOffset: TableMapOffset,
    val gridVisible: Boolean,
    val showTokenNames: Boolean,
    val fog: MapFogSceneState?,
    val tokens: List<Token>,
    val activeTokenId: String?,
    val dynamicMapRenderMode: DynamicMapRenderMode = DynamicMapRenderMode.RENDER,
    val openDynamicDoorIds: Set<String> = emptySet(),
)

internal data class MapFogSceneState(
    val cols: Int,
    val rows: Int,
    val colOffset: Int,
    val rowOffset: Int,
    val mode: MapFogSceneMode,
    val cells: List<Pair<Int, Int>>,
)

internal enum class MapFogSceneMode {
    HIDDEN_ALL,
    REVEALED_ALL,
    PARTIAL_REVEALED,
    PARTIAL_HIDDEN,
}

internal object MapSceneCodec {
    private const val VERSION = 2
    private const val LEGACY_VERSION = 1

    fun serialize(state: MapSceneState): String {
        val props = Properties().apply {
            setProperty("version", VERSION.toString())
            state.dynamicMapBundlePath?.let { setProperty("dynamicMap.bundlePath", it) }
            state.dynamicMapDisplayPath?.let { setProperty("dynamicMap.displayPath", it) }
            setProperty("dynamicMap.renderMode", state.dynamicMapRenderMode.name)
            val openDoorIds = state.openDynamicDoorIds.filter { it.isNotBlank() }.sorted()
            setProperty("dynamicMap.openDoors.count", openDoorIds.size.toString())
            openDoorIds.forEachIndexed { index, doorId ->
                setProperty("dynamicMap.openDoors.$index", doorId)
            }
            state.mapImageUri?.let { setProperty("mapImageUri", it) }
            state.mapDisplayPath?.let { setProperty("mapDisplayPath", it) }
            setProperty("map.scale", state.mapCalibration.scale.toString())
            setProperty("map.offsetX", state.mapCalibration.offsetX.toString())
            setProperty("map.offsetY", state.mapCalibration.offsetY.toString())
            setProperty("grid.cellSizeInPixels", state.gridCalibration.cellSizeInPixels.toString())
            setProperty("grid.scale", state.gridCalibration.scale.toString())
            setProperty("grid.offsetX", state.gridCalibration.offsetX.toString())
            setProperty("grid.offsetY", state.gridCalibration.offsetY.toString())
            setProperty("grid.color", MapSettingsSerializer.colorToString(state.gridColor))
            setProperty("background.color", MapSettingsSerializer.colorToString(state.backgroundColor))
            setProperty("map.rotation", state.mapRotation.toString())
            setProperty("table.offsetX", state.tableMapOffset.offsetX.toString())
            setProperty("table.offsetY", state.tableMapOffset.offsetY.toString())
            setProperty("grid.visible", state.gridVisible.toString())
            setProperty("tokens.showNames", state.showTokenNames.toString())
            state.activeTokenId?.let { setProperty("tokens.active", it) }

            setProperty("token.count", state.tokens.size.toString())
            state.tokens.forEachIndexed { index, token ->
                val prefix = "token.$index"
                setProperty("$prefix.id", token.id)
                setProperty("$prefix.name", token.name)
                setProperty("$prefix.col", token.col.toString())
                setProperty("$prefix.row", token.row.toString())
                setProperty("$prefix.size", token.size.name)
                setProperty("$prefix.color", ColorHexCodec.colorToHex(token.color))
                setProperty("$prefix.isPlayerCharacter", token.isPlayerCharacter.toString())
                token.darkvisionRangeCells?.let { setProperty("$prefix.darkvisionRangeCells", it.toString()) }
                token.lightSource?.let { source ->
                    setProperty("$prefix.lightBrightRangeCells", source.brightRangeCells.toString())
                    setProperty("$prefix.lightDimRangeCells", source.dimRangeCells.toString())
                    setProperty("$prefix.lightColor", source.colorHex)
                }
                token.imageUri?.let { setProperty("$prefix.imageUri", it) }
                setProperty("$prefix.imageScaleX", token.imageScaleX.toString())
                setProperty("$prefix.imageScaleY", token.imageScaleY.toString())
                setProperty("$prefix.imageOffsetX", token.imageOffsetX.toString())
                setProperty("$prefix.imageOffsetY", token.imageOffsetY.toString())
            }

            if (state.fog != null) {
                setProperty("fog.present", true.toString())
                setProperty("fog.cols", state.fog.cols.toString())
                setProperty("fog.rows", state.fog.rows.toString())
                setProperty("fog.colOffset", state.fog.colOffset.toString())
                setProperty("fog.rowOffset", state.fog.rowOffset.toString())
                setProperty("fog.mode", state.fog.mode.name)
                setProperty("fog.cells.count", state.fog.cells.size.toString())
                state.fog.cells.forEachIndexed { index, (col, row) ->
                    setProperty("fog.cells.$index", "$col,$row")
                }
            } else {
                setProperty("fog.present", false.toString())
            }
        }

        return StringWriter().use { writer ->
            props.store(writer, "TabletopControl map scene")
            writer.toString()
        }
    }

    fun deserialize(text: String): MapSceneState? {
        val props = Properties().apply { load(StringReader(text)) }
        val version = props.getProperty("version")?.toIntOrNull() ?: return null
        if (version != VERSION && version != LEGACY_VERSION) return null

        val mapCalibration = MapCalibration(
            scale = props.getProperty("map.scale")?.toDoubleOrNull() ?: 1.0,
            offsetX = props.getProperty("map.offsetX")?.toDoubleOrNull() ?: 0.0,
            offsetY = props.getProperty("map.offsetY")?.toDoubleOrNull() ?: 0.0,
        )
        val gridCalibration = GridCalibration(
            cellSizeInPixels = props.getProperty("grid.cellSizeInPixels")?.toDoubleOrNull() ?: 50.0,
            scale = props.getProperty("grid.scale")?.toDoubleOrNull() ?: 1.0,
            offsetX = props.getProperty("grid.offsetX")?.toDoubleOrNull() ?: 0.0,
            offsetY = props.getProperty("grid.offsetY")?.toDoubleOrNull() ?: 0.0,
        )
        val gridColor = MapSettingsSerializer.stringToColor(props.getProperty("grid.color")) ?: GridConfig().color
        val backgroundColor =
            MapSettingsSerializer.stringToColor(props.getProperty("background.color")) ?: Color.BLACK
        val mapRotation = props.getProperty("map.rotation")?.toIntOrNull() ?: 0
        val tableMapOffset = TableMapOffset(
            offsetX = props.getProperty("table.offsetX")?.toDoubleOrNull() ?: 0.0,
            offsetY = props.getProperty("table.offsetY")?.toDoubleOrNull() ?: 0.0,
        )
        val dynamicMapRenderMode =
            DynamicMapRenderMode.fromPersisted(props.getProperty("dynamicMap.renderMode"))
                ?: DynamicMapRenderMode.RENDER
        val openDynamicDoorIds = buildSet {
            val count = props.getProperty("dynamicMap.openDoors.count")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            for (index in 0 until count) {
                props.getProperty("dynamicMap.openDoors.$index")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }

        val tokenCount = props.getProperty("token.count")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val tokens = buildList {
            for (index in 0 until tokenCount) {
                val prefix = "token.$index"
                val id = props.getProperty("$prefix.id") ?: continue
                val name = props.getProperty("$prefix.name") ?: continue
                val col = props.getProperty("$prefix.col")?.toIntOrNull() ?: continue
                val row = props.getProperty("$prefix.row")?.toIntOrNull() ?: continue
                val color = ColorHexCodec.parseOrNull(props.getProperty("$prefix.color")) ?: continue
                add(
                    Token(
                        id = id,
                        name = name,
                        col = col,
                        row = row,
                        size = runCatching {
                            com.tabletopcontrol.core.TokenSize.valueOf(props.getProperty("$prefix.size"))
                        }.getOrDefault(com.tabletopcontrol.core.TokenSize.MEDIUM),
                        color = color,
                        isPlayerCharacter = props.getProperty("$prefix.isPlayerCharacter")
                            ?.toBooleanStrictOrNull()
                            ?: false,
                        darkvisionRangeCells = props.getProperty("$prefix.darkvisionRangeCells")
                            ?.toDoubleOrNull()
                            ?.takeIf { it.isFinite() && it > 0.0 },
                        lightSource = readTokenLightSource(props, prefix),
                        imageUri = props.getProperty("$prefix.imageUri"),
                        imageScaleX = props.getProperty("$prefix.imageScaleX")?.toDoubleOrNull() ?: 1.0,
                        imageScaleY = props.getProperty("$prefix.imageScaleY")?.toDoubleOrNull() ?: 1.0,
                        imageOffsetX = props.getProperty("$prefix.imageOffsetX")?.toDoubleOrNull() ?: 0.0,
                        imageOffsetY = props.getProperty("$prefix.imageOffsetY")?.toDoubleOrNull() ?: 0.0,
                    ),
                )
            }
        }

        val fog = if (props.getProperty("fog.present")?.toBooleanStrictOrNull() == true) {
            val cols = props.getProperty("fog.cols")?.toIntOrNull() ?: return null
            val rows = props.getProperty("fog.rows")?.toIntOrNull() ?: return null
            val colOffset = props.getProperty("fog.colOffset")?.toIntOrNull() ?: return null
            val rowOffset = props.getProperty("fog.rowOffset")?.toIntOrNull() ?: return null
            val mode = when (version) {
                VERSION -> runCatching { MapFogSceneMode.valueOf(props.getProperty("fog.mode")) }.getOrNull()
                    ?: MapFogSceneMode.HIDDEN_ALL

                else -> when (props.getProperty("fog.mode")) {
                    "REVEALED_ALL" -> MapFogSceneMode.REVEALED_ALL
                    "PARTIAL" -> MapFogSceneMode.PARTIAL_REVEALED
                    else -> MapFogSceneMode.HIDDEN_ALL
                }
            }
            val cells = buildList {
                val countKey = if (version == VERSION) "fog.cells.count" else "fog.revealed.count"
                val itemPrefix = if (version == VERSION) "fog.cells." else "fog.revealed."
                val cellCount = props.getProperty(countKey)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                for (index in 0 until cellCount) {
                    val coords = props.getProperty("$itemPrefix$index")?.split(',') ?: continue
                    if (coords.size != 2) continue
                    val col = coords[0].toIntOrNull() ?: continue
                    val row = coords[1].toIntOrNull() ?: continue
                    add(Pair(col, row))
                }
            }
            MapFogSceneState(
                cols = cols,
                rows = rows,
                colOffset = colOffset,
                rowOffset = rowOffset,
                mode = mode,
                cells = cells,
            )
        } else {
            null
        }

        return MapSceneState(
            dynamicMapBundlePath = props.getProperty("dynamicMap.bundlePath"),
            dynamicMapDisplayPath = props.getProperty("dynamicMap.displayPath"),
            mapImageUri = props.getProperty("mapImageUri"),
            mapDisplayPath = props.getProperty("mapDisplayPath"),
            mapCalibration = mapCalibration,
            gridCalibration = gridCalibration,
            gridColor = gridColor,
            backgroundColor = backgroundColor,
            mapRotation = mapRotation,
            tableMapOffset = tableMapOffset,
            gridVisible = props.getProperty("grid.visible")?.toBooleanStrictOrNull() ?: false,
            showTokenNames = props.getProperty("tokens.showNames")?.toBooleanStrictOrNull() ?: false,
            fog = fog,
            tokens = tokens,
            activeTokenId = props.getProperty("tokens.active"),
            dynamicMapRenderMode = dynamicMapRenderMode,
            openDynamicDoorIds = openDynamicDoorIds,
        )
    }

    private fun readTokenLightSource(
        props: Properties,
        prefix: String,
    ): TokenLightSource? {
        val brightRange = props.getProperty("$prefix.lightBrightRangeCells")
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return null
        val dimRange = props.getProperty("$prefix.lightDimRangeCells")
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return null
        if (brightRange <= 0.0 && dimRange <= 0.0) return null
        val colorHex = props.getProperty("$prefix.lightColor")
            ?.takeIf { ColorHexCodec.parseOrNull(it) != null }
            ?: "#FFFFFF"
        return TokenLightSource(
            brightRangeCells = brightRange,
            dimRangeCells = maxOf(dimRange, brightRange),
            colorHex = colorHex,
        )
    }
}
