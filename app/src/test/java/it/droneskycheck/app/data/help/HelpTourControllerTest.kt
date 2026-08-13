package it.droneskycheck.app.data.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpTourControllerTest {
    @Test
    fun openZonesPreparesAndCleansUpLayerSheet() {
        val environment = environment(layerSheetVisible = false)
        val session = HelpTourController.initialSession(environment)

        val prepare = HelpTourController.prepareStep(step(HelpTourTarget.ZONES_BUTTON, HelpTourAction.OPEN_ZONES), session, environment)

        assertEquals(setOf(HelpTourEffect.OPEN_ZONES), prepare.effects)
        assertTrue(prepare.session.openedLayerSheet)

        val cleanup = HelpTourController.cleanupStep(
            step = step(HelpTourTarget.ZONES_BUTTON, HelpTourAction.OPEN_ZONES),
            session = prepare.session,
            environment = environment(layerSheetVisible = true),
            finishingTour = false
        )

        assertEquals(setOf(HelpTourEffect.CLOSE_ZONES), cleanup.effects)
        assertFalse(cleanup.session.openedLayerSheet)
    }

    @Test
    fun openProfilePreparesAndCleansUpOnlyWhenTourOpenedIt() {
        val environment = environment(profileSheetVisible = false)
        val session = HelpTourController.initialSession(environment)

        val prepare = HelpTourController.prepareStep(step(HelpTourTarget.PROFILE_BUTTON, HelpTourAction.OPEN_PROFILE), session, environment)

        assertEquals(setOf(HelpTourEffect.OPEN_PROFILE), prepare.effects)
        assertTrue(prepare.session.openedProfileSheet)

        val cleanup = HelpTourController.cleanupTour(
            session = prepare.session,
            environment = environment(profileSheetVisible = true)
        )

        assertEquals(setOf(HelpTourEffect.CLOSE_PROFILE), cleanup.effects)
    }

    @Test
    fun openProfileDoesNotCloseProfileThatWasAlreadyOpen() {
        val environment = environment(profileSheetVisible = true)
        val session = HelpTourController.initialSession(environment)

        val prepare = HelpTourController.prepareStep(step(HelpTourTarget.PROFILE_BUTTON, HelpTourAction.OPEN_PROFILE), session, environment)
        val cleanup = HelpTourController.cleanupTour(
            session = prepare.session,
            environment = environment(profileSheetVisible = true)
        )

        assertEquals(emptySet<HelpTourEffect>(), prepare.effects)
        assertEquals(emptySet<HelpTourEffect>(), cleanup.effects)
    }

    @Test
    fun openProfileDoesNotReopenAfterUserDismissesItDuringSameStep() {
        val environment = environment(profileSheetVisible = false)
        val session = HelpTourController.initialSession(environment)
        val firstPrepare = HelpTourController.prepareStep(
            step(HelpTourTarget.PROFILE_BUTTON, HelpTourAction.OPEN_PROFILE),
            session,
            environment
        )

        val secondPrepare = HelpTourController.prepareStep(
            step(HelpTourTarget.PROFILE_BUTTON, HelpTourAction.OPEN_PROFILE),
            firstPrepare.session,
            environment(profileSheetVisible = false)
        )

        assertEquals(setOf(HelpTourEffect.OPEN_PROFILE), firstPrepare.effects)
        assertEquals(emptySet<HelpTourEffect>(), secondPrepare.effects)
    }

    @Test
    fun openTrafficEnablesAndRestoresWhenInitiallyOff() {
        val environment = environment(selectedPointAvailable = true, trafficEnabled = false)
        val session = HelpTourController.initialSession(environment)

        val prepare = HelpTourController.prepareStep(step(HelpTourTarget.TRAFFIC_BUTTON, HelpTourAction.OPEN_TRAFFIC), session, environment)

        assertEquals(setOf(HelpTourEffect.ENABLE_TRAFFIC), prepare.effects)
        assertTrue(prepare.session.enabledTraffic)

        val cleanup = HelpTourController.cleanupStep(
            step = step(HelpTourTarget.TRAFFIC_BUTTON, HelpTourAction.OPEN_TRAFFIC),
            session = prepare.session,
            environment = environment(selectedPointAvailable = true, trafficEnabled = true),
            finishingTour = false
        )

        assertEquals(setOf(HelpTourEffect.DISABLE_TRAFFIC), cleanup.effects)
    }

    @Test
    fun trafficWithoutPointOrCameraDoesNotEnableOrCrash() {
        val environment = environment(selectedPointAvailable = false, cameraCenterAvailable = false, trafficEnabled = false)
        val session = HelpTourController.initialSession(environment)

        val prepare = HelpTourController.prepareStep(step(HelpTourTarget.TRAFFIC_BUTTON, HelpTourAction.OPEN_TRAFFIC), session, environment)

        assertEquals(emptySet<HelpTourEffect>(), prepare.effects)
        assertFalse(prepare.session.enabledTraffic)
    }

    @Test
    fun weatherWithoutSelectedPointStaysVisibleInTour() {
        val environment = environment(selectedPointAvailable = false)

        assertTrue(HelpTourController.canShow(step(HelpTourTarget.WEATHER_ACTION, HelpTourAction.OPEN_WEATHER), environment))
    }

    @Test
    fun selectedPointPanelWithoutPointStaysVisibleInTour() {
        val environment = environment(selectedPointAvailable = false)

        assertTrue(
            HelpTourController.canShow(
                step(HelpTourTarget.SELECTED_POINT_PANEL, HelpTourAction.OPEN_SELECTED_POINT_DETAILS),
                environment
            )
        )
    }

    @Test
    fun selectedPointPanelWithSelectedPointCanOpenRealZoneSheet() {
        val environment = environment(selectedPointAvailable = true)
        val session = HelpTourController.initialSession(environment)

        val prepare = HelpTourController.prepareStep(
            step(HelpTourTarget.SELECTED_POINT_PANEL, HelpTourAction.OPEN_SELECTED_POINT_DETAILS),
            session,
            environment
        )

        assertEquals(setOf(HelpTourEffect.OPEN_SELECTED_POINT_DETAILS), prepare.effects)
    }

    @Test
    fun weatherWithSelectedPointCanOpenRealFlow() {
        val environment = environment(selectedPointAvailable = true)
        val session = HelpTourController.initialSession(environment)

        val prepare = HelpTourController.prepareStep(step(HelpTourTarget.FLIGHT_OPPORTUNITY_CARD, HelpTourAction.OPEN_WEATHER), session, environment)

        assertEquals(
            setOf(HelpTourEffect.OPEN_SELECTED_POINT_DETAILS, HelpTourEffect.OPEN_WEATHER),
            prepare.effects
        )
    }

    @Test
    fun weatherCleanupClosesZoneSheetOpenedByTour() {
        val environment = environment(selectedPointAvailable = true, selectedPointSheetVisible = false)
        val session = HelpTourController.initialSession(environment)
        val selectedPointPrepare = HelpTourController.prepareStep(
            step(HelpTourTarget.SELECTED_POINT_PANEL, HelpTourAction.OPEN_SELECTED_POINT_DETAILS),
            session,
            environment
        )
        val weatherPrepare = HelpTourController.prepareStep(
            step(HelpTourTarget.FLIGHT_OPPORTUNITY_CARD, HelpTourAction.OPEN_WEATHER),
            selectedPointPrepare.session,
            environment(selectedPointAvailable = true, selectedPointSheetVisible = true)
        )

        val cleanup = HelpTourController.cleanupStep(
            step = step(HelpTourTarget.FLIGHT_OPPORTUNITY_CARD, HelpTourAction.OPEN_WEATHER),
            session = weatherPrepare.session,
            environment = environment(selectedPointAvailable = true, selectedPointSheetVisible = true),
            finishingTour = false
        )

        assertEquals(setOf(HelpTourEffect.CLOSE_SELECTED_POINT_DETAILS), cleanup.effects)
        assertFalse(cleanup.session.openedSelectedPointSheet)
    }

    @Test
    fun replayProducesSameUiEffectsAfterCleanup() {
        val zonesStep = step(HelpTourTarget.ZONES_BUTTON, HelpTourAction.OPEN_ZONES)
        val firstSession = HelpTourController.initialSession(environment(layerSheetVisible = false))
        val firstPrepare = HelpTourController.prepareStep(zonesStep, firstSession, environment(layerSheetVisible = false))
        val firstCleanup = HelpTourController.cleanupTour(firstPrepare.session, environment(layerSheetVisible = true))

        val replaySession = HelpTourController.initialSession(environment(layerSheetVisible = false))
        val replayPrepare = HelpTourController.prepareStep(zonesStep, replaySession, environment(layerSheetVisible = false))

        assertEquals(setOf(HelpTourEffect.OPEN_ZONES), firstPrepare.effects)
        assertEquals(setOf(HelpTourEffect.CLOSE_ZONES), firstCleanup.effects)
        assertEquals(firstPrepare.effects, replayPrepare.effects)
    }

    private fun step(
        target: HelpTourTarget,
        action: HelpTourAction
    ): HelpOnboardingStep =
        HelpOnboardingStep(
            id = target.wireName,
            target = target,
            action = action,
            title = "Titolo",
            text = "Testo",
            order = 1
        )

    private fun environment(
        selectedPointAvailable: Boolean = false,
        cameraCenterAvailable: Boolean = true,
        selectedPointSheetVisible: Boolean = false,
        layerSheetVisible: Boolean = false,
        profileSheetVisible: Boolean = false,
        trafficEnabled: Boolean = false
    ): HelpTourEnvironment =
        HelpTourEnvironment(
            selectedPointAvailable = selectedPointAvailable,
            cameraCenterAvailable = cameraCenterAvailable,
            selectedPointSheetVisible = selectedPointSheetVisible,
            layerSheetVisible = layerSheetVisible,
            profileSheetVisible = profileSheetVisible,
            trafficEnabled = trafficEnabled
        )
}
