package it.droneskycheck.app.data.help

import android.content.Context
import it.droneskycheck.app.R
import it.droneskycheck.app.data.DscApiConfig
import it.droneskycheck.app.data.DscLogger
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Duration
import java.time.Instant

interface HelpManifestClient {
    suspend fun getCurrentManifest(): HelpManifest
    suspend fun checkForUpdatesIfDue(): HelpManifestUpdateResult
}

class InMemoryHelpManifestClient(
    private var manifest: HelpManifest = HelpManifest.empty()
) : HelpManifestClient {
    override suspend fun getCurrentManifest(): HelpManifest = manifest

    override suspend fun checkForUpdatesIfDue(): HelpManifestUpdateResult =
        HelpManifestUpdateResult.Skipped("No remote updater configured")

    fun setManifest(next: HelpManifest) {
        manifest = next
    }
}

class HelpRepository(
    private val storage: HelpManifestStorage,
    private val manifestUrl: String = DscApiConfig.HelpManifestUrl,
    private val httpClient: HelpManifestHttpClient = UrlConnectionHelpManifestHttpClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val checkInterval: Duration = DefaultCheckInterval
) : HelpManifestClient {
    @Volatile
    private var cachedManifest: HelpManifest? = null

    constructor(
        context: Context,
        manifestUrl: String = DscApiConfig.HelpManifestUrl,
        httpClient: HelpManifestHttpClient = UrlConnectionHelpManifestHttpClient(),
        clock: Clock = Clock.systemUTC(),
        checkInterval: Duration = DefaultCheckInterval
    ) : this(
        storage = FileHelpManifestStorage(
            directory = File(context.filesDir, CacheDirectoryName),
            embeddedJsonProvider = {
                context.resources.openRawResource(R.raw.help_manifest)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            }
        ),
        manifestUrl = manifestUrl,
        httpClient = httpClient,
        clock = clock,
        checkInterval = checkInterval
    )

    override suspend fun getCurrentManifest(): HelpManifest {
        cachedManifest?.let { return it }
        val loaded = loadBestAvailableManifest()
        cachedManifest = loaded
        return loaded
    }

    override suspend fun checkForUpdatesIfDue(): HelpManifestUpdateResult {
        val installed = getCurrentManifest()
        val metadata = storage.readMetadata()
        val lastChecked = metadata?.lastCheckedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val hasUsableManifest = installed.schemaVersion > 0 && installed.contentVersion > 0
        if (hasUsableManifest && lastChecked != null && Duration.between(lastChecked, clock.instant()) < checkInterval) {
            return HelpManifestUpdateResult.Skipped("Last check is still fresh")
        }

        storage.writeMetadata((metadata ?: HelpManifestMetadata()).copy(lastCheckedAt = clock.instant().toString()))
        return checkForUpdates()
    }

    suspend fun checkForUpdates(): HelpManifestUpdateResult {
        val installed = getCurrentManifest()
        return runCatching {
            val response = httpClient.get(
                url = manifestUrl,
                headers = mapOf("Accept" to "application/json"),
                timeoutMillis = TimeoutMillis
            )
            if (response.statusCode !in 200..299) {
                return HelpManifestUpdateResult.Failed("HTTP_${response.statusCode}")
            }
            installManifest(response.body, installed)
        }.getOrElse { error ->
            DscLogger.warn(LogTag, "Help: remote manifest update failed", error)
            HelpManifestUpdateResult.Failed(error.toHelpRemoteReason())
        }
    }

    fun installManifest(json: String, installed: HelpManifest? = cachedManifest): HelpManifestUpdateResult {
        val parsed = HelpManifestParser.parse(json)
        val manifest = parsed.manifest ?: run {
            DscLogger.warn(LogTag, "Help: downloaded manifest invalid ${parsed.warnings.firstOrNull()?.message.orEmpty()}")
            return HelpManifestUpdateResult.Failed("Invalid manifest")
        }
        parsed.warnings.take(5).forEach { warning ->
            DscLogger.debug(LogTag, "Help: manifest warning ${warning.code}: ${warning.message}")
        }
        val current = installed ?: loadBestAvailableManifest()
        if (current.contentVersion > 0 && manifest.contentVersion < current.contentVersion) {
            DscLogger.debug(
                LogTag,
                "Help: remote manifest older remote=${manifest.contentVersion} current=${current.contentVersion}"
            )
            return HelpManifestUpdateResult.Skipped("Remote manifest is older than cache")
        }
        if (current.contentVersion > 0 && manifest.contentVersion == current.contentVersion) {
            storage.writeMetadata(
                storage.readMetadata().orEmpty().copy(
                    schemaVersion = manifest.schemaVersion,
                    contentVersion = manifest.contentVersion,
                    updatedAt = manifest.updatedAt
                )
            )
            return HelpManifestUpdateResult.Skipped("Remote manifest is not newer")
        }

        storage.writeManifestAtomically(json)
        storage.writeMetadata(
            HelpManifestMetadata(
                schemaVersion = manifest.schemaVersion,
                contentVersion = manifest.contentVersion,
                updatedAt = manifest.updatedAt,
                installedAt = clock.instant().toString(),
                lastCheckedAt = storage.readMetadata()?.lastCheckedAt
            )
        )
        cachedManifest = manifest
        DscLogger.debug(LogTag, "Help: manifest v${manifest.contentVersion} installed")
        return HelpManifestUpdateResult.Installed(manifest.contentVersion)
    }

    private fun loadBestAvailableManifest(): HelpManifest {
        val cached = storage.readManifestJson()?.let { json ->
            loadValidatedManifest(json, source = "cached")
                ?: DscLogger.warn(LogTag, "Help: cached manifest invalid, trying embedded").let { null }
        }
        val embedded = loadValidatedManifest(storage.readEmbeddedManifestJson(), source = "embedded")
        if (cached != null && embedded != null) {
            return if (embedded.contentVersion > cached.contentVersion) embedded else cached
        }
        cached?.let { return it }
        embedded?.let { return it }
        DscLogger.warn(LogTag, "Help: no valid manifest available")
        return HelpManifest.empty()
    }

    private fun loadValidatedManifest(json: String, source: String): HelpManifest? {
        val parsed = HelpManifestParser.parse(json)
        val manifest = parsed.manifest ?: run {
            DscLogger.warn(LogTag, "Help: $source manifest invalid ${parsed.warnings.firstOrNull()?.message.orEmpty()}")
            return null
        }
        DscLogger.debug(
            LogTag,
            "Help: using $source manifest content=${manifest.contentVersion} topics=${manifest.topics.size}"
        )
        return manifest
    }

    companion object {
        const val CacheDirectoryName = "help"
        const val ManifestCacheFileName = "help_manifest.json"
        const val MetadataFileName = "help_manifest_metadata.json"
        val DefaultCheckInterval: Duration = Duration.ofHours(24)
        private const val TimeoutMillis = 8_000
        private const val LogTag = "Help"
    }
}

sealed class HelpManifestUpdateResult {
    data class Installed(val contentVersion: Int) : HelpManifestUpdateResult()
    data class Skipped(val reason: String) : HelpManifestUpdateResult()
    data class Failed(val reason: String) : HelpManifestUpdateResult()
}

data class HelpManifestMetadata(
    val schemaVersion: Int = 0,
    val contentVersion: Int = 0,
    val updatedAt: String? = null,
    val installedAt: String? = null,
    val lastCheckedAt: String? = null
)

interface HelpManifestStorage {
    fun readManifestJson(): String?
    fun readEmbeddedManifestJson(): String
    fun writeManifestAtomically(json: String)
    fun readMetadata(): HelpManifestMetadata?
    fun writeMetadata(metadata: HelpManifestMetadata)
}

class FileHelpManifestStorage(
    private val directory: File,
    private val embeddedJsonProvider: () -> String
) : HelpManifestStorage {
    private val manifestFile = File(directory, HelpRepository.ManifestCacheFileName)
    private val metadataFile = File(directory, HelpRepository.MetadataFileName)

    override fun readManifestJson(): String? =
        manifestFile.takeIf { it.exists() }?.readText(Charsets.UTF_8)

    override fun readEmbeddedManifestJson(): String =
        embeddedJsonProvider()

    override fun writeManifestAtomically(json: String) {
        if (!directory.exists()) directory.mkdirs()
        val tempFile = File(directory, "${HelpRepository.ManifestCacheFileName}.tmp")
        tempFile.writeText(json, Charsets.UTF_8)
        runCatching {
            Files.move(
                tempFile.toPath(),
                manifestFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrElse {
            Files.move(
                tempFile.toPath(),
                manifestFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    override fun readMetadata(): HelpManifestMetadata? =
        metadataFile.takeIf { it.exists() }
            ?.readText(Charsets.UTF_8)
            ?.let { runCatching { parseHelpManifestMetadata(it) }.getOrNull() }

    override fun writeMetadata(metadata: HelpManifestMetadata) {
        if (!directory.exists()) directory.mkdirs()
        metadataFile.writeText(metadata.toJsonString(), Charsets.UTF_8)
    }
}

interface HelpManifestHttpClient {
    fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int
    ): HelpManifestHttpResponse
}

data class HelpManifestHttpResponse(
    val statusCode: Int,
    val body: String
)

class UrlConnectionHelpManifestHttpClient : HelpManifestHttpClient {
    override fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int
    ): HelpManifestHttpResponse {
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
            HelpManifestHttpResponse(statusCode, body)
        } catch (err: SocketTimeoutException) {
            throw HelpRemoteError.Timeout(err.message)
        } catch (err: IOException) {
            throw HelpRemoteError.Network(err.message)
        } finally {
            connection.disconnect()
        }
    }
}

sealed class HelpRemoteError(message: String?) : RuntimeException(message) {
    class Timeout(message: String?) : HelpRemoteError(message)
    class Network(message: String?) : HelpRemoteError(message)
}

private fun parseHelpManifestMetadata(json: String): HelpManifestMetadata {
    val root = JSONObject(json.ifBlank { "{}" })
    return HelpManifestMetadata(
        schemaVersion = root.optInt("schemaVersion", 0),
        contentVersion = root.optInt("contentVersion", 0),
        updatedAt = root.optStringOrNull("updatedAt"),
        installedAt = root.optStringOrNull("installedAt"),
        lastCheckedAt = root.optStringOrNull("lastCheckedAt")
    )
}

private fun HelpManifestMetadata.toJsonString(): String =
    JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("contentVersion", contentVersion)
        .put("updatedAt", updatedAt)
        .put("installedAt", installedAt)
        .put("lastCheckedAt", lastCheckedAt)
        .toString(2)

private fun HelpManifestMetadata?.orEmpty(): HelpManifestMetadata =
    this ?: HelpManifestMetadata()

private fun JSONObject.optStringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).trim().takeIf { it.isNotBlank() && it != "null" }

private fun Throwable.toHelpRemoteReason(): String =
    when (this) {
        is HelpRemoteError.Timeout -> "TIMEOUT"
        is HelpRemoteError.Network -> "NETWORK"
        else -> "REMOTE_INTERNAL"
    }
