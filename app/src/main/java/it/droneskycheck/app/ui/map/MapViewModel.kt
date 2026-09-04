package it.droneskycheck.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.droneskycheck.app.data.InMemoryMapPreferences
import it.droneskycheck.app.data.InMemoryLocalPilotStore
import it.droneskycheck.app.data.LegalTimelineClient
import it.droneskycheck.app.data.LegalTimelineContributor
import it.droneskycheck.app.data.LegalTimelineRepository
import it.droneskycheck.app.data.DscLogger
import it.droneskycheck.app.data.LegalTimelineRepositoryError
import it.droneskycheck.app.data.LegalTimelineResponse
import it.droneskycheck.app.data.LegalTimelineSegment
import it.droneskycheck.app.data.LegalTimelineState
import it.droneskycheck.app.data.LocalDrone
import it.droneskycheck.app.data.LocalPilotStore
import it.droneskycheck.app.data.MapPreferences
import it.droneskycheck.app.data.NotamInfo
import it.droneskycheck.app.data.TemporalBarEntry
import it.droneskycheck.app.data.UasDatasetUpdatesRepository
import it.droneskycheck.app.data.ZoneCheckV3Client
import it.droneskycheck.app.data.ZoneCheckV3Repository
import it.droneskycheck.app.data.ZoneCheckV3Response
import it.droneskycheck.app.data.ZoneInfo
import it.droneskycheck.app.data.filterableTypes
import it.droneskycheck.app.data.drone.DroneOperationalAssessmentEngine
import it.droneskycheck.app.data.drone.DroneOperationalLevel
import it.droneskycheck.app.data.drone.DroneTechnicalCatalogClient
import it.droneskycheck.app.data.drone.DroneTechnicalCatalogResolver
import it.droneskycheck.app.data.drone.InMemoryDroneTechnicalCatalogClient
import it.droneskycheck.app.data.flight.FlightLightPreference
import it.droneskycheck.app.data.flight.FlightOpportunityEngine
import it.droneskycheck.app.data.flight.FlightOpportunityDroneCandidate
import it.droneskycheck.app.data.flight.FlightOpportunityDroneRecommendation
import it.droneskycheck.app.data.flight.FlightOpportunityDroneRecommendationReason
import it.droneskycheck.app.data.flight.FlightOpportunityInput
import it.droneskycheck.app.data.flight.FlightOpportunityLevel
import it.droneskycheck.app.data.flight.FlightOpportunityMode
import it.droneskycheck.app.data.flight.FlightOpportunityResult
import it.droneskycheck.app.data.flight.FlightOpportunityStatus
import it.droneskycheck.app.data.flight.FlightOpportunityWeatherSlot
import it.droneskycheck.app.data.flight.DroneWindowCompatibility
import it.droneskycheck.app.data.help.ActiveHelpOnboarding
import it.droneskycheck.app.data.help.HelpManifest
import it.droneskycheck.app.data.help.HelpManifestClient
import it.droneskycheck.app.data.help.HelpManifestUpdateResult
import it.droneskycheck.app.data.help.HelpOnboardingPolicy
import it.droneskycheck.app.data.help.HelpOnboardingReason
import it.droneskycheck.app.data.help.HelpPreferences
import it.droneskycheck.app.data.help.HelpTourController
import it.droneskycheck.app.data.help.HelpTourEffect
import it.droneskycheck.app.data.help.HelpTourEnvironment
import it.droneskycheck.app.data.help.HelpTourSession
import it.droneskycheck.app.data.help.InMemoryHelpManifestClient
import it.droneskycheck.app.data.help.InMemoryHelpPreferences
import it.droneskycheck.app.data.solar.SolarLightCalculator
import it.droneskycheck.app.data.solar.SolarWindow
import it.droneskycheck.app.data.weather.WeatherAssessmentEngine
import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.data.weather.WeatherForecastClient
import it.droneskycheck.app.data.weather.WeatherForecastRepository
import it.droneskycheck.app.data.weather.NearbyMetarClient
import it.droneskycheck.app.data.weather.NearbyMetarRepository
import it.droneskycheck.app.data.weather.toWeatherMetrics
import it.droneskycheck.app.data.weatherMap.WeatherMapClient
import it.droneskycheck.app.data.weatherMap.WeatherMapDefaults
import it.droneskycheck.app.data.weatherMap.WeatherMapForecast
import it.droneskycheck.app.data.weatherMap.WeatherMapRepository
import it.droneskycheck.app.data.weatherMap.WeatherMapLogTag
import it.droneskycheck.app.data.weatherMap.WeatherWindField
import it.droneskycheck.app.data.weatherMap.cameraFitFor
import it.droneskycheck.app.data.weatherMap.particleVectorFieldFor
import it.droneskycheck.app.data.weatherMap.toWeatherMapDiagnosticReason
import it.droneskycheck.app.data.weatherAlerts.WeatherAlertLoadResult
import it.droneskycheck.app.data.weatherAlerts.WeatherAlertsClient
import it.droneskycheck.app.data.weatherAlerts.WeatherAlertsRepository
import it.droneskycheck.app.data.weatherAlerts.WeatherLocalChange
import it.droneskycheck.app.data.weatherAlerts.criticalityLevelLabel
import it.droneskycheck.app.data.weatherAlerts.localWeatherChange
import it.droneskycheck.app.data.weatherAlerts.weatherAlertBanner
import it.droneskycheck.app.data.traffic.TrafficAwarenessClient
import it.droneskycheck.app.data.traffic.TrafficAwarenessDefaults
import it.droneskycheck.app.data.traffic.TrafficAwarenessLogTag
import it.droneskycheck.app.data.traffic.TrafficAwarenessRepository
import it.droneskycheck.app.data.traffic.TrafficAwarenessState
import it.droneskycheck.app.data.traffic.TrafficOperationCenter
import it.droneskycheck.app.data.traffic.TrafficAlertController
import it.droneskycheck.app.data.traffic.TrafficAlertEvent
import it.droneskycheck.app.data.traffic.TrafficAssessment
import it.droneskycheck.app.data.traffic.TrafficAwarenessResponse
import it.droneskycheck.app.data.traffic.TrafficFeedType
import it.droneskycheck.app.data.traffic.TrafficHeatmapCell
import it.droneskycheck.app.data.traffic.TrafficHeatmapClient
import it.droneskycheck.app.data.traffic.TrafficHeatmapDefaults
import it.droneskycheck.app.data.traffic.TrafficHeatmapLogTag
import it.droneskycheck.app.data.traffic.TrafficHeatmapMaxAgl
import it.droneskycheck.app.data.traffic.TrafficHeatmapRepository
import it.droneskycheck.app.data.traffic.TrafficHeatmapCellDetail
import it.droneskycheck.app.data.traffic.TrafficRelevance
import it.droneskycheck.app.data.traffic.TrafficRelevanceEngine
import it.droneskycheck.app.data.traffic.TrafficTarget
import it.droneskycheck.app.data.traffic.TrafficTargetKind
import it.droneskycheck.app.data.traffic.TrafficTime
import it.droneskycheck.app.data.traffic.coarseTraffic
import it.droneskycheck.app.data.traffic.toTrafficHeatmapDiagnosticReason
import it.droneskycheck.app.data.traffic.toTrafficAwarenessDiagnosticReason
import it.droneskycheck.app.data.traffic.trafficFeedType
import it.droneskycheck.app.data.traffic.trafficTargetKind
import it.droneskycheck.app.map.DscLayerCategory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.LinkedHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel(
    private val zoneCheckRepository: ZoneCheckV3Client = ZoneCheckV3Repository(),
    private val legalTimelineRepository: LegalTimelineClient = LegalTimelineRepository(),
    private val weatherForecastRepository: WeatherForecastClient = WeatherForecastRepository(),
    private val weatherMapRepository: WeatherMapClient = WeatherMapRepository(),
    private val weatherAlertsRepository: WeatherAlertsClient = WeatherAlertsRepository(),
    private val nearbyMetarRepository: NearbyMetarClient = NearbyMetarRepository(),
    private val trafficAwarenessRepository: TrafficAwarenessClient = TrafficAwarenessRepository(),
    private val trafficHeatmapRepository: TrafficHeatmapClient = TrafficHeatmapRepository(),
    private val weatherAssessmentEngine: WeatherAssessmentEngine = WeatherAssessmentEngine(),
    private val droneAssessmentEngine: DroneOperationalAssessmentEngine = DroneOperationalAssessmentEngine(),
    private val flightOpportunityEngine: FlightOpportunityEngine = FlightOpportunityEngine(),
    private val solarLightCalculator: SolarLightCalculator = SolarLightCalculator(),
    private val droneTechnicalCatalog: DroneTechnicalCatalogClient = InMemoryDroneTechnicalCatalogClient(),
    private val mapPreferences: MapPreferences = InMemoryMapPreferences(),
    private val uasDatasetUpdatesRepository: UasDatasetUpdatesRepository? = null,
    private val helpRepository: HelpManifestClient = InMemoryHelpManifestClient(),
    private val helpPreferences: HelpPreferences = InMemoryHelpPreferences(),
    private val localPilotStore: LocalPilotStore = InMemoryLocalPilotStore(),
    private val clock: Clock = Clock.systemUTC(),
    private val timelineZoneId: ZoneId = ZoneId.systemDefault(),
    private val trafficAwarenessPollingIntervalMillis: Long = TrafficAwarenessDefaults.PollingIntervalMillis,
    private val trafficAwarenessRadiusKm: Double = TrafficAwarenessDefaults.DefaultRadiusKm,
    private val trafficHeatmapDebounceMillis: Long = TrafficHeatmapDefaults.DebounceMillis,
    private val weatherStatusPollingIntervalMillis: Long = WeatherStatusPollingIntervalMillis,
    private val weatherCameraDebounceMillis: Long = WeatherCameraDebounceMillis,
    private val weatherTimeRefreshMillis: Long = WeatherTimeRefreshMillis,
    private val loadHelpOnInit: Boolean = true,
    externalScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = externalScope ?: viewModelScope
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private val _trafficAlertEvents = MutableSharedFlow<TrafficAlertEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val trafficAlertEvents: SharedFlow<TrafficAlertEvent> = _trafficAlertEvents.asSharedFlow()
    private val _helpTourUiCommands = MutableSharedFlow<HelpTourUiCommand>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val helpTourUiCommands: SharedFlow<HelpTourUiCommand> = _helpTourUiCommands.asSharedFlow()

    private var selectionRequestId = 0L
    private var verdictJob: Job? = null
    private var legalTimelineJob: Job? = null
    private var weatherJob: Job? = null
    private var weatherMapJob: Job? = null
    private var weatherAlertsJob: Job? = null
    private var weatherStatusPollingJob: Job? = null
    private var weatherTimeRefreshJob: Job? = null
    private var weatherCameraDebounceJob: Job? = null
    private var weatherRequestGeneration = 0L
    private var weatherSessionResumed = false
    private var lastCriticalityRevision: String? = null
    private var lastVigilanceRevision: String? = null
    private var lastWeatherPoint: MapPoint? = null
    private var trafficAwarenessJob: Job? = null
    private var trafficHeatmapJob: Job? = null
    private var trafficHeatmapRequestId = 0L
    private var mapStatusMessageJob: Job? = null
    private var latestUnfilteredTrafficAwarenessResponse: TrafficAwarenessResponse? = null
    private val trafficHeatmapCache = object : LinkedHashMap<TrafficHeatmapCacheKey, List<TrafficHeatmapCell>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TrafficHeatmapCacheKey, List<TrafficHeatmapCell>>?): Boolean =
            size > TrafficHeatmapDefaults.CacheMaxEntries
    }
    private var lastLegalTimelineRequest: LegalTimelineRequestKey? = null
    private var catalogResolver: DroneTechnicalCatalogResolver = DroneTechnicalCatalogResolver.empty()
    private var analyzeNextUserLocation = false
    private val trafficRelevanceEngine = TrafficRelevanceEngine()
    private val trafficAlertController = TrafficAlertController()

    init {
        loadAccessibilityPreferences()
        loadTrafficAlertPreferences()
        loadTrafficHeatmapPreferences()
        if (loadHelpOnInit) loadHelpManifest()
        loadUasDatasetUpdates(showRefreshing = false)
        loadDroneCatalogAndFleet()
    }

    private fun loadUasDatasetUpdates(showRefreshing: Boolean) {
        val repository = uasDatasetUpdatesRepository ?: run {
            if (showRefreshing) {
                _uiState.value = _uiState.value.copy(isUasDatasetRefreshing = false)
            }
            return
        }
        if (showRefreshing && _uiState.value.isUasDatasetRefreshing) return
        if (showRefreshing) {
            _uiState.value = _uiState.value.copy(isUasDatasetRefreshing = true)
        }
        scope.launch {
            val updates = withContext(Dispatchers.IO) {
                repository.getUpdates()
            }
            _uiState.value = _uiState.value.copy(
                uasDatasetUpdates = updates ?: _uiState.value.uasDatasetUpdates,
                isUasDatasetRefreshing = false
            )
        }
    }

    fun onUasDatasetRefreshRequested() {
        loadUasDatasetUpdates(showRefreshing = true)
    }

    fun requestHelpOnboardingReplay(profileSheetVisible: Boolean = false) {
        val manifest = _uiState.value.helpManifest
        if (manifest.contentVersion > 0) {
            startHelpOnboarding(manifest, HelpOnboardingReason.REPLAY_REQUESTED, profileSheetVisible)
            return
        }
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                helpRepository.getCurrentManifest()
            }
            _uiState.value = _uiState.value.copy(helpManifest = loaded)
            startHelpOnboarding(loaded, HelpOnboardingReason.REPLAY_REQUESTED, profileSheetVisible)
        }
    }

    fun refreshHelpManifestNow() {
        if (_uiState.value.isHelpManifestRefreshing) return
        scope.launch {
            _uiState.value = _uiState.value.copy(
                isHelpManifestRefreshing = true,
                helpManifestRefreshMessage = null
            )
            val update = withContext(Dispatchers.IO) {
                helpRepository.checkForUpdatesNow()
            }
            val manifest = withContext(Dispatchers.IO) {
                helpRepository.getCurrentManifest()
            }
            _uiState.value = _uiState.value.copy(
                helpManifest = manifest,
                isHelpManifestRefreshing = false,
                helpManifestRefreshMessage = update.toHelpRefreshMessage()
            )
        }
    }

    fun onHelpOnboardingNext(profileSheetVisible: Boolean = false) {
        val active = _uiState.value.activeHelpOnboarding ?: return
        if (active.isLastStep) {
            finishHelpOnboarding(profileSheetVisible)
        } else {
            moveHelpOnboarding(direction = 1, profileSheetVisible = profileSheetVisible)
        }
    }

    fun onHelpOnboardingPrevious(profileSheetVisible: Boolean = false) {
        val active = _uiState.value.activeHelpOnboarding ?: return
        if (active.isFirstStep) return
        moveHelpOnboarding(direction = -1, profileSheetVisible = profileSheetVisible)
    }

    fun onHelpOnboardingSkipped(profileSheetVisible: Boolean = false) {
        cleanupHelpTour(profileSheetVisible)
        markHelpOnboardingSeen(_uiState.value.helpManifest)
        _uiState.value = _uiState.value.copy(activeHelpOnboarding = null)
    }

    private fun finishHelpOnboarding(profileSheetVisible: Boolean = false) {
        cleanupHelpTour(profileSheetVisible)
        markHelpOnboardingSeen(_uiState.value.helpManifest)
        _uiState.value = _uiState.value.copy(activeHelpOnboarding = null)
    }

    private fun loadHelpManifest() {
        scope.launch {
            val manifest = withContext(Dispatchers.IO) {
                helpRepository.getCurrentManifest()
            }
            _uiState.value = _uiState.value.copy(helpManifest = manifest)
            maybeStartInitialHelpOnboarding(manifest)

            val update = withContext(Dispatchers.IO) {
                helpRepository.checkForUpdatesIfDue()
            }
            if (update is HelpManifestUpdateResult.Installed) {
                val updatedManifest = withContext(Dispatchers.IO) {
                    helpRepository.getCurrentManifest()
                }
                _uiState.value = _uiState.value.copy(helpManifest = updatedManifest)
                val decision = HelpOnboardingPolicy.evaluate(
                    lastSeenOnboardingVersion = helpPreferences.getLastSeenOnboardingVersion(),
                    lastSeenContentVersion = helpPreferences.getLastSeenContentVersion(),
                    manifest = updatedManifest,
                    replayRequested = false
                )
                if (decision.newFeaturesAvailable) {
                    DscLogger.debug(LogTag, "Help: new guide content available without automatic full tour")
                }
            }
        }
    }

    private fun maybeStartInitialHelpOnboarding(manifest: HelpManifest) {
        val decision = HelpOnboardingPolicy.evaluate(
            lastSeenOnboardingVersion = helpPreferences.getLastSeenOnboardingVersion(),
            lastSeenContentVersion = helpPreferences.getLastSeenContentVersion(),
            manifest = manifest,
            replayRequested = false
        )
        if (decision.shouldShowFullTour) {
            startHelpOnboarding(manifest, HelpOnboardingReason.FIRST_RUN, profileSheetVisible = false)
        }
    }

    private fun startHelpOnboarding(
        manifest: HelpManifest,
        reason: HelpOnboardingReason,
        profileSheetVisible: Boolean
    ) {
        val steps = manifest.onboardingSteps.take(MaxHelpTourSteps)
        if (steps.isEmpty()) return
        val environment = helpTourEnvironment(profileSheetVisible)
        val firstIndex = steps.indexOfFirst { HelpTourController.canShow(it, environment) }
        if (firstIndex < 0) return
        val session = HelpTourController.initialSession(environment)
        _uiState.value = _uiState.value.copy(
            activeHelpOnboarding = ActiveHelpOnboarding(
                reason = reason,
                steps = steps,
                session = session,
                currentIndex = firstIndex
            )
        )
        prepareCurrentHelpStep(profileSheetVisible)
    }

    private fun moveHelpOnboarding(
        direction: Int,
        profileSheetVisible: Boolean = false
    ) {
        val active = _uiState.value.activeHelpOnboarding ?: return
        val currentCleanup = HelpTourController.cleanupStep(
            step = active.currentStep,
            session = active.session,
            environment = helpTourEnvironment(profileSheetVisible),
            finishingTour = false
        )
        applyHelpTourEffects(currentCleanup.effects)

        val nextIndex = generateSequence(active.currentIndex + direction) { it + direction }
            .takeWhile { it in active.steps.indices }
            .firstOrNull { index ->
                HelpTourController.canShow(
                    active.steps[index],
                    helpTourEnvironment(profileSheetVisible)
                )
            }
            ?: run {
                finishHelpOnboarding(profileSheetVisible)
                return
            }
        _uiState.value = _uiState.value.copy(
            activeHelpOnboarding = active.copy(
                currentIndex = nextIndex,
                session = currentCleanup.session
            )
        )
        prepareCurrentHelpStep(profileSheetVisible)
    }

    fun onHelpTourProfileVisibilityChanged(visible: Boolean) {
        prepareCurrentHelpStep(profileSheetVisible = visible)
        bumpHelpTourOverlay()
    }

    private fun prepareCurrentHelpStep(profileSheetVisible: Boolean) {
        val active = _uiState.value.activeHelpOnboarding ?: return
        val step = active.currentStep ?: return
        val plan = HelpTourController.prepareStep(
            step = step,
            session = active.session,
            environment = helpTourEnvironment(profileSheetVisible)
        )
        _uiState.value = _uiState.value.copy(
            activeHelpOnboarding = active.copy(session = plan.session)
        )
        applyHelpTourEffects(plan.effects)
    }

    private fun cleanupHelpTour(profileSheetVisible: Boolean) {
        val active = _uiState.value.activeHelpOnboarding ?: return
        val currentCleanup = HelpTourController.cleanupStep(
            step = active.currentStep,
            session = active.session,
            environment = helpTourEnvironment(profileSheetVisible),
            finishingTour = true
        )
        applyHelpTourEffects(currentCleanup.effects)
        val finalCleanup = HelpTourController.cleanupTour(
            session = currentCleanup.session,
            environment = helpTourEnvironment(profileSheetVisible)
        )
        applyHelpTourEffects(finalCleanup.effects)
    }

    private fun helpTourEnvironment(profileSheetVisible: Boolean): HelpTourEnvironment =
        HelpTourEnvironment(
            selectedPointAvailable = _uiState.value.selectedPoint != null,
            cameraCenterAvailable = _uiState.value.cameraBounds != null,
            selectedPointSheetVisible = _uiState.value.isZoneSheetVisible,
            layerSheetVisible = _uiState.value.isLayerSheetVisible,
            profileSheetVisible = profileSheetVisible,
            trafficEnabled = _uiState.value.trafficAwareness.enabled
        )

    private fun applyHelpTourEffects(effects: Set<HelpTourEffect>) {
        var shouldRefreshHelpOverlay = false
        effects.forEach { effect ->
            when (effect) {
                HelpTourEffect.OPEN_ZONES -> {
                    onLayerPanelRequested()
                    shouldRefreshHelpOverlay = true
                }
                HelpTourEffect.CLOSE_ZONES -> {
                    onLayerPanelDismissed()
                    shouldRefreshHelpOverlay = true
                }
                HelpTourEffect.OPEN_PROFILE -> {
                    if (_uiState.value.isZoneSheetVisible) {
                        _uiState.value = _uiState.value.copy(isZoneSheetVisible = false)
                    }
                    _helpTourUiCommands.tryEmit(HelpTourUiCommand.OpenProfile)
                    shouldRefreshHelpOverlay = true
                }
                HelpTourEffect.CLOSE_PROFILE -> {
                    _helpTourUiCommands.tryEmit(HelpTourUiCommand.CloseProfile)
                    shouldRefreshHelpOverlay = true
                }
                HelpTourEffect.ENABLE_TRAFFIC -> enableTrafficAwareness()
                HelpTourEffect.DISABLE_TRAFFIC -> disableTrafficAwareness()
                HelpTourEffect.OPEN_SELECTED_POINT_DETAILS -> {
                    if (openSelectedPointDetailsForHelp()) {
                        shouldRefreshHelpOverlay = true
                    }
                }
                HelpTourEffect.CLOSE_SELECTED_POINT_DETAILS -> {
                    onZoneSheetDismissed()
                    shouldRefreshHelpOverlay = true
                }
                HelpTourEffect.OPEN_WEATHER -> {
                    if (openSelectedPointDetailsForHelp()) {
                        if (!_uiState.value.isOperationalContextRequested) {
                            onOperationalContextRequested()
                        }
                        _uiState.value = _uiState.value.copy(
                            isOperationalReportExpanded = true,
                            isZoneSheetVisible = true
                        )
                        shouldRefreshHelpOverlay = true
                    }
                }
            }
        }
        if (shouldRefreshHelpOverlay) {
            bumpHelpTourOverlay()
        }
    }

    private fun openSelectedPointDetailsForHelp(): Boolean {
        val currentPoint = _uiState.value.selectedPoint
        if (currentPoint != null) {
            _uiState.value = _uiState.value.copy(isZoneSheetVisible = true)
            return true
        }
        val centerPoint = _uiState.value.cameraBounds?.centerPoint() ?: return false
        DscLogger.debug(
            LogTag,
            "Help: selectedPoint changed source=mapCenter lat=${centerPoint.lat} lon=${centerPoint.lon}"
        )
        requestAnalysis(
            MapTapSelection(
                point = centerPoint,
                zone = null
            )
        )
        return true
    }

    private fun bumpHelpTourOverlay() {
        if (_uiState.value.activeHelpOnboarding == null) return
        _uiState.value = _uiState.value.copy(
            helpTourOverlayRevision = _uiState.value.helpTourOverlayRevision + 1
        )
    }

    private fun markHelpOnboardingSeen(manifest: HelpManifest) {
        if (manifest.onboardingVersion > 0) {
            helpPreferences.setLastSeenOnboardingVersion(manifest.onboardingVersion)
        }
        if (manifest.contentVersion > 0) {
            helpPreferences.setLastSeenContentVersion(manifest.contentVersion)
        }
    }

    fun onAppInfoRequested() {
        val state = _uiState.value
        val trafficAttention = trafficAttentionPresentation(
            targets = state.trafficAwareness.response?.traffic?.targets.orEmpty(),
            assessments = state.trafficAssessments
        )
        if (!mapTitleAppInfoEnabled(state.mapStatusMessage, trafficAttention)) return
        _uiState.value = state.copy(isAppInfoSheetVisible = true)
    }

    fun onAppInfoDismissed() {
        _uiState.value = _uiState.value.copy(isAppInfoSheetVisible = false)
    }

    fun onMapTapped(selection: MapTapSelection) {
        requestAnalysis(selection)
    }

    fun onZoneCheckRetryRequested() {
        val point = _uiState.value.selectedPoint ?: return
        requestAnalysis(
            MapTapSelection(
                point = point,
                zone = _uiState.value.selectedZone
            )
        )
    }

    fun onOperationalContextRequested() {
        val point = _uiState.value.selectedPoint ?: run {
            showTransientMapStatus(SelectPointMessage)
            return
        }
        mapStatusMessageJob?.cancel()
        val requestId = selectionRequestId
        val windowStart = clock.instant()
        val windowEnd = legalTimelineEndIncludingWeekend(windowStart, timelineZoneId)
        val timelineRequest = LegalTimelineRequestKey(
            point = point,
            from = windowStart,
            to = windowEnd
        )
        lastLegalTimelineRequest = timelineRequest

        legalTimelineJob?.cancel()
        weatherJob?.cancel()
        weatherMapJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isOperationalContextRequested = true,
            isWeatherAnalysisEnabled = true,
            isLegalTimelineLoading = true,
            legalTimeline = null,
            legalTimelineError = null,
            isWeatherAnalysisLoading = true,
            weatherForecast = null,
            weatherAssessment = null,
            nearbyMetar = null,
            isWeatherMapLoading = true,
            weatherMapForecast = null,
            weatherMapWindField = null,
            weatherParticleField = null,
            weatherMapCameraFit = null,
            selectedForecastTime = null,
            weatherMapError = null,
            droneOperationalAssessment = null,
            flightOpportunityMode = FlightOpportunityMode.OPEN,
            flightOpportunityStatus = FlightOpportunityStatus.LOADING,
            flightOpportunityResult = null,
            isOperationalReportExpanded = false,
            weatherError = null,
            mapStatusMessage = null
        )
        DscLogger.debug(
            LogTag,
            "Operational context requested lat=${point.lat} lon=${point.lon} " +
                "from=${timelineRequest.from} to=${timelineRequest.to}"
        )

        launchLegalTimeline(
            requestId = requestId,
            request = timelineRequest
        )
        launchWeatherAnalysis(
            requestId = requestId,
            point = point
        )
        launchWeatherMap(
            requestId = requestId,
            point = point
        )
    }

    fun onWeatherAnalysisEnabledChanged(enabled: Boolean) {
        mapPreferences.setWeatherAnalysisEnabled(enabled)
        if (!enabled) {
            weatherJob?.cancel()
            weatherMapJob?.cancel()
            _uiState.value = _uiState.value.copy(
                isOperationalContextRequested = false,
                isWeatherAnalysisEnabled = false,
                isWeatherAnalysisLoading = false,
                weatherForecast = null,
                weatherAssessment = null,
                nearbyMetar = null,
                isWeatherMapLoading = false,
                weatherMapForecast = null,
                weatherMapWindField = null,
                weatherParticleField = null,
                weatherMapCameraFit = null,
                selectedForecastTime = null,
                weatherMapError = null,
                droneOperationalAssessment = null,
                flightOpportunityMode = FlightOpportunityMode.OPEN,
                flightOpportunityStatus = FlightOpportunityStatus.IDLE,
                flightOpportunityResult = null,
                isOperationalReportExpanded = false,
                weatherError = null
            )
            return
        }

        onOperationalContextRequested()
    }

    fun onAnalyzeUserLocationRequested() {
        val location = _uiState.value.userLocation ?: return
        selectUserLocationForAnalysis(location)
    }

    fun onLocationSearchSelected(point: MapPoint) {
        requestAnalysis(
            MapTapSelection(
                point = point,
                zone = null
            )
        )
    }

    private fun requestAnalysis(selection: MapTapSelection) {
        selectionRequestId += 1
        val requestId = selectionRequestId

        verdictJob?.cancel()
        legalTimelineJob?.cancel()
        weatherJob?.cancel()
        weatherMapJob?.cancel()
        val currentState = _uiState.value
        val keepTrafficSnapshot = currentState.trafficAwareness.enabled &&
            currentState.trafficAwarenessPositionLocked &&
            currentState.trafficAwarenessCenter != null

        _uiState.value = _uiState.value.copy(
            selectedZone = selection.zone,
            selectedPoint = selection.point,
            isZoneSheetVisible = true,
            isVerdictLoading = true,
            verdict = null,
            verdictError = null,
            isOperationalContextRequested = false,
            isLegalTimelineLoading = false,
            legalTimeline = null,
            legalTimelineError = null,
            isWeatherAnalysisEnabled = false,
            isWeatherAnalysisLoading = false,
            weatherForecast = null,
            weatherAssessment = null,
            nearbyMetar = null,
            isWeatherMapLoading = false,
            weatherMapForecast = null,
            weatherMapWindField = null,
            weatherParticleField = null,
            weatherMapCameraFit = null,
            selectedForecastTime = null,
            weatherMapError = null,
            droneOperationalAssessment = null,
            flightOpportunityMode = FlightOpportunityMode.OPEN,
            flightOpportunityStatus = FlightOpportunityStatus.IDLE,
            flightOpportunityResult = null,
            isOperationalReportExpanded = false,
            weatherError = null,
            trafficAssessments = if (keepTrafficSnapshot) currentState.trafficAssessments else emptyMap(),
            trafficVisualAssessments = if (keepTrafficSnapshot) currentState.trafficVisualAssessments else emptyMap(),
            selectedTrafficTarget = null
        )

        requestDscWeatherForCurrentPoint(immediate = true)

        launchZoneVerdict(requestId, selection.point)
        val stateAfterSelection = _uiState.value
        if (stateAfterSelection.trafficAwareness.enabled) {
            if (stateAfterSelection.trafficAwarenessPositionLocked && stateAfterSelection.trafficAwarenessCenter != null) {
                DscLogger.debug(
                    TrafficAwarenessLogTag,
                    "selectedPoint changed pollRestart=false reason=position_locked"
                )
            } else {
                DscLogger.debug(
                    TrafficAwarenessLogTag,
                    "selectedPoint changed pollRestart=true lat=${selection.point.lat.coarseTraffic()} lon=${selection.point.lon.coarseTraffic()}"
                )
                startTrafficAwarenessPolling(selection.point, clearSnapshot = true)
            }
        }
    }

    private fun launchZoneVerdict(requestId: Long, point: MapPoint) {
        verdictJob = scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    zoneCheckRepository.check(
                        lat = point.lat,
                        lon = point.lon
                    )
                }
            }.onSuccess { response ->
                if (!isCurrentSelection(requestId, point)) return@onSuccess
                _uiState.value = _uiState.value.copy(
                    isVerdictLoading = false,
                    verdict = response,
                    verdictError = null,
                    mapStatusMessage = null
                )
                _uiState.value = _uiState.value.withFlightOpportunity(
                    timeline = _uiState.value.legalTimeline,
                    forecast = _uiState.value.weatherForecast,
                    selectedDrone = _uiState.value.selectedDrone
                )
            }.onFailure {
                if (!isCurrentSelection(requestId, point)) return@onFailure
                _uiState.value = _uiState.value.copy(
                    isVerdictLoading = false,
                    verdictError = "DSC non e' raggiungibile in questo momento."
                )
            }
        }
    }

    private fun launchLegalTimeline(requestId: Long, request: LegalTimelineRequestKey) {
        legalTimelineJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                legalTimelineRepository.getLegalTimeline(
                    lat = request.point.lat,
                    lon = request.point.lon,
                    from = request.from,
                    to = request.to
                )
            }

            if (!isCurrentSelection(requestId, request.point)) return@launch
            result.onSuccess { response ->
                if (response.segments.isEmpty()) {
                    DscLogger.warn(
                        LogTag,
                        "Legal timeline response has no segments for lat=${request.point.lat} " +
                            "lon=${request.point.lon} from=${request.from} to=${request.to}"
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isLegalTimelineLoading = false,
                    legalTimeline = response,
                    legalTimelineError = null
                )
                _uiState.value = _uiState.value.withFlightOpportunity(
                    timeline = response,
                    forecast = _uiState.value.weatherForecast,
                    selectedDrone = _uiState.value.selectedDrone
                )
            }.onFailure { error ->
                DscLogger.warn(
                    LogTag,
                    "Legal timeline hidden by UI reason=${error.toMapLegalTimelineReason()} " +
                        "lat=${request.point.lat} lon=${request.point.lon} " +
                        "from=${request.from} to=${request.to}",
                    error
                )
                _uiState.value = _uiState.value.copy(
                    isLegalTimelineLoading = false,
                    legalTimeline = null,
                    legalTimelineError = "Previsione temporale non disponibile",
                    flightOpportunityStatus = FlightOpportunityStatus.ERROR,
                    flightOpportunityResult = null
                )
            }
        }
    }

    private fun launchWeatherAnalysis(requestId: Long, point: MapPoint) {
        weatherJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isWeatherAnalysisLoading = true,
            weatherForecast = null,
            weatherAssessment = null,
            nearbyMetar = null,
            droneOperationalAssessment = null,
            flightOpportunityStatus = FlightOpportunityStatus.LOADING,
            flightOpportunityResult = null,
            weatherError = null
        )

        weatherJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                DscLogger.debug(
                    LogTag,
                    "Weather analysis request lat=${point.lat} lon=${point.lon}"
                )
                val forecast = weatherForecastRepository.getForecast(
                    latitude = point.lat,
                    longitude = point.lon
                )
                val metar = nearbyMetarRepository.getNearbyMetar(
                    latitude = point.lat,
                    longitude = point.lon
                ).getOrNull()
                forecast.map { it to metar }
            }

            if (!isCurrentSelection(requestId, point) || !_uiState.value.isOperationalContextRequested) {
                return@launch
            }

            result.onSuccess { (forecast, metar) ->
                val assessment = forecast.closestHour(clock.instant())
                    ?.toWeatherMetrics()
                    ?.let(weatherAssessmentEngine::assess)
                DscLogger.debug(
                    LogTag,
                    "Weather analysis success lat=${point.lat} lon=${point.lon} " +
                        "hours=${forecast.hours.size} assessment=${assessment != null}"
                )
                _uiState.value = _uiState.value.copy(
                    isWeatherAnalysisLoading = false,
                    weatherForecast = forecast,
                    weatherAssessment = assessment,
                    nearbyMetar = metar,
                    droneOperationalAssessment = currentDroneAssessment(forecast, assessment),
                    weatherError = null
                )
                _uiState.value = _uiState.value.withFlightOpportunity(
                    timeline = _uiState.value.legalTimeline,
                    forecast = forecast,
                    selectedDrone = _uiState.value.selectedDrone
                )
            }.onFailure { error ->
                DscLogger.warn(
                    LogTag,
                    "Weather analysis hidden by UI reason=${error.toMapWeatherReason()} " +
                        "lat=${point.lat} lon=${point.lon}",
                    error
                )
                _uiState.value = _uiState.value.copy(
                    isWeatherAnalysisLoading = false,
                    weatherForecast = null,
                    weatherAssessment = null,
                    nearbyMetar = null,
                    droneOperationalAssessment = null,
                    flightOpportunityStatus = FlightOpportunityStatus.ERROR,
                    flightOpportunityResult = null,
                    weatherError = "Meteo non disponibile"
                )
            }
        }
    }

    private fun launchWeatherMap(requestId: Long, point: MapPoint) {
        weatherMapJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isWeatherMapLoading = true,
            weatherMapForecast = null,
            weatherMapWindField = null,
            weatherParticleField = null,
            weatherMapCameraFit = null,
            weatherMapError = null
        )

        weatherMapJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                weatherMapRepository.getWeatherMap(
                    latitude = point.lat,
                    longitude = point.lon,
                    mode = WeatherMapDefaults.ModeOperational
                )
            }

            if (!isCurrentSelection(requestId, point) || !_uiState.value.isOperationalContextRequested) {
                return@launch
            }

            result.onSuccess { forecast ->
                val current = _uiState.value
                val field = forecast.windFieldFor(
                    selectedTime = current.selectedForecastTime,
                    zoom = current.cameraBounds?.zoom ?: DefaultWeatherMapZoom
                )
                val particleField = forecast.particleVectorFieldFor(current.selectedForecastTime)
                _uiState.value = current.copy(
                    isWeatherMapLoading = false,
                    weatherMapForecast = forecast,
                    weatherMapWindField = field,
                    weatherParticleField = particleField,
                    weatherMapCameraFit = forecast.cameraFitFor(
                        id = requestId,
                        requestedLat = point.lat,
                        requestedLon = point.lon
                    ),
                    weatherMapError = if (field == null) WeatherMapUnavailableHint else null
                )
                DscLogger.debug(
                    WeatherMapLogTag,
                    "Weather map success lat=${point.lat} lon=${point.lon} " +
                        "times=${forecast.times.size} nodes=${forecast.nodes.size} fieldVectors=${field?.vectors?.size ?: 0}"
                )
            }.onFailure { error ->
                DscLogger.warn(
                    WeatherMapLogTag,
                    "Weather map hidden by UI reason=${error.toWeatherMapDiagnosticReason()} " +
                        "lat=${point.lat} lon=${point.lon}",
                    error
                )
                _uiState.value = _uiState.value.copy(
                    isWeatherMapLoading = false,
                    weatherMapForecast = null,
                    weatherMapWindField = null,
                    weatherParticleField = null,
                    weatherMapCameraFit = null,
                    weatherMapError = WeatherMapUnavailableHint
                )
            }
        }
    }

    fun onOperationalWeatherForecastTimeChanged(selectedTime: Instant?) {
        val state = _uiState.value
        val field = state.weatherMapForecast?.windFieldFor(
            selectedTime = selectedTime,
            zoom = state.cameraBounds?.zoom ?: DefaultWeatherMapZoom
        )
        val particleField = state.weatherMapForecast?.particleVectorFieldFor(selectedTime)
        _uiState.value = state.copy(
            selectedForecastTime = selectedTime,
            weatherMapWindField = field,
            weatherParticleField = particleField,
            weatherMapError = when {
                state.isWeatherMapLoading -> null
                selectedTime == null -> null
                state.weatherMapForecast != null && field == null -> WeatherMapUnavailableHint
                else -> state.weatherMapError
            }
        )
    }

    fun onOperationalWeatherSheetDismissed() {
        weatherMapJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isWeatherMapLoading = false,
            weatherMapWindField = null,
            weatherParticleField = null,
            weatherMapCameraFit = null,
            selectedForecastTime = null
        )
    }

    fun enableTrafficAwareness() {
        val point = _uiState.value.selectedPoint ?: _uiState.value.cameraBounds?.centerPoint() ?: run {
            DscLogger.warn(TrafficAwarenessLogTag, "cannot enable: selectedPoint missing")
            _uiState.value = _uiState.value.copy(
                trafficAwareness = _uiState.value.trafficAwareness.copy(
                    enabled = false,
                    loading = false,
                    error = SelectPointMessage
                )
            )
            showTransientMapStatus(SelectPointMessage)
            return
        }
        if (_uiState.value.selectedPoint == null) {
            DscLogger.debug(
                TrafficAwarenessLogTag,
                "selectedPoint changed source=mapCenter lat=${point.lat.coarseTraffic()} lon=${point.lon.coarseTraffic()}"
            )
            _uiState.value = _uiState.value.copy(selectedPoint = point)
        }

        startTrafficAwarenessPolling(point)
    }

    fun disableTrafficAwareness() {
        trafficAwarenessJob?.cancel()
        trafficAwarenessJob = null
        latestUnfilteredTrafficAwarenessResponse = null
        _uiState.value = _uiState.value.copy(
            trafficAwareness = TrafficAwarenessState(enabled = false),
            trafficAwarenessCenter = null,
            trafficAssessments = emptyMap(),
            trafficVisualAssessments = emptyMap(),
            selectedTrafficTarget = null
        )
    }

    private fun startTrafficAwarenessPolling(
        point: MapPoint,
        clearSnapshot: Boolean = false
    ) {
        trafficAwarenessJob?.cancel()
        if (clearSnapshot) {
            latestUnfilteredTrafficAwarenessResponse = null
        }
        val currentTraffic = _uiState.value.trafficAwareness
        _uiState.value = _uiState.value.copy(
            trafficAwareness = currentTraffic.copy(
                enabled = true,
                loading = true,
                response = if (clearSnapshot) null else currentTraffic.response,
                error = null
            ),
            trafficAwarenessCenter = point,
            trafficAssessments = if (clearSnapshot) emptyMap() else _uiState.value.trafficAssessments,
            trafficVisualAssessments = if (clearSnapshot) emptyMap() else _uiState.value.trafficVisualAssessments,
            selectedTrafficTarget = if (clearSnapshot) null else _uiState.value.selectedTrafficTarget
        )
        DscLogger.debug(
            TrafficAwarenessLogTag,
            "polling started lat=${point.lat.coarseTraffic()} lon=${point.lon.coarseTraffic()} " +
                "radiusKm=${trafficAwarenessRadiusKm.coarseTraffic(0)} clearSnapshot=$clearSnapshot"
        )

        trafficAwarenessJob = scope.launch {
            try {
                while (_uiState.value.trafficAwareness.enabled && _uiState.value.trafficAwarenessCenter == point) {
                    fetchTrafficAwareness(point)
                    delay(trafficAwarenessPollingIntervalMillis)
                }
            } finally {
                DscLogger.debug(
                    TrafficAwarenessLogTag,
                    "polling stopped reason=${trafficAwarenessStopReason(point)}"
                )
            }
        }
    }

    private suspend fun fetchTrafficAwareness(point: MapPoint) {
        DscLogger.trace(
            TrafficAwarenessLogTag,
            "poll request lat=${point.lat.coarseTraffic()} lon=${point.lon.coarseTraffic()} " +
                "radiusKm=${trafficAwarenessRadiusKm.coarseTraffic(0)}"
        )
        _uiState.value = _uiState.value.copy(
            trafficAwareness = _uiState.value.trafficAwareness.copy(
                loading = true,
                error = null
            )
        )

        val result = withContext(Dispatchers.IO) {
            trafficAwarenessRepository.getTrafficAwareness(
                lat = point.lat,
                lon = point.lon,
                radiusKm = trafficAwarenessRadiusKm
            )
        }

        if (!_uiState.value.trafficAwareness.enabled || _uiState.value.trafficAwarenessCenter != point) {
            return
        }

        result.onSuccess { response ->
            DscLogger.trace(
                TrafficAwarenessLogTag,
                "state updated enabled=true targets=${response.traffic.targets.size}"
            )
            val nowMillis = clock.millis()
            val previousTraffic = _uiState.value.trafficAwareness
            val unfilteredResponse = response.withPersistentDroneTargets(
                previousResponse = previousTraffic.response,
                previousLastUpdatedAt = previousTraffic.lastUpdatedAt,
                nowMillis = nowMillis
            )
            latestUnfilteredTrafficAwarenessResponse = unfilteredResponse
            val visibleResponse = unfilteredResponse.filteredForTrafficPresentation(
                filters = _uiState.value.trafficFeedFilters,
                showHighAltitudeTraffic = _uiState.value.highAltitudeTrafficAlertEnabled
            )
            val assessments = trafficRelevanceEngine.assessTrafficBatch(
                targets = visibleResponse.traffic.targets,
                operationCenter = TrafficOperationCenter(point.lat, point.lon),
                nowMillis = nowMillis
            )
            val hiddenHighAltitudeCount = unfilteredResponse.traffic.targets.count { target ->
                target.isHiddenHighAltitudeTraffic(_uiState.value.highAltitudeTrafficAlertEnabled)
            }
            val alertAttentionCount = assessments.values.count { it.relevance == TrafficRelevance.ATTENTION }
            assessments.forEach { (id, assessment) ->
                DscLogger.trace(
                    TrafficAwarenessLogTag,
                    "assessment id=$id relevance=${assessment.relevance} " +
                        "distance=${assessment.currentDistanceM?.toInt()} " +
                        "cpa=${assessment.cpaDistanceM?.toInt()} tcpa=${assessment.timeToCpaSec?.toInt()}"
                )
            }
            if (hiddenHighAltitudeCount > 0 || alertAttentionCount > 0) {
                DscLogger.debug(
                    TrafficAwarenessLogTag,
                    "attention visible=$alertAttentionCount hiddenHighAltitude=$hiddenHighAltitudeCount " +
                        "highAltitudeVisible=${_uiState.value.highAltitudeTrafficAlertEnabled}"
                )
            }
            if (!_uiState.value.trafficAwareness.enabled || _uiState.value.trafficAwarenessCenter != point) {
                return@onSuccess
            }
            trafficAlertController.update(assessments, nowMillis)?.let { event ->
                DscLogger.debug(
                    TrafficAwarenessLogTag,
                    "alert triggered target=${event.primaryTargetId} count=${event.triggeredCount}"
                )
                if (_trafficAlertEvents.subscriptionCount.value > 0) {
                    _trafficAlertEvents.tryEmit(event)
                }
            }
            _uiState.value = _uiState.value.copy(
                trafficAwareness = _uiState.value.trafficAwareness.copy(
                    enabled = true,
                    loading = false,
                    response = visibleResponse,
                    error = null,
                    lastUpdatedAt = nowMillis
                ),
                trafficAssessments = assessments,
                trafficVisualAssessments = assessments,
                selectedTrafficTarget = _uiState.value.selectedTrafficTarget?.let { selected ->
                    visibleResponse.traffic.targets.firstOrNull { it.id == selected.id }
                }
            )
        }.onFailure { error ->
            DscLogger.warn(
                TrafficAwarenessLogTag,
                "state error keepingLastSnapshot=${_uiState.value.trafficAwareness.response != null} " +
                    "reason=${error.toTrafficAwarenessDiagnosticReason()}",
                error
            )
            _uiState.value = _uiState.value.copy(
                trafficAwareness = _uiState.value.trafficAwareness.copy(
                    enabled = true,
                    loading = false,
                    error = "Traffic Awareness non disponibile"
                )
            )
        }
    }

    fun onTrafficTargetSelected(targetId: String) {
        val target = _uiState.value.trafficAwareness.response
            ?.traffic
            ?.targets
            ?.firstOrNull { it.id == targetId }
            ?: return
        _uiState.value = _uiState.value.copy(
            selectedTrafficTarget = target,
            isZoneSheetVisible = false
        )
    }

    fun onTrafficTargetSheetDismissed() {
        _uiState.value = _uiState.value.copy(selectedTrafficTarget = null)
    }

    fun onTrafficAlertSettingsRequested() {
        _uiState.value = _uiState.value.copy(isTrafficAlertSettingsSheetVisible = true)
    }

    fun onTrafficAlertSettingsDismissed() {
        _uiState.value = _uiState.value.copy(isTrafficAlertSettingsSheetVisible = false)
    }

    fun onTrafficAlertSoundEnabledChanged(enabled: Boolean) {
        mapPreferences.setTrafficAlertSoundEnabled(enabled)
        DscLogger.debug(TrafficAwarenessLogTag, "alert sound enabled=$enabled")
        _uiState.value = _uiState.value.copy(trafficAlertSoundEnabled = enabled)
    }

    fun onTrafficAlertVibrationEnabledChanged(enabled: Boolean) {
        mapPreferences.setTrafficAlertVibrationEnabled(enabled)
        DscLogger.debug(TrafficAwarenessLogTag, "alert vibration enabled=$enabled")
        _uiState.value = _uiState.value.copy(trafficAlertVibrationEnabled = enabled)
    }

    fun onHighAltitudeTrafficAlertEnabledChanged(enabled: Boolean) {
        mapPreferences.setHighAltitudeTrafficAlertEnabled(enabled)
        DscLogger.debug(TrafficAwarenessLogTag, "alert highAltitude enabled=$enabled")
        _uiState.value = _uiState.value.copy(highAltitudeTrafficAlertEnabled = enabled)
        refreshTrafficPresentationForCurrentSnapshot()
    }

    fun onTrafficAwarenessPositionLockedChanged(locked: Boolean) {
        mapPreferences.setTrafficAwarenessPositionLocked(locked)
        DscLogger.debug(TrafficAwarenessLogTag, "position locked=$locked")
        _uiState.value = _uiState.value.copy(trafficAwarenessPositionLocked = locked)
    }

    fun onTrafficFeedEnabledChanged(type: TrafficFeedType, enabled: Boolean) {
        if (type !in TrafficFeedType.filterableTypes) return
        mapPreferences.setTrafficFeedEnabled(type, enabled)
        DscLogger.debug(TrafficAwarenessLogTag, "feed filter type=${type.name} enabled=$enabled")
        val filters = _uiState.value.trafficFeedFilters + (type to enabled)
        val currentResponse = latestUnfilteredTrafficAwarenessResponse ?: _uiState.value.trafficAwareness.response
        _uiState.value = _uiState.value.copy(
            trafficFeedFilters = filters,
            trafficAwareness = _uiState.value.trafficAwareness.copy(
                response = currentResponse?.filteredForTrafficPresentation(
                    filters = filters,
                    showHighAltitudeTraffic = _uiState.value.highAltitudeTrafficAlertEnabled
                )
            )
        )
        refreshTrafficPresentationForCurrentSnapshot()
    }

    fun onLargeTextEnabledChanged(enabled: Boolean) {
        mapPreferences.setLargeTextEnabled(enabled)
        _uiState.value = _uiState.value.copy(isLargeTextEnabled = enabled)
    }

    fun onMapDarkeningEnabledChanged(enabled: Boolean) {
        if (enabled) {
            mapPreferences.setEnhancedZoneOutlinesEnabled(false)
        }
        mapPreferences.setMapDarkeningEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            isMapDarkeningEnabled = enabled,
            isEnhancedZoneOutlinesEnabled = if (enabled) false else _uiState.value.isEnhancedZoneOutlinesEnabled
        )
    }

    fun onEnhancedZoneOutlinesEnabledChanged(enabled: Boolean) {
        if (enabled) {
            mapPreferences.setMapDarkeningEnabled(false)
        }
        mapPreferences.setEnhancedZoneOutlinesEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            isEnhancedZoneOutlinesEnabled = enabled,
            isMapDarkeningEnabled = if (enabled) false else _uiState.value.isMapDarkeningEnabled
        )
    }

    fun onAutomaticLocationEnabledChanged(enabled: Boolean) {
        mapPreferences.setAutomaticLocationEnabled(enabled)
        _uiState.value = _uiState.value.copy(isAutomaticLocationEnabled = enabled)
    }

    private fun trafficAwarenessStopReason(point: MapPoint): String =
        when {
            !_uiState.value.trafficAwareness.enabled -> "disabled"
            _uiState.value.trafficAwarenessCenter != point -> "center_changed"
            else -> "cancelled"
        }

    private fun loadAccessibilityPreferences() {
        val mapDarkeningEnabled = mapPreferences.isMapDarkeningEnabled()
        val enhancedZoneOutlinesEnabled = mapPreferences.isEnhancedZoneOutlinesEnabled() && !mapDarkeningEnabled
        if (mapDarkeningEnabled && mapPreferences.isEnhancedZoneOutlinesEnabled()) {
            mapPreferences.setEnhancedZoneOutlinesEnabled(false)
        }
        _uiState.value = _uiState.value.copy(
            isLargeTextEnabled = mapPreferences.isLargeTextEnabled(),
            isMapDarkeningEnabled = mapDarkeningEnabled,
            isEnhancedZoneOutlinesEnabled = enhancedZoneOutlinesEnabled,
            isAutomaticLocationEnabled = mapPreferences.isAutomaticLocationEnabled()
        )
    }

    private fun loadTrafficAlertPreferences() {
        _uiState.value = _uiState.value.copy(
            trafficAlertSoundEnabled = mapPreferences.isTrafficAlertSoundEnabled(),
            trafficAlertVibrationEnabled = mapPreferences.isTrafficAlertVibrationEnabled(),
            highAltitudeTrafficAlertEnabled = mapPreferences.isHighAltitudeTrafficAlertEnabled(),
            trafficAwarenessPositionLocked = mapPreferences.isTrafficAwarenessPositionLocked(),
            trafficFeedFilters = TrafficFeedType.filterableTypes.associateWith { mapPreferences.isTrafficFeedEnabled(it) }
        )
    }

    private fun loadTrafficHeatmapPreferences() {
        _uiState.value = _uiState.value.copy(
            trafficHeatmap = _uiState.value.trafficHeatmap.copy(
                enabled = mapPreferences.isTrafficHeatmapEnabled(),
                maxAgl = mapPreferences.getTrafficHeatmapMaxAgl()
            )
        )
    }

    private fun refreshTrafficPresentationForCurrentSnapshot() {
        val state = _uiState.value
        val point = state.trafficAwarenessCenter ?: state.selectedPoint ?: return
        val response = (latestUnfilteredTrafficAwarenessResponse ?: state.trafficAwareness.response)
            ?.filteredForTrafficPresentation(
                filters = state.trafficFeedFilters,
                showHighAltitudeTraffic = state.highAltitudeTrafficAlertEnabled
            )
        val targets = response?.traffic?.targets.orEmpty()
        if (targets.isEmpty()) {
            _uiState.value = state.copy(
                trafficAwareness = state.trafficAwareness.copy(response = response),
                trafficAssessments = emptyMap(),
                trafficVisualAssessments = emptyMap(),
                selectedTrafficTarget = null
            )
            return
        }

        val nowMillis = clock.millis()
        val assessments = trafficRelevanceEngine.assessTrafficBatch(
            targets = targets,
            operationCenter = TrafficOperationCenter(point.lat, point.lon),
            nowMillis = nowMillis
        )
        _uiState.value = _uiState.value.copy(
            trafficAwareness = state.trafficAwareness.copy(response = response),
            trafficAssessments = assessments,
            trafficVisualAssessments = assessments,
            selectedTrafficTarget = state.selectedTrafficTarget?.let { selected ->
                targets.firstOrNull { it.id == selected.id }
            }
        )
    }

    private fun TrafficAwarenessResponse.withPersistentDroneTargets(
        previousResponse: TrafficAwarenessResponse?,
        previousLastUpdatedAt: Long?,
        nowMillis: Long
    ): TrafficAwarenessResponse {
        val previousTargets = previousResponse?.traffic?.targets.orEmpty()
        if (previousTargets.isEmpty()) return this

        val currentIds = traffic.targets.mapTo(mutableSetOf()) { it.id }
        val retainedDrones = previousTargets.mapNotNull { target ->
            if (target.id in currentIds || target.trafficTargetKind() != TrafficTargetKind.DRONE) {
                return@mapNotNull null
            }
            val lastSeenMillis = target.lastSeenMillis(
                fallbackLastUpdatedAt = previousLastUpdatedAt,
                nowMillis = nowMillis
            )
            val ageMillis = nowMillis - lastSeenMillis
            if (ageMillis in 0..TrafficAwarenessDefaults.DronePersistenceMillis) {
                target.withTrafficAge(nowMillis = nowMillis, lastSeenMillis = lastSeenMillis)
            } else {
                null
            }
        }
        if (retainedDrones.isEmpty()) return this

        DscLogger.trace(
            TrafficAwarenessLogTag,
            "drone persistence retained=${retainedDrones.size} freshTargets=${traffic.targets.size}"
        )
        val visibleTargets = traffic.targets + retainedDrones
        return copy(
            traffic = traffic.copy(
                count = visibleTargets.size,
                targets = visibleTargets
            )
        )
    }

    private fun TrafficTarget.lastSeenMillis(
        fallbackLastUpdatedAt: Long?,
        nowMillis: Long
    ): Long =
        time.timestamp
            ?: time.ageSec?.takeIf { it.isFinite() && it >= 0.0 }?.let { ageSec ->
                (fallbackLastUpdatedAt ?: nowMillis) - (ageSec * 1_000.0).toLong()
            }
            ?: fallbackLastUpdatedAt
            ?: nowMillis

    private fun TrafficTarget.withTrafficAge(nowMillis: Long, lastSeenMillis: Long): TrafficTarget =
        copy(
            time = TrafficTime(
                timestamp = time.timestamp ?: lastSeenMillis,
                ageSec = ((nowMillis - lastSeenMillis).coerceAtLeast(0L)) / 1_000.0
            )
        )

    private fun TrafficAwarenessResponse.filteredForTrafficPresentation(
        filters: Map<TrafficFeedType, Boolean>,
        showHighAltitudeTraffic: Boolean
    ): TrafficAwarenessResponse {
        val targets = traffic.targets.filter { target ->
            val type = target.trafficFeedType()
            (type == TrafficFeedType.UNKNOWN || filters[type] != false) &&
                !target.isHiddenHighAltitudeTraffic(showHighAltitudeTraffic)
        }
        return copy(
            traffic = traffic.copy(
                count = targets.size,
                targets = targets
            )
        )
    }

    private fun TrafficTarget.isHiddenHighAltitudeTraffic(showHighAltitudeTraffic: Boolean): Boolean =
        !showHighAltitudeTraffic &&
            trafficTargetKind() in HighAltitudeTrafficTargetKinds &&
            altitude.aglM?.let { it.isFinite() && it >= HighAltitudeTrafficAlertThresholdM } == true

    fun onDroneSelected(droneId: String) {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                localPilotStore.selectDrone(droneId)
                droneTechnicalCatalog.resolver() to localPilotStore.getDrones()
            }
            catalogResolver = loaded.first
            val drones = loaded.second
            val selected = drones.firstOrNull { it.isSelected } ?: drones.firstOrNull { it.id == droneId }
            val match = selected?.let { catalogResolver.resolve(it.manufacturer, it.model) }
            logDroneCatalogMatch(selected, match)
            _uiState.value = _uiState.value.copy(
                droneFleet = drones,
                selectedDrone = selected,
                selectedDroneCatalogMatch = match,
                droneOperationalAssessment = currentDroneAssessment(
                    forecast = _uiState.value.weatherForecast,
                    weatherAssessment = _uiState.value.weatherAssessment,
                    selectedDrone = selected
                )
            ).withFlightOpportunity(
                timeline = _uiState.value.legalTimeline,
                forecast = _uiState.value.weatherForecast,
                selectedDrone = selected
            )
            launchCatalogUpdateIfDue()
        }
    }

    fun onFlightLightPreferenceSelected(preference: FlightLightPreference) {
        if (_uiState.value.selectedLightPreference == preference) return
        _uiState.value = _uiState.value.copy(
            selectedLightPreference = preference
        ).withFlightOpportunity(
            timeline = _uiState.value.legalTimeline,
            forecast = _uiState.value.weatherForecast,
            selectedDrone = _uiState.value.selectedDrone
        )
    }

    fun onTechnicalPlanningRequested() {
        val result = _uiState.value.flightOpportunityResult ?: return
        if (!result.technicalPlanningAvailable) return
        _uiState.value = _uiState.value.copy(
            flightOpportunityMode = FlightOpportunityMode.TECHNICAL_PLANNING
        ).withFlightOpportunity(
            timeline = _uiState.value.legalTimeline,
            forecast = _uiState.value.weatherForecast,
            selectedDrone = _uiState.value.selectedDrone
        )
    }

    fun onOperationalReportExpansionChanged(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isOperationalReportExpanded = expanded)
    }

    private fun loadDroneCatalogAndFleet() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                droneTechnicalCatalog.resolver() to localPilotStore.getDrones()
            }
            catalogResolver = loaded.first
            val drones = loaded.second
            val selected = drones.firstOrNull { it.isSelected } ?: drones.firstOrNull()
            val match = selected?.let { catalogResolver.resolve(it.manufacturer, it.model) }
            logDroneCatalogMatch(selected, match)
            _uiState.value = _uiState.value.copy(
                droneFleet = drones,
                selectedDrone = selected,
                selectedDroneCatalogMatch = match,
                droneOperationalAssessment = currentDroneAssessment(
                    forecast = _uiState.value.weatherForecast,
                    weatherAssessment = _uiState.value.weatherAssessment,
                    selectedDrone = selected
                )
            ).withFlightOpportunity(
                timeline = _uiState.value.legalTimeline,
                forecast = _uiState.value.weatherForecast,
                selectedDrone = selected
            )
            launchCatalogUpdateIfDue()
        }
    }

    private fun launchCatalogUpdateIfDue() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                droneTechnicalCatalog.checkForUpdatesIfDue()
            }
            if (result !is it.droneskycheck.app.data.drone.DroneCatalogUpdateResult.Installed) return@launch
            catalogResolver = withContext(Dispatchers.IO) {
                droneTechnicalCatalog.resolver()
            }
            val selected = _uiState.value.selectedDrone
            val match = selected?.let { catalogResolver.resolve(it.manufacturer, it.model) }
            logDroneCatalogMatch(selected, match)
            _uiState.value = _uiState.value.copy(
                selectedDroneCatalogMatch = match,
                droneOperationalAssessment = currentDroneAssessment(
                    forecast = _uiState.value.weatherForecast,
                    weatherAssessment = _uiState.value.weatherAssessment,
                    selectedDrone = selected
                )
            ).withFlightOpportunity(
                timeline = _uiState.value.legalTimeline,
                forecast = _uiState.value.weatherForecast,
                selectedDrone = selected
            )
        }
    }

    private fun logDroneCatalogMatch(
        drone: LocalDrone?,
        match: it.droneskycheck.app.data.drone.DroneCatalogMatchResult?
    ) {
        if (drone == null) {
            DscLogger.debug(LogTag, "DroneCatalog: no selected drone to resolve")
            return
        }
        DscLogger.debug(
            LogTag,
            "DroneCatalog: resolve selected drone manufacturer='${drone.manufacturer}' model='${drone.model}' " +
                "catalogSchema=${catalogResolver.catalog.schemaVersion} catalogVersion=${catalogResolver.catalog.catalogVersion} " +
                "drones=${catalogResolver.catalog.drones.size} status=${match?.status} " +
            "matched='${match?.matchedDrone?.displayName}' suggestions=${match?.suggestions?.joinToString { it.displayName }}"
        )
    }

    private fun MapUiState.withFlightOpportunity(
        timeline: LegalTimelineResponse?,
        forecast: WeatherForecast?,
        selectedDrone: LocalDrone?
    ): MapUiState {
        if (!isWeatherAnalysisEnabled && !isOperationalContextRequested) {
            return copy(
                flightOpportunityStatus = FlightOpportunityStatus.IDLE,
                flightOpportunityResult = null
            )
        }
        if (legalTimelineError != null || weatherError != null) {
            return copy(
                flightOpportunityStatus = FlightOpportunityStatus.ERROR,
                flightOpportunityResult = null
            )
        }
        if (timeline == null || forecast == null) {
            return copy(
                flightOpportunityStatus = FlightOpportunityStatus.LOADING,
                flightOpportunityResult = null
            )
        }

        val now = clock.instant()
        val zoneId = forecast.timezone ?: timelineZoneId
        val solarWindows = selectedPoint?.let { point ->
            solarLightCalculator.windowsForRange(
                latitude = point.lat,
                longitude = point.lon,
                zoneId = zoneId,
                from = now,
                to = timeline.segments.maxOfOrNull { it.to } ?: forecast.hours.maxOfOrNull { it.instant } ?: now
            )
        }.orEmpty()
        val legalSegments = timeline.segments.withActiveHardNotamGuards(
            verdict = verdict,
            now = now,
            fallbackTo = timeline.window.to
                ?: timeline.segments.maxOfOrNull { it.to }
                ?: forecast.hours.maxOfOrNull { it.instant }
                ?: now
        )
        val result = flightOpportunityEngine.evaluate(
            FlightOpportunityInput(
                legalSegments = legalSegments,
                weatherSlots = forecast.toFlightOpportunityWeatherSlots(
                    now = now,
                    selectedDrone = selectedDrone
                ),
                zoneId = zoneId,
                now = now,
                lightPreference = selectedLightPreference,
                solarWindows = solarWindows,
                mode = flightOpportunityMode
            )
        )
        val resultWithDroneAdvice = result.withDroneRecommendation(
            legalSegments = legalSegments,
            forecast = forecast,
            now = now,
            zoneId = zoneId,
            solarWindows = solarWindows,
            lightPreference = selectedLightPreference,
            selectedDrone = selectedDrone
        )
        return copy(
            flightOpportunityStatus = resultWithDroneAdvice.status,
            flightOpportunityResult = resultWithDroneAdvice
        )
    }

    private fun FlightOpportunityResult.withDroneRecommendation(
        legalSegments: List<LegalTimelineSegment>,
        forecast: WeatherForecast,
        now: Instant,
        zoneId: ZoneId,
        solarWindows: List<SolarWindow>,
        lightPreference: FlightLightPreference,
        selectedDrone: LocalDrone?
    ): FlightOpportunityResult {
        val drones = _uiState.value.droneFleet
            .distinctBy { it.id }
            .takeIf { it.size > 1 }
            ?: return this
        val compared = drones.map { drone ->
            val capabilities = catalogResolver.capabilitiesFor(drone).first
            val weatherSlots = forecast.toFlightOpportunityWeatherSlots(
                now = now,
                selectedDrone = drone
            )
            val evaluated = flightOpportunityEngine.evaluate(
                FlightOpportunityInput(
                    legalSegments = legalSegments,
                    weatherSlots = weatherSlots,
                    zoneId = zoneId,
                    now = now,
                    lightPreference = lightPreference,
                    solarWindows = solarWindows,
                    mode = mode
                )
            )
            val best = evaluated.bestOpportunity
            val bestDroneAssessment = best?.let { opportunity ->
                weatherSlots.firstOrNull { slot ->
                    slot.droneAssessment != null &&
                        slot.from.isBefore(opportunity.to) &&
                        slot.to.isAfter(opportunity.from)
                }?.droneAssessment
            } ?: weatherSlots.firstOrNull { it.droneAssessment != null }?.droneAssessment
            FlightOpportunityDroneCandidate(
                droneId = drone.id,
                displayName = drone.displayName,
                opportunityScore = best?.opportunityScore,
                opportunityLevel = best?.opportunityLevel,
                droneScore = best?.droneScore,
                droneLevel = best?.droneLevel,
                windResistanceMs = capabilities.maxWindResistanceMs,
                massGrams = capabilities.massGrams,
                compatibility = best.toDroneWindowCompatibility(evaluated.status, bestDroneAssessment),
                compatibilityReason = best.toDroneCompatibilityReason(evaluated.status, bestDroneAssessment),
                bestFrom = best?.from,
                bestTo = best?.to
            )
        }.sortedWith(
            compareByDescending<FlightOpportunityDroneCandidate> { it.candidateRank() }
                .thenBy { it.bestFrom ?: Instant.MAX }
        )
        val usable = compared.filter { it.compatibility == DroneWindowCompatibility.USABLE }
        val usableWithCaution = compared.filter { it.compatibility == DroneWindowCompatibility.USABLE_WITH_CAUTION }
        val bestOperationalMargin = usable.firstOrNull() ?: usableWithCaution.firstOrNull()
        val lightestCompatible = usable
            .filter { it.massGrams != null && it.massGrams > 0.0 }
            .minByOrNull { requireNotNull(it.massGrams) }
        val recommended = bestOperationalMargin ?: compared.firstOrNull() ?: return this
        val selectedCandidate = selectedDrone?.let { drone ->
            compared.firstOrNull { it.droneId == drone.id }
        }
        val reason = when {
            usable.size == 1 && usableWithCaution.isEmpty() -> FlightOpportunityDroneRecommendationReason.ONLY_USABLE
            lightestCompatible != null && lightestCompatible.droneId == recommended.droneId ->
                FlightOpportunityDroneRecommendationReason.LIGHTEST_COMPATIBLE
            selectedCandidate != null &&
                recommended.bestFrom != null &&
                selectedCandidate.bestFrom != null &&
                recommended.bestFrom != selectedCandidate.bestFrom -> FlightOpportunityDroneRecommendationReason.BETTER_WINDOW
            recommended.windResistanceMs != null &&
                compared.drop(1).any { (recommended.windResistanceMs ?: 0.0) > (it.windResistanceMs ?: 0.0) } ->
                FlightOpportunityDroneRecommendationReason.WIND_MARGIN
            bestOperationalMargin != null -> FlightOpportunityDroneRecommendationReason.BEST_OPERATIONAL_MARGIN
            else -> FlightOpportunityDroneRecommendationReason.NO_CLEAR_ADVANTAGE
        }
        return copy(
            droneRecommendation = FlightOpportunityDroneRecommendation(
                recommended = recommended,
                compared = compared,
                reason = reason,
                lightestCompatible = lightestCompatible,
                bestOperationalMargin = bestOperationalMargin,
                usableCount = usable.size,
                cautionCount = usableWithCaution.size
            )
        )
    }

    private fun List<LegalTimelineSegment>.withActiveHardNotamGuards(
        verdict: ZoneCheckV3Response?,
        now: Instant,
        fallbackTo: Instant
    ): List<LegalTimelineSegment> {
        val guards = verdict.activeHardNotamGuardSegments(now, fallbackTo)
        return if (guards.isEmpty()) this else this + guards
    }

    private fun ZoneCheckV3Response?.activeHardNotamGuardSegments(
        now: Instant,
        fallbackTo: Instant
    ): List<LegalTimelineSegment> {
        val response = this ?: return emptyList()
        if (!response.verdict.status.equals("NO_FLY", ignoreCase = true)) return emptyList()

        val responseHasHardNotam = response.blockers.hasHardNotamBlocker() ||
            response.verdict.source.equals("NOTAM", ignoreCase = true)

        return response.zones.flatMap { zone ->
            val notamSegments = zone.notams.mapNotNull { notam ->
                notam.toActiveHardNotamGuardSegment(zone, now, fallbackTo)
            }
            notamSegments.ifEmpty {
                zone.toActiveHardNotamGuardSegment(responseHasHardNotam, now, fallbackTo)?.let(::listOf).orEmpty()
            }
        }.distinctBy { "${it.from}|${it.to}|${it.contributors.firstOrNull()?.designator.orEmpty()}" }
    }

    private fun NotamInfo.toActiveHardNotamGuardSegment(
        zone: ZoneInfo,
        now: Instant,
        fallbackTo: Instant
    ): LegalTimelineSegment? {
        if (!hasHardBlockingEffect()) return null
        if (!isActiveNow(zone)) return null
        if (!hasContinuousActivation()) return null

        val from = validity?.validFrom.toInstantOrNull() ?: zone.validity?.validFrom.toInstantOrNull() ?: now
        val to = validity?.validTo.toInstantOrNull() ?: zone.validity?.validTo.toInstantOrNull() ?: fallbackTo
        if (!to.isAfter(now) || !to.isAfter(from)) return null

        return activeHardNotamSegment(
            from = from,
            to = to,
            designator = code ?: zone.name,
            reasonCodes = (blockers.mapNotNull { it.code } + blockingReason + "ACTIVE_HARD_NOTAM")
                .filterNotNull()
                .distinct()
        )
    }

    private fun ZoneInfo.toActiveHardNotamGuardSegment(
        responseHasHardNotam: Boolean,
        now: Instant,
        fallbackTo: Instant
    ): LegalTimelineSegment? {
        if (!isNotamZone()) return null
        if (!responseHasHardNotam && !blockers.hasHardNotamBlocker()) return null
        if (!isActiveNow()) return null
        if (!validity.hasContinuousActivation()) return null

        val from = validity?.validFrom.toInstantOrNull() ?: now
        val to = validity?.validTo.toInstantOrNull() ?: fallbackTo
        if (!to.isAfter(now) || !to.isAfter(from)) return null

        return activeHardNotamSegment(
            from = from,
            to = to,
            designator = name,
            reasonCodes = (blockers.mapNotNull { it.code } + "ACTIVE_HARD_NOTAM").distinct()
        )
    }

    private fun activeHardNotamSegment(
        from: Instant,
        to: Instant,
        designator: String?,
        reasonCodes: List<String>
    ): LegalTimelineSegment =
        LegalTimelineSegment(
            from = from,
            to = to,
            state = LegalTimelineState.UNAVAILABLE,
            rawState = LegalTimelineState.UNAVAILABLE.name,
            maxAltitudeAgl = 0,
            authorization = null,
            contributors = listOf(
                LegalTimelineContributor(
                    id = designator?.let { "NOTAM:$it:VERDICT_GUARD" },
                    sourceType = "NOTAM",
                    designator = designator,
                    role = listOf("ACTIVE", "APPLIED_EFFECT"),
                    temporalPolicy = "SCHEDULED",
                    operationalRelevance = "OPERATIONAL",
                    maxAltitudeAgl = 0,
                    reasonCodes = reasonCodes,
                    warnings = emptyList()
                )
            ),
            warnings = emptyList(),
            confidence = "HIGH",
            reasonCodes = reasonCodes
        )

    private fun NotamInfo.hasHardBlockingEffect(): Boolean =
        blockingReason.equals("ACTIVE_HARD_NOTAM", ignoreCase = true) ||
            severity.equals("HARD", ignoreCase = true) ||
            severity.equals("BLOCKER", ignoreCase = true) ||
            blockers.hasHardNotamBlocker()

    private fun NotamInfo.isActiveNow(zone: ZoneInfo): Boolean =
        validity?.activeNow == true ||
            schedule?.activeNow == true ||
            zone.validity?.activeNow == true ||
            zone.activeNow == true ||
            zone.operationalStatus.equals("NOTAM_ACTIVE", ignoreCase = true) ||
            zone.operationalStatus.equals("ACTIVE_HARD_NOTAM", ignoreCase = true)

    private fun ZoneInfo.isActiveNow(): Boolean =
        validity?.activeNow == true ||
            activeNow == true ||
            operationalStatus.equals("NOTAM_ACTIVE", ignoreCase = true) ||
            operationalStatus.equals("ACTIVE_HARD_NOTAM", ignoreCase = true)

    private fun NotamInfo.hasContinuousActivation(): Boolean =
        daySchedule.isFullDayActive() ||
            weekSchedule.isFullWeekActive() ||
            schedule.hasContinuousActivation() ||
            validity.hasContinuousActivation()

    private fun it.droneskycheck.app.data.ValidityInfo?.hasContinuousActivation(): Boolean {
        val scheduleText = listOfNotNull(this?.schedule, this?.interpretedSchedule)
            .joinToString(" ")
        return scheduleText.isBlank() || scheduleText.isContinuousScheduleText()
    }

    private fun it.droneskycheck.app.data.ScheduleInfo?.hasContinuousActivation(): Boolean {
        val scheduleText = listOfNotNull(this?.raw, this?.human)
            .joinToString(" ")
        return scheduleText.isBlank() || scheduleText.isContinuousScheduleText()
    }

    private fun String.isContinuousScheduleText(): Boolean {
        val normalized = uppercase()
        return "H24" in normalized ||
            "24H" in normalized ||
            "24 H" in normalized ||
            "CONTINU" in normalized ||
            "SEMPRE" in normalized ||
            "ALWAYS" in normalized
    }

    private fun List<Boolean?>.isFullDayActive(): Boolean =
        isNotEmpty() && all { it == true }

    private fun List<TemporalBarEntry>.isFullWeekActive(): Boolean =
        isNotEmpty() && all { entry ->
            entry.activeRatio?.let { it >= 0.99f } == true ||
                entry.segments.any { it.start <= 0.01f && it.end >= 0.99f } ||
                entry.active == true && entry.segments.isEmpty() && entry.activeRatio == null
        }

    private fun ZoneInfo.isNotamZone(): Boolean =
        type.equals("P_NOTAM", ignoreCase = true) ||
            family.equals("NOTAM", ignoreCase = true) ||
            name?.contains("NOTAM", ignoreCase = true) == true

    private fun List<it.droneskycheck.app.data.Issue>.hasHardNotamBlocker(): Boolean =
        any { it.code.equals("ACTIVE_HARD_NOTAM", ignoreCase = true) }

    private fun String?.toInstantOrNull(): Instant? =
        this?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun FlightOpportunityDroneCandidate.isUsableRecommendation(): Boolean =
        compatibility == DroneWindowCompatibility.USABLE ||
            compatibility == DroneWindowCompatibility.USABLE_WITH_CAUTION

    private fun FlightOpportunityDroneCandidate.candidateRank(): Int {
        val base = opportunityScore ?: -100
        val drone = droneScore ?: -50
        val wind = windResistanceMs?.times(2)?.toInt() ?: 0
        val levelBonus = when (opportunityLevel) {
            FlightOpportunityLevel.EXCELLENT -> 40
            FlightOpportunityLevel.GOOD -> 25
            FlightOpportunityLevel.MARGINAL -> 10
            FlightOpportunityLevel.PARTIAL -> -20
            FlightOpportunityLevel.POOR,
            null -> -40
        }
        val compatibilityBonus = when (compatibility) {
            DroneWindowCompatibility.USABLE -> 80
            DroneWindowCompatibility.USABLE_WITH_CAUTION -> 35
            DroneWindowCompatibility.NOT_RECOMMENDED -> -20
            DroneWindowCompatibility.NOT_COMPATIBLE -> -80
            DroneWindowCompatibility.UNKNOWN -> -40
        }
        return base + drone + wind + levelBonus + compatibilityBonus
    }

    private fun it.droneskycheck.app.data.flight.FlightOpportunity?.toDroneWindowCompatibility(
        status: FlightOpportunityStatus,
        droneAssessment: it.droneskycheck.app.data.drone.DroneOperationalAssessment?
    ): DroneWindowCompatibility {
        val opportunity = this
        if (droneAssessment?.factors?.any { it.level == DroneOperationalLevel.UNFAVORABLE } == true ||
            droneAssessment?.level == DroneOperationalLevel.UNFAVORABLE
        ) {
            return DroneWindowCompatibility.NOT_COMPATIBLE
        }
        if (droneAssessment?.factors?.any { it.level == DroneOperationalLevel.CAUTION } == true ||
            droneAssessment?.level == DroneOperationalLevel.CAUTION
        ) {
            return DroneWindowCompatibility.USABLE_WITH_CAUTION
        }
        if (opportunity == null && droneAssessment != null) {
            return when (droneAssessment.level) {
                DroneOperationalLevel.FAVORABLE,
                DroneOperationalLevel.ACCEPTABLE -> DroneWindowCompatibility.USABLE
                DroneOperationalLevel.UNKNOWN -> DroneWindowCompatibility.UNKNOWN
                DroneOperationalLevel.CAUTION -> DroneWindowCompatibility.USABLE_WITH_CAUTION
                DroneOperationalLevel.UNFAVORABLE -> DroneWindowCompatibility.NOT_COMPATIBLE
            }
        }
        if (opportunity == null) {
            return when (status) {
                FlightOpportunityStatus.DRONE_UNFAVORABLE -> DroneWindowCompatibility.NOT_COMPATIBLE
                FlightOpportunityStatus.NO_FAVORABLE_WEATHER -> DroneWindowCompatibility.NOT_RECOMMENDED
                FlightOpportunityStatus.INSUFFICIENT_DATA,
                FlightOpportunityStatus.ERROR -> DroneWindowCompatibility.UNKNOWN
                else -> DroneWindowCompatibility.NOT_RECOMMENDED
            }
        }
        if (!opportunity.droneAssessmentAvailable) return DroneWindowCompatibility.UNKNOWN
        return when (opportunity.droneLevel) {
            DroneOperationalLevel.FAVORABLE,
            DroneOperationalLevel.ACCEPTABLE -> DroneWindowCompatibility.USABLE
            DroneOperationalLevel.CAUTION -> DroneWindowCompatibility.USABLE_WITH_CAUTION
            DroneOperationalLevel.UNFAVORABLE -> DroneWindowCompatibility.NOT_COMPATIBLE
            DroneOperationalLevel.UNKNOWN,
            null -> DroneWindowCompatibility.UNKNOWN
        }
    }

    private fun it.droneskycheck.app.data.flight.FlightOpportunity?.toDroneCompatibilityReason(
        status: FlightOpportunityStatus,
        droneAssessment: it.droneskycheck.app.data.drone.DroneOperationalAssessment?
    ): String {
        val opportunity = this
        droneAssessment?.factors?.firstOrNull { it.level == DroneOperationalLevel.UNFAVORABLE }?.let { return it.message }
        droneAssessment?.factors?.firstOrNull { it.level == DroneOperationalLevel.CAUTION }?.let { return it.message }
        if (opportunity == null && droneAssessment != null) {
            return when (droneAssessment.level) {
                DroneOperationalLevel.FAVORABLE -> "Vento e raffiche entro un buon margine operativo."
                DroneOperationalLevel.ACCEPTABLE -> "Condizioni entro i limiti operativi disponibili."
                DroneOperationalLevel.CAUTION -> "Margine ridotto rispetto alle condizioni previste."
                DroneOperationalLevel.UNFAVORABLE -> "Condizioni oltre i limiti operativi considerati."
                DroneOperationalLevel.UNKNOWN -> "Dati tecnici insufficienti per una valutazione completa."
            }
        }
        if (opportunity == null) {
            return when (status) {
                FlightOpportunityStatus.DRONE_UNFAVORABLE -> "Condizioni oltre i limiti operativi considerati."
                FlightOpportunityStatus.NO_FAVORABLE_WEATHER -> "Finestra poco favorevole per meteo o margine drone."
                else -> "Dati insufficienti per una valutazione completa."
            }
        }
        return when (opportunity.droneLevel) {
            DroneOperationalLevel.FAVORABLE -> "Vento e raffiche entro un buon margine operativo."
            DroneOperationalLevel.ACCEPTABLE -> "Condizioni entro i limiti operativi disponibili."
            DroneOperationalLevel.CAUTION -> "Margine ridotto rispetto alle condizioni previste."
            DroneOperationalLevel.UNFAVORABLE -> "Condizioni oltre i limiti operativi considerati."
            DroneOperationalLevel.UNKNOWN,
            null -> "Dati tecnici insufficienti per una valutazione completa."
        }
    }

    private fun WeatherForecast.toFlightOpportunityWeatherSlots(
        now: Instant,
        selectedDrone: LocalDrone?
    ): List<FlightOpportunityWeatherSlot> {
        val slotDuration = defaultForecastSlotDuration()
        val sortedHours = hours
            .filter { it.instant.plus(slotDuration).isAfter(now) }
            .sortedBy { it.instant }
        if (sortedHours.isEmpty()) return emptyList()
        val capabilities = selectedDrone?.let { catalogResolver.capabilitiesFor(it).first }

        return sortedHours.mapIndexed { index, hour ->
            val nextInstant = sortedHours.getOrNull(index + 1)?.instant
            val to = nextInstant?.takeIf { it.isAfter(hour.instant) }
                ?: hour.instant.plus(slotDuration)
            val metrics = hour.toWeatherMetrics()
            val weather = weatherAssessmentEngine.assess(metrics)
            FlightOpportunityWeatherSlot(
                from = hour.instant,
                to = to,
                weatherAssessment = weather,
                droneAssessment = capabilities?.let {
                    droneAssessmentEngine.assess(
                        metrics = metrics,
                        capabilities = it,
                        weatherAssessment = weather
                    )
                }
            )
        }
    }

    private fun WeatherForecast.defaultForecastSlotDuration(): Duration {
        val sorted = hours.map { it.instant }.sorted()
        return sorted.zipWithNext()
            .map { (from, to) -> Duration.between(from, to) }
            .firstOrNull { !it.isZero && !it.isNegative }
            ?: Duration.ofHours(1)
    }

    private fun currentDroneAssessment(
        forecast: WeatherForecast?,
        weatherAssessment: it.droneskycheck.app.data.weather.WeatherAssessment?,
        selectedDrone: LocalDrone? = _uiState.value.selectedDrone
    ) =
        if (_uiState.value.isWeatherAnalysisEnabled) {
            val capabilities = selectedDrone?.let { catalogResolver.capabilitiesFor(it).first }
            forecast
                ?.closestHour(clock.instant())
                ?.toWeatherMetrics()
                ?.let { metrics ->
                    droneAssessmentEngine.assess(
                        metrics = metrics,
                        capabilities = capabilities,
                        weatherAssessment = weatherAssessment
                    )
                }
        } else {
            null
        }

    private fun isCurrentSelection(requestId: Long, point: MapPoint): Boolean =
        selectionRequestId == requestId && _uiState.value.selectedPoint == point

    fun onMapDataDegraded() {
        showTransientMapStatus(CachedMapDataMessage)
    }

    fun onDscWeatherSessionResumed() {
        if (weatherSessionResumed) return
        weatherSessionResumed = true
        requestDscWeatherForCurrentPoint(immediate = true)
        weatherStatusPollingJob?.cancel()
        weatherStatusPollingJob = scope.launch {
            while (weatherSessionResumed) {
                refreshDscWeatherStatus()
                delay(weatherStatusPollingIntervalMillis)
            }
        }
        weatherTimeRefreshJob?.cancel()
        weatherTimeRefreshJob = scope.launch {
            while (weatherSessionResumed) {
                refreshDscWeatherForTime()
                delay(weatherTimeRefreshMillis)
            }
        }
    }

    fun onDscWeatherSessionPaused() {
        weatherSessionResumed = false
        weatherStatusPollingJob?.cancel()
        weatherStatusPollingJob = null
        weatherTimeRefreshJob?.cancel()
        weatherTimeRefreshJob = null
        weatherCameraDebounceJob?.cancel()
        weatherCameraDebounceJob = null
        weatherAlertsJob?.cancel()
        weatherAlertsJob = null
        if (_uiState.value.dscWeather.loading) {
            _uiState.value = _uiState.value.copy(
                dscWeather = _uiState.value.dscWeather.copy(loading = false)
            )
        }
    }

    fun onDscWeatherChangeMessageShown() {
        val current = _uiState.value.dscWeather
        if (current.changeMessage != null) {
            _uiState.value = _uiState.value.copy(
                dscWeather = current.copy(changeMessage = null)
            )
        }
    }

    private suspend fun refreshDscWeatherStatus() {
        val status = weatherAlertsRepository.getStatus().getOrNull() ?: return
        val hasBaseline = lastCriticalityRevision != null || lastVigilanceRevision != null
        val changed = hasBaseline && (
            status.criticalityRevision != lastCriticalityRevision ||
                status.vigilanceRevision != lastVigilanceRevision
            )
        lastCriticalityRevision = status.criticalityRevision
        lastVigilanceRevision = status.vigilanceRevision
        if (changed) {
            requestDscWeatherForCurrentPoint(immediate = true, revisionTriggered = true)
        }
    }

    private fun requestDscWeatherForCurrentPoint(
        immediate: Boolean,
        revisionTriggered: Boolean = false
    ) {
        if (!weatherSessionResumed) return
        val point = currentDscWeatherPoint()?.normalizedWeatherPoint() ?: return
        weatherCameraDebounceJob?.cancel()
        weatherCameraDebounceJob = scope.launch {
            if (!immediate) delay(weatherCameraDebounceMillis)
            loadDscWeather(point, revisionTriggered)
        }
    }

    private fun loadDscWeather(point: MapPoint, revisionTriggered: Boolean) {
        weatherRequestGeneration += 1
        val generation = weatherRequestGeneration
        weatherAlertsJob?.cancel()
        val currentWeather = _uiState.value.dscWeather
        val samePoint = lastWeatherPoint == point
        _uiState.value = _uiState.value.copy(
            dscWeather = if (samePoint) {
                currentWeather.copy(loading = currentWeather.data == null, error = false)
            } else {
                WeatherAlertUiState(loading = true)
            }
        )
        weatherAlertsJob = scope.launch {
            val result = weatherAlertsRepository.getAlerts(point.lat, point.lon)
            if (!weatherSessionResumed || generation != weatherRequestGeneration ||
                currentDscWeatherPoint()?.normalizedWeatherPoint() != point
            ) return@launch

            when (result) {
                is WeatherAlertLoadResult.Available -> {
                    val previous = _uiState.value.dscWeather.data
                    val change = if (revisionTriggered && previous != null && lastWeatherPoint == point) {
                        localWeatherChange(previous, result.response, clock.instant())
                    } else {
                        null
                    }
                    lastWeatherPoint = point
                    _uiState.value = _uiState.value.copy(
                        dscWeather = WeatherAlertUiState(
                            loading = false,
                            data = result.response,
                            banner = weatherAlertBanner(result.response, clock.instant()),
                            stale = result.stale,
                            fetchedAt = result.fetchedAt,
                            error = false,
                            changeMessage = change?.toUserMessage()
                        )
                    )
                }
                WeatherAlertLoadResult.Unavailable -> {
                    lastWeatherPoint = null
                    _uiState.value = _uiState.value.copy(
                        dscWeather = WeatherAlertUiState(error = true)
                    )
                }
            }
        }
    }

    private fun refreshDscWeatherForTime() {
        val state = _uiState.value.dscWeather
        val data = state.data ?: return
        if (state.stale && state.fetchedAt?.let {
                Duration.between(it, clock.instant()) > DscWeatherStaleWindow
            } == true
        ) {
            _uiState.value = _uiState.value.copy(dscWeather = WeatherAlertUiState(error = true))
            return
        }
        val updatedBanner = weatherAlertBanner(data, clock.instant())
        if (updatedBanner != state.banner) {
            _uiState.value = _uiState.value.copy(
                dscWeather = state.copy(banner = updatedBanner, changeMessage = null)
            )
        }
    }

    private fun currentDscWeatherPoint(): MapPoint? =
        _uiState.value.cameraBounds?.centerPoint() ?: _uiState.value.selectedPoint

    private fun showTransientMapStatus(message: String) {
        mapStatusMessageJob?.cancel()
        _uiState.value = _uiState.value.copy(
            mapStatusMessage = message
        )
        mapStatusMessageJob = scope.launch {
            delay(StatusMessageMillis)
            if (_uiState.value.mapStatusMessage == message) {
                _uiState.value = _uiState.value.copy(mapStatusMessage = null)
            }
        }
    }

    fun onZoneSheetDismissed() {
        verdictJob?.cancel()
        legalTimelineJob?.cancel()
        weatherJob?.cancel()
        val trafficEnabled = _uiState.value.trafficAwareness.enabled
        _uiState.value = _uiState.value.copy(
            selectedZone = null,
            selectedPoint = _uiState.value.selectedPoint,
            isZoneSheetVisible = false,
            isVerdictLoading = false,
            verdict = null,
            verdictError = null,
            isOperationalContextRequested = false,
            isLegalTimelineLoading = false,
            legalTimeline = null,
            legalTimelineError = null,
            isWeatherAnalysisLoading = false,
            weatherForecast = null,
            weatherAssessment = null,
            nearbyMetar = null,
            droneOperationalAssessment = null,
            flightOpportunityMode = FlightOpportunityMode.OPEN,
            flightOpportunityStatus = FlightOpportunityStatus.IDLE,
            flightOpportunityResult = null,
            weatherError = null,
            trafficAssessments = if (trafficEnabled) _uiState.value.trafficAssessments else emptyMap(),
            trafficVisualAssessments = if (trafficEnabled) _uiState.value.trafficVisualAssessments else emptyMap(),
            selectedTrafficTarget = _uiState.value.selectedTrafficTarget
        )
    }

    fun onCameraIdle(bounds: CameraBounds) {
        val current = _uiState.value
        _uiState.value = current.copy(
            cameraBounds = bounds,
            weatherMapWindField = current.weatherMapForecast?.windFieldFor(
                selectedTime = current.selectedForecastTime,
                zoom = bounds.zoom
            )
        )
        scheduleTrafficHeatmapLoad(bounds)
        requestDscWeatherForCurrentPoint(immediate = false)
    }

    fun onLayerPanelRequested() {
        _uiState.value = _uiState.value.copy(isLayerSheetVisible = true)
    }

    fun onLayerPanelDismissed() {
        _uiState.value = _uiState.value.copy(isLayerSheetVisible = false)
    }

    fun onLayerCategoryVisibilityChanged(category: DscLayerCategory, isVisible: Boolean) {
        _uiState.value = _uiState.value.copy(
            layerVisibility = _uiState.value.layerVisibility + (category to isVisible)
        )
    }

    fun onShowAllLayerCategories() {
        _uiState.value = _uiState.value.copy(
            layerVisibility = DscLayerCategory.defaultVisibility
        )
    }

    fun onHideAllLayerCategories() {
        _uiState.value = _uiState.value.copy(
            layerVisibility = DscLayerCategory.entries.associateWith { false }
        )
    }

    fun onTrafficHeatmapEnabledChanged(enabled: Boolean) {
        mapPreferences.setTrafficHeatmapEnabled(enabled)
        trafficHeatmapRequestId += 1
        if (!enabled) {
            trafficHeatmapJob?.cancel()
            trafficHeatmapJob = null
            _uiState.value = _uiState.value.copy(
                trafficHeatmap = _uiState.value.trafficHeatmap.copy(
                    enabled = false,
                    loading = false,
                    cells = emptyList(),
                    error = null,
                    selectedCell = null
                )
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            trafficHeatmap = _uiState.value.trafficHeatmap.copy(
                enabled = true,
                error = null,
                selectedCell = null
            )
        )
        scheduleTrafficHeatmapLoad(_uiState.value.cameraBounds, immediate = true)
    }

    fun onTrafficHeatmapMaxAglChanged(maxAgl: TrafficHeatmapMaxAgl) {
        trafficHeatmapRequestId += 1
        mapPreferences.setTrafficHeatmapMaxAgl(maxAgl)
        val current = _uiState.value.trafficHeatmap
        _uiState.value = _uiState.value.copy(
            trafficHeatmap = current.copy(
                maxAgl = maxAgl,
                loading = if (current.cells.isNotEmpty()) false else current.loading,
                error = null,
                selectedCell = null
            )
        )
        if (current.enabled && current.cells.isEmpty()) {
            scheduleTrafficHeatmapLoad(_uiState.value.cameraBounds, immediate = true)
        }
    }

    fun onTrafficHeatmapCellSelected(detail: TrafficHeatmapCellDetail) {
        _uiState.value = _uiState.value.copy(
            trafficHeatmap = _uiState.value.trafficHeatmap.copy(selectedCell = detail)
        )
    }

    fun onTrafficHeatmapCellDismissed() {
        _uiState.value = _uiState.value.copy(
            trafficHeatmap = _uiState.value.trafficHeatmap.copy(selectedCell = null)
        )
    }

    private fun scheduleTrafficHeatmapLoad(
        bounds: CameraBounds?,
        immediate: Boolean = false
    ) {
        val state = _uiState.value.trafficHeatmap
        if (!state.enabled) return
        val request = bounds?.toTrafficHeatmapViewportRequest(state.maxAgl)
        if (request == null) {
            trafficHeatmapJob?.cancel()
            _uiState.value = _uiState.value.copy(
                trafficHeatmap = state.copy(
                    loading = false,
                    cells = emptyList(),
                    error = TrafficHeatmapZoomHint,
                    selectedCell = null
                )
            )
            return
        }

        val cacheKey = request.toCacheKey(state.maxAgl, TrafficHeatmapDefaults.DefaultDays)
        trafficHeatmapCache[cacheKey]?.let { cachedCells ->
            trafficHeatmapJob?.cancel()
            _uiState.value = _uiState.value.copy(
                trafficHeatmap = state.copy(
                    loading = false,
                    cells = cachedCells,
                    periodDays = TrafficHeatmapDefaults.DefaultDays,
                    error = if (cachedCells.isEmpty()) TrafficHeatmapEmptyHint else null,
                    lastUpdatedAt = clock.millis()
                )
            )
            return
        }

        val requestId = ++trafficHeatmapRequestId
        trafficHeatmapJob?.cancel()
        trafficHeatmapJob = scope.launch {
            if (!immediate) delay(trafficHeatmapDebounceMillis)
            if (requestId != trafficHeatmapRequestId || !_uiState.value.trafficHeatmap.enabled) return@launch
            _uiState.value = _uiState.value.copy(
                trafficHeatmap = _uiState.value.trafficHeatmap.copy(
                    loading = true,
                    error = null
                )
            )

            val result = withContext(Dispatchers.IO) {
                trafficHeatmapRepository.getTrafficHeatmap(
                    lat = request.center.lat,
                    lon = request.center.lon,
                    radiusKm = request.radiusKm,
                    days = TrafficHeatmapDefaults.DefaultDays,
                    maxAgl = state.maxAgl
                )
            }

            if (requestId != trafficHeatmapRequestId || !_uiState.value.trafficHeatmap.enabled) return@launch
            result.onSuccess { response ->
                val cells = response.cells
                trafficHeatmapCache[cacheKey] = cells
                _uiState.value = _uiState.value.copy(
                    trafficHeatmap = _uiState.value.trafficHeatmap.copy(
                        loading = false,
                        cells = cells,
                        periodDays = response.periodDays,
                        error = if (cells.isEmpty()) TrafficHeatmapEmptyHint else null,
                        lastUpdatedAt = clock.millis(),
                        selectedCell = null
                    )
                )
            }.onFailure { error ->
                DscLogger.warn(
                    TrafficHeatmapLogTag,
                    "state error reason=${error.toTrafficHeatmapDiagnosticReason()}",
                    error
                )
                _uiState.value = _uiState.value.copy(
                    trafficHeatmap = _uiState.value.trafficHeatmap.copy(
                        loading = false,
                        error = TrafficHeatmapUnavailableHint
                    )
                )
            }
        }
    }

    fun onLocationPermissionExplanationRequested() {
        _uiState.value = _uiState.value.copy(
            locationPermissionSheetVisible = true,
            locationStatusMessage = null
        )
    }

    fun onLocationPermissionExplanationDismissed() {
        _uiState.value = _uiState.value.copy(locationPermissionSheetVisible = false)
    }

    fun onLocationEnabled() {
        val location = _uiState.value.userLocation
        analyzeNextUserLocation = location == null
        _uiState.value = _uiState.value.copy(
            isUserLocationEnabled = true,
            shouldCenterOnUserLocation = true,
            isLocationControlSheetVisible = false,
            locationPermissionSheetVisible = false,
            locationStatusMessage = null
        )
        if (location != null) {
            selectUserLocationForAnalysis(location)
        }
    }

    fun onLocationDisabled() {
        analyzeNextUserLocation = false
        _uiState.value = _uiState.value.copy(
            isUserLocationEnabled = false,
            userLocation = null,
            shouldCenterOnUserLocation = false,
            isLocationControlSheetVisible = false,
            locationStatusMessage = null
        )
    }

    fun onLocationControlRequested() {
        _uiState.value = _uiState.value.copy(isLocationControlSheetVisible = true)
    }

    fun onLocationControlDismissed() {
        _uiState.value = _uiState.value.copy(isLocationControlSheetVisible = false)
    }

    fun onLocationRecenterRequested() {
        _uiState.value = _uiState.value.copy(
            shouldCenterOnUserLocation = true,
            isLocationControlSheetVisible = false
        )
    }

    fun onUserLocationUpdated(location: UserLocation) {
        _uiState.value = _uiState.value.copy(userLocation = location)
        if (analyzeNextUserLocation && _uiState.value.isUserLocationEnabled) {
            analyzeNextUserLocation = false
            selectUserLocationForAnalysis(location)
        }
    }

    fun onUserLocationCentered() {
        _uiState.value = _uiState.value.copy(shouldCenterOnUserLocation = false)
    }

    fun onLocationPermissionDenied(permanently: Boolean) {
        analyzeNextUserLocation = false
        _uiState.value = _uiState.value.copy(
            isUserLocationEnabled = false,
            userLocation = null,
            shouldCenterOnUserLocation = false,
            isLocationControlSheetVisible = false,
            locationPermissionSheetVisible = false,
            locationStatusMessage = if (permanently) {
                "Permesso posizione non disponibile. Puoi continuare a usare la mappa e abilitarlo dalle impostazioni di Android."
            } else {
                "Posizione non attivata. Puoi comunque esplorare la mappa e selezionare un punto manualmente."
            }
        )
    }

    fun onLocationPermissionRevoked() {
        analyzeNextUserLocation = false
        _uiState.value = _uiState.value.copy(
            isUserLocationEnabled = false,
            userLocation = null,
            shouldCenterOnUserLocation = false,
            isLocationControlSheetVisible = false,
            locationStatusMessage = "Permesso posizione non piu disponibile."
        )
    }

    fun onLocationProviderUnavailable() {
        analyzeNextUserLocation = false
        _uiState.value = _uiState.value.copy(
            locationStatusMessage = "Posizione non disponibile: controlla che i servizi di localizzazione siano attivi."
        )
    }

    private fun selectUserLocationForAnalysis(location: UserLocation) {
        _uiState.value = _uiState.value.copy(
            userLocation = location,
            shouldCenterOnUserLocation = true,
            isLocationControlSheetVisible = false,
            locationPermissionSheetVisible = false,
            locationStatusMessage = null
        )
        requestAnalysis(
            MapTapSelection(
                point = location.point,
                zone = null
            )
        )
    }

    private data class LegalTimelineRequestKey(
        val point: MapPoint,
        val from: Instant,
        val to: Instant
    )

    private companion object {
        const val LogTag = "DscMapViewModel"
        const val CachedMapDataMessage = "Dati mappa salvati"
        const val SelectPointMessage = "Seleziona un punto sulla mappa"
        const val TrafficHeatmapZoomHint = "Ingrandisci la mappa per visualizzare il traffico osservato."
        const val TrafficHeatmapEmptyHint = "Nessun dato storico disponibile per l'area visualizzata."
        const val TrafficHeatmapUnavailableHint = "Dati traffico storico temporaneamente non disponibili."
        const val WeatherMapUnavailableHint = "Campo vento non disponibile"
        const val DefaultWeatherMapZoom = 13.0
        const val StatusMessageMillis = 8_000L
        const val WeatherStatusPollingIntervalMillis = 5 * 60 * 1_000L
        const val WeatherCameraDebounceMillis = 400L
        const val WeatherTimeRefreshMillis = 30_000L
        const val MaxHelpTourSteps = 7
        val DscWeatherStaleWindow: Duration = Duration.ofMinutes(30)
    }

    override fun onCleared() {
        weatherMapJob?.cancel()
        weatherAlertsJob?.cancel()
        weatherStatusPollingJob?.cancel()
        weatherTimeRefreshJob?.cancel()
        weatherCameraDebounceJob?.cancel()
        trafficAwarenessJob?.cancel()
        trafficHeatmapJob?.cancel()
        mapStatusMessageJob?.cancel()
        super.onCleared()
    }
}

private fun MapPoint.normalizedWeatherPoint(): MapPoint = MapPoint(
    lat = kotlin.math.round(lat * 10_000.0) / 10_000.0,
    lon = kotlin.math.round(lon * 10_000.0) / 10_000.0
)

private fun WeatherLocalChange.toUserMessage(): String? {
    val previousLevel = previous?.criticalityLevel
    val currentLevel = current?.criticalityLevel
    return when {
        previous == null && current != null -> "Nuova informazione meteo nella zona · ${current.detail}"
        previousLevel != null && currentLevel != null && previousLevel != currentLevel ->
            "Aggiornamento allerta · La criticità è passata da ${criticalityLevelLabel(previousLevel)} " +
                "ad ${criticalityLevelLabel(currentLevel)}"
        current != null -> "Aggiornamento DSC METEO · ${current.detail}"
        else -> null
    }
}

sealed interface HelpTourUiCommand {
    data object OpenProfile : HelpTourUiCommand
    data object CloseProfile : HelpTourUiCommand
}

private fun HelpManifestUpdateResult.toHelpRefreshMessage(): String =
    when (this) {
        is HelpManifestUpdateResult.Installed -> "Guida aggiornata alla versione $contentVersion."
        is HelpManifestUpdateResult.Skipped -> when (reason) {
            "Remote manifest is not newer" -> "Guida gia aggiornata."
            "Remote manifest is older than cache" -> "La guida online e meno recente di quella installata."
            "No remote updater configured" -> "Aggiornamento guida non disponibile."
            else -> "Nessun aggiornamento guida disponibile."
        }
        is HelpManifestUpdateResult.Failed -> "Non riesco ad aggiornare la guida."
    }

private fun Throwable.toMapLegalTimelineReason(): String =
    when (this) {
        is LegalTimelineRepositoryError.HttpError -> when (statusCode) {
            401, 403 -> "HTTP_AUTH"
            404 -> "HTTP_NOT_FOUND"
            in 500..599 -> "HTTP_SERVER"
            else -> "HTTP_$statusCode"
        }
        is LegalTimelineRepositoryError.Timeout -> "TIMEOUT"
        is LegalTimelineRepositoryError.Network -> "NETWORK"
        is LegalTimelineRepositoryError.InvalidJson -> "JSON_PARSING"
        is LegalTimelineRepositoryError.InvalidSchema -> "JSON_SCHEMA"
        is LegalTimelineRepositoryError.InvalidCoordinates,
        is LegalTimelineRepositoryError.InvalidWindow -> "REPOSITORY_INPUT"
        else -> "REPOSITORY_INTERNAL"
    }

private fun CameraBounds.centerPoint(): MapPoint? {
    val lat = (north + south) / 2.0
    val lon = (east + west) / 2.0
    return MapPoint(lat = lat, lon = lon)
        .takeIf { it.lat.isFinite() && it.lon.isFinite() }
}

private fun CameraBounds.toTrafficHeatmapViewportRequest(maxAgl: TrafficHeatmapMaxAgl): TrafficHeatmapViewportRequest? {
    if (!zoom.isFinite() || zoom < maxAgl.minZoom) return null
    val center = centerPoint() ?: return null
    val radiusKm = viewportRadiusKm(center)
    if (!radiusKm.isFinite() || radiusKm <= 0.0) return null
    val requestRadiusKm = when {
        radiusKm <= TrafficHeatmapDefaults.MaxRadiusKm -> kotlin.math.ceil(radiusKm).coerceAtLeast(1.0)
        maxAgl in CenteredMaxRadiusTrafficHeatmapFilters -> TrafficHeatmapDefaults.MaxRadiusKm
        else -> return null
    }
    return TrafficHeatmapViewportRequest(center = center, radiusKm = requestRadiusKm)
}

private fun CameraBounds.viewportRadiusKm(center: MapPoint): Double {
    val corners = listOf(
        MapPoint(north, east),
        MapPoint(north, west),
        MapPoint(south, east),
        MapPoint(south, west)
    )
    return corners.maxOf { corner -> center.distanceKmTo(corner) } * TrafficHeatmapViewportMargin
}

private fun MapPoint.distanceKmTo(other: MapPoint): Double {
    val earthRadiusKm = 6371.0
    val lat1 = Math.toRadians(lat)
    val lat2 = Math.toRadians(other.lat)
    val dLat = Math.toRadians(other.lat - lat)
    val dLon = Math.toRadians(other.lon - lon)
    val a = kotlin.math.sin(dLat / 2.0) * kotlin.math.sin(dLat / 2.0) +
        kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
        kotlin.math.sin(dLon / 2.0) * kotlin.math.sin(dLon / 2.0)
    val c = 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
    return earthRadiusKm * c
}

private fun TrafficHeatmapViewportRequest.toCacheKey(
    maxAgl: TrafficHeatmapMaxAgl,
    days: Int
): TrafficHeatmapCacheKey =
    TrafficHeatmapCacheKey(
        latBucket = kotlin.math.round(center.lat * TrafficHeatmapCacheBucketFactor) / TrafficHeatmapCacheBucketFactor,
        lonBucket = kotlin.math.round(center.lon * TrafficHeatmapCacheBucketFactor) / TrafficHeatmapCacheBucketFactor,
        radiusKm = kotlin.math.ceil(radiusKm).toInt(),
        days = days,
        maxAgl = maxAgl.preferenceValue
    )

private data class TrafficHeatmapViewportRequest(
    val center: MapPoint,
    val radiusKm: Double
)

private data class TrafficHeatmapCacheKey(
    val latBucket: Double,
    val lonBucket: Double,
    val radiusKm: Int,
    val days: Int,
    val maxAgl: String
)

private val CenteredMaxRadiusTrafficHeatmapFilters = setOf(
    TrafficHeatmapMaxAgl.Below120,
    TrafficHeatmapMaxAgl.Below300,
    TrafficHeatmapMaxAgl.Below500
)
private const val TrafficHeatmapViewportMargin = 1.08
private const val TrafficHeatmapCacheBucketFactor = 50.0

private fun Throwable.toMapWeatherReason(): String =
    when (this) {
        is it.droneskycheck.app.data.weather.WeatherForecastRepositoryError.HttpError -> when (statusCode) {
            401, 403 -> "HTTP_AUTH"
            404 -> "HTTP_NOT_FOUND"
            in 500..599 -> "HTTP_SERVER"
            else -> "HTTP_$statusCode"
        }
        is it.droneskycheck.app.data.weather.WeatherForecastRepositoryError.Timeout -> "TIMEOUT"
        is it.droneskycheck.app.data.weather.WeatherForecastRepositoryError.Network -> "NETWORK"
        is it.droneskycheck.app.data.weather.WeatherForecastRepositoryError.InvalidJson -> "JSON_PARSING"
        is it.droneskycheck.app.data.weather.WeatherForecastRepositoryError.InvalidSchema -> "JSON_SCHEMA"
        is it.droneskycheck.app.data.weather.WeatherForecastRepositoryError.UnsupportedSchemaVersion ->
            "UNSUPPORTED_SCHEMA"
        is it.droneskycheck.app.data.weather.WeatherForecastRepositoryError.EmptyForecast -> "EMPTY_FORECAST"
        is it.droneskycheck.app.data.weather.WeatherForecastRepositoryError.InvalidCoordinates -> "REPOSITORY_INPUT"
        else -> "REPOSITORY_INTERNAL"
    }

private fun WeatherForecast.closestHour(now: Instant) =
    hours.minByOrNull { hour ->
        kotlin.math.abs(Duration.between(now, hour.instant).toMillis())
    }

private const val HighAltitudeTrafficAlertThresholdM = 1_000.0
private val HighAltitudeTrafficTargetKinds = setOf(TrafficTargetKind.AIRCRAFT, TrafficTargetKind.HELICOPTER)
