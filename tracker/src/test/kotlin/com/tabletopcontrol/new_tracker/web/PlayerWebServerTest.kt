package com.tabletopcontrol.new_tracker.web

import com.tabletopcontrol.core.EventBus
import com.tabletopcontrol.core.TokenMoveDirection
import com.tabletopcontrol.core.TokenMoveRequestedEvent
import com.tabletopcontrol.core.TokenMovedEvent
import com.tabletopcontrol.core.ui.color.ColorHexCodec
import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorTracker
import com.tabletopcontrol.new_tracker.model.ActorType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class PlayerWebServerTest {
    @AfterEach
    fun tearDown() {
        EventBus.clear()
    }

    @Test
    fun `players endpoint lists PC actor names`() {
        val server = PlayerWebServer(
            actorTracker = ActorTracker(
                mutableListOf(
                    Actor(name = "Aria", actorType = ActorType.PC),
                    Actor(name = "Goblin", actorType = ActorType.NPC),
                    Actor(name = "Bryn", actorType = ActorType.PC),
                    Actor(name = "Aria", actorType = ActorType.PC),
                ),
            ),
            port = freePort(),
        )

        try {
            server.start()

            val response = get(server.port, "/api/players")

            assertEquals(200, response.statusCode())
            assertEquals("""["Aria","Bryn"]""", response.body())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `state endpoint resolves PCs by actor name`() {
        val server = PlayerWebServer(
            actorTracker = ActorTracker(
                mutableListOf(
                    Actor(name = "Aria", hp = 30, ac = 14, actorType = ActorType.PC),
                    Actor(name = "Goblin", hp = 7, ac = 15, actorType = ActorType.NPC),
                ),
            ),
            port = freePort(),
        )

        try {
            server.start()

            val pcResponse = get(server.port, "/api/state?player=Aria")
            val npcResponse = get(server.port, "/api/state?player=Goblin")

            assertEquals(200, pcResponse.statusCode())
            assertEquals(
                """{"name":"Aria","hp":30,"ac":14,"initiative":null,"initiativeRequired":false,"col":0,"row":0,"active":false,"color":"#808080"}""",
                pcResponse.body(),
            )
            assertEquals(404, npcResponse.statusCode())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `stats endpoint updates HP and AC on the application actor`() {
        val tracker = ActorTracker(
            mutableListOf(
                Actor(name = "Aria", hp = 30, ac = 14, actorType = ActorType.PC),
            ),
        )
        val server = PlayerWebServer(
            actorTracker = tracker,
            port = freePort(),
            applicationDispatcher = { action -> action() },
        )

        try {
            server.start()

            val response = put(server.port, "/api/stats?player=Aria&hp=22&ac=16")

            assertEquals(200, response.statusCode())
            assertEquals(22, tracker.actorList.single().hp)
            assertEquals(16, tracker.actorList.single().ac)
            assertEquals(
                """{"name":"Aria","hp":22,"ac":16,"initiative":null,"initiativeRequired":false,"col":0,"row":0,"active":false,"color":"#808080"}""",
                response.body(),
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun `initiative request blocks player state until initiative is submitted`() {
        val tracker = ActorTracker(
            mutableListOf(
                Actor(name = "Aria", hp = 30, ac = 14, actorType = ActorType.PC),
            ),
        )
        val server = PlayerWebServer(
            actorTracker = tracker,
            port = freePort(),
            applicationDispatcher = { action -> action() },
        )

        try {
            server.start()
            server.requestInitiatives()

            val requiredState = get(server.port, "/api/state?player=Aria")
            val response = put(server.port, "/api/initiative?player=Aria&initiative=18")

            assertEquals(200, requiredState.statusCode())
            assertEquals(
                """{"name":"Aria","hp":30,"ac":14,"initiative":null,"initiativeRequired":true,"col":0,"row":0,"active":false,"color":"#808080"}""",
                requiredState.body(),
            )
            assertEquals(200, response.statusCode())
            assertEquals(18, tracker.actorList.single().initiative)
            assertEquals(
                """{"name":"Aria","hp":30,"ac":14,"initiative":18,"initiativeRequired":false,"col":0,"row":0,"active":true,"color":"#808080"}""",
                response.body(),
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun `initiative endpoint resolves ties between PCs`() {
        val tracker = ActorTracker()
        tracker.addActor(Actor(id = "pc-1", name = "Aria", initiative = 12, actorType = ActorType.PC))
        tracker.addActor(Actor(id = "pc-2", name = "Bryn", actorType = ActorType.PC))
        val resolverCalls = mutableListOf<List<String>>()
        val server = PlayerWebServer(
            actorTracker = tracker,
            port = freePort(),
            applicationDispatcher = { action -> action() },
        ).apply {
            initiativeTieResolver = { actorsAtInitiative, initiative, movedActorId ->
                assertEquals(12, initiative)
                assertEquals("pc-2", movedActorId)
                resolverCalls += actorsAtInitiative.map(Actor::name)
                listOf(actorsAtInitiative[1], actorsAtInitiative[0])
            }
        }

        try {
            server.start()

            val response = put(server.port, "/api/initiative?player=Bryn&initiative=12")

            assertEquals(200, response.statusCode())
            assertEquals(listOf(listOf("Aria", "Bryn")), resolverCalls)
            assertEquals(listOf("Bryn", "Aria"), tracker.actorList.take(2).map(Actor::name))
        } finally {
            server.stop()
        }
    }

    @Test
    fun `initiative endpoint resolves ties between PCs and NPCs`() {
        val tracker = ActorTracker()
        tracker.addActor(Actor(id = "npc-1", name = "Guard", initiative = 11, actorType = ActorType.NPC))
        tracker.addActor(Actor(id = "pc-1", name = "Aria", actorType = ActorType.PC))
        val resolverCalls = mutableListOf<List<String>>()
        val server = PlayerWebServer(
            actorTracker = tracker,
            port = freePort(),
            applicationDispatcher = { action -> action() },
        ).apply {
            initiativeTieResolver = { actorsAtInitiative, initiative, movedActorId ->
                assertEquals(11, initiative)
                assertEquals("pc-1", movedActorId)
                resolverCalls += actorsAtInitiative.map(Actor::name)
                actorsAtInitiative
            }
        }

        try {
            server.start()

            val response = put(server.port, "/api/initiative?player=Aria&initiative=11")

            assertEquals(200, response.statusCode())
            assertEquals(listOf(listOf("Guard", "Aria")), resolverCalls)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `initiative request does not block PCs when only NPCs are missing initiative`() {
        val tracker = ActorTracker()
        tracker.addActor(Actor(name = "Aria", hp = 30, ac = 14, initiative = 18, actorType = ActorType.PC))
        tracker.addActor(Actor(name = "Goblin", hp = 7, ac = 15, actorType = ActorType.NPC))
        val colorHex = ColorHexCodec.colorToHex(tracker.actorList.first { it.name == "Aria" }.color)
        val server = PlayerWebServer(
            actorTracker = tracker,
            port = freePort(),
        )

        try {
            server.start()
            server.requestInitiatives()

            val response = get(server.port, "/api/state?player=Aria")

            assertEquals(200, response.statusCode())
            assertEquals(
                """{"name":"Aria","hp":30,"ac":14,"initiative":18,"initiativeRequired":false,"col":0,"row":0,"active":true,"color":"$colorHex"}""",
                response.body(),
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun `state endpoint marks current initiative actor with token color`() {
        val tracker = ActorTracker()
        val actor = Actor(name = "Aria", hp = 30, ac = 14, initiative = 10, actorType = ActorType.PC)
        tracker.addActor(actor)
        val colorHex = ColorHexCodec.colorToHex(tracker.actorList.single().color)
        val server = PlayerWebServer(
            actorTracker = tracker,
            port = freePort(),
        )

        try {
            server.start()

            val response = get(server.port, "/api/state?player=Aria")

            assertEquals(200, response.statusCode())
            assertEquals(
                """{"name":"Aria","hp":30,"ac":14,"initiative":10,"initiativeRequired":false,"col":0,"row":0,"active":true,"color":"$colorHex"}""",
                response.body(),
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun `move endpoint delegates relative movement to map owner`() {
        val tracker = ActorTracker(
            mutableListOf(
                Actor(id = "actor-1", name = "Aria", hp = 30, ac = 14, actorType = ActorType.PC),
            ),
        )
        val requests = mutableListOf<TokenMoveRequestedEvent>()
        EventBus.subscribe<TokenMoveRequestedEvent> { event ->
            requests += event
            EventBus.publish(TokenMovedEvent(event.id, event.name, 5, 7))
        }
        val server = PlayerWebServer(
            actorTracker = tracker,
            port = freePort(),
            applicationDispatcher = { action -> action() },
        )

        try {
            server.start()

            val response = put(server.port, "/api/move?player=Aria&dir=s")

            assertEquals(
                listOf(TokenMoveRequestedEvent("actor-1", "Aria", TokenMoveDirection.SOUTH)),
                requests,
            )
            assertEquals(200, response.statusCode())
            assertEquals(
                """{"name":"Aria","hp":30,"ac":14,"initiative":null,"initiativeRequired":false,"col":5,"row":7,"active":false,"color":"#808080"}""",
                response.body(),
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun `events endpoint sends application update events`() {
        val server = PlayerWebServer(
            actorTracker = ActorTracker(),
            port = freePort(),
        )

        try {
            server.start()

            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:${server.port}/api/events"))
                .GET()
                .build()
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream())

            response.body().bufferedReader().use { reader ->
                assertEquals(200, response.statusCode())
                assertEquals("event: applicationUpdate", reader.readLine())
                assertEquals("""data: {"activeActorId":null,"activeActorName":null}""", reader.readLine())
                assertEquals("", reader.readLine())
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `initiative requests are sent to connected event streams`() {
        val server = PlayerWebServer(
            actorTracker = ActorTracker(
                mutableListOf(
                    Actor(name = "Aria", actorType = ActorType.PC),
                ),
            ),
            port = freePort(),
        )

        try {
            server.start()

            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:${server.port}/api/events"))
                .GET()
                .build()
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream())

            response.body().bufferedReader().use { reader ->
                assertEquals(200, response.statusCode())
                assertEquals("event: applicationUpdate", reader.readLine())
                assertEquals("""data: {"activeActorId":null,"activeActorName":null}""", reader.readLine())
                assertEquals("", reader.readLine())

                server.requestInitiatives()

                assertEquals("event: initiativeRequired", reader.readLine())
                assertEquals("""data: {}""", reader.readLine())
                assertEquals("", reader.readLine())
            }
        } finally {
            server.stop()
        }
    }

    private fun get(port: Int, path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:$port$path"))
            .GET()
            .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun put(port: Int, path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:$port$path"))
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun freePort(): Int =
        ServerSocket(0).use { socket -> socket.localPort }
}
