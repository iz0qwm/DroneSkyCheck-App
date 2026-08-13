package it.droneskycheck.app.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZonesRepositoryTest {
    @Test
    fun tacticalCacheStaysFreshForThirtyDays() {
        FakeZonesServer(
            listOf(
                FakeZonesResponse(200, """{"type":"FeatureCollection","features":[{"id":"cached"}]}"""),
                FakeZonesResponse(500, "server down")
            )
        ).use { server ->
            val cacheDir = tempCacheDir()
            val repository = ZonesRepository(cacheDir = cacheDir, endpointUrl = server.url, apiKey = "test-key")

            val first = repository.getZonesResult(SampleBbox, "TACTICAL")
            cacheDir.singleGeoJsonFile().setLastModified(System.currentTimeMillis() - Days20Millis)
            val second = repository.getZonesResult(SampleBbox, "TACTICAL")

            assertEquals(first.body, second.body)
            assertFalse(second.degraded)
            assertEquals(1, server.calls.size)
        }
    }

    @Test
    fun tacticalCacheFallsBackToStaleFileWhenApiFails() {
        FakeZonesServer(
            listOf(
                FakeZonesResponse(200, """{"type":"FeatureCollection","features":[{"id":"cached"}]}"""),
                FakeZonesResponse(503, "temporarily unavailable")
            )
        ).use { server ->
            val cacheDir = tempCacheDir()
            val repository = ZonesRepository(cacheDir = cacheDir, endpointUrl = server.url, apiKey = "test-key")

            val first = repository.getZonesResult(SampleBbox, "TACTICAL")
            cacheDir.singleGeoJsonFile().setLastModified(System.currentTimeMillis() - Days31Millis)
            val second = repository.getZonesResult(SampleBbox, "TACTICAL")

            assertEquals(first.body, second.body)
            assertTrue(second.degraded)
            assertEquals(2, server.calls.size)
        }
    }

    @Test
    fun sameZonesRequestIsFetchedOnceWhenConcurrent() {
        FakeZonesServer(
            listOf(
                FakeZonesResponse(200, """{"type":"FeatureCollection","features":[{"id":"shared"}]}""", delayMillis = 250)
            )
        ).use { server ->
            val repository = ZonesRepository(cacheDir = tempCacheDir(), endpointUrl = server.url, apiKey = "test-key")
            val executor = Executors.newFixedThreadPool(3)
            val tasks = (1..3).map {
                executor.submit<CachedGeoJson> {
                    repository.getZonesResult(SampleBbox, "CORRIDOR")
                }
            }

            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            assertEquals(1, server.calls.size)
            assertEquals(1, tasks.map { it.get().body }.distinct().size)
            assertTrue(tasks.all { !it.get().degraded })
        }
    }

    private fun tempCacheDir() =
        Files.createTempDirectory("zones-repository-test").toFile()

    private fun java.io.File.singleGeoJsonFile() =
        listFiles { file -> file.extension == "geojson" }?.single()
            ?: error("Expected one cached GeoJSON file")

    private data class FakeZonesResponse(
        val status: Int,
        val body: String,
        val delayMillis: Long = 0
    )

    private class FakeZonesServer(responses: List<FakeZonesResponse>) : AutoCloseable {
        private val pendingResponses = ArrayDeque(responses)
        val calls = Collections.synchronizedList(mutableListOf<String>())
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        val url: String
            get() = "http://127.0.0.1:${server.address.port}/zones"

        init {
            server.createContext("/zones") { exchange ->
                calls += exchange.requestURI.toString()
                val response = synchronized(pendingResponses) {
                    pendingResponses.removeFirstOrNull()
                        ?: FakeZonesResponse(500, "unexpected extra request")
                }
                if (response.delayMillis > 0) Thread.sleep(response.delayMillis)
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

    private companion object {
        const val SampleBbox = "41.0,12.0,41.1,12.1"
        const val Days20Millis = 20L * 24L * 60L * 60L * 1000L
        const val Days31Millis = 31L * 24L * 60L * 60L * 1000L
    }
}
