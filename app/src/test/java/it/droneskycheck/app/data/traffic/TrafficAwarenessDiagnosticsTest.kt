package it.droneskycheck.app.data.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficAwarenessDiagnosticsTest {
    @Test
    fun classificationReasonExplainsDscDroneAndOgnAircraftWithoutChangingClassification() {
        val dsc = target(
            id = "dsc_uas:airsense:rid-1",
            provider = "dsc_uas",
            source = "airsense",
            sourceId = "rid-1",
            objectType = "drone"
        )
        val ogn = target(
            id = "ogn:FLR123456",
            provider = "OGN",
            source = "ADSB",
            sourceId = "FLR123456"
        )

        assertEquals(TrafficTargetKind.DRONE, dsc.trafficTargetKind())
        assertEquals("source_dsc_drone", dsc.trafficClassificationReason())
        assertEquals(TrafficTargetKind.AIRCRAFT, ogn.trafficTargetKind())
        assertEquals("ogn_default_aircraft", ogn.trafficClassificationReason())
    }

    @Test
    fun nearbyDscAndOgnTargetsAreOnlyReportedAsDiagnosticCandidates() {
        val dsc = target(
            id = "dsc_uas:airsense:rid-1",
            provider = "dsc_uas",
            source = "airsense",
            sourceId = "RID-1",
            lat = 41.900000,
            lon = 12.500000,
            timestamp = 1_000_000L,
            objectType = "drone"
        )
        val ogn = target(
            id = "ogn:RID-1",
            provider = "OGN",
            source = "ADSB",
            sourceId = "rid-1",
            lat = 41.900090,
            lon = 12.500000,
            timestamp = 1_002_000L
        )

        val candidate = diagnosticDscOgnCandidates(listOf(dsc, ogn)).single()

        assertEquals(dsc.id, candidate.dscTarget.id)
        assertEquals(ogn.id, candidate.ognTarget.id)
        assertTrue(candidate.distanceMeters in 9.0..11.0)
        assertEquals(2_000L, candidate.timeDeltaMillis)
        assertEquals("sourceId:sourceId", candidate.identityMatch)
        assertEquals(2, listOf(dsc, ogn).size)
    }

    @Test
    fun distantOrTimeSeparatedTargetsAreNotPossibleDuplicateCandidates() {
        val dsc = target(
            id = "dsc_uas:airsense:rid-1",
            provider = "dsc_uas",
            source = "airsense",
            lat = 41.900000,
            lon = 12.500000,
            timestamp = 1_000_000L,
            objectType = "drone"
        )
        val distantOgn = target(
            id = "ogn:distant",
            provider = "OGN",
            lat = 41.910000,
            lon = 12.500000,
            timestamp = 1_002_000L
        )
        val oldOgn = target(
            id = "ogn:old",
            provider = "OGN",
            lat = 41.900090,
            lon = 12.500000,
            timestamp = 1_061_000L
        )

        assertTrue(diagnosticDscOgnCandidates(listOf(dsc, distantOgn, oldOgn)).isEmpty())
    }

    @Test
    fun diagnosticRateLimiterLogsChangesImmediatelyAndMovementPeriodically() {
        val limiter = TrafficDiagnosticRateLimiter(intervalMillis = 15_000L)

        assertTrue(limiter.shouldLog("RAW:rid-1", "DRONE", 1_000L))
        assertFalse(limiter.shouldLog("RAW:rid-1", "DRONE", 6_000L))
        assertTrue(limiter.shouldLog("RAW:rid-1", "AIRCRAFT", 7_000L))
        assertFalse(limiter.shouldLog("RAW:rid-1", "AIRCRAFT", 21_999L))
        assertTrue(limiter.shouldLog("RAW:rid-1", "AIRCRAFT", 22_000L))
    }

    private fun target(
        id: String,
        provider: String,
        source: String? = null,
        sourceId: String? = null,
        callsign: String? = null,
        lat: Double = 41.9,
        lon: Double = 12.5,
        timestamp: Long? = null,
        objectType: String? = null
    ): TrafficTarget =
        TrafficTarget(
            id = id,
            identifiers = TrafficIdentifiers(
                icao24 = null,
                callsign = callsign,
                registration = null,
                sourceId = sourceId
            ),
            position = TrafficPosition(lat = lat, lon = lon),
            altitude = TrafficAltitude(
                baroM = null,
                geoM = null,
                mslM = null,
                aglM = null,
                sourceM = null,
                sourceReference = null
            ),
            motion = TrafficMotion(
                groundSpeedMps = null,
                verticalRateMps = null,
                trackDeg = null,
                headingDeg = null
            ),
            aircraft = TrafficAircraft(category = null, type = null),
            time = TrafficTime(timestamp = timestamp, ageSec = null),
            relative = TrafficRelative(distanceM = null, bearingDeg = null),
            provider = provider,
            source = source,
            quality = null,
            sources = emptyList(),
            provenance = null,
            objectType = objectType
        )
}
