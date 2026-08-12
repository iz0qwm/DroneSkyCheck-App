package it.droneskycheck.app.data.traffic

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficAwarenessRepositoryTest {
    @Test
    fun parsesAdsbTarget() {
        val response = parseTrafficAwarenessResponse(JSONObject(fixtureJson()))
        val adsb = response.traffic.targets.first { it.id == "icao:3009bc" }

        assertEquals("3009bc", adsb.identifiers.icao24)
        assertEquals("ARES44", adsb.identifiers.callsign)
        assertEquals(41.9278, adsb.position.lat, 0.0)
        assertEquals(12.426, adsb.position.lon, 0.0)
        assertEquals(198.12, adsb.altitude.baroM ?: -1.0, 0.0)
        assertEquals(312.42, adsb.altitude.geoM ?: -1.0, 0.0)
        assertEquals(35.59, adsb.motion.groundSpeedMps ?: -1.0, 0.0)
        assertEquals(4.14, adsb.motion.trackDeg ?: -1.0, 0.0)
        assertEquals(6859.22, adsb.relative.distanceM ?: -1.0, 0.0)
        assertEquals("opensky", adsb.provider)
        assertEquals("OpenSky", adsb.source)
    }

    @Test
    fun parsesOgnTargetWithNullIcaoAndSourceAltitude() {
        val response = parseTrafficAwarenessResponse(JSONObject(fixtureJson()))
        val ogn = response.traffic.targets.first { it.id == "ogn:FLRDDDDA9" }

        assertNull(ogn.identifiers.icao24)
        assertEquals("FLRDDDDA9", ogn.identifiers.sourceId)
        assertEquals(68.0, ogn.altitude.sourceM ?: -1.0, 0.0)
        assertEquals("ogn.alt_m", ogn.altitude.sourceReference)
        assertEquals(314.0, ogn.motion.headingDeg ?: -1.0, 0.0)
        assertEquals("OGN", ogn.provider)
        assertEquals("FREEFLIGHT", ogn.source)
    }

    @Test
    fun parsesMixedTrafficResponse() {
        val response = parseTrafficAwarenessResponse(JSONObject(fixtureJson()))

        assertEquals(2, response.traffic.count)
        assertEquals(2, response.traffic.targets.size)
        assertEquals("ok", response.providers["opensky"]?.status)
        assertEquals("ok", response.providers["OGN"]?.status)
    }

    @Test
    fun parsesZeroTraffic() {
        val response = parseTrafficAwarenessResponse(JSONObject(fixtureJson(targetsJson = "[]", count = 0)))

        assertEquals(0, response.traffic.count)
        assertTrue(response.traffic.targets.isEmpty())
    }

    @Test
    fun providerTimeoutIsValidResponse() {
        val response = parseTrafficAwarenessResponse(
            JSONObject(
                fixtureJson(
                    providerJson = """
                        {
                          "opensky": { "status": "timeout", "count": 0, "errorCode": "TIMEOUT" },
                          "OGN": { "status": "ok", "count": 1 }
                        }
                    """.trimIndent()
                )
            )
        )

        assertEquals("timeout", response.providers["opensky"]?.status)
        assertEquals("TIMEOUT", response.providers["opensky"]?.errorCode)
        assertEquals(2, response.traffic.targets.size)
    }

    @Test
    fun optionalMissingFieldsDoNotBreakParsing() {
        val response = parseTrafficAwarenessResponse(
            JSONObject(
                fixtureJson(
                    targetsJson = """
                        [
                          {
                            "id": "ogn:minimal",
                            "position": { "lat": 41.9, "lon": 12.5 },
                            "provider": "OGN",
                            "source": "FANET"
                          }
                        ]
                    """.trimIndent(),
                    count = 1,
                    includeCache = false
                )
            )
        )

        val target = response.traffic.targets.first()
        assertEquals("ogn:minimal", target.id)
        assertNull(target.identifiers.callsign)
        assertNull(target.altitude.aglM)
        assertNull(response.cache)
    }

    @Test
    fun invalidTargetIsDiscardedWithoutFailingResponse() {
        val response = parseTrafficAwarenessResponse(
            JSONObject(
                fixtureJson(
                    targetsJson = """
                        [
                          { "id": "bad-no-position" },
                          { "id": "good", "position": { "lat": 41.9, "lon": 12.5 } }
                        ]
                    """.trimIndent(),
                    count = 2
                )
            )
        )

        assertEquals(2, response.traffic.count)
        assertEquals(1, response.traffic.targets.size)
        assertEquals("good", response.traffic.targets.first().id)
    }

    @Test
    fun repositoryMapsHttpErrors() = runBlocking {
        val error = repositoryWith(FakeTrafficClient(TrafficAwarenessHttpResponse(401, """{"error":"auth"}""")))
            .getTrafficAwareness(41.9, 12.5)
            .exceptionOrNull()

        assertTrue(error is TrafficAwarenessRepositoryError.HttpError)
        assertEquals(401, (error as TrafficAwarenessRepositoryError.HttpError).statusCode)
    }

    @Test
    fun repositoryMapsMalformedJson() = runBlocking {
        val error = repositoryWith(FakeTrafficClient(TrafficAwarenessHttpResponse(200, "not json")))
            .getTrafficAwareness(41.9, 12.5)
            .exceptionOrNull()

        assertTrue(error is TrafficAwarenessRepositoryError.InvalidJson)
    }

    @Test
    fun repositoryUsesConfiguredEndpointApiKeyAndRadius() = runBlocking {
        val client = FakeTrafficClient(TrafficAwarenessHttpResponse(200, fixtureJson()))
        val result = TrafficAwarenessRepository(
            endpointUrl = "https://example.test/appTrafficAwareness",
            apiKey = "dsc_app_key",
            httpClient = client
        ).getTrafficAwareness(41.9, 12.5, 20.0)

        assertTrue(result.isSuccess)
        assertEquals("application/json", client.lastHeaders["Accept"])
        assertEquals("dsc_app_key", client.lastHeaders["x-api-key"])
        assertTrue(client.lastUrl.contains("appTrafficAwareness"))
        assertTrue(client.lastUrl.contains("lat=41.9"))
        assertTrue(client.lastUrl.contains("lon=12.5"))
        assertTrue(client.lastUrl.contains("radius=20.0"))
    }

    private fun repositoryWith(client: FakeTrafficClient): TrafficAwarenessRepository =
        TrafficAwarenessRepository(
            endpointUrl = "https://example.test/appTrafficAwareness",
            apiKey = "dsc_test",
            httpClient = client
        )

    private fun fixtureJson(
        targetsJson: String? = null,
        providerJson: String? = null,
        count: Int = 2,
        includeCache: Boolean = true
    ): String {
        val targets = targetsJson ?: """
            [
              {
                "id": "icao:3009bc",
                "identifiers": {
                  "icao24": "3009bc",
                  "callsign": "ARES44",
                  "registration": null,
                  "sourceId": "3009bc"
                },
                "position": { "lat": 41.9278, "lon": 12.426 },
                "altitude": {
                  "baroM": 198.12,
                  "geoM": 312.42,
                  "mslM": null,
                  "aglM": null,
                  "sourceM": null,
                  "sourceReference": null
                },
                "motion": {
                  "groundSpeedMps": 35.59,
                  "verticalRateMps": null,
                  "trackDeg": 4.14,
                  "headingDeg": 4.14
                },
                "aircraft": { "category": 0, "type": null },
                "time": { "timestamp": 1786550398000, "ageSec": 2.795 },
                "relative": { "distanceM": 6859.22, "bearingDeg": 296.81 },
                "provider": "opensky",
                "source": "OpenSky",
                "quality": null,
                "sources": [{ "provider": "opensky", "source": "OpenSky" }],
                "provenance": {
                  "sources": [{ "provider": "opensky", "source": "OpenSky" }],
                  "contributions": [
                    {
                      "id": "icao:3009bc",
                      "provider": "opensky",
                      "source": "OpenSky",
                      "sourceId": "3009bc",
                      "timestamp": 1786550398000
                    }
                  ]
                }
              },
              {
                "id": "ogn:FLRDDDDA9",
                "identifiers": {
                  "icao24": null,
                  "callsign": "FLRDDDDA9",
                  "registration": null,
                  "sourceId": "FLRDDDDA9"
                },
                "position": { "lat": 41.89266, "lon": 12.6505 },
                "altitude": {
                  "baroM": null,
                  "geoM": null,
                  "mslM": null,
                  "aglM": null,
                  "sourceM": 68,
                  "sourceReference": "ogn.alt_m"
                },
                "motion": {
                  "groundSpeedMps": 0,
                  "verticalRateMps": null,
                  "trackDeg": null,
                  "headingDeg": 314
                },
                "aircraft": { "category": null, "type": null },
                "time": { "timestamp": 1786550396111, "ageSec": null },
                "relative": { "distanceM": 12483.30, "bearingDeg": 93.69 },
                "provider": "OGN",
                "source": "FREEFLIGHT",
                "sources": [{ "provider": "OGN", "source": "FREEFLIGHT" }]
              }
            ]
        """.trimIndent()
        val providers = providerJson ?: """
            {
              "opensky": { "status": "ok", "count": 3 },
              "OGN": { "status": "ok", "count": 219 }
            }
        """.trimIndent()
        val cache = if (includeCache) {
            ""","cache": { "hit": false, "ageMs": 0, "ttlMs": 5000, "singleFlight": false }"""
        } else {
            ""
        }
        return """
            {
              "ok": true,
              "generatedAt": 1786550400795,
              "servedAt": 1786550400900,
              "center": { "lat": 41.9, "lon": 12.5 },
              "radiusKm": 20,
              "traffic": {
                "count": $count,
                "targets": $targets
              },
              "providers": $providers
              $cache
            }
        """.trimIndent()
    }

    private class FakeTrafficClient(
        private val response: TrafficAwarenessHttpResponse? = null,
        private val error: Throwable? = null
    ) : TrafficAwarenessHttpClient {
        var lastUrl: String = ""
        var lastHeaders: Map<String, String> = emptyMap()

        override fun get(
            url: String,
            headers: Map<String, String>,
            timeoutMillis: Int
        ): TrafficAwarenessHttpResponse {
            lastUrl = url
            lastHeaders = headers
            error?.let { throw it }
            return response ?: TrafficAwarenessHttpResponse(200, "{}")
        }
    }
}
