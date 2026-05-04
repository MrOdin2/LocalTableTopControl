package com.tabletopcontrol.new_tracker.web

import com.tabletopcontrol.new_tracker.model.Actor
import com.tabletopcontrol.new_tracker.model.ActorTracker
import com.tabletopcontrol.new_tracker.model.ActorType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class PlayerWebServerTest {
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
            assertEquals("""{"name":"Aria","hp":30,"ac":14,"col":0,"row":0}""", pcResponse.body())
            assertEquals(404, npcResponse.statusCode())
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

    private fun freePort(): Int =
        ServerSocket(0).use { socket -> socket.localPort }
}
