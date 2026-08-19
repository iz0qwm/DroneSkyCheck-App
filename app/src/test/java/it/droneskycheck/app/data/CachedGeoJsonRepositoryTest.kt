package it.droneskycheck.app.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedGeoJsonRepositoryTest {
    @Test
    fun staticGeoJsonCacheCanStayFreshForLongLivedDatasets() {
        FakeGeoJsonServer(
            listOf(
                FakeGeoJsonResponse(200, """{"type":"FeatureCollection","features":[{"id":"parks"}]}"""),
                FakeGeoJsonResponse(500, "server down")
            )
        ).use { server ->
            val cacheDir = Files.createTempDirectory("cached-geojson-test").toFile()
            val repository = CachedGeoJsonRepository(cacheDir)

            val first = repository.get(server.url, "parks-env", ttlMillis = Days180Millis)
            cacheDir.singleGeoJsonFile().setLastModified(System.currentTimeMillis() - Days30Millis)
            val second = repository.get(server.url, "parks-env", ttlMillis = Days180Millis)

            assertEquals(first.body, second.body)
            assertFalse(second.degraded)
            assertEquals(1, server.calls.size)
        }
    }

    @Test
    fun staticGeoJsonCacheFallsBackToLastGoodCopyWhenRefreshFails() {
        FakeGeoJsonServer(
            listOf(
                FakeGeoJsonResponse(200, """{"type":"FeatureCollection","features":[{"id":"cached"}]}"""),
                FakeGeoJsonResponse(503, "temporarily unavailable")
            )
        ).use { server ->
            val cacheDir = Files.createTempDirectory("cached-geojson-test").toFile()
            val repository = CachedGeoJsonRepository(cacheDir)

            val first = repository.get(server.url, "parks-env", ttlMillis = Days180Millis)
            cacheDir.singleGeoJsonFile().setLastModified(System.currentTimeMillis() - Days181Millis)
            val second = repository.get(server.url, "parks-env", ttlMillis = Days180Millis)

            assertEquals(first.body, second.body)
            assertTrue(second.degraded)
            assertEquals(2, server.calls.size)
        }
    }

    @Test
    fun staticGeoJsonCacheCanBeInvalidatedAfterCorruptRead() {
        FakeGeoJsonServer(
            listOf(
                FakeGeoJsonResponse(200, """{"type":"FeatureCollection","features":[{"id":"fresh"}]}""")
            )
        ).use { server ->
            val cacheDir = Files.createTempDirectory("cached-geojson-test").toFile()
            val repository = CachedGeoJsonRepository(cacheDir)
            val corruptCache = cacheDir.resolve("parks-env.geojson")
            corruptCache.writeText("""{"type":"FeatureCollection","features":[""", Charsets.UTF_8)

            val corrupt = repository.get(server.url, "parks-env", ttlMillis = Days180Millis)
            repository.invalidate("parks-env")
            val fresh = repository.get(server.url, "parks-env", ttlMillis = Days180Millis, forceRefresh = true)

            assertTrue(corrupt.body.endsWith("["))
            assertEquals("""{"type":"FeatureCollection","features":[{"id":"fresh"}]}""", fresh.body)
            assertEquals(1, server.calls.size)
        }
    }

    private fun java.io.File.singleGeoJsonFile() =
        listFiles { file -> file.extension == "geojson" }?.single()
            ?: error("Expected one cached GeoJSON file")

    private data class FakeGeoJsonResponse(
        val status: Int,
        val body: String
    )

    private class FakeGeoJsonServer(responses: List<FakeGeoJsonResponse>) : AutoCloseable {
        private val pendingResponses = ArrayDeque(responses)
        val calls = Collections.synchronizedList(mutableListOf<String>())
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        val url: String
            get() = "http://127.0.0.1:${server.address.port}/layer_parchi.geojson"

        init {
            server.createContext("/layer_parchi.geojson") { exchange ->
                calls += exchange.requestURI.toString()
                val response = synchronized(pendingResponses) {
                    pendingResponses.removeFirstOrNull()
                        ?: FakeGeoJsonResponse(500, "unexpected extra request")
                }
                val bytes = response.body.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(response.status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        override fun close() {
            server.stop(0)
        }
    }

    private companion object {
        const val Days30Millis = 30L * 24L * 60L * 60L * 1000L
        const val Days180Millis = 180L * 24L * 60L * 60L * 1000L
        const val Days181Millis = 181L * 24L * 60L * 60L * 1000L
    }
}
