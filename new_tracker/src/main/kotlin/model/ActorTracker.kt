package com.tabletopcontrol.new_tracker.model


class ActorTracker(
    val actorList: MutableList<Actor> = mutableListOf(),
){

    fun addActor(actor: Actor) {
        actorList.add(actor)
        actorList.sortBy { it -> it.initiative }
    }

    fun removeActor(actor: Actor) {
        actorList.remove(actor)
    }

    fun updateActor(updatedActor: Actor) {
        val index = actorList.indexOfFirst { it.id == updatedActor.id }
        if (index != -1) {
            actorList[index] = updatedActor
        }
    }

    fun removeAllActors() {
        actorList.clear()
    }

}

