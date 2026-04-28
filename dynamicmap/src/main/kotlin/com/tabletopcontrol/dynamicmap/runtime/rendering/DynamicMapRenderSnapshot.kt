package com.tabletopcontrol.dynamicmap.runtime.rendering

import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRenderMode
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRuntimeBackgroundCalibration
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRuntimeLight
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRuntimeWall
import com.tabletopcontrol.dynamicmap.runtime.MeasurementType
import com.tabletopcontrol.dynamicmap.runtime.logic.FogOfWarState
import com.tabletopcontrol.dynamicmap.runtime.logic.GridCalibration
import com.tabletopcontrol.dynamicmap.runtime.logic.TableMapOffset
import javafx.scene.paint.Color

/**
 * Immutable render input shared by JavaFX and LibGDX renderer implementations.
 *
 * This is the compatibility layer between the existing DynamicMap runtime/EventBus
 * model and any concrete drawing backend. It deliberately stores no JavaFX Canvas
 * objects, GraphicsContext handles, or LibGDX resources.
 */
internal data class DynamicMapRenderSnapshot(
    val surfaceWidth: Double,
    val surfaceHeight: Double,
    val backgroundColor: RenderColor,
    val gridCalibration: GridCalibration,
    val grid: RenderGrid?,
    val dynamicMap: RenderDynamicMap?,
    val fogOfWar: RenderFogOfWar?,
    val fogOpacity: Double,
    val sightline: RenderSightlineMesh?,
    val sightlineTint: RenderColor,
    val sightlineOpacity: Double,
    val tokens: List<RenderToken>,
    val activeTokenId: String?,
    val showTokenNames: Boolean,
    val hideTokensInFog: Boolean,
    val measurements: List<RenderMeasurement>,
    val showDmOnlyMeasurements: Boolean,
    val dynamicMapRenderMode: DynamicMapRenderMode,
    val dynamicDebugWallColor: RenderColor,
    val showDynamicLightMarkers: Boolean,
    val tableMapOffset: TableMapOffset,
    val applyTableMapOffset: Boolean,
    val viewportScale: Double,
    val viewportOffsetX: Double,
    val viewportOffsetY: Double,
    val showTableViewportOutline: Boolean,
    val tableViewportWidth: Double,
    val tableViewportHeight: Double,
    val tableViewportOutlineColor: RenderColor,
)

internal data class RenderColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
) {
    companion object {
        val Black: RenderColor = RenderColor(0f, 0f, 0f, 1f)
        val White: RenderColor = RenderColor(1f, 1f, 1f, 1f)
        val Orange: RenderColor = RenderColor(1f, 0.647f, 0f, 1f)
    }
}

internal data class RenderGrid(
    val color: RenderColor,
    val lineWidth: Double,
)

internal data class RenderDynamicMap(
    val cols: Int,
    val rows: Int,
    val backgroundBytes: ByteArray?,
    val backgroundCalibration: DynamicMapRuntimeBackgroundCalibration,
    val walls: List<DynamicMapRuntimeWall>,
    val lights: List<DynamicMapRuntimeLight>,
)

internal class RenderFogOfWar(
    val cols: Int,
    val rows: Int,
    val colOffset: Int,
    val rowOffset: Int,
    private val revealed: Array<BooleanArray>,
) {
    fun isRevealed(col: Int, row: Int): Boolean =
        col in 0 until cols && row in 0 until rows && revealed[col][row]
}

internal data class RenderSightlineMesh(
    val triangles: List<RenderSightTriangle>,
)

internal data class RenderSightTriangle(
    val origin: RenderPoint,
    val first: RenderPoint,
    val second: RenderPoint,
)

internal data class RenderPoint(
    val x: Double,
    val y: Double,
)

internal data class RenderToken(
    val id: String,
    val name: String,
    val col: Int,
    val row: Int,
    val size: TokenSize,
    val color: RenderColor,
    val imagePath: String?,
    val imageScaleX: Double,
    val imageScaleY: Double,
    val imageOffsetX: Double,
    val imageOffsetY: Double,
    val visibleInSightline: Boolean,
    val occupiedCells: List<RenderGridCell>,
)

internal data class RenderGridCell(
    val col: Int,
    val row: Int,
)

internal data class RenderMeasurement(
    val id: String,
    val type: MeasurementType,
    val startCol: Int,
    val startRow: Int,
    val endCol: Int,
    val endRow: Int,
    val coneAngleDegrees: Double,
    val mirroredToTable: Boolean,
    val unitLabel: String,
    val unitsSuffix: String,
    val color: RenderColor,
    val dimensionText: String,
)

internal fun Color.toRenderColor(): RenderColor =
    RenderColor(
        red = red.toFloat().coerceIn(0f, 1f),
        green = green.toFloat().coerceIn(0f, 1f),
        blue = blue.toFloat().coerceIn(0f, 1f),
        alpha = opacity.toFloat().coerceIn(0f, 1f),
    )

internal fun FogOfWarState.toRenderFogOfWar(colOffset: Int, rowOffset: Int): RenderFogOfWar {
    val copy = Array(cols) { col ->
        BooleanArray(rows) { row -> isRevealed(col, row) }
    }
    return RenderFogOfWar(
        cols = cols,
        rows = rows,
        colOffset = colOffset,
        rowOffset = rowOffset,
        revealed = copy,
    )
}
