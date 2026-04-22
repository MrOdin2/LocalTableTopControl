package com.tabletopcontrol.dynamicmap

import javafx.scene.canvas.GraphicsContext
import javafx.scene.image.Image
import kotlin.math.abs
import kotlin.math.min

/**
 * Background calibration stored in map-relative units so it survives pane resizing.
 *
 * [scale] expresses rendered cell widths per image pixel. The actual canvas scale used
 * during drawing is `scale * cellSize`.
 *
 * [offsetX] and [offsetY] displace the image centre from the map centre in grid cells.
 * The actual canvas offset used during drawing is `offset * cellSize`.
 */
data class DynamicMapBackgroundCalibration(
    val scale: Double = 1.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
) {
    init {
        require(scale > 0.0) { "scale must be positive, was $scale" }
    }
}

data class DynamicMapEditorMetrics(
    val cellSize: Double,
    val originX: Double,
    val originY: Double,
    val mapWidth: Double,
    val mapHeight: Double,
)

enum class DynamicMapGuidedCalibrationAxis {
    HORIZONTAL,
    VERTICAL,
}

fun computeEditorMetrics(
    width: Double,
    height: Double,
    cols: Int,
    rows: Int,
): DynamicMapEditorMetrics {
    val padding = 20.0
    val safeCols = cols.coerceAtLeast(1)
    val safeRows = rows.coerceAtLeast(1)
    val availableWidth = (width - padding * 2.0).coerceAtLeast(1.0)
    val availableHeight = (height - padding * 2.0).coerceAtLeast(1.0)
    val cellSize = min(availableWidth / safeCols, availableHeight / safeRows).coerceAtLeast(2.0)
    val mapWidth = safeCols * cellSize
    val mapHeight = safeRows * cellSize
    return DynamicMapEditorMetrics(
        cellSize = cellSize,
        originX = (width - mapWidth) / 2.0,
        originY = (height - mapHeight) / 2.0,
        mapWidth = mapWidth,
        mapHeight = mapHeight,
    )
}

fun fittedBackgroundCalibration(
    imageWidth: Double,
    imageHeight: Double,
    cols: Int,
    rows: Int,
): DynamicMapBackgroundCalibration {
    if (imageWidth <= 0.0 || imageHeight <= 0.0) return DynamicMapBackgroundCalibration()
    val scale = min(
        cols.coerceAtLeast(1).toDouble() / imageWidth,
        rows.coerceAtLeast(1).toDouble() / imageHeight,
    ).coerceAtLeast(0.0001)
    return DynamicMapBackgroundCalibration(scale = scale)
}

fun drawCalibratedBackgroundImage(
    gc: GraphicsContext,
    image: Image,
    metrics: DynamicMapEditorMetrics,
    calibration: DynamicMapBackgroundCalibration,
) {
    val destWidth = image.width * calibration.scale * metrics.cellSize
    val destHeight = image.height * calibration.scale * metrics.cellSize
    if (!destWidth.isFinite() || !destHeight.isFinite() || destWidth <= 0.0 || destHeight <= 0.0) return

    val centerX = metrics.originX + metrics.mapWidth / 2.0
    val centerY = metrics.originY + metrics.mapHeight / 2.0
    gc.drawImage(
        image,
        centerX - destWidth / 2.0 + calibration.offsetX * metrics.cellSize,
        centerY - destHeight / 2.0 + calibration.offsetY * metrics.cellSize,
        destWidth,
        destHeight,
    )
}

internal fun guidedBackgroundCalibrationStep1(
    current: DynamicMapBackgroundCalibration,
    clickX: Double,
    clickY: Double,
    targetX: Double,
    targetY: Double,
    cellSizeInPixels: Double,
): DynamicMapBackgroundCalibration {
    if (cellSizeInPixels <= 0.0) return current
    return DynamicMapBackgroundCalibration(
        scale = current.scale,
        offsetX = current.offsetX + (targetX - clickX) / cellSizeInPixels,
        offsetY = current.offsetY + (targetY - clickY) / cellSizeInPixels,
    )
}

internal fun guidedBackgroundCalibrationStep2(
    currentCalibration: DynamicMapBackgroundCalibration,
    cornerX: Double,
    cornerY: Double,
    targetX: Double,
    targetY: Double,
    cellSizeInPixels: Double,
    axis: DynamicMapGuidedCalibrationAxis = DynamicMapGuidedCalibrationAxis.HORIZONTAL,
    targetTileSpan: Int = 1,
): DynamicMapBackgroundCalibration? {
    if (cellSizeInPixels <= 0.0 || targetTileSpan <= 0) return null

    val distance = when (axis) {
        DynamicMapGuidedCalibrationAxis.HORIZONTAL -> abs(cornerX - targetX)
        DynamicMapGuidedCalibrationAxis.VERTICAL -> abs(cornerY - targetY)
    }
    if (distance < 1.0) return null

    val scaleFactor = (cellSizeInPixels * targetTileSpan) / distance
    return DynamicMapBackgroundCalibration(
        scale = currentCalibration.scale * scaleFactor,
        offsetX = currentCalibration.offsetX * scaleFactor,
        offsetY = currentCalibration.offsetY * scaleFactor,
    )
}
