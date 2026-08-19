package it.droneskycheck.app.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class CachedGeoJsonRepository internal constructor(
    cacheDir: File
) {
    constructor(context: Context) : this(File(context.cacheDir, "dsc_geojson_layers"))

    private val cacheDir = cacheDir.apply {
        mkdirs()
    }

    fun get(
        url: String,
        cacheKey: String,
        ttlMillis: Long? = null,
        timeoutMillis: Int = DefaultTimeoutMillis,
        forceRefresh: Boolean = false
    ): CachedGeoJson {
        val cacheFile = File(cacheDir, "${cacheKey.cacheSafeName()}.geojson")
        val now = System.currentTimeMillis()

        if (!forceRefresh &&
            ttlMillis != null &&
            cacheFile.exists() &&
            now - cacheFile.lastModified() < ttlMillis
        ) {
            return CachedGeoJson(cacheFile.readText(Charsets.UTF_8), degraded = false)
        }

        return runCatching {
            fetch(url, timeoutMillis).also { body ->
                cacheFile.writeAtomically(body)
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

    fun invalidate(cacheKey: String) {
        val cacheFile = File(cacheDir, "${cacheKey.cacheSafeName()}.geojson")
        runCatching { cacheFile.delete() }
        runCatching { File(cacheDir, "${cacheFile.name}.tmp").delete() }
    }

    private fun fetch(url: String, timeoutMillis: Int): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
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

    private fun File.writeAtomically(body: String) {
        val tempFile = File(parentFile, "$name.tmp")
        tempFile.writeText(body, Charsets.UTF_8)
        runCatching {
            Files.move(
                tempFile.toPath(),
                toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        }.getOrElse {
            Files.move(
                tempFile.toPath(),
                toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    companion object {
        const val DefaultTimeoutMillis = 8_000
    }
}

data class CachedGeoJson(
    val body: String,
    val degraded: Boolean
)
