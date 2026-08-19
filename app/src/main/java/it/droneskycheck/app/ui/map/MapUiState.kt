package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.ZoneCheckV3Response
import it.droneskycheck.app.data.LegalTimelineResponse
import it.droneskycheck.app.data.LocalDrone
import it.droneskycheck.app.data.UasDatasetUpdates
import it.droneskycheck.app.data.drone.DroneCatalogMatchResult
import it.droneskycheck.app.data.drone.DroneOperationalAssessment
import it.droneskycheck.app.data.flight.FlightLightPreference
import it.droneskycheck.app.data.flight.FlightOpportunityMode
import it.droneskycheck.app.data.flight.FlightOpportunityResult
import it.droneskycheck.app.data.flight.FlightOpportunityStatus
import it.droneskycheck.app.data.filterableTypes
import it.droneskycheck.app.data.help.ActiveHelpOnboarding
import it.droneskycheck.app.data.help.HelpManifest
import it.droneskycheck.app.data.traffic.TrafficAwarenessState
import it.droneskycheck.app.data.traffic.TrafficAssessment
import it.droneskycheck.app.data.traffic.TrafficFeedType
import it.droneskycheck.app.data.traffic.TrafficTarget
import it.droneskycheck.app.data.weather.WeatherAssessment
import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.data.weather.NearbyMetar
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
    val isLargeTextEnabled: Boolean = false,
    val isMapDarkeningEnabled: Boolean = false,
    val isEnhancedZoneOutlinesEnabled: Boolean = false,
    val isWeatherAnalysisLoading: Boolean = false,
    val weatherForecast: WeatherForecast? = null,
    val weatherAssessment: WeatherAssessment? = null,
    val nearbyMetar: NearbyMetar? = null,
    val weatherError: String? = null,
    val trafficAwareness: TrafficAwarenessState = TrafficAwarenessState(),
    val trafficAwarenessCenter: MapPoint? = null,
    val trafficAwarenessPositionLocked: Boolean = false,
    val trafficAssessments: Map<String, TrafficAssessment> = emptyMap(),
    val trafficVisualAssessments: Map<String, TrafficAssessment> = emptyMap(),
    val selectedTrafficTarget: TrafficTarget? = null,
    val trafficAlertSoundEnabled: Boolean = true,
    val trafficAlertVibrationEnabled: Boolean = true,
    val highAltitudeTrafficAlertEnabled: Boolean = false,
    val trafficFeedFilters: Map<TrafficFeedType, Boolean> = TrafficFeedType.filterableTypes.associateWith { true },
    val isTrafficAlertSettingsSheetVisible: Boolean = false,
    val droneFleet: List<LocalDrone> = emptyList(),
    val selectedDrone: LocalDrone? = null,
    val selectedDroneCatalogMatch: DroneCatalogMatchResult? = null,
    val droneOperationalAssessment: DroneOperationalAssessment? = null,
    val selectedLightPreference: FlightLightPreference = FlightLightPreference.DAYLIGHT,
    val flightOpportunityMode: FlightOpportunityMode = FlightOpportunityMode.OPEN,
    val flightOpportunityStatus: FlightOpportunityStatus = FlightOpportunityStatus.IDLE,
    val flightOpportunityResult: FlightOpportunityResult? = null,
    val isOperationalReportExpanded: Boolean = false,
    val mapStatusMessage: String? = null,
    val isAppInfoSheetVisible: Boolean = false,
    val uasDatasetUpdates: UasDatasetUpdates? = null,
    val isUasDatasetRefreshing: Boolean = false,
    val cameraBounds: CameraBounds? = null,
    val layerVisibility: Map<DscLayerCategory, Boolean> = DscLayerCategory.defaultVisibility,
    val isLayerSheetVisible: Boolean = false,
    val helpManifest: HelpManifest = HelpManifest.empty(),
    val activeHelpOnboarding: ActiveHelpOnboarding? = null,
    val helpTourOverlayRevision: Int = 0,
    val isHelpManifestRefreshing: Boolean = false,
    val helpManifestRefreshMessage: String? = null
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
