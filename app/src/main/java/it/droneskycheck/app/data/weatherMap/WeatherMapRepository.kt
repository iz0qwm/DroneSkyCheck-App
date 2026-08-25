package it.droneskycheck.app.data.weatherMap

import it.droneskycheck.app.data.DscApiConfig
import it.droneskycheck.app.data.DscLogger
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToLong
import org.json.JSONException
import org.json.JSONObject

interface WeatherMapClient {
    suspend fun getWeatherMap(
        latitude: Double,
        longitude: Double,
        mode: String = WeatherMapDefaults.ModeOperational
    ): Result<WeatherMapForecast>
}

class WeatherMapRepository(
    private val endpointUrl: String = DscApiConfig.WeatherMapUrl,
    private val apiKey: String = DscApiConfig.ApiKey,
    private val httpClient: WeatherMapHttpClient = UrlConnectionWeatherMapHttpClient()
) : WeatherMapClient {
    private val memoryCache = object : LinkedHashMap<WeatherMapCacheKey, WeatherMapForecast>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<WeatherMapCacheKey, WeatherMapForecast>?): Boolean =
            size > WeatherMapDefaults.CacheMaxEntries
    }

    override suspend fun getWeatherMap(
        latitude: Double,
        longitude: Double,
        mode: String
    ): Result<WeatherMapForecast> =
        runCatching {
            validateQuery(latitude, longitude, mode)
            val cacheKey = WeatherMapCacheKey.from(latitude, longitude, mode)
            synchronized(memoryCache) {
                memoryCache[cacheKey]?.let { return@runCatching it }
            }

            val url = "$endpointUrl?lat=${encode(latitude)}&lon=${encode(longitude)}&mode=${encode(mode)}"
            DscLogger.debug(
                WeatherMapLogTag,
                "appWeatherMap request method=GET url=$url headers=Accept,x-api-key " +
                    "lat=$latitude lon=$longitude mode=$mode timeoutMillis=$TimeoutMillis"
            )
            val response = httpClient.get(
                url = url,
                headers = mapOf(
                    "Accept" to "application/json",
                    "x-api-key" to apiKey
                ),
                timeoutMillis = TimeoutMillis
            )

            if (response.statusCode !in 200..299) {
                DscLogger.warn(
                    WeatherMapLogTag,
                    "appWeatherMap HTTP failure category=${response.statusCode.toWeatherMapHttpCategory()} " +
                        "status=${response.statusCode} body=${response.body.toBodySnippet()}"
                )
                throw WeatherMapRepositoryError.HttpError(
                    statusCode = response.statusCode,
                    message = response.statusCode.toWeatherMapHttpMessage()
                )
            }

            val forecast = try {
                parseWeatherMapResponse(JSONObject(response.body))
            } catch (err: JSONException) {
                throw WeatherMapRepositoryError.InvalidJson(err.message)
            } catch (err: WeatherMapMappingError) {
                throw WeatherMapRepositoryError.InvalidSchema(err.message)
            }
            synchronized(memoryCache) {
                memoryCache[cacheKey] = forecast
            }
            forecast
        }.onFailure { error ->
            DscLogger.warn(
                WeatherMapLogTag,
                "appWeatherMap failed reason=${error.toWeatherMapDiagnosticReason()} " +
                    "lat=$latitude lon=$longitude mode=$mode endpoint=$endpointUrl",
                error
            )
        }

    private fun validateQuery(latitude: Double, longitude: Double, mode: String) {
        if (!latitude.isFinite() || !longitude.isFinite()) {
            throw WeatherMapRepositoryError.InvalidQuery
        }
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
            throw WeatherMapRepositoryError.InvalidQuery
        }
        if (!mode.equals(WeatherMapDefaults.ModeOperational, ignoreCase = true)) {
            throw WeatherMapRepositoryError.InvalidQuery
        }
    }

    private fun encode(value: Double): String =
        URLEncoder.encode(value.toString(), Charsets.UTF_8.name())

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private data class WeatherMapCacheKey(
        val latBucket: Double,
        val lonBucket: Double,
        val mode: String
    ) {
        companion object {
            fun from(latitude: Double, longitude: Double, mode: String): WeatherMapCacheKey =
                WeatherMapCacheKey(
                    latBucket = (latitude * CacheBucketFactor).roundToLong() / CacheBucketFactor,
                    lonBucket = (longitude * CacheBucketFactor).roundToLong() / CacheBucketFactor,
                    mode = mode.lowercase()
                )
        }
    }

    private companion object {
        const val TimeoutMillis = 8_000
        const val CacheBucketFactor = 100_000.0
    }
}

interface WeatherMapHttpClient {
    fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int
    ): WeatherMapHttpResponse
}

data class WeatherMapHttpResponse(
    val statusCode: Int,
    val body: String
)

class UrlConnectionWeatherMapHttpClient : WeatherMapHttpClient {
    override fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int
    ): WeatherMapHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            useCaches = true
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }

        return try {
            val statusCode = connection.responseCode
            val body = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            WeatherMapHttpResponse(statusCode = statusCode, body = body)
        } catch (err: SocketTimeoutException) {
            throw WeatherMapRepositoryError.Timeout(err.message)
        } catch (err: IOException) {
            throw WeatherMapRepositoryError.Network(err.message)
        } finally {
            connection.disconnect()
        }
    }
}

sealed class WeatherMapRepositoryError(message: String?) : Exception(message) {
    object InvalidQuery : WeatherMapRepositoryError("Invalid weather map query")
    data class HttpError(val statusCode: Int, override val message: String) :
        WeatherMapRepositoryError(message)

    data class Timeout(override val message: String?) :
        WeatherMapRepositoryError(message ?: "Weather map timeout")

    data class Network(override val message: String?) :
        WeatherMapRepositoryError(message ?: "Weather map network error")

    data class InvalidJson(override val message: String?) :
        WeatherMapRepositoryError(message ?: "Invalid weather map JSON")

    data class InvalidSchema(override val message: String?) :
        WeatherMapRepositoryError(message ?: "Invalid weather map schema")
}

fun Throwable.toWeatherMapDiagnosticReason(): String =
    when (this) {
        is WeatherMapRepositoryError.HttpError -> when (statusCode) {
            401, 403 -> "HTTP_AUTH"
            404 -> "HTTP_NOT_FOUND"
            in 500..599 -> "HTTP_SERVER"
            else -> "HTTP_$statusCode"
        }
        is WeatherMapRepositoryError.Timeout -> "TIMEOUT"
        is WeatherMapRepositoryError.Network -> "NETWORK"
        is WeatherMapRepositoryError.InvalidJson -> "JSON_PARSING"
        is WeatherMapRepositoryError.InvalidSchema -> "JSON_SCHEMA"
        is WeatherMapRepositoryError.InvalidQuery -> "REPOSITORY_INPUT"
        else -> "REPOSITORY_INTERNAL"
    }

private fun Int.toWeatherMapHttpCategory(): String =
    when (this) {
        401, 403 -> "AUTH"
        404 -> "NOT_FOUND"
        in 500..599 -> "SERVER"
        else -> "HTTP"
    }

private fun Int.toWeatherMapHttpMessage(): String =
    when (this) {
        400 -> "Weather map request is invalid"
        401, 403 -> "Weather map request is not authorized"
        502 -> "Weather map provider error"
        504 -> "Weather map provider timeout"
        else -> "Weather map HTTP $this"
    }

private fun String.toBodySnippet(maxLength: Int = 500): String? =
    replace(Regex("\\s+"), " ")
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }

const val WeatherMapLogTag = "WeatherMap"
