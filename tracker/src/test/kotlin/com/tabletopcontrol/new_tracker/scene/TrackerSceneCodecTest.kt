package com.tabletopcontrol.new_tracker.scene

import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorImageSettings
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrackerSceneCodecTest {
    @Test
    fun `serialize and deserialize preserve actor encounter state`() {
        val state = TrackerSceneState(
            actors = listOf(
                Actor(
                    id = "actor-1",
                    name = "Goblin Boss",
                    hp = 21,
                    ac = 17,
                    initiative = 19,
                    tokenSize = TokenSize.LARGE,
                    color = Color.DARKRED,
                    imageSettings = ActorImageSettings(
                        uri = "file:///tokens/goblin.png",
                        scaleX = 1.2,
                        scaleY = 1.1,
                        offsetX = 4.0,
                        offsetY = -2.0,
                    ),
                ),
            ),
            activeActorId = "actor-1",
            roundCount = 3,
        )

        val restored = TrackerSceneCodec.deserialize(TrackerSceneCodec.serialize(state))

        assertEquals(state, restored)
    }
}
