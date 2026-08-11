package it.droneskycheck.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json.JSONException
import org.json.JSONObject

interface LegalTimelineClient {
    suspend fun getLegalTimeline(
        lat: Double,
        lon: Double,
        from: Instant,
        to: Instant
    ): Result<LegalTimelineResponse>
}

class LegalTimelineRepository(
    private val endpointUrl: String = DscApiConfig.LegalTimelineUrl,
    private val apiKey: String = DscApiConfig.ApiKey,
    private val httpClient: LegalTimelineHttpClient = UrlConnectionLegalTimelineHttpClient()
) : LegalTimelineClient {
    override suspend fun getLegalTimeline(
        lat: Double,
        lon: Double,
        from: Instant,
        to: Instant
    ): Result<LegalTimelineResponse> =
        runCatching {
            validateCoordinates(lat, lon)
            if (!to.isAfter(from)) {
                throw LegalTimelineRepositoryError.InvalidWindow
            }

            val requestFrom = from.truncatedTo(ChronoUnit.SECONDS)
            val requestTo = to.truncatedTo(ChronoUnit.SECONDS)
            val url = "$endpointUrl?lat=${encode(lat)}&lon=${encode(lon)}" +
                "&from=${encode(requestFrom.toString())}&to=${encode(requestTo.toString())}"
            DscLogger.debug(
                LogTag,
                "appLegalTimeline request method=GET url=$url headers=Accept,x-api-key " +
                    "lat=$lat lon=$lon from=$requestFrom to=$requestTo timeoutMillis=$TimeoutMillis"
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
                    "appLegalTimeline HTTP failure category=${response.statusCode.toHttpCategory()} " +
                        "status=${response.statusCode} body=${response.body.toBodySnippet()}"
                )
                throw LegalTimelineRepositoryError.HttpError(
                    statusCode = response.statusCode,
                    message = response.statusCode.toHttpMessage(),
                    bodySnippet = response.body.toBodySnippet()
                )
            }

            try {
                parseLegalTimelineResponse(JSONObject(response.body)).also { parsed ->
                    if (parsed.segments.isEmpty()) {
                        DscLogger.warn(
                            LogTag,
                            "appLegalTimeline returned an empty timeline lat=$lat lon=$lon from=$from to=$to"
                        )
                    }
                }
            } catch (err: JSONException) {
                throw LegalTimelineRepositoryError.InvalidJson(err.message)
            } catch (err: IllegalArgumentException) {
                throw LegalTimelineRepositoryError.InvalidSchema(err.message)
            }
        }.onFailure { error ->
            DscLogger.warn(
                LogTag,
                "appLegalTimeline failed reason=${error.toLegalTimelineDiagnosticReason()} " +
                    "lat=$lat lon=$lon from=$from to=$to endpoint=$endpointUrl",
                error
            )
        }

    private fun validateCoordinates(lat: Double, lon: Double) {
        if (!lat.isFinite() || !lon.isFinite()) {
            throw LegalTimelineRepositoryError.InvalidCoordinates
        }
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            throw LegalTimelineRepositoryError.InvalidCoordinates
        }
    }

    private fun encode(value: Any): String =
        URLEncoder.encode(value.toString(), Charsets.UTF_8.name())

    private companion object {
        const val LogTag = "DscLegalTimeline"
        const val TimeoutMillis = 20_000
    }
}

interface LegalTimelineHttpClient {
    fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int
    ): LegalTimelineHttpResponse
}

data class LegalTimelineHttpResponse(
    val statusCode: Int,
    val body: String
)

class UrlConnectionLegalTimelineHttpClient : LegalTimelineHttpClient {
    override fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int
    ): LegalTimelineHttpResponse {
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

            LegalTimelineHttpResponse(
                statusCode = statusCode,
                body = body
            )
        } catch (err: SocketTimeoutException) {
            throw LegalTimelineRepositoryError.Timeout(err.message)
        } catch (err: IOException) {
            throw LegalTimelineRepositoryError.Network(err.message)
        } finally {
            connection.disconnect()
        }
    }
}

sealed class LegalTimelineRepositoryError(message: String?) : Exception(message) {
    object InvalidCoordinates : LegalTimelineRepositoryError("Invalid coordinates")
    object InvalidWindow : LegalTimelineRepositoryError("Invalid legal timeline window")
    data class HttpError(
        val statusCode: Int,
        override val message: String,
        val bodySnippet: String? = null
    ) :
        LegalTimelineRepositoryError(message)

    data class Timeout(override val message: String?) :
        LegalTimelineRepositoryError(message ?: "Legal timeline timeout")

    data class Network(override val message: String?) :
        LegalTimelineRepositoryError(message ?: "Legal timeline network error")

    data class InvalidJson(override val message: String?) :
        LegalTimelineRepositoryError(message ?: "Invalid legal timeline JSON")

    data class InvalidSchema(override val message: String?) :
        LegalTimelineRepositoryError(message ?: "Invalid legal timeline schema")
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
        401, 403 -> "Legal timeline request is not authorized"
        404 -> "Legal timeline endpoint was not found"
        in 500..599 -> "Legal timeline server error HTTP $this"
        else -> "Legal timeline HTTP $this"
    }

private fun String.toBodySnippet(maxLength: Int = 500): String? =
    replace(Regex("\\s+"), " ")
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }

private fun Throwable.toLegalTimelineDiagnosticReason(): String =
    when (this) {
        is LegalTimelineRepositoryError.HttpError -> when (statusCode) {
            401, 403 -> "HTTP_AUTH"
            404 -> "HTTP_NOT_FOUND"
            in 500..599 -> "HTTP_SERVER"
            else -> "HTTP_$statusCode"
        }
        is LegalTimelineRepositoryError.Timeout -> "TIMEOUT"
        is LegalTimelineRepositoryError.Network -> "NETWORK"
        is LegalTimelineRepositoryError.InvalidJson -> "JSON_PARSING"
        is LegalTimelineRepositoryError.InvalidSchema -> "JSON_SCHEMA"
        is LegalTimelineRepositoryError.InvalidCoordinates,
        is LegalTimelineRepositoryError.InvalidWindow -> "REPOSITORY_INPUT"
        else -> "REPOSITORY_INTERNAL"
    }
