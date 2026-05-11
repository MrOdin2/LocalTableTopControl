package com.tabletopcontrol.new_tracker.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.TokenAddedEvent
import com.tabletopcontrol.core.TokenMoveDirection
import com.tabletopcontrol.core.TokenMoveRequestedEvent
import com.tabletopcontrol.core.TokenMovedEvent
import com.tabletopcontrol.core.TokensResetEvent
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorTracker
import com.tabletopcontrol.new_tracker.model.ActorType
import com.tabletopcontrol.new_tracker.model.DistanceUnit
import com.tabletopcontrol.new_tracker.model.InitiativeTieResolver
import javafx.application.Platform
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A lightweight embedded HTTP server that exposes a mobile-optimised web companion
 * for players. Players connect via a browser, select their character name (which
 * matches the [Actor.name] field of a PC actor in the tracker), and use four
 * directional buttons to move their map token one grid cell at a time.
 *
 * The server subscribes to [TokenMovedEvent] to mirror the latest authoritative map position
 * and publishes relative move requests for map modules to resolve.
 *
 * All [TokenMovedEvent] publications are dispatched on the JavaFX Application Thread by
 * default so that map renderers receive them correctly.
 *
 * @param actorTracker the live [ActorTracker] instance used to resolve PC actor names.
 * @param port         TCP port to listen on; defaults to [DEFAULT_PORT].
 */
class PlayerWebServer(
    private val actorTracker: ActorTracker,
    val port: Int = DEFAULT_PORT,
    private val applicationDispatcher: ((() -> Unit) -> Unit) = { action ->
        if (Platform.isFxApplicationThread()) action() else Platform.runLater(action)
    },
) {
    companion object {
        const val DEFAULT_PORT = 8765
    }

    private var server: HttpServer? = null
    private var executor: ExecutorService? = null
    private var trackerSubscription: ActorTracker.Subscription? = null
    private val subscriptions = mutableListOf<EventBus.Subscription>()
    private val updateClients = CopyOnWriteArraySet<SseClient>()
    @Volatile
    private var initiativeEntryRequested = false

    var onActorChanged: (() -> Unit)? = null
    var initiativeTieResolver: InitiativeTieResolver = { actorsAtInitiative, _, _ -> actorsAtInitiative }

    /** Cache of current token positions: tokenId → (col, row). */
    private val tokenPositions = mutableMapOf<String, Pair<Int, Int>>()

    /** Whether the server is currently running. */
    val isRunning: Boolean get() = server != null

    fun requestInitiatives() {
        if (!actorTracker.hasPlayerCharactersMissingInitiative()) return
        initiativeEntryRequested = true
        publishInitiativeRequired()
    }

    /**
     * Starts the HTTP server and begins subscribing to token events.
     * If the server is already running this is a no-op.
     */
    fun start() {
        if (server != null) return

        val exec = Executors.newCachedThreadPool()
        executor = exec

        trackerSubscription = actorTracker.onChanged {
            if (initiativeEntryRequested && !actorTracker.hasPlayerCharactersMissingInitiative()) {
                initiativeEntryRequested = false
            }
            publishApplicationUpdate()
        }
        subscriptions += EventBus.subscribe<TokenAddedEvent> {
            publishApplicationUpdate()
        }
        subscriptions += EventBus.subscribe<TokenMovedEvent> { event ->
            tokenPositions[event.id] = Pair(event.col, event.row)
            publishApplicationUpdate()
        }
        subscriptions += EventBus.subscribe<TokensResetEvent> {
            tokenPositions.clear()
            publishApplicationUpdate()
        }

        try {
            val srv = HttpServer.create(InetSocketAddress(port), 0)
            srv.createContext("/api/players", ::handlePlayers)
            srv.createContext("/api/state", ::handleState)
            srv.createContext("/api/stats", ::handleStats)
            srv.createContext("/api/initiative", ::handleInitiative)
            srv.createContext("/api/movement", ::handleMovement)
            srv.createContext("/api/dash", ::handleDash)
            srv.createContext("/api/move", ::handleMove)
            srv.createContext("/api/events", ::handleEvents)
            srv.createContext("/", ::handleIndex)
            srv.executor = exec
            srv.start()
            server = srv
            publishApplicationUpdate()
        } catch (t: Throwable) {
            trackerSubscription?.unsubscribe()
            trackerSubscription = null
            subscriptions.forEach { it.unsubscribe() }
            subscriptions.clear()
            closeUpdateClients()
            executor?.shutdownNow()
            executor = null
            throw t
        }
    }

    /**
     * Stops the HTTP server and cleans up all subscriptions.
     * Safe to call multiple times.
     */
    fun stop() {
        server?.stop(0)
        server = null
        trackerSubscription?.unsubscribe()
        trackerSubscription = null
        subscriptions.forEach { it.unsubscribe() }
        subscriptions.clear()
        closeUpdateClients()
        executor?.shutdownNow()
        executor = null
        tokenPositions.clear()
    }

    // -------------------------------------------------------------------------
    // HTTP handlers
    // -------------------------------------------------------------------------

    /** `GET /api/players` - returns a JSON array of PC actor names. */
    private fun handlePlayers(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            respond(exchange, 405, "Method Not Allowed")
            return
        }
        val names = actorTracker.actorList
            .mapNotNull { actor ->
                actor.name.takeIf { actor.actorType == ActorType.PC && it.isNotBlank() }
            }
            .distinct()
        val json = names.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"${it.jsonEscape()}\"" }
        respondJson(exchange, 200, json)
    }

    /**
     * `GET /api/state?player=<name>` — returns the actor's current stats and token position.
     *
     * Response JSON includes name, HP, AC, token position, active-turn flag, and actor colour.
     */
    private fun handleState(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            respond(exchange, 405, "Method Not Allowed")
            return
        }
        val actorName = queryParam(exchange.requestURI, "player")
        if (actorName == null) {
            respond(exchange, 400, "Missing 'player' query parameter")
            return
        }
        val actor = findPlayerActor(actorName)
        if (actor == null) {
            respond(exchange, 404, "No PC actor named \"$actorName\"")
            return
        }
        val (col, row) = tokenPositions[actor.id] ?: Pair(0, 0)
        respondJson(exchange, 200, stateJson(actor, col, row))
    }

    /**
     * `PUT /api/stats?player=<name>&hp=<hp>&ac=<ac>` - updates HP and AC for the PC actor.
     */
    private fun handleStats(exchange: HttpExchange) {
        if (exchange.requestMethod != "PUT") {
            respond(exchange, 405, "Method Not Allowed")
            return
        }
        val actorName = queryParam(exchange.requestURI, "player")
        val hp = nonNegativeIntQueryParam(exchange.requestURI, "hp")
        val ac = nonNegativeIntQueryParam(exchange.requestURI, "ac")
        if (actorName == null || hp == null || ac == null) {
            respond(exchange, 400, "Missing or invalid 'player', 'hp', or 'ac' query parameter")
            return
        }

        var status = 500
        var body = "Update did not complete"
        val completed = runOnApplicationThreadAndWait {
            val actor = findPlayerActor(actorName)
            if (actor == null) {
                status = 404
                body = "No PC actor named \"$actorName\""
                return@runOnApplicationThreadAndWait
            }
            actorTracker.updateActor(actor.copy(hp = hp, ac = ac))
            onActorChanged?.invoke()
            val updatedActor = actorTracker.findActor(actor.id) ?: actor.copy(hp = hp, ac = ac)
            val (col, row) = tokenPositions[updatedActor.id] ?: Pair(0, 0)
            status = 200
            body = stateJson(updatedActor, col, row)
        }

        if (!completed) {
            respond(exchange, 503, "Application update timed out")
            return
        }
        if (status == 200) {
            respondJson(exchange, status, body)
        } else {
            respond(exchange, status, body)
        }
    }

    /**
     * `PUT /api/initiative?player=<name>&initiative=<initiative>` - updates initiative for the PC actor.
     */
    private fun handleInitiative(exchange: HttpExchange) {
        if (exchange.requestMethod != "PUT") {
            respond(exchange, 405, "Method Not Allowed")
            return
        }
        val actorName = queryParam(exchange.requestURI, "player")
        val initiative = nonNegativeIntQueryParam(exchange.requestURI, "initiative")
        if (actorName == null || initiative == null) {
            respond(exchange, 400, "Missing or invalid 'player' or 'initiative' query parameter")
            return
        }

        var status = 500
        var body = "Update did not complete"
        val completed = runOnApplicationThreadAndWait {
            val actor = findPlayerActor(actorName)
            if (actor == null) {
                status = 404
                body = "No PC actor named \"$actorName\""
                return@runOnApplicationThreadAndWait
            }
            actorTracker.updateActor(actor.copy(initiative = initiative), initiativeTieResolver)
            if (!actorTracker.hasPlayerCharactersMissingInitiative()) {
                initiativeEntryRequested = false
            }
            onActorChanged?.invoke()
            val updatedActor = actorTracker.findActor(actor.id) ?: actor.copy(initiative = initiative)
            val (col, row) = tokenPositions[updatedActor.id] ?: Pair(0, 0)
            status = 200
            body = stateJson(updatedActor, col, row)
        }

        if (!completed) {
            respond(exchange, 503, "Application update timed out")
            return
        }
        if (status == 200) {
            respondJson(exchange, status, body)
        } else {
            respond(exchange, status, body)
        }
    }

    /**
     * `PUT /api/movement?player=<name>&amount=<amount>&unit=<FEET|METERS>` - updates base movement.
     */
    private fun handleMovement(exchange: HttpExchange) {
        if (exchange.requestMethod != "PUT") {
            respond(exchange, 405, "Method Not Allowed")
            return
        }
        val actorName = queryParam(exchange.requestURI, "player")
        val amount = nonNegativeIntQueryParam(exchange.requestURI, "amount")
        val requestedUnit = queryParam(exchange.requestURI, "unit")
        val unit = requestedUnit?.let(::distanceUnitOrNull)
        if (actorName == null || amount == null || (requestedUnit != null && unit == null)) {
            respond(exchange, 400, "Missing or invalid 'player', 'amount', or 'unit' query parameter")
            return
        }

        var status = 500
        var body = "Update did not complete"
        val completed = runOnApplicationThreadAndWait {
            val actor = findPlayerActor(actorName)
            if (actor == null) {
                status = 404
                body = "No PC actor named \"$actorName\""
                return@runOnApplicationThreadAndWait
            }
            actorTracker.updateActorMovementRange(
                actor.id,
                amount,
                unit ?: actor.features.movementRange?.unit ?: DistanceUnit.FEET,
            )
            onActorChanged?.invoke()
            val updatedActor = actorTracker.findActor(actor.id) ?: actor
            val (col, row) = tokenPositions[updatedActor.id] ?: Pair(0, 0)
            status = 200
            body = stateJson(updatedActor, col, row)
        }

        if (!completed) {
            respond(exchange, 503, "Application update timed out")
            return
        }
        if (status == 200) {
            respondJson(exchange, status, body)
        } else {
            respond(exchange, status, body)
        }
    }

    /**
     * `PUT /api/dash?player=<name>` - adds base movement to this round's remaining movement.
     */
    private fun handleDash(exchange: HttpExchange) {
        if (exchange.requestMethod != "PUT") {
            respond(exchange, 405, "Method Not Allowed")
            return
        }
        val actorName = queryParam(exchange.requestURI, "player")
        if (actorName == null) {
            respond(exchange, 400, "Missing 'player' query parameter")
            return
        }

        var status = 500
        var body = "Update did not complete"
        val completed = runOnApplicationThreadAndWait {
            val actor = findPlayerActor(actorName)
            if (actor == null) {
                status = 404
                body = "No PC actor named \"$actorName\""
                return@runOnApplicationThreadAndWait
            }
            actorTracker.dashActorMovement(actor.id)
            onActorChanged?.invoke()
            val updatedActor = actorTracker.findActor(actor.id) ?: actor
            val (col, row) = tokenPositions[updatedActor.id] ?: Pair(0, 0)
            status = 200
            body = stateJson(updatedActor, col, row)
        }

        if (!completed) {
            respond(exchange, 503, "Application update timed out")
            return
        }
        if (status == 200) {
            respondJson(exchange, status, body)
        } else {
            respond(exchange, status, body)
        }
    }

    /**
     * `PUT /api/move?player=<name>&dir=<n|s|e|w>` - moves the player's token one cell
     * in the requested direction and returns the updated state JSON.
     */
    private fun handleMove(exchange: HttpExchange) {
        if (exchange.requestMethod != "PUT") {
            respond(exchange, 405, "Method Not Allowed")
            return
        }
        val actorName = queryParam(exchange.requestURI, "player")
        val dir = queryParam(exchange.requestURI, "dir")
        if (actorName == null || dir == null) {
            respond(exchange, 400, "Missing 'player' or 'dir' query parameter")
            return
        }
        val actor = findPlayerActor(actorName)
        if (actor == null) {
            respond(exchange, 404, "No PC actor named \"$actorName\"")
            return
        }
        val direction = when (dir.lowercase()) {
            "n" -> TokenMoveDirection.NORTH
            "s" -> TokenMoveDirection.SOUTH
            "w" -> TokenMoveDirection.WEST
            "e" -> TokenMoveDirection.EAST
            else -> {
                respond(exchange, 400, "Invalid direction; use n, s, e, or w")
                return
            }
        }
        var updatedActor = actor
        val completed = runOnApplicationThreadAndWait {
            val before = tokenPositions[actor.id]
            EventBus.publish(TokenMoveRequestedEvent(actor.id, actor.name, direction))
            val after = tokenPositions[actor.id]
            if (after != null && after != before) {
                actorTracker.spendActorMovement(actor.id, 1)
            }
            updatedActor = actorTracker.findActor(actor.id) ?: actor
        }
        if (!completed) {
            respond(exchange, 503, "Application move timed out")
            return
        }
        val (col, row) = tokenPositions[updatedActor.id] ?: run {
            respond(exchange, 409, "Token is not available on the active map")
            return
        }
        respondJson(exchange, 200, stateJson(updatedActor, col, row))
    }

    /** `GET /api/events` - server-sent applicationUpdate events for connected browsers. */
    private fun handleEvents(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            respond(exchange, 405, "Method Not Allowed")
            return
        }
        exchange.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-cache")
        exchange.responseHeaders.add("Connection", "keep-alive")
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(200, 0)

        val client = SseClient(exchange, exchange.responseBody)
        updateClients += client
        if (!sendApplicationUpdate(client)) {
            closeUpdateClient(client)
            return
        }
        startHeartbeat(client)
    }

    /**
     * Periodically writes an SSE comment so idle disconnects are detected and stale clients
     * are removed from [updateClients].
     */
    private fun startHeartbeat(client: SseClient) {
        val heartbeatThread = Thread {
            while (updateClients.contains(client)) {
                try {
                    Thread.sleep(15_000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    closeUpdateClient(client)
                    return@Thread
                }
                if (!updateClients.contains(client)) {
                    return@Thread
                }
            }
        }
        heartbeatThread.isDaemon = true
        heartbeatThread.name = "player-web-sse-heartbeat"
        heartbeatThread.start()
    }

    /** `GET /` — serves the mobile-optimised player web app HTML page. */
    private fun handleIndex(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            respond(exchange, 405, "Method Not Allowed")
            return
        }
        val html = buildPlayerHtml()
        val bytes = html.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun respondJson(exchange: HttpExchange, status: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun queryParam(uri: URI, name: String): String? {
        val query = uri.rawQuery ?: return null
        return query.split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.firstOrNull() == name }
            ?.getOrNull(1)
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }

    private fun nonNegativeIntQueryParam(uri: URI, name: String): Int? =
        queryParam(uri, name)?.toIntOrNull()?.takeIf { it >= 0 }

    private fun distanceUnitOrNull(value: String): DistanceUnit? =
        DistanceUnit.entries.firstOrNull { unit -> unit.name.equals(value, ignoreCase = true) }

    private fun findPlayerActor(actorName: String): Actor? =
        actorTracker.actorList.firstOrNull { actor ->
            actor.actorType == ActorType.PC && actor.name == actorName
        }

    private fun runOnApplicationThreadAndWait(action: () -> Unit): Boolean {
        val latch = CountDownLatch(1)
        var failure: Throwable? = null
        applicationDispatcher {
            try {
                action()
            } catch (t: Throwable) {
                failure = t
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(5, TimeUnit.SECONDS)) return false
        failure?.let { throw it }
        return true
    }

    private fun stateJson(
        actor: Actor,
        col: Int,
        row: Int,
    ): String {
        val movement = actorTracker.movementBudget(actor)
        return """{"name":"${actor.name.jsonEscape()}","hp":${actor.hp},"ac":${actor.ac},"initiative":${actor.initiative ?: "null"},"initiativeRequired":${initiativeEntryRequested && actor.initiative == null},"col":$col,"row":$row,"active":${actor.id == actorTracker.getCurrentActor()?.id},"color":"${ColorHexCodec.colorToHex(actor.color)}","movementBaseCells":${movement.baseMovementCells},"movementRemainingCells":${movement.remainingMovementCells},"movementBaseAmount":${movement.baseAmount.jsonNumber()},"movementRemainingAmount":${movement.remainingAmount.jsonNumber()},"movementUnit":"${movement.unit.name}","movementUnitLabel":"${movement.unit.label}"}"""
    }

    private fun publishApplicationUpdate() {
        executor?.execute {
            updateClients.forEach { client ->
                if (!sendApplicationUpdate(client)) {
                    closeUpdateClient(client)
                }
            }
        }
    }

    private fun sendApplicationUpdate(client: SseClient): Boolean =
        sendSseEvent(client, "applicationUpdate", applicationUpdateJson())

    private fun publishInitiativeRequired() {
        executor?.execute {
            updateClients.forEach { client ->
                if (!sendSseEvent(client, "initiativeRequired", "{}")) {
                    closeUpdateClient(client)
                }
            }
        }
    }

    private fun sendSseEvent(client: SseClient, eventName: String, data: String): Boolean =
        try {
            synchronized(client) {
                val message = "event: $eventName\n" +
                    "data: $data\n\n"
                client.body.write(message.toByteArray(Charsets.UTF_8))
                client.body.flush()
            }
            true
        } catch (_: IOException) {
            false
        }

    private fun applicationUpdateJson(): String {
        val currentActor = actorTracker.getCurrentActor()
        return """{"activeActorId":${currentActor?.id?.jsonString() ?: "null"},"activeActorName":${currentActor?.name?.jsonString() ?: "null"}}"""
    }

    private fun closeUpdateClients() {
        updateClients.forEach(::closeUpdateClient)
    }

    private fun closeUpdateClient(client: SseClient) {
        updateClients -= client
        runCatching { client.body.close() }
        runCatching { client.exchange.close() }
    }

    private fun String.jsonEscape(): String = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun String.jsonString(): String = "\"${jsonEscape()}\""

    private fun Double.jsonNumber(): String =
        if (isFinite()) toString() else "0.0"

    private val DistanceUnit.label: String
        get() = when (this) {
            DistanceUnit.FEET -> "ft"
            DistanceUnit.METERS -> "m"
        }

    private data class SseClient(
        val exchange: HttpExchange,
        val body: OutputStream,
    )

    // -------------------------------------------------------------------------
    // HTML page
    // -------------------------------------------------------------------------

    private fun buildPlayerHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no"/>
  <title>TabletopControl — Player</title>
  <style>
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    :root {
      --accent: #1565c0;
      --accent-dark: #0d47a1;
      --bg: #1a1a2e;
      --surface: #16213e;
      --text: #e0e0e0;
      --text-muted: #9e9e9e;
      --btn-radius: 12px;
    }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      background: var(--bg);
      color: var(--text);
      min-height: 100dvh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 1.5rem;
    }
    h1 { font-size: 1.6rem; font-weight: 700; margin-bottom: 0.25rem; color: #fff; }
    .subtitle { color: var(--text-muted); font-size: 0.9rem; margin-bottom: 2rem; }
    .card {
      background: var(--surface);
      border: 3px solid transparent;
      border-radius: 16px;
      padding: 1.75rem;
      width: 100%;
      max-width: 360px;
      box-shadow: 0 8px 32px rgba(0,0,0,0.4);
    }
    .game-card.active {
      border-color: var(--actor-color, var(--accent));
      box-shadow: 0 0 0 2px var(--actor-color, var(--accent)), 0 8px 32px rgba(0,0,0,0.4);
    }
    select, input[type="text"], input[type="number"] {
      width: 100%;
      padding: 0.75rem 1rem;
      border-radius: var(--btn-radius);
      border: 2px solid #333;
      background: #0f3460;
      color: var(--text);
      font-size: 1rem;
      margin-bottom: 1rem;
      appearance: none;
    }
    select:focus, input[type="text"]:focus, input[type="number"]:focus { outline: none; border-color: var(--accent); }
    .btn-primary {
      width: 100%;
      padding: 0.85rem;
      background: var(--accent);
      color: #fff;
      border: none;
      border-radius: var(--btn-radius);
      font-size: 1rem;
      font-weight: 600;
      cursor: pointer;
      transition: background 0.2s;
    }
    .btn-primary:active { background: var(--accent-dark); }
    .btn-secondary {
      align-self: end;
      min-height: 44px;
      padding: 0 1rem;
      background: var(--accent);
      color: #fff;
      border: none;
      border-radius: var(--btn-radius);
      font-size: 0.95rem;
      font-weight: 600;
      cursor: pointer;
    }
    .btn-secondary:active { background: var(--accent-dark); }
    .stat-editor {
      display: grid;
      grid-template-columns: 1fr 1fr auto;
      gap: 10px;
      align-items: end;
      margin-bottom: 1.25rem;
    }
    .stat-editor label {
      display: flex;
      flex-direction: column;
      gap: 4px;
      color: var(--text-muted);
      font-size: 0.75rem;
      text-transform: uppercase;
    }
    .stat-editor input { margin-bottom: 0; }
    .movement-editor {
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
      margin-bottom: 1.25rem;
    }
    .movement-row {
      display: grid;
      grid-template-columns: 1fr 0.9fr auto;
      gap: 10px;
      align-items: end;
    }
    .movement-editor label {
      display: flex;
      flex-direction: column;
      gap: 4px;
      color: var(--text-muted);
      font-size: 0.75rem;
      text-transform: uppercase;
    }
    .movement-editor input,
    .movement-editor select { margin-bottom: 0; }
    .movement-summary {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 0.75rem;
      color: var(--text-muted);
      font-size: 0.9rem;
    }
    .dash-btn {
      min-height: 44px;
      padding: 0 1rem;
      background: #0f3460;
      color: var(--text);
      border: 2px solid #1e5799;
      border-radius: var(--btn-radius);
      font-size: 0.95rem;
      font-weight: 600;
      cursor: pointer;
    }
    .dash-btn:active { background: var(--accent); }
    .dpad {
      display: grid;
      grid-template-columns: 1fr 1fr 1fr;
      grid-template-rows: 1fr 1fr 1fr;
      gap: 10px;
      width: 100%;
      max-width: 240px;
      margin: 0 auto;
    }
    .dpad-btn {
      aspect-ratio: 1;
      background: #0f3460;
      border: 2px solid #1e5799;
      border-radius: var(--btn-radius);
      color: var(--text);
      font-size: 1.8rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.15s, transform 0.1s;
      user-select: none;
      -webkit-user-select: none;
      touch-action: manipulation;
    }
    .dpad-btn:active { background: var(--accent); transform: scale(0.93); }
    .dpad-center { grid-column: 2; grid-row: 2; background: transparent; border: none; cursor: default; }
    .status { text-align: center; font-size: 0.85rem; color: var(--text-muted); margin-top: 1rem; min-height: 1.2em; }
    .back-btn {
      background: none;
      border: none;
      color: var(--text-muted);
      font-size: 0.85rem;
      cursor: pointer;
      margin-top: 1rem;
      text-decoration: underline;
    }
    .back-btn:hover { color: var(--text); }
    .modal-backdrop {
      position: fixed;
      inset: 0;
      display: none;
      align-items: center;
      justify-content: center;
      background: rgba(0, 0, 0, 0.72);
      padding: 1.5rem;
      z-index: 10;
    }
    .modal-card {
      width: 100%;
      max-width: 360px;
      background: var(--surface);
      border: 3px solid var(--accent);
      border-radius: 16px;
      padding: 1.75rem;
      box-shadow: 0 12px 40px rgba(0,0,0,0.5);
    }
    .modal-title {
      font-size: 1.25rem;
      font-weight: 700;
      margin-bottom: 0.5rem;
      text-align: center;
    }
    .modal-text {
      color: var(--text-muted);
      font-size: 0.9rem;
      margin-bottom: 1rem;
      text-align: center;
    }
    .modal-card label {
      display: flex;
      flex-direction: column;
      gap: 4px;
      color: var(--text-muted);
      font-size: 0.75rem;
      text-transform: uppercase;
    }
    #connect-screen, #game-screen { width: 100%; max-width: 360px; }
    #game-screen { display: none; }
    .actor-name { font-size: 1.1rem; font-weight: 600; margin-bottom: 1rem; text-align: center; }
  </style>
</head>
<body>

<div id="connect-screen">
  <h1>TabletopControl</h1>
  <p class="subtitle">Player Companion</p>
  <div class="card">
    <select id="player-select"><option value="">Loading players…</option></select>
    <button class="btn-primary" onclick="connect()">Connect</button>
  </div>
</div>

<div id="game-screen">
  <div class="card game-card" id="game-card">
    <div class="actor-name" id="actor-name">—</div>
    <div class="stat-editor">
      <label for="hp-input">HP<input id="hp-input" type="number" min="0" inputmode="numeric"/></label>
      <label for="ac-input">AC<input id="ac-input" type="number" min="0" inputmode="numeric"/></label>
      <button class="btn-secondary" onclick="commitStats()">Set</button>
    </div>
    <div class="movement-editor">
      <div class="movement-row">
        <label for="movement-input">Move<input id="movement-input" type="number" min="0" inputmode="numeric"/></label>
        <label for="movement-unit">Unit
          <select id="movement-unit">
            <option value="FEET">ft</option>
            <option value="METERS">m</option>
          </select>
        </label>
        <button class="btn-secondary" onclick="commitMovement()">Set</button>
      </div>
      <div class="movement-summary">
        <span id="movement-remaining"></span>
        <button class="dash-btn" onclick="commitDash()">Dash</button>
      </div>
    </div>
    <div class="dpad">
      <div></div>
      <button class="dpad-btn" ontouchstart="handleMove('n')" onclick="handleMove('n')" aria-label="North">↑</button>
      <div></div>
      <button class="dpad-btn" ontouchstart="handleMove('w')" onclick="handleMove('w')" aria-label="West">←</button>
      <div class="dpad-center"></div>
      <button class="dpad-btn" ontouchstart="handleMove('e')" onclick="handleMove('e')" aria-label="East">→</button>
      <div></div>
      <button class="dpad-btn" ontouchstart="handleMove('s')" onclick="handleMove('s')" aria-label="South">↓</button>
      <div></div>
    </div>
    <div class="status" id="status"></div>
    <button class="back-btn" onclick="disconnect()">← Change player</button>
  </div>
</div>

<div id="initiative-modal" class="modal-backdrop" role="dialog" aria-modal="true">
  <div class="modal-card">
    <div class="modal-title">Enter initiative</div>
    <p class="modal-text">Your DM is starting initiative.</p>
    <label for="initiative-input">Initiative
      <input id="initiative-input" type="number" min="0" inputmode="numeric"/>
    </label>
    <button class="btn-primary" onclick="commitInitiative()">Submit</button>
    <div class="status" id="initiative-status"></div>
  </div>
</div>

<script>
  let currentPlayer = '';
  let updateEvents = null;
  let initiativeRequired = false;

  async function loadPlayers() {
    try {
      const res = await fetch('/api/players');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const names = await res.json();
      const sel = document.getElementById('player-select');
      if (names.length === 0) {
        sel.innerHTML = '<option value="">No players configured</option>';
      } else {
        sel.innerHTML = '<option value="">— Select player —</option>' +
          names.map(n => '<option value="' + esc(n) + '">' + esc(n) + '</option>').join('');
      }
    } catch (e) {
      document.getElementById('player-select').innerHTML = '<option value="">Could not load players</option>';
    }
  }

  async function connect() {
    const sel = document.getElementById('player-select');
    const name = sel.value;
    if (!name) { setStatus('Please select a player.'); return; }
    try {
      const res = await fetch('/api/state?player=' + encodeURIComponent(name));
      if (!res.ok) { setStatus('Player not found. Ask your DM to assign you an actor.'); return; }
      const data = await res.json();
      currentPlayer = name;
      updateDisplay(data);
      openApplicationUpdates();
      document.getElementById('connect-screen').style.display = 'none';
      document.getElementById('game-screen').style.display = 'flex';
    } catch (e) {
      setStatus('Connection error: ' + e.message);
    }
  }

  async function handleMove(dir) {
    if (!currentPlayer) return;
    if (initiativeRequired) { setInitiativeStatus('Enter initiative first.'); return; }
    try {
      const res = await fetch('/api/move?player=' + encodeURIComponent(currentPlayer) + '&dir=' + dir, { method: 'PUT' });
      if (!res.ok) { setStatus('Move failed.'); return; }
      updateDisplay(await res.json());
      setStatus('');
    } catch (e) {
      setStatus('Error: ' + e.message);
    }
  }

  async function commitStats() {
    if (!currentPlayer) return;
    if (initiativeRequired) { setInitiativeStatus('Enter initiative first.'); return; }
    const hp = nonNegativeInt(document.getElementById('hp-input').value);
    const ac = nonNegativeInt(document.getElementById('ac-input').value);
    if (hp === null || ac === null) { setStatus('HP and AC must be 0 or higher.'); return; }
    try {
      const res = await fetch('/api/stats?player=' + encodeURIComponent(currentPlayer) + '&hp=' + hp + '&ac=' + ac, { method: 'PUT' });
      if (!res.ok) { setStatus('Stats update failed.'); return; }
      updateDisplay(await res.json());
      setStatus('');
    } catch (e) {
      setStatus('Error: ' + e.message);
    }
  }

  async function commitMovement() {
    if (!currentPlayer) return;
    if (initiativeRequired) { setInitiativeStatus('Enter initiative first.'); return; }
    const amount = nonNegativeInt(document.getElementById('movement-input').value);
    const unit = document.getElementById('movement-unit').value;
    if (amount === null) { setStatus('Movement must be 0 or higher.'); return; }
    try {
      const url = '/api/movement?player=' + encodeURIComponent(currentPlayer) +
        '&amount=' + amount + '&unit=' + encodeURIComponent(unit);
      const res = await fetch(url, { method: 'PUT' });
      if (!res.ok) { setStatus('Movement update failed.'); return; }
      updateDisplay(await res.json());
      setStatus('');
    } catch (e) {
      setStatus('Error: ' + e.message);
    }
  }

  async function commitDash() {
    if (!currentPlayer) return;
    if (initiativeRequired) { setInitiativeStatus('Enter initiative first.'); return; }
    try {
      const res = await fetch('/api/dash?player=' + encodeURIComponent(currentPlayer), { method: 'PUT' });
      if (!res.ok) { setStatus('Dash failed.'); return; }
      updateDisplay(await res.json());
      setStatus('');
    } catch (e) {
      setStatus('Error: ' + e.message);
    }
  }

  function disconnect() {
    closeApplicationUpdates();
    hideInitiativeDialog();
    currentPlayer = '';
    document.getElementById('game-screen').style.display = 'none';
    document.getElementById('connect-screen').style.display = '';
    loadPlayers();
  }

  function updateDisplay(data) {
    const card = document.getElementById('game-card');
    card.style.setProperty('--actor-color', data.color || 'transparent');
    card.classList.toggle('active', Boolean(data.active));
    document.getElementById('actor-name').textContent = data.name;
    document.getElementById('hp-input').value = data.hp;
    document.getElementById('ac-input').value = data.ac;
    document.getElementById('movement-input').value = formatMovementAmount(data.movementBaseAmount);
    document.getElementById('movement-unit').value = data.movementUnit || 'FEET';
    document.getElementById('movement-remaining').textContent =
      'Left ' + formatMovementAmount(data.movementRemainingAmount) + ' ' + (data.movementUnitLabel || unitLabel(data.movementUnit));
    if (data.initiativeRequired) {
      showInitiativeDialog();
    } else if (initiativeRequired) {
      hideInitiativeDialog();
    }
  }

  function setStatus(msg) {
    document.getElementById('status').textContent = msg;
  }

  function setInitiativeStatus(msg) {
    document.getElementById('initiative-status').textContent = msg;
  }

  function showInitiativeDialog() {
    if (!currentPlayer) return;
    const wasOpen = initiativeRequired;
    initiativeRequired = true;
    const modal = document.getElementById('initiative-modal');
    const input = document.getElementById('initiative-input');
    modal.style.display = 'flex';
    if (!wasOpen) {
      setInitiativeStatus('');
      input.value = '';
      setTimeout(() => input.focus(), 0);
    }
  }

  function hideInitiativeDialog() {
    initiativeRequired = false;
    document.getElementById('initiative-modal').style.display = 'none';
    setInitiativeStatus('');
  }

  async function commitInitiative() {
    if (!currentPlayer) return;
    const initiative = nonNegativeInt(document.getElementById('initiative-input').value);
    if (initiative === null) { setInitiativeStatus('Initiative must be 0 or higher.'); return; }
    try {
      const res = await fetch('/api/initiative?player=' + encodeURIComponent(currentPlayer) + '&initiative=' + initiative, { method: 'PUT' });
      if (!res.ok) { setInitiativeStatus('Initiative update failed.'); return; }
      updateDisplay(await res.json());
      hideInitiativeDialog();
      setStatus('');
    } catch (e) {
      setInitiativeStatus('Error: ' + e.message);
    }
  }

  async function refreshSelectedActor() {
    if (!currentPlayer) return;
    try {
      const res = await fetch('/api/state?player=' + encodeURIComponent(currentPlayer));
      if (res.status === 404) {
        setStatus('Character no longer available. Ask your DM to reconnect you.');
        return;
      }
      if (!res.ok) throw new Error('HTTP ' + res.status);
      updateDisplay(await res.json());
      setStatus('');
    } catch (e) {
      setStatus('Refresh error: ' + e.message);
    }
  }

  function openApplicationUpdates() {
    closeApplicationUpdates();
    updateEvents = new EventSource('/api/events');
    updateEvents.addEventListener('applicationUpdate', refreshSelectedActor);
    updateEvents.addEventListener('initiativeRequired', refreshSelectedActor);
    updateEvents.onerror = function() {
      if (currentPlayer) setStatus('Waiting for live updates...');
    };
  }

  function closeApplicationUpdates() {
    if (updateEvents) {
      updateEvents.close();
      updateEvents = null;
    }
  }

  function formatMovementAmount(value) {
    const number = Number(value || 0);
    if (Number.isInteger(number)) return String(number);
    return number.toFixed(1).replace(/\.0$/, '');
  }

  function unitLabel(unit) {
    return unit === 'METERS' ? 'm' : 'ft';
  }

  function nonNegativeInt(value) {
    if (value === '') return null;
    const number = Number(value);
    if (!Number.isInteger(number) || number < 0) return null;
    return number;
  }

  function esc(s) {
    return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  }

  // Prevent double-fire on touch devices (touchstart + click)
  document.querySelectorAll('.dpad-btn').forEach(btn => {
    btn.addEventListener('touchstart', function(e) { e.preventDefault(); }, { passive: false });
  });

  document.querySelectorAll('#hp-input, #ac-input').forEach(input => {
    input.addEventListener('keydown', function(e) {
      if (e.key === 'Enter') commitStats();
    });
  });

  document.getElementById('movement-input').addEventListener('keydown', function(e) {
    if (e.key === 'Enter') commitMovement();
  });

  document.getElementById('initiative-input').addEventListener('keydown', function(e) {
    if (e.key === 'Enter') commitInitiative();
  });

  loadPlayers();
</script>
</body>
</html>
    """.trimIndent()
}
