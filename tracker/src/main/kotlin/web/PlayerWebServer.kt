package com.tabletopcontrol.new_tracker.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.TokenAddedEvent
import com.tabletopcontrol.core.TokenMovedEvent
import com.tabletopcontrol.core.TokensResetEvent
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorTracker
import com.tabletopcontrol.new_tracker.model.ActorType
import javafx.application.Platform
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A lightweight embedded HTTP server that exposes a mobile-optimised web companion
 * for players. Players connect via a browser, select their character name (which
 * matches the [Actor.name] field of a PC actor in the tracker), and use four
 * directional buttons to move their map token one grid cell at a time.
 *
 * The server subscribes to [TokenMovedEvent] to maintain an up-to-date position
 * cache so that each directional step is relative to the token's current position.
 *
 * All [TokenMovedEvent] publications are dispatched on the JavaFX Application Thread
 * via [Platform.runLater] so that map renderers receive them correctly.
 *
 * @param actorTracker the live [ActorTracker] instance used to resolve PC actor names.
 * @param port         TCP port to listen on; defaults to [DEFAULT_PORT].
 */
class PlayerWebServer(
    private val actorTracker: ActorTracker,
    val port: Int = DEFAULT_PORT,
) {
    companion object {
        const val DEFAULT_PORT = 8765
    }

    private var server: HttpServer? = null
    private var executor: ExecutorService? = null
    private val subscriptions = mutableListOf<EventBus.Subscription>()

    /** Cache of current token positions: tokenId → (col, row). */
    private val tokenPositions = mutableMapOf<String, Pair<Int, Int>>()

    /** Whether the server is currently running. */
    val isRunning: Boolean get() = server != null

    /**
     * Starts the HTTP server and begins subscribing to token events.
     * If the server is already running this is a no-op.
     */
    fun start() {
        if (server != null) return

        subscriptions += EventBus.subscribe<TokenAddedEvent> { event ->
            // Register a new token at (0,0) until its actual position is reported via a TokenMovedEvent.
            // Tokens placed on the map by the DM will update this cache when their position events arrive.
            tokenPositions.putIfAbsent(event.id, Pair(0, 0))
        }
        subscriptions += EventBus.subscribe<TokenMovedEvent> { event ->
            tokenPositions[event.id] = Pair(event.col, event.row)
        }
        subscriptions += EventBus.subscribe<TokensResetEvent> {
            tokenPositions.clear()
        }

        val srv = HttpServer.create(InetSocketAddress(port), 0)
        val exec = Executors.newCachedThreadPool()
        srv.createContext("/api/players", ::handlePlayers)
        srv.createContext("/api/state", ::handleState)
        srv.createContext("/api/move", ::handleMove)
        srv.createContext("/", ::handleIndex)
        srv.executor = exec
        srv.start()
        server = srv
        executor = exec
    }

    /**
     * Stops the HTTP server and cleans up all subscriptions.
     * Safe to call multiple times.
     */
    fun stop() {
        server?.stop(0)
        server = null
        executor?.shutdownNow()
        executor = null
        subscriptions.forEach { it.unsubscribe() }
        subscriptions.clear()
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
     * Response JSON: `{"name":"…","hp":0,"ac":0,"col":0,"row":0}`
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
        respondJson(
            exchange, 200,
            """{"name":"${actor.name.jsonEscape()}","hp":${actor.hp},"ac":${actor.ac},"col":$col,"row":$row}""",
        )
    }

    /**
     * `PUT /api/move?player=<name>&dir=<n|s|e|w>` — moves the player's token one cell
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
        val (col, row) = tokenPositions[actor.id] ?: Pair(0, 0)
        val (newCol, newRow) = when (dir.lowercase()) {
            "n" -> Pair(col, row - 1)
            "s" -> Pair(col, row + 1)
            "w" -> Pair(col - 1, row)
            "e" -> Pair(col + 1, row)
            else -> {
                respond(exchange, 400, "Invalid direction; use n, s, e, or w")
                return
            }
        }
        val newColClamped = newCol.coerceAtLeast(0)
        val newRowClamped = newRow.coerceAtLeast(0)
        tokenPositions[actor.id] = Pair(newColClamped, newRowClamped)
        Platform.runLater {
            EventBus.publish(TokenMovedEvent(actor.id, actor.name, newColClamped, newRowClamped))
        }
        respondJson(
            exchange, 200,
            """{"name":"${actor.name.jsonEscape()}","hp":${actor.hp},"ac":${actor.ac},"col":$newColClamped,"row":$newRowClamped}""",
        )
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

    private fun findPlayerActor(actorName: String): Actor? =
        actorTracker.actorList.firstOrNull { actor ->
            actor.actorType == ActorType.PC && actor.name == actorName
        }

    private fun String.jsonEscape(): String = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

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
      border-radius: 16px;
      padding: 1.75rem;
      width: 100%;
      max-width: 360px;
      box-shadow: 0 8px 32px rgba(0,0,0,0.4);
    }
    select, input[type="text"] {
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
    select:focus, input[type="text"]:focus { outline: none; border-color: var(--accent); }
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
    .info-bar {
      display: flex;
      justify-content: space-between;
      background: #0f3460;
      border-radius: 10px;
      padding: 0.75rem 1rem;
      margin-bottom: 1.5rem;
      font-size: 0.95rem;
    }
    .info-item { display: flex; flex-direction: column; align-items: center; gap: 2px; }
    .info-label { font-size: 0.7rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em; }
    .info-value { font-size: 1.1rem; font-weight: 700; }
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
  <div class="card">
    <div class="actor-name" id="actor-name">—</div>
    <div class="info-bar">
      <div class="info-item"><span class="info-label">HP</span><span class="info-value" id="info-hp">—</span></div>
      <div class="info-item"><span class="info-label">AC</span><span class="info-value" id="info-ac">—</span></div>
      <div class="info-item"><span class="info-label">Col</span><span class="info-value" id="info-col">—</span></div>
      <div class="info-item"><span class="info-label">Row</span><span class="info-value" id="info-row">—</span></div>
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

<script>
  let currentPlayer = '';

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
      currentPlayer = name;
      updateDisplay(await res.json());
      document.getElementById('connect-screen').style.display = 'none';
      document.getElementById('game-screen').style.display = '';
    } catch (e) {
      setStatus('Connection error: ' + e.message);
    }
  }

  async function handleMove(dir) {
    if (!currentPlayer) return;
    try {
      const res = await fetch('/api/move?player=' + encodeURIComponent(currentPlayer) + '&dir=' + dir, { method: 'PUT' });
      if (!res.ok) { setStatus('Move failed.'); return; }
      updateDisplay(await res.json());
      setStatus('');
    } catch (e) {
      setStatus('Error: ' + e.message);
    }
  }

  function disconnect() {
    currentPlayer = '';
    document.getElementById('game-screen').style.display = 'none';
    document.getElementById('connect-screen').style.display = '';
    loadPlayers();
  }

  function updateDisplay(data) {
    document.getElementById('actor-name').textContent = data.name;
    document.getElementById('info-hp').textContent = data.hp;
    document.getElementById('info-ac').textContent = data.ac;
    document.getElementById('info-col').textContent = data.col;
    document.getElementById('info-row').textContent = data.row;
  }

  function setStatus(msg) {
    document.getElementById('status').textContent = msg;
  }

  function esc(s) {
    return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  }

  // Prevent double-fire on touch devices (touchstart + click)
  document.querySelectorAll('.dpad-btn').forEach(btn => {
    btn.addEventListener('touchstart', function(e) { e.preventDefault(); }, { passive: false });
  });

  loadPlayers();
</script>
</body>
</html>
    """.trimIndent()
}
