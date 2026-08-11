package it.droneskycheck.app.data.weather

import it.droneskycheck.app.data.DscApiConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import org.json.JSONException
import org.json.JSONObject

class WeatherForecastRepository(
    private val endpointUrl: String = DscApiConfig.WeatherForecastUrl,
    private val apiKey: String = DscApiConfig.ApiKey,
    private val httpClient: WeatherForecastHttpClient = UrlConnectionWeatherForecastHttpClient()
) {
    suspend fun getForecast(latitude: Double, longitude: Double): Result<WeatherForecast> =
        runCatching {
            validateCoordinates(latitude, longitude)

            val response = httpClient.get(
                url = "$endpointUrl?lat=${encode(latitude)}&lon=${encode(longitude)}",
                headers = mapOf(
                    "Accept" to "application/json",
                    "x-api-key" to apiKey
                ),
                timeoutMillis = TimeoutMillis
            )

            if (response.statusCode !in 200..299) {
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
                dto.toDomain()
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
