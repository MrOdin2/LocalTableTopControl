package com.tabletopcontrol.new_tracker.model


class ActorTracker(
    val actorList: MutableList<Actor> = mutableListOf(),
    var currentlyActive: Int = 0
){

    private var activeActors: Int = 0

    fun addActor(actor: Actor) {
        actorList.add(actor)
        if(actor.initiative != null){
            activeActors++
            sortActorsByInitiative()
        }
    }

    fun removeActor(actor: Actor) {
        if(actor.initiative != null){
            activeActors--
        }
        actorList.remove(actor)
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
                return true
            }
        }
        return false
    }

    fun removeAllActors() {
        actorList.clear()
        activeActors = 0
        currentlyActive = 0
    }

    fun findActor(actorId: String): Actor? = actorList.firstOrNull { it.id == actorId }

    private fun sortActorsByInitiative() {
        actorList.sortWith(compareByDescending<Actor> { it.initiative ?: Int.MIN_VALUE })
    }

    fun next(){
        println("currentlyActive: $currentlyActive of $activeActors")
        currentlyActive = (currentlyActive + 1) % (activeActors + 1)
    }

    fun getCurrentActor(): Actor = actorList[currentlyActive]

}

