package it.droneskycheck.app.data.traffic

import it.droneskycheck.app.data.DscLogger
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object TrafficAwarenessDefaults {
    const val DefaultRadiusKm = 20.0
    const val PollingIntervalMillis = 5_000L
}

data class TrafficAwarenessResponse(
    val ok: Boolean,
    val generatedAt: Long?,
    val servedAt: Long?,
    val center: TrafficCenter?,
    val radiusKm: Double?,
    val traffic: TrafficSummary,
    val providers: Map<String, TrafficProviderStatus>,
    val cache: TrafficCacheInfo?
)

data class TrafficCenter(
    val lat: Double?,
    val lon: Double?
)

data class TrafficSummary(
    val count: Int,
    val targets: List<TrafficTarget>
)

data class TrafficTarget(
    val id: String,
    val identifiers: TrafficIdentifiers,
    val position: TrafficPosition,
    val altitude: TrafficAltitude,
    val motion: TrafficMotion,
    val aircraft: TrafficAircraft,
    val time: TrafficTime,
    val relative: TrafficRelative,
    val provider: String?,
    val source: String?,
    val quality: String?,
    val sources: List<TrafficSource>,
    val provenance: TrafficProvenance?,
    val objectType: String? = null
)

enum class TrafficTargetKind {
    AIRCRAFT,
    DRONE
}

data class TrafficIdentifiers(
    val icao24: String?,
    val callsign: String?,
    val registration: String?,
    val sourceId: String?
)

data class TrafficPosition(
    val lat: Double,
    val lon: Double
)

data class TrafficAltitude(
    val baroM: Double?,
    val geoM: Double?,
    val mslM: Double?,
    val aglM: Double?,
    val sourceM: Double?,
    val sourceReference: String?
)

data class TrafficMotion(
    val groundSpeedMps: Double?,
    val verticalRateMps: Double?,
    val trackDeg: Double?,
    val headingDeg: Double?
)

data class TrafficAircraft(
    val category: String?,
    val type: String?
)

data class TrafficTime(
    val timestamp: Long?,
    val ageSec: Double?
)

data class TrafficRelative(
    val distanceM: Double?,
    val bearingDeg: Double?
)

data class TrafficSource(
    val provider: String?,
    val source: String?
)

data class TrafficProvenance(
    val sources: List<TrafficSource>,
    val contributions: List<TrafficContribution>
)

data class TrafficContribution(
    val id: String?,
    val provider: String?,
    val source: String?,
    val sourceId: String?,
    val timestamp: Long?
)

data class TrafficProviderStatus(
    val status: String,
    val count: Int?,
    val errorCode: String?
)

data class TrafficCacheInfo(
    val hit: Boolean?,
    val ageMs: Long?,
    val ttlMs: Long?,
    val singleFlight: Boolean?
)

data class TrafficAwarenessState(
    val enabled: Boolean = false,
    val loading: Boolean = false,
    val response: TrafficAwarenessResponse? = null,
    val error: String? = null,
    val lastUpdatedAt: Long? = null
)

sealed class TrafficAwarenessMappingError(message: String) : Exception(message) {
    object NotOk : TrafficAwarenessMappingError("Traffic Awareness response is not ok")
    object InvalidTrafficEnvelope : TrafficAwarenessMappingError("Invalid Traffic Awareness envelope")
}

fun parseTrafficAwarenessResponse(json: JSONObject): TrafficAwarenessResponse {
    if (!json.optBoolean("ok", false)) {
        throw TrafficAwarenessMappingError.NotOk
    }

    val trafficObject = json.optJSONObject("traffic") ?: JSONObject()
    val targets = trafficObject.optJSONArray("targets").toObjectListNotNull { target ->
        target.toTrafficTargetOrNull()
    }
    val declaredCount = trafficObject.optIntOrNull("count") ?: targets.size

    return TrafficAwarenessResponse(
        ok = true,
        generatedAt = json.optLongOrNull("generatedAt"),
        servedAt = json.optLongOrNull("servedAt"),
        center = json.optJSONObject("center")?.let {
            TrafficCenter(
                lat = it.optDoubleOrNull("lat"),
                lon = it.optDoubleOrNull("lon")
            )
        },
        radiusKm = json.optDoubleOrNull("radiusKm"),
        traffic = TrafficSummary(
            count = declaredCount,
            targets = targets
        ),
        providers = json.optJSONObject("providers").toProviderMap(),
        cache = json.optJSONObject("cache")?.toTrafficCacheInfo()
    ).also { response ->
        DscLogger.trace(
            TrafficAwarenessLogTag,
            "parsed ok=${response.ok} trafficCount=${response.traffic.count} " +
                "targetsParsed=${response.traffic.targets.size} cacheHit=${response.cache?.hit}"
        )
        response.traffic.targets.forEach { target ->
            DscLogger.trace(
                TrafficAwarenessLogTag,
                "target id=${target.id} callsign=${target.identifiers.callsign ?: target.identifiers.registration ?: target.identifiers.icao24 ?: target.identifiers.sourceId} " +
                    "provider=${target.provider} source=${target.source} " +
                    "lat=${target.position.lat.coarseTraffic(4)} lon=${target.position.lon.coarseTraffic(4)} " +
                    "distanceM=${target.relative.distanceM?.toInt()}"
            )
        }
    }
}

private fun JSONObject.toTrafficTargetOrNull(): TrafficTarget? {
    val id = optStringOrNull("id") ?: run {
        DscLogger.warn(TrafficAwarenessLogTag, "target skipped reason=missing_id")
        return null
    }
    val position = optJSONObject("position").toTrafficPositionOrNull() ?: run {
        DscLogger.warn(TrafficAwarenessLogTag, "target skipped reason=missing_position id=$id")
        return null
    }
    return TrafficTarget(
        id = id,
        identifiers = optJSONObject("identifiers").toTrafficIdentifiers(),
        position = position,
        altitude = optJSONObject("altitude").toTrafficAltitude(),
        motion = optJSONObject("motion").toTrafficMotion(),
        aircraft = optJSONObject("aircraft").toTrafficAircraft(),
        time = optJSONObject("time").toTrafficTime(),
        relative = optJSONObject("relative").toTrafficRelative(),
        provider = optStringOrNull("provider"),
        source = optStringOrNull("source"),
        quality = optStringOrNull("quality"),
        sources = optJSONArray("sources").toObjectList { it.toTrafficSource() },
        provenance = optJSONObject("provenance")?.toTrafficProvenance(),
        objectType = optFirstStringOrNull("kind", "targetKind", "trafficKind", "objectType", "vehicleType", "targetType")
    )
}

fun TrafficTarget.trafficTargetKind(): TrafficTargetKind =
    if (droneTrafficHints().any { it.hasDroneTrafficHint() }) {
        TrafficTargetKind.DRONE
    } else {
        TrafficTargetKind.AIRCRAFT
    }

private fun TrafficTarget.droneTrafficHints(): List<String> =
    buildList {
        add(id)
        listOfNotNull(
            objectType,
            provider,
            source,
            quality,
            aircraft.category,
            aircraft.type,
            identifiers.icao24,
            identifiers.callsign,
            identifiers.registration,
            identifiers.sourceId
        ).forEach(::add)
        sources.forEach { trafficSource ->
            listOfNotNull(trafficSource.provider, trafficSource.source).forEach(::add)
        }
        provenance?.sources?.forEach { trafficSource ->
            listOfNotNull(trafficSource.provider, trafficSource.source).forEach(::add)
        }
        provenance?.contributions?.forEach { contribution ->
            listOfNotNull(
                contribution.id,
                contribution.provider,
                contribution.source,
                contribution.sourceId
            ).forEach(::add)
        }
    }

private fun String.hasDroneTrafficHint(): Boolean {
    val normalized = lowercase(Locale.US)
        .replace("_", " ")
        .replace("-", " ")
    return DroneTrafficKeywords.any { keyword -> normalized.contains(keyword) }
}

private fun JSONObject?.toTrafficIdentifiers(): TrafficIdentifiers {
    val json = this ?: JSONObject()
    return TrafficIdentifiers(
        icao24 = json.optStringOrNull("icao24"),
        callsign = json.optStringOrNull("callsign"),
        registration = json.optStringOrNull("registration"),
        sourceId = json.optStringOrNull("sourceId")
    )
}

private fun JSONObject?.toTrafficPositionOrNull(): TrafficPosition? {
    val json = this ?: return null
    val lat = json.optDoubleOrNull("lat") ?: return null
    val lon = json.optDoubleOrNull("lon") ?: return null
    return TrafficPosition(lat = lat, lon = lon)
}

private fun JSONObject?.toTrafficAltitude(): TrafficAltitude {
    val json = this ?: JSONObject()
    return TrafficAltitude(
        baroM = json.optDoubleOrNull("baroM"),
        geoM = json.optDoubleOrNull("geoM"),
        mslM = json.optDoubleOrNull("mslM"),
        aglM = json.optDoubleOrNull("aglM"),
        sourceM = json.optDoubleOrNull("sourceM"),
        sourceReference = json.optStringOrNull("sourceReference")
    )
}

private fun JSONObject?.toTrafficMotion(): TrafficMotion {
    val json = this ?: JSONObject()
    return TrafficMotion(
        groundSpeedMps = json.optDoubleOrNull("groundSpeedMps"),
        verticalRateMps = json.optDoubleOrNull("verticalRateMps"),
        trackDeg = json.optDoubleOrNull("trackDeg"),
        headingDeg = json.optDoubleOrNull("headingDeg")
    )
}

private fun JSONObject?.toTrafficAircraft(): TrafficAircraft {
    val json = this ?: JSONObject()
    return TrafficAircraft(
        category = json.optStringOrNull("category"),
        type = json.optStringOrNull("type")
    )
}

private fun JSONObject?.toTrafficTime(): TrafficTime {
    val json = this ?: JSONObject()
    return TrafficTime(
        timestamp = json.optLongOrNull("timestamp"),
        ageSec = json.optDoubleOrNull("ageSec")
    )
}

private fun JSONObject?.toTrafficRelative(): TrafficRelative {
    val json = this ?: JSONObject()
    return TrafficRelative(
        distanceM = json.optDoubleOrNull("distanceM"),
        bearingDeg = json.optDoubleOrNull("bearingDeg")
    )
}

private fun JSONObject.toTrafficSource(): TrafficSource =
    TrafficSource(
        provider = optStringOrNull("provider"),
        source = optStringOrNull("source")
    )

private fun JSONObject.toTrafficProvenance(): TrafficProvenance =
    TrafficProvenance(
        sources = optJSONArray("sources").toObjectList { it.toTrafficSource() },
        contributions = optJSONArray("contributions").toObjectList { it.toTrafficContribution() }
    )

private fun JSONObject.toTrafficContribution(): TrafficContribution =
    TrafficContribution(
        id = optStringOrNull("id"),
        provider = optStringOrNull("provider"),
        source = optStringOrNull("source"),
        sourceId = optStringOrNull("sourceId"),
        timestamp = optLongOrNull("timestamp")
    )

private fun JSONObject?.toProviderMap(): Map<String, TrafficProviderStatus> {
    val json = this ?: return emptyMap()
    return buildMap {
        json.keys().forEachRemaining { key ->
            val value = json.optJSONObject(key) ?: return@forEachRemaining
            put(
                key,
                TrafficProviderStatus(
                    status = value.optStringOrNull("status").orEmpty().ifBlank { "unknown" },
                    count = value.optIntOrNull("count"),
                    errorCode = value.optStringOrNull("errorCode")
                )
            )
        }
    }
}

private fun JSONObject.toTrafficCacheInfo(): TrafficCacheInfo =
    TrafficCacheInfo(
        hit = optBooleanOrNull("hit"),
        ageMs = optLongOrNull("ageMs"),
        ttlMs = optLongOrNull("ttlMs"),
        singleFlight = optBooleanOrNull("singleFlight")
    )

private fun <T> JSONArray?.toObjectList(transform: (JSONObject) -> T): List<T> =
    toObjectListNotNull { transform(it) }

private fun <T> JSONArray?.toObjectListNotNull(transform: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.let(transform)
    }
}

private fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return opt(name)?.toString()?.trim()?.takeIf { it.isNotBlank() }
}

private fun JSONObject.optFirstStringOrNull(vararg names: String): String? =
    names.firstNotNullOfOrNull(::optStringOrNull)

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
        opt(name) is Number -> optInt(name)
        else -> optString(name).toIntOrNull()
    }

private fun JSONObject.optBooleanOrNull(name: String): Boolean? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Boolean -> optBoolean(name)
        optString(name).equals("true", ignoreCase = true) -> true
        optString(name).equals("false", ignoreCase = true) -> false
        else -> null
    }

private val DroneTrafficKeywords = listOf(
    "airsense",
    "air sense",
    "drone",
    "uas",
    "uav",
    "rpas",
    "remoteid",
    "remote id"
)
