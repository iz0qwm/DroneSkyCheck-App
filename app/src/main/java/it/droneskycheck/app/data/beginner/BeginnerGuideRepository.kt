package it.droneskycheck.app.data.beginner

import android.content.Context
import it.droneskycheck.app.data.DscApiConfig
import it.droneskycheck.app.data.DscLogger
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class BeginnerGuideContent(
    val manifest: BeginnerGuideManifest,
    val degraded: Boolean,
    val unavailableImages: Set<String>
)

sealed class BeginnerGuideLoadResult {
    data class Available(val content: BeginnerGuideContent) : BeginnerGuideLoadResult()
    data class Failed(val reason: String) : BeginnerGuideLoadResult()
}

interface BeginnerGuideClient {
    suspend fun loadGuide(forceRefresh: Boolean = false): BeginnerGuideLoadResult
    fun cachedImageFile(manifest: BeginnerGuideManifest, page: BeginnerGuidePage): File?
}

class BeginnerGuideRepository(
    private val storage: BeginnerGuideStorage,
    private val manifestUrl: String = DscApiConfig.BeginnerGuideManifestUrl,
    private val imagesBaseUrl: String = DscApiConfig.BeginnerGuideImagesBaseUrl,
    private val httpClient: BeginnerGuideHttpClient = UrlConnectionBeginnerGuideHttpClient()
) : BeginnerGuideClient {
    constructor(
        context: Context,
        manifestUrl: String = DscApiConfig.BeginnerGuideManifestUrl,
        imagesBaseUrl: String = DscApiConfig.BeginnerGuideImagesBaseUrl,
        httpClient: BeginnerGuideHttpClient = UrlConnectionBeginnerGuideHttpClient()
    ) : this(
        storage = FileBeginnerGuideStorage(File(context.filesDir, CacheDirectoryName)),
        manifestUrl = manifestUrl,
        imagesBaseUrl = imagesBaseUrl,
        httpClient = httpClient
    )

    override suspend fun loadGuide(forceRefresh: Boolean): BeginnerGuideLoadResult {
        val cached = storage.readManifestJson()
            ?.let { BeginnerGuideManifestParser.parse(it).manifest }

        val manifest = try {
            fetchRemoteManifest()
        } catch (error: Throwable) {
            DscLogger.warn(LogTag, "Beginner guide manifest remote unavailable", error)
            if (!forceRefresh && cached != null) {
                return availableFromCache(cached, degraded = true)
            }
            return cached?.let { availableFromCache(it, degraded = true) }
                ?: BeginnerGuideLoadResult.Failed(error.toBeginnerGuideReason())
        }

        storage.writeManifestAtomically(manifest.json)
        val unavailableImages = installMissingImages(manifest.manifest)
        return BeginnerGuideLoadResult.Available(
            BeginnerGuideContent(
                manifest = manifest.manifest,
                degraded = unavailableImages.isNotEmpty(),
                unavailableImages = unavailableImages
            )
        )
    }

    override fun cachedImageFile(manifest: BeginnerGuideManifest, page: BeginnerGuidePage): File? =
        storage.imageFile(manifest.contentVersion, page.cacheFileName())
            .takeIf { it.exists() && it.length() > 0L }

    private fun availableFromCache(
        manifest: BeginnerGuideManifest,
        degraded: Boolean
    ): BeginnerGuideLoadResult.Available =
        BeginnerGuideLoadResult.Available(
            BeginnerGuideContent(
                manifest = manifest,
                degraded = degraded,
                unavailableImages = manifest.pages
                    .filter { cachedImageFile(manifest, it) == null }
                    .map { it.id }
                    .toSet()
            )
        )

    private fun fetchRemoteManifest(): DownloadedBeginnerManifest {
        val response = httpClient.get(manifestUrl, timeoutMillis = TimeoutMillis)
        if (response.statusCode !in 200..299) error("HTTP_${response.statusCode}")
        val parsed = BeginnerGuideManifestParser.parse(response.body)
        val manifest = parsed.manifest ?: error("Invalid manifest")
        parsed.warnings.take(5).forEach { warning ->
            DscLogger.debug(LogTag, "Beginner guide manifest warning ${warning.code}: ${warning.message}")
        }
        return DownloadedBeginnerManifest(manifest = manifest, json = response.body)
    }

    private fun installMissingImages(manifest: BeginnerGuideManifest): Set<String> {
        val unavailable = mutableSetOf<String>()
        manifest.pages.forEach { page ->
            val target = storage.imageFile(manifest.contentVersion, page.cacheFileName())
            if (target.exists() && target.length() > 0L) return@forEach
            runCatching {
                val response = httpClient.getBytes(
                    url = page.imageUrl(),
                    timeoutMillis = TimeoutMillis
                )
                if (response.statusCode !in 200..299 || response.bytes.isEmpty()) {
                    error("HTTP_${response.statusCode}")
                }
                storage.writeImageAtomically(manifest.contentVersion, page.cacheFileName(), response.bytes)
            }.onFailure { error ->
                unavailable += page.id
                DscLogger.warn(LogTag, "Beginner guide image unavailable page=${page.id}", error)
            }
        }
        return unavailable
    }

    private fun BeginnerGuidePage.imageUrl(): String =
        imagesBaseUrl.trimEnd('/') + "/" + image.trimStart('/')

    private data class DownloadedBeginnerManifest(
        val manifest: BeginnerGuideManifest,
        val json: String
    )

    companion object {
        const val CacheDirectoryName = "beginner_guide"
        private const val TimeoutMillis = 8_000
        private const val LogTag = "BeginnerGuide"
    }
}

interface BeginnerGuideStorage {
    fun readManifestJson(): String?
    fun writeManifestAtomically(json: String)
    fun imageFile(contentVersion: String, fileName: String): File
    fun writeImageAtomically(contentVersion: String, fileName: String, bytes: ByteArray)
}

class FileBeginnerGuideStorage(
    private val directory: File
) : BeginnerGuideStorage {
    private val manifestFile = File(directory, ManifestFileName)

    override fun readManifestJson(): String? =
        manifestFile.takeIf { it.exists() }?.readText(Charsets.UTF_8)

    override fun writeManifestAtomically(json: String) {
        if (!directory.exists()) directory.mkdirs()
        val tempFile = File(directory, "$ManifestFileName.tmp")
        tempFile.writeText(json, Charsets.UTF_8)
        moveAtomically(tempFile, manifestFile)
    }

    override fun imageFile(contentVersion: String, fileName: String): File =
        File(File(directory, "images_${contentVersion.cacheSafeName()}"), fileName)

    override fun writeImageAtomically(contentVersion: String, fileName: String, bytes: ByteArray) {
        val target = imageFile(contentVersion, fileName)
        target.parentFile?.mkdirs()
        val tempFile = File(target.parentFile, "$fileName.tmp")
        tempFile.writeBytes(bytes)
        moveAtomically(tempFile, target)
    }

    private fun moveAtomically(source: File, target: File) {
        runCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrElse {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private companion object {
        const val ManifestFileName = "manifest.json"
    }
}

interface BeginnerGuideHttpClient {
    fun get(url: String, timeoutMillis: Int): BeginnerGuideHttpResponse
    fun getBytes(url: String, timeoutMillis: Int): BeginnerGuideBytesResponse
}

data class BeginnerGuideHttpResponse(
    val statusCode: Int,
    val body: String
)

data class BeginnerGuideBytesResponse(
    val statusCode: Int,
    val bytes: ByteArray
)

class UrlConnectionBeginnerGuideHttpClient : BeginnerGuideHttpClient {
    override fun get(url: String, timeoutMillis: Int): BeginnerGuideHttpResponse {
        val connection = openConnection(url, timeoutMillis)
        return try {
            val statusCode = connection.responseCode
            val body = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            BeginnerGuideHttpResponse(statusCode, body)
        } catch (err: SocketTimeoutException) {
            throw BeginnerGuideRemoteError.Timeout(err.message)
        } catch (err: IOException) {
            throw BeginnerGuideRemoteError.Network(err.message)
        } finally {
            connection.disconnect()
        }
    }

    override fun getBytes(url: String, timeoutMillis: Int): BeginnerGuideBytesResponse {
        val connection = openConnection(url, timeoutMillis)
        return try {
            val statusCode = connection.responseCode
            val bytes = if (statusCode in 200..299) {
                connection.inputStream.use { it.readBytes() }
            } else {
                ByteArray(0)
            }
            BeginnerGuideBytesResponse(statusCode, bytes)
        } catch (err: SocketTimeoutException) {
            throw BeginnerGuideRemoteError.Timeout(err.message)
        } catch (err: IOException) {
            throw BeginnerGuideRemoteError.Network(err.message)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, timeoutMillis: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            useCaches = true
        }
}

sealed class BeginnerGuideRemoteError(message: String?) : RuntimeException(message) {
    class Timeout(message: String?) : BeginnerGuideRemoteError(message)
    class Network(message: String?) : BeginnerGuideRemoteError(message)
}

private fun BeginnerGuidePage.cacheFileName(): String =
    image.substringAfterLast('/').cacheSafeName().ifBlank { "$id.png" }

private fun String.cacheSafeName(): String =
    trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')

private fun Throwable.toBeginnerGuideReason(): String =
    when (this) {
        is BeginnerGuideRemoteError.Timeout -> "TIMEOUT"
        is BeginnerGuideRemoteError.Network -> "NETWORK"
        else -> message ?: "REMOTE_INTERNAL"
    }
