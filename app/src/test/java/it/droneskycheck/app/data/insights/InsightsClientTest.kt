package it.droneskycheck.app.data.insights

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InsightsClientTest {
    private lateinit var server: HttpServer
    private lateinit var endpoint: String
    private val requests = mutableListOf<CapturedRequest>()

    @Before
    fun setUp() {
        requests.clear()
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/events") { exchange -> respond(exchange) }
            start()
        }
        endpoint = "http://127.0.0.1:${server.address.port}/events"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun sendsOnlyCataloguedAndroidOpenEventsAndStartsSessionOnce() = runBlocking {
        val consent = FakeConsent(true)
        val client = client(consent)

        assertTrue(client.trackToolOpened(InsightsTool.ZoneInformation))
        assertTrue(client.trackToolOpened(InsightsTool.AirTraffic))

        assertEquals(2, requests.size)
        val first = JSONObject(requests[0].body)
        assertEquals("ANDROID", first.getString("source"))
        assertEquals("GRANTED", requests[0].consentHeader)
        assertEquals(2, first.getJSONArray("events").length())
        assertEquals("session_started", first.getJSONArray("events").getJSONObject(0).getString("eventName"))
        val zoneProperties = first.getJSONArray("events").getJSONObject(1).getJSONObject("properties")
        assertEquals("ZONE_CHECK", zoneProperties.getString("toolClass"))
        assertEquals("ANDROID", zoneProperties.getString("platform"))
        assertFalse(requests[0].body.contains("latitude", ignoreCase = true))
        assertFalse(requests[0].body.contains("longitude", ignoreCase = true))

        val second = JSONObject(requests[1].body)
        assertEquals(1, second.getJSONArray("events").length())
        assertEquals(
            "AIR_TRAFFIC",
            second.getJSONArray("events").getJSONObject(0).getJSONObject("properties").getString("toolClass")
        )
    }

    @Test
    fun disabledConsentDoesNotSend() = runBlocking {
        val client = client(FakeConsent(false))

        assertFalse(client.trackToolOpened(InsightsTool.News))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun optingOutAndBackInStartsANewAnonymousSession() = runBlocking {
        val consent = FakeConsent(true)
        val client = client(consent)
        assertTrue(client.trackToolOpened(InsightsTool.Weather))

        client.onConsentChanged(false)
        assertFalse(client.trackToolOpened(InsightsTool.AiAssistant))
        client.onConsentChanged(true)
        assertTrue(client.trackToolOpened(InsightsTool.AiAssistant))

        assertEquals(2, requests.size)
        assertEquals(2, JSONObject(requests[1].body).getJSONArray("events").length())
    }

    private fun client(consent: FakeConsent) = InsightsClient(
        consent = consent,
        endpointUrl = endpoint,
        clock = Clock.fixed(Instant.parse("2026-09-04T14:00:00Z"), ZoneOffset.UTC),
        timeoutMillis = 1_000,
        sessionTokenFactory = { "sessiontoken12345678901234567890" },
        batchIdFactory = { "batchidentifier1234567890" }
    )

    private fun respond(exchange: HttpExchange) {
        requests += CapturedRequest(
            body = exchange.requestBody.bufferedReader().use { it.readText() },
            consentHeader = exchange.requestHeaders.getFirst("X-DSC-Insights-Consent")
        )
        val body = """{"accepted":1,"rejected":0,"reasonCodes":[],"receipt":"test"}"""
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }

    private data class CapturedRequest(val body: String, val consentHeader: String?)

    private class FakeConsent(private var enabled: Boolean) : InsightsConsentPreferences {
        override fun isEnabled(): Boolean = enabled
        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }
}
