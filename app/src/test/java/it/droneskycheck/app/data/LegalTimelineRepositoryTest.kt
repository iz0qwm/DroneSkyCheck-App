package it.droneskycheck.app.data

import java.time.Instant
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalTimelineRepositoryTest {
    @Test
    fun parserReadsTimelineResponseAndAllStates() {
        val response = parseLegalTimelineResponse(JSONObject(fixtureJson()))

        assertEquals(5, response.segments.size)
        assertEquals(LegalTimelineState.AVAILABLE, response.segments[0].state)
        assertEquals(LegalTimelineState.AVAILABLE_WITH_LIMIT, response.segments[1].state)
        assertEquals(LegalTimelineState.AUTH_REQUIRED, response.segments[2].state)
        assertEquals(LegalTimelineState.UNAVAILABLE, response.segments[3].state)
        assertEquals(LegalTimelineState.UNKNOWN, response.segments[4].state)
        assertEquals("UNKNOWN", response.segments[4].rawState)
        assertEquals("UTC", response.window.timezone)
        assertEquals("LI R14/A", response.segments[3].contributors.single().designator)
        assertEquals("UNRESOLVED_CONTRIBUTOR", response.diagnostics.single().code)
    }

    @Test
    fun currentSegmentUsesHalfOpenIntervals() {
        val response = parseLegalTimelineResponse(JSONObject(fixtureJson()))

        assertEquals(
            LegalTimelineState.AVAILABLE_WITH_LIMIT,
            response.currentSegment(Instant.parse("2026-08-11T07:00:00Z"))?.state
        )
        assertEquals(
            LegalTimelineState.AUTH_REQUIRED,
            response.currentSegment(Instant.parse("2026-08-11T08:00:00Z"))?.state
        )
    }

    @Test
    fun formatsUtcSegmentAsLocalDeviceTime() {
        val response = parseLegalTimelineResponse(JSONObject(fixtureJson()))
        val segment = response.segments.first()

        assertEquals(
            "08:00 - 09:00",
            segment.formatLocalRange(ZoneId.of("Europe/Rome"))
        )
    }

    @Test
    fun formatsRangeEndingTomorrowWithoutDuplicateTimes() {
        val response = parseLegalTimelineResponse(JSONObject(realLiR314CloudFunctionJson()))
        val segment = response.segments.single()

        assertEquals(
            "09:16 - domani 09:16",
            segment.formatLocalRange(ZoneId.of("Europe/Rome"))
        )
    }

    @Test
    fun repositoryReportsHttpAndInvalidJsonErrors() = kotlinx.coroutines.runBlocking {
        val httpError = LegalTimelineRepository(
            httpClient = FakeLegalTimelineHttpClient(LegalTimelineHttpResponse(500, "server down"))
        ).getLegalTimeline(41.9, 12.5, Instant.parse("2026-08-11T06:00:00Z"), Instant.parse("2026-08-12T06:00:00Z"))

        val invalidJson = LegalTimelineRepository(
            httpClient = FakeLegalTimelineHttpClient(LegalTimelineHttpResponse(200, "{"))
        ).getLegalTimeline(41.9, 12.5, Instant.parse("2026-08-11T06:00:00Z"), Instant.parse("2026-08-12T06:00:00Z"))

        assertTrue(httpError.exceptionOrNull() is LegalTimelineRepositoryError.HttpError)
        assertEquals(
            "server down",
            (httpError.exceptionOrNull() as LegalTimelineRepositoryError.HttpError).bodySnippet
        )
        assertTrue(invalidJson.exceptionOrNull() is LegalTimelineRepositoryError.InvalidJson)
    }

    @Test
    fun repositoryBuildsCloudFunctionRequestWithLongTimelineTimeout() = kotlinx.coroutines.runBlocking {
        val client = FakeLegalTimelineHttpClient(
            LegalTimelineHttpResponse(200, realBariCloudFunctionJson())
        )
        val result = LegalTimelineRepository(
            endpointUrl = "https://example.test/appLegalTimeline",
            apiKey = "test-key",
            httpClient = client
        ).getLegalTimeline(
            lat = 41.1389,
            lon = 16.7606,
            from = Instant.parse("2026-08-11T06:40:00Z"),
            to = Instant.parse("2026-08-12T06:40:00Z")
        )

        assertTrue(result.isSuccess)
        assertEquals(20_000, client.lastTimeoutMillis)
        assertEquals("application/json", client.lastHeaders["Accept"])
        assertEquals("test-key", client.lastHeaders["x-api-key"])
        assertTrue(client.lastUrl.startsWith("https://example.test/appLegalTimeline?"))
        assertTrue(client.lastUrl.contains("lat=41.1389"))
        assertTrue(client.lastUrl.contains("lon=16.7606"))
        assertTrue(client.lastUrl.contains("from=2026-08-11T06%3A40%3A00Z"))
        assertTrue(client.lastUrl.contains("to=2026-08-12T06%3A40%3A00Z"))
    }

    @Test
    fun repositorySendsTimelineWindowWithoutFractionalSeconds() = kotlinx.coroutines.runBlocking {
        val client = FakeLegalTimelineHttpClient(
            LegalTimelineHttpResponse(200, realBariCloudFunctionJson())
        )

        val result = LegalTimelineRepository(
            endpointUrl = "https://example.test/appLegalTimeline",
            apiKey = "test-key",
            httpClient = client
        ).getLegalTimeline(
            lat = 42.15833870580846,
            lon = 12.779899613895253,
            from = Instant.parse("2026-08-11T07:13:21.139859Z"),
            to = Instant.parse("2026-08-12T07:13:21.139859Z")
        )

        assertTrue(result.isSuccess)
        assertTrue(client.lastUrl.contains("from=2026-08-11T07%3A13%3A21Z"))
        assertTrue(client.lastUrl.contains("to=2026-08-12T07%3A13%3A21Z"))
        assertFalse(client.lastUrl.contains(".139859"))
    }

    @Test
    fun parserReadsRealBariCloudFunctionResponse() {
        val response = parseLegalTimelineResponse(JSONObject(realBariCloudFunctionJson()))

        assertEquals(1, response.segments.size)
        assertEquals(LegalTimelineState.UNAVAILABLE, response.segments.single().state)
        assertEquals(0, response.segments.single().maxAltitudeAgl)
        assertEquals("LIBD BARI/PALESE 07/25", response.segments.single().contributors[1].designator)
        assertEquals("STATIC_LAYER", response.segments.single().contributors.first().sourceType)
        assertEquals("CONTROLLED_AIRSPACE_ATM09", response.segments.single().authorization?.reasonCodes?.single())
        assertEquals("legal-timeline-v1", response.meta.version)
    }

    @Test
    fun parserReadsRealLiR22CloudFunctionResponseWithUnknownTemporalContributor() {
        val response = parseLegalTimelineResponse(JSONObject(realLiR22CloudFunctionJson()))
        val segment = response.segments.single()

        assertEquals(LegalTimelineState.UNKNOWN, segment.state)
        assertEquals(25, segment.maxAltitudeAgl)
        assertNotNull(segment.contributors.firstOrNull { it.designator == "LI R22" })
        assertEquals("ENR schedule is outside the supported LegalTemporalEvaluator subset", segment.warnings.single())
        assertEquals("INSUFFICIENT", segment.confidence)
    }

    @Test
    fun validEmptyTimelineRemainsSuccessForDiagnosticsOnly() = kotlinx.coroutines.runBlocking {
        val result = LegalTimelineRepository(
            httpClient = FakeLegalTimelineHttpClient(
                LegalTimelineHttpResponse(
                    200,
                    """
                    {
                      "generatedAt": "2026-08-11T06:40:00.000Z",
                      "query": { "lat": 41.9, "lon": 12.5 },
                      "window": {
                        "from": "2026-08-11T06:40:00.000Z",
                        "to": "2026-08-12T06:40:00.000Z",
                        "timezone": "UTC"
                      },
                      "segments": [],
                      "diagnostics": [],
                      "meta": { "schemaVersion": 1, "engine": "DSC", "version": "legal-timeline-v1" }
                    }
                    """.trimIndent()
                )
            )
        ).getLegalTimeline(41.9, 12.5, Instant.parse("2026-08-11T06:40:00Z"), Instant.parse("2026-08-12T06:40:00Z"))

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().segments.isNotEmpty())
    }

    private fun fixtureJson(): String =
        """
        {
          "generatedAt": "2026-08-11T04:00:00.000Z",
          "query": { "lat": 41.9, "lon": 12.5 },
          "window": {
            "from": "2026-08-11T06:00:00.000Z",
            "to": "2026-08-11T11:00:00.000Z",
            "timezone": "UTC"
          },
          "segments": [
            {
              "from": "2026-08-11T06:00:00.000Z",
              "to": "2026-08-11T07:00:00.000Z",
              "state": "AVAILABLE",
              "maxAltitudeAgl": 120,
              "authorization": { "required": false },
              "contributors": [],
              "warnings": [],
              "confidence": "HIGH",
              "reasonCodes": []
            },
            {
              "from": "2026-08-11T07:00:00.000Z",
              "to": "2026-08-11T08:00:00.000Z",
              "state": "AVAILABLE_WITH_LIMIT",
              "maxAltitudeAgl": 60,
              "authorization": { "required": false },
              "contributors": [],
              "warnings": [],
              "confidence": "HIGH",
              "reasonCodes": []
            },
            {
              "from": "2026-08-11T08:00:00.000Z",
              "to": "2026-08-11T09:00:00.000Z",
              "state": "AUTH_REQUIRED",
              "maxAltitudeAgl": 120,
              "authorization": {
                "required": true,
                "resolutionStatus": "RESOLVED",
                "procedures": [{ "type": "ATM09", "label": "ATM09" }],
                "additionalRequirements": [],
                "reasonCodes": ["CONTROLLED_AIRSPACE_ATM09"],
                "blockingReasons": []
              },
              "contributors": [],
              "warnings": [],
              "confidence": "HIGH",
              "reasonCodes": []
            },
            {
              "from": "2026-08-11T09:00:00.000Z",
              "to": "2026-08-11T10:00:00.000Z",
              "state": "UNAVAILABLE",
              "maxAltitudeAgl": 0,
              "authorization": { "required": true },
              "contributors": [
                {
                  "id": "ENR:LI R14/A:SCHEDULED",
                  "sourceType": "ENR",
                  "designator": "LI R14/A",
                  "role": ["ACTIVE"],
                  "temporalPolicy": "SCHEDULED",
                  "operationalRelevance": "OPERATIONAL",
                  "maxAltitudeAgl": 0,
                  "reasonCodes": ["ACTIVE_ENR"],
                  "warnings": []
                }
              ],
              "warnings": [],
              "confidence": "HIGH",
              "reasonCodes": ["ACTIVE_ENR"]
            },
            {
              "from": "2026-08-11T10:00:00.000Z",
              "to": "2026-08-11T11:00:00.000Z",
              "state": "UNKNOWN",
              "maxAltitudeAgl": 120,
              "authorization": { "required": false },
              "contributors": [],
              "warnings": [],
              "confidence": "INSUFFICIENT",
              "reasonCodes": ["UNRESOLVED_OPERATIONAL_CONTRIBUTOR"]
            }
          ],
          "diagnostics": [
            {
              "code": "UNRESOLVED_CONTRIBUTOR",
              "severity": "WARNING",
              "sourceType": "ENR",
              "designator": "LI R999",
              "message": "Contributor has unresolved temporal policy"
            }
          ],
          "meta": {
            "schemaVersion": 1,
            "engine": "DSC",
            "version": "legal-timeline-v1",
            "maxWindowHours": 168
          }
        }
        """.trimIndent()

    private fun realBariCloudFunctionJson(): String =
        """
        {
          "generatedAt": "2026-08-11T06:50:54.664Z",
          "query": { "lat": 41.1389, "lon": 16.7606 },
          "window": {
            "from": "2026-08-11T06:40:00.000Z",
            "to": "2026-08-12T06:40:00.000Z",
            "timezone": "UTC"
          },
          "segments": [
            {
              "from": "2026-08-11T06:40:00.000Z",
              "to": "2026-08-12T06:40:00.000Z",
              "state": "UNAVAILABLE",
              "maxAltitudeAgl": 0,
              "authorization": {
                "required": true,
                "resolutionStatus": "RESOLVED",
                "procedures": [{ "type": "ATM09", "version": 1, "label": "ATM09", "reasonCode": "CONTROLLED_AIRSPACE_ATM09" }],
                "additionalRequirements": [],
                "reasonCodes": ["CONTROLLED_AIRSPACE_ATM09"],
                "blockingReasons": []
              },
              "contributors": [
                {
                  "id": "STATIC_LAYER:ATM09CTR:A537947",
                  "sourceType": "STATIC_LAYER",
                  "designator": "LIBD BARI CTR",
                  "role": ["ACTIVE", "APPLIED_EFFECT"],
                  "temporalPolicy": "STATIC",
                  "operationalRelevance": "OPERATIONAL",
                  "maxAltitudeAgl": 60,
                  "reasonCodes": ["STATIC_OPERATIONAL", "TEMPORAL_POLICY_STATIC"],
                  "warnings": []
                },
                {
                  "id": "STATIC_LAYER:ATM09OTHER:A538225",
                  "sourceType": "STATIC_LAYER",
                  "designator": "LIBD BARI/PALESE 07/25",
                  "role": ["ACTIVE", "APPLIED_EFFECT"],
                  "temporalPolicy": "STATIC",
                  "operationalRelevance": "OPERATIONAL",
                  "maxAltitudeAgl": 0,
                  "reasonCodes": ["STATIC_OPERATIONAL", "TEMPORAL_POLICY_STATIC"],
                  "warnings": []
                },
                {
                  "id": "STATIC_LAYER:OTHERATZ:A538005",
                  "sourceType": "STATIC_LAYER",
                  "designator": "LIBD BARI PALESE ATZ",
                  "role": ["ACTIVE", "APPLIED_EFFECT"],
                  "temporalPolicy": "STATIC",
                  "operationalRelevance": "OPERATIONAL",
                  "maxAltitudeAgl": 60,
                  "reasonCodes": ["STATIC_OPERATIONAL", "TEMPORAL_POLICY_STATIC"],
                  "warnings": []
                }
              ],
              "warnings": [],
              "confidence": "HIGH",
              "reasonCodes": ["STATIC_OPERATIONAL", "TEMPORAL_POLICY_STATIC"]
            }
          ],
          "diagnostics": [],
          "meta": {
            "schemaVersion": 1,
            "engine": "DSC",
            "version": "legal-timeline-v1",
            "maxWindowHours": 168,
            "source": { "zoneDetails": 3, "contributors": 3, "excludedContributors": 0 },
            "timeline": {
              "inputContributorCount": 3,
              "scheduledContributorCount": 0,
              "unresolvedContributorCount": 0,
              "excludedContributorCount": 0,
              "rawSegmentCount": 1,
              "segmentCount": 1,
              "coverageContinuous": true,
              "guardWarnings": []
            }
          }
        }
        """.trimIndent()

    private fun realLiR22CloudFunctionJson(): String =
        """
        {
          "generatedAt": "2026-08-11T06:52:28.326Z",
          "query": { "lat": 41.7417292054051, "lon": 13.3121257492348 },
          "window": {
            "from": "2026-08-11T06:40:00.000Z",
            "to": "2026-08-12T06:40:00.000Z",
            "timezone": "UTC"
          },
          "segments": [
            {
              "from": "2026-08-11T06:40:00.000Z",
              "to": "2026-08-12T06:40:00.000Z",
              "state": "UNKNOWN",
              "maxAltitudeAgl": 25,
              "authorization": {
                "required": true,
                "resolutionStatus": "RESOLVED",
                "procedures": [{ "type": "ATM09", "version": 1, "label": "ATM09", "reasonCode": "CONTROLLED_AIRSPACE_ATM09" }],
                "additionalRequirements": [],
                "reasonCodes": ["CONTROLLED_AIRSPACE_ATM09"],
                "blockingReasons": []
              },
              "contributors": [
                {
                  "id": "STATIC_LAYER:ATM09AVIOSUP:A581032",
                  "sourceType": "STATIC_LAYER",
                  "designator": "avio 1235 Ospedale San Benedetto di Alatri",
                  "role": ["ACTIVE", "APPLIED_EFFECT"],
                  "temporalPolicy": "STATIC",
                  "operationalRelevance": "OPERATIONAL",
                  "maxAltitudeAgl": 25,
                  "reasonCodes": ["STATIC_OPERATIONAL", "TEMPORAL_POLICY_STATIC"],
                  "warnings": []
                },
                {
                  "id": "STATIC_LAYER:ATM09CTR:A537959",
                  "sourceType": "STATIC_LAYER",
                  "designator": "LIRH FROSINONE CTR",
                  "role": ["ACTIVE", "APPLIED_EFFECT"],
                  "temporalPolicy": "STATIC",
                  "operationalRelevance": "OPERATIONAL",
                  "maxAltitudeAgl": 60,
                  "reasonCodes": ["STATIC_OPERATIONAL", "TEMPORAL_POLICY_STATIC"],
                  "warnings": []
                },
                {
                  "id": "ENR:LI R22:SCHEDULED",
                  "sourceType": "ENR",
                  "designator": "LI R22",
                  "role": ["UNKNOWN"],
                  "temporalPolicy": "SCHEDULED",
                  "operationalRelevance": "UNKNOWN",
                  "maxAltitudeAgl": null,
                  "reasonCodes": ["TEMPORAL_POLICY_SCHEDULED"],
                  "warnings": []
                }
              ],
              "warnings": ["ENR schedule is outside the supported LegalTemporalEvaluator subset"],
              "confidence": "INSUFFICIENT",
              "reasonCodes": [
                "STATIC_BASE_HELD_BY_TEMPORAL_OVERLAY",
                "STATIC_OPERATIONAL",
                "TEMPORAL_POLICY_STATIC",
                "ENR_TEMPORAL_UNKNOWN"
              ]
            }
          ],
          "diagnostics": [],
          "meta": {
            "schemaVersion": 1,
            "engine": "DSC",
            "version": "legal-timeline-v1",
            "maxWindowHours": 168,
            "source": { "zoneDetails": 3, "contributors": 4, "excludedContributors": 0 },
            "timeline": {
              "inputContributorCount": 4,
              "scheduledContributorCount": 1,
              "unresolvedContributorCount": 0,
              "excludedContributorCount": 0,
              "rawSegmentCount": 1,
              "segmentCount": 1,
              "coverageContinuous": true,
              "guardWarnings": []
            }
          }
        }
        """.trimIndent()

    private fun realLiR314CloudFunctionJson(): String =
        """
        {
          "generatedAt": "2026-08-11T07:14:47.156Z",
          "query": { "lat": 42.15833870580846, "lon": 12.779899613895253 },
          "window": {
            "from": "2026-08-11T07:13:21.000Z",
            "to": "2026-08-12T07:13:21.000Z",
            "timezone": "UTC"
          },
          "segments": [
            {
              "from": "2026-08-11T07:16:21.000Z",
              "to": "2026-08-12T07:16:21.000Z",
              "state": "UNKNOWN",
              "maxAltitudeAgl": 120,
              "authorization": {
                "required": false,
                "resolutionStatus": "RESOLVED",
                "procedures": [],
                "additionalRequirements": [],
                "reasonCodes": [],
                "blockingReasons": []
              },
              "contributors": [
                {
                  "id": "ENR:LI R314:SCHEDULED",
                  "sourceType": "ENR",
                  "designator": "LI R314",
                  "role": ["UNKNOWN"],
                  "temporalPolicy": "SCHEDULED",
                  "operationalRelevance": "UNKNOWN",
                  "maxAltitudeAgl": null,
                  "reasonCodes": ["TEMPORAL_POLICY_SCHEDULED"],
                  "warnings": []
                }
              ],
              "warnings": ["ENR schedule is outside the supported LegalTemporalEvaluator subset"],
              "confidence": "INSUFFICIENT",
              "reasonCodes": ["STATIC_BASE_HELD_BY_TEMPORAL_OVERLAY", "ENR_TEMPORAL_UNKNOWN"]
            }
          ],
          "diagnostics": [],
          "meta": {
            "schemaVersion": 1,
            "engine": "DSC",
            "version": "legal-timeline-v1",
            "maxWindowHours": 168
          }
        }
        """.trimIndent()
}

private class FakeLegalTimelineHttpClient(
    private val response: LegalTimelineHttpResponse
) : LegalTimelineHttpClient {
    var lastUrl: String = ""
        private set
    var lastHeaders: Map<String, String> = emptyMap()
        private set
    var lastTimeoutMillis: Int = -1
        private set

    override fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int
    ): LegalTimelineHttpResponse {
        lastUrl = url
        lastHeaders = headers
        lastTimeoutMillis = timeoutMillis
        return response
    }
}
