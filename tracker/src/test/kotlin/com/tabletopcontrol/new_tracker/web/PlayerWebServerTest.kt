package com.tabletopcontrol.new_tracker.web

import com.tabletopcontrol.core.EventBus
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
                """{"name":"Aria","hp":30,"ac":14,"col":0,"row":0,"active":false,"color":"#808080"}""",
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
                """{"name":"Aria","hp":22,"ac":16,"col":0,"row":0,"active":false,"color":"#808080"}""",
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
                """{"name":"Aria","hp":30,"ac":14,"col":0,"row":0,"active":true,"color":"$colorHex"}""",
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
