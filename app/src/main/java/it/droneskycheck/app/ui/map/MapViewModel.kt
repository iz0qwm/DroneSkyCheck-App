package it.droneskycheck.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.droneskycheck.app.data.InMemoryMapPreferences
import it.droneskycheck.app.data.InMemoryLocalPilotStore
import it.droneskycheck.app.data.LegalTimelineClient
import it.droneskycheck.app.data.LegalTimelineRepository
import it.droneskycheck.app.data.DscLogger
import it.droneskycheck.app.data.LegalTimelineRepositoryError
import it.droneskycheck.app.data.LegalTimelineResponse
import it.droneskycheck.app.data.LocalDrone
import it.droneskycheck.app.data.LocalPilotStore
import it.droneskycheck.app.data.MapPreferences
import it.droneskycheck.app.data.ZoneCheckV3Client
import it.droneskycheck.app.data.ZoneCheckV3Repository
import it.droneskycheck.app.data.drone.DroneOperationalAssessmentEngine
import it.droneskycheck.app.data.drone.DroneTechnicalCatalogClient
import it.droneskycheck.app.data.drone.DroneTechnicalCatalogResolver
import it.droneskycheck.app.data.drone.InMemoryDroneTechnicalCatalogClient
import it.droneskycheck.app.data.flight.FlightOpportunityEngine
import it.droneskycheck.app.data.flight.FlightOpportunityDroneCandidate
import it.droneskycheck.app.data.flight.FlightOpportunityDroneRecommendation
import it.droneskycheck.app.data.flight.FlightOpportunityDroneRecommendationReason
import it.droneskycheck.app.data.flight.FlightOpportunityInput
import it.droneskycheck.app.data.flight.FlightOpportunityLevel
import it.droneskycheck.app.data.flight.FlightOpportunityResult
import it.droneskycheck.app.data.flight.FlightOpportunityStatus
import it.droneskycheck.app.data.flight.FlightOpportunityWeatherSlot
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
import it.droneskycheck.app.data.weather.WeatherAssessmentEngine
import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.data.weather.WeatherForecastClient
import it.droneskycheck.app.data.weather.WeatherForecastRepository
import it.droneskycheck.app.data.weather.toWeatherMetrics
import it.droneskycheck.app.data.traffic.TrafficAwarenessClient
import it.droneskycheck.app.data.traffic.TrafficAwarenessDefaults
import it.droneskycheck.app.data.traffic.TrafficAwarenessLogTag
import it.droneskycheck.app.data.traffic.TrafficAwarenessRepository
import it.droneskycheck.app.data.traffic.TrafficAwarenessState
import it.droneskycheck.app.data.traffic.TrafficOperationCenter
import it.droneskycheck.app.data.traffic.TrafficAlertController
import it.droneskycheck.app.data.traffic.TrafficAlertEvent
import it.droneskycheck.app.data.traffic.TrafficRelevanceEngine
import it.droneskycheck.app.data.traffic.coarseTraffic
import it.droneskycheck.app.data.traffic.toTrafficAwarenessDiagnosticReason
import it.droneskycheck.app.map.DscLayerCategory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
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
    private val trafficAwarenessRepository: TrafficAwarenessClient = TrafficAwarenessRepository(),
    private val weatherAssessmentEngine: WeatherAssessmentEngine = WeatherAssessmentEngine(),
    private val droneAssessmentEngine: DroneOperationalAssessmentEngine = DroneOperationalAssessmentEngine(),
    private val flightOpportunityEngine: FlightOpportunityEngine = FlightOpportunityEngine(),
    private val droneTechnicalCatalog: DroneTechnicalCatalogClient = InMemoryDroneTechnicalCatalogClient(),
    private val mapPreferences: MapPreferences = InMemoryMapPreferences(),
    private val helpRepository: HelpManifestClient = InMemoryHelpManifestClient(),
    private val helpPreferences: HelpPreferences = InMemoryHelpPreferences(),
    private val localPilotStore: LocalPilotStore = InMemoryLocalPilotStore(),
    private val clock: Clock = Clock.systemUTC(),
    private val timelineZoneId: ZoneId = ZoneId.systemDefault(),
    private val trafficAwarenessPollingIntervalMillis: Long = TrafficAwarenessDefaults.PollingIntervalMillis,
    private val trafficAwarenessRadiusKm: Double = TrafficAwarenessDefaults.DefaultRadiusKm,
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
    private var trafficAwarenessJob: Job? = null
    private var lastLegalTimelineRequest: LegalTimelineRequestKey? = null
    private var catalogResolver: DroneTechnicalCatalogResolver = DroneTechnicalCatalogResolver.empty()
    private val trafficRelevanceEngine = TrafficRelevanceEngine()
    private val trafficAlertController = TrafficAlertController()

    init {
        loadTrafficAlertPreferences()
        if (loadHelpOnInit) loadHelpManifest()
        loadDroneCatalogAndFleet()
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
        val point = _uiState.value.selectedPoint ?: return
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
        _uiState.value = _uiState.value.copy(
            isOperationalContextRequested = true,
            isWeatherAnalysisEnabled = true,
            isLegalTimelineLoading = true,
            legalTimeline = null,
            legalTimelineError = null,
            isWeatherAnalysisLoading = true,
            weatherForecast = null,
            weatherAssessment = null,
            droneOperationalAssessment = null,
            flightOpportunityStatus = FlightOpportunityStatus.LOADING,
            flightOpportunityResult = null,
            isOperationalReportExpanded = false,
            weatherError = null
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
    }

    fun onWeatherAnalysisEnabledChanged(enabled: Boolean) {
        mapPreferences.setWeatherAnalysisEnabled(enabled)
        if (!enabled) {
            weatherJob?.cancel()
            _uiState.value = _uiState.value.copy(
                isOperationalContextRequested = false,
                isWeatherAnalysisEnabled = false,
                isWeatherAnalysisLoading = false,
                weatherForecast = null,
                weatherAssessment = null,
                droneOperationalAssessment = null,
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
        requestAnalysis(
            MapTapSelection(
                point = location.point,
                zone = null
            )
        )
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
            droneOperationalAssessment = null,
            flightOpportunityStatus = FlightOpportunityStatus.IDLE,
            flightOpportunityResult = null,
            isOperationalReportExpanded = false,
            weatherError = null,
            trafficAssessments = emptyMap(),
            selectedTrafficTarget = null
        )

        launchZoneVerdict(requestId, selection.point)
        if (_uiState.value.trafficAwareness.enabled) {
            DscLogger.debug(
                TrafficAwarenessLogTag,
                "selectedPoint changed pollRestart=true lat=${selection.point.lat.coarseTraffic()} lon=${selection.point.lon.coarseTraffic()}"
            )
            startTrafficAwarenessPolling(selection.point, clearSnapshot = true)
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
                ).withFlightOpportunity(
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
                weatherForecastRepository.getForecast(
                    latitude = point.lat,
                    longitude = point.lon
                )
            }

            if (!isCurrentSelection(requestId, point) || !_uiState.value.isOperationalContextRequested) {
                return@launch
            }

            result.onSuccess { forecast ->
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
                    droneOperationalAssessment = currentDroneAssessment(forecast, assessment),
                    weatherError = null
                ).withFlightOpportunity(
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
                    droneOperationalAssessment = null,
                    flightOpportunityStatus = FlightOpportunityStatus.ERROR,
                    flightOpportunityResult = null,
                    weatherError = "Meteo non disponibile"
                )
            }
        }
    }

    fun enableTrafficAwareness() {
        val point = _uiState.value.selectedPoint ?: _uiState.value.cameraBounds?.centerPoint() ?: run {
            DscLogger.warn(TrafficAwarenessLogTag, "cannot enable: selectedPoint missing")
            _uiState.value = _uiState.value.copy(
                trafficAwareness = _uiState.value.trafficAwareness.copy(
                    enabled = false,
                    loading = false,
                    error = "Seleziona un punto sulla mappa"
                ),
                mapStatusMessage = "Seleziona un punto sulla mappa"
            )
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
        _uiState.value = _uiState.value.copy(
            trafficAwareness = TrafficAwarenessState(enabled = false),
            trafficAssessments = emptyMap(),
            selectedTrafficTarget = null
        )
    }

    private fun startTrafficAwarenessPolling(
        point: MapPoint,
        clearSnapshot: Boolean = false
    ) {
        trafficAwarenessJob?.cancel()
        val currentTraffic = _uiState.value.trafficAwareness
        _uiState.value = _uiState.value.copy(
            trafficAwareness = currentTraffic.copy(
                enabled = true,
                loading = true,
                response = if (clearSnapshot) null else currentTraffic.response,
                error = null
            ),
            trafficAssessments = if (clearSnapshot) emptyMap() else _uiState.value.trafficAssessments,
            selectedTrafficTarget = if (clearSnapshot) null else _uiState.value.selectedTrafficTarget
        )
        DscLogger.debug(
            TrafficAwarenessLogTag,
            "polling started lat=${point.lat.coarseTraffic()} lon=${point.lon.coarseTraffic()} " +
                "radiusKm=${trafficAwarenessRadiusKm.coarseTraffic(0)} clearSnapshot=$clearSnapshot"
        )

        trafficAwarenessJob = scope.launch {
            try {
                while (_uiState.value.trafficAwareness.enabled && _uiState.value.selectedPoint == point) {
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

        if (!_uiState.value.trafficAwareness.enabled || _uiState.value.selectedPoint != point) {
            return
        }

        result.onSuccess { response ->
            DscLogger.trace(
                TrafficAwarenessLogTag,
                "state updated enabled=true targets=${response.traffic.targets.size}"
            )
            val nowMillis = clock.millis()
            val assessments = trafficRelevanceEngine.assessTrafficBatch(
                targets = response.traffic.targets,
                operationCenter = TrafficOperationCenter(point.lat, point.lon),
                nowMillis = nowMillis
            )
            assessments.forEach { (id, assessment) ->
                DscLogger.trace(
                    TrafficAwarenessLogTag,
                    "assessment id=$id relevance=${assessment.relevance} " +
                        "distance=${assessment.currentDistanceM?.toInt()} " +
                        "cpa=${assessment.cpaDistanceM?.toInt()} tcpa=${assessment.timeToCpaSec?.toInt()}"
                )
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
                    response = response,
                    error = null,
                    lastUpdatedAt = nowMillis
                ),
                trafficAssessments = assessments,
                selectedTrafficTarget = _uiState.value.selectedTrafficTarget?.let { selected ->
                    response.traffic.targets.firstOrNull { it.id == selected.id }
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

    private fun trafficAwarenessStopReason(point: MapPoint): String =
        when {
            !_uiState.value.trafficAwareness.enabled -> "disabled"
            _uiState.value.selectedPoint != point -> "center_changed"
            else -> "cancelled"
        }

    private fun loadTrafficAlertPreferences() {
        _uiState.value = _uiState.value.copy(
            trafficAlertSoundEnabled = mapPreferences.isTrafficAlertSoundEnabled(),
            trafficAlertVibrationEnabled = mapPreferences.isTrafficAlertVibrationEnabled()
        )
    }

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
        val result = flightOpportunityEngine.evaluate(
            FlightOpportunityInput(
                legalSegments = timeline.segments,
                weatherSlots = forecast.toFlightOpportunityWeatherSlots(
                    now = now,
                    selectedDrone = selectedDrone
                ),
                zoneId = forecast.timezone ?: timelineZoneId,
                now = now
            )
        )
        val resultWithDroneAdvice = result.withDroneRecommendation(
            timeline = timeline,
            forecast = forecast,
            now = now,
            zoneId = forecast.timezone ?: timelineZoneId,
            selectedDrone = selectedDrone
        )
        return copy(
            flightOpportunityStatus = resultWithDroneAdvice.status,
            flightOpportunityResult = resultWithDroneAdvice
        )
    }

    private fun FlightOpportunityResult.withDroneRecommendation(
        timeline: LegalTimelineResponse,
        forecast: WeatherForecast,
        now: Instant,
        zoneId: ZoneId,
        selectedDrone: LocalDrone?
    ): FlightOpportunityResult {
        val drones = _uiState.value.droneFleet
            .distinctBy { it.id }
            .takeIf { it.size > 1 }
            ?: return this
        val compared = drones.map { drone ->
            val capabilities = catalogResolver.capabilitiesFor(drone).first
            val evaluated = flightOpportunityEngine.evaluate(
                FlightOpportunityInput(
                    legalSegments = timeline.segments,
                    weatherSlots = forecast.toFlightOpportunityWeatherSlots(
                        now = now,
                        selectedDrone = drone
                    ),
                    zoneId = zoneId,
                    now = now
                )
            )
            val best = evaluated.bestOpportunity
            FlightOpportunityDroneCandidate(
                droneId = drone.id,
                displayName = drone.displayName,
                opportunityScore = best?.opportunityScore,
                opportunityLevel = best?.opportunityLevel,
                droneScore = best?.droneScore,
                droneLevel = best?.droneLevel,
                windResistanceMs = capabilities.maxWindResistanceMs,
                bestFrom = best?.from,
                bestTo = best?.to
            )
        }.sortedWith(
            compareByDescending<FlightOpportunityDroneCandidate> { it.candidateRank() }
                .thenBy { it.bestFrom ?: Instant.MAX }
        )
        val recommended = compared.firstOrNull { it.isUsableRecommendation() } ?: return this
        val selectedCandidate = selectedDrone?.let { drone ->
            compared.firstOrNull { it.droneId == drone.id }
        }
        val reason = when {
            compared.count { it.isUsableRecommendation() } == 1 -> FlightOpportunityDroneRecommendationReason.ONLY_USABLE
            selectedCandidate != null &&
                recommended.bestFrom != null &&
                selectedCandidate.bestFrom != null &&
                recommended.bestFrom != selectedCandidate.bestFrom -> FlightOpportunityDroneRecommendationReason.BETTER_WINDOW
            recommended.windResistanceMs != null &&
                compared.drop(1).any { (recommended.windResistanceMs ?: 0.0) > (it.windResistanceMs ?: 0.0) } ->
                FlightOpportunityDroneRecommendationReason.WIND_MARGIN
            else -> FlightOpportunityDroneRecommendationReason.NO_CLEAR_ADVANTAGE
        }
        return copy(
            droneRecommendation = FlightOpportunityDroneRecommendation(
                recommended = recommended,
                compared = compared,
                reason = reason
            )
        )
    }

    private fun FlightOpportunityDroneCandidate.isUsableRecommendation(): Boolean =
        opportunityScore != null &&
            opportunityLevel != FlightOpportunityLevel.POOR &&
            bestFrom != null &&
            bestTo != null

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
        return base + drone + wind + levelBonus
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
        _uiState.value = _uiState.value.copy(
            mapStatusMessage = CachedMapDataMessage
        )
        scope.launch {
            delay(StatusMessageMillis)
            if (_uiState.value.mapStatusMessage == CachedMapDataMessage) {
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
            selectedPoint = if (trafficEnabled) _uiState.value.selectedPoint else null,
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
            droneOperationalAssessment = null,
            weatherError = null,
            trafficAssessments = if (trafficEnabled) _uiState.value.trafficAssessments else emptyMap(),
            selectedTrafficTarget = _uiState.value.selectedTrafficTarget
        )
    }

    fun onCameraIdle(bounds: CameraBounds) {
        _uiState.value = _uiState.value.copy(cameraBounds = bounds)
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
        _uiState.value = _uiState.value.copy(
            isUserLocationEnabled = true,
            shouldCenterOnUserLocation = true,
            isLocationControlSheetVisible = false,
            locationPermissionSheetVisible = false,
            locationStatusMessage = null
        )
    }

    fun onLocationDisabled() {
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
    }

    fun onUserLocationCentered() {
        _uiState.value = _uiState.value.copy(shouldCenterOnUserLocation = false)
    }

    fun onLocationPermissionDenied(permanently: Boolean) {
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
        _uiState.value = _uiState.value.copy(
            isUserLocationEnabled = false,
            userLocation = null,
            shouldCenterOnUserLocation = false,
            isLocationControlSheetVisible = false,
            locationStatusMessage = "Permesso posizione non piu disponibile."
        )
    }

    fun onLocationProviderUnavailable() {
        _uiState.value = _uiState.value.copy(
            locationStatusMessage = "Posizione non disponibile: controlla che i servizi di localizzazione siano attivi."
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
        const val StatusMessageMillis = 8_000L
        const val MaxHelpTourSteps = 7
    }

    override fun onCleared() {
        trafficAwarenessJob?.cancel()
        super.onCleared()
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
