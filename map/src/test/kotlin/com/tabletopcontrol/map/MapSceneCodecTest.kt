package com.tabletopcontrol.map

import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.map.logic.GridCalibration
import com.tabletopcontrol.map.logic.MapCalibration
import com.tabletopcontrol.map.logic.TableMapOffset
import com.tabletopcontrol.map.logic.Token
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapSceneCodecTest {
    @Test
    fun `serialize and deserialize preserve map fog and token state`() {
        val state = MapSceneState(
            mapImageUri = "file:///maps/cave.png",
            mapDisplayPath = "C:\\maps\\cave.png",
            mapCalibration = MapCalibration(scale = 1.5, offsetX = 10.0, offsetY = -5.0),
            gridCalibration = GridCalibration(cellSizeInPixels = 70.0, scale = 1.1, offsetX = 3.0, offsetY = 9.0),
            gridColor = Color.color(1.0, 1.0, 1.0, 0.5),
            backgroundColor = Color.DARKSLATEGRAY,
            mapRotation = 180,
            tableMapOffset = TableMapOffset(offsetX = 12.0, offsetY = -24.0),
            gridVisible = true,
            showTokenNames = true,
            fog = MapFogSceneState(
                cols = 4,
                rows = 3,
                colOffset = -2,
                rowOffset = -1,
                mode = MapFogSceneMode.PARTIAL_HIDDEN,
                cells = listOf(Pair(1, 1), Pair(2, 0)),
            ),
            tokens = listOf(
                Token(
                    id = "token-1",
                    name = "Hero",
                    col = 8,
                    row = 5,
                    size = TokenSize.LARGE,
                    color = Color.CORNFLOWERBLUE,
                    imageUri = "file:///tokens/hero.png",
                    imageScaleX = 1.3,
                    imageScaleY = 1.0,
                    imageOffsetX = 5.0,
                    imageOffsetY = -3.0,
                ),
            ),
            activeTokenId = "token-1",
        )

        val restored = MapSceneCodec.deserialize(MapSceneCodec.serialize(state))

        assertEquals(state, restored)
    }

    @Test
    fun `serialize stores whichever fog cell list is shorter`() {
        val state = MapSceneState(
            mapImageUri = null,
            mapDisplayPath = null,
            mapCalibration = MapCalibration(),
            gridCalibration = GridCalibration(),
            gridColor = Color.WHITE,
            backgroundColor = Color.BLACK,
            mapRotation = 0,
            tableMapOffset = TableMapOffset(),
            gridVisible = false,
            showTokenNames = false,
            fog = MapFogSceneState(
                cols = 4,
                rows = 3,
                colOffset = 0,
                rowOffset = 0,
                mode = MapFogSceneMode.PARTIAL_HIDDEN,
                cells = listOf(Pair(0, 0), Pair(3, 2)),
            ),
            tokens = emptyList(),
            activeTokenId = null,
        )

        val serialized = MapSceneCodec.serialize(state)

        assertTrue(serialized.contains("fog.mode=PARTIAL_HIDDEN"))
        assertTrue(serialized.contains("fog.cells.count=2"))
        assertTrue(serialized.contains("fog.cells.0=0,0"))
        assertTrue(serialized.contains("fog.cells.1=3,2"))
    }

    @Test
    fun `deserialize supports legacy partial fog scenes`() {
        val legacy = """
            version=1
            map.scale=1.0
            map.offsetX=0.0
            map.offsetY=0.0
            grid.cellSizeInPixels=50.0
            grid.scale=1.0
            grid.offsetX=0.0
            grid.offsetY=0.0
            grid.color=#FFFFFF
            background.color=#000000
            map.rotation=0
            table.offsetX=0.0
            table.offsetY=0.0
            grid.visible=false
            tokens.showNames=false
            token.count=0
            fog.present=true
            fog.cols=4
            fog.rows=3
            fog.colOffset=-2
            fog.rowOffset=-1
            fog.mode=PARTIAL
            fog.revealed.count=2
            fog.revealed.0=1,1
            fog.revealed.1=2,0
        """.trimIndent()

        val restored = requireNotNull(MapSceneCodec.deserialize(legacy))

        assertEquals(
            MapFogSceneState(
                cols = 4,
                rows = 3,
                colOffset = -2,
                rowOffset = -1,
                mode = MapFogSceneMode.PARTIAL_REVEALED,
                cells = listOf(Pair(1, 1), Pair(2, 0)),
            ),
            restored.fog,
        )
    }
}
