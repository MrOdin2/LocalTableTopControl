package com.tabletopcontrol.new_tracker.model

import com.tabletopcontrol.core.ActiveTokenChangedEvent
import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.TokenAddedEvent
import com.tabletopcontrol.core.TokenEffectsChangedEvent
import com.tabletopcontrol.core.TokenImageChangedEvent
import com.tabletopcontrol.core.TokenLightSource
import com.tabletopcontrol.core.TokenMovementBudgetChangedEvent
import com.tabletopcontrol.core.TokenRemovedEvent
import com.tabletopcontrol.core.TokensResetEvent
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.new_tracker.scene.TrackerSceneState
import javafx.scene.paint.Color
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

typealias InitiativeTieResolver = (
    actorsAtInitiative: List<Actor>,
    initiative: Int,
    movedActorId: String,
) -> List<Actor>?

data class ActorMovementBudget(
    val baseMovementCells: Int,
    val remainingMovementCells: Int,
    val unit: DistanceUnit,
    val baseAmount: Double,
    val remainingAmount: Double,
)

private val KEEP_EXISTING_TIE_ORDER: InitiativeTieResolver =
    { actorsAtInitiative, _, _ -> actorsAtInitiative }

class ActorTracker(
    val actorList: MutableList<Actor> = mutableListOf(),
){
    class Subscription(
        private val unsubscribeAction: () -> Unit,
    ) {
        fun unsubscribe() = unsubscribeAction()
    }

    var currentlyActive: Int = 0
    var roundCount: Int = 0

    private var activeActors: Int = 0
    private val changeListeners = CopyOnWriteArrayList<() -> Unit>()
    private val movementBudgets = mutableMapOf<String, Int>()

    fun onChanged(listener: () -> Unit): Subscription {
        changeListeners += listener
        return Subscription { changeListeners -= listener }
    }

    fun addActor(
        actor: Actor,
        tieResolver: InitiativeTieResolver = KEEP_EXISTING_TIE_ORDER,
    ) {
        val currentActorId = getCurrentActor()?.id
        actor.color = TOKEN_COLORS[actorList.size]
        actorList.add(actor)
        EventBus.publish(
            TokenAddedEvent(
                actor.id,
                actor.name,
                actor.color,
                actor.tokenSize,
                isPlayerCharacter = actor.actorType == ActorType.PC,
                darkvisionRangeCells = actor.darkvisionRangeCells(),
                lightSource = actor.tokenLightSource(),
            ),
        )
        if (actor.imageSettings.uri != null) {
            publishImageEvent(actor)
        }
        publishEffectsEvent(actor)
        if(actor.initiative != null){
            activeActors++
            sortActorsByInitiative(actor.id, tieResolver)
        }
        normalizeCurrentSelection(currentActorId)
        publishCurrentMovementBudget()
        notifyChanged()
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
        movementBudgets.remove(actor.id)
        EventBus.publish(TokenRemovedEvent(actor.id, actor.name))
        normalizeCurrentSelection(currentActorId)
        publishCurrentMovementBudget()
        notifyChanged()
    }

    fun updateActor(
        updatedActor: Actor,
        tieResolver: InitiativeTieResolver = KEEP_EXISTING_TIE_ORDER,
    ): Boolean {
        val index = actorList.indexOfFirst { it.id == updatedActor.id }
        if (index != -1) {
            val previousActor = actorList[index]
            val movementRangeChanged = previousActor.features.movementRange != updatedActor.features.movementRange
            if (previousActor.imageSettings != updatedActor.imageSettings) {
                publishImageEvent(updatedActor)
            }
            if (previousActor.effects != updatedActor.effects) {
                publishEffectsEvent(updatedActor)
            }
            if (
                previousActor.name != updatedActor.name ||
                previousActor.color != updatedActor.color ||
                previousActor.tokenSize != updatedActor.tokenSize ||
                previousActor.actorType != updatedActor.actorType ||
                previousActor.features.darkvisionRange != updatedActor.features.darkvisionRange ||
                previousActor.features.lightSource != updatedActor.features.lightSource
            ) {
                EventBus.publish(
                    TokenAddedEvent(
                        updatedActor.id,
                        updatedActor.name,
                        updatedActor.color,
                        updatedActor.tokenSize,
                        isPlayerCharacter = updatedActor.actorType == ActorType.PC,
                        darkvisionRangeCells = updatedActor.darkvisionRangeCells(),
                        lightSource = updatedActor.tokenLightSource(),
                    ),
                )
            }
            val currentActorId = getCurrentActor()?.id
            actorList[index] = updatedActor
            if (movementRangeChanged) {
                movementBudgets[updatedActor.id] = updatedActor.baseMovementCells()
            }
            if (previousActor.initiative != updatedActor.initiative) {
                if(previousActor.initiative != null && updatedActor.initiative == null) {
                    activeActors--
                }else if(previousActor.initiative == null) {
                    activeActors++
                }

                sortActorsByInitiative(updatedActor.id, tieResolver)
                normalizeCurrentSelection(currentActorId)
                val newCurrentActor = getCurrentActor()
                if (newCurrentActor?.id != currentActorId) {
                    movementBudgets[newCurrentActor?.id as String] = newCurrentActor.baseMovementCells()
                }
                publishCurrentMovementBudget()
                notifyChanged()
                return true
            }
            publishCurrentMovementBudget()
            notifyChanged()
        }
        normalizeCurrentSelection()
        publishCurrentMovementBudget()
        return false
    }

    fun removeAllActors() {
        actorList.clear()
        activeActors = 0
        currentlyActive = 0
        roundCount = 0
        movementBudgets.clear()
        EventBus.publish(TokensResetEvent())
        EventBus.publish(ActiveTokenChangedEvent(null, null)
        )
        publishCurrentMovementBudget()
        notifyChanged()
    }

    fun findActor(actorId: String): Actor? = actorList.firstOrNull { it.id == actorId }

    fun setActorEffects(
        actorId: String,
        effects: List<Effect>,
    ): Boolean {
        if (effects.map(Effect::id).distinct().size != effects.size) return false
        val actor = findActor(actorId) ?: return false
        val updatedEffects = effects.toList()
        updateActor(actor.copy(effects = updatedEffects))
        return findActor(actorId)?.effects == updatedEffects
    }

    fun addActorEffect(
        actorId: String,
        effect: Effect,
    ): Boolean {
        val actor = findActor(actorId) ?: return false
        if (actor.effects.any { it.id == effect.id }) return false
        return setActorEffects(actorId, actor.effects + effect)
    }

    fun removeActorEffect(
        actorId: String,
        effectId: String,
    ): Boolean {
        val actor = findActor(actorId) ?: return false
        val remaining = actor.effects.filterNot { it.id == effectId }
        if (remaining.size == actor.effects.size) return false
        return setActorEffects(actorId, remaining)
    }

    /** Republishes the authoritative effect state after a map cache is restored. */
    fun replayTokenEffects() {
        actorList.forEach(::publishEffectsEvent)
    }

    fun movementBudget(actorId: String): ActorMovementBudget? =
        findActor(actorId)?.let(::movementBudget)

    fun movementBudget(actor: Actor): ActorMovementBudget {
        val range = actor.features.movementRange
        val unit = range?.unit ?: DistanceUnit.FEET
        val baseCells = actor.baseMovementCells()
        val remainingCells = (movementBudgets[actor.id] ?: baseCells).coerceAtLeast(0)
        return ActorMovementBudget(
            baseMovementCells = baseCells,
            remainingMovementCells = remainingCells,
            unit = unit,
            baseAmount = range?.amount?.toDouble() ?: movementCellsToAmount(baseCells, unit),
            remainingAmount = movementCellsToAmount(remainingCells, unit),
        )
    }

    fun updateActorMovementRange(
        actorId: String,
        amount: Int,
        unit: DistanceUnit,
    ): Boolean {
        val actor = findActor(actorId) ?: return false
        val updated = actor.copy(
            features = actor.features.copy(
                movementRange = DistanceRange(amount = amount.coerceAtLeast(0), unit = unit),
            ),
        )
        movementBudgets[actorId] = updated.baseMovementCells()
        updateActor(updated)
        return true
    }

    fun dashActorMovement(actorId: String): Boolean {
        val actor = findActor(actorId) ?: return false
        val budget = movementBudget(actor)
        movementBudgets[actor.id] = budget.remainingMovementCells + budget.baseMovementCells
        publishCurrentMovementBudget()
        notifyChanged()
        return true
    }

    fun spendActorMovement(
        actorId: String,
        cells: Int,
    ): Boolean {
        if (cells <= 0) return true
        val actor = findActor(actorId) ?: return false
        val budget = movementBudget(actor)
        movementBudgets[actor.id] = (budget.remainingMovementCells - cells).coerceAtLeast(0)
        publishCurrentMovementBudget()
        notifyChanged()
        return true
    }

    fun hasPlayerCharactersMissingInitiative(): Boolean =
        actorList.any { actor ->
            actor.actorType == ActorType.PC && actor.initiative == null
        }

    internal fun snapshot(): TrackerSceneState =
        TrackerSceneState(
            actors = actorList.map { actor -> actor.copy() },
            activeActorId = getCurrentActor()?.id,
            roundCount = roundCount,
        )

    internal fun replaceAllActors(sceneState: TrackerSceneState) {
        removeAllActors()
        actorList.clear()
        actorList.addAll(sceneState.actors.map { actor -> actor.copy() })
        activeActors = actorList.count { it.initiative != null }
        roundCount = sceneState.roundCount.coerceAtLeast(0)
        currentlyActive = sceneState.activeActorId
            ?.let { actorId -> actorList.indexOfFirst { it.id == actorId } }
            ?.takeIf { it in 0 until activeActors }
            ?: 0

        actorList.forEach { actor ->
            movementBudgets[actor.id] = actor.baseMovementCells()
            EventBus.publish(
                TokenAddedEvent(
                    actor.id,
                    actor.name,
                    actor.color,
                    actor.tokenSize,
                    isPlayerCharacter = actor.actorType == ActorType.PC,
                    darkvisionRangeCells = actor.darkvisionRangeCells(),
                    lightSource = actor.tokenLightSource(),
                ),
            )
            if (actor.imageSettings.uri != null) {
                publishImageEvent(actor)
            }
            publishEffectsEvent(actor)
        }
        EventBus.publish(ActiveTokenChangedEvent(getCurrentActor()?.id, getCurrentActor()?.name))
        publishCurrentMovementBudget()
        notifyChanged()
    }

    private fun sortActorsByInitiative(
        actorId: String,
        tieResolver: InitiativeTieResolver,
    ) {
        val currentIndex = actorList.indexOfFirst { it.id == actorId }
        if (currentIndex == -1) {
            return
        }

        val actor = actorList.removeAt(currentIndex)
        val initiative = actor.initiative ?: run {
            actorList.add(actor)
            return
        }

        val insertIndex = actorList.indexOfFirst { other ->
            val otherInitiative = other.initiative
            otherInitiative == null || otherInitiative <= initiative
        }.let { index ->
            if (index == -1) actorList.size else index
        }

        val tieCount = actorList.drop(insertIndex).takeWhile { it.initiative == initiative }.size
        if (tieCount == 0) {
            actorList.add(insertIndex, actor)
            return
        }

        actorList.add(insertIndex + tieCount, actor)
        val tiedActors = actorList.subList(insertIndex, insertIndex + tieCount + 1).toList()
        val resolvedOrder = tieResolver(tiedActors, initiative, actor.id) ?: return
        applyTieOrder(initiative, resolvedOrder)
    }

    private fun applyTieOrder(
        initiative: Int,
        resolvedOrder: List<Actor>,
    ) {
        val tieStart = actorList.indexOfFirst { it.initiative == initiative }
        if (tieStart == -1) {
            return
        }
        val tieCount = actorList.drop(tieStart).takeWhile { it.initiative == initiative }.size
        if (tieCount == 0) {
            return
        }

        val currentTieActors = actorList.subList(tieStart, tieStart + tieCount).toList()
        val currentActorsById = currentTieActors.associateBy(Actor::id)
        val resolvedIds = resolvedOrder.map(Actor::id)

        if (resolvedIds.toSet().size != resolvedIds.size) {
            return
        }
        if (!currentActorsById.keys.containsAll(resolvedIds)) {
            return
        }

        val resolvedIdSet = resolvedIds.toSet()
        val finalOrder = resolvedIds.mapNotNull(currentActorsById::get) +
            currentTieActors.filterNot { it.id in resolvedIdSet }

        actorList.subList(tieStart, tieStart + tieCount).clear()
        actorList.addAll(tieStart, finalOrder)
    }

    fun next(){
        if (activeActors == 0) {
            currentlyActive = 0
            roundCount = 0
            publishCurrentMovementBudget()
            notifyChanged()
            return
        }

        val advancesRound = currentlyActive == activeActors - 1
        if (advancesRound) {
            roundCount++
            decrementRoundEffects()
        }
        currentlyActive = (currentlyActive + 1) % activeActors
        movementBudgets[actorList[currentlyActive].id] = actorList[currentlyActive].baseMovementCells()

        EventBus.publish(
            ActiveTokenChangedEvent(
                actorList[currentlyActive].id,
                actorList[currentlyActive].name,
            ),
        )
        publishCurrentMovementBudget()
        notifyChanged()
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

    private fun publishEffectsEvent(actor: Actor) {
        EventBus.publish(
            TokenEffectsChangedEvent(
                tokenId = actor.id,
                effects = actor.effects.map(Effect::toTokenEffect),
            ),
        )
    }

    private fun decrementRoundEffects() {
        actorList.forEachIndexed { index, actor ->
            val updatedEffects = actor.effects.mapNotNull { effect ->
                when (effect.durationRounds) {
                    null -> effect
                    1 -> null
                    else -> effect.copy(durationRounds = effect.durationRounds - 1)
                }
            }
            if (updatedEffects != actor.effects) {
                val updatedActor = actor.copy(effects = updatedEffects)
                actorList[index] = updatedActor
                publishEffectsEvent(updatedActor)
            }
        }
    }

    private fun publishCurrentMovementBudget() {
        val actor = getCurrentActor()
        if (actor == null) {
            EventBus.publish(
                TokenMovementBudgetChangedEvent(
                    id = null,
                    name = null,
                    baseMovementCells = 0,
                    remainingMovementCells = 0,
                ),
            )
            return
        }

        val budget = movementBudget(actor)
        EventBus.publish(
            TokenMovementBudgetChangedEvent(
                id = actor.id,
                name = actor.name,
                baseMovementCells = budget.baseMovementCells,
                remainingMovementCells = budget.remainingMovementCells,
            ),
        )
    }

    private fun notifyChanged() {
        changeListeners.forEach { listener -> listener() }
    }

    private fun Actor.darkvisionRangeCells(): Double? =
        features.darkvisionRange
            ?.toGridCells()
            ?.takeIf { it.isFinite() && it > 0.0 }

    private fun Actor.tokenLightSource(): TokenLightSource? {
        val source = features.lightSource ?: return null
        val brightRangeCells = source.brightRange.toGridCells()
            .takeIf { it.isFinite() && it > 0.0 }
            ?: 0.0
        val dimRangeCells = source.dimRange.toGridCells()
            .takeIf { it.isFinite() && it > 0.0 }
            ?: 0.0
        if (brightRangeCells <= 0.0 && dimRangeCells <= 0.0) return null

        return TokenLightSource(
            brightRangeCells = brightRangeCells,
            dimRangeCells = maxOf(dimRangeCells, brightRangeCells),
            colorHex = ColorHexCodec.colorToHex(source.color),
        )
    }

    private fun Actor.baseMovementCells(): Int =
        features.movementRange
            ?.toGridCells()
            ?.roundToInt()
            ?.coerceAtLeast(0)
            ?: 0

    private fun movementCellsToAmount(
        cells: Int,
        unit: DistanceUnit,
    ): Double =
        when (unit) {
            DistanceUnit.FEET -> cells * FEET_PER_GRID_CELL
            DistanceUnit.METERS -> cells * METERS_PER_GRID_CELL
        }

    private fun DistanceRange.toGridCells(): Double =
        when (unit) {
            DistanceUnit.FEET -> amount / FEET_PER_GRID_CELL
            DistanceUnit.METERS -> amount / METERS_PER_GRID_CELL
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

    private companion object {
        const val FEET_PER_GRID_CELL = 5.0
        const val METERS_PER_GRID_CELL = 1.5
    }
}

