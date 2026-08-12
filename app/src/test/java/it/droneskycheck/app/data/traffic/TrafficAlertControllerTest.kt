package it.droneskycheck.app.data.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficAlertControllerTest {
    @Test
    fun enteringAttentionFromMonitorProducesAlert() {
        val controller = TrafficAlertController()

        assertNull(controller.update(mapOf("A" to assessment(TrafficRelevance.MONITOR)), nowMillis = 0L))
        val event = controller.update(mapOf("A" to assessment(TrafficRelevance.ATTENTION)), nowMillis = 5_000L)

        assertEquals("A", event?.primaryTargetId)
        assertEquals(setOf("A"), event?.triggeredTargetIds)
        assertEquals(1, event?.triggeredCount)
    }

    @Test
    fun stableAttentionDoesNotRetriggerAfterFirstAlert() {
        val controller = TrafficAlertController()

        assertEquals("A", controller.update(mapOf("A" to assessment(TrafficRelevance.ATTENTION)), 0L)?.primaryTargetId)

        assertNull(controller.update(mapOf("A" to assessment(TrafficRelevance.ATTENTION)), 5_000L))
        assertNull(controller.update(mapOf("A" to assessment(TrafficRelevance.ATTENTION)), 10_000L))
    }

    @Test
    fun newTargetDirectlyInAttentionProducesAlert() {
        val event = TrafficAlertController().update(
            mapOf("A" to assessment(TrafficRelevance.ATTENTION)),
            nowMillis = 0L
        )

        assertEquals("A", event?.primaryTargetId)
    }

    @Test
    fun attentionReentryInsideCooldownDoesNotAlert() {
        val controller = TrafficAlertController()

        controller.update(mapOf("A" to assessment(TrafficRelevance.ATTENTION)), 0L)
        controller.update(mapOf("A" to assessment(TrafficRelevance.MONITOR)), 10_000L)

        assertNull(controller.update(mapOf("A" to assessment(TrafficRelevance.ATTENTION)), 20_000L))
    }

    @Test
    fun attentionReentryAfterCooldownAlertsAgain() {
        val controller = TrafficAlertController()

        controller.update(mapOf("A" to assessment(TrafficRelevance.ATTENTION)), 0L)
        controller.update(mapOf("A" to assessment(TrafficRelevance.MONITOR)), 10_000L)
        val event = controller.update(mapOf("A" to assessment(TrafficRelevance.ATTENTION)), 70_000L)

        assertEquals("A", event?.primaryTargetId)
    }

    @Test
    fun temporarilyMissingTargetKeepsAttentionMemory() {
        val controller = TrafficAlertController()

        controller.update(mapOf("A" to assessment(TrafficRelevance.ATTENTION)), 0L)
        assertNull(controller.update(emptyMap(), 5_000L))

        assertNull(controller.update(mapOf("A" to assessment(TrafficRelevance.ATTENTION)), 10_000L))
    }

    @Test
    fun newAttentionTargetAlertsWhileExistingAttentionTargetStaysQuiet() {
        val controller = TrafficAlertController()

        controller.update(
            mapOf(
                "A" to assessment(TrafficRelevance.ATTENTION),
                "B" to assessment(TrafficRelevance.MONITOR)
            ),
            0L
        )
        val event = controller.update(
            mapOf(
                "A" to assessment(TrafficRelevance.ATTENTION),
                "B" to assessment(TrafficRelevance.ATTENTION)
            ),
            5_000L
        )

        assertEquals(setOf("B"), event?.triggeredTargetIds)
        assertEquals("B", event?.primaryTargetId)
    }

    @Test
    fun multipleTargetsEnteringAttentionAreCoalescedAndMarkedAlerted() {
        val controller = TrafficAlertController()
        val assessments = mapOf(
            "A" to assessment(TrafficRelevance.ATTENTION),
            "B" to assessment(TrafficRelevance.ATTENTION),
            "C" to assessment(TrafficRelevance.ATTENTION)
        )

        val event = controller.update(assessments, 0L)

        assertEquals(3, event?.triggeredCount)
        assertEquals(setOf("A", "B", "C"), event?.triggeredTargetIds)
        assertNull(controller.update(assessments, 5_000L))
    }

    @Test
    fun informationAndMonitorDoNotAlert() {
        val controller = TrafficAlertController()

        assertNull(controller.update(mapOf("A" to assessment(TrafficRelevance.INFORMATION)), 0L))
        assertNull(controller.update(mapOf("A" to assessment(TrafficRelevance.MONITOR)), 5_000L))
    }

    @Test
    fun cooldownIsPerTarget() {
        val controller = TrafficAlertController()

        controller.update(mapOf("A" to assessment(TrafficRelevance.ATTENTION)), 0L)
        val event = controller.update(
            mapOf(
                "A" to assessment(TrafficRelevance.ATTENTION),
                "B" to assessment(TrafficRelevance.ATTENTION)
            ),
            5_000L
        )

        assertEquals(setOf("B"), event?.triggeredTargetIds)
    }

    @Test
    fun retentionCleanupAllowsFreshAttentionAfterExpiry() {
        val controller = TrafficAlertController()

        controller.update(mapOf("A" to assessment(TrafficRelevance.ATTENTION)), 0L)
        controller.update(emptyMap(), TrafficAlertDefaults.TargetMemoryRetentionMs + 1L)

        val event = controller.update(
            mapOf("A" to assessment(TrafficRelevance.ATTENTION)),
            TrafficAlertDefaults.TargetMemoryRetentionMs + 2L
        )

        assertEquals("A", event?.primaryTargetId)
        assertEquals(1, controller.trackedTargetCount())
    }

    @Test
    fun primaryRankingUsesTimeCpaThenCpaDistanceThenCurrentDistanceThenId() {
        val controller = TrafficAlertController()

        val timeEvent = controller.update(
            mapOf(
                "slow" to assessment(TrafficRelevance.ATTENTION, timeToCpaSec = 80.0, cpaDistanceM = 100.0),
                "fast" to assessment(TrafficRelevance.ATTENTION, timeToCpaSec = 20.0, cpaDistanceM = 900.0)
            ),
            0L
        )
        assertEquals("fast", timeEvent?.primaryTargetId)

        val cpaEvent = TrafficAlertController().update(
            mapOf(
                "near-cpa" to assessment(TrafficRelevance.ATTENTION, timeToCpaSec = null, cpaDistanceM = 50.0),
                "far-cpa" to assessment(TrafficRelevance.ATTENTION, timeToCpaSec = null, cpaDistanceM = 100.0)
            ),
            0L
        )
        assertEquals("near-cpa", cpaEvent?.primaryTargetId)

        val currentDistanceEvent = TrafficAlertController().update(
            mapOf(
                "far" to assessment(TrafficRelevance.ATTENTION, timeToCpaSec = null, cpaDistanceM = null, currentDistanceM = 900.0),
                "near" to assessment(TrafficRelevance.ATTENTION, timeToCpaSec = null, cpaDistanceM = null, currentDistanceM = 100.0)
            ),
            0L
        )
        assertEquals("near", currentDistanceEvent?.primaryTargetId)

        val idEvent = TrafficAlertController().update(
            mapOf(
                "B" to assessment(TrafficRelevance.ATTENTION),
                "A" to assessment(TrafficRelevance.ATTENTION)
            ),
            0L
        )
        assertEquals("A", idEvent?.primaryTargetId)
    }

    @Test
    fun defaultValuesAreCentralized() {
        assertEquals(60_000L, TrafficAlertDefaults.AlertCooldownMs)
        assertEquals(120_000L, TrafficAlertDefaults.TargetMemoryRetentionMs)
        assertTrue(TrafficAlertDefaults.TargetMemoryRetentionMs > TrafficAlertDefaults.AlertCooldownMs)
    }
}

private fun assessment(
    relevance: TrafficRelevance,
    currentDistanceM: Double? = 1_000.0,
    cpaDistanceM: Double? = 500.0,
    timeToCpaSec: Double? = 30.0
): TrafficAssessment =
    TrafficAssessment(
        relevance = relevance,
        currentDistanceM = currentDistanceM,
        converging = true,
        relativeBearingDeg = null,
        trackDifferenceDeg = null,
        cpaDistanceM = cpaDistanceM,
        timeToCpaSec = timeToCpaSec,
        calculationConfidence = TrafficCalculationConfidence.HIGH,
        reasons = emptyList()
    )
