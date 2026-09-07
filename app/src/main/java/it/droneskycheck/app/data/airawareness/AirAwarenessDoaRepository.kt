package it.droneskycheck.app.data.airawareness

import it.droneskycheck.app.data.DscApiConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

interface AirAwarenessDoaClient {
    suspend fun createDoa(
        operationId: String,
        closeToken: String,
        lat: Double,
        lon: Double
    ): Result<PublishedDoa>

    suspend fun closeDoa(doa: PublishedDoa): Result<Unit>
}

class AirAwarenessDoaRepository(
    private val endpointUrl: String = DscApiConfig.AirAwarenessDoaUrl,
    private val apiKey: String = DscApiConfig.ApiKey,
    private val httpClient: AirAwarenessDoaHttpClient = UrlConnectionAirAwarenessDoaHttpClient()
) : AirAwarenessDoaClient {
    override suspend fun createDoa(
        operationId: String,
        closeToken: String,
        lat: Double,
        lon: Double
    ): Result<PublishedDoa> = runCatching {
        require(lat.isFinite() && lat in -90.0..90.0)
        require(lon.isFinite() && lon in -180.0..180.0)
        val response = httpClient.post(
            url = endpointUrl,
            headers = headers(),
            body = JSONObject()
                .put("action", "create")
                .put("operationId", operationId)
                .put("closeToken", closeToken)
                .put("lat", lat)
                .put("lon", lon)
                .toString(),
            timeoutMillis = TimeoutMillis
        )
        val json = response.requireSuccessfulJson()
        parsePublishedDoa(json.getJSONObject("doa"), closeToken)
    }

    override suspend fun closeDoa(doa: PublishedDoa): Result<Unit> = runCatching {
        val response = httpClient.post(
            url = endpointUrl,
            headers = headers(),
            body = JSONObject()
                .put("action", "close")
                .put("operationId", doa.id)
                .put("closeToken", doa.closeToken)
                .toString(),
            timeoutMillis = TimeoutMillis
        )
        response.requireSuccessfulJson()
        Unit
    }

    private fun headers(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Content-Type" to "application/json; charset=utf-8",
        "x-api-key" to apiKey
    )

    private fun AirAwarenessDoaHttpResponse.requireSuccessfulJson(): JSONObject {
        if (statusCode !in 200..299) throw AirAwarenessDoaError.Http(statusCode)
        val json = try {
            JSONObject(body)
        } catch (error: Exception) {
            throw AirAwarenessDoaError.InvalidResponse(error.message)
        }
        if (!json.optBoolean("ok", false)) throw AirAwarenessDoaError.InvalidResponse("DOA response not ok")
        return json
    }

    private companion object {
        const val TimeoutMillis = 12_000
    }
}

interface AirAwarenessDoaHttpClient {
    fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMillis: Int
    ): AirAwarenessDoaHttpResponse
}

data class AirAwarenessDoaHttpResponse(val statusCode: Int, val body: String)

class UrlConnectionAirAwarenessDoaHttpClient : AirAwarenessDoaHttpClient {
    override fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMillis: Int
    ): AirAwarenessDoaHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            doOutput = true
            useCaches = false
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val statusCode = connection.responseCode
            val responseBody = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            AirAwarenessDoaHttpResponse(statusCode, responseBody)
        } catch (error: IOException) {
            throw AirAwarenessDoaError.Network(error.message)
        } finally {
            connection.disconnect()
        }
    }
}

sealed class AirAwarenessDoaError(message: String) : Exception(message) {
    data class Http(val statusCode: Int) : AirAwarenessDoaError("DOA HTTP $statusCode")
    data class Network(override val message: String?) : AirAwarenessDoaError(message ?: "DOA network error")
    data class InvalidResponse(override val message: String?) : AirAwarenessDoaError(message ?: "Invalid DOA response")
}
