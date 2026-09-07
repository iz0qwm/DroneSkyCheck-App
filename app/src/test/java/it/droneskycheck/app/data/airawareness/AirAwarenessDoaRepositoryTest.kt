package it.droneskycheck.app.data.airawareness

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AirAwarenessDoaRepositoryTest {
    @Test
    fun createSendsIdempotencyAndCloseCredentialsAndParsesServerTimes() {
        val http = RecordingHttpClient(
            response = AirAwarenessDoaHttpResponse(
                201,
                """{
                    "ok":true,
                    "doa":{
                        "id":"air-awareness-operation-0001",
                        "lat":41.9,
                        "lon":12.5,
                        "radiusM":300,
                        "startTime":"2026-09-07T10:00:00.000Z",
                        "endTime":"2026-09-07T11:00:00.000Z",
                        "active":true
                    }
                }""".trimIndent()
            )
        )
        val repository = AirAwarenessDoaRepository(
            endpointUrl = "https://example.test/appDoa",
            apiKey = "test-key",
            httpClient = http
        )

        val result = kotlinx.coroutines.runBlocking {
            repository.createDoa(
                operationId = "air-awareness-operation-0001",
                closeToken = "close-token-with-at-least-24-characters",
                lat = 41.9,
                lon = 12.5
            )
        }.getOrThrow()

        assertEquals(300.0, result.radiusMeters, 0.0)
        assertEquals(Instant.parse("2026-09-07T11:00:00Z").toEpochMilli(), result.endTimeMillis)
        assertEquals("close-token-with-at-least-24-characters", result.closeToken)
        assertTrue(http.lastBody!!.contains("\"action\":\"create\""))
        assertTrue(http.lastBody!!.contains("\"operationId\":\"air-awareness-operation-0001\""))
        assertEquals("test-key", http.lastHeaders?.get("x-api-key"))
    }

    @Test
    fun backendErrorDoesNotProducePublishedDoa() {
        val repository = AirAwarenessDoaRepository(
            endpointUrl = "https://example.test/appDoa",
            apiKey = "test-key",
            httpClient = RecordingHttpClient(AirAwarenessDoaHttpResponse(503, "{}"))
        )

        val result = kotlinx.coroutines.runBlocking {
            repository.createDoa(
                operationId = "air-awareness-operation-0001",
                closeToken = "close-token-with-at-least-24-characters",
                lat = 41.9,
                lon = 12.5
            )
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun closeUsesThePublishedDoaCredentials() {
        val http = RecordingHttpClient(AirAwarenessDoaHttpResponse(200, "{\"ok\":true}"))
        val repository = AirAwarenessDoaRepository(
            endpointUrl = "https://example.test/appDoa",
            apiKey = "test-key",
            httpClient = http
        )
        val doa = PublishedDoa(
            id = "air-awareness-operation-0001",
            closeToken = "close-token-with-at-least-24-characters",
            lat = 41.9,
            lon = 12.5,
            radiusMeters = 300.0,
            startTimeMillis = 1,
            endTimeMillis = 2
        )

        val result = kotlinx.coroutines.runBlocking { repository.closeDoa(doa) }

        assertTrue(result.isSuccess)
        assertTrue(http.lastBody!!.contains("\"action\":\"close\""))
        assertTrue(http.lastBody!!.contains(doa.closeToken))
    }
}

private class RecordingHttpClient(
    private val response: AirAwarenessDoaHttpResponse
) : AirAwarenessDoaHttpClient {
    var lastBody: String? = null
    var lastHeaders: Map<String, String>? = null

    override fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMillis: Int
    ): AirAwarenessDoaHttpResponse {
        lastBody = body
        lastHeaders = headers
        return response
    }
}
