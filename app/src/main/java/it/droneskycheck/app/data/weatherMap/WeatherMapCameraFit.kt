package it.droneskycheck.app.data.weatherMap

data class WeatherMapCameraFit(
    val id: Long,
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
    val targetLat: Double,
    val targetLon: Double
)

fun WeatherMapForecast.cameraFitFor(id: Long, requestedLat: Double, requestedLon: Double): WeatherMapCameraFit? {
    if (!requestedLat.isFinite() || !requestedLon.isFinite() || nodes.isEmpty()) return null
    val maxLatDelta = nodes.maxOf { kotlin.math.abs(it.lat - requestedLat) }
    val maxLonDelta = nodes.maxOf { kotlin.math.abs(it.lon - requestedLon) }
    val latDelta = maxLatDelta.coerceAtLeast(MinWeatherMapDeltaDegrees) * WeatherMapCameraMargin
    val lonDelta = maxLonDelta.coerceAtLeast(MinWeatherMapDeltaDegrees) * WeatherMapCameraMargin
    return WeatherMapCameraFit(
        id = id,
        north = (requestedLat + latDelta).coerceAtMost(90.0),
        south = (requestedLat - latDelta).coerceAtLeast(-90.0),
        east = (requestedLon + lonDelta).coerceAtMost(180.0),
        west = (requestedLon - lonDelta).coerceAtLeast(-180.0),
        targetLat = requestedLat,
        targetLon = requestedLon
    )
}

fun WeatherMapCameraFit.containsAll(nodes: List<WeatherMapNode>): Boolean =
    nodes.all { it.lat in south..north && it.lon in west..east }

private const val WeatherMapCameraMargin = 1.18
private const val MinWeatherMapDeltaDegrees = 0.02
