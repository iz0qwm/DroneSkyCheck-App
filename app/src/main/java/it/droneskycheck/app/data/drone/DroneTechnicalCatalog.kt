package it.droneskycheck.app.data.drone

import android.content.Context
import it.droneskycheck.app.data.DscApiConfig
import it.droneskycheck.app.data.DscLogger
import it.droneskycheck.app.data.LocalDrone
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.min

data class DroneTechnicalCatalog(
    val version: Int,
    val catalogVersion: Int,
    val updatedAt: String?,
    val drones: List<DroneTechnicalCatalogEntry>
) {
    val schemaVersion: Int
        get() = version
}

data class DroneTechnicalCatalogEntry(
    val manufacturer: String,
    val model: String,
    val segment: DroneCatalogSegment,
    val aliases: List<String>,
    val windResistance: DroneWindResistance,
    val operatingTemperatureMinC: Double?,
    val operatingTemperatureMaxC: Double?,
    val operatingTemperatureNotes: String?,
    val ingressProtectionRating: String?,
    val precipitationCapability: DronePrecipitationCapability,
    val source: DroneTechnicalCatalogSource
) {
    val displayName: String
        get() = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ")

    val maxWindResistanceMs: Double?
        get() = resolveOperationalWindResistance(windResistance).valueMs
}

data class DroneTechnicalCatalogSource(
    val type: DroneCapabilitySource,
    val name: String?,
    val reference: String?,
    val verifiedAt: String?
)

data class DroneWindResistance(
    val generalMs: Double? = null,
    val generalMinMs: Double? = null,
    val generalMaxMs: Double? = null,
    val takeoffLandingMs: Double? = null,
    val cruiseMs: Double? = null,
    val notes: String? = null
)

data class OperationalWindResistance(
    val valueMs: Double?,
    val basis: OperationalWindResistanceBasis
)

enum class OperationalWindResistanceBasis {
    TAKEOFF_LANDING,
    GENERAL,
    GENERAL_RANGE_MINIMUM,
    CRUISE_ONLY,
    UNKNOWN
}

enum class DroneCatalogSegment {
    CONSUMER,
    ENTERPRISE,
    UNKNOWN
}

data class DroneCatalogMatchResult(
    val status: DroneCatalogMatchStatus,
    val matchedDrone: DroneTechnicalCatalogEntry? = null,
    val matchType: DroneCatalogMatchType? = null,
    val suggestions: List<DroneTechnicalCatalogEntry> = emptyList()
)

enum class DroneCatalogMatchStatus {
    EXACT,
    ALIAS,
    SUGGESTED,
    NOT_FOUND,
    AMBIGUOUS
}

enum class DroneCatalogMatchType {
    EXACT,
    ALIAS,
    SUGGESTED
}

interface DroneTechnicalCatalogClient {
    suspend fun resolver(): DroneTechnicalCatalogResolver
    suspend fun getCurrentCatalog(): DroneTechnicalCatalog =
        resolver().catalog

    suspend fun checkForUpdatesIfDue(): DroneCatalogUpdateResult =
        DroneCatalogUpdateResult.Skipped("No remote updater configured")
}

class DroneTechnicalCatalogRepository(
    private val storage: DroneCatalogStorage,
    private val manifestUrl: String = DscApiConfig.DroneCatalogManifestUrl,
    private val httpClient: DroneCatalogHttpClient = UrlConnectionDroneCatalogHttpClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val checkInterval: Duration = DefaultCheckInterval
) : DroneTechnicalCatalogClient {
    @Volatile
    private var cachedCatalog: DroneTechnicalCatalog? = null

    constructor(
        context: Context,
        manifestUrl: String = DscApiConfig.DroneCatalogManifestUrl,
        httpClient: DroneCatalogHttpClient = UrlConnectionDroneCatalogHttpClient(),
        clock: Clock = Clock.systemUTC(),
        checkInterval: Duration = DefaultCheckInterval
    ) : this(
        storage = FileDroneCatalogStorage(
            directory = File(context.filesDir, CacheDirectoryName),
            seedCatalogJsonProvider = {
                context.assets.open(CatalogAssetName)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            }
        ),
        manifestUrl = manifestUrl,
        httpClient = httpClient,
        clock = clock,
        checkInterval = checkInterval
    )

    override suspend fun resolver(): DroneTechnicalCatalogResolver {
        return DroneTechnicalCatalogResolver(getCurrentCatalog())
    }

    override suspend fun getCurrentCatalog(): DroneTechnicalCatalog {
        cachedCatalog?.let { return it }
        val loaded = loadBestAvailableCatalog()
        cachedCatalog = loaded
        return loaded
    }

    override suspend fun checkForUpdatesIfDue(): DroneCatalogUpdateResult {
        val installed = getCurrentCatalog()
        val metadata = storage.readMetadata()
        val lastChecked = metadata?.lastCheckedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val hasUsableCatalog = installed.schemaVersion > 0 &&
            installed.catalogVersion > 0 &&
            installed.drones.isNotEmpty()
        if (hasUsableCatalog && lastChecked != null && Duration.between(lastChecked, clock.instant()) < checkInterval) {
            DscLogger.debug(
                LogTag,
                "DroneCatalog: skip remote check, lastCheckedAt=$lastChecked " +
                    "installedSchema=${installed.schemaVersion} installedCatalog=${installed.catalogVersion}"
            )
            return DroneCatalogUpdateResult.Skipped("Last check is still fresh")
        }

        DscLogger.debug(
            LogTag,
            "DroneCatalog: remote check due installedSchema=${installed.schemaVersion} " +
                "installedCatalog=${installed.catalogVersion} drones=${installed.drones.size} " +
                "hasUsableCatalog=$hasUsableCatalog lastCheckedAt=${metadata?.lastCheckedAt}"
        )
        storage.writeMetadata((metadata ?: DroneCatalogMetadata()).copy(lastCheckedAt = clock.instant().toString()))
        return checkForUpdates()
    }

    suspend fun checkForUpdates(): DroneCatalogUpdateResult {
        val installed = getCurrentCatalog()
        DscLogger.debug(
            LogTag,
            "DroneCatalog: checking remote manifest url=$manifestUrl installedCatalog=${installed.catalogVersion}"
        )
        val result = DroneTechnicalCatalogRemoteUpdater(
            manifestUrl = manifestUrl,
            httpClient = httpClient
        ).checkForUpdate(installed)

        return when (result) {
            is DroneCatalogRemoteUpdateCheck.NoUpdate -> {
                DscLogger.debug(LogTag, "DroneCatalog: cached catalog v${installed.catalogVersion} is current")
                storage.writeMetadata(
                    storage.readMetadata().orEmpty().copy(
                        schemaVersion = installed.schemaVersion,
                        catalogVersion = installed.catalogVersion
                    )
                )
                DroneCatalogUpdateResult.Skipped("Remote catalog is not newer")
            }
            is DroneCatalogRemoteUpdateCheck.UpdateAvailable -> installCatalog(
                json = result.catalogJson,
                manifest = result.manifest
            )
            is DroneCatalogRemoteUpdateCheck.Failed -> {
                DscLogger.warn(LogTag, "DroneCatalog: update failed, keeping v${installed.catalogVersion}: ${result.reason}", result.error)
                DroneCatalogUpdateResult.Failed(result.reason)
            }
        }
    }

    fun installCatalog(json: String, manifest: DroneCatalogManifest? = null): DroneCatalogUpdateResult {
        val parsed = try {
            parseDroneTechnicalCatalog(json)
        } catch (err: Exception) {
            DscLogger.warn(LogTag, "DroneCatalog: downloaded catalog JSON invalid, keeping previous catalog", err)
            return DroneCatalogUpdateResult.Failed("Invalid catalog JSON")
        }
        val errors = validateDroneTechnicalCatalog(parsed)
        if (errors.isNotEmpty()) {
            DscLogger.warn(LogTag, "DroneCatalog: downloaded catalog validation failed ${errors.first()}")
            return DroneCatalogUpdateResult.Failed("Invalid catalog: ${errors.first()}")
        }
        if (parsed.schemaVersion !in SupportedSchemaVersions) {
            DscLogger.warn(LogTag, "DroneCatalog: Catalog schema version unsupported ${parsed.schemaVersion}")
            return DroneCatalogUpdateResult.Failed("Catalog schema version unsupported")
        }
        if (manifest != null) {
            if (manifest.schemaVersion != parsed.schemaVersion || manifest.catalogVersion != parsed.catalogVersion) {
                return DroneCatalogUpdateResult.Failed("Manifest/catalog version mismatch")
            }
            if (!sha256Hex(json).equals(manifest.sha256, ignoreCase = true)) {
                DscLogger.warn(LogTag, "DroneCatalog: checksum mismatch, keeping previous catalog")
                return DroneCatalogUpdateResult.Failed("SHA-256 mismatch")
            }
        }

        storage.writeCatalogAtomically(json)
        storage.writeMetadata(
            DroneCatalogMetadata(
                schemaVersion = parsed.schemaVersion,
                catalogVersion = parsed.catalogVersion,
                updatedAt = parsed.updatedAt,
                installedAt = clock.instant().toString(),
                lastCheckedAt = storage.readMetadata()?.lastCheckedAt
            )
        )
        cachedCatalog = parsed
        DscLogger.debug(LogTag, "DroneCatalog: catalog v${parsed.catalogVersion} installed")
        return DroneCatalogUpdateResult.Installed(parsed.catalogVersion)
    }

    private fun loadBestAvailableCatalog(): DroneTechnicalCatalog {
        storage.readCatalogJson()?.let { json ->
            loadValidatedCatalog(json, source = "cached")?.let { return it }
                ?: DscLogger.warn(LogTag, "DroneCatalog: cached catalog invalid, trying embedded seed")
        }
        loadValidatedCatalog(storage.readSeedCatalogJson(), source = "embedded seed")?.let { return it }
        DscLogger.warn(LogTag, "DroneCatalog: no valid catalog available, using empty resolver")
        return DroneTechnicalCatalog(version = 0, catalogVersion = 0, updatedAt = null, drones = emptyList())
    }

    private fun loadValidatedCatalog(json: String, source: String): DroneTechnicalCatalog? {
        val catalog = runCatching { parseDroneTechnicalCatalog(json) }
            .getOrElse { error ->
                DscLogger.warn(LogTag, "DroneCatalog: $source catalog parse failed", error)
                return null
            }
        val validationErrors = validateDroneTechnicalCatalog(catalog)
        if (catalog.schemaVersion !in SupportedSchemaVersions || validationErrors.isNotEmpty()) {
            DscLogger.warn(
                LogTag,
                "DroneCatalog: $source catalog invalid schema=${catalog.schemaVersion} " +
                    "catalog=${catalog.catalogVersion} errors=${validationErrors.take(3).joinToString()}"
            )
            return null
        }
        DscLogger.debug(
            LogTag,
            "DroneCatalog: using $source catalog schema=${catalog.schemaVersion} " +
                "catalog=${catalog.catalogVersion} drones=${catalog.drones.size} updatedAt=${catalog.updatedAt}"
        )
        return catalog
    }

    companion object {
        const val CatalogAssetName = "drone_technical_catalog.json"
        const val CacheDirectoryName = "drone_catalog"
        const val CatalogCacheFileName = "drone_technical_catalog.json"
        const val MetadataFileName = "drone_catalog_metadata.json"
        val DefaultCheckInterval: Duration = Duration.ofHours(24)
        val SupportedSchemaVersions: Set<Int> = setOf(2)
        private const val LogTag = "DroneCatalog"
    }
}

sealed class DroneCatalogUpdateResult {
    data class Installed(val catalogVersion: Int) : DroneCatalogUpdateResult()
    data class Skipped(val reason: String) : DroneCatalogUpdateResult()
    data class Failed(val reason: String) : DroneCatalogUpdateResult()
}

data class DroneCatalogManifest(
    val schemaVersion: Int,
    val catalogVersion: Int,
    val updatedAt: String?,
    val catalogUrl: String,
    val sha256: String
)

data class DroneCatalogMetadata(
    val schemaVersion: Int = 0,
    val catalogVersion: Int = 0,
    val updatedAt: String? = null,
    val installedAt: String? = null,
    val lastCheckedAt: String? = null
)

interface DroneCatalogStorage {
    fun readCatalogJson(): String?
    fun readSeedCatalogJson(): String
    fun writeCatalogAtomically(json: String)
    fun readMetadata(): DroneCatalogMetadata?
    fun writeMetadata(metadata: DroneCatalogMetadata)
}

class FileDroneCatalogStorage(
    private val directory: File,
    private val seedCatalogJsonProvider: () -> String
) : DroneCatalogStorage {
    private val catalogFile = File(directory, DroneTechnicalCatalogRepository.CatalogCacheFileName)
    private val metadataFile = File(directory, DroneTechnicalCatalogRepository.MetadataFileName)

    override fun readCatalogJson(): String? =
        catalogFile.takeIf { it.exists() }?.readText(Charsets.UTF_8)

    override fun readSeedCatalogJson(): String =
        seedCatalogJsonProvider()

    override fun writeCatalogAtomically(json: String) {
        if (!directory.exists()) directory.mkdirs()
        val tempFile = File(directory, "${DroneTechnicalCatalogRepository.CatalogCacheFileName}.tmp")
        tempFile.writeText(json, Charsets.UTF_8)
        runCatching {
            Files.move(
                tempFile.toPath(),
                catalogFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrElse {
            Files.move(
                tempFile.toPath(),
                catalogFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    override fun readMetadata(): DroneCatalogMetadata? =
        metadataFile.takeIf { it.exists() }
            ?.readText(Charsets.UTF_8)
            ?.let { runCatching { parseDroneCatalogMetadata(it) }.getOrNull() }

    override fun writeMetadata(metadata: DroneCatalogMetadata) {
        if (!directory.exists()) directory.mkdirs()
        metadataFile.writeText(metadata.toJsonString(), Charsets.UTF_8)
    }
}

interface DroneCatalogHttpClient {
    fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int
    ): DroneCatalogHttpResponse
}

data class DroneCatalogHttpResponse(
    val statusCode: Int,
    val body: String
)

class UrlConnectionDroneCatalogHttpClient : DroneCatalogHttpClient {
    override fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int
    ): DroneCatalogHttpResponse {
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
            DroneCatalogHttpResponse(statusCode, body)
        } catch (err: SocketTimeoutException) {
            throw DroneCatalogRemoteError.Timeout(err.message)
        } catch (err: IOException) {
            throw DroneCatalogRemoteError.Network(err.message)
        } finally {
            connection.disconnect()
        }
    }
}

private class DroneTechnicalCatalogRemoteUpdater(
    private val manifestUrl: String,
    private val httpClient: DroneCatalogHttpClient
) {
    fun checkForUpdate(installed: DroneTechnicalCatalog): DroneCatalogRemoteUpdateCheck =
        runCatching {
            val manifestResponse = httpClient.get(
                url = manifestUrl,
                headers = mapOf("Accept" to "application/json"),
                timeoutMillis = TimeoutMillis
            )
            if (manifestResponse.statusCode !in 200..299) {
                throw DroneCatalogRemoteError.HttpError(manifestResponse.statusCode)
            }
            DscLogger.debug(
                LogTag,
                "DroneCatalog: manifest HTTP ${manifestResponse.statusCode} bytes=${manifestResponse.body.length}"
            )
            val manifest = parseDroneCatalogManifest(manifestResponse.body)
            validateManifest(manifest)
            DscLogger.debug(
                LogTag,
                "DroneCatalog: manifest schema=${manifest.schemaVersion} catalog=${manifest.catalogVersion} " +
                    "updatedAt=${manifest.updatedAt} catalogUrl=${manifest.catalogUrl}"
            )
            if (manifest.schemaVersion !in DroneTechnicalCatalogRepository.SupportedSchemaVersions) {
                DscLogger.warn(LogTag, "DroneCatalog: remote schema unsupported, keeping v${installed.catalogVersion}")
                return DroneCatalogRemoteUpdateCheck.Failed("Catalog schema version unsupported")
            }
            val installedIsUsable = installed.schemaVersion > 0 &&
                installed.catalogVersion > 0 &&
                installed.drones.isNotEmpty()
            if (installedIsUsable && manifest.catalogVersion <= installed.catalogVersion) {
                DscLogger.debug(
                    LogTag,
                    "DroneCatalog: no remote update remoteCatalog=${manifest.catalogVersion} " +
                        "installedCatalog=${installed.catalogVersion}"
                )
                return DroneCatalogRemoteUpdateCheck.NoUpdate
            }

            DscLogger.debug(LogTag, "DroneCatalog: remote catalog v${manifest.catalogVersion} available")
            val catalogResponse = httpClient.get(
                url = manifest.catalogUrl,
                headers = mapOf("Accept" to "application/json"),
                timeoutMillis = TimeoutMillis
            )
            if (catalogResponse.statusCode !in 200..299) {
                throw DroneCatalogRemoteError.HttpError(catalogResponse.statusCode)
            }
            val actualSha = sha256Hex(catalogResponse.body)
            DscLogger.debug(
                LogTag,
                "DroneCatalog: catalog HTTP ${catalogResponse.statusCode} bytes=${catalogResponse.body.length} " +
                    "sha=$actualSha expectedSha=${manifest.sha256}"
            )
            if (!actualSha.equals(manifest.sha256, ignoreCase = true)) {
                return DroneCatalogRemoteUpdateCheck.Failed("SHA-256 mismatch")
            }
            val catalog = parseDroneTechnicalCatalog(catalogResponse.body)
            if (catalog.schemaVersion != manifest.schemaVersion || catalog.catalogVersion != manifest.catalogVersion) {
                DscLogger.warn(
                    LogTag,
                    "DroneCatalog: manifest/catalog mismatch manifestSchema=${manifest.schemaVersion} " +
                        "manifestCatalog=${manifest.catalogVersion} downloadedSchema=${catalog.schemaVersion} " +
                        "downloadedCatalog=${catalog.catalogVersion}"
                )
                return DroneCatalogRemoteUpdateCheck.Failed("Manifest/catalog version mismatch")
            }
            val validationErrors = validateDroneTechnicalCatalog(catalog)
            if (validationErrors.isNotEmpty()) {
                DscLogger.warn(
                    LogTag,
                    "DroneCatalog: downloaded catalog validation errors=${validationErrors.take(3).joinToString()}"
                )
                return DroneCatalogRemoteUpdateCheck.Failed("Invalid catalog: ${validationErrors.first()}")
            }
            DscLogger.debug(
                LogTag,
                "DroneCatalog: downloaded catalog valid schema=${catalog.schemaVersion} " +
                    "catalog=${catalog.catalogVersion} drones=${catalog.drones.size}"
            )
            DroneCatalogRemoteUpdateCheck.UpdateAvailable(manifest, catalogResponse.body)
        }.getOrElse { error ->
            DroneCatalogRemoteUpdateCheck.Failed(error.toDroneCatalogRemoteReason(), error)
        }

    private companion object {
        const val LogTag = "DroneCatalog"
        const val TimeoutMillis = 8_000
    }
}

private sealed class DroneCatalogRemoteUpdateCheck {
    object NoUpdate : DroneCatalogRemoteUpdateCheck()
    data class UpdateAvailable(val manifest: DroneCatalogManifest, val catalogJson: String) : DroneCatalogRemoteUpdateCheck()
    data class Failed(val reason: String, val error: Throwable? = null) : DroneCatalogRemoteUpdateCheck()
}

sealed class DroneCatalogRemoteError(message: String?) : Exception(message) {
    data class HttpError(val statusCode: Int) : DroneCatalogRemoteError("Drone catalog HTTP $statusCode")
    data class Timeout(override val message: String?) : DroneCatalogRemoteError(message ?: "Drone catalog timeout")
    data class Network(override val message: String?) : DroneCatalogRemoteError(message ?: "Drone catalog network error")
    data class InvalidJson(override val message: String?) : DroneCatalogRemoteError(message ?: "Invalid drone catalog JSON")
    data class InvalidManifest(override val message: String?) : DroneCatalogRemoteError(message ?: "Invalid drone catalog manifest")
}

class InMemoryDroneTechnicalCatalogClient(
    private val resolver: DroneTechnicalCatalogResolver = DroneTechnicalCatalogResolver.empty()
) : DroneTechnicalCatalogClient {
    override suspend fun resolver(): DroneTechnicalCatalogResolver = resolver
}

class DroneTechnicalCatalogResolver(
    val catalog: DroneTechnicalCatalog
) {
    private val indexed = catalog.drones.map { entry ->
        IndexedDroneCatalogEntry(
            entry = entry,
            manufacturerKey = normalizeManufacturer(entry.manufacturer),
            modelKey = normalizeDroneModel(entry.model, entry.manufacturer),
            aliasKeys = entry.aliases.map { normalizeDroneModel(it, entry.manufacturer) }.distinct()
        )
    }

    fun resolve(manufacturer: String, model: String): DroneCatalogMatchResult {
        val manufacturerKey = normalizeManufacturer(manufacturer)
        val modelKey = normalizeDroneModel(model, manufacturer)
        if (manufacturerKey.isBlank() || modelKey.length < MinimumSafeModelLength) {
            return notFoundOrSuggested(manufacturerKey, modelKey)
        }

        val manufacturerMatches = indexed.filter { it.manufacturerKey == manufacturerKey }
        if (manufacturerMatches.isEmpty()) return DroneCatalogMatchResult(DroneCatalogMatchStatus.NOT_FOUND)

        val exact = manufacturerMatches.filter { it.modelKey == modelKey }
        if (exact.size == 1) {
            return DroneCatalogMatchResult(
                status = DroneCatalogMatchStatus.EXACT,
                matchedDrone = exact.single().entry,
                matchType = DroneCatalogMatchType.EXACT
            )
        }
        if (exact.size > 1) {
            return DroneCatalogMatchResult(
                status = DroneCatalogMatchStatus.AMBIGUOUS,
                suggestions = exact.map { it.entry }
            )
        }

        val alias = manufacturerMatches.filter { modelKey in it.aliasKeys }
        if (alias.size == 1) {
            return DroneCatalogMatchResult(
                status = DroneCatalogMatchStatus.ALIAS,
                matchedDrone = alias.single().entry,
                matchType = DroneCatalogMatchType.ALIAS
            )
        }
        if (alias.size > 1) {
            return DroneCatalogMatchResult(
                status = DroneCatalogMatchStatus.AMBIGUOUS,
                suggestions = alias.map { it.entry }
            )
        }

        return notFoundOrSuggested(manufacturerKey, modelKey)
    }

    fun capabilitiesFor(drone: LocalDrone): Pair<DroneOperationalCapabilities, DroneCatalogMatchResult> {
        val match = resolve(drone.manufacturer, drone.model)
        return capabilitiesFor(drone, match) to match
    }

    fun capabilitiesFor(
        drone: LocalDrone,
        match: DroneCatalogMatchResult
    ): DroneOperationalCapabilities {
        val catalogDrone = match.matchedDrone.takeIf {
            match.status == DroneCatalogMatchStatus.EXACT || match.status == DroneCatalogMatchStatus.ALIAS
        }
        val manualWind = drone.manualMaxWindResistanceMs
        val resolvedWind = catalogDrone?.windResistance?.let(::resolveOperationalWindResistance)
            ?: OperationalWindResistance(null, OperationalWindResistanceBasis.UNKNOWN)
        val effectiveWindResistance = manualWind ?: resolvedWind.valueMs
        return DroneOperationalCapabilities(
            droneId = drone.id,
            displayName = drone.displayName,
            manufacturer = drone.manufacturer,
            model = drone.model,
            massGrams = drone.weight,
            euClass = drone.classLabel.takeIf { it.isNotBlank() },
            maxWindResistanceMs = effectiveWindResistance,
            windResistance = catalogDrone?.windResistance ?: DroneWindResistance(),
            operationalWindResistanceBasis = if (manualWind != null) {
                OperationalWindResistanceBasis.GENERAL
            } else {
                resolvedWind.basis
            },
            minOperatingTemperatureC = catalogDrone?.operatingTemperatureMinC,
            maxOperatingTemperatureC = catalogDrone?.operatingTemperatureMaxC,
            operatingTemperatureNotes = catalogDrone?.operatingTemperatureNotes,
            ingressProtectionRating = catalogDrone?.ingressProtectionRating,
            weatherProtection = DroneWeatherProtection.UNKNOWN,
            precipitationCapability = catalogDrone?.precipitationCapability ?: DronePrecipitationCapability.UNKNOWN,
            massSource = drone.weight?.let { DroneCapabilitySource.USER_PROVIDED } ?: DroneCapabilitySource.UNKNOWN,
            euClassSource = drone.classLabel.takeIf { it.isNotBlank() }?.let { DroneCapabilitySource.USER_PROVIDED }
                ?: DroneCapabilitySource.UNKNOWN,
            windResistanceSource = when {
                manualWind != null -> DroneCapabilitySource.USER_PROVIDED
                effectiveWindResistance != null -> catalogDrone?.source?.type ?: DroneCapabilitySource.UNKNOWN
                else -> DroneCapabilitySource.UNKNOWN
            },
            temperatureRangeSource = if (
                catalogDrone?.operatingTemperatureMinC != null &&
                catalogDrone.operatingTemperatureMaxC != null
            ) {
                catalogDrone.source.type
            } else {
                DroneCapabilitySource.UNKNOWN
            },
            precipitationSource = catalogDrone?.source?.type ?: DroneCapabilitySource.UNKNOWN,
            technicalProfileName = catalogDrone?.displayName,
            technicalProfileSourceName = catalogDrone?.source?.name,
            technicalProfileSourceReference = catalogDrone?.source?.reference,
            technicalProfileMatchStatus = match.status.name,
            technicalCatalogSchemaVersion = catalog.schemaVersion,
            technicalCatalogVersion = catalog.catalogVersion,
            technicalCatalogUpdatedAt = catalog.updatedAt,
            manualWindResistanceOverride = manualWind != null
        )
    }

    private fun notFoundOrSuggested(
        manufacturerKey: String,
        modelKey: String
    ): DroneCatalogMatchResult {
        if (manufacturerKey.isBlank() || modelKey.length < MinimumSuggestionModelLength) {
            return DroneCatalogMatchResult(DroneCatalogMatchStatus.NOT_FOUND)
        }
        val suggestions = indexed
            .filter { it.manufacturerKey == manufacturerKey }
            .mapNotNull { indexed ->
                val distance = levenshteinDistance(modelKey, indexed.modelKey)
                val prefix = indexed.modelKey.startsWith(modelKey) || modelKey.startsWith(indexed.modelKey)
                if (distance <= 2 || prefix) indexed.entry else null
            }
            .distinct()
            .take(MaxSuggestions)
        return if (suggestions.isEmpty()) {
            DroneCatalogMatchResult(DroneCatalogMatchStatus.NOT_FOUND)
        } else {
            DroneCatalogMatchResult(
                status = DroneCatalogMatchStatus.SUGGESTED,
                suggestions = suggestions
            )
        }
    }

    companion object {
        fun empty(): DroneTechnicalCatalogResolver =
            DroneTechnicalCatalogResolver(
                DroneTechnicalCatalog(version = 0, catalogVersion = 0, updatedAt = null, drones = emptyList())
            )

        private const val MinimumSafeModelLength = 3
        private const val MinimumSuggestionModelLength = 4
        private const val MaxSuggestions = 3
    }
}

fun resolveOperationalWindResistance(windResistance: DroneWindResistance): OperationalWindResistance =
    when {
        windResistance.takeoffLandingMs != null -> OperationalWindResistance(
            windResistance.takeoffLandingMs,
            OperationalWindResistanceBasis.TAKEOFF_LANDING
        )
        windResistance.generalMs != null -> OperationalWindResistance(
            windResistance.generalMs,
            OperationalWindResistanceBasis.GENERAL
        )
        windResistance.generalMinMs != null -> OperationalWindResistance(
            windResistance.generalMinMs,
            OperationalWindResistanceBasis.GENERAL_RANGE_MINIMUM
        )
        windResistance.cruiseMs != null -> OperationalWindResistance(
            windResistance.cruiseMs,
            OperationalWindResistanceBasis.CRUISE_ONLY
        )
        else -> OperationalWindResistance(null, OperationalWindResistanceBasis.UNKNOWN)
    }

fun parseDroneTechnicalCatalog(json: String): DroneTechnicalCatalog {
    val root = try {
        JSONObject(json.ifBlank { "{}" })
    } catch (err: JSONException) {
        throw DroneCatalogRemoteError.InvalidJson(err.message)
    }
    val schemaVersion = root.optInt("schemaVersion", root.optInt("version", 0))
    return DroneTechnicalCatalog(
        version = schemaVersion,
        catalogVersion = root.optInt("catalogVersion", root.optInt("dataVersion", 1)),
        updatedAt = root.optStringOrNull("updatedAt"),
        drones = root.optJSONArray("drones").toObjectList { it.toDroneTechnicalCatalogEntry() }
    )
}

fun parseDroneCatalogManifest(json: String): DroneCatalogManifest {
    val root = try {
        JSONObject(json.ifBlank { "{}" })
    } catch (err: JSONException) {
        throw DroneCatalogRemoteError.InvalidJson(err.message)
    }
    return DroneCatalogManifest(
        schemaVersion = root.optInt("schemaVersion", 0),
        catalogVersion = root.optInt("catalogVersion", 0),
        updatedAt = root.optStringOrNull("updatedAt"),
        catalogUrl = root.optString("catalogUrl"),
        sha256 = root.optString("sha256")
    )
}

fun validateManifest(manifest: DroneCatalogManifest) {
    if (manifest.schemaVersion <= 0) {
        throw DroneCatalogRemoteError.InvalidManifest("Missing schemaVersion")
    }
    if (manifest.catalogVersion <= 0) {
        throw DroneCatalogRemoteError.InvalidManifest("Missing catalogVersion")
    }
    manifest.updatedAt?.let {
        runCatching { OffsetDateTime.parse(it) }
            .getOrElse { throw DroneCatalogRemoteError.InvalidManifest("Invalid updatedAt") }
    }
    if (!manifest.sha256.matches(Regex("^[A-Fa-f0-9]{64}$"))) {
        throw DroneCatalogRemoteError.InvalidManifest("Invalid sha256")
    }
    validateDroneCatalogUrl(manifest.catalogUrl)
}

fun validateDroneCatalogUrl(value: String) {
    val uri = URI(value)
    if (uri.scheme != "https") {
        throw DroneCatalogRemoteError.InvalidManifest("Catalog URL must be HTTPS")
    }
    if (uri.host != DscApiConfig.KwosAllowedHost) {
        throw DroneCatalogRemoteError.InvalidManifest("Catalog URL host is not allowed")
    }
    val path = uri.path.orEmpty()
    if (!path.startsWith(DscApiConfig.KwosDroneSkyCheckPathPrefix)) {
        throw DroneCatalogRemoteError.InvalidManifest("Catalog URL path is not allowed")
    }
}

fun validateDroneTechnicalCatalog(catalog: DroneTechnicalCatalog): List<String> {
    val errors = mutableListOf<String>()
    if (catalog.schemaVersion !in DroneTechnicalCatalogRepository.SupportedSchemaVersions) {
        errors += "unsupported schemaVersion ${catalog.schemaVersion}"
    }
    if (catalog.catalogVersion <= 0) {
        errors += "catalogVersion must be positive"
    }
    if (catalog.updatedAt != null && !catalog.updatedAt.isCatalogDateOrDateTime()) {
        errors += "updatedAt is invalid"
    }
    val canonicalModels = mutableSetOf<String>()
    val aliasMap = mutableMapOf<String, MutableSet<String>>()
    catalog.drones.forEach { drone ->
        if (drone.manufacturer.isBlank()) errors += "manufacturer is blank"
        if (drone.model.isBlank()) errors += "model is blank"
        if (drone.segment == DroneCatalogSegment.UNKNOWN) errors += "${drone.displayName} segment unknown"
        if (drone.source.type == DroneCapabilitySource.UNKNOWN) errors += "${drone.displayName} source type unknown"
        validateSource(drone.source)?.let { errors += "${drone.displayName} $it" }
        drone.ingressProtectionRating?.let {
            if (!it.matches(Regex("IP[0-6X][0-9X]"))) errors += "${drone.displayName} invalid IP rating"
        }

        val modelKey = "${normalizeManufacturer(drone.manufacturer)}:${normalizeDroneModel(drone.model, drone.manufacturer)}"
        if (!canonicalModels.add(modelKey)) errors += "duplicate canonical model $modelKey"

        listOf(
            drone.windResistance.generalMs,
            drone.windResistance.generalMinMs,
            drone.windResistance.generalMaxMs,
            drone.windResistance.takeoffLandingMs,
            drone.windResistance.cruiseMs
        ).filterNotNull().forEach {
            if (it <= 0.0 || !it.isFinite()) errors += "${drone.displayName} wind value must be positive"
        }
        if (
            drone.windResistance.generalMinMs != null &&
            drone.windResistance.generalMaxMs != null &&
            drone.windResistance.generalMinMs > drone.windResistance.generalMaxMs
        ) {
            errors += "${drone.displayName} wind range is invalid"
        }
        if (
            drone.operatingTemperatureMinC != null &&
            drone.operatingTemperatureMaxC != null &&
            drone.operatingTemperatureMinC >= drone.operatingTemperatureMaxC
        ) {
            errors += "${drone.displayName} temperature range is invalid"
        }

        val names = listOf(drone.model) + drone.aliases
        names.forEach { alias ->
            val aliasKey = "${normalizeManufacturer(drone.manufacturer)}:${normalizeDroneModel(alias, drone.manufacturer)}"
            aliasMap.getOrPut(aliasKey) { mutableSetOf() }.add(drone.displayName)
        }
    }
    aliasMap.filterValues { it.size > 1 }.forEach { (alias, models) ->
        errors += "ambiguous alias $alias -> ${models.joinToString()}"
    }
    return errors
}

fun normalizeManufacturer(value: String): String =
    normalizeForCatalog(value)
        .replace(Regex("[^a-z0-9]+"), "")
        .let {
            when (it) {
                "autelrobotics" -> "autel"
                else -> it
            }
        }

fun normalizeDroneModel(value: String, manufacturer: String = ""): String {
    val manufacturerKey = normalizeManufacturer(manufacturer)
    val normalized = normalizeForCatalog(value)
        .replace(Regex("[\\s\\-_.\\/]+"), "")
        .replace(Regex("[^a-z0-9]+"), "")
        .replace("evoii", "evo2")
    return if (manufacturerKey.isNotBlank() && normalized.startsWith(manufacturerKey)) {
        normalized.removePrefix(manufacturerKey)
    } else {
        normalized
    }
}

private data class IndexedDroneCatalogEntry(
    val entry: DroneTechnicalCatalogEntry,
    val manufacturerKey: String,
    val modelKey: String,
    val aliasKeys: List<String>
)

private fun JSONObject.toDroneTechnicalCatalogEntry(): DroneTechnicalCatalogEntry =
    DroneTechnicalCatalogEntry(
        manufacturer = optString("manufacturer"),
        model = optString("model"),
        segment = optString("segment").toCatalogSegment(),
        aliases = optJSONArray("aliases").toStringList(),
        windResistance = optJSONObject("windResistance").toDroneWindResistance(optDoubleOrNull("maxWindResistanceMs")),
        operatingTemperatureMinC = optDoubleOrNull("operatingTemperatureMinC"),
        operatingTemperatureMaxC = optDoubleOrNull("operatingTemperatureMaxC"),
        operatingTemperatureNotes = optStringOrNull("operatingTemperatureNotes"),
        ingressProtectionRating = optStringOrNull("ingressProtectionRating"),
        precipitationCapability = optString("precipitationCapability").toPrecipitationCapability(),
        source = optJSONObject("source").toDroneTechnicalCatalogSource()
    )

private fun JSONObject?.toDroneWindResistance(legacyGeneralMs: Double?): DroneWindResistance {
    val json = this ?: return DroneWindResistance(generalMs = legacyGeneralMs)
    return DroneWindResistance(
        generalMs = json.optDoubleOrNull("generalMs"),
        generalMinMs = json.optDoubleOrNull("generalMinMs"),
        generalMaxMs = json.optDoubleOrNull("generalMaxMs"),
        takeoffLandingMs = json.optDoubleOrNull("takeoffLandingMs"),
        cruiseMs = json.optDoubleOrNull("cruiseMs"),
        notes = json.optStringOrNull("notes")
    )
}

private fun JSONObject?.toDroneTechnicalCatalogSource(): DroneTechnicalCatalogSource {
    val json = this ?: JSONObject()
    return DroneTechnicalCatalogSource(
        type = json.optString("type").toCapabilitySource(),
        name = json.optStringOrNull("name"),
        reference = json.optStringOrNull("reference"),
        verifiedAt = json.optStringOrNull("verifiedAt")
    )
}

private fun parseDroneCatalogMetadata(json: String): DroneCatalogMetadata {
    val root = JSONObject(json.ifBlank { "{}" })
    return DroneCatalogMetadata(
        schemaVersion = root.optInt("schemaVersion", 0),
        catalogVersion = root.optInt("catalogVersion", 0),
        updatedAt = root.optStringOrNull("updatedAt"),
        installedAt = root.optStringOrNull("installedAt"),
        lastCheckedAt = root.optStringOrNull("lastCheckedAt")
    )
}

private fun DroneCatalogMetadata.toJsonString(): String =
    JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("catalogVersion", catalogVersion)
        .put("updatedAt", updatedAt)
        .put("installedAt", installedAt)
        .put("lastCheckedAt", lastCheckedAt)
        .toString(2)

private fun DroneCatalogMetadata?.orEmpty(): DroneCatalogMetadata =
    this ?: DroneCatalogMetadata()

private fun validateSource(source: DroneTechnicalCatalogSource): String? {
    val reference = source.reference ?: return "missing source reference"
    val uri = runCatching { URI(reference) }.getOrNull() ?: return "invalid source URL"
    if (uri.scheme != "https") return "source URL must be HTTPS"
    val host = uri.host.orEmpty()
    if (!host.isAllowedManufacturerHost()) {
        return "source URL host is not a supported manufacturer"
    }
    val verifiedAt = source.verifiedAt ?: return "missing verifiedAt"
    runCatching { LocalDate.parse(verifiedAt) }
        .getOrElse { return "invalid verifiedAt" }
    return null
}

private fun String.isCatalogDateOrDateTime(): Boolean =
    runCatching { LocalDate.parse(this) }.isSuccess ||
        runCatching { OffsetDateTime.parse(this) }.isSuccess

private fun String.isAllowedManufacturerHost(): Boolean =
    this == "dji.com" ||
        endsWith(".dji.com") ||
        this == "autelrobotics.com" ||
        endsWith(".autelrobotics.com")

fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun Throwable.toDroneCatalogRemoteReason(): String =
    when (this) {
        is DroneCatalogRemoteError.HttpError -> when (statusCode) {
            404 -> "HTTP_NOT_FOUND"
            in 500..599 -> "HTTP_SERVER"
            else -> "HTTP_$statusCode"
        }
        is DroneCatalogRemoteError.Timeout -> "TIMEOUT"
        is DroneCatalogRemoteError.Network -> "NETWORK"
        is DroneCatalogRemoteError.InvalidJson -> "JSON_PARSING"
        is DroneCatalogRemoteError.InvalidManifest -> "INVALID_MANIFEST"
        else -> "REMOTE_INTERNAL"
    }

private fun String.toCapabilitySource(): DroneCapabilitySource =
    when (uppercase()) {
        "MANUFACTURER" -> DroneCapabilitySource.MANUFACTURER
        "USER_PROVIDED" -> DroneCapabilitySource.USER_PROVIDED
        "CATALOG" -> DroneCapabilitySource.CATALOG
        else -> DroneCapabilitySource.UNKNOWN
    }

private fun String.toCatalogSegment(): DroneCatalogSegment =
    when (uppercase()) {
        "CONSUMER" -> DroneCatalogSegment.CONSUMER
        "ENTERPRISE" -> DroneCatalogSegment.ENTERPRISE
        else -> DroneCatalogSegment.UNKNOWN
    }

private fun String.toPrecipitationCapability(): DronePrecipitationCapability =
    when (uppercase()) {
        "NOT_DECLARED" -> DronePrecipitationCapability.NOT_DECLARED
        "LIGHT_PRECIPITATION" -> DronePrecipitationCapability.LIGHT_PRECIPITATION
        "RAIN_RESISTANT" -> DronePrecipitationCapability.RAIN_RESISTANT
        else -> DronePrecipitationCapability.UNKNOWN
    }

private fun JSONArray?.toStringList(): List<String> {
    val array = this ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optString(index).takeIf { it.isNotBlank() }
    }
}

private fun <T> JSONArray?.toObjectList(transform: (JSONObject) -> T): List<T> {
    val array = this ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let(transform)
    }
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name).takeIf { it.isFinite() } else null

private fun normalizeForCatalog(value: String): String {
    val withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return withoutMarks
        .lowercase()
        .replace("®", "")
        .replace("™", "")
        .replace("+", "plus")
        .trim()
}

private fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in a.indices) {
        current[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            current[j + 1] = min(
                min(current[j] + 1, previous[j + 1] + 1),
                previous[j] + cost
            )
        }
        val tmp = previous
        previous = current
        current = tmp
    }
    return previous[b.length]
}
