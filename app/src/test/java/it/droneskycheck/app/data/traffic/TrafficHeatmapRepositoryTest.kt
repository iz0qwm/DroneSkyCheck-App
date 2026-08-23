package it.droneskycheck.app.data.traffic

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficHeatmapRepositoryTest {
    @Test
    fun parsesHeatmapCellsAndCalculatesCumulativeAglFilters() {
        val response = parseTrafficHeatmapResponse(JSONObject(fixtureJson()))
        val cell = response.cells.first()

        assertEquals(570, cell.observations)
        assertEquals(5, cell.estimatedAglBands["unknown"])
        assertEquals(2, cell.estimatedAglBands["below_terrain_or_inconsistent"])
        assertEquals(5, cell.filteredObservationsFor(TrafficHeatmapMaxAgl.Below120))
        assertEquals(10, cell.filteredObservationsFor(TrafficHeatmapMaxAgl.Below300))
        assertEquals(20, cell.filteredObservationsFor(TrafficHeatmapMaxAgl.Below500))
        assertEquals(70, cell.filteredObservationsFor(TrafficHeatmapMaxAgl.Below1000))
        assertEquals(570, cell.filteredObservationsFor(TrafficHeatmapMaxAgl.All))
    }

    @Test
    fun excludesUnknownAndInconsistentFromFilteredDensity() {
        val cell = parseTrafficHeatmapResponse(JSONObject(fixtureJson())).cells.first()

        assertEquals(5, cell.filteredObservationsFor(TrafficHeatmapMaxAgl.Below120))
        assertEquals(7, (cell.estimatedAglBands["unknown"] ?: 0) + (cell.estimatedAglBands["below_terrain_or_inconsistent"] ?: 0))
    }

    @Test
    fun convertsOnlyPositiveFilteredCellsToGeoJsonFeatures() {
        val response = parseTrafficHeatmapResponse(JSONObject(fixtureJson(includeZeroCell = true)))
        val features = trafficHeatmapCellsToFeatureCollection(
            cells = response.cells,
            maxAgl = TrafficHeatmapMaxAgl.Below500
        ).features().orEmpty()

        assertEquals(1, features.size)
        assertEquals(20, features.first().properties()?.get("filteredObservations")?.asInt)
        assertTrue((features.first().properties()?.get("weight")?.asDouble ?: 0.0) > 0.0)
    }

    @Test
    fun parsesEmptyResponse() {
        val response = parseTrafficHeatmapResponse(JSONObject(fixtureJson(cellsJson = "[]", count = 0)))

        assertEquals(0, response.count)
        assertTrue(response.cells.isEmpty())
    }

    @Test
    fun repositoryUsesEndpointApiKeyDaysRadiusAndMaxAgl() = runBlocking {
        val client = FakeHeatmapHttpClient(TrafficAwarenessHttpResponse(200, fixtureJson()))
        val result = TrafficHeatmapRepository(
            endpointUrl = "https://example.test/appTrafficHeatmap",
            apiKey = "dsc_app_key",
            httpClient = client
        ).getTrafficHeatmap(
            lat = 41.9,
            lon = 12.5,
            radiusKm = 80.0,
            days = 30,
            maxAgl = TrafficHeatmapMaxAgl.Below300
        )

        assertTrue(result.isSuccess)
        assertEquals("application/json", client.lastHeaders["Accept"])
        assertEquals("dsc_app_key", client.lastHeaders["x-api-key"])
        assertTrue(client.lastUrl.contains("appTrafficHeatmap"))
        assertTrue(client.lastUrl.contains("lat=41.9"))
        assertTrue(client.lastUrl.contains("lon=12.5"))
        assertTrue(client.lastUrl.contains("radiusKm=80.0"))
        assertTrue(client.lastUrl.contains("days=30"))
        assertTrue(client.lastUrl.contains("maxAgl=300"))
    }

    @Test
    fun repositoryMapsHttpErrors() = runBlocking {
        val error = TrafficHeatmapRepository(
            endpointUrl = "https://example.test/appTrafficHeatmap",
            apiKey = "dsc_app_key",
            httpClient = FakeHeatmapHttpClient(TrafficAwarenessHttpResponse(504, """{"error":"upstream_timeout"}"""))
        ).getTrafficHeatmap(41.9, 12.5, 20.0).exceptionOrNull()

        assertTrue(error is TrafficHeatmapRepositoryError.HttpError)
        assertEquals("HTTP_SERVER", error?.toTrafficHeatmapDiagnosticReason())
    }

    private fun fixtureJson(
        cellsJson: String? = null,
        count: Int = 1,
        includeZeroCell: Boolean = false
    ): String {
        val cells = cellsJson ?: """
            [
              {
                "lat": 41.9,
                "lon": 12.5,
                "observations": 570,
                "uniqueTargetBucketSum": 123,
                "sources": { "FLARM": 20, "ADSB": 550 },
                "altitudeBands": { "low": 20, "high": 550 },
                "estimatedAglBands": {
                  "lt_50m": 3,
                  "50_120m": 2,
                  "120_300m": 5,
                  "300_500m": 10,
                  "500_1000m": 50,
                  "gt_1000m": 500,
                  "unknown": 5,
                  "below_terrain_or_inconsistent": 2
                },
                "filteredObservations": 20,
                "maxAgl": "500"
              }
              ${if (includeZeroCell) """,
              {
                "lat": 41.91,
                "lon": 12.51,
                "observations": 7,
                "estimatedAglBands": { "unknown": 7 }
              }
              """ else ""}
            ]
        """.trimIndent()

        return """
            {
              "ok": true,
              "generatedAt": 1786550400,
              "servedAt": 1786550500,
              "periodDays": 30,
              "query": { "lat": 41.9, "lon": 12.5, "radiusKm": 80, "days": 30, "maxAgl": "500" },
              "count": $count,
              "cells": $cells,
              "cache": { "hit": false, "ageMs": 0, "ttlMs": 600000, "singleFlight": false }
            }
        """.trimIndent()
    }

    private class FakeHeatmapHttpClient(
        private val response: TrafficAwarenessHttpResponse
    ) : TrafficAwarenessHttpClient {
        var lastUrl: String = ""
            private set
        var lastHeaders: Map<String, String> = emptyMap()
            private set

        override fun get(
            url: String,
            headers: Map<String, String>,
            timeoutMillis: Int
        ): TrafficAwarenessHttpResponse {
            lastUrl = url
            lastHeaders = headers
            return response
        }
    }
}
