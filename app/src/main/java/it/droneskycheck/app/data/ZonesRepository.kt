package it.droneskycheck.app.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

class ZonesRepository(
    context: Context,
    private val endpointUrl: String = DscApiConfig.ZonesUrl,
    private val apiKey: String = DscApiConfig.ApiKey
) {
    private val cacheDir = File(context.cacheDir, "dsc_zones_api").apply {
        mkdirs()
    }

    fun getZones(bbox: String, type: String): String =
        getZonesResult(bbox, type).body

    fun getZonesResult(bbox: String, type: String): CachedGeoJson {
        val normalizedBbox = normalizeBbox(bbox)
        val cacheFile = File(cacheDir, "${type.lowercase()}_${normalizedBbox.cacheSafeName()}.geojson")
        val now = System.currentTimeMillis()

        if (cacheFile.exists() && now - cacheFile.lastModified() < CacheTtlMillis) {
            return CachedGeoJson(cacheFile.readText(Charsets.UTF_8), degraded = false)
        }

        return runCatching {
            fetchZones(normalizedBbox, type).also { body ->
                cacheFile.writeText(body, Charsets.UTF_8)
            }.let { body ->
                CachedGeoJson(body, degraded = false)
            }
        }.getOrElse { error ->
            if (cacheFile.exists()) {
                CachedGeoJson(cacheFile.readText(Charsets.UTF_8), degraded = true)
            } else {
                throw error
            }
        }
    }

    private fun fetchZones(bbox: String, type: String): String {
        val url = URL(
            "$endpointUrl?bbox=${encode(bbox)}&type=${encode(type)}&simplify=true&limit=1000"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TimeoutMillis
            readTimeout = TimeoutMillis
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-api-key", apiKey)
            useCaches = true
        }

        return try {
            val statusCode = connection.responseCode
            val body = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (statusCode !in 200..299) {
                throw IllegalStateException("zones HTTP $statusCode")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeBbox(bbox: String): String {
        val parts = bbox.split(',').mapNotNull { it.toDoubleOrNull() }
        if (parts.size != 4) return bbox

        val south = floor((parts[0] - BboxPaddingDegrees) / BboxGridDegrees) * BboxGridDegrees
        val west = floor((parts[1] - BboxPaddingDegrees) / BboxGridDegrees) * BboxGridDegrees
        val north = ceil((parts[2] + BboxPaddingDegrees) / BboxGridDegrees) * BboxGridDegrees
        val east = ceil((parts[3] + BboxPaddingDegrees) / BboxGridDegrees) * BboxGridDegrees

        return listOf(south, west, north, east).joinToString(",") {
            String.format(Locale.US, "%.3f", it)
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun String.cacheSafeName(): String =
        replace("-", "m")
            .replace(".", "p")
            .replace(",", "_")

    private companion object {
        const val TimeoutMillis = 8_000
        const val CacheTtlMillis = 7L * 24L * 60L * 60L * 1000L
        const val BboxGridDegrees = 0.1
        const val BboxPaddingDegrees = 0.03
    }
}
