package com.tabletopcontrol.new_tracker.model

import java.util.UUID

data class Actor(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hp: Int = 0,
    val ac: Int = 0,
    val initiative: Int? = null,
){
    fun duplicateActor(): Actor = Actor(UUID.randomUUID().toString(), name, hp, ac, initiative)
}
