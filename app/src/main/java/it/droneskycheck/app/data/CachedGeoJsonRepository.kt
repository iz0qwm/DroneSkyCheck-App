package it.droneskycheck.app.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class CachedGeoJsonRepository(context: Context) {
    private val cacheDir = File(context.cacheDir, "dsc_geojson_layers").apply {
        mkdirs()
    }

    fun get(url: String, cacheKey: String): CachedGeoJson {
        val cacheFile = File(cacheDir, "${cacheKey.cacheSafeName()}.geojson")

        return runCatching {
            fetch(url).also { body ->
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

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TimeoutMillis
            readTimeout = TimeoutMillis
            requestMethod = "GET"
            useCaches = true
            setRequestProperty("Accept", "application/geo+json, application/json")
        }

        return try {
            val statusCode = connection.responseCode
            val body = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (statusCode !in 200..299) {
                throw IllegalStateException("GeoJSON HTTP $statusCode")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun String.cacheSafeName(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        const val TimeoutMillis = 8_000
    }
}

data class CachedGeoJson(
    val body: String,
    val degraded: Boolean
)
