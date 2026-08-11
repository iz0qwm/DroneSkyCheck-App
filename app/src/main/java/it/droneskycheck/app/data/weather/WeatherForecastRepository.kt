package it.droneskycheck.app.data.weather

import it.droneskycheck.app.data.DscApiConfig
import it.droneskycheck.app.data.DscLogger
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import org.json.JSONException
import org.json.JSONObject

interface WeatherForecastClient {
    suspend fun getForecast(latitude: Double, longitude: Double): Result<WeatherForecast>
}

class WeatherForecastRepository(
    private val endpointUrl: String = DscApiConfig.WeatherForecastUrl,
    private val apiKey: String = DscApiConfig.ApiKey,
    private val httpClient: WeatherForecastHttpClient = UrlConnectionWeatherForecastHttpClient()
) : WeatherForecastClient {
    override suspend fun getForecast(latitude: Double, longitude: Double): Result<WeatherForecast> =
        runCatching {
            validateCoordinates(latitude, longitude)

            val url = "$endpointUrl?lat=${encode(latitude)}&lon=${encode(longitude)}"
            DscLogger.debug(
                LogTag,
                "appWeatherForecast request method=GET url=$url headers=Accept,x-api-key " +
                    "lat=$latitude lon=$longitude timeoutMillis=$TimeoutMillis"
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
                    LogTag,
                    "appWeatherForecast HTTP failure category=${response.statusCode.toHttpCategory()} " +
                        "status=${response.statusCode} body=${response.body.toBodySnippet()}"
                )
                throw WeatherForecastRepositoryError.HttpError(
                    statusCode = response.statusCode,
                    message = httpErrorMessage(response.statusCode)
                )
            }

            val dto = try {
                parseWeatherForecastApiResponse(JSONObject(response.body))
            } catch (err: JSONException) {
                throw WeatherForecastRepositoryError.InvalidJson(err.message)
            }

            try {
                dto.toDomain().also { forecast ->
                    DscLogger.debug(
                        LogTag,
                        "appWeatherForecast success lat=$latitude lon=$longitude hours=${forecast.hours.size}"
                    )
                }
            } catch (err: WeatherForecastMappingError) {
                throw when (err) {
                    WeatherForecastMappingError.EmptyForecast ->
                        WeatherForecastRepositoryError.EmptyForecast
                    is WeatherForecastMappingError.InvalidTime ->
                        WeatherForecastRepositoryError.InvalidSchema(err.message)
                    is WeatherForecastMappingError.UnsupportedSchemaVersion ->
                        WeatherForecastRepositoryError.UnsupportedSchemaVersion(err.schemaVersion)
                }
            }
        }.onFailure { error ->
            DscLogger.warn(
                LogTag,
                "appWeatherForecast failed reason=${error.toWeatherDiagnosticReason()} " +
                    "lat=$latitude lon=$longitude endpoint=$endpointUrl",
                error
            )
        }

    private fun validateCoordinates(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) {
            throw WeatherForecastRepositoryError.InvalidCoordinates
        }
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
            throw WeatherForecastRepositoryError.InvalidCoordinates
        }
    }

    private fun encode(value: Double): String =
        URLEncoder.encode(value.toString(), Charsets.UTF_8.name())

    private fun httpErrorMessage(statusCode: Int): String =
        when (statusCode) {
            400 -> "Weather forecast request is invalid"
            401, 403 -> "Weather forecast request is not authorized"
            502 -> "Weather forecast provider error"
            504 -> "Weather forecast provider timeout"
            else -> "Weather forecast HTTP $statusCode"
        }

    private companion object {
        const val LogTag = "DscWeather"
        const val TimeoutMillis = 8_000
    }
}

interface WeatherForecastHttpClient {
    fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int
    ): WeatherForecastHttpResponse
}

data class WeatherForecastHttpResponse(
    val statusCode: Int,
    val body: String
)

class UrlConnectionWeatherForecastHttpClient : WeatherForecastHttpClient {
    override fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int
    ): WeatherForecastHttpResponse {
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

            WeatherForecastHttpResponse(
                statusCode = statusCode,
                body = body
            )
        } catch (err: SocketTimeoutException) {
            throw WeatherForecastRepositoryError.Timeout(err.message)
        } catch (err: IOException) {
            throw WeatherForecastRepositoryError.Network(err.message)
        } finally {
            connection.disconnect()
        }
    }
}

sealed class WeatherForecastRepositoryError(message: String?) : Exception(message) {
    object InvalidCoordinates : WeatherForecastRepositoryError("Invalid coordinates")
    data class HttpError(val statusCode: Int, override val message: String) :
        WeatherForecastRepositoryError(message)

    data class Timeout(override val message: String?) :
        WeatherForecastRepositoryError(message ?: "Weather forecast timeout")

    data class Network(override val message: String?) :
        WeatherForecastRepositoryError(message ?: "Weather forecast network error")

    data class InvalidJson(override val message: String?) :
        WeatherForecastRepositoryError(message ?: "Invalid weather forecast JSON")

    data class InvalidSchema(override val message: String?) :
        WeatherForecastRepositoryError(message ?: "Invalid weather forecast schema")

    data class UnsupportedSchemaVersion(val schemaVersion: Int) :
        WeatherForecastRepositoryError("Unsupported weather forecast schemaVersion $schemaVersion")

    object EmptyForecast : WeatherForecastRepositoryError("Weather forecast is empty")
}

private fun Int.toHttpCategory(): String =
    when (this) {
        401, 403 -> "AUTH"
        404 -> "NOT_FOUND"
        in 500..599 -> "SERVER"
        else -> "HTTP"
    }

private fun String.toBodySnippet(maxLength: Int = 500): String? =
    replace(Regex("\\s+"), " ")
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }

private fun Throwable.toWeatherDiagnosticReason(): String =
    when (this) {
        is WeatherForecastRepositoryError.HttpError -> when (statusCode) {
            401, 403 -> "HTTP_AUTH"
            404 -> "HTTP_NOT_FOUND"
            in 500..599 -> "HTTP_SERVER"
            else -> "HTTP_$statusCode"
        }
        is WeatherForecastRepositoryError.Timeout -> "TIMEOUT"
        is WeatherForecastRepositoryError.Network -> "NETWORK"
        is WeatherForecastRepositoryError.InvalidJson -> "JSON_PARSING"
        is WeatherForecastRepositoryError.InvalidSchema -> "JSON_SCHEMA"
        is WeatherForecastRepositoryError.UnsupportedSchemaVersion -> "UNSUPPORTED_SCHEMA"
        is WeatherForecastRepositoryError.EmptyForecast -> "EMPTY_FORECAST"
        is WeatherForecastRepositoryError.InvalidCoordinates -> "REPOSITORY_INPUT"
        else -> "REPOSITORY_INTERNAL"
    }
