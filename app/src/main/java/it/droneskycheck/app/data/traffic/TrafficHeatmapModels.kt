package it.droneskycheck.app.data.traffic

import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

object TrafficHeatmapDefaults {
    const val DefaultDays = 30
    const val DefaultRadiusKm = 20.0
    const val MaxRadiusKm = 100.0
    const val DebounceMillis = 350L
    const val CacheMaxEntries = 24
}

enum class TrafficHeatmapMaxAgl(
    val preferenceValue: String,
    val requestValue: String,
    val shortLabel: String,
    val detailLabel: String,
    val minZoom: Double,
    val includedBands: List<String>?
) {
    Below120(
        preferenceValue = "120",
        requestValue = "120",
        shortLabel = "<120 m",
        detailLabel = "sotto 120 m",
        minZoom = 7.0,
        includedBands = listOf("lt_50m", "50_120m")
    ),
    Below300(
        preferenceValue = "300",
        requestValue = "300",
        shortLabel = "<300 m",
        detailLabel = "sotto 300 m",
        minZoom = 7.0,
        includedBands = listOf("lt_50m", "50_120m", "120_300m")
    ),
    Below500(
        preferenceValue = "500",
        requestValue = "500",
        shortLabel = "<500 m",
        detailLabel = "sotto 500 m",
        minZoom = 7.0,
        includedBands = listOf("lt_50m", "50_120m", "120_300m", "300_500m")
    ),
    Below1000(
        preferenceValue = "1000",
        requestValue = "1000",
        shortLabel = "<1000 m",
        detailLabel = "sotto 1000 m",
        minZoom = 8.0,
        includedBands = listOf("lt_50m", "50_120m", "120_300m", "300_500m", "500_1000m")
    ),
    All(
        preferenceValue = "all",
        requestValue = "all",
        shortLabel = "Tutto",
        detailLabel = "tutto il traffico",
        minZoom = 8.0,
        includedBands = null
    );

    companion object {
        val Default = Below500

        fun fromPreferenceValue(value: String?): TrafficHeatmapMaxAgl =
            entries.firstOrNull { it.preferenceValue == value?.trim()?.lowercase() } ?: Default
    }
}

data class TrafficHeatmapResponse(
    val ok: Boolean,
    val generatedAt: Long?,
    val servedAt: Long?,
    val dataAvailableFrom: Long?,
    val periodDays: Int,
    val query: TrafficHeatmapQuery?,
    val count: Int,
    val cells: List<TrafficHeatmapCell>,
    val cache: TrafficHeatmapCacheInfo?
)

data class TrafficHeatmapQuery(
    val lat: Double?,
    val lon: Double?,
    val radiusKm: Double?,
    val days: Int?,
    val maxAgl: TrafficHeatmapMaxAgl
)

data class TrafficHeatmapCacheInfo(
    val hit: Boolean?,
    val ageMs: Long?,
    val ttlMs: Long?,
    val singleFlight: Boolean?
)

data class TrafficHeatmapCell(
    val lat: Double,
    val lon: Double,
    val observations: Int,
    val uniqueTargetBucketSum: Int,
    val sources: Map<String, Int>,
    val altitudeBands: Map<String, Int>,
    val estimatedAglBands: Map<String, Int>,
    val maxAgl: TrafficHeatmapMaxAgl?,
    val filteredObservations: Int?
) {
    fun filteredObservationsFor(maxAgl: TrafficHeatmapMaxAgl): Int =
        if (maxAgl == TrafficHeatmapMaxAgl.All) {
            observations
        } else {
            maxAgl.includedBands.orEmpty().sumOf { band -> estimatedAglBands[band] ?: 0 }
        }.coerceAtLeast(0)
}

data class TrafficHeatmapState(
    val enabled: Boolean = false,
    val loading: Boolean = false,
    val maxAgl: TrafficHeatmapMaxAgl = TrafficHeatmapMaxAgl.Default,
    val cells: List<TrafficHeatmapCell> = emptyList(),
    val periodDays: Int = TrafficHeatmapDefaults.DefaultDays,
    val error: String? = null,
    val lastUpdatedAt: Long? = null,
    val selectedCell: TrafficHeatmapCellDetail? = null
)

data class TrafficHeatmapCellDetail(
    val lat: Double,
    val lon: Double,
    val maxAgl: TrafficHeatmapMaxAgl,
    val periodDays: Int,
    val observations: Int,
    val filteredObservations: Int,
    val estimatedAglBands: Map<String, Int>,
    val unknownAgl: Int,
    val inconsistentAgl: Int
)

sealed class TrafficHeatmapMappingError(message: String) : Exception(message) {
    object NotOk : TrafficHeatmapMappingError("Traffic heatmap response is not ok")
}

fun parseTrafficHeatmapResponse(json: JSONObject): TrafficHeatmapResponse {
    if (!json.optBoolean("ok", false)) {
        throw TrafficHeatmapMappingError.NotOk
    }

    val query = json.optJSONObject("query")
    val cells = json.findCellsArray()
        .toObjectListNotNull { it.toTrafficHeatmapCellOrNull() }
    val periodDays = json.optIntOrNull("periodDays") ?: query?.optIntOrNull("days") ?: TrafficHeatmapDefaults.DefaultDays

    return TrafficHeatmapResponse(
        ok = true,
        generatedAt = json.optLongOrNull("generatedAt"),
        servedAt = json.optLongOrNull("servedAt"),
        dataAvailableFrom = json.optLongOrNull("dataAvailableFrom"),
        periodDays = periodDays,
        query = query?.let {
            TrafficHeatmapQuery(
                lat = it.optDoubleOrNull("lat"),
                lon = it.optDoubleOrNull("lon"),
                radiusKm = it.optDoubleOrNull("radiusKm"),
                days = it.optIntOrNull("days"),
                maxAgl = TrafficHeatmapMaxAgl.fromPreferenceValue(it.optStringOrNull("maxAgl"))
            )
        },
        count = json.optIntOrNull("count") ?: cells.size,
        cells = cells,
        cache = json.optJSONObject("cache")?.let {
            TrafficHeatmapCacheInfo(
                hit = it.optBooleanOrNull("hit"),
                ageMs = it.optLongOrNull("ageMs"),
                ttlMs = it.optLongOrNull("ttlMs"),
                singleFlight = it.optBooleanOrNull("singleFlight")
            )
        }
    )
}

fun trafficHeatmapCellsToFeatureCollection(
    cells: List<TrafficHeatmapCell>,
    maxAgl: TrafficHeatmapMaxAgl
): FeatureCollection =
    FeatureCollection.fromFeatures(
        cells.mapNotNull { cell ->
            val filtered = cell.filteredObservationsFor(maxAgl)
            if (filtered <= 0) return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(cell.lon, cell.lat)).apply {
                addNumberProperty(TrafficHeatmapMapProperties.Lat, cell.lat)
                addNumberProperty(TrafficHeatmapMapProperties.Lon, cell.lon)
                addNumberProperty(TrafficHeatmapMapProperties.Observations, cell.observations)
                addNumberProperty(TrafficHeatmapMapProperties.FilteredObservations, filtered)
                addNumberProperty(TrafficHeatmapMapProperties.Weight, kotlin.math.ln(1.0 + filtered.toDouble()))
                addStringProperty(TrafficHeatmapMapProperties.MaxAgl, maxAgl.preferenceValue)
                TrafficHeatmapBandProperties.forEach { (band, property) ->
                    addNumberProperty(property, cell.estimatedAglBands[band] ?: 0)
                }
            }
        }
    )

fun trafficHeatmapCellDetailFromFeature(
    feature: Feature,
    fallbackMaxAgl: TrafficHeatmapMaxAgl,
    periodDays: Int
): TrafficHeatmapCellDetail? {
    val lat = feature.properties()?.doubleValue(TrafficHeatmapMapProperties.Lat) ?: return null
    val lon = feature.properties()?.doubleValue(TrafficHeatmapMapProperties.Lon) ?: return null
    val maxAgl = TrafficHeatmapMaxAgl.fromPreferenceValue(
        feature.properties()?.stringValue(TrafficHeatmapMapProperties.MaxAgl)
    ).takeIf { feature.properties()?.stringValue(TrafficHeatmapMapProperties.MaxAgl) != null } ?: fallbackMaxAgl
    val estimatedAglBands = TrafficHeatmapBandProperties.mapValues { (_, property) ->
        feature.properties()?.intValue(property) ?: 0
    }.filterValues { it > 0 }
    return TrafficHeatmapCellDetail(
        lat = lat,
        lon = lon,
        maxAgl = maxAgl,
        periodDays = periodDays,
        observations = feature.properties()?.intValue(TrafficHeatmapMapProperties.Observations) ?: 0,
        filteredObservations = feature.properties()?.intValue(TrafficHeatmapMapProperties.FilteredObservations) ?: 0,
        estimatedAglBands = estimatedAglBands,
        unknownAgl = estimatedAglBands["unknown"] ?: 0,
        inconsistentAgl = estimatedAglBands["below_terrain_or_inconsistent"] ?: 0
    )
}

object TrafficHeatmapMapProperties {
    const val Lat = "trafficHeatmapLat"
    const val Lon = "trafficHeatmapLon"
    const val Observations = "observations"
    const val FilteredObservations = "filteredObservations"
    const val Weight = "weight"
    const val MaxAgl = "maxAgl"
}

val TrafficHeatmapBandProperties: Map<String, String> = linkedMapOf(
    "lt_50m" to "aglLt50m",
    "50_120m" to "agl50To120m",
    "120_300m" to "agl120To300m",
    "300_500m" to "agl300To500m",
    "500_1000m" to "agl500To1000m",
    "gt_1000m" to "aglGt1000m",
    "unknown" to "aglUnknown",
    "below_terrain_or_inconsistent" to "aglInconsistent"
)

private fun JSONObject.findCellsArray(): JSONArray? =
    optJSONArray("cells")
        ?: optJSONObject("heatmap")?.optJSONArray("cells")
        ?: optJSONObject("grid")?.optJSONArray("cells")

private fun JSONObject.toTrafficHeatmapCellOrNull(): TrafficHeatmapCell? {
    val lat = optFirstDoubleOrNull("lat", "centerLat", "center_lat") ?: return null
    val lon = optFirstDoubleOrNull("lon", "centerLon", "center_lon") ?: return null
    return TrafficHeatmapCell(
        lat = lat,
        lon = lon,
        observations = optIntOrNull("observations").nonNegative(),
        uniqueTargetBucketSum = optIntOrNull("uniqueTargetBucketSum").nonNegative(),
        sources = optJSONObject("sources").toCountMap(),
        altitudeBands = optJSONObject("altitudeBands").toCountMap(),
        estimatedAglBands = optJSONObject("estimatedAglBands").toCountMap(),
        maxAgl = optStringOrNull("maxAgl")?.let(TrafficHeatmapMaxAgl::fromPreferenceValue),
        filteredObservations = optIntOrNull("filteredObservations")?.coerceAtLeast(0)
    )
}

private fun JSONObject?.toCountMap(): Map<String, Int> {
    val json = this ?: return emptyMap()
    return buildMap {
        json.keys().forEachRemaining { key ->
            val count = json.optIntOrNull(key).nonNegative()
            if (count > 0) put(key, count)
        }
    }
}

private fun JSONArray?.toObjectListNotNull(transform: (JSONObject) -> TrafficHeatmapCell?): List<TrafficHeatmapCell> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }
}

private fun Int?.nonNegative(): Int = this?.coerceAtLeast(0) ?: 0

private fun JSONObject.optFirstDoubleOrNull(vararg names: String): Double? =
    names.firstNotNullOfOrNull(::optDoubleOrNull)

private fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return opt(name)?.toString()?.trim()?.takeIf { it.isNotBlank() }
}

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Number -> optDouble(name)
        else -> optString(name).toDoubleOrNull()
    }?.takeIf { it.isFinite() }

private fun JSONObject.optLongOrNull(name: String): Long? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Number -> optLong(name)
        else -> optString(name).toLongOrNull()
    }

private fun JSONObject.optIntOrNull(name: String): Int? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Number -> optDouble(name).toInt()
        else -> optString(name).toDoubleOrNull()?.toInt()
    }

private fun JSONObject.optBooleanOrNull(name: String): Boolean? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Boolean -> optBoolean(name)
        optString(name).equals("true", ignoreCase = true) -> true
        optString(name).equals("false", ignoreCase = true) -> false
        else -> null
    }

private fun com.google.gson.JsonObject.stringValue(key: String): String? =
    get(key)
        ?.takeIf { !it.isJsonNull }
        ?.let { value -> runCatching { value.asString }.getOrNull() }
        ?.takeIf { it.isNotBlank() }

private fun com.google.gson.JsonObject.doubleValue(key: String): Double? =
    get(key)
        ?.takeIf { !it.isJsonNull }
        ?.let { value ->
            when {
                value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> value.asDouble
                else -> value.asString.toDoubleOrNull()
            }
        }
        ?.takeIf { it.isFinite() }

private fun com.google.gson.JsonObject.intValue(key: String): Int? =
    get(key)
        ?.takeIf { !it.isJsonNull }
        ?.let { value ->
            when {
                value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> value.asInt
                else -> value.asString.toDoubleOrNull()?.toInt()
            }
        }
