package it.droneskycheck.app.data.ai

import it.droneskycheck.app.data.DscApiConfig
import it.droneskycheck.app.data.DscLogger
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.json.JSONException
import org.json.JSONObject

interface AiAssistantClient {
    suspend fun answer(request: AiAssistantRequest): Result<AiAssistantResponse>
}

class AiAssistantRepository(
    private val endpointUrl: String = DscApiConfig.AiAssistantAnswerUrl,
    private val apiKey: String = DscApiConfig.ApiKey,
    private val httpClient: AiAssistantHttpClient = UrlConnectionAiAssistantHttpClient()
) : AiAssistantClient {
    override suspend fun answer(request: AiAssistantRequest): Result<AiAssistantResponse> =
        runCatching {
            val startedAt = System.nanoTime()
            DscLogger.debug(
                AiAssistantLogTag,
                "request start endpoint=${endpointUrl.toEndpointName()} " +
                    "queryLength=${request.query.length} includeSources=${request.includeSources} " +
                    "includeDiagnostics=${request.includeDiagnostics} context=${request.context.toLogSummary()}"
            )
            val response = httpClient.post(
                url = URL(endpointUrl),
                apiKey = apiKey,
                body = request.toJson().toString()
            )
            val elapsedMs = startedAt.elapsedMillis()
            DscLogger.debug(
                AiAssistantLogTag,
                "HTTP response status=${response.statusCode} elapsedMs=$elapsedMs bodyLength=${response.body.length}"
            )
            if (response.statusCode !in 200..299) {
                throw AiAssistantRepositoryError.HttpError(response.statusCode)
            }
            try {
                val responseJson = JSONObject(response.body)
                DscLogger.debug(AiAssistantLogTag, "response shape ${responseJson.toAiAssistantShapeLogSummary()}")
                parseAiAssistantResponse(responseJson).also { parsed ->
                    DscLogger.debug(
                        AiAssistantLogTag,
                        "response parsed status=${parsed.status ?: "missing"} route=${parsed.route ?: "missing"} " +
                            "kind=${parsed.kind} mappedTextSource=${parsed.mappedTextSource} " +
                            "textLength=${parsed.displayText.length} sources=${parsed.sources.size} " +
                            "elapsedMs=${startedAt.elapsedMillis()}"
                    )
                }
            } catch (error: JSONException) {
                DscLogger.warn(
                    AiAssistantLogTag,
                    "response JSON parse failed elapsedMs=${startedAt.elapsedMillis()} bodyLength=${response.body.length}",
                    error
                )
                throw AiAssistantRepositoryError.InvalidJson(error.message)
            } catch (error: RuntimeException) {
                DscLogger.warn(
                    AiAssistantLogTag,
                    "response schema parse failed elapsedMs=${startedAt.elapsedMillis()} bodyLength=${response.body.length}",
                    error
                )
                throw AiAssistantRepositoryError.InvalidSchema(error.message)
            }
        }.onFailure { error ->
            DscLogger.warn(
                AiAssistantLogTag,
                "request failed reason=${error.toAiAssistantDiagnosticReason()}",
                error
            )
        }
}

interface AiAssistantHttpClient {
    fun post(url: URL, apiKey: String, body: String): AiAssistantHttpResponse
}

data class AiAssistantHttpResponse(
    val statusCode: Int,
    val body: String
)

class UrlConnectionAiAssistantHttpClient : AiAssistantHttpClient {
    override fun post(url: URL, apiKey: String, body: String): AiAssistantHttpResponse {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = ConnectTimeoutMillis
            readTimeout = ReadTimeoutMillis
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
        }

        return try {
            connection.outputStream.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            AiAssistantHttpResponse(
                statusCode = statusCode,
                body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            )
        } catch (error: SocketTimeoutException) {
            throw AiAssistantRepositoryError.Timeout(error.message)
        } catch (error: IOException) {
            throw AiAssistantRepositoryError.Network(error.message)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ConnectTimeoutMillis = 15_000
        const val ReadTimeoutMillis = 60_000
    }
}

sealed class AiAssistantRepositoryError(message: String?) : Exception(message) {
    data class HttpError(val statusCode: Int) : AiAssistantRepositoryError("AI assistant HTTP $statusCode")
    data class Timeout(override val message: String?) : AiAssistantRepositoryError(message ?: "AI assistant timeout")
    data class Network(override val message: String?) : AiAssistantRepositoryError(message ?: "AI assistant network error")
    data class InvalidJson(override val message: String?) : AiAssistantRepositoryError(message ?: "Invalid AI assistant JSON")
    data class InvalidSchema(override val message: String?) : AiAssistantRepositoryError(message ?: "Invalid AI assistant schema")
}

private const val AiAssistantLogTag = "DscAiAssistant"

private fun AiAssistantContext.toLogSummary(): String =
    listOf(
        "location=${location != null}",
        "aircraftModel=${aircraftModel != null}",
        "classMark=${classMark != null}",
        "massGrams=${massGrams != null}",
        "cameraPresent=${cameraPresent != null}"
    ).joinToString(prefix = "{", postfix = "}")

private fun Throwable.toAiAssistantDiagnosticReason(): String =
    when (this) {
        is AiAssistantRepositoryError.Timeout -> "TIMEOUT"
        is AiAssistantRepositoryError.Network -> "NETWORK"
        is AiAssistantRepositoryError.HttpError -> "HTTP_$statusCode"
        is AiAssistantRepositoryError.InvalidJson -> "JSON_PARSING"
        is AiAssistantRepositoryError.InvalidSchema -> "JSON_SCHEMA"
        else -> this::class.simpleName ?: "UNKNOWN"
    }

private fun String.toEndpointName(): String =
    substringAfterLast('/').ifBlank { this }

private fun Long.elapsedMillis(): Long =
    (System.nanoTime() - this) / 1_000_000L
