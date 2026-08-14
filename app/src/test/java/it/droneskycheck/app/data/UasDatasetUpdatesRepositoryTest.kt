package it.droneskycheck.app.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UasDatasetUpdatesRepositoryTest {
    @Test
    fun parsesUpdatesJsonCountsAndTimestamps() {
        val updates = parseUasDatasetUpdates(
            json = """
                {
                  "timestamp": "2026-08-14T08:23:49.067110Z",
                  "added": [{ "id": "a" }],
                  "removed": [],
                  "modified": [{ "id": "m1" }, { "id": "m2" }]
                }
            """.trimIndent(),
            cachedOnDeviceAt = "2026-08-14T08:24:02Z",
            degraded = false
        )

        assertEquals("2026-08-14T08:23:49.067110Z", updates.sourceUpdatedAt)
        assertEquals(1, updates.addedCount)
        assertEquals(0, updates.removedCount)
        assertEquals(2, updates.modifiedCount)
        assertEquals("2026-08-14T08:24:02Z", updates.cachedOnDeviceAt)
        assertFalse(updates.degraded)
    }

    @Test
    fun fallsBackToCachedUpdatesWhenRemoteFails() {
        FakeUpdatesServer(
            listOf(
                FakeUpdatesResponse(200, """{"timestamp":"2026-08-14T08:23:49Z","added":[],"removed":[],"modified":[]}"""),
                FakeUpdatesResponse(503, "down")
            )
        ).use { server ->
            val repository = UasDatasetUpdatesRepository(
                cacheDir = Files.createTempDirectory("uas-updates-test").toFile(),
                updatesUrl = server.url,
                clock = Clock.fixed(Instant.parse("2026-08-14T08:24:02Z"), ZoneOffset.UTC)
            )

            val first = repository.getUpdates()
            val second = repository.getUpdates()

            assertEquals("2026-08-14T08:23:49Z", first?.sourceUpdatedAt)
            assertEquals("2026-08-14T08:23:49Z", second?.sourceUpdatedAt)
            assertEquals("2026-08-14T08:24:02Z", second?.cachedOnDeviceAt)
            assertTrue(second?.degraded == true)
            assertEquals(2, server.calls)
        }
    }

    private data class FakeUpdatesResponse(
        val status: Int,
        val body: String
    )

    private class FakeUpdatesServer(responses: List<FakeUpdatesResponse>) : AutoCloseable {
        private val pendingResponses = ArrayDeque(responses)
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var calls: Int = 0
            private set

        val url: String
            get() = "http://127.0.0.1:${server.address.port}/updates.json"

        init {
            server.createContext("/updates.json") { exchange ->
                calls += 1
                val response = synchronized(pendingResponses) {
                    pendingResponses.removeFirstOrNull()
                        ?: FakeUpdatesResponse(500, "unexpected extra request")
                }
                val bytes = response.body.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(response.status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.executor = Executors.newCachedThreadPool()
            server.start()
        }

        override fun close() {
            server.stop(0)
        }
    }
}
