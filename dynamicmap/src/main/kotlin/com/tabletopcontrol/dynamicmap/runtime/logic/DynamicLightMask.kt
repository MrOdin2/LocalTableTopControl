package com.tabletopcontrol.dynamicmap.runtime.logic

import com.tabletopcontrol.dynamicmap.runtime.DynamicMapBundle
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRuntimeSunlightArea
import com.tabletopcontrol.dynamicmap.runtime.blocksDimLightWhenClosed
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

internal class DynamicLightMask(
    val cols: Int,
    val rows: Int,
    private val litArea: Area,
    private val brightArea: Area,
    val tintContributions: List<DynamicLightTintContribution>,
    val lightingActive: Boolean,
) {
    init {
        require(cols > 0) { "cols must be positive, was $cols" }
        require(rows > 0) { "rows must be positive, was $rows" }
    }

    fun copyLitArea(): Area = Area(litArea)

    fun copyBrightArea(): Area = Area(brightArea)

    fun containsPoint(x: Double, y: Double): Boolean =
        x in 0.0..cols.toDouble() &&
            y in 0.0..rows.toDouble() &&
            litArea.contains(x, y)

    fun containsBrightPoint(x: Double, y: Double): Boolean =
        x in 0.0..cols.toDouble() &&
            y in 0.0..rows.toDouble() &&
            brightArea.contains(x, y)

    fun applyToSightMesh(
        geometry: DynamicSightlineGeometry,
        sightMesh: DynamicSightlineMesh,
    ): DynamicSightlineMesh {
        if (!lightingActive) return sightMesh
        val litVisibleArea = sightMesh.copyVisibleArea().apply {
            intersect(copyLitArea())
        }
        return geometry.meshFromVisibleArea(litVisibleArea)
    }

    companion object {
        fun fromBundle(
            bundle: DynamicMapBundle,
            geometry: DynamicSightlineGeometry,
            dimLightGeometry: DynamicSightlineGeometry = hardLightGeometryFor(bundle),
        ): DynamicLightMask {
            val mapBounds = Area(Rectangle2D.Double(0.0, 0.0, bundle.cols.toDouble(), bundle.rows.toDouble()))
            val litArea = Area()
            val brightArea = Area()
            val tintContributions = mutableListOf<DynamicLightTintContribution>()
            val hasAuthoredLighting = bundle.lights.isNotEmpty() || bundle.sunlightAreas.isNotEmpty()
            val hasHardWalls = bundle.walls.any { it.blocksDimLightWhenClosed() }

            bundle.lights
                .filter { it.enabled }
                .forEach { light ->
                    val dimContribution = dimLightContribution(
                        x = light.position.x,
                        y = light.position.y,
                        radius = light.dimRadius,
                        dimLightGeometry = dimLightGeometry,
                        hasHardWalls = hasHardWalls,
                    )
                    val brightContribution = if (light.brightRadius > 0.0) {
                        geometry.visibleAreaFromPoint(
                            x = light.position.x,
                            y = light.position.y,
                            radius = light.brightRadius,
                        )
                    } else {
                        Area()
                    }
                    addPointLightContribution(
                        litArea = litArea,
                        brightArea = brightArea,
                        tintContributions = tintContributions,
                        colorHex = light.colorHex,
                        x = light.position.x,
                        y = light.position.y,
                        dimRadius = light.dimRadius,
                        dimContribution = dimContribution,
                        brightContribution = brightContribution,
                    )
                }

            bundle.sunlightAreas
                .map(::sunlightAreaToArea)
                .forEach(litArea::add)

            litArea.intersect(mapBounds)
            brightArea.intersect(mapBounds)

            return DynamicLightMask(
                cols = bundle.cols,
                rows = bundle.rows,
                litArea = litArea,
                brightArea = brightArea,
                tintContributions = tintContributions,
                lightingActive = hasAuthoredLighting,
            )
        }

        fun fromPcTokenLights(
            bundle: DynamicMapBundle,
            pcSightlines: Iterable<DynamicPcSightlineContribution>,
            dimLightGeometry: DynamicSightlineGeometry = hardLightGeometryFor(bundle),
        ): DynamicLightMask {
            val mapBounds = Area(Rectangle2D.Double(0.0, 0.0, bundle.cols.toDouble(), bundle.rows.toDouble()))
            val litArea = Area()
            val brightArea = Area()
            val tintContributions = mutableListOf<DynamicLightTintContribution>()
            var hasTokenLighting = false
            val hasHardWalls = bundle.walls.any { it.blocksDimLightWhenClosed() }

            pcSightlines.forEach { snapshot ->
                val source = snapshot.token.lightSource ?: return@forEach
                val origin = snapshot.token.sightOrigin()
                val brightRadius = source.brightRangeCells.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
                val dimRadius = maxOf(
                    source.dimRangeCells.takeIf { it.isFinite() && it > 0.0 } ?: 0.0,
                    brightRadius,
                )
                if (brightRadius <= 0.0 && dimRadius <= 0.0) return@forEach

                hasTokenLighting = true
                val dimContribution = dimLightContribution(
                    x = origin.x,
                    y = origin.y,
                    radius = dimRadius,
                    dimLightGeometry = dimLightGeometry,
                    hasHardWalls = hasHardWalls,
                )
                val brightContribution = if (brightRadius > 0.0) {
                    snapshot.contribution.copyVisibleArea().apply {
                        intersect(circleArea(origin.x, origin.y, brightRadius))
                    }
                } else {
                    Area()
                }
                addPointLightContribution(
                    litArea = litArea,
                    brightArea = brightArea,
                    tintContributions = tintContributions,
                    colorHex = source.colorHex,
                    x = origin.x,
                    y = origin.y,
                    dimRadius = dimRadius,
                    dimContribution = dimContribution,
                    brightContribution = brightContribution,
                )
            }

            litArea.intersect(mapBounds)
            brightArea.intersect(mapBounds)

            return DynamicLightMask(
                cols = bundle.cols,
                rows = bundle.rows,
                litArea = litArea,
                brightArea = brightArea,
                tintContributions = tintContributions,
                lightingActive = hasTokenLighting,
            )
        }

        fun combine(
            cols: Int,
            rows: Int,
            masks: Iterable<DynamicLightMask?>,
        ): DynamicLightMask {
            val mapBounds = Area(Rectangle2D.Double(0.0, 0.0, cols.toDouble(), rows.toDouble()))
            val litArea = Area()
            val brightArea = Area()
            val tintContributions = mutableListOf<DynamicLightTintContribution>()
            var lightingActive = false

            masks.filterNotNull().forEach { mask ->
                require(mask.cols == cols && mask.rows == rows) {
                    "Cannot combine light masks with different dimensions."
                }
                litArea.add(mask.copyLitArea())
                brightArea.add(mask.copyBrightArea())
                tintContributions += mask.tintContributions
                lightingActive = lightingActive || mask.lightingActive
            }

            litArea.intersect(mapBounds)
            brightArea.intersect(mapBounds)

            return DynamicLightMask(
                cols = cols,
                rows = rows,
                litArea = litArea,
                brightArea = brightArea,
                tintContributions = tintContributions,
                lightingActive = lightingActive,
            )
        }
    }
}

private fun addPointLightContribution(
    litArea: Area,
    brightArea: Area,
    tintContributions: MutableList<DynamicLightTintContribution>,
    colorHex: String,
    x: Double,
    y: Double,
    dimRadius: Double,
    dimContribution: Area,
    brightContribution: Area,
) {
    if (!dimContribution.isEmpty) {
        litArea.add(dimContribution)
    }
    if (!brightContribution.isEmpty) {
        brightArea.add(brightContribution)
        litArea.add(brightContribution)
    }
    if (!dimContribution.isEmpty || !brightContribution.isEmpty) {
        tintContributions += DynamicLightTintContribution(
            colorHex = colorHex,
            x = x,
            y = y,
            dimRadius = dimRadius,
            dimArea = dimContribution,
            brightArea = brightContribution,
        )
    }
}

internal class DynamicLightTintContribution(
    val colorHex: String,
    val x: Double,
    val y: Double,
    val dimRadius: Double,
    private val dimArea: Area,
    private val brightArea: Area,
) {
    val hasDimTint: Boolean
        get() = !dimArea.isEmpty

    val hasBrightTint: Boolean
        get() = !brightArea.isEmpty

    fun containsDimPoint(x: Double, y: Double): Boolean = dimArea.contains(x, y)

    fun containsBrightPoint(x: Double, y: Double): Boolean = brightArea.contains(x, y)

    fun drawDimArea(
        moveTo: (DynamicSightPoint) -> Unit,
        lineTo: (DynamicSightPoint) -> Unit,
        closePath: () -> Unit,
    ) {
        drawDynamicAreaPath(dimArea, moveTo, lineTo, closePath)
    }

    fun drawBrightArea(
        moveTo: (DynamicSightPoint) -> Unit,
        lineTo: (DynamicSightPoint) -> Unit,
        closePath: () -> Unit,
    ) {
        drawDynamicAreaPath(brightArea, moveTo, lineTo, closePath)
    }
}

private fun hardLightGeometryFor(bundle: DynamicMapBundle): DynamicSightlineGeometry =
    DynamicSightlineGeometry.forMap(
        cols = bundle.cols,
        rows = bundle.rows,
        walls = bundle.walls.filter { it.blocksDimLightWhenClosed() },
    )

private fun dimLightContribution(
    x: Double,
    y: Double,
    radius: Double,
    dimLightGeometry: DynamicSightlineGeometry,
    hasHardWalls: Boolean,
): Area =
    when {
        !radius.isFinite() || radius <= 0.0 -> Area()
        hasHardWalls -> dimLightGeometry.visibleAreaFromPoint(x = x, y = y, radius = radius)
        else -> circleArea(x, y, radius)
    }

private fun circleArea(x: Double, y: Double, radius: Double): Area {
    if (!x.isFinite() || !y.isFinite() || !radius.isFinite() || radius <= 0.0) return Area()
    return Area(Ellipse2D.Double(x - radius, y - radius, radius * 2.0, radius * 2.0))
}

private fun sunlightAreaToArea(area: DynamicMapRuntimeSunlightArea): Area {
    val points = area.points.filter { it.x.isFinite() && it.y.isFinite() }
    if (points.size < 3) return Area()

    val path = Path2D.Double()
    val first = points.first()
    path.moveTo(first.x, first.y)
    points.drop(1).forEach { point ->
        path.lineTo(point.x, point.y)
    }
    path.closePath()
    return Area(path)
}
