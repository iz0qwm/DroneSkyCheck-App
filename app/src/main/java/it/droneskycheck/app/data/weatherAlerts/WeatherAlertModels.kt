package it.droneskycheck.app.data.weatherAlerts

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

enum class CriticalityLevel(val priority: Int) {
    NONE(0),
    UNKNOWN(0),
    YELLOW(1),
    ORANGE(2),
    RED(3);

    companion object {
        fun fromApi(value: String?): CriticalityLevel =
            entries.firstOrNull { it.name == value?.trim()?.uppercase() } ?: UNKNOWN
    }
}

enum class VigilanceLevel(val priority: Int) {
    NONE(0),
    UNKNOWN(0),
    WEAK(1),
    MODERATE(2),
    HEAVY(3),
    VERY_HEAVY(4);

    companion object {
        fun fromApi(value: String?): VigilanceLevel =
            entries.firstOrNull { it.name == value?.trim()?.uppercase() } ?: UNKNOWN
    }
}

enum class WeatherRisk(val apiName: String, val italianLabel: String) {
    THUNDERSTORM("thunderstorm", "temporali"),
    HYDROGEOLOGICAL("hydrogeological", "rischio idrogeologico"),
    HYDRAULIC("hydraulic", "rischio idraulico")
}

data class WeatherAlertResponse(
    val point: WeatherAlertPoint?,
    val criticality: WeatherCriticality?,
    val vigilance: WeatherVigilance?,
    val sources: WeatherSources?,
    val disclaimer: String?,
    val vigilanceNational: WeatherNationalVigilance? = null
)

data class WeatherAlertPoint(val lat: Double?, val lon: Double?)

data class WeatherCriticality(
    val zoneCode: String?,
    val zoneName: String?,
    val periods: Map<String, WeatherCriticalityPeriod>
)

data class WeatherCriticalityPeriod(
    val onset: Instant?,
    val expires: Instant?,
    val overallLevel: CriticalityLevel,
    val risks: Map<WeatherRisk, CriticalityLevel>
) {
    val maximumLevel: CriticalityLevel
        get() = (risks.values + overallLevel).maxByOrNull(CriticalityLevel::priority) ?: CriticalityLevel.NONE
}

data class WeatherVigilance(
    val zoneId: Long?,
    val zoneName: String?,
    val periods: Map<String, WeatherVigilancePeriod>
)

data class WeatherVigilancePeriod(
    val precipitation: WeatherPrecipitation?
)

data class WeatherPrecipitation(
    val level: VigilanceLevel,
    val originalText: String?
)

data class WeatherNationalVigilance(
    val geolocated: Boolean?,
    val localization: WeatherVigilanceLocalization?,
    val periods: Map<String, WeatherNationalVigilancePeriod>
)

data class WeatherVigilanceLocalization(
    val method: String?,
    val precision: String?,
    val pointRegions: List<String>
)

data class WeatherNationalVigilancePeriod(
    val onset: Instant?,
    val expires: Instant?,
    val precipitationText: String?,
    val affectedRegions: List<String>,
    val matchedRegions: List<String>,
    val appliesToPoint: Boolean?
)

data class WeatherSources(
    val criticality: WeatherSource?,
    val vigilance: WeatherSource?
)

data class WeatherSource(
    val revision: String?,
    val sourceName: String?,
    val license: String?
)

data class WeatherStatus(
    val criticalityRevision: String?,
    val vigilanceRevision: String?
)

fun parseWeatherAlertResponse(json: String): WeatherAlertResponse {
    val root = JSONObject(json)
    return WeatherAlertResponse(
        point = root.optJSONObject("point")?.let { point ->
            WeatherAlertPoint(
                lat = point.finiteDoubleOrNull("lat"),
                lon = point.finiteDoubleOrNull("lon")
            )
        },
        criticality = root.optJSONObject("criticality")?.toCriticality(),
        vigilance = root.optJSONObject("vigilance")?.toVigilance(),
        vigilanceNational = root.optJSONObject("vigilance_national")?.toNationalVigilance(),
        sources = root.optJSONObject("sources")?.let { sources ->
            WeatherSources(
                criticality = sources.optJSONObject("criticality")?.toSource(),
                vigilance = sources.optJSONObject("vigilance")?.toSource()
            )
        },
        disclaimer = root.stringOrNull("disclaimer")
    )
}

fun parseWeatherStatus(json: String): WeatherStatus {
    val root = JSONObject(json)
    return WeatherStatus(
        criticalityRevision = root.stringOrNull("criticality_revision"),
        vigilanceRevision = root.stringOrNull("vigilance_revision")
    )
}

private fun JSONObject.toCriticality(): WeatherCriticality =
    WeatherCriticality(
        zoneCode = stringOrNull("zone_code"),
        zoneName = stringOrNull("zone_name"),
        periods = optJSONObject("periods").toCriticalityPeriods()
    )

private fun JSONObject?.toCriticalityPeriods(): Map<String, WeatherCriticalityPeriod> {
    if (this == null) return emptyMap()
    return periodKeys().mapNotNull { key ->
        optJSONObject(key)?.let { period ->
            key to WeatherCriticalityPeriod(
                onset = period.instantOrNull("onset"),
                expires = period.instantOrNull("expires"),
                overallLevel = CriticalityLevel.fromApi(period.stringOrNull("overall_level")),
                risks = period.optJSONObject("risks").toRisks()
            )
        }
    }.toMap()
}

private fun JSONObject?.toRisks(): Map<WeatherRisk, CriticalityLevel> {
    if (this == null) return WeatherRisk.entries.associateWith { CriticalityLevel.NONE }
    return WeatherRisk.entries.associateWith { risk ->
        CriticalityLevel.fromApi(stringOrNull(risk.apiName))
    }
}

private fun JSONObject.toVigilance(): WeatherVigilance =
    WeatherVigilance(
        zoneId = longOrNull("zone_id"),
        zoneName = stringOrNull("zone_name"),
        periods = optJSONObject("periods").toVigilancePeriods()
    )

private fun JSONObject?.toVigilancePeriods(): Map<String, WeatherVigilancePeriod> {
    if (this == null) return emptyMap()
    return periodKeys().mapNotNull { key ->
        optJSONObject(key)?.let { period ->
            val precipitation = period.optJSONObject("precipitation")?.let { value ->
                WeatherPrecipitation(
                    level = VigilanceLevel.fromApi(value.stringOrNull("level")),
                    originalText = value.stringOrNull("original_text")
                )
            }
            key to WeatherVigilancePeriod(precipitation)
        }
    }.toMap()
}

private fun JSONObject.toNationalVigilance(): WeatherNationalVigilance =
    WeatherNationalVigilance(
        geolocated = booleanOrNull("geolocated"),
        localization = optJSONObject("localization")?.let { localization ->
            WeatherVigilanceLocalization(
                method = localization.stringOrNull("method"),
                precision = localization.stringOrNull("precision"),
                pointRegions = localization.optJSONArray("point_regions").toStringList()
            )
        },
        periods = optJSONObject("periods").toNationalVigilancePeriods()
    )

private fun JSONObject?.toNationalVigilancePeriods(): Map<String, WeatherNationalVigilancePeriod> {
    if (this == null) return emptyMap()
    return periodKeys().mapNotNull { key ->
        optJSONObject(key)?.let { period ->
            key to WeatherNationalVigilancePeriod(
                onset = period.instantOrNull("onset"),
                expires = period.instantOrNull("expires"),
                precipitationText = period.stringOrNull("precipitation_text"),
                affectedRegions = period.optJSONArray("affected_regions").toStringList(),
                matchedRegions = period.optJSONArray("matched_regions").toStringList(),
                appliesToPoint = period.booleanOrNull("applies_to_point")
            )
        }
    }.toMap()
}

private fun JSONObject.toSource(): WeatherSource =
    WeatherSource(
        revision = stringOrNull("revision"),
        sourceName = stringOrNull("source_name"),
        license = stringOrNull("license")
    )

private fun JSONObject?.periodKeys(): List<String> =
    listOf("TODAY", "TOMORROW", "AFTER_TOMORROW").filter { this?.has(it) == true }

private fun JSONObject.stringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null
    else optString(name).trim().takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.instantOrNull(name: String): Instant? =
    stringOrNull(name)?.let { value ->
        val isoInstant = if (ShortUtcOffset.matchesAtEnd(value)) "$value:00" else value
        runCatching { Instant.parse(isoInstant) }.getOrNull()
    }

private fun JSONObject.finiteDoubleOrNull(name: String): Double? =
    if (!has(name) || isNull(name)) null else optDouble(name).takeIf(Double::isFinite)

private fun JSONObject.longOrNull(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name)

private fun JSONObject.booleanOrNull(name: String): Boolean? =
    if (!has(name) || isNull(name)) null else optBoolean(name)

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optString(index).trim().takeIf(String::isNotBlank)
    }
}

private val ShortUtcOffset = Regex("[+-]\\d{2}$")

private fun Regex.matchesAtEnd(value: String): Boolean = find(value)?.range?.last == value.lastIndex
