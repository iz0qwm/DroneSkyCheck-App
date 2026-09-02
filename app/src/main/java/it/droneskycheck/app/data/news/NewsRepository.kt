package it.droneskycheck.app.data.news

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface NewsLoadResult {
    data class Available(
        val feed: NewsFeedResponse,
        val fromCache: Boolean,
        val notModified: Boolean
    ) : NewsLoadResult

    data object Unavailable : NewsLoadResult
}

class NewsRepository internal constructor(
    cacheDir: File,
    private val endpoint: String = NewsEndpoint
) {
    constructor(context: Context) : this(File(context.cacheDir, "dsc_news"))

    private val cacheDir = cacheDir.apply { mkdirs() }
    private val cacheLock = Any()

    fun getNews(request: NewsFeedRequest): NewsLoadResult {
        val key = request.cacheKey()
        val payloadFile = File(cacheDir, "$key.json")
        val etagFile = File(cacheDir, "$key.etag")
        val cachedEtag = synchronized(cacheLock) { etagFile.readTextOrNull() }

        return runCatching {
            val response = fetch(request, cachedEtag)
            when (response.statusCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = response.body
                    val parsed = parseNewsFeed(body)
                    synchronized(cacheLock) {
                        payloadFile.writeAtomically(body)
                        if (response.etag != null) {
                            etagFile.writeAtomically(response.etag)
                        } else {
                            etagFile.delete()
                        }
                    }
                    NewsLoadResult.Available(parsed, fromCache = false, notModified = false)
                }
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    val cached = synchronized(cacheLock) { payloadFile.readTextOrNull() }
                        ?: throw IllegalStateException("News HTTP 304 without cached payload")
                    NewsLoadResult.Available(
                        feed = parseNewsFeed(cached),
                        fromCache = false,
                        notModified = true
                    )
                }
                else -> throw IllegalStateException("News HTTP ${response.statusCode}")
            }
        }.getOrElse {
            val cached = synchronized(cacheLock) { payloadFile.readTextOrNull() }
                ?: return NewsLoadResult.Unavailable
            runCatching {
                NewsLoadResult.Available(
                    feed = parseNewsFeed(cached),
                    fromCache = true,
                    notModified = false
                )
            }.getOrDefault(NewsLoadResult.Unavailable)
        }
    }

    private fun fetch(request: NewsFeedRequest, etag: String?): HttpResponse {
        val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TimeoutMillis
            readTimeout = TimeoutMillis
            useCaches = true
            setRequestProperty("Accept", "application/json")
            etag?.let { setRequestProperty("If-None-Match", it) }
        }
        return try {
            val statusCode = connection.responseCode
            val stream = when {
                statusCode == HttpURLConnection.HTTP_NOT_MODIFIED -> null
                statusCode in 200..299 -> connection.inputStream
                else -> connection.errorStream
            }
            HttpResponse(
                statusCode = statusCode,
                body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(),
                etag = connection.getHeaderField("ETag")?.trim()?.takeIf(String::isNotBlank)
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun NewsFeedRequest.url(): String {
        val parameters = buildList {
            add("limit" to limit.toString())
            add("offset" to offset.toString())
            category?.let { add("category" to it) }
            scope?.let { add("scope" to it) }
            sourceType?.let { add("source_type" to it) }
        }.joinToString("&") { (name, value) ->
            "$name=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
        return "$endpoint?$parameters"
    }

    private fun NewsFeedRequest.cacheKey(): String = buildString {
        append("limit_").append(limit)
        append("_offset_").append(offset)
        append("_category_").append(category ?: "all")
        append("_scope_").append(scope ?: "all")
        append("_sourceType_").append(sourceType ?: "all")
    }.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun File.readTextOrNull(): String? =
        takeIf { exists() }?.readText(Charsets.UTF_8)?.takeIf { it.isNotBlank() }

    private fun File.writeAtomically(value: String) {
        val temporary = File(parentFile, "$name.tmp")
        temporary.writeText(value, Charsets.UTF_8)
        runCatching {
            Files.move(
                temporary.toPath(),
                toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        }.getOrElse {
            Files.move(temporary.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private data class HttpResponse(
        val statusCode: Int,
        val body: String,
        val etag: String?
    )

    private companion object {
        const val NewsEndpoint = "https://solarmonitor.kwos.org/api/news"
        const val TimeoutMillis = 8_000
    }
}
