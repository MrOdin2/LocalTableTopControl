package com.tabletopcontrol.dynamicmap.runtime

import com.tabletopcontrol.core.TokenLightSource
import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicLightMask
import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicPcSightlineContribution
import com.tabletopcontrol.dynamicmap.runtime.logic.DynamicSightlineGeometry
import com.tabletopcontrol.dynamicmap.runtime.logic.Token
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicLightMaskTest {
    @Test
    fun `point light illuminates within dim radius`() {
        val bundle = bundle(
            lights = listOf(
                DynamicMapRuntimeLight(
                    position = DynamicMapRuntimePoint(2.0, 2.0),
                    brightRadius = 1.0,
                    dimRadius = 2.0,
                    colorHex = "#ffffff",
                    enabled = true,
                ),
            ),
        )

        val mask = lightMask(bundle)

        assertTrue(mask.lightingActive)
        assertTrue(mask.containsPoint(2.5, 2.0))
        assertFalse(mask.containsPoint(4.5, 2.0))
    }

    @Test
    fun `walls occlude bright light but dim light leaks softly`() {
        val wall = DynamicMapRuntimeWall(
            start = DynamicMapRuntimePoint(2.0, 0.0),
            end = DynamicMapRuntimePoint(2.0, 5.0),
        )
        val bundle = bundle(
            walls = listOf(wall),
            lights = listOf(
                DynamicMapRuntimeLight(
                    position = DynamicMapRuntimePoint(1.0, 2.0),
                    brightRadius = 1.0,
                    dimRadius = 5.0,
                    colorHex = "#ff8800",
                    enabled = true,
                ),
            ),
        )

        val mask = lightMask(bundle)

        assertTrue(mask.containsPoint(1.5, 2.0))
        assertTrue(mask.containsPoint(3.0, 2.0))
        assertTrue(mask.containsBrightPoint(1.5, 2.0))
        assertFalse(mask.containsBrightPoint(3.0, 2.0))
        assertEquals("#ff8800", mask.tintContributions.single().colorHex)
        assertTrue(mask.tintContributions.single().hasDimTint)
        assertTrue(mask.tintContributions.single().hasBrightTint)
        assertFalse(mask.tintContributions.single().containsBrightPoint(3.0, 2.0))
    }

    @Test
    fun `hard walls occlude dim light using the larger light mesh`() {
        val wall = DynamicMapRuntimeWall(
            start = DynamicMapRuntimePoint(2.0, 0.0),
            end = DynamicMapRuntimePoint(2.0, 5.0),
            kind = DynamicMapRuntimeWallKind.HARD,
        )
        val bundle = bundle(
            walls = listOf(wall),
            lights = listOf(
                DynamicMapRuntimeLight(
                    position = DynamicMapRuntimePoint(1.0, 2.0),
                    brightRadius = 1.0,
                    dimRadius = 5.0,
                    colorHex = "#ff8800",
                    enabled = true,
                ),
            ),
        )

        val mask = lightMask(bundle)

        assertTrue(mask.containsPoint(1.5, 2.0))
        assertFalse(mask.containsPoint(3.0, 2.0))
        assertTrue(mask.tintContributions.single().containsDimPoint(1.5, 2.0))
        assertFalse(mask.tintContributions.single().containsDimPoint(3.0, 2.0))
    }

    @Test
    fun `sunlight polygons are lit without point lights`() {
        val bundle = bundle(
            sunlightAreas = listOf(
                DynamicMapRuntimeSunlightArea(
                    points = listOf(
                        DynamicMapRuntimePoint(1.0, 1.0),
                        DynamicMapRuntimePoint(4.0, 1.0),
                        DynamicMapRuntimePoint(4.0, 3.0),
                        DynamicMapRuntimePoint(1.0, 3.0),
                    ),
                ),
            ),
        )

        val mask = lightMask(bundle)

        assertTrue(mask.lightingActive)
        assertTrue(mask.containsPoint(2.0, 2.0))
        assertFalse(mask.containsPoint(0.5, 2.0))
    }

    @Test
    fun `bundles without authored lighting leave lighting inactive for compatibility`() {
        val mask = lightMask(bundle())

        assertFalse(mask.lightingActive)
    }

    @Test
    fun `lighting clips pc sight to lit areas`() {
        val bundle = bundle(
            lights = listOf(
                DynamicMapRuntimeLight(
                    position = DynamicMapRuntimePoint(1.0, 2.0),
                    brightRadius = 1.0,
                    dimRadius = 1.5,
                    colorHex = "#ffffff",
                    enabled = true,
                ),
            ),
        )
        val geometry = DynamicSightlineGeometry.forMap(
            cols = bundle.cols,
            rows = bundle.rows,
            walls = bundle.walls,
        )
        val sightMesh = geometry.compute(
            listOf(
                Token(
                    id = "pc",
                    name = "Hero",
                    col = 0,
                    row = 1,
                    color = Color.BLUE,
                    isPlayerCharacter = true,
                ),
            ),
        )

        val visibleMesh = lightMask(bundle).applyToSightMesh(geometry, sightMesh)

        assertTrue(sightMesh.containsPoint(4.0, 2.0))
        assertFalse(visibleMesh.containsPoint(4.0, 2.0))
        assertTrue(visibleMesh.containsPoint(1.25, 2.0))
    }

    @Test
    fun `pc token light contributes moving dim and wall occluded bright light`() {
        val wall = DynamicMapRuntimeWall(
            start = DynamicMapRuntimePoint(2.0, 0.0),
            end = DynamicMapRuntimePoint(2.0, 5.0),
        )
        val bundle = bundle(walls = listOf(wall))
        val geometry = DynamicSightlineGeometry.forMap(
            cols = bundle.cols,
            rows = bundle.rows,
            walls = bundle.walls,
        )
        val pc = Token(
            id = "pc",
            name = "Lantern",
            col = 0,
            row = 1,
            color = Color.BLUE,
            isPlayerCharacter = true,
            lightSource = TokenLightSource(
                brightRangeCells = 5.0,
                dimRangeCells = 5.0,
                colorHex = "#ffcc66",
            ),
        )
        val contribution = requireNotNull(geometry.computeContribution(pc))

        val mask = DynamicLightMask.fromPcTokenLights(
            bundle = bundle,
            pcSightlines = listOf(DynamicPcSightlineContribution(pc, contribution)),
        )

        assertTrue(mask.lightingActive)
        assertTrue(mask.containsPoint(3.0, 1.5))
        assertTrue(mask.containsBrightPoint(1.5, 1.5))
        assertFalse(mask.containsBrightPoint(3.0, 1.5))
        assertEquals("#ffcc66", mask.tintContributions.single().colorHex)
    }

    @Test
    fun `pc token light dim range is occluded by hard walls`() {
        val wall = DynamicMapRuntimeWall(
            start = DynamicMapRuntimePoint(2.0, 0.0),
            end = DynamicMapRuntimePoint(2.0, 5.0),
            kind = DynamicMapRuntimeWallKind.HARD,
        )
        val bundle = bundle(walls = listOf(wall))
        val geometry = DynamicSightlineGeometry.forMap(
            cols = bundle.cols,
            rows = bundle.rows,
            walls = bundle.walls,
        )
        val pc = Token(
            id = "pc",
            name = "Lantern",
            col = 0,
            row = 1,
            color = Color.BLUE,
            isPlayerCharacter = true,
            lightSource = TokenLightSource(
                brightRangeCells = 1.0,
                dimRangeCells = 5.0,
                colorHex = "#ffcc66",
            ),
        )
        val contribution = requireNotNull(geometry.computeContribution(pc))

        val mask = DynamicLightMask.fromPcTokenLights(
            bundle = bundle,
            pcSightlines = listOf(DynamicPcSightlineContribution(pc, contribution)),
        )

        assertTrue(mask.containsPoint(1.5, 1.5))
        assertFalse(mask.containsPoint(3.0, 1.5))
    }

    private fun lightMask(bundle: DynamicMapBundle): DynamicLightMask =
        DynamicLightMask.fromBundle(
            bundle = bundle,
            geometry = DynamicSightlineGeometry.forMap(
                cols = bundle.cols,
                rows = bundle.rows,
                walls = bundle.walls,
            ),
        )

    private fun bundle(
        walls: List<DynamicMapRuntimeWall> = emptyList(),
        lights: List<DynamicMapRuntimeLight> = emptyList(),
        sunlightAreas: List<DynamicMapRuntimeSunlightArea> = emptyList(),
    ): DynamicMapBundle =
        DynamicMapBundle(
            sourcePath = "test.dynamicmap",
            displayPath = "test.dynamicmap",
            cols = 6,
            rows = 5,
            backgroundEntry = null,
            backgroundBytes = null,
            backgroundCalibration = DynamicMapRuntimeBackgroundCalibration(),
            walls = walls,
            lights = lights,
            sunlightAreas = sunlightAreas,
        )
}
