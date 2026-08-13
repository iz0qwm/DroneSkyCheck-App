package it.droneskycheck.app.data.help

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpOnboardingPolicyTest {
    @Test
    fun neverSeenShowsFirstRunTour() {
        val decision = HelpOnboardingPolicy.evaluate(
            lastSeenOnboardingVersion = 0,
            lastSeenContentVersion = 0,
            manifest = manifest(onboardingVersion = 1, contentVersion = 1),
            replayRequested = false
        )

        assertTrue(decision.firstRun)
        assertTrue(decision.shouldShowFullTour)
    }

    @Test
    fun sameVersionDoesNotShowAutomatically() {
        val decision = HelpOnboardingPolicy.evaluate(
            lastSeenOnboardingVersion = 1,
            lastSeenContentVersion = 1,
            manifest = manifest(onboardingVersion = 1, contentVersion = 1),
            replayRequested = false
        )

        assertFalse(decision.firstRun)
        assertFalse(decision.newFeaturesAvailable)
        assertFalse(decision.shouldShowFullTour)
    }

    @Test
    fun newerVersionMarksNewFeaturesWithoutRestartingFullTour() {
        val decision = HelpOnboardingPolicy.evaluate(
            lastSeenOnboardingVersion = 1,
            lastSeenContentVersion = 1,
            manifest = manifest(onboardingVersion = 2, contentVersion = 2),
            replayRequested = false
        )

        assertTrue(decision.newFeaturesAvailable)
        assertFalse(decision.shouldShowFullTour)
    }

    @Test
    fun manualReplayShowsTourEvenWhenAlreadySeen() {
        val decision = HelpOnboardingPolicy.evaluate(
            lastSeenOnboardingVersion = 1,
            lastSeenContentVersion = 1,
            manifest = manifest(onboardingVersion = 1, contentVersion = 1),
            replayRequested = true
        )

        assertTrue(decision.replayRequested)
        assertTrue(decision.shouldShowFullTour)
    }

    private fun manifest(onboardingVersion: Int, contentVersion: Int): HelpManifest =
        HelpManifest(
            schemaVersion = 1,
            contentVersion = contentVersion,
            updatedAt = "2026-08-13",
            onboardingVersion = onboardingVersion,
            onboardingSteps = listOf(
                HelpOnboardingStep(
                    id = "map",
                    target = HelpTourTarget.MAP,
                    title = "Controlla",
                    text = "Tocca",
                    order = 1
                )
            ),
            topics = emptyList()
        )
}
