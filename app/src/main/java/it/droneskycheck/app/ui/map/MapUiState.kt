package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.ZoneCheckV3Response

data class MapUiState(
    val selectedZone: DemoZone? = null,
    val selectedPoint: MapPoint? = null,
    val isVerdictLoading: Boolean = false,
    val verdict: ZoneCheckV3Response? = null,
    val verdictError: String? = null,
    val cameraBounds: CameraBounds? = null
)

data class MapTapSelection(
    val point: MapPoint,
    val zone: DemoZone?
)

data class MapPoint(
    val lat: Double,
    val lon: Double
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
