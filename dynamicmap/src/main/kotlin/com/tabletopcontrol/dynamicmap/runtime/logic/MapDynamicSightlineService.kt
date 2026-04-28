package com.tabletopcontrol.dynamicmap.runtime.logic

import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.TokenAddedEvent
import com.tabletopcontrol.core.TokenMovedEvent
import com.tabletopcontrol.core.TokenRemovedEvent
import com.tabletopcontrol.core.TokensResetEvent
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapBundle
import com.tabletopcontrol.dynamicmap.runtime.DynamicMapLoadEvent
import com.tabletopcontrol.dynamicmap.runtime.DynamicSightlineMeshUpdatedEvent
import com.tabletopcontrol.dynamicmap.runtime.MapClearEvent
import com.tabletopcontrol.dynamicmap.runtime.MapLoadEvent
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javafx.application.Platform

/**
 * Computes DynamicMap PC sightlines once per scene revision and publishes the result
 * to all renderer instances. The expensive ray casting stays off the JavaFX thread.
 */
internal class MapDynamicSightlineService {
    private val tokens = linkedMapOf<String, Token>()
    private val subscriptions = mutableListOf<EventBus.Subscription>()
    private val revision = AtomicLong(0L)
    private val contributionCacheLock = Any()
    private val contributionCache = mutableMapOf<String, CachedPcSightlineContribution>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DynamicMapSightline").apply { isDaemon = true }
    }

    private var currentBundle: DynamicMapBundle? = null
    private var currentGeometry: DynamicSightlineGeometry? = null
    private var topologyVersion: Long = 0L
    @Volatile
    private var disposed: Boolean = false

    init {
        attachToEventBus()
    }

    fun dispose() {
        disposed = true
        subscriptions.forEach { it.unsubscribe() }
        subscriptions.clear()
        executor.shutdownNow()
    }

    private fun attachToEventBus() {
        subscriptions += EventBus.subscribe<DynamicMapLoadEvent> { event ->
            currentBundle = event.bundle
            currentGeometry = DynamicSightlineGeometry.forMap(
                cols = event.bundle.cols,
                rows = event.bundle.rows,
                walls = event.bundle.walls,
            )
            topologyVersion++
            clearContributionCache()
            scheduleCompute()
        }
        subscriptions += EventBus.subscribe<MapLoadEvent> {
            clearDynamicMap()
        }
        subscriptions += EventBus.subscribe<MapClearEvent> {
            clearDynamicMap()
        }
        subscriptions += EventBus.subscribe<TokenAddedEvent> { event ->
            val previous = tokens[event.id]
            if (previous != null) {
                tokens[event.id] = previous.copy(
                    name = event.name,
                    color = event.color,
                    size = event.size,
                    isPlayerCharacter = event.isPlayerCharacter,
                )
                if (previous.isPlayerCharacter || event.isPlayerCharacter) {
                    if (!event.isPlayerCharacter) {
                        removeContributionCacheEntry(event.id)
                    }
                    scheduleCompute()
                }
            } else {
                val (nextTokenCol, nextTokenRow) = nextAvailableTokenPlacement(tokens.values, event.size)
                tokens[event.id] = Token(
                    id = event.id,
                    name = event.name,
                    col = nextTokenCol,
                    row = nextTokenRow,
                    size = event.size,
                    color = event.color,
                    isPlayerCharacter = event.isPlayerCharacter,
                )
                if (event.isPlayerCharacter) {
                    scheduleCompute()
                }
            }
        }
        subscriptions += EventBus.subscribe<TokenRemovedEvent> { event ->
            val removed = tokens.remove(event.id)
            if (removed?.isPlayerCharacter == true) {
                removeContributionCacheEntry(event.id)
                scheduleCompute()
            }
        }
        subscriptions += EventBus.subscribe<TokenMovedEvent> { event ->
            val previous = tokens[event.id] ?: return@subscribe
            tokens[event.id] = previous.copy(col = event.col, row = event.row)
            if (previous.isPlayerCharacter) {
                scheduleCompute()
            }
        }
        subscriptions += EventBus.subscribe<TokensResetEvent> {
            val hadPc = tokens.values.any { it.isPlayerCharacter }
            tokens.clear()
            if (hadPc) {
                clearContributionCache()
                scheduleCompute()
            }
        }
    }

    private fun clearDynamicMap() {
        if (disposed) return
        currentBundle = null
        currentGeometry = null
        topologyVersion++
        clearContributionCache()
        val nextRevision = revision.incrementAndGet()
        publishOnFx(DynamicSightlineMeshUpdatedEvent(nextRevision, null))
    }

    private fun scheduleCompute() {
        if (disposed) return
        val bundle = currentBundle ?: return
        val snapshotTopologyVersion = topologyVersion
        val geometry = currentGeometry ?: DynamicSightlineGeometry.forMap(
            cols = bundle.cols,
            rows = bundle.rows,
            walls = bundle.walls,
        ).also { currentGeometry = it }
        val pcSnapshot = tokens.values
            .filter { it.isPlayerCharacter }
            .map { it.copy() }
        val nextRevision = revision.incrementAndGet()

        executor.execute {
            if (disposed || revision.get() != nextRevision) return@execute
            val contributions = computeContributions(
                geometry = geometry,
                pcs = pcSnapshot,
                topologyVersion = snapshotTopologyVersion,
                expectedRevision = nextRevision,
            ) ?: return@execute
            val mesh = geometry.combine(contributions)
            if (disposed || revision.get() != nextRevision) return@execute
            publishOnFx(DynamicSightlineMeshUpdatedEvent(nextRevision, mesh))
        }
    }

    private fun computeContributions(
        geometry: DynamicSightlineGeometry,
        pcs: List<Token>,
        topologyVersion: Long,
        expectedRevision: Long,
    ): List<DynamicSightlineContribution>? {
        val pcIds = pcs.mapTo(mutableSetOf()) { it.id }
        val contributionsByToken = linkedMapOf<String, DynamicSightlineContribution>()

        synchronized(contributionCacheLock) {
            contributionCache.keys.retainAll(pcIds)
            pcs.forEach { pc ->
                val key = PcSightlineCacheKey.from(pc, topologyVersion)
                contributionCache[pc.id]
                    ?.takeIf { it.key == key }
                    ?.let { contributionsByToken[pc.id] = it.contribution }
            }
        }

        pcs.forEach { pc ->
            if (disposed || revision.get() != expectedRevision) return null
            if (contributionsByToken.containsKey(pc.id)) return@forEach

            val key = PcSightlineCacheKey.from(pc, topologyVersion)
            val contribution = geometry.computeContribution(pc) ?: DynamicSightlineContribution.empty()
            if (disposed || revision.get() != expectedRevision) return null

            synchronized(contributionCacheLock) {
                contributionCache[pc.id] = CachedPcSightlineContribution(key, contribution)
            }
            contributionsByToken[pc.id] = contribution
        }

        return pcs.mapNotNull { pc -> contributionsByToken[pc.id] }
    }

    private fun clearContributionCache() {
        synchronized(contributionCacheLock) {
            contributionCache.clear()
        }
    }

    private fun removeContributionCacheEntry(tokenId: String) {
        synchronized(contributionCacheLock) {
            contributionCache.remove(tokenId)
        }
    }

    private fun publishOnFx(event: DynamicSightlineMeshUpdatedEvent) {
        if (disposed) return
        if (Platform.isFxApplicationThread()) {
            EventBus.publish(event)
        } else {
            Platform.runLater {
                if (!disposed && revision.get() == event.revision) {
                    EventBus.publish(event)
                }
            }
        }
    }

    private data class PcSightlineCacheKey(
        val tokenId: String,
        val col: Int,
        val row: Int,
        val sizeName: String,
        val topologyVersion: Long,
    ) {
        companion object {
            fun from(token: Token, topologyVersion: Long): PcSightlineCacheKey =
                PcSightlineCacheKey(
                    tokenId = token.id,
                    col = token.col,
                    row = token.row,
                    sizeName = token.size.name,
                    topologyVersion = topologyVersion,
                )
        }
    }

    private data class CachedPcSightlineContribution(
        val key: PcSightlineCacheKey,
        val contribution: DynamicSightlineContribution,
    )
}
