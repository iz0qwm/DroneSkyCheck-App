package it.droneskycheck.app.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject

data class LegalTimelineResponse(
    val generatedAt: Instant?,
    val query: LegalTimelineQuery,
    val window: LegalTimelineWindow,
    val segments: List<LegalTimelineSegment>,
    val diagnostics: List<LegalTimelineDiagnostic>,
    val meta: LegalTimelineMeta
) {
    fun currentSegment(now: Instant = Instant.now()): LegalTimelineSegment? =
        segments.firstOrNull { segment -> !now.isBefore(segment.from) && now.isBefore(segment.to) }
}

data class LegalTimelineQuery(
    val lat: Double?,
    val lon: Double?
)

data class LegalTimelineWindow(
    val from: Instant?,
    val to: Instant?,
    val timezone: String?
)

data class LegalTimelineSegment(
    val from: Instant,
    val to: Instant,
    val state: LegalTimelineState,
    val rawState: String,
    val maxAltitudeAgl: Int?,
    val authorization: LegalTimelineAuthorization?,
    val contributors: List<LegalTimelineContributor>,
    val warnings: List<String>,
    val confidence: String?,
    val reasonCodes: List<String>
) {
    fun contains(instant: Instant): Boolean =
        !instant.isBefore(from) && instant.isBefore(to)
}

enum class LegalTimelineState {
    AVAILABLE,
    AVAILABLE_WITH_LIMIT,
    AUTH_REQUIRED,
    UNAVAILABLE,
    UNKNOWN
}

data class LegalTimelineAuthorization(
    val required: Boolean?,
    val resolutionStatus: String?,
    val procedures: List<LegalTimelineAuthorizationProcedure>,
    val additionalRequirements: List<LegalTimelineAuthorizationRequirement>,
    val reasonCodes: List<String>,
    val blockingReasons: List<LegalTimelineAuthorizationBlockingReason>
)

data class LegalTimelineAuthorizationProcedure(
    val type: String?,
    val label: String?,
    val reasonCode: String?
)

data class LegalTimelineAuthorizationRequirement(
    val type: String?,
    val label: String?,
    val reasonCode: String?
)

data class LegalTimelineAuthorizationBlockingReason(
    val code: String?
)

data class LegalTimelineContributor(
    val id: String?,
    val sourceType: String?,
    val designator: String?,
    val role: List<String>,
    val temporalPolicy: String?,
    val operationalRelevance: String?,
    val maxAltitudeAgl: Int?,
    val reasonCodes: List<String>,
    val warnings: List<String>
)

data class LegalTimelineDiagnostic(
    val code: String?,
    val severity: String?,
    val sourceType: String?,
    val designator: String?,
    val message: String?
)

data class LegalTimelineMeta(
    val schemaVersion: Int?,
    val engine: String?,
    val version: String?,
    val maxWindowHours: Int?
)

fun parseLegalTimelineResponse(json: JSONObject): LegalTimelineResponse =
    LegalTimelineResponse(
        generatedAt = json.optStringOrNull("generatedAt").toInstantOrNull(),
        query = json.optJSONObject("query").toLegalTimelineQuery(),
        window = json.optJSONObject("window").toLegalTimelineWindow(),
        segments = json.optJSONArray("segments").toObjectList { it.toLegalTimelineSegment() },
        diagnostics = json.optJSONArray("diagnostics").toObjectList { it.toLegalTimelineDiagnostic() },
        meta = json.optJSONObject("meta").toLegalTimelineMeta()
    )

fun LegalTimelineSegment.formatLocalRange(
    zoneId: ZoneId = ZoneId.systemDefault(),
    formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
): String {
    val localFrom = from.atZone(zoneId)
    val localTo = to.atZone(zoneId)
    val fromText = formatter.format(localFrom)
    val toText = formatter.format(localTo)
    val dayDelta = ChronoUnit.DAYS.between(localFrom.toLocalDate(), localTo.toLocalDate())
    return when (dayDelta) {
        0L -> "$fromText - $toText"
        1L -> "$fromText - domani $toText"
        else -> "$fromText - ${DateTimeFormatter.ofPattern("dd/MM HH:mm").format(localTo)}"
    }
}

private fun JSONObject?.toLegalTimelineQuery(): LegalTimelineQuery {
    val json = this ?: JSONObject()
    return LegalTimelineQuery(
        lat = json.optDoubleOrNull("lat"),
        lon = json.optDoubleOrNull("lon")
    )
}

private fun JSONObject?.toLegalTimelineWindow(): LegalTimelineWindow {
    val json = this ?: JSONObject()
    return LegalTimelineWindow(
        from = json.optStringOrNull("from").toInstantOrNull(),
        to = json.optStringOrNull("to").toInstantOrNull(),
        timezone = json.optStringOrNull("timezone")
    )
}

private fun JSONObject.toLegalTimelineSegment(): LegalTimelineSegment {
    val from = optStringOrNull("from").toInstantOrNull()
    val to = optStringOrNull("to").toInstantOrNull()
    val rawState = optStringOrNull("state").orEmpty()
    return LegalTimelineSegment(
        from = requireNotNull(from) { "Invalid legal timeline segment.from" },
        to = requireNotNull(to) { "Invalid legal timeline segment.to" },
        state = rawState.toLegalTimelineState(),
        rawState = rawState,
        maxAltitudeAgl = optIntOrNull("maxAltitudeAgl"),
        authorization = optJSONObject("authorization")?.toLegalTimelineAuthorization(),
        contributors = optJSONArray("contributors").toObjectList { it.toLegalTimelineContributor() },
        warnings = optJSONArray("warnings").toStringList(),
        confidence = optStringOrNull("confidence"),
        reasonCodes = optJSONArray("reasonCodes").toStringList()
    )
}

private fun JSONObject.toLegalTimelineAuthorization(): LegalTimelineAuthorization =
    LegalTimelineAuthorization(
        required = optBooleanOrNull("required"),
        resolutionStatus = optStringOrNull("resolutionStatus"),
        procedures = optJSONArray("procedures").toObjectList { it.toLegalTimelineAuthorizationProcedure() },
        additionalRequirements = optJSONArray("additionalRequirements").toObjectList {
            it.toLegalTimelineAuthorizationRequirement()
        },
        reasonCodes = optJSONArray("reasonCodes").toStringList(),
        blockingReasons = optJSONArray("blockingReasons").toObjectList {
            it.toLegalTimelineAuthorizationBlockingReason()
        }
    )

private fun JSONObject.toLegalTimelineAuthorizationProcedure(): LegalTimelineAuthorizationProcedure =
    LegalTimelineAuthorizationProcedure(
        type = optStringOrNull("type"),
        label = optStringOrNull("label"),
        reasonCode = optStringOrNull("reasonCode")
    )

private fun JSONObject.toLegalTimelineAuthorizationRequirement(): LegalTimelineAuthorizationRequirement =
    LegalTimelineAuthorizationRequirement(
        type = optStringOrNull("type"),
        label = optStringOrNull("label"),
        reasonCode = optStringOrNull("reasonCode")
    )

private fun JSONObject.toLegalTimelineAuthorizationBlockingReason(): LegalTimelineAuthorizationBlockingReason =
    LegalTimelineAuthorizationBlockingReason(
        code = optStringOrNull("code")
    )

private fun JSONObject.toLegalTimelineContributor(): LegalTimelineContributor =
    LegalTimelineContributor(
        id = optStringOrNull("id"),
        sourceType = optStringOrNull("sourceType"),
        designator = optStringOrNull("designator"),
        role = optJSONArray("role").toStringList(),
        temporalPolicy = optStringOrNull("temporalPolicy"),
        operationalRelevance = optStringOrNull("operationalRelevance"),
        maxAltitudeAgl = optIntOrNull("maxAltitudeAgl"),
        reasonCodes = optJSONArray("reasonCodes").toStringList(),
        warnings = optJSONArray("warnings").toStringList()
    )

private fun JSONObject.toLegalTimelineDiagnostic(): LegalTimelineDiagnostic =
    LegalTimelineDiagnostic(
        code = optStringOrNull("code"),
        severity = optStringOrNull("severity"),
        sourceType = optStringOrNull("sourceType"),
        designator = optStringOrNull("designator"),
        message = optStringOrNull("message")
    )

private fun JSONObject?.toLegalTimelineMeta(): LegalTimelineMeta {
    val json = this ?: JSONObject()
    return LegalTimelineMeta(
        schemaVersion = json.optIntOrNull("schemaVersion"),
        engine = json.optStringOrNull("engine"),
        version = json.optStringOrNull("version"),
        maxWindowHours = json.optIntOrNull("maxWindowHours")
    )
}

private fun String.toLegalTimelineState(): LegalTimelineState =
    when (uppercase()) {
        "AVAILABLE" -> LegalTimelineState.AVAILABLE
        "AVAILABLE_WITH_LIMIT" -> LegalTimelineState.AVAILABLE_WITH_LIMIT
        "AUTH_REQUIRED" -> LegalTimelineState.AUTH_REQUIRED
        "UNAVAILABLE" -> LegalTimelineState.UNAVAILABLE
        "UNKNOWN" -> LegalTimelineState.UNKNOWN
        else -> LegalTimelineState.UNKNOWN
    }

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        opt(index)?.toString()?.takeIf { it.isNotBlank() }
    }
}

private fun <T> JSONArray?.toObjectList(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.let(transform)
    }
}

private fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return opt(name)?.toString()?.takeIf { it.isNotBlank() }
}

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Number -> optDouble(name)
        else -> optString(name).toDoubleOrNull()
    }?.takeIf { it.isFinite() }

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

private fun String?.toInstantOrNull(): Instant? =
    this?.let { runCatching { Instant.parse(it) }.getOrNull() }
