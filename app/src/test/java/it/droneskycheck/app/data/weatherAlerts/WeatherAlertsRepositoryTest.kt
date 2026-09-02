package it.droneskycheck.app.data.weatherAlerts

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WeatherAlertsRepositoryTest {
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private var alertsStatus = 200
    private var alertsBody = validJson
    private var responseDelayMillis = 0L

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/alerts") { exchange -> respondAlerts(exchange) }
            createContext("/status") { exchange ->
                respond(exchange, 200, """{"criticality_revision":"c1","vigilance_revision":"v1"}""")
            }
            start()
        }
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun parsesSuccessfulResponseAndMissingFutureFields() = runBlocking {
        val repository = repository()

        val result = repository.getAlerts(44.49, 11.34) as WeatherAlertLoadResult.Available

        assertFalse(result.stale)
        assertEquals("Collina bolognese", result.response.criticality?.zoneName)
        assertEquals(CriticalityLevel.YELLOW, result.response.criticality?.periods?.get("TODAY")?.maximumLevel)
        assertEquals(
            Instant.parse("2026-09-02T10:00:00Z"),
            result.response.criticality?.periods?.get("TODAY")?.onset
        )
        assertNull(result.response.sources)
    }

    @Test
    fun parsesStatusRevisions() = runBlocking {
        val status = repository().getStatus().getOrThrow()

        assertEquals("c1", status.criticalityRevision)
        assertEquals("v1", status.vigilanceRevision)
    }

    @Test
    fun malformedJsonAndHttpErrorAreUnavailable() = runBlocking {
        alertsBody = "not-json"
        assertEquals(WeatherAlertLoadResult.Unavailable, repository().getAlerts(44.49, 11.34))

        alertsStatus = 503
        assertEquals(WeatherAlertLoadResult.Unavailable, repository().getAlerts(44.49, 11.34))
    }

    @Test
    fun timeoutIsUnavailable() = runBlocking {
        responseDelayMillis = 150

        val result = repository(timeoutMillis = 25).getAlerts(44.49, 11.34)

        assertEquals(WeatherAlertLoadResult.Unavailable, result)
    }

    @Test
    fun cancellationDoesNotPublishAResult() = runBlocking {
        responseDelayMillis = 150
        var cancelled = false

        try {
            withTimeout(20) { repository(timeoutMillis = 1_000).getAlerts(44.49, 11.34) }
        } catch (_: TimeoutCancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
    }

    @Test
    fun cachedFallbackExpiresAfterThirtyMinutesAndIsCoordinateScoped() = runBlocking {
        val clock = MutableClock(Instant.parse("2026-09-02T12:00:00Z"))
        val repository = repository(clock = clock)
        assertTrue(repository.getAlerts(44.49, 11.34) is WeatherAlertLoadResult.Available)

        alertsStatus = 503
        clock.now = clock.now.plusSeconds(10 * 60)
        val stale = repository.getAlerts(44.49, 11.34) as WeatherAlertLoadResult.Available
        assertTrue(stale.stale)
        assertEquals(
            WeatherAlertLoadResult.Unavailable,
            repository.getAlerts(41.90, 12.50)
        )

        clock.now = clock.now.plusSeconds(21 * 60)
        assertEquals(
            WeatherAlertLoadResult.Unavailable,
            repository.getAlerts(44.49, 11.34)
        )
    }

    private fun repository(
        clock: Clock = MutableClock(Instant.parse("2026-09-02T12:00:00Z")),
        timeoutMillis: Int = 1_000
    ) = WeatherAlertsRepository(
        alertsEndpoint = "$baseUrl/alerts",
        statusEndpoint = "$baseUrl/status",
        clock = clock,
        timeoutMillis = timeoutMillis
    )

    private fun respondAlerts(exchange: HttpExchange) {
        if (responseDelayMillis > 0) Thread.sleep(responseDelayMillis)
        respond(exchange, alertsStatus, alertsBody)
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        runCatching {
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        exchange.close()
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private companion object {
        val validJson = """
            {
              "point": {"lat": 44.49, "lon": 11.34},
              "criticality": {
                "zone_code": "Emil-C2",
                "zone_name": "Collina bolognese",
                "periods": {
                  "TODAY": {
                    "onset": "2026-09-02T12:00:00+02:00",
                    "expires": "2026-09-02T23:59:59+02:00",
                    "overall_level": "YELLOW",
                    "risks": {"thunderstorm": "YELLOW"}
                  }
                }
              },
              "vigilance": {
                "zone_id": 84,
                "zone_name": "Appennino emiliano romagnolo",
                "periods": {"TODAY": {"precipitation": {"level": "WEAK"}}}
              },
              "future_field": {"ignored": true}
            }
        """.trimIndent()
    }
}
