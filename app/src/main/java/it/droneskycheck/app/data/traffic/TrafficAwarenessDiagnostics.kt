package it.droneskycheck.app.data.traffic

import android.util.Log
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

private const val TrafficDiagnosticLogIntervalMillis = 15_000L
private const val TrafficDiagnosticLogStateLimit = 512
private const val EarthRadiusMeters = 6_371_000.0

private enum class TrafficDiagnosticOrigin {
    DSC,
    OGN
}

internal data class TrafficDuplicateDiagnostic(
    val dscTarget: TrafficTarget,
    val ognTarget: TrafficTarget,
    val distanceMeters: Double,
    val timeDeltaMillis: Long?,
    val identityMatch: String
)

internal class TrafficDiagnosticRateLimiter(
    private val intervalMillis: Long = TrafficDiagnosticLogIntervalMillis
) {
    private data class State(val loggedAtMillis: Long, val significantSignature: String)

    private val states = object : LinkedHashMap<String, State>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, State>?): Boolean =
            size > TrafficDiagnosticLogStateLimit
    }

    @Synchronized
    fun shouldLog(key: String, significantSignature: String, nowMillis: Long): Boolean {
        val previous = states[key]
        val elapsed = previous?.let { nowMillis - it.loggedAtMillis }
        val shouldLog = previous == null ||
            previous.significantSignature != significantSignature ||
            elapsed == null || elapsed < 0L || elapsed >= intervalMillis
        if (shouldLog) states[key] = State(nowMillis, significantSignature)
        return shouldLog
    }
}

private object TrafficDiagnosticLogger {
    private val rateLimiter = TrafficDiagnosticRateLimiter()

    fun log(
        stage: String,
        key: String,
        significantSignature: String,
        message: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (!rateLimiter.shouldLog("$stage:$key", significantSignature, nowMillis)) return
        runCatching { Log.i(TrafficAwarenessLogTag, "[$stage] $message") }
    }
}

internal fun logRawTrafficTarget(raw: JSONObject, nowMillis: Long = System.currentTimeMillis()) {
    // Android receives the backend's serialized target, so RAW here means before the App parser.
    val origins = raw.diagnosticOrigins()
    if (origins.isEmpty()) return

    val identifiers = raw.optJSONObject("identifiers")
    val position = raw.optJSONObject("position")
    val altitude = raw.optJSONObject("altitude")
    val motion = raw.optJSONObject("motion")
    val aircraft = raw.optJSONObject("aircraft")
    val time = raw.optJSONObject("time")
    val normalizedId = raw.stringOrNull("id")
    val rawId = identifiers.stringOrNull("sourceId") ?: normalizedId
    val type = raw.firstStringOrNull("kind", "targetKind", "trafficKind", "objectType", "vehicleType", "targetType")
    val source = raw.diagnosticSourceLabel(origins)
    val fields = diagnosticFields(
        "scope" to "app_api_payload",
        "source" to source,
        "rawId" to rawId,
        "callsign" to identifiers.stringOrNull("callsign"),
        "registration" to identifiers.stringOrNull("registration"),
        "type" to type,
        "aircraftType" to aircraft.stringOrNull("type"),
        "category" to aircraft.stringOrNull("category"),
        "deviceType" to (raw.stringOrNull("deviceType") ?: aircraft.stringOrNull("deviceType")),
        "lat" to position.numberOrNull("lat")?.diagnosticNumber(6),
        "lon" to position.numberOrNull("lon")?.diagnosticNumber(6),
        "alt" to altitude.firstNumberOrNull("aglM", "mslM", "geoM", "baroM", "sourceM")?.diagnosticNumber(1),
        "speed" to motion.numberOrNull("groundSpeedMps")?.diagnosticNumber(2),
        "heading" to motion.numberOrNull("headingDeg")?.diagnosticNumber(1),
        "track" to motion.numberOrNull("trackDeg")?.diagnosticNumber(1),
        "timestamp" to time.longOrNull("timestamp")
    )
    val key = rawId ?: normalizedId ?: "${source}:${position.numberOrNull("lat")}:${position.numberOrNull("lon")}"
    val signature = listOf(source, rawId, type, aircraft.stringOrNull("type"), raw.stringOrNull("deviceType")).joinToString("|")
    TrafficDiagnosticLogger.log("RAW", key, signature, fields, nowMillis)
}

internal fun TrafficTarget.logNormalizedTrafficTarget(nowMillis: Long = System.currentTimeMillis()) {
    val origins = diagnosticOrigins()
    if (origins.isEmpty()) return

    val altitude = diagnosticAltitude()
    val fields = diagnosticFields(
        "source" to diagnosticSourceLabel(origins),
        "rawId" to (identifiers.sourceId ?: provenance?.contributions?.firstNotNullOfOrNull { it.sourceId }),
        "normalizedId" to id,
        "targetType" to trafficTargetKind().name,
        "sourceType" to trafficFeedType().name,
        "category" to aircraft.category,
        "lat" to position.lat.diagnosticNumber(6),
        "lon" to position.lon.diagnosticNumber(6),
        "alt" to altitude?.first?.diagnosticNumber(1),
        "altRef" to altitude?.second,
        "speed" to motion.groundSpeedMps?.diagnosticNumber(2),
        "heading" to motion.headingDeg?.diagnosticNumber(1),
        "track" to motion.trackDeg?.diagnosticNumber(1),
        "ageMs" to diagnosticAgeMillis(nowMillis),
        "classificationReason" to trafficClassificationReason()
    )
    val signature = listOf(
        diagnosticSourceLabel(origins),
        identifiers.sourceId,
        trafficTargetKind().name,
        trafficFeedType().name,
        trafficClassificationReason()
    ).joinToString("|")
    TrafficDiagnosticLogger.log("NORMALIZED", id, signature, fields, nowMillis)
}

internal fun logTrafficDeduplicationDiagnostics(
    targets: List<TrafficTarget>,
    nowMillis: Long = System.currentTimeMillis()
) {
    targets.filter { target ->
        target.provenance?.contributions.orEmpty().size > 1 && target.diagnosticOrigins().isNotEmpty()
    }
        .forEach { target -> logObservedUpstreamMerge(target, nowMillis) }

    diagnosticDscOgnCandidates(targets).forEach { candidate ->
        val dscType = candidate.dscTarget.trafficTargetKind().name
        val ognType = candidate.ognTarget.trafficTargetKind().name
        val pairKey = "${candidate.dscTarget.id}|${candidate.ognTarget.id}"
        val signature = "$dscType|$ognType|${candidate.identityMatch}|KEEP_BOTH"
        TrafficDiagnosticLogger.log(
            stage = "POSSIBLE_DUPLICATE",
            key = pairKey,
            significantSignature = signature,
            message = diagnosticFields(
                "dscId" to candidate.dscTarget.id,
                "ognId" to candidate.ognTarget.id,
                "distanceMeters" to candidate.distanceMeters.diagnosticNumber(1),
                "timeDeltaMs" to candidate.timeDeltaMillis,
                "dscType" to dscType,
                "ognType" to ognType
            ),
            nowMillis = nowMillis
        )
        TrafficDiagnosticLogger.log(
            stage = "DEDUPE",
            key = pairKey,
            significantSignature = signature,
            message = "candidateA(${diagnosticFields("source" to candidate.dscTarget.diagnosticSourceLabel(), "id" to candidate.dscTarget.id, "type" to dscType)}) " +
                "candidateB(${diagnosticFields("source" to candidate.ognTarget.diagnosticSourceLabel(), "id" to candidate.ognTarget.id, "type" to ognType)}) " +
                diagnosticFields(
                    "distanceMeters" to candidate.distanceMeters.diagnosticNumber(1),
                    "timeDeltaMs" to candidate.timeDeltaMillis,
                    "identityMatch" to candidate.identityMatch,
                    "decision" to "KEEP_BOTH",
                    "reason" to "no_android_cross_source_dedupe"
                ),
            nowMillis = nowMillis
        )
    }
}

internal fun diagnosticDscOgnCandidates(targets: List<TrafficTarget>): List<TrafficDuplicateDiagnostic> {
    val dscTargets = targets.filter { TrafficDiagnosticOrigin.DSC in it.diagnosticOrigins() }
    val ognTargets = targets.filter { TrafficDiagnosticOrigin.OGN in it.diagnosticOrigins() }

    // Diagnostic-only window: reuse existing proximity/freshness thresholds, never merge or drop.
    return dscTargets.flatMap { dsc ->
        ognTargets.mapNotNull { ogn ->
            if (dsc === ogn || dsc.id == ogn.id) return@mapNotNull null
            val distanceMeters = distanceMeters(dsc.position, ogn.position)
            val timeDeltaMillis = dsc.diagnosticTimeDeltaMillis(ogn)
            val closeInTime = timeDeltaMillis == null ||
                timeDeltaMillis <= (TrafficRelevanceThresholds.FreshMotionMaxAgeSec * 1_000.0).toLong()
            if (distanceMeters > TrafficRelevanceThresholds.DroneAttentionDistanceM || !closeInTime) {
                return@mapNotNull null
            }
            TrafficDuplicateDiagnostic(
                dscTarget = dsc,
                ognTarget = ogn,
                distanceMeters = distanceMeters,
                timeDeltaMillis = timeDeltaMillis,
                identityMatch = dsc.diagnosticIdentityMatch(ogn)
            )
        }
    }
}

internal fun TrafficTarget.logFinalTrafficTarget(
    displayType: String,
    icon: String,
    nowMillis: Long = System.currentTimeMillis()
) {
    val origins = diagnosticOrigins()
    if (origins.isEmpty()) return
    val altitude = diagnosticAltitude()
    val fields = diagnosticFields(
        "id" to id,
        "source" to diagnosticSourceLabel(origins),
        "targetType" to trafficTargetKind().name,
        "displayType" to displayType,
        "icon" to icon,
        "lat" to position.lat.diagnosticNumber(6),
        "lon" to position.lon.diagnosticNumber(6),
        "alt" to altitude?.first?.diagnosticNumber(1),
        "altRef" to altitude?.second,
        "ageMs" to diagnosticAgeMillis(nowMillis)
    )
    val signature = listOf(diagnosticSourceLabel(origins), trafficTargetKind().name, displayType, icon).joinToString("|")
    TrafficDiagnosticLogger.log("FINAL", id, signature, fields, nowMillis)
}

private fun logObservedUpstreamMerge(target: TrafficTarget, nowMillis: Long) {
    val contributions = target.provenance?.contributions.orEmpty()
    val first = contributions.firstOrNull() ?: return
    contributions.drop(1).forEach { candidate ->
        val key = "${first.id}|${candidate.id}|${target.id}"
        val identityMatch = if (target.identifiers.icao24 != null && target.id.startsWith("icao:", ignoreCase = true)) {
            "ICAO24"
        } else {
            "UNKNOWN"
        }
        val signature = "$identityMatch|MERGE"
        TrafficDiagnosticLogger.log(
            stage = "DEDUPE",
            key = key,
            significantSignature = signature,
            message = "candidateA(${diagnosticFields("source" to first.diagnosticSourceLabel(), "id" to first.id)}) " +
                "candidateB(${diagnosticFields("source" to candidate.diagnosticSourceLabel(), "id" to candidate.id)}) " +
                diagnosticFields(
                    "timeDeltaMs" to first.timestamp?.let { firstTimestamp -> candidate.timestamp?.let { abs(firstTimestamp - it) } },
                    "identityMatch" to identityMatch,
                    "decision" to "MERGE",
                    "stage" to "upstream_observed",
                    "normalizedId" to target.id
                ),
            nowMillis = nowMillis
        )
    }
}

private fun TrafficTarget.diagnosticIdentityMatch(other: TrafficTarget): String {
    val own = diagnosticIdentifiers()
    val candidate = other.diagnosticIdentifiers()
    own.forEach { (ownField, ownValue) ->
        candidate.forEach { (candidateField, candidateValue) ->
            if (ownValue == candidateValue) return "$ownField:$candidateField"
        }
    }
    return "NONE"
}

private fun TrafficTarget.diagnosticIdentifiers(): List<Pair<String, String>> =
    listOfNotNull(
        identifiers.icao24.diagnosticIdentifier("icao24"),
        identifiers.registration.diagnosticIdentifier("registration"),
        identifiers.callsign.diagnosticIdentifier("callsign"),
        identifiers.sourceId.diagnosticIdentifier("sourceId")
    )

private fun String?.diagnosticIdentifier(field: String): Pair<String, String>? =
    this?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() }?.let { field to it }

private fun TrafficTarget.diagnosticTimeDeltaMillis(other: TrafficTarget): Long? =
    when {
        time.timestamp != null && other.time.timestamp != null -> abs(time.timestamp - other.time.timestamp)
        time.ageSec != null && other.time.ageSec != null -> abs(((time.ageSec - other.time.ageSec) * 1_000.0).toLong())
        else -> null
    }

private fun TrafficTarget.diagnosticAgeMillis(nowMillis: Long): Long? =
    time.ageSec
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let { (it * 1_000.0).toLong() }
        ?: time.timestamp?.let { (nowMillis - it).coerceAtLeast(0L) }

private fun TrafficTarget.diagnosticAltitude(): Pair<Double, String>? =
    when {
        altitude.aglM?.isFinite() == true -> altitude.aglM to "aglM"
        altitude.mslM?.isFinite() == true -> altitude.mslM to "mslM"
        altitude.geoM?.isFinite() == true -> altitude.geoM to "geoM"
        altitude.baroM?.isFinite() == true -> altitude.baroM to "baroM"
        altitude.sourceM?.isFinite() == true -> altitude.sourceM to (altitude.sourceReference ?: "sourceM")
        else -> null
    }

private fun TrafficTarget.diagnosticOrigins(): Set<TrafficDiagnosticOrigin> =
    buildList {
        add(id)
        listOfNotNull(provider, source, objectType).forEach(::add)
        sources.forEach { listOfNotNull(it.provider, it.source).forEach(::add) }
        provenance?.sources?.forEach { listOfNotNull(it.provider, it.source).forEach(::add) }
        provenance?.contributions?.forEach {
            listOfNotNull(it.id, it.provider, it.source, it.sourceId).forEach(::add)
        }
    }.diagnosticOrigins()

private fun JSONObject.diagnosticOrigins(): Set<TrafficDiagnosticOrigin> {
    val values = buildList {
        listOf("id", "provider", "source", "objectType", "kind", "targetKind", "trafficKind", "vehicleType", "targetType")
            .mapNotNullTo(this) { stringOrNull(it) }
        addJsonSourceTokens(optJSONArray("sources"))
        val provenance = optJSONObject("provenance")
        addJsonSourceTokens(provenance?.optJSONArray("sources"))
        addJsonSourceTokens(provenance?.optJSONArray("contributions"))
    }
    return values.diagnosticOrigins()
}

private fun MutableList<String>.addJsonSourceTokens(array: JSONArray?) {
    if (array == null) return
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        listOf("id", "provider", "source", "sourceId").mapNotNullTo(this) { item.stringOrNull(it) }
    }
}

private fun List<String>.diagnosticOrigins(): Set<TrafficDiagnosticOrigin> =
    buildSet {
        this@diagnosticOrigins.forEach { value ->
            val normalized = value.trim().lowercase(Locale.US).replace("_", " ").replace("-", " ")
            if (
                normalized.contains("dsc uas") || normalized.contains("airsense") ||
                normalized.contains("air sense") || normalized.contains("tracker mini") ||
                normalized.contains("drone pilot app")
            ) {
                add(TrafficDiagnosticOrigin.DSC)
            }
            if (normalized == "ogn" || normalized.startsWith("ogn:") || normalized.contains("open glider network")) {
                add(TrafficDiagnosticOrigin.OGN)
            }
        }
    }

private fun TrafficTarget.diagnosticSourceLabel(origins: Set<TrafficDiagnosticOrigin> = diagnosticOrigins()): String =
    listOfNotNull(
        origins.joinToString("+") { it.name }.takeIf { it.isNotBlank() },
        provider,
        source
    ).distinct().joinToString("/")

private fun JSONObject.diagnosticSourceLabel(origins: Set<TrafficDiagnosticOrigin>): String =
    listOfNotNull(
        origins.joinToString("+") { it.name }.takeIf { it.isNotBlank() },
        stringOrNull("provider"),
        stringOrNull("source")
    ).distinct().joinToString("/")

private fun TrafficContribution.diagnosticSourceLabel(): String =
    listOfNotNull(provider, source).distinct().joinToString("/")

private fun distanceMeters(a: TrafficPosition, b: TrafficPosition): Double {
    val lat1 = a.lat * PI / 180.0
    val lat2 = b.lat * PI / 180.0
    val deltaLat = (b.lat - a.lat) * PI / 180.0
    val deltaLon = (b.lon - a.lon) * PI / 180.0
    val haversine = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
        cos(lat1) * cos(lat2) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
    return EarthRadiusMeters * 2.0 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}

private fun diagnosticFields(vararg fields: Pair<String, Any?>): String =
    fields.mapNotNull { (name, value) -> value?.toString()?.takeIf { it.isNotBlank() }?.let { "$name=${it.logValue()}" } }
        .joinToString(" ")

private fun String.logValue(): String =
    if (any { it.isWhitespace() }) "\"${replace("\"", "'")}\"" else this

private fun Double.diagnosticNumber(decimals: Int): String = String.format(Locale.US, "%.${decimals}f", this)

private fun JSONObject?.stringOrNull(name: String): String? {
    val json = this ?: return null
    if (!json.has(name) || json.isNull(name)) return null
    return json.opt(name)?.toString()?.trim()?.takeIf { it.isNotBlank() }
}

private fun JSONObject?.numberOrNull(name: String): Double? {
    val json = this ?: return null
    if (!json.has(name) || json.isNull(name)) return null
    return when (val value = json.opt(name)) {
        is Number -> value.toDouble()
        else -> value?.toString()?.toDoubleOrNull()
    }?.takeIf { it.isFinite() }
}

private fun JSONObject?.longOrNull(name: String): Long? {
    val json = this ?: return null
    if (!json.has(name) || json.isNull(name)) return null
    return when (val value = json.opt(name)) {
        is Number -> value.toLong()
        else -> value?.toString()?.toLongOrNull()
    }
}

private fun JSONObject.firstStringOrNull(vararg names: String): String? =
    names.firstNotNullOfOrNull { stringOrNull(it) }

private fun JSONObject?.firstNumberOrNull(vararg names: String): Double? =
    names.firstNotNullOfOrNull { numberOrNull(it) }
