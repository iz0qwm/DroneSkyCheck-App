package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.ZoneCheckV3Response
import it.droneskycheck.app.data.LegalTimelineResponse
import it.droneskycheck.app.data.weather.WeatherAssessment
import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.map.DscLayerCategory

data class MapUiState(
    val selectedZone: DemoZone? = null,
    val selectedPoint: MapPoint? = null,
    val isZoneSheetVisible: Boolean = false,
    val userLocation: UserLocation? = null,
    val isUserLocationEnabled: Boolean = false,
    val shouldCenterOnUserLocation: Boolean = false,
    val isLocationControlSheetVisible: Boolean = false,
    val locationPermissionSheetVisible: Boolean = false,
    val locationStatusMessage: String? = null,
    val isVerdictLoading: Boolean = false,
    val verdict: ZoneCheckV3Response? = null,
    val verdictError: String? = null,
    val isLegalTimelineLoading: Boolean = false,
    val legalTimeline: LegalTimelineResponse? = null,
    val legalTimelineError: String? = null,
    val isOperationalContextRequested: Boolean = false,
    val isWeatherAnalysisEnabled: Boolean = false,
    val isWeatherAnalysisLoading: Boolean = false,
    val weatherForecast: WeatherForecast? = null,
    val weatherAssessment: WeatherAssessment? = null,
    val weatherError: String? = null,
    val mapStatusMessage: String? = null,
    val cameraBounds: CameraBounds? = null,
    val layerVisibility: Map<DscLayerCategory, Boolean> = DscLayerCategory.defaultVisibility,
    val isLayerSheetVisible: Boolean = false
)

data class MapTapSelection(
    val point: MapPoint,
    val zone: DemoZone?,
    val zones: List<DemoZone> = zone?.let(::listOf).orEmpty()
)

data class MapPoint(
    val lat: Double,
    val lon: Double
)

data class UserLocation(
    val point: MapPoint,
    val accuracyMeters: Float?,
    val isPrecise: Boolean
)

data class DemoZone(
    val id: String,
    val name: String,
    val type: String,
    val restriction: String?,
    val lowerLimit: Int,
    val upperLimit: Int?,
    val description: String?
) {
    val status: DemoZoneStatus = demoStatusForLowerLimit(lowerLimit)
}

data class CameraBounds(
    val zoom: Double,
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
) {
    val bbox: String
        get() = "$south,$west,$north,$east"
}
