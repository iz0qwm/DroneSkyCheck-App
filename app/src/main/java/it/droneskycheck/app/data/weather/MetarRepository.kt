package it.droneskycheck.app.data.weather

import it.droneskycheck.app.data.AirportPoint
import it.droneskycheck.app.data.AirportRepository
import it.droneskycheck.app.data.DscLogger
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

interface NearbyMetarClient {
    suspend fun getNearbyMetar(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = NearbyMetarDefaults.RadiusKm
    ): Result<NearbyMetar?>
}

object NearbyMetarDefaults {
    const val RadiusKm = 20.0
}

private const val NearbyMetarUserAgent = "DroneSkyCheckAndroid/3.1"

data class NearbyMetar(
    val icao: String,
    val distanceKm: Double,
    val rawText: String?,
    val observedAt: String?,
    val temperatureC: Double?,
    val dewpointC: Double?,
    val windDirectionDeg: Int?,
    val windSpeedKt: Int?,
    val windGustKt: Int?,
    val visibilityMeters: Double?,
    val flightCategory: String?
)

class NearbyMetarRepository(
    private val endpointUrl: String = "https://aviationweather.gov/api/data/metar",
    private val airportRepository: AirportRepository = AirportRepository(emptyList()),
    private val httpClient: NearbyMetarHttpClient = UrlConnectionNearbyMetarHttpClient()
) : NearbyMetarClient {
    override suspend fun getNearbyMetar(
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): Result<NearbyMetar?> =
        runCatching {
            validate(latitude, longitude, radiusKm)
            val nearestAirport = airportRepository
                .findNearestAirport(AirportPoint(latitude, longitude))
                ?.takeIf { it.distanceKm <= radiusKm }
            val url = if (nearestAirport != null) {
                "$endpointUrl?format=json&ids=${encode(nearestAirport.airport.icao)}"
            } else {
                val bbox = metarBoundingBox(latitude, longitude, radiusKm)
                "$endpointUrl?format=json&bbox=${encode(bbox)}&hours=2"
            }
            val response = httpClient.get(url = url, timeoutMillis = TimeoutMillis)
            if (response.statusCode == HttpURLConnection.HTTP_NO_CONTENT) {
                return@runCatching null
            }
            if (response.statusCode !in 200..299) {
                throw NearbyMetarRepositoryError.HttpError(response.statusCode)
            }
            if (response.body.isBlank()) {
                return@runCatching null
            }
            parseMetars(JSONArray(response.body), latitude, longitude)
                .filter { it.distanceKm <= radiusKm }
                .minByOrNull { it.distanceKm }
        }.onFailure { error ->
            DscLogger.warn(LogTag, "nearby METAR unavailable reason=${error.javaClass.simpleName}", error)
        }

    private fun validate(latitude: Double, longitude: Double, radiusKm: Double) {
        if (!latitude.isFinite() || !longitude.isFinite() || !radiusKm.isFinite()) {
            throw NearbyMetarRepositoryError.InvalidCoordinates
        }
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0 || radiusKm <= 0.0) {
            throw NearbyMetarRepositoryError.InvalidCoordinates
        }
    }

    private fun parseMetars(json: JSONArray, latitude: Double, longitude: Double): List<NearbyMetar> =
        try {
            (0 until json.length()).mapNotNull { index ->
                val item = json.optJSONObject(index) ?: return@mapNotNull null
                val stationLat = item.optDoubleOrNull("lat") ?: return@mapNotNull null
                val stationLon = item.optDoubleOrNull("lon") ?: return@mapNotNull null
                val distanceKm = distanceKm(latitude, longitude, stationLat, stationLon)
                NearbyMetar(
                    icao = item.optStringOrNull("icaoId") ?: return@mapNotNull null,
                    distanceKm = distanceKm,
                    rawText = item.optStringOrNull("rawOb"),
                    observedAt = item.optStringOrNull("reportTime") ?: item.optStringOrNull("obsTime"),
                    temperatureC = item.optDoubleOrNull("temp"),
                    dewpointC = item.optDoubleOrNull("dewp"),
                    windDirectionDeg = item.optIntOrNull("wdir"),
                    windSpeedKt = item.optIntOrNull("wspd"),
                    windGustKt = item.optIntOrNull("wgst"),
                    visibilityMeters = item.optVisibilityMeters(),
                    flightCategory = item.optStringOrNull("fltCat")
                )
            }
        } catch (err: JSONException) {
            throw NearbyMetarRepositoryError.InvalidJson(err.message)
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val TimeoutMillis = 6_000
        const val LogTag = "DscMetar"
    }
}

interface NearbyMetarHttpClient {
    fun get(url: String, timeoutMillis: Int): NearbyMetarHttpResponse
}

data class NearbyMetarHttpResponse(
    val statusCode: Int,
    val body: String
)

class UrlConnectionNearbyMetarHttpClient : NearbyMetarHttpClient {
    override fun get(url: String, timeoutMillis: Int): NearbyMetarHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            useCaches = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", NearbyMetarUserAgent)
        }
        return try {
            val statusCode = connection.responseCode
            val body = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            NearbyMetarHttpResponse(
                statusCode = statusCode,
                body = body?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            )
        } catch (err: IOException) {
            throw NearbyMetarRepositoryError.Network(err.message)
        } finally {
            connection.disconnect()
        }
    }
}

sealed class NearbyMetarRepositoryError(message: String?) : Exception(message) {
    object InvalidCoordinates : NearbyMetarRepositoryError("Invalid coordinates")
    data class HttpError(val statusCode: Int) : NearbyMetarRepositoryError("METAR HTTP $statusCode")
    data class Network(override val message: String?) : NearbyMetarRepositoryError(message ?: "METAR network error")
    data class InvalidJson(override val message: String?) : NearbyMetarRepositoryError(message ?: "Invalid METAR JSON")
}

private fun metarBoundingBox(lat: Double, lon: Double, radiusKm: Double): String {
    val latDelta = radiusKm / 111.32
    val lonDelta = radiusKm / (111.32 * cos(lat.toRadians()).coerceAtLeast(0.2))
    return listOf(
        lat - latDelta,
        lon - lonDelta,
        lat + latDelta,
        lon + lonDelta
    ).joinToString(",")
}

private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = (lat2 - lat1).toRadians()
    val dLon = (lon2 - lon1).toRadians()
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1.toRadians()) * cos(lat2.toRadians()) *
        sin(dLon / 2) * sin(dLon / 2)
    return 2.0 * EARTH_RADIUS_KM * atan2(sqrt(a), sqrt(1.0 - a))
}

private fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return opt(name)?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
}

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Number -> optDouble(name)
        else -> optString(name).toDoubleOrNull()
    }?.takeIf { it.isFinite() }

private fun JSONObject.optIntOrNull(name: String): Int? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Number -> optInt(name)
        else -> optString(name).toIntOrNull()
    }

private fun JSONObject.optVisibilityMeters(): Double? =
    (optDoubleOrNull("visib")
        ?: optStringOrNull("visib")
            ?.removeSuffix("+")
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() })
        ?.times(1_609.344)

private fun Double.toRadians(): Double = this * PI / 180.0

private const val EARTH_RADIUS_KM = 6371.0
