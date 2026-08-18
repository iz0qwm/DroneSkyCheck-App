package it.droneskycheck.app.data.traffic

import it.droneskycheck.app.data.DscApiConfig
import it.droneskycheck.app.data.DscLogger
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import org.json.JSONException
import org.json.JSONObject

interface TrafficAwarenessClient {
    suspend fun getTrafficAwareness(
        lat: Double,
        lon: Double,
        radiusKm: Double = TrafficAwarenessDefaults.DefaultRadiusKm
    ): Result<TrafficAwarenessResponse>
}

class TrafficAwarenessRepository(
    private val endpointUrl: String = DscApiConfig.TrafficAwarenessUrl,
    private val apiKey: String = DscApiConfig.ApiKey,
    private val httpClient: TrafficAwarenessHttpClient = UrlConnectionTrafficAwarenessHttpClient()
) : TrafficAwarenessClient {
    override suspend fun getTrafficAwareness(
        lat: Double,
        lon: Double,
        radiusKm: Double
    ): Result<TrafficAwarenessResponse> =
        runCatching {
            validateQuery(lat, lon, radiusKm)

            val disabledProviders = TemporarilyDisabledProviders.joinToString(",")
            val url = "$endpointUrl?lat=${encode(lat)}&lon=${encode(lon)}&radius=${encode(radiusKm)}" +
                "&disabledProviders=${encode(disabledProviders)}"
            DscLogger.trace(
                TrafficAwarenessLogTag,
                "HTTP request start method=GET endpoint=appTrafficAwareness " +
                    "lat=${lat.coarseTraffic()} lon=${lon.coarseTraffic()} " +
                    "radiusKm=${radiusKm.coarseTraffic(0)} disabledProviders=$disabledProviders " +
                    "timeoutMillis=$TimeoutMillis"
            )
            val startedAt = System.currentTimeMillis()
            val response = httpClient.get(
                url = url,
                headers = mapOf(
                    "Accept" to "application/json",
                    "x-api-key" to apiKey
                ),
                timeoutMillis = TimeoutMillis
            )
            DscLogger.trace(
                TrafficAwarenessLogTag,
                "HTTP response code=${response.statusCode} durationMs=${System.currentTimeMillis() - startedAt}"
            )

            if (response.statusCode !in 200..299) {
                DscLogger.warn(
                    TrafficAwarenessLogTag,
                    "HTTP error code=${response.statusCode} category=${response.statusCode.toHttpCategory()} " +
                        "body=${response.body.toBodySnippet()}"
                )
                throw TrafficAwarenessRepositoryError.HttpError(
                    statusCode = response.statusCode,
                    message = response.statusCode.toHttpMessage()
                )
            }

            try {
                parseTrafficAwarenessResponse(JSONObject(response.body)).also { parsed ->
                    DscLogger.trace(
                        TrafficAwarenessLogTag,
                        "providers ${parsed.providers.entries.joinToString(" ") { "${it.key}=${it.value.status}" }}"
                    )
                }
            } catch (err: JSONException) {
                throw TrafficAwarenessRepositoryError.InvalidJson(err.message)
            } catch (err: TrafficAwarenessMappingError) {
                throw TrafficAwarenessRepositoryError.InvalidSchema(err.message)
            } catch (err: IllegalArgumentException) {
                throw TrafficAwarenessRepositoryError.InvalidSchema(err.message)
            }
        }.onFailure { error ->
            DscLogger.warn(
                TrafficAwarenessLogTag,
                "network error type=${error.javaClass.simpleName} reason=${error.toTrafficAwarenessDiagnosticReason()} " +
                    "lat=${lat.coarseTraffic()} lon=${lon.coarseTraffic()} radiusKm=${radiusKm.coarseTraffic(0)}",
                error
            )
        }

    private fun validateQuery(lat: Double, lon: Double, radiusKm: Double) {
        if (!lat.isFinite() || !lon.isFinite() || !radiusKm.isFinite()) {
            throw TrafficAwarenessRepositoryError.InvalidQuery
        }
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            throw TrafficAwarenessRepositoryError.InvalidQuery
        }
        if (radiusKm <= 0.0 || radiusKm > MaxRadiusKm) {
            throw TrafficAwarenessRepositoryError.InvalidQuery
        }
    }

    private fun encode(value: Double): String =
        URLEncoder.encode(value.toString(), Charsets.UTF_8.name())

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val TimeoutMillis = 10_000
        const val MaxRadiusKm = 100.0
        val TemporarilyDisabledProviders = listOf("airplanes.lol", "adsb.lol")
    }
}

interface TrafficAwarenessHttpClient {
    fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int
    ): TrafficAwarenessHttpResponse
}

data class TrafficAwarenessHttpResponse(
    val statusCode: Int,
    val body: String
)

class UrlConnectionTrafficAwarenessHttpClient : TrafficAwarenessHttpClient {
    override fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int
    ): TrafficAwarenessHttpResponse {
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

            TrafficAwarenessHttpResponse(
                statusCode = statusCode,
                body = body
            )
        } catch (err: SocketTimeoutException) {
            DscLogger.warn(TrafficAwarenessLogTag, "network error type=SocketTimeoutException", err)
            throw TrafficAwarenessRepositoryError.Timeout(err.message)
        } catch (err: IOException) {
            DscLogger.warn(TrafficAwarenessLogTag, "network error type=${err.javaClass.simpleName}", err)
            throw TrafficAwarenessRepositoryError.Network(err.message)
        } finally {
            connection.disconnect()
        }
    }
}

sealed class TrafficAwarenessRepositoryError(message: String?) : Exception(message) {
    object InvalidQuery : TrafficAwarenessRepositoryError("Invalid Traffic Awareness query")
    data class HttpError(val statusCode: Int, override val message: String) :
        TrafficAwarenessRepositoryError(message)

    data class Timeout(override val message: String?) :
        TrafficAwarenessRepositoryError(message ?: "Traffic Awareness timeout")

    data class Network(override val message: String?) :
        TrafficAwarenessRepositoryError(message ?: "Traffic Awareness network error")

    data class InvalidJson(override val message: String?) :
        TrafficAwarenessRepositoryError(message ?: "Invalid Traffic Awareness JSON")

    data class InvalidSchema(override val message: String?) :
        TrafficAwarenessRepositoryError(message ?: "Invalid Traffic Awareness schema")
}

private fun Int.toHttpCategory(): String =
    when (this) {
        401, 403 -> "AUTH"
        404 -> "NOT_FOUND"
        in 500..599 -> "SERVER"
        else -> "HTTP"
    }

private fun Int.toHttpMessage(): String =
    when (this) {
        400 -> "Traffic Awareness request is invalid"
        401, 403 -> "Traffic Awareness request is not authorized"
        404 -> "Traffic Awareness endpoint was not found"
        in 500..599 -> "Traffic Awareness server error HTTP $this"
        else -> "Traffic Awareness HTTP $this"
    }

private fun String.toBodySnippet(maxLength: Int = 500): String? =
    replace(Regex("\\s+"), " ")
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }

fun Throwable.toTrafficAwarenessDiagnosticReason(): String =
    when (this) {
        is TrafficAwarenessRepositoryError.HttpError -> when (statusCode) {
            401, 403 -> "HTTP_AUTH"
            404 -> "HTTP_NOT_FOUND"
            in 500..599 -> "HTTP_SERVER"
            else -> "HTTP_$statusCode"
        }
        is TrafficAwarenessRepositoryError.Timeout -> "TIMEOUT"
        is TrafficAwarenessRepositoryError.Network -> "NETWORK"
        is TrafficAwarenessRepositoryError.InvalidJson -> "JSON_PARSING"
        is TrafficAwarenessRepositoryError.InvalidSchema -> "JSON_SCHEMA"
        is TrafficAwarenessRepositoryError.InvalidQuery -> "REPOSITORY_INPUT"
        else -> "REPOSITORY_INTERNAL"
    }
