package it.droneskycheck.app.data.help

data class HelpTourEnvironment(
    val selectedPointAvailable: Boolean,
    val cameraCenterAvailable: Boolean,
    val layerSheetVisible: Boolean,
    val profileSheetVisible: Boolean,
    val trafficEnabled: Boolean
)

data class HelpTourSession(
    val initialLayerSheetVisible: Boolean,
    val initialProfileSheetVisible: Boolean,
    val initialTrafficEnabled: Boolean,
    val openedLayerSheet: Boolean = false,
    val openedProfileSheet: Boolean = false,
    val enabledTraffic: Boolean = false
)

data class HelpTourStepPlan(
    val effects: Set<HelpTourEffect> = emptySet(),
    val session: HelpTourSession
)

enum class HelpTourEffect {
    OPEN_ZONES,
    CLOSE_ZONES,
    OPEN_PROFILE,
    CLOSE_PROFILE,
    ENABLE_TRAFFIC,
    DISABLE_TRAFFIC,
    OPEN_SELECTED_POINT_DETAILS,
    OPEN_WEATHER
}

object HelpTourController {
    fun initialSession(environment: HelpTourEnvironment): HelpTourSession =
        HelpTourSession(
            initialLayerSheetVisible = environment.layerSheetVisible,
            initialProfileSheetVisible = environment.profileSheetVisible,
            initialTrafficEnabled = environment.trafficEnabled
        )

    fun canShow(step: HelpOnboardingStep, environment: HelpTourEnvironment): Boolean =
        when (step.target) {
            HelpTourTarget.SELECTED_POINT_PANEL,
            HelpTourTarget.WEATHER_ACTION,
            HelpTourTarget.FLIGHT_OPPORTUNITY_CARD -> environment.selectedPointAvailable
            else -> true
        } && when (step.action) {
            HelpTourAction.OPEN_SELECTED_POINT_DETAILS,
            HelpTourAction.OPEN_WEATHER -> environment.selectedPointAvailable
            else -> true
        }

    fun prepareStep(
        step: HelpOnboardingStep,
        session: HelpTourSession,
        environment: HelpTourEnvironment
    ): HelpTourStepPlan =
        when (step.action) {
            HelpTourAction.NONE -> HelpTourStepPlan(session = session)
            HelpTourAction.OPEN_ZONES -> HelpTourStepPlan(
                effects = if (environment.layerSheetVisible) emptySet() else setOf(HelpTourEffect.OPEN_ZONES),
                session = session.copy(openedLayerSheet = session.openedLayerSheet || !environment.layerSheetVisible)
            )
            HelpTourAction.OPEN_PROFILE -> HelpTourStepPlan(
                effects = if (environment.profileSheetVisible) emptySet() else setOf(HelpTourEffect.OPEN_PROFILE),
                session = session.copy(openedProfileSheet = session.openedProfileSheet || !environment.profileSheetVisible)
            )
            HelpTourAction.OPEN_TRAFFIC -> {
                val canEnable = environment.selectedPointAvailable || environment.cameraCenterAvailable
                HelpTourStepPlan(
                    effects = if (!environment.trafficEnabled && canEnable) setOf(HelpTourEffect.ENABLE_TRAFFIC) else emptySet(),
                    session = session.copy(enabledTraffic = session.enabledTraffic || (!environment.trafficEnabled && canEnable))
                )
            }
            HelpTourAction.OPEN_SELECTED_POINT_DETAILS -> HelpTourStepPlan(
                effects = setOf(HelpTourEffect.OPEN_SELECTED_POINT_DETAILS),
                session = session
            )
            HelpTourAction.OPEN_WEATHER -> HelpTourStepPlan(
                effects = setOf(HelpTourEffect.OPEN_SELECTED_POINT_DETAILS, HelpTourEffect.OPEN_WEATHER),
                session = session
            )
        }

    fun cleanupStep(
        step: HelpOnboardingStep?,
        session: HelpTourSession,
        environment: HelpTourEnvironment,
        finishingTour: Boolean
    ): HelpTourStepPlan {
        val effects = mutableSetOf<HelpTourEffect>()
        var nextSession = session
        if (step?.action == HelpTourAction.OPEN_ZONES && session.openedLayerSheet && !session.initialLayerSheetVisible && environment.layerSheetVisible) {
            effects += HelpTourEffect.CLOSE_ZONES
            nextSession = nextSession.copy(openedLayerSheet = false)
        }
        if ((step?.action == HelpTourAction.OPEN_PROFILE || finishingTour) &&
            session.openedProfileSheet &&
            !session.initialProfileSheetVisible &&
            environment.profileSheetVisible
        ) {
            effects += HelpTourEffect.CLOSE_PROFILE
            nextSession = nextSession.copy(openedProfileSheet = false)
        }
        if ((step?.action == HelpTourAction.OPEN_TRAFFIC || finishingTour) &&
            session.enabledTraffic &&
            !session.initialTrafficEnabled &&
            environment.trafficEnabled
        ) {
            effects += HelpTourEffect.DISABLE_TRAFFIC
            nextSession = nextSession.copy(enabledTraffic = false)
        }
        return HelpTourStepPlan(effects = effects, session = nextSession)
    }

    fun cleanupTour(
        session: HelpTourSession,
        environment: HelpTourEnvironment
    ): HelpTourStepPlan {
        val effects = mutableSetOf<HelpTourEffect>()
        var nextSession = session
        if (!session.initialLayerSheetVisible && environment.layerSheetVisible) {
            effects += HelpTourEffect.CLOSE_ZONES
            nextSession = nextSession.copy(openedLayerSheet = false)
        }
        if (!session.initialProfileSheetVisible && environment.profileSheetVisible) {
            effects += HelpTourEffect.CLOSE_PROFILE
            nextSession = nextSession.copy(openedProfileSheet = false)
        }
        if (!session.initialTrafficEnabled && environment.trafficEnabled) {
            effects += HelpTourEffect.DISABLE_TRAFFIC
            nextSession = nextSession.copy(enabledTraffic = false)
        }
        return HelpTourStepPlan(effects = effects, session = nextSession)
    }
}
