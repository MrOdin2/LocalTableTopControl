package com.tabletopcontrol.new_tracker.model

import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.TokenAddedEvent
import javafx.scene.paint.Color
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ActorTrackerTest {

    @AfterEach
    fun tearDown() {
        EventBus.clear()
    }

    @Test
    fun `adding an actor with matching initiative can place the new actor first`() {
        val tracker = ActorTracker()
        tracker.addActor(actor("Alpha", 15))
        tracker.addActor(actor("Bravo", 15))
        val charlie = actor("Charlie", 15)

        tracker.addActor(charlie) { actorsAtInitiative, initiative, movedActorId ->
            assertEquals(15, initiative)
            assertEquals(charlie.id, movedActorId)
            assertEquals(listOf("Alpha", "Bravo", "Charlie"), actorsAtInitiative.map(Actor::name))
            listOf(actorsAtInitiative[2], actorsAtInitiative[0], actorsAtInitiative[1])
        }

        assertEquals(listOf("Charlie", "Alpha", "Bravo"), tracker.actorList.map(Actor::name))
    }

    @Test
    fun `adding an actor with matching initiative keeps existing order when dialog is cancelled`() {
        val tracker = ActorTracker()
        tracker.addActor(actor("Alpha", 15))
        tracker.addActor(actor("Bravo", 15))

        tracker.addActor(actor("Charlie", 15)) { _, _, _ ->
            null
        }

        assertEquals(listOf("Alpha", "Bravo", "Charlie"), tracker.actorList.map(Actor::name))
    }

    @Test
    fun `updating an actor into a tie resolves the full initiative group`() {
        val tracker = ActorTracker()
        tracker.addActor(actor("Alpha", 18))
        tracker.addActor(actor("Bravo", 15))
        tracker.addActor(actor("Charlie", 15))
        val delta = actor("Delta", 10)
        tracker.addActor(delta)

        val updated = tracker.updateActor(
            tracker.actorList.first { it.id == delta.id }.copy(initiative = 15),
        ) { actorsAtInitiative, initiative, movedActorId ->
            assertEquals(15, initiative)
            assertEquals(delta.id, movedActorId)
            assertEquals(listOf("Bravo", "Charlie", "Delta"), actorsAtInitiative.map(Actor::name))
            listOf(actorsAtInitiative[2], actorsAtInitiative[0], actorsAtInitiative[1])
        }

        assertEquals(true, updated)
        assertEquals(
            listOf("Alpha", "Delta", "Bravo", "Charlie"),
            tracker.actorList.map(Actor::name),
        )
    }

    @Test
    fun `initiative tie resolution can include actors that join while resolver is open`() {
        val tracker = ActorTracker()
        val alpha = actor("Alpha", 15)
        val bravo = actor("Bravo", null)
        val charlie = actor("Charlie", null)
        tracker.addActor(alpha)
        tracker.addActor(bravo)
        tracker.addActor(charlie)

        tracker.updateActor(bravo.copy(initiative = 15)) { actorsAtInitiative, initiative, movedActorId ->
            assertEquals(15, initiative)
            assertEquals(bravo.id, movedActorId)
            assertEquals(listOf("Alpha", "Bravo"), actorsAtInitiative.map(Actor::name))

            tracker.updateActor(charlie.copy(initiative = 15)) { expandedActorsAtInitiative, expandedInitiative, expandedMovedActorId ->
                assertEquals(15, expandedInitiative)
                assertEquals(charlie.id, expandedMovedActorId)
                assertEquals(listOf("Alpha", "Bravo", "Charlie"), expandedActorsAtInitiative.map(Actor::name))
                expandedActorsAtInitiative
            }

            val expandedTie = tracker.actorList.filter { it.initiative == 15 }
            listOf(
                expandedTie.first { it.name == "Charlie" },
                expandedTie.first { it.name == "Bravo" },
                expandedTie.first { it.name == "Alpha" },
            )
        }

        assertEquals(listOf("Charlie", "Bravo", "Alpha"), tracker.actorList.map(Actor::name))
    }

    @Test
    fun `cancelled initiative tie keeps actors that joined while resolver was open`() {
        val tracker = ActorTracker()
        val alpha = actor("Alpha", 15)
        val bravo = actor("Bravo", null)
        val charlie = actor("Charlie", null)
        tracker.addActor(alpha)
        tracker.addActor(bravo)
        tracker.addActor(charlie)

        tracker.updateActor(bravo.copy(initiative = 15)) { _, _, _ ->
            tracker.updateActor(charlie.copy(initiative = 15))
            null
        }

        assertEquals(listOf("Alpha", "Bravo", "Charlie"), tracker.actorList.map(Actor::name))
    }

    @Test
    fun `adding a pc actor marks its token as player controlled`() {
        val tracker = ActorTracker()
        val tokenEvents = mutableListOf<TokenAddedEvent>()
        EventBus.subscribe<TokenAddedEvent> { tokenEvents += it }

        tracker.addActor(Actor(name = "Hero", actorType = ActorType.PC))

        assertEquals(true, tokenEvents.single().isPlayerCharacter)
    }

    @Test
    fun `player initiative requirement ignores npcs without initiative`() {
        val tracker = ActorTracker()
        tracker.addActor(Actor(name = "Goblin", actorType = ActorType.NPC))

        assertEquals(false, tracker.hasPlayerCharactersMissingInitiative())

        val hero = Actor(name = "Hero", actorType = ActorType.PC)
        tracker.addActor(hero)

        assertEquals(true, tracker.hasPlayerCharactersMissingInitiative())

        tracker.updateActor(hero.copy(initiative = 14))

        assertEquals(false, tracker.hasPlayerCharactersMissingInitiative())
    }

    @Test
    fun `adding an actor publishes darkvision range in grid cells`() {
        val tracker = ActorTracker()
        val tokenEvents = mutableListOf<TokenAddedEvent>()
        EventBus.subscribe<TokenAddedEvent> { tokenEvents += it }

        tracker.addActor(
            Actor(
                name = "Scout",
                features = ActorFeatures(
                    darkvisionRange = DistanceRange(60, DistanceUnit.FEET),
                ),
            ),
        )

        assertEquals(12.0, tokenEvents.single().darkvisionRangeCells)
    }

    @Test
    fun `changing darkvision republishes token metadata`() {
        val tracker = ActorTracker()
        val actor = Actor(name = "Scout")
        tracker.addActor(actor)
        val tokenEvents = mutableListOf<TokenAddedEvent>()
        EventBus.subscribe<TokenAddedEvent> { tokenEvents += it }

        tracker.updateActor(
            actor.copy(
                color = tracker.actorList.single().color,
                features = ActorFeatures(
                    darkvisionRange = DistanceRange(9, DistanceUnit.METERS),
                ),
            ),
        )

        assertEquals(1, tokenEvents.size)
        assertEquals(6.0, tokenEvents.single().darkvisionRangeCells)
    }

    @Test
    fun `adding an actor publishes light source ranges in grid cells`() {
        val tracker = ActorTracker()
        val tokenEvents = mutableListOf<TokenAddedEvent>()
        EventBus.subscribe<TokenAddedEvent> { tokenEvents += it }

        tracker.addActor(
            Actor(
                name = "Torchbearer",
                actorType = ActorType.PC,
                features = ActorFeatures(
                    lightSource = ActorLightSource(
                        brightRange = DistanceRange(20, DistanceUnit.FEET),
                        dimRange = DistanceRange(9, DistanceUnit.METERS),
                        color = Color.web("#FFD37A"),
                    ),
                ),
            ),
        )

        val lightSource = requireNotNull(tokenEvents.single().lightSource)
        assertEquals(4.0, lightSource.brightRangeCells)
        assertEquals(6.0, lightSource.dimRangeCells)
        assertEquals("#FFD37A", lightSource.colorHex)
    }

    @Test
    fun `changing light source republishes token metadata`() {
        val tracker = ActorTracker()
        val actor = Actor(name = "Torchbearer", actorType = ActorType.PC)
        tracker.addActor(actor)
        val tokenEvents = mutableListOf<TokenAddedEvent>()
        EventBus.subscribe<TokenAddedEvent> { tokenEvents += it }

        tracker.updateActor(
            actor.copy(
                color = tracker.actorList.single().color,
                features = ActorFeatures(
                    lightSource = ActorLightSource(
                        brightRange = DistanceRange(15, DistanceUnit.FEET),
                        dimRange = DistanceRange(30, DistanceUnit.FEET),
                        color = Color.web("#88CCFF"),
                    ),
                ),
            ),
        )

        assertEquals(1, tokenEvents.size)
        val lightSource = requireNotNull(tokenEvents.single().lightSource)
        assertEquals(3.0, lightSource.brightRangeCells)
        assertEquals(6.0, lightSource.dimRangeCells)
        assertEquals("#88CCFF", lightSource.colorHex)
    }

    @Test
    fun `changing actor type republishes token metadata`() {
        val tracker = ActorTracker()
        val actor = Actor(name = "Hero")
        tracker.addActor(actor)
        val tokenEvents = mutableListOf<TokenAddedEvent>()
        EventBus.subscribe<TokenAddedEvent> { tokenEvents += it }

        tracker.updateActor(actor.copy(actorType = ActorType.PC, color = tracker.actorList.single().color))

        assertEquals(1, tokenEvents.size)
        assertEquals(true, tokenEvents.single().isPlayerCharacter)
    }

    @Test
    fun `movement dash stacks and resets when the actor becomes active again`() {
        val tracker = ActorTracker()
        tracker.addActor(
            Actor(
                id = "hero",
                name = "Hero",
                initiative = 12,
                actorType = ActorType.PC,
                features = ActorFeatures(movementRange = DistanceRange(30, DistanceUnit.FEET)),
            ),
        )
        tracker.addActor(Actor(id = "guard", name = "Guard", initiative = 10))

        assertEquals(6, tracker.movementBudget("hero")?.remainingMovementCells)

        tracker.dashActorMovement("hero")
        tracker.dashActorMovement("hero")
        tracker.spendActorMovement("hero", 2)

        assertEquals(16, tracker.movementBudget("hero")?.remainingMovementCells)

        tracker.next()
        tracker.next()

        assertEquals(6, tracker.movementBudget("hero")?.remainingMovementCells)
    }

    private fun actor(name: String, initiative: Int?): Actor =
        Actor(name = name, initiative = initiative)
}
