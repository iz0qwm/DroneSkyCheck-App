package it.droneskycheck.app.ui.map

data class MapUiState(
    val selectedZone: DemoZone? = null,
    val cameraBounds: CameraBounds? = null
)

data class DemoZone(
    val id: String,
    val name: String,
    val type: String,
    val restriction: String?,
    val lowerLimit: Int,
    val upperLimit: Int?
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
