package com.tabletopcontrol.map

import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.map.logic.GridCalibration
import com.tabletopcontrol.map.logic.MapCalibration
import com.tabletopcontrol.map.logic.TableMapOffset
import com.tabletopcontrol.map.logic.Token
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
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
                mode = MapFogSceneMode.PARTIAL,
                revealedCells = listOf(Pair(1, 1), Pair(2, 0)),
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
}
