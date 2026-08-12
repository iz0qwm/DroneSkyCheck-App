package it.droneskycheck.app.data.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficRelevanceEngineTest {
    private val engine = TrafficRelevanceEngine()
    private val center = TrafficOperationCenter(41.9, 12.5)
    private val nowMillis = 1_800_000_000_000L

    @Test
    fun targetNorthTrackingSouthConvergesTowardCenterWithNearZeroCpa() {
        val assessment = assess(target(northM = 2_000.0, eastM = 0.0, speedMps = 40.0, trackDeg = 180.0))

        assertEquals(true, assessment.converging)
        assertEquals(0.0, assessment.cpaDistanceM ?: -1.0, 8.0)
        assertEquals(50.0, assessment.timeToCpaSec ?: -1.0, 1.0)
        assertEquals(TrafficRelevance.ATTENTION, assessment.relevance)
    }

    @Test
    fun targetNorthTrackingNorthIsDivergingWithoutFutureCpa() {
        val assessment = assess(target(northM = 2_000.0, eastM = 0.0, speedMps = 40.0, trackDeg = 0.0))

        assertEquals(false, assessment.converging)
        assertNull(assessment.cpaDistanceM)
        assertNull(assessment.timeToCpaSec)
        assertTrue(assessment.reasons.contains(TrafficRelevanceReason.DIVERGING))
    }

    @Test
    fun lateralPassComputesKnownCpaAndTime() {
        val assessment = assess(target(northM = 4_000.0, eastM = 1_000.0, speedMps = 40.0, trackDeg = 180.0))

        assertEquals(true, assessment.converging)
        assertEquals(1_000.0, assessment.cpaDistanceM ?: -1.0, 15.0)
        assertEquals(100.0, assessment.timeToCpaSec ?: -1.0, 1.0)
        assertEquals(TrafficRelevance.ATTENTION, assessment.relevance)
    }

    @Test
    fun trackNinetyDegreesMovesEastAndTrackTwoSeventyMovesWest() {
        val eastbound = assess(target(northM = 0.0, eastM = -2_000.0, speedMps = 20.0, trackDeg = 90.0))
        val westbound = assess(target(northM = 0.0, eastM = 2_000.0, speedMps = 20.0, trackDeg = 270.0))

        assertEquals(true, eastbound.converging)
        assertEquals(0.0, eastbound.cpaDistanceM ?: -1.0, 8.0)
        assertEquals(true, westbound.converging)
        assertEquals(0.0, westbound.cpaDistanceM ?: -1.0, 8.0)
    }

    @Test
    fun zeroSpeedDoesNotCalculateCpa() {
        val assessment = assess(target(northM = 2_000.0, eastM = 0.0, speedMps = 0.0, trackDeg = 180.0))

        assertNull(assessment.converging)
        assertNull(assessment.cpaDistanceM)
        assertNull(assessment.timeToCpaSec)
        assertEquals(TrafficCalculationConfidence.INSUFFICIENT, assessment.calculationConfidence)
    }

    @Test
    fun missingTrackAndHeadingDoesNotCalculateCpa() {
        val assessment = assess(target(northM = 2_000.0, eastM = 0.0, speedMps = 40.0, trackDeg = null, headingDeg = null))

        assertNull(assessment.converging)
        assertNull(assessment.cpaDistanceM)
        assertTrue(assessment.reasons.contains(TrafficRelevanceReason.INSUFFICIENT_MOTION_DATA))
    }

    @Test
    fun headingIsUsedAsFallbackWithPartialConfidence() {
        val assessment = assess(target(northM = 2_000.0, eastM = 0.0, speedMps = 40.0, trackDeg = null, headingDeg = 180.0))

        assertEquals(true, assessment.converging)
        assertEquals(TrafficCalculationConfidence.PARTIAL, assessment.calculationConfidence)
        assertTrue(assessment.reasons.contains(TrafficRelevanceReason.HEADING_FALLBACK))
    }

    @Test
    fun informationForFarDivergingTarget() {
        val assessment = assess(target(northM = 15_000.0, eastM = 0.0, speedMps = 50.0, trackDeg = 0.0))

        assertEquals(TrafficRelevance.INFORMATION, assessment.relevance)
    }

    @Test
    fun monitorForCurrentDistance() {
        val assessment = assess(target(northM = 8_000.0, eastM = 0.0, speedMps = 35.0, trackDeg = 0.0))

        assertEquals(TrafficRelevance.MONITOR, assessment.relevance)
        assertTrue(assessment.reasons.contains(TrafficRelevanceReason.WITHIN_MONITOR_DISTANCE))
    }

    @Test
    fun monitorForConvergingFutureCpaOutsideAttentionTime() {
        val assessment = assess(target(northM = 12_000.0, eastM = 4_000.0, speedMps = 40.0, trackDeg = 180.0))

        assertEquals(true, assessment.converging)
        assertEquals(TrafficRelevance.MONITOR, assessment.relevance)
        assertTrue(assessment.timeToCpaSec ?: 0.0 > TrafficRelevanceThresholds.AttentionTimeToCpaSec)
    }

    @Test
    fun attentionRequiresMotionNotOnlyDistance() {
        val assessment = assess(target(northM = 2_000.0, eastM = 0.0, speedMps = null, trackDeg = null))

        assertEquals(TrafficRelevance.MONITOR, assessment.relevance)
        assertNull(assessment.cpaDistanceM)
        assertTrue(assessment.reasons.contains(TrafficRelevanceReason.INSUFFICIENT_MOTION_DATA))
    }

    @Test
    fun altitudeDoesNotInfluenceRelevance() {
        val low = assess(target(northM = 4_000.0, eastM = 1_000.0, speedMps = 40.0, trackDeg = 180.0, geoM = 100.0))
        val high = assess(target(northM = 4_000.0, eastM = 1_000.0, speedMps = 40.0, trackDeg = 180.0, geoM = 11_000.0))

        assertEquals(low.relevance, high.relevance)
        assertEquals(low.cpaDistanceM, high.cpaDistanceM)
        assertEquals(low.timeToCpaSec, high.timeToCpaSec)
    }

    @Test
    fun staleMotionReducesConfidenceAndDoesNotProduceAttention() {
        val assessment = assess(
            target(
                northM = 2_000.0,
                eastM = 0.0,
                speedMps = 40.0,
                trackDeg = 180.0,
                ageSec = 120.0
            )
        )

        assertEquals(TrafficCalculationConfidence.PARTIAL, assessment.calculationConfidence)
        assertEquals(TrafficRelevance.MONITOR, assessment.relevance)
        assertNull(assessment.cpaDistanceM)
        assertTrue(assessment.reasons.contains(TrafficRelevanceReason.STALE_MOTION_DATA))
    }

    @Test
    fun sameTargetChangesAssessmentWhenOperationCenterChanges() {
        val target = target(northM = 2_000.0, eastM = 0.0, speedMps = 40.0, trackDeg = 180.0)
        val original = engine.assessTraffic(target, center, nowMillis)
        val shiftedCenter = TrafficOperationCenter(center.lat + metersToLatDegrees(4_000.0), center.lon)
        val shifted = engine.assessTraffic(target, shiftedCenter, nowMillis)

        assertEquals(true, original.converging)
        assertEquals(false, shifted.converging)
    }

    private fun assess(target: TrafficTarget): TrafficAssessment =
        engine.assessTraffic(target, center, nowMillis)

    private fun target(
        northM: Double,
        eastM: Double,
        speedMps: Double?,
        trackDeg: Double?,
        headingDeg: Double? = null,
        ageSec: Double? = 2.0,
        geoM: Double? = null
    ): TrafficTarget {
        val lat = center.lat + metersToLatDegrees(northM)
        val lon = center.lon + metersToLonDegrees(eastM, center.lat)
        val distanceM = kotlin.math.hypot(eastM, northM)
        val bearingDeg = ((kotlin.math.atan2(eastM, northM) * 180.0 / kotlin.math.PI) + 360.0) % 360.0
        return TrafficTarget(
            id = "traffic:test",
            identifiers = TrafficIdentifiers(
                icao24 = null,
                callsign = "TEST",
                registration = null,
                sourceId = "test"
            ),
            position = TrafficPosition(lat = lat, lon = lon),
            altitude = TrafficAltitude(
                baroM = null,
                geoM = geoM,
                mslM = null,
                aglM = null,
                sourceM = null,
                sourceReference = null
            ),
            motion = TrafficMotion(
                groundSpeedMps = speedMps,
                verticalRateMps = null,
                trackDeg = trackDeg,
                headingDeg = headingDeg
            ),
            aircraft = TrafficAircraft(category = null, type = null),
            time = TrafficTime(
                timestamp = null,
                ageSec = ageSec
            ),
            relative = TrafficRelative(
                distanceM = distanceM,
                bearingDeg = bearingDeg
            ),
            provider = "fixture",
            source = "fixture",
            quality = null,
            sources = emptyList(),
            provenance = null
        )
    }
}

private fun metersToLatDegrees(meters: Double): Double =
    meters / 111_320.0

private fun metersToLonDegrees(meters: Double, atLat: Double): Double =
    meters / (111_320.0 * kotlin.math.cos(atLat * kotlin.math.PI / 180.0))
