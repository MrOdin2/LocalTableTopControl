package com.tabletopcontrol.new_tracker.scene

import com.tabletopcontrol.core.TokenSize
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorFeatures
import com.tabletopcontrol.new_tracker.model.ActorLightSource
import com.tabletopcontrol.new_tracker.model.ActorType
import com.tabletopcontrol.new_tracker.model.ActorImageSettings
import com.tabletopcontrol.new_tracker.model.DistanceRange
import com.tabletopcontrol.new_tracker.model.DistanceUnit
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
                    actorType = ActorType.PC,
                    features = ActorFeatures(
                        darkvisionRange = DistanceRange(60, DistanceUnit.FEET),
                        movementRange = DistanceRange(9, DistanceUnit.METERS),
                        lightSource = ActorLightSource(
                            brightRange = DistanceRange(20, DistanceUnit.FEET),
                            dimRange = DistanceRange(12, DistanceUnit.METERS),
                            color = Color.web("#FFD37A"),
                        ),
                    ),
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

    @Test
    fun `serialize uses readable tracker properties`() {
        val state = TrackerSceneState(
            actors = listOf(
                Actor(
                    id = "actor-1",
                    name = "Goblin Boss",
                    hp = 21,
                    ac = 17,
                    initiative = 19,
                    tokenSize = TokenSize.LARGE,
                    actorType = ActorType.PC,
                    features = ActorFeatures(
                        darkvisionRange = DistanceRange(60, DistanceUnit.FEET),
                        movementRange = DistanceRange(9, DistanceUnit.METERS),
                        lightSource = ActorLightSource(
                            brightRange = DistanceRange(20, DistanceUnit.FEET),
                            dimRange = DistanceRange(12, DistanceUnit.METERS),
                            color = Color.web("#FFD37A"),
                        ),
                    ),
                    color = Color.DARKRED,
                    imageSettings = ActorImageSettings(uri = "file:///tokens/goblin.png"),
                ),
            ),
            activeActorId = "actor-1",
            roundCount = 3,
        )

        val serialized = TrackerSceneCodec.serialize(state)

        assertTrue(serialized.contains("version=5"))
        assertTrue(serialized.contains("actor.0.name=Goblin Boss"))
        assertTrue(serialized.contains("actor.0.actorType=PC"))
        assertTrue(serialized.contains("actor.0.darkvisionRange=60"))
        assertTrue(serialized.contains("actor.0.movementUnit=METERS"))
        assertTrue(serialized.contains("actor.0.lightBrightRange=20"))
        assertTrue(serialized.contains("actor.0.lightDimUnit=METERS"))
        assertTrue(serialized.contains("actor.0.lightColor=\\#FFD37A"))
        assertTrue(serialized.contains("actor.0.imageUri=file\\:///tokens/goblin.png"))
    }

    @Test
    fun `deserialize version two tracker scenes defaults actor type to npc`() {
        val serialized = """
            #TabletopControl tracker scene
            version=2
            roundCount=1
            actor.count=1
            actor.0.id=actor-1
            actor.0.name=Goblin
            actor.0.hp=7
            actor.0.ac=15
            actor.0.initiative=12
            actor.0.tokenSize=MEDIUM
            actor.0.color=#008000
            actor.0.imageScaleX=1.0
            actor.0.imageScaleY=1.0
            actor.0.imageOffsetX=0.0
            actor.0.imageOffsetY=0.0
        """.trimIndent()

        val restored = requireNotNull(TrackerSceneCodec.deserialize(serialized))

        assertEquals(ActorType.NPC, restored.actors.single().actorType)
        assertEquals(ActorFeatures(), restored.actors.single().features)
    }

    @Test
    fun `deserialize still supports legacy tracker scenes`() {
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

        val restored = TrackerSceneCodec.deserialize(TrackerSceneCodec.serializeLegacy(state))

        assertEquals(state, restored)
    }
}
