package com.tabletopcontrol.new_tracker.model

import com.tabletopcontrol.core.ActiveTokenChangedEvent
import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.TokenAddedEvent
import com.tabletopcontrol.core.TokenImageChangedEvent
import com.tabletopcontrol.core.TokenRemovedEvent
import com.tabletopcontrol.core.TokensResetEvent
import javafx.scene.paint.Color

enum class InitiativeTieDecision {
    NEW_ACTOR_FIRST,
    EXISTING_ACTOR_FIRST,
}

typealias InitiativeTieResolver = (newActor: Actor, existingActor: Actor) -> InitiativeTieDecision

private val KEEP_EXISTING_TIE_ORDER: InitiativeTieResolver =
    { _, _ -> InitiativeTieDecision.EXISTING_ACTOR_FIRST }

class ActorTracker(
    val actorList: MutableList<Actor> = mutableListOf(),
){

    var currentlyActive: Int = 0
    var roundCount: Int = 0

    private var activeActors: Int = 0

    fun addActor(
        actor: Actor,
        tieResolver: InitiativeTieResolver = KEEP_EXISTING_TIE_ORDER,
    ) {
        val currentActorId = getCurrentActor()?.id
        actor.color = TOKEN_COLORS[actorList.size]
        actorList.add(actor)
        EventBus.publish(TokenAddedEvent(actor.id, actor.name, actor.color))
        if (actor.imageSettings.uri != null) {
            publishImageEvent(actor)
        }
        if(actor.initiative != null){
            activeActors++
            sortActorsByInitiative(actor.id, tieResolver)
        }
        normalizeCurrentSelection(currentActorId)
    }

    fun duplicateActor(
        actor: Actor,
        tieResolver: InitiativeTieResolver = KEEP_EXISTING_TIE_ORDER,
    ): Actor {
        val newActor = actor.duplicateActor()
        addActor(newActor, tieResolver)
        return newActor
    }

    fun removeActor(actor: Actor) {
        val currentActorId = getCurrentActor()?.id?.takeUnless { it == actor.id }
        if(actor.initiative != null){
            activeActors--
        }
        actorList.remove(actor)
        EventBus.publish(TokenRemovedEvent(actor.id, actor.name))
        normalizeCurrentSelection(currentActorId)
    }

    fun updateActor(
        updatedActor: Actor,
        tieResolver: InitiativeTieResolver = KEEP_EXISTING_TIE_ORDER,
    ): Boolean {
        val index = actorList.indexOfFirst { it.id == updatedActor.id }
        if (index != -1) {
            val previousActor = actorList[index]
            if (previousActor.imageSettings != updatedActor.imageSettings) {
                publishImageEvent(updatedActor)
            }
            val currentActorId = getCurrentActor()?.id
            actorList[index] = updatedActor
            if (previousActor.initiative != updatedActor.initiative) {
                if(previousActor.initiative != null && updatedActor.initiative == null) {
                    activeActors--
                }else if(previousActor.initiative == null) {
                    activeActors++
                }

                sortActorsByInitiative(updatedActor.id, tieResolver)
                normalizeCurrentSelection(currentActorId)
                return true
            }
        }
        normalizeCurrentSelection()
        return false
    }

    fun removeAllActors() {
        actorList.clear()
        activeActors = 0
        currentlyActive = 0
        roundCount = 0
        EventBus.publish(TokensResetEvent())
        EventBus.publish(ActiveTokenChangedEvent(null, null)
        )
    }

    fun findActor(actorId: String): Actor? = actorList.firstOrNull { it.id == actorId }

    private fun sortActorsByInitiative(
        actorId: String,
        tieResolver: InitiativeTieResolver,
    ) {
        val currentIndex = actorList.indexOfFirst { it.id == actorId }
        if (currentIndex == -1) {
            return
        }

        val actor = actorList.removeAt(currentIndex)
        val insertIndex = findInsertIndex(actor, tieResolver)
        actorList.add(insertIndex, actor)
    }

    private fun findInsertIndex(
        actor: Actor,
        tieResolver: InitiativeTieResolver,
    ): Int {
        val initiative = actor.initiative ?: return actorList.size

        actorList.forEachIndexed { index, other ->
            val otherInitiative = other.initiative
            when {
                otherInitiative == null -> return index
                otherInitiative > initiative -> Unit
                otherInitiative < initiative -> return index
                tieResolver(actor, other) == InitiativeTieDecision.NEW_ACTOR_FIRST -> return index
            }
        }

        return actorList.size
    }

    fun next(){
        if (activeActors == 0) {
            currentlyActive = 0
            roundCount = 0
            return
        }

        roundCount += if (currentlyActive == activeActors - 1) 1 else 0
        currentlyActive = (currentlyActive + 1) % activeActors

        EventBus.publish(
            ActiveTokenChangedEvent(
                actorList[currentlyActive].id,
                actorList[currentlyActive].name,
            ),
        )
    }

    fun getCurrentActor(): Actor? =
        if (activeActors == 0 || currentlyActive >= actorList.size) {
            null
        } else {
            actorList[currentlyActive]
        }

    private fun normalizeCurrentSelection(preferredActorId: String? = null) {
        if (activeActors == 0) {
            currentlyActive = 0
            return
        }

        if (preferredActorId != null) {
            val preferredIndex = actorList.indexOfFirst { it.id == preferredActorId }
            if (preferredIndex in 0 until activeActors) {
                currentlyActive = preferredIndex
                return
            }
        }

        currentlyActive = currentlyActive.coerceIn(0, activeActors - 1)
    }

    private fun publishImageEvent(actor: Actor) {
        val settings = actor.imageSettings
        EventBus.publish(
            TokenImageChangedEvent(
                id = actor.id,
                imageUri = settings.uri,
                imageScaleX = settings.scaleX,
                imageScaleY = settings.scaleY,
                imageOffsetX = settings.offsetX,
                imageOffsetY = settings.offsetY,
            ),
        )
    }

    private val TOKEN_COLORS: List<Color> = run {
        val hues = List(16) { it * 22.5 }
        val variants = listOf(
            Pair(1.00, 0.90),   // vivid
            Pair(0.55, 1.00),   // light
            Pair(1.00, 0.55),   // dark
            Pair(0.45, 0.80),   // muted
        )
        List(64) { i -> Color.hsb(hues[i % 16], variants[i / 16].first, variants[i / 16].second) }
    }
}

