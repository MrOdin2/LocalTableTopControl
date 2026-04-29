package com.tabletopcontrol.dynamicmap.runtime.logic

import java.awt.geom.Area
import java.awt.geom.Ellipse2D

internal data class DynamicPcSightlineContribution(
    val token: Token,
    val contribution: DynamicSightlineContribution,
)

internal fun DynamicSightlineGeometry.darkvisionMeshFrom(
    pcSightlines: Iterable<DynamicPcSightlineContribution>,
): DynamicSightlineMesh {
    val darkvisionArea = Area()
    pcSightlines.forEach { snapshot ->
        val radius = snapshot.token.darkvisionRangeCells
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return@forEach
        val origin = snapshot.token.sightOrigin()
        val contributionArea = snapshot.contribution.copyVisibleArea().apply {
            intersect(
                Area(
                    Ellipse2D.Double(
                        origin.x - radius,
                        origin.y - radius,
                        radius * 2.0,
                        radius * 2.0,
                    ),
                ),
            )
        }
        darkvisionArea.add(contributionArea)
    }
    return meshFromVisibleArea(darkvisionArea)
}
