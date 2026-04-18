package com.tabletopcontrol.new_tracker.model


class ActorTracker(
    val actorList: MutableList<Actor> = mutableListOf(),
){

    var currentlyActive: Int = 0
    var roundCount: Int = 0

    private var activeActors: Int = 0

    fun addActor(actor: Actor) {
        actorList.add(actor)
        if(actor.initiative != null){
            activeActors++
            sortActorsByInitiative()
        }
        normalizeCurrentSelection()
    }

    fun duplicateActor(actor: Actor): Actor {
        val newActor = actor.duplicateActor()
        addActor(newActor)
        return newActor
    }

    fun removeActor(actor: Actor) {
        if(actor.initiative != null){
            activeActors--
        }
        actorList.remove(actor)
        normalizeCurrentSelection()
    }

    fun updateActor(updatedActor: Actor): Boolean {
        val index = actorList.indexOfFirst { it.id == updatedActor.id }
        if (index != -1) {
            val previousActor = actorList[index]

            actorList[index] = updatedActor
            if (previousActor.initiative != updatedActor.initiative) {
                if(previousActor.initiative != null && updatedActor.initiative == null) {
                    activeActors--
                }else if(previousActor.initiative == null) {
                    activeActors++
                }

                sortActorsByInitiative()
                normalizeCurrentSelection()
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
    }

    fun findActor(actorId: String): Actor? = actorList.firstOrNull { it.id == actorId }

    private fun sortActorsByInitiative() {
        actorList.sortWith(compareByDescending<Actor> { it.initiative ?: Int.MIN_VALUE })
    }

    fun next(){
        if (activeActors == 0) {
            currentlyActive = 0
            roundCount = 0
            return
        }

        roundCount += if (currentlyActive == activeActors - 1) 1 else 0
        currentlyActive = (currentlyActive + 1) % activeActors
    }

    fun getCurrentActor(): Actor? =
        if (activeActors == 0 || currentlyActive >= actorList.size) {
            null
        } else {
            actorList[currentlyActive]
        }

    private fun normalizeCurrentSelection() {
        if (activeActors == 0) {
            currentlyActive = 0
            return
        }

        currentlyActive = currentlyActive.coerceIn(0, activeActors - 1)
    }

}

