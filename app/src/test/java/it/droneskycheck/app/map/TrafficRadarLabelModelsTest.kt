package it.droneskycheck.app.map

import it.droneskycheck.app.data.traffic.TrafficAircraft
import it.droneskycheck.app.data.traffic.TrafficAltitude
import it.droneskycheck.app.data.traffic.TrafficAssessment
import it.droneskycheck.app.data.traffic.TrafficCalculationConfidence
import it.droneskycheck.app.data.traffic.TrafficIdentifiers
import it.droneskycheck.app.data.traffic.TrafficMotion
import it.droneskycheck.app.data.traffic.TrafficPosition
import it.droneskycheck.app.data.traffic.TrafficProvenance
import it.droneskycheck.app.data.traffic.TrafficRelative
import it.droneskycheck.app.data.traffic.TrafficRelevance
import it.droneskycheck.app.data.traffic.TrafficSource
import it.droneskycheck.app.data.traffic.TrafficTarget
import it.droneskycheck.app.data.traffic.TrafficTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficRadarLabelModelsTest {
    @Test
    fun buildsTwoLineRadarLabelTarget() {
        val label = listOf(
            trafficTarget(aglM = 610.0, speedMps = 87.455)
        ).toTrafficRadarLabelTargets(emptyMap()).single()

        assertEquals("AEREO · 610 m AGL", label.firstLine)
        assertEquals(listOf("AEREO · 610 m AGL", "170 kt"), label.displayLines)
    }

    @Test
    fun omitsMissingSpeedAndKeepsAltitudeLine() {
        val label = listOf(
            trafficTarget(source = "FLARM", provider = "OGN", aglM = 430.0, speedMps = null)
        ).toTrafficRadarLabelTargets(emptyMap()).single()

        assertEquals("GLIDER · 430 m AGL", label.firstLine)
        assertEquals(listOf("GLIDER · 430 m AGL"), label.displayLines)
    }

    @Test
    fun omitsMissingAltitudeWithoutInventingInvalidText() {
        val label = listOf(
            trafficTarget(id = "traffic:unknown", provider = null, source = null, aglM = null, mslM = null, geoM = null, sourceM = null)
        ).toTrafficRadarLabelTargets(emptyMap()).single()

        assertEquals("TRAFFICO", label.firstLine)
        assertFalse(label.displayLines.any { it.contains("null", ignoreCase = true) })
    }

    @Test
    fun suppressesUnknownZeroAltitudeFallback() {
        val label = listOf(
            trafficTarget(id = "traffic:unknown", provider = null, source = null, sourceM = 0.0, sourceReference = null)
        ).toTrafficRadarLabelTargets(emptyMap()).single()

        assertEquals("TRAFFICO", label.firstLine)
        assertFalse(label.displayLines.any { it == "0 m ALT" || it.contains("0 m ALT") })
    }

    @Test
    fun sortsAttentionBeforeMonitorBeforeInformation() {
        val labels = sortTrafficRadarLabelTargets(
            listOf(
                label("info", TrafficRelevance.INFORMATION),
                label("attention", TrafficRelevance.ATTENTION),
                label("monitor", TrafficRelevance.MONITOR)
            )
        )

        assertEquals(listOf("attention", "monitor", "info"), labels.map { it.id })
    }

    @Test
    fun skipsInformationCollisionButKeepsMonitorAndAttention() {
        val accepted = listOf(
            TrafficRadarLabelPlacement(
                id = "accepted",
                relevance = TrafficRelevance.ATTENTION,
                bounds = TrafficRadarLabelBounds(0.0f, 0.0f, 80.0f, 40.0f)
            )
        )
        val collidingInformation = TrafficRadarLabelPlacement(
            id = "info",
            relevance = TrafficRelevance.INFORMATION,
            bounds = TrafficRadarLabelBounds(20.0f, 10.0f, 90.0f, 50.0f)
        )
        val collidingMonitor = collidingInformation.copy(
            id = "monitor",
            relevance = TrafficRelevance.MONITOR
        )

        assertFalse(shouldDrawTrafficRadarLabel(accepted, collidingInformation))
        assertTrue(shouldDrawTrafficRadarLabel(accepted, collidingMonitor))
    }

    private fun label(id: String, relevance: TrafficRelevance): TrafficRadarLabelTarget =
        TrafficRadarLabelTarget(
            id = id,
            latitude = 41.9,
            longitude = 12.5,
            trafficTypeLabel = "AEREO",
            altitudeLabel = "610 m AGL",
            speedLabel = null,
            relevance = relevance
        )

    private fun trafficTarget(
        id: String = "icao:test",
        lat: Double = 41.9,
        lon: Double = 12.5,
        callsign: String? = "TEST",
        provider: String? = "opensky",
        source: String? = "OpenSky",
        speedMps: Double? = null,
        geoM: Double? = null,
        mslM: Double? = null,
        aglM: Double? = null,
        sourceM: Double? = null,
        sourceReference: String? = null
    ): TrafficTarget =
        TrafficTarget(
            id = id,
            identifiers = TrafficIdentifiers(
                icao24 = id.removePrefix("icao:").takeIf { id.startsWith("icao:") },
                callsign = callsign,
                registration = null,
                sourceId = id.substringAfter(':', id)
            ),
            position = TrafficPosition(lat = lat, lon = lon),
            altitude = TrafficAltitude(
                baroM = null,
                geoM = geoM,
                mslM = mslM,
                aglM = aglM,
                sourceM = sourceM,
                sourceReference = sourceReference
            ),
            motion = TrafficMotion(
                groundSpeedMps = speedMps,
                verticalRateMps = null,
                trackDeg = 0.0,
                headingDeg = null
            ),
            aircraft = TrafficAircraft(category = null, type = null),
            time = TrafficTime(timestamp = null, ageSec = null),
            relative = TrafficRelative(distanceM = null, bearingDeg = null),
            provider = provider,
            source = source,
            quality = null,
            sources = listOf(TrafficSource(provider = provider, source = source)),
            provenance = TrafficProvenance(
                sources = listOf(TrafficSource(provider = provider, source = source)),
                contributions = emptyList()
            ),
            objectType = null
        )
}
