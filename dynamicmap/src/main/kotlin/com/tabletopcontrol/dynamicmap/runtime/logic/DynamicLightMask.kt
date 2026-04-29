package com.tabletopcontrol.dynamicmap.runtime.logic

import com.tabletopcontrol.dynamicmap.runtime.DynamicMapBundle
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapRuntimeSunlightArea
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

internal class DynamicLightMask(
    val cols: Int,
    val rows: Int,
    private val litArea: Area,
    val lightingActive: Boolean,
) {
    init {
        require(cols > 0) { "cols must be positive, was $cols" }
        require(rows > 0) { "rows must be positive, was $rows" }
    }

    fun copyLitArea(): Area = Area(litArea)

    fun containsPoint(x: Double, y: Double): Boolean =
        x in 0.0..cols.toDouble() &&
            y in 0.0..rows.toDouble() &&
            litArea.contains(x, y)

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
        ): DynamicLightMask {
            val mapBounds = Area(Rectangle2D.Double(0.0, 0.0, bundle.cols.toDouble(), bundle.rows.toDouble()))
            val litArea = Area()
            val hasAuthoredLighting = bundle.lights.isNotEmpty() || bundle.sunlightAreas.isNotEmpty()

            bundle.lights
                .filter { it.enabled && it.dimRadius > 0.0 }
                .forEach { light ->
                    litArea.add(
                        geometry.visibleAreaFromPoint(
                            x = light.position.x,
                            y = light.position.y,
                            radius = light.dimRadius,
                        ),
                    )
                }

            bundle.sunlightAreas
                .map(::sunlightAreaToArea)
                .forEach(litArea::add)

            litArea.intersect(mapBounds)

            return DynamicLightMask(
                cols = bundle.cols,
                rows = bundle.rows,
                litArea = litArea,
                lightingActive = hasAuthoredLighting,
            )
        }
    }
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
