package it.droneskycheck.app.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.Instant
import org.json.JSONObject

data class UasDatasetUpdates(
    val sourceUpdatedAt: String,
    val addedCount: Int,
    val removedCount: Int,
    val modifiedCount: Int,
    val cachedOnDeviceAt: String?,
    val degraded: Boolean
)

class UasDatasetUpdatesRepository internal constructor(
    cacheDir: File,
    private val updatesUrl: String = DscApiConfig.UasDatasetUpdatesUrl,
    private val clock: Clock = Clock.systemUTC()
) {
    constructor(
        context: Context,
        updatesUrl: String = DscApiConfig.UasDatasetUpdatesUrl,
        clock: Clock = Clock.systemUTC()
    ) : this(
        cacheDir = File(context.cacheDir, "dsc_dataset_updates"),
        updatesUrl = updatesUrl,
        clock = clock
    )

    private val cacheDir = cacheDir.apply { mkdirs() }
    private val cacheFile = File(cacheDir, "updates.json")
    private val cachedAtFile = File(cacheDir, "updates_cached_at.txt")

    fun getUpdates(): UasDatasetUpdates? =
        runCatching {
            val body = fetch(updatesUrl)
            val cachedAt = clock.instant().toString()
            cacheFile.writeText(body, Charsets.UTF_8)
            cachedAtFile.writeText(cachedAt, Charsets.UTF_8)
            parseUasDatasetUpdates(body, cachedOnDeviceAt = cachedAt, degraded = false)
        }.getOrElse {
            if (!cacheFile.exists()) return null
            runCatching {
                parseUasDatasetUpdates(
                    json = cacheFile.readText(Charsets.UTF_8),
                    cachedOnDeviceAt = cachedAtFile.readTextOrNull(),
                    degraded = true
                )
            }.getOrNull()
        }

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TimeoutMillis
            readTimeout = TimeoutMillis
            useCaches = true
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val statusCode = connection.responseCode
            val body = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                throw IllegalStateException("updates HTTP $statusCode")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun File.readTextOrNull(): String? =
        takeIf { exists() }
            ?.readText(Charsets.UTF_8)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val TimeoutMillis = 4_000
    }
}

fun parseUasDatasetUpdates(
    json: String,
    cachedOnDeviceAt: String?,
    degraded: Boolean
): UasDatasetUpdates {
    val root = JSONObject(json.ifBlank { "{}" })
    val timestamp = root.optStringOrNull("timestamp")
        ?: throw IllegalArgumentException("updates.json missing timestamp")
    Instant.parse(timestamp)
    cachedOnDeviceAt?.let { Instant.parse(it) }
    return UasDatasetUpdates(
        sourceUpdatedAt = timestamp,
        addedCount = root.optJSONArray("added")?.length() ?: 0,
        removedCount = root.optJSONArray("removed")?.length() ?: 0,
        modifiedCount = root.optJSONArray("modified")?.length() ?: 0,
        cachedOnDeviceAt = cachedOnDeviceAt,
        degraded = degraded
    )
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).trim().takeIf { it.isNotBlank() && it != "null" }
