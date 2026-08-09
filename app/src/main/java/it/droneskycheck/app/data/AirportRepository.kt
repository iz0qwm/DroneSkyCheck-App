package it.droneskycheck.app.data

import android.content.Context
import org.json.JSONArray
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Airport(
    val icao: String,
    val name: String,
    val city: String,
    val lat: Double,
    val lon: Double
)

data class AirportPoint(
    val lat: Double,
    val lon: Double
)

data class NearestAirport(
    val airport: Airport,
    val distanceKm: Double,
    val distanceNm: Double
)

class AirportRepository(
    private val jsonLoader: () -> String
) {
    constructor(context: Context) : this({
        context.assets.open(AirportsAssetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
    })

    constructor(airports: List<Airport>) : this({
        JSONArray().also { array ->
            airports.forEach { airport ->
                array.put(
                    org.json.JSONObject()
                        .put("icao", airport.icao)
                        .put("name", airport.name)
                        .put("city", airport.city)
                        .put("lat", airport.lat)
                        .put("lon", airport.lon)
                )
            }
        }.toString()
    })

    private var cachedAirports: List<Airport>? = null

    fun loadAirports(): List<Airport> =
        cachedAirports ?: parseAirports(jsonLoader()).also { cachedAirports = it }

    fun findNearestAirport(point: AirportPoint?): NearestAirport? {
        if (point == null || !point.hasValidCoordinates()) return null

        return loadAirports()
            .mapNotNull { airport ->
                val airportPoint = AirportPoint(airport.lat, airport.lon)
                val distanceKm = calculateDistanceKm(point, airportPoint)
                if (!distanceKm.isFinite()) {
                    null
                } else {
                    NearestAirport(
                        airport = airport,
                        distanceKm = distanceKm,
                        distanceNm = calculateDistanceNm(distanceKm)
                    )
                }
            }
            .minByOrNull { it.distanceKm }
    }

    fun findNearestAirport(
        takeoff: AirportPoint?,
        area: List<AirportPoint>
    ): NearestAirport? =
        findNearestAirport(takeoff ?: calculateAreaCenter(area))

    fun calculateDistanceKm(pointA: AirportPoint, pointB: AirportPoint): Double {
        if (!pointA.hasValidCoordinates() || !pointB.hasValidCoordinates()) return Double.POSITIVE_INFINITY

        val dLat = (pointB.lat - pointA.lat).toRadians()
        val dLon = (pointB.lon - pointA.lon).toRadians()
        val lat1 = pointA.lat.toRadians()
        val lat2 = pointB.lat.toRadians()

        val aa = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(aa), sqrt(1 - aa))
        return EarthRadiusKm * c
    }

    fun calculateDistanceNm(distanceKm: Double): Double =
        distanceKm * KmToNmLegacy

    fun calculateAreaCenter(area: List<AirportPoint>): AirportPoint? {
        val validPoints = area.filter { it.hasValidCoordinates() }
        if (validPoints.isEmpty()) return null

        return AirportPoint(
            lat = validPoints.sumOf { it.lat } / validPoints.size,
            lon = validPoints.sumOf { it.lon } / validPoints.size
        )
    }

    companion object {
        const val AirportsAssetName = "icao-it.json"
        const val KmToNmLegacy = 0.539957
        private const val EarthRadiusKm = 6371.0

        fun parseAirports(json: String): List<Airport> {
            val array = JSONArray(json.ifBlank { "[]" })
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                Airport(
                    icao = item.optString("icao"),
                    name = item.optString("name"),
                    city = item.optString("city"),
                    lat = item.optDouble("lat", Double.NaN),
                    lon = item.optDouble("lon", Double.NaN)
                ).takeIf { it.icao.isNotBlank() && it.hasValidCoordinates() }
            }
        }
    }
}

private fun Airport.hasValidCoordinates(): Boolean =
    lat.isFinite() && lon.isFinite()

private fun AirportPoint.hasValidCoordinates(): Boolean =
    lat.isFinite() && lon.isFinite()

private fun Double.toRadians(): Double =
    this * Math.PI / 180
