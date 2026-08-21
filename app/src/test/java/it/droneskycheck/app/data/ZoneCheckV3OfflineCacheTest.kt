package it.droneskycheck.app.data

import java.net.URL
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneCheckV3OfflineCacheTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-22T15:30:00Z"), ZoneOffset.UTC)

    @Test
    fun onlineSuccessReturnsFreshResultAndUpdatesCache() {
        val store = InMemoryCachedZoneAnalysisStore()
        val repository = repository(
            store = store,
            httpClient = FakeZoneCheckHttpClient(
                ZoneCheckV3HttpResponse(200, zoneJson(lat = 41.9, lon = 12.5, zoneId = "X"))
            )
        )

        val response = repository.check(41.9, 12.5)

        assertNull(response.offlineCache)
        assertEquals("X", response.zones.single().id)
        assertEquals(1, store.savedCount)
        assertEquals(clock.millis(), store.getBlocking(41.9, 12.5)?.analyzedAtUtc)
    }

    @Test
    fun networkFailureUsesCachedResultOnlyForSameNormalizedPoint() {
        val store = InMemoryCachedZoneAnalysisStore()
        store.put(41.9, 12.5, zoneJson(lat = 41.9, lon = 12.5, zoneId = "X"), clock.millis())
        val repository = repository(
            store = store,
            httpClient = FakeZoneCheckHttpClient(
                error = ZoneCheckV3RepositoryError.Network("offline")
            )
        )

        val response = repository.check(41.900001, 12.500004)

        assertEquals("X", response.zones.single().id)
        assertEquals(clock.millis(), response.offlineCache?.analyzedAtUtcMillis)
        assertEquals(ZoneCheckOfflineFallbackReason.NETWORK_FAILURE, response.offlineCache?.reason)
    }

    @Test
    fun networkFailureDoesNotReuseCacheForDifferentPoint() {
        val store = InMemoryCachedZoneAnalysisStore()
        store.put(41.9, 12.5, zoneJson(lat = 41.9, lon = 12.5, zoneId = "X"), clock.millis())
        val repository = repository(
            store = store,
            httpClient = FakeZoneCheckHttpClient(
                error = ZoneCheckV3RepositoryError.Network("offline")
            )
        )

        assertThrows(ZoneCheckV3RepositoryError.Network::class.java) {
            repository.check(41.90002, 12.5)
        }
    }

    @Test
    fun timeoutUsesCachedResultWithTimeoutReason() {
        val store = InMemoryCachedZoneAnalysisStore()
        store.put(41.9, 12.5, zoneJson(lat = 41.9, lon = 12.5, zoneId = "T"), clock.millis())
        val repository = repository(
            store = store,
            httpClient = FakeZoneCheckHttpClient(
                error = ZoneCheckV3RepositoryError.Timeout("timeout")
            )
        )

        val response = repository.check(41.9, 12.5)

        assertEquals("T", response.zones.single().id)
        assertEquals(ZoneCheckOfflineFallbackReason.TIMEOUT, response.offlineCache?.reason)
    }

    @Test
    fun serverErrorUsesCachedResultWithServerUnavailableReason() {
        val store = InMemoryCachedZoneAnalysisStore()
        store.put(41.9, 12.5, zoneJson(lat = 41.9, lon = 12.5, zoneId = "S"), clock.millis())
        val repository = repository(
            store = store,
            httpClient = FakeZoneCheckHttpClient(
                ZoneCheckV3HttpResponse(503, """{"error":"unavailable"}""")
            )
        )

        val response = repository.check(41.9, 12.5)

        assertEquals("S", response.zones.single().id)
        assertEquals(ZoneCheckOfflineFallbackReason.SERVER_UNAVAILABLE, response.offlineCache?.reason)
    }

    @Test
    fun clientErrorsAndParsingErrorsDoNotUseCachedResult() {
        val store = InMemoryCachedZoneAnalysisStore()
        store.put(41.9, 12.5, zoneJson(lat = 41.9, lon = 12.5, zoneId = "X"), clock.millis())

        val clientErrorRepository = repository(
            store = store,
            httpClient = FakeZoneCheckHttpClient(
                ZoneCheckV3HttpResponse(400, """{"error":"bad request"}""")
            )
        )
        val parsingErrorRepository = repository(
            store = store,
            httpClient = FakeZoneCheckHttpClient(
                ZoneCheckV3HttpResponse(200, "{")
            )
        )

        assertThrows(ZoneCheckV3RepositoryError.HttpError::class.java) {
            clientErrorRepository.check(41.9, 12.5)
        }
        assertThrows(ZoneCheckV3RepositoryError.InvalidJson::class.java) {
            parsingErrorRepository.check(41.9, 12.5)
        }
    }

    @Test
    fun restoredNetworkAlwaysFetchesOnlineAndReplacesCachedResult() {
        val store = InMemoryCachedZoneAnalysisStore()
        store.put(41.9, 12.5, zoneJson(lat = 41.9, lon = 12.5, zoneId = "OLD"), 1_000L)
        val repository = repository(
            store = store,
            httpClient = FakeZoneCheckHttpClient(
                ZoneCheckV3HttpResponse(200, zoneJson(lat = 41.9, lon = 12.5, zoneId = "NEW"))
            )
        )

        val response = repository.check(41.9, 12.5)

        assertEquals("NEW", response.zones.single().id)
        assertNull(response.offlineCache)
        assertTrue(store.getBlocking(41.9, 12.5)?.responseJson?.contains("NEW") == true)
    }

    private fun repository(
        store: CachedZoneAnalysisStore,
        httpClient: ZoneCheckV3HttpClient
    ): ZoneCheckV3Repository =
        ZoneCheckV3Repository(
            endpointUrl = "https://example.test/appZoneCheckV3",
            apiKey = "test-key",
            cacheStore = store,
            clock = clock,
            httpClient = httpClient
        )
}

private class FakeZoneCheckHttpClient(
    private val response: ZoneCheckV3HttpResponse? = null,
    private val error: ZoneCheckV3RepositoryError? = null
) : ZoneCheckV3HttpClient {
    override fun get(url: URL, apiKey: String): ZoneCheckV3HttpResponse {
        error?.let { throw it }
        return requireNotNull(response)
    }
}

private class InMemoryCachedZoneAnalysisStore : CachedZoneAnalysisStore {
    private val entries = linkedMapOf<String, CachedZoneAnalysis>()
    var savedCount = 0
        private set

    override suspend fun get(lat: Double, lon: Double): CachedZoneAnalysis? =
        entries[key(lat, lon)]

    override suspend fun upsert(
        lat: Double,
        lon: Double,
        analyzedAtUtc: Long,
        responseJson: String,
        response: ZoneCheckV3Response
    ) {
        put(lat, lon, responseJson, analyzedAtUtc)
        savedCount += 1
    }

    fun put(lat: Double, lon: Double, responseJson: String, analyzedAtUtc: Long) {
        val normalizedLat = normalizeCachedZoneCoordinate(lat)
        val normalizedLon = normalizeCachedZoneCoordinate(lon)
        entries[key(lat, lon)] = CachedZoneAnalysis(
            id = cachedZoneAnalysisId(normalizedLat, normalizedLon),
            lat = lat,
            lon = lon,
            analyzedAtUtc = analyzedAtUtc,
            responseJson = responseJson
        )
    }

    fun getBlocking(lat: Double, lon: Double): CachedZoneAnalysis? =
        entries[key(lat, lon)]

    private fun key(lat: Double, lon: Double): String =
        cachedZoneAnalysisId(
            normalizeCachedZoneCoordinate(lat),
            normalizeCachedZoneCoordinate(lon)
        )
}

private fun zoneJson(lat: Double, lon: Double, zoneId: String): String =
    """
    {
      "position": { "lat": $lat, "lon": $lon },
      "verdict": {
        "status": "OPEN",
        "maxAltitudeMetersAgl": 120,
        "source": "BASE",
        "explanation": "Fixture"
      },
      "zones": [
        {
          "identity": { "id": "$zoneId", "name": "Zona $zoneId" },
          "classification": { "family": "TEST", "type": "TEST" },
          "uasLimit": { "metersAgl": 120 },
          "authorization": { "required": false },
          "notams": [{ "code": "W1234/26" }]
        }
      ],
      "blockers": [],
      "warnings": [],
      "baseline": { "maxAltitudeMetersAgl": 120, "representedAsZone": false },
      "meta": { "engine": "DSC", "version": "v3" }
    }
    """.trimIndent()
