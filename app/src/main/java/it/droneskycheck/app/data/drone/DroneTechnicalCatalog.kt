package it.droneskycheck.app.data.drone

import android.content.Context
import it.droneskycheck.app.data.LocalDrone
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.min

data class DroneTechnicalCatalog(
    val version: Int,
    val updatedAt: String?,
    val drones: List<DroneTechnicalCatalogEntry>
)

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
}

class DroneTechnicalCatalogRepository(
    private val context: Context
) : DroneTechnicalCatalogClient {
    @Volatile
    private var cachedResolver: DroneTechnicalCatalogResolver? = null

    override suspend fun resolver(): DroneTechnicalCatalogResolver {
        cachedResolver?.let { return it }
        val loaded = context.assets.open(CatalogAssetName)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
            .let(::parseDroneTechnicalCatalog)
            .let(::DroneTechnicalCatalogResolver)
        cachedResolver = loaded
        return loaded
    }

    private companion object {
        const val CatalogAssetName = "drone_technical_catalog.json"
    }
}

class InMemoryDroneTechnicalCatalogClient(
    private val resolver: DroneTechnicalCatalogResolver = DroneTechnicalCatalogResolver.empty()
) : DroneTechnicalCatalogClient {
    override suspend fun resolver(): DroneTechnicalCatalogResolver = resolver
}

class DroneTechnicalCatalogResolver(
    private val catalog: DroneTechnicalCatalog
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
            DroneTechnicalCatalogResolver(DroneTechnicalCatalog(version = 0, updatedAt = null, drones = emptyList()))

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
    val root = JSONObject(json.ifBlank { "{}" })
    return DroneTechnicalCatalog(
        version = root.optInt("version", 0),
        updatedAt = root.optStringOrNull("updatedAt"),
        drones = root.optJSONArray("drones").toObjectList { it.toDroneTechnicalCatalogEntry() }
    )
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
    optString(name).takeIf { it.isNotBlank() }

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
