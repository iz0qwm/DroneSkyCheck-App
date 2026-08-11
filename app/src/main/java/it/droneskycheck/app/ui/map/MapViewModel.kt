package it.droneskycheck.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.droneskycheck.app.data.InMemoryMapPreferences
import it.droneskycheck.app.data.LegalTimelineClient
import it.droneskycheck.app.data.LegalTimelineRepository
import it.droneskycheck.app.data.DscLogger
import it.droneskycheck.app.data.LegalTimelineRepositoryError
import it.droneskycheck.app.data.MapPreferences
import it.droneskycheck.app.data.ZoneCheckV3Client
import it.droneskycheck.app.data.ZoneCheckV3Repository
import it.droneskycheck.app.data.weather.WeatherAssessmentEngine
import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.data.weather.WeatherForecastClient
import it.droneskycheck.app.data.weather.WeatherForecastRepository
import it.droneskycheck.app.data.weather.toWeatherMetrics
import it.droneskycheck.app.map.DscLayerCategory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel(
    private val zoneCheckRepository: ZoneCheckV3Client = ZoneCheckV3Repository(),
    private val legalTimelineRepository: LegalTimelineClient = LegalTimelineRepository(),
    private val weatherForecastRepository: WeatherForecastClient = WeatherForecastRepository(),
    private val weatherAssessmentEngine: WeatherAssessmentEngine = WeatherAssessmentEngine(),
    private val mapPreferences: MapPreferences = InMemoryMapPreferences(),
    private val clock: Clock = Clock.systemUTC(),
    externalScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = externalScope ?: viewModelScope
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var selectionRequestId = 0L
    private var verdictJob: Job? = null
    private var legalTimelineJob: Job? = null
    private var weatherJob: Job? = null
    private var lastLegalTimelineRequest: LegalTimelineRequestKey? = null

    fun onMapTapped(selection: MapTapSelection) {
        requestAnalysis(selection)
    }

    fun onZoneCheckRetryRequested() {
        val point = _uiState.value.selectedPoint ?: return
        requestAnalysis(
            MapTapSelection(
                point = point,
                zone = _uiState.value.selectedZone
            ),
            forceTimeline = true
        )
    }

    fun onOperationalContextRequested() {
        val point = _uiState.value.selectedPoint ?: return
        val requestId = selectionRequestId
        val windowStart = clock.instant()
        val timelineRequest = LegalTimelineRequestKey(
            point = point,
            from = windowStart,
            to = windowStart.plus(StandardTimelineWindow)
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

    private fun requestAnalysis(
        selection: MapTapSelection,
        forceTimeline: Boolean = false
    ) {
        selectionRequestId += 1
        val requestId = selectionRequestId
        val windowStart = clock.instant()
        val windowEnd = windowStart.plus(StandardTimelineWindow)
        val timelineRequest = LegalTimelineRequestKey(
            point = selection.point,
            from = windowStart,
            to = windowEnd
        )
        val shouldRequestTimeline = forceTimeline || timelineRequest != lastLegalTimelineRequest
        if (forceTimeline) lastLegalTimelineRequest = timelineRequest

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
            weatherError = null
        )

        launchZoneVerdict(requestId, selection.point)
        if (forceTimeline && shouldRequestTimeline) {
            _uiState.value = _uiState.value.copy(
                isOperationalContextRequested = true,
                isWeatherAnalysisEnabled = true,
                isLegalTimelineLoading = true,
                isWeatherAnalysisLoading = true
            )
            launchLegalTimeline(
                requestId = requestId,
                request = timelineRequest
            )
            launchWeatherAnalysis(
                requestId = requestId,
                point = selection.point
            )
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
                    legalTimelineError = "Previsione temporale non disponibile"
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
                    weatherError = null
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
                    weatherError = "Meteo non disponibile"
                )
            }
        }
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
        _uiState.value = _uiState.value.copy(
            selectedZone = null,
            selectedPoint = null,
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
            weatherError = null
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
        val StandardTimelineWindow: Duration = Duration.ofHours(24)
    }
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
