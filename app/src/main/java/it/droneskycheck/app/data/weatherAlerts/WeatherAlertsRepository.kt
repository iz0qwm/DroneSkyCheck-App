package it.droneskycheck.app.data.weatherAlerts

import it.droneskycheck.app.data.DscLogger
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONException

sealed interface WeatherAlertLoadResult {
    data class Available(
        val response: WeatherAlertResponse,
        val fetchedAt: Instant,
        val stale: Boolean
    ) : WeatherAlertLoadResult

    data object Unavailable : WeatherAlertLoadResult
}

interface WeatherAlertsClient {
    suspend fun getAlerts(lat: Double, lon: Double): WeatherAlertLoadResult
    suspend fun getStatus(): Result<WeatherStatus>
}

class WeatherAlertsRepository(
    private val alertsEndpoint: String = AlertsEndpoint,
    private val statusEndpoint: String = StatusEndpoint,
    private val clock: Clock = Clock.systemUTC(),
    private val timeoutMillis: Int = TimeoutMillis
) : WeatherAlertsClient {
    private var cached: CachedWeatherAlert? = null

    override suspend fun getAlerts(lat: Double, lon: Double): WeatherAlertLoadResult = withContext(Dispatchers.IO) {
        require(lat.isFinite() && lat in -90.0..90.0)
        require(lon.isFinite() && lon in -180.0..180.0)
        val key = WeatherCoordinateKey.from(lat, lon)
        try {
            DscLogger.debug(
                LogTag,
                "weatherAlerts request lat=$lat lon=$lon endpoint=$alertsEndpoint"
            )
            val json = getJson(
                "$alertsEndpoint?lat=${lat.encoded()}&lon=${lon.encoded()}"
            )
            ensureActive()
            val response = parseWeatherAlertResponse(json)
            val nationalToday = response.vigilanceNational?.periods?.get("TODAY")
            DscLogger.debug(
                LogTag,
                "weatherAlerts success lat=$lat lon=$lon " +
                    "nationalApplies=${nationalToday?.appliesToPoint} " +
                    "matched=${nationalToday?.matchedRegions.orEmpty()} " +
                    "onset=${nationalToday?.onset} expires=${nationalToday?.expires}"
            )
            val fetchedAt = clock.instant()
            cached = CachedWeatherAlert(key, fetchedAt, response)
            WeatherAlertLoadResult.Available(response, fetchedAt, stale = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            val fallback = cached
            val usableFallback = fallback?.takeIf {
                it.key == key && Duration.between(it.fetchedAt, clock.instant()) <= StaleWindow
            }
            DscLogger.warn(
                LogTag,
                "weatherAlerts failed lat=$lat lon=$lon " +
                    "reason=${error::class.simpleName} fallback=${usableFallback != null}",
                error
            )
            if (usableFallback != null) {
                WeatherAlertLoadResult.Available(
                    usableFallback.response,
                    usableFallback.fetchedAt,
                    stale = true
                )
            } else {
                WeatherAlertLoadResult.Unavailable
            }
        }
    }

    override suspend fun getStatus(): Result<WeatherStatus> = withContext(Dispatchers.IO) {
        try {
            val json = getJson(statusEndpoint)
            ensureActive()
            Result.success(parseWeatherStatus(json))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun getJson(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) connection.disconnect()
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw WeatherAlertsRepositoryError.HttpError(status)
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (error: SocketTimeoutException) {
            throw WeatherAlertsRepositoryError.Timeout(error)
        } catch (error: JSONException) {
            throw WeatherAlertsRepositoryError.InvalidJson(error)
        } catch (error: IOException) {
            throw WeatherAlertsRepositoryError.Network(error)
        } finally {
            cancellationHandle.dispose()
            connection.disconnect()
        }
    }

    private fun Double.encoded(): String = URLEncoder.encode(toString(), Charsets.UTF_8.name())

    private data class CachedWeatherAlert(
        val key: WeatherCoordinateKey,
        val fetchedAt: Instant,
        val response: WeatherAlertResponse
    )

    private data class WeatherCoordinateKey(val lat: Long, val lon: Long) {
        companion object {
            fun from(lat: Double, lon: Double): WeatherCoordinateKey = WeatherCoordinateKey(
                lat = kotlin.math.round(lat * CoordinateBucketFactor).toLong(),
                lon = kotlin.math.round(lon * CoordinateBucketFactor).toLong()
            )
        }
    }

    private companion object {
        const val LogTag = "DscWeatherAlerts"
        const val AlertsEndpoint = "https://solarmonitor.kwos.org/api/weather-alerts"
        const val StatusEndpoint = "https://solarmonitor.kwos.org/api/weather-status"
        const val TimeoutMillis = 8_000
        const val CoordinateBucketFactor = 1_000.0
        val StaleWindow: Duration = Duration.ofMinutes(30)
    }
}

sealed class WeatherAlertsRepositoryError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class HttpError(val statusCode: Int) : WeatherAlertsRepositoryError("Weather alerts HTTP $statusCode")
    class Timeout(cause: Throwable) : WeatherAlertsRepositoryError("Weather alerts timeout", cause)
    class Network(cause: Throwable) : WeatherAlertsRepositoryError("Weather alerts network failure", cause)
    class InvalidJson(cause: Throwable) : WeatherAlertsRepositoryError("Weather alerts invalid JSON", cause)
}
