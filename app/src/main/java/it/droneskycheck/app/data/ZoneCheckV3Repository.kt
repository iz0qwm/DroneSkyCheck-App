package it.droneskycheck.app.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class ZoneCheckV3Repository(
    private val endpointUrl: String = DscApiConfig.ZoneCheckV3Url,
    private val apiKey: String = DscApiConfig.ApiKey
) {
    fun check(lat: Double, lon: Double): ZoneCheckV3Response {
        val url = URL(
            "$endpointUrl?lat=${encode(lat)}&lon=${encode(lon)}"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TimeoutMillis
            readTimeout = TimeoutMillis
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-api-key", apiKey)
        }

        return try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (statusCode !in 200..299) {
                throw IllegalStateException("zoneCheckV3 HTTP $statusCode")
            }

            parseZoneCheckV3Response(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: Double): String =
        URLEncoder.encode(value.toString(), Charsets.UTF_8.name())

    private companion object {
        const val TimeoutMillis = 8_000
    }
}

internal fun parseZoneCheckV3Response(json: JSONObject): ZoneCheckV3Response {
    val verdict = json.optJSONObject("verdict") ?: JSONObject()
    val baseline = json.optJSONObject("baseline") ?: JSONObject()
    val meta = json.optJSONObject("meta") ?: JSONObject()
    val responsibleZone = json.optJSONObject("responsibleZone")
        ?: json.optJSONObject("limitingZone")
        ?: json.optJSONObject("verdictZone")

    return ZoneCheckV3Response(
        position = (json.optJSONObject("position") ?: JSONObject()).let {
            Position(
                lat = it.optDouble("lat"),
                lon = it.optDouble("lon")
            )
        },
        verdict = Verdict(
            status = verdict.optString("status"),
            maxAltitudeMetersAgl = verdict.optInt("maxAltitudeMetersAgl"),
            source = verdict.optFirstString("source"),
            explanation = verdict.optString("explanation"),
            baselineMetersAgl = verdict.optFirstInt("baselineMetersAgl", "baseline"),
            isBaseline = verdict.optFirstBoolean("isBaseline"),
            responsibleZoneId = verdict.optFirstString("responsibleZoneId", "limitingZoneId", "zoneId"),
            responsibleZoneName = verdict.optFirstString("responsibleZoneName", "limitingZoneName", "zoneName")
        ),
        zones = json.optArray("zones").toObjectList { it.toZoneInfo() },
        blockers = json.optArray("blockers").toIssueList(),
        warnings = json.optArray("warnings").toIssueList(),
        baseline = Baseline(
            maxAltitudeMetersAgl = baseline.optInt("maxAltitudeMetersAgl"),
            representedAsZone = baseline.optBoolean("representedAsZone")
        ),
        meta = Meta(
            engine = meta.optString("engine"),
            version = meta.optString("version")
        ),
        responsibleZone = responsibleZone?.let {
            ResponsibleZone(
                id = it.optFirstString("id", "zoneId", "identifier"),
                name = it.optFirstString("name", "zoneName"),
                reason = it.optFirstString("reason", "explanation")
            )
        }
    )
}

private fun JSONObject.toZoneInfo(): ZoneInfo {
    val identity = optJSONObject("identity")
    val classificationObject = optJSONObject("classification")
    val uasLimit = optJSONObject("uasLimit")
    val info = optJSONObject("info")
    val authority = optJSONObject("authority")
    val validity = optJSONObject("validity")?.toValidityInfo()
    val authorization = (optJSONObject("authorization") ?: authority).toAuthorizationInfo()
    val official = (optJSONObject("official") ?: optJSONObject("source") ?: optJSONObject("raw"))
        ?.toOfficialInfo()
        ?: toOfficialInfoFromInlineFields()

    return ZoneInfo(
        id = identity?.optFirstString("id", "identifier", "zoneId")
            ?: optFirstString("id", "identifier", "zoneId"),
        name = identity?.optFirstString("name", "title")
            ?: optFirstString("name", "title", "zoneName"),
        code = identity?.optFirstString("code", "ref", "reference")
            ?: optFirstString("code", "ref", "reference"),
        family = classificationObject?.optFirstString("family")
            ?: optFirstString("family"),
        type = classificationObject?.optFirstString("type")
            ?: optFirstString("type"),
        classification = classificationObject?.optFirstString("classification", "label", "description")
            ?: optFirstString("classification"),
        limitMetersAgl = uasLimit?.optFirstInt("metersAgl", "maxAltitudeMetersAgl", "limitMetersAgl")
            ?: optFirstInt("limitMetersAgl", "maxAltitudeMetersAgl", "maxAltitude", "upperLimit"),
        verticalLimits = (optJSONObject("verticalLimits") ?: uasLimit)?.toVerticalLimits(),
        description = info?.optFirstString("description")
            ?: optFirstString("description"),
        official = official,
        info = info?.toZoneNarrative(),
        validity = validity,
        authorization = authorization,
        authority = authority?.toAuthorityInfo(),
        operationalStatus = optFirstString("operationalStatus", "status", "state"),
        notams = optArray("notams", "notam").toObjectList { it.toNotamInfo() },
        enr = optJSONObject("enr")?.toEnrInfo(),
        sup = optJSONObject("sup")?.toSupInfo(),
        blockers = optArray("blockers").toIssueList(),
        warnings = optArray("warnings").toIssueList(),
        enriched = optEnrichedData(),
        authorizationRequired = authorization.required
            ?: authority?.optFirstBoolean("authorizationRequired", "required")
            ?: optFirstBoolean("authorizationRequired"),
        activeNow = validity?.activeNow ?: optFirstBoolean("activeNow"),
        isVerdictSource = optFirstBoolean("isVerdictSource", "responsibleForVerdict", "limiting")
    )
}

private fun JSONObject.toNotamInfo(): NotamInfo =
    NotamInfo(
        code = optFirstString("code", "number", "notamNumber", "id"),
        fir = optFirstString("fir", "FIR"),
        location = optFirstString("location", "aerodrome", "A"),
        zoneReference = optFirstString("zoneReference", "zoneRef", "zoneName"),
        activityType = optFirstString("activityType", "activity", "type"),
        severity = optFirstString("severity"),
        summary = optFirstString("summary", "title"),
        explanation = optFirstString("explanation"),
        operationalMeaning = optFirstString("operationalMeaning", "meaning"),
        blockingReason = optFirstString("blockingReason"),
        official = (optJSONObject("official") ?: optJSONObject("raw"))?.toOfficialInfo()
            ?: toOfficialInfoFromInlineFields(),
        validity = optJSONObject("validity")?.toValidityInfo() ?: toValidityInfo(),
        blockers = optArray("blockers").toIssueList(),
        warnings = optArray("warnings").toIssueList()
    )

private fun JSONObject.toEnrInfo(): EnrInfo =
    EnrInfo(
        code = optFirstString("code", "reference", "id"),
        name = optFirstString("name", "title"),
        classification = optFirstString("classification", "type"),
        activationType = optFirstString("activationType"),
        operationMode = optFirstString("operationMode"),
        operationCategory = optFirstString("operationCategory"),
        requiredLicense = optFirstString("requiredLicense"),
        authorizationRequired = optFirstBoolean("authorizationRequired"),
        authority = optJSONObject("authority")?.toAuthorityInfo(),
        official = (optJSONObject("official") ?: optJSONObject("source"))?.toOfficialInfo()
            ?: toOfficialInfoFromInlineFields(),
        validity = optJSONObject("validity")?.toValidityInfo() ?: toValidityInfo(),
        explanation = optFirstString("explanation"),
        operationalMeaning = optFirstString("operationalMeaning", "meaning")
    )

private fun JSONObject.toSupInfo(): SupInfo =
    SupInfo(
        title = optFirstString("title", "name"),
        reference = optFirstString("reference", "code", "id"),
        generality = optFirstString("generality", "general"),
        description = optFirstString("description", "summary"),
        authority = optJSONObject("authority")?.toAuthorityInfo(),
        official = (optJSONObject("official") ?: optJSONObject("source"))?.toOfficialInfo()
            ?: toOfficialInfoFromInlineFields(),
        validity = optJSONObject("validity")?.toValidityInfo() ?: toValidityInfo(),
        authorization = optJSONObject("authorization").toAuthorizationInfo(),
        explanation = optFirstString("explanation"),
        operationalMeaning = optFirstString("operationalMeaning", "meaning"),
        blockers = optArray("blockers").toIssueList(),
        warnings = optArray("warnings").toIssueList()
    )

private fun JSONObject.toZoneNarrative(): ZoneNarrative =
    ZoneNarrative(
        summary = optFirstString("summary", "title"),
        explanation = optFirstString("explanation"),
        operationalMeaning = optFirstString("operationalMeaning", "meaning")
    )

private fun JSONObject?.toAuthorizationInfo(): AuthorizationInfo =
    AuthorizationInfo(
        required = this?.optFirstBoolean("required", "authorizationRequired"),
        requirement = this?.optFirstString("requirement", "authorizationRequirement"),
        operationMode = this?.optFirstString("operationMode"),
        operationCategory = this?.optFirstString("operationCategory"),
        requiredLicense = this?.optFirstString("requiredLicense"),
        explanation = this?.optFirstString("explanation")
    )

private fun JSONObject.toAuthorityInfo(): AuthorityInfo =
    AuthorityInfo(
        name = optFirstString("name", "authority"),
        code = optFirstString("code"),
        contact = optFirstString("contact", "email", "phone"),
        source = optFirstString("source", "sourceReference")
    )

private fun JSONObject.toValidityInfo(): ValidityInfo =
    ValidityInfo(
        activeNow = optFirstBoolean("activeNow"),
        validFrom = optFirstString("validFrom", "from", "B"),
        validTo = optFirstString("validTo", "to", "C"),
        schedule = optFirstString("schedule", "originalSchedule", "D"),
        interpretedSchedule = optFirstString("interpretedSchedule", "scheduleExplanation"),
        explanation = optFirstString("explanation", "stateExplanation"),
        future = optFirstBoolean("future", "isFuture"),
        expired = optFirstBoolean("expired", "isExpired")
    )

private fun JSONObject.toOfficialInfo(): OfficialInfo =
    OfficialInfo(
        sourceText = optFirstString("sourceText", "officialText", "rawText", "icaoText", "text", "E"),
        sourceReference = optFirstString("sourceReference", "reference", "source"),
        qLine = optFirstString("qLine", "Q"),
        fields = collectOfficialFields()
    )

private fun JSONObject.toOfficialInfoFromInlineFields(): OfficialInfo? {
    val officialText = optFirstString("sourceText", "officialText", "rawText", "icaoText", "officialDescription")
    val sourceReference = optFirstString("sourceReference", "reference")
    val fields = collectOfficialFields()
    if (officialText == null && sourceReference == null && fields.isEmpty()) return null

    return OfficialInfo(
        sourceText = officialText,
        sourceReference = sourceReference,
        qLine = optFirstString("qLine", "Q"),
        fields = fields
    )
}

private fun JSONObject.toVerticalLimits(): VerticalLimits =
    VerticalLimits(
        lower = optFirstString("lower", "lowerLimit"),
        upper = optFirstString("upper", "upperLimit", "limit"),
        lowerMetersAgl = optFirstInt("lowerMetersAgl"),
        upperMetersAgl = optFirstInt("upperMetersAgl", "metersAgl", "limitMetersAgl", "maxAltitudeMetersAgl")
    )

private fun JSONObject.toIssue(): Issue =
    Issue(
        code = optFirstString("code", "id"),
        zoneName = optFirstString("zoneName", "name"),
        message = optFirstString("message", "summary"),
        severity = optFirstString("severity"),
        explanation = optFirstString("explanation"),
        operationalMeaning = optFirstString("operationalMeaning", "meaning")
    )

private fun JSONArray?.toIssueList(): List<Issue> =
    this.toMixedList { value ->
        when (value) {
            is JSONObject -> value.toIssue()
            is String -> Issue(code = value.takeIf { it.isNotBlank() }, zoneName = null)
            else -> null
        }
    }

private fun JSONObject.collectOfficialFields(): List<KeyValueInfo> =
    buildList {
        val fieldObject = optJSONObject("fields") ?: optJSONObject("originalFields")
        fieldObject?.keys()?.forEachRemaining { key ->
            fieldObject.optFirstString(key)?.let { add(KeyValueInfo(key = key, value = it)) }
        }
        listOf("Q", "A", "B", "C", "D", "E", "F", "G").forEach { key ->
            optFirstString(key)?.let { value ->
                if (none { it.key == key }) add(KeyValueInfo(key = key, value = value))
            }
        }
    }

private fun JSONObject.optEnrichedData(): List<KeyValueInfo> {
    val enriched = optJSONObject("enriched") ?: optJSONObject("enrichedData") ?: return emptyList()
    return buildList {
        enriched.keys().forEachRemaining { key ->
            enriched.optFirstString(key)?.let { add(KeyValueInfo(key = key, value = it)) }
        }
    }
}

private fun JSONObject.optArray(vararg names: String): JSONArray? =
    names.firstNotNullOfOrNull { name ->
        val value = opt(name)
        when (value) {
            is JSONArray -> value
            is JSONObject -> JSONArray().put(value)
            else -> null
        }
    }

private fun <T> JSONArray?.toObjectList(transform: (JSONObject) -> T): List<T> =
    this.toMixedList { value ->
        (value as? JSONObject)?.let(transform)
    }

private fun <T> JSONArray?.toMixedList(transform: (Any) -> T?): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        transform(opt(index))
    }
}

private fun JSONObject.optFirstString(vararg names: String): String? =
    names.firstNotNullOfOrNull { name ->
        if (!has(name) || isNull(name)) {
            null
        } else {
            opt(name)?.toString()?.takeIf { it.isNotBlank() }
        }
    }

private fun JSONObject.optFirstInt(vararg names: String): Int? =
    names.firstNotNullOfOrNull { name ->
        when {
            !has(name) || isNull(name) -> null
            opt(name) is Number -> optInt(name)
            else -> optString(name).toIntOrNull()
        }
    }

private fun JSONObject.optFirstBoolean(vararg names: String): Boolean? =
    names.firstNotNullOfOrNull { name ->
        when {
            !has(name) || isNull(name) -> null
            opt(name) is Boolean -> optBoolean(name)
            optString(name).equals("true", ignoreCase = true) -> true
            optString(name).equals("false", ignoreCase = true) -> false
            else -> null
        }
    }
