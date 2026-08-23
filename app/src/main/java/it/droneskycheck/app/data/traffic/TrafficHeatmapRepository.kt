package it.droneskycheck.app.data.traffic

import it.droneskycheck.app.data.DscApiConfig
import it.droneskycheck.app.data.DscLogger
import java.net.URLEncoder
import org.json.JSONException
import org.json.JSONObject

interface TrafficHeatmapClient {
    suspend fun getTrafficHeatmap(
        lat: Double,
        lon: Double,
        radiusKm: Double = TrafficHeatmapDefaults.DefaultRadiusKm,
        days: Int = TrafficHeatmapDefaults.DefaultDays,
        maxAgl: TrafficHeatmapMaxAgl = TrafficHeatmapMaxAgl.Default
    ): Result<TrafficHeatmapResponse>
}

class TrafficHeatmapRepository(
    private val endpointUrl: String = DscApiConfig.TrafficHeatmapUrl,
    private val apiKey: String = DscApiConfig.ApiKey,
    private val httpClient: TrafficAwarenessHttpClient = UrlConnectionTrafficAwarenessHttpClient()
) : TrafficHeatmapClient {
    override suspend fun getTrafficHeatmap(
        lat: Double,
        lon: Double,
        radiusKm: Double,
        days: Int,
        maxAgl: TrafficHeatmapMaxAgl
    ): Result<TrafficHeatmapResponse> =
        runCatching {
            validateQuery(lat, lon, radiusKm, days)
            val url = "$endpointUrl?lat=${encode(lat)}&lon=${encode(lon)}" +
                "&radiusKm=${encode(radiusKm)}&days=${encode(days)}&maxAgl=${encode(maxAgl.requestValue)}"
            val response = httpClient.get(
                url = url,
                headers = mapOf(
                    "Accept" to "application/json",
                    "x-api-key" to apiKey
                ),
                timeoutMillis = TimeoutMillis
            )
            if (response.statusCode !in 200..299) {
                throw TrafficHeatmapRepositoryError.HttpError(
                    statusCode = response.statusCode,
                    message = response.statusCode.toTrafficHeatmapHttpMessage()
                )
            }

            try {
                parseTrafficHeatmapResponse(JSONObject(response.body))
            } catch (err: JSONException) {
                throw TrafficHeatmapRepositoryError.InvalidJson(err.message)
            } catch (err: TrafficHeatmapMappingError) {
                throw TrafficHeatmapRepositoryError.InvalidSchema(err.message)
            } catch (err: IllegalArgumentException) {
                throw TrafficHeatmapRepositoryError.InvalidSchema(err.message)
            }
        }.onFailure { error ->
            DscLogger.warn(
                TrafficHeatmapLogTag,
                "heatmap request failed reason=${error.toTrafficHeatmapDiagnosticReason()} " +
                    "lat=${lat.coarseTraffic()} lon=${lon.coarseTraffic()} " +
                    "radiusKm=${radiusKm.coarseTraffic(0)} maxAgl=${maxAgl.requestValue}",
                error
            )
        }

    private fun validateQuery(lat: Double, lon: Double, radiusKm: Double, days: Int) {
        if (!lat.isFinite() || !lon.isFinite() || !radiusKm.isFinite()) {
            throw TrafficHeatmapRepositoryError.InvalidQuery
        }
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            throw TrafficHeatmapRepositoryError.InvalidQuery
        }
        if (radiusKm <= 0.0 || radiusKm > TrafficHeatmapDefaults.MaxRadiusKm) {
            throw TrafficHeatmapRepositoryError.InvalidQuery
        }
        if (days <= 0) {
            throw TrafficHeatmapRepositoryError.InvalidQuery
        }
    }

    private fun encode(value: Double): String =
        URLEncoder.encode(value.toString(), Charsets.UTF_8.name())

    private fun encode(value: Int): String =
        URLEncoder.encode(value.toString(), Charsets.UTF_8.name())

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val TimeoutMillis = 8_000
    }
}

sealed class TrafficHeatmapRepositoryError(message: String?) : Exception(message) {
    object InvalidQuery : TrafficHeatmapRepositoryError("Invalid traffic heatmap query")
    data class HttpError(val statusCode: Int, override val message: String) :
        TrafficHeatmapRepositoryError(message)

    data class InvalidJson(override val message: String?) :
        TrafficHeatmapRepositoryError(message ?: "Invalid traffic heatmap JSON")

    data class InvalidSchema(override val message: String?) :
        TrafficHeatmapRepositoryError(message ?: "Invalid traffic heatmap schema")
}

fun Throwable.toTrafficHeatmapDiagnosticReason(): String =
    when (this) {
        is TrafficHeatmapRepositoryError.HttpError -> when (statusCode) {
            401, 403 -> "HTTP_AUTH"
            404 -> "HTTP_NOT_FOUND"
            in 500..599 -> "HTTP_SERVER"
            else -> "HTTP_$statusCode"
        }
        is TrafficHeatmapRepositoryError.InvalidJson -> "JSON_PARSING"
        is TrafficHeatmapRepositoryError.InvalidSchema -> "JSON_SCHEMA"
        is TrafficHeatmapRepositoryError.InvalidQuery -> "REPOSITORY_INPUT"
        else -> "REPOSITORY_INTERNAL"
    }

private fun Int.toTrafficHeatmapHttpMessage(): String =
    when (this) {
        400, 413 -> "Ingrandisci la mappa per visualizzare il traffico osservato"
        401, 403 -> "Richiesta traffico storico non autorizzata"
        404 -> "Endpoint traffico storico non trovato"
        in 500..599 -> "Dati traffico storico temporaneamente non disponibili"
        else -> "Traffico storico HTTP $this"
    }

const val TrafficHeatmapLogTag = "TrafficHeatmap"
