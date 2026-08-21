package it.droneskycheck.app.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

private const val ZoneCheckParserLogTag = "DscZoneCheckV3"

interface ZoneCheckV3Client {
    fun check(lat: Double, lon: Double): ZoneCheckV3Response
}

class ZoneCheckV3Repository(
    private val endpointUrl: String = DscApiConfig.ZoneCheckV3Url,
    private val apiKey: String = DscApiConfig.ApiKey
) : ZoneCheckV3Client {
    override fun check(lat: Double, lon: Double): ZoneCheckV3Response {
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
    val zoneName = identity?.optFirstString("name", "title")
        ?: optFirstString("name", "title", "zoneName")
    val classificationObject = optJSONObject("classification")
    val uasLimit = optJSONObject("uasLimit")
    val info = optJSONObject("info")
    val authority = optJSONObject("authority")
    val authorization = optJSONObject("authorization").toAuthorizationInfo()
    val details = optJSONObject("details")
    val temporalDetailsObject = optJSONObject("temporalDetails") ?: details?.optJSONObject("temporal")
    val officialDetailsObject = optJSONObject("officialDetails") ?: details?.optJSONObject("official")
    val enrDetailsObject = optJSONObject("enrDetails") ?: details?.optJSONObject("enr")
    val validity = listOfNotNull(
        optJSONObject("validity")?.toValidityInfo(),
        temporalDetailsObject?.toValidityInfo()
    ).firstOrNull { it.hasParserContent() }
    val enrichedObject = optJSONObject("enriched") ?: optJSONObject("enrichedData")
    val sup = optJSONObject("sup")?.toSupInfo()
    val uasGeographicalZone = optJSONObject("uasGeographicalZone")?.toUasGeographicalZoneInfo()
    val enrCandidates = listOfNotNull(
        optJSONObject("enr")?.let { EnrJsonCandidate("enr", it) },
        enrDetailsObject?.let { EnrJsonCandidate("enrDetails", it) },
        enrichedObject?.takeIf { it.hasInlineEnrContent() }?.let { EnrJsonCandidate("enriched", it) },
        takeIf { hasInlineEnrContent() }?.let { EnrJsonCandidate("zoneInline", it) }
    )
    enrCandidates.forEach { candidate ->
        candidate.json.logEnrCandidate(zoneName, candidate.source)
    }
    val enr = enrCandidates.map { it.json }.filter { it.isPotentialEnrObject() }.firstNotNullOfOrNull { candidate ->
        candidate.toEnrInfo(temporalDetailsObject).takeIf { it.hasParserContent() }
    }
    DscLogger.debug(
        ZoneCheckParserLogTag,
        "ENR parsed zone='${zoneName.orEmpty()}' result=${enr != null} " +
            "name='${enr?.name.orEmpty()}' classification='${enr?.classification.orEmpty()}' " +
            "scheduleRaw='${enr?.schedule?.raw.orEmpty()}'"
    )
    val official = (optJSONObject("official") ?: optJSONObject("source") ?: optJSONObject("raw"))
        ?.toOfficialInfo()
        ?: toOfficialInfoFromInlineFields()
        ?: officialDetailsObject?.toOfficialInfo()
        ?: enr?.official

    return ZoneInfo(
        id = identity?.optFirstString("id", "identifier", "zoneId")
            ?: optFirstString("id", "identifier", "zoneId"),
        name = zoneName,
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
            ?: optFirstString("description", "descrizione")
            ?: enr?.description
            ?: sup?.combinedDescription()
            ?: uasGeographicalZone?.combinedDescription(),
        official = official,
        info = info?.toZoneNarrative(),
        validity = validity,
        authorization = authorization,
        authority = authority?.toAuthorityInfo(),
        operationalStatus = optFirstString("operationalStatus", "status", "state"),
        notams = optArray("notams", "notam").toObjectList { it.toNotamInfo() },
        enr = enr,
        sup = sup,
        uasGeographicalZone = uasGeographicalZone,
        blockers = optArray("blockers").toIssueList(),
        warnings = optArray("warnings").toIssueList(),
        enriched = optEnrichedData(),
        authorizationRequired = authorization?.required
            ?: authorization?.derivedRequired()
            ?: authority?.optFirstBoolean("authorizationRequired", "required")
            ?: optFirstBoolean("authorizationRequired"),
        activeNow = validity?.activeNow
            ?: temporalDetailsObject?.optFirstBoolean("activeNow")
            ?: optFirstBoolean("activeNow"),
        isVerdictSource = optFirstBoolean("isVerdictSource", "responsibleForVerdict", "limiting")
    )
}

private data class EnrJsonCandidate(
    val source: String,
    val json: JSONObject
)

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
        schedule = optJSONObject("schedule")?.toScheduleInfo(),
        official = (optJSONObject("official") ?: optJSONObject("raw"))?.toOfficialInfo()
            ?: toOfficialInfoFromInlineFields(),
        validity = optJSONObject("validity")?.toValidityInfo() ?: toValidityInfo(),
        weekSchedule = optArray("weekSchedule").toTemporalBarEntries(),
        daySchedule = optArray("daySchedule").toNullableBooleanList(),
        blockers = optArray("blockers").toIssueList(),
        warnings = optArray("warnings").toIssueList()
    )

private fun JSONObject.toEnrInfo(temporalDetails: JSONObject? = null): EnrInfo {
    val enrichment = optJSONObject("enrichment")
    val classification = optFirstString("classification")
        ?: enrichment?.optFirstString("classification")
        ?: optFirstString("enrType")?.let { "ENR $it" }
        ?: enrichment?.optFirstString("enrType")?.let { "ENR $it" }

    return EnrInfo(
        code = optFirstString("code", "reference", "id", "aip", "enrRef")
            ?: enrichment?.optFirstString("code", "reference", "id", "aip", "enrRef"),
        name = optFirstString("name", "title")
            ?: enrichment?.optFirstString("name", "title", "aip"),
        description = optFirstString("description", "descrizione", "desc")
            ?: enrichment?.optFirstString("description", "descrizione", "desc"),
        limitText = optFirstString("limitText") ?: enrichment?.optFirstString("limitText"),
        notes = optFirstString("notes") ?: enrichment?.optFirstString("notes", "note"),
        classification = classification,
        activationType = optFirstString("activationType") ?: enrichment?.optFirstString("activationType"),
        operationMode = optFirstString("operationMode") ?: enrichment?.optFirstString("operationMode"),
        operationCategory = optFirstString("operationCategory") ?: enrichment?.optFirstString("operationCategory"),
        requiredLicense = optFirstString("requiredLicense") ?: enrichment?.optFirstString("requiredLicense"),
        authorizationRequired = optFirstBoolean("authorizationRequired") ?: enrichment?.optFirstBoolean("authorizationRequired"),
        schedule = optJSONObject("schedule")?.toScheduleInfo()
            ?: enrichment?.optJSONObject("schedule")?.toScheduleInfo()
            ?: temporalDetails?.toScheduleInfo()
            ?: takeIf { optFirstString("rawSchedule", "scheduleRaw", "humanSchedule", "scheduleHuman") != null }
                ?.toScheduleInfo(),
        authority = (optJSONObject("authority") ?: enrichment?.optJSONObject("authority"))?.toAuthorityInfo(),
        official = (optJSONObject("official") ?: optJSONObject("source"))?.toOfficialInfo()
            ?: toOfficialInfoFromInlineFields(),
        validity = optJSONObject("validity")?.toValidityInfo()
            ?: enrichment?.optJSONObject("validity")?.toValidityInfo()
            ?: temporalDetails?.toValidityInfo()
            ?: toValidityInfo(),
        explanation = optFirstString("explanation") ?: enrichment?.optFirstString("explanation"),
        operationalMeaning = optFirstString("operationalMeaning", "meaning")
            ?: enrichment?.optFirstString("operationalMeaning", "meaning"),
        weekSchedule = (optArray("weekSchedule") ?: temporalDetails?.optArray("weekSchedule")).toTemporalBarEntries(),
        daySchedule = (optArray("daySchedule") ?: temporalDetails?.optArray("daySchedule")).toNullableBooleanList()
    )
}

private fun JSONObject.hasInlineEnrContent(): Boolean =
    optCleanString("descrizione") != null ||
        optCleanString("aip") != null ||
        optCleanString("enrType") != null ||
        optCleanString("enrRef") != null ||
        optCleanString("sourceFile")?.contains("ENR", ignoreCase = true) == true

private fun JSONObject.isPotentialEnrObject(): Boolean {
    if (optFirstBoolean("hasEnr") == false || optFirstBoolean("present") == false) return false
    if (optFirstBoolean("hasEnr") == true || optFirstBoolean("present") == true) return true
    if (hasInlineEnrContent()) return true
    if (optJSONObject("enrichment")?.hasInlineEnrContent() == true) return true
    if (optFirstString("source").equals("ENR", ignoreCase = true)) return true
    if (optFirstString("sourcePipeline")?.contains("ENR", ignoreCase = true) == true) return true
    if (optFirstString("classification")?.contains("ENR", ignoreCase = true) == true) return true
    if (optJSONObject("schedule") != null || optJSONObject("official") != null) {
        return optFirstString("code", "reference", "aip", "enrRef") != null ||
            optFirstString("name", "title") != null
    }
    return false
}

private fun JSONObject.logEnrCandidate(zoneName: String?, source: String) {
    DscLogger.debug(
        ZoneCheckParserLogTag,
        "ENR candidate zone='${zoneName.orEmpty()}' source=$source " +
            "potential=${isPotentialEnrObject()} hasEnr=${optFirstBoolean("hasEnr")} " +
            "present=${optFirstBoolean("present")} type='${optFirstString("type").orEmpty()}' " +
            "family='${optFirstString("family").orEmpty()}' classification='${optFirstString("classification").orEmpty()}' " +
            "sourceField='${optFirstString("source").orEmpty()}' sourcePipeline='${optFirstString("sourcePipeline").orEmpty()}' " +
            "name='${optFirstString("name", "title").orEmpty()}' code='${optFirstString("code", "reference", "aip", "enrRef").orEmpty()}' " +
            "keys=${keySummary()}"
    )
}

private fun JSONObject.keySummary(): String =
    buildList {
        keys().forEachRemaining { add(it) }
    }
        .take(16)
        .joinToString(prefix = "[", postfix = "]")

private fun EnrInfo.hasParserContent(): Boolean =
    !code.isNullOrBlank() ||
        !name.isNullOrBlank() ||
        !description.isNullOrBlank() ||
        !limitText.isNullOrBlank() ||
        !notes.isNullOrBlank() ||
        !classification.isNullOrBlank() ||
        !activationType.isNullOrBlank() ||
        !operationMode.isNullOrBlank() ||
        !operationCategory.isNullOrBlank() ||
        !requiredLicense.isNullOrBlank() ||
        authorizationRequired != null ||
        schedule != null ||
        authority != null ||
        official != null ||
        validity?.hasParserContent() == true ||
        !explanation.isNullOrBlank() ||
        !operationalMeaning.isNullOrBlank() ||
        weekSchedule.isNotEmpty() ||
        daySchedule.isNotEmpty()

private fun JSONObject.toSupInfo(): SupInfo =
    SupInfo(
        title = optFirstString("title", "name", "sup"),
        reference = optFirstString("reference", "code", "id", "sup"),
        generality = optFirstString("generality", "general", "generalita"),
        description = optFirstString("description", "descrizione", "summary"),
        operationMode = optFirstString("operationMode"),
        operationCategory = optFirstString("operationCategory"),
        requiredLicense = optFirstString("requiredLicense"),
        authorizationRequired = optFirstBoolean("authorizationRequired"),
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

private fun JSONObject.toUasGeographicalZoneInfo(): UasGeographicalZoneInfo =
    UasGeographicalZoneInfo(
        id = optFirstString("id", "uassec", "name", "title"),
        generality = optFirstString("generality", "general", "generalita"),
        description = optFirstString("description", "descrizione", "summary"),
        schedule = optFirstString("schedule", "orariRaw"),
        operationMode = optFirstString("operationMode"),
        operationCategory = optFirstString("operationCategory"),
        requiredLicense = optFirstString("requiredLicense"),
        authorizationRequired = optFirstBoolean("authorizationRequired"),
        authority = optJSONObject("authority")?.toAuthorityInfo(),
        confidence = optFirstString("confidence")
    )

private fun SupInfo.combinedDescription(): String? =
    listOf(generality, description).joinDistinctNonBlank()

private fun UasGeographicalZoneInfo.combinedDescription(): String? =
    listOf(generality, description).joinDistinctNonBlank()

private fun JSONObject.toZoneNarrative(): ZoneNarrative =
    ZoneNarrative(
        summary = optFirstString("summary", "title"),
        explanation = optFirstString("explanation"),
        operationalMeaning = optFirstString("operationalMeaning", "meaning")
    )

private fun JSONObject?.toAuthorizationInfo(): AuthorizationInfo? {
    if (this == null) return null

    return AuthorizationInfo(
        required = optFirstBoolean("required", "authorizationRequired"),
        requirement = optFirstString("requirement", "authorizationRequirement"),
        operationMode = optFirstString("operationMode"),
        operationCategory = optFirstString("operationCategory"),
        requiredLicense = optFirstString("requiredLicense"),
        explanation = optFirstString("explanation"),
        applicability = optFirstString("applicability"),
        resolutionStatus = optFirstString("resolutionStatus"),
        procedures = optArray("procedures").toObjectList { it.toAuthorizationProcedure() },
        additionalRequirements = optArray("additionalRequirements").toObjectList {
            it.toAuthorizationAdditionalRequirement()
        },
        reasonCodes = optArray("reasonCodes").toStringList(),
        blockingReasons = optArray("blockingReasons").toObjectList {
            it.toAuthorizationBlockingReason()
        },
        resolverVersion = optFirstInt("resolverVersion")
    )
}

private fun JSONObject.toAuthorizationProcedure(): AuthorizationProcedure =
    AuthorizationProcedure(
        type = optFirstString("type"),
        version = optFirstInt("version"),
        label = optFirstString("label"),
        reasonCode = optFirstString("reasonCode")
    )

private fun JSONObject.toAuthorizationAdditionalRequirement(): AuthorizationAdditionalRequirement =
    AuthorizationAdditionalRequirement(
        type = optFirstString("type"),
        label = optFirstString("label"),
        reasonCode = optFirstString("reasonCode")
    )

private fun JSONObject.toAuthorizationBlockingReason(): AuthorizationBlockingReason =
    AuthorizationBlockingReason(
        code = optFirstString("code")
    )

private fun JSONObject.toAuthorityInfo(): AuthorityInfo {
    val authorityObjects = authorityObjects()
    val emails = authorityObjects
        .flatMap { it.optAuthorityEmails() }
        .distinctBy { it.lowercase() }
    val note = authorityObjects.firstCleanString("note", "notes")
    val name = authorityObjects.firstCleanString("name")
        ?: authorityObjects.firstCleanString("authority")
        ?: note
    val contact = emails.joinToString(", ")
        .ifBlank { authorityObjects.firstCleanString("contact", "phone", "email").orEmpty() }
        .ifBlank { null }

    return AuthorityInfo(
        name = name,
        code = authorityObjects.firstCleanString("code"),
        contact = contact,
        source = authorityObjects.firstCleanString("source", "sourceReference"),
        emails = emails,
        note = note
    )
}

private fun JSONObject.toValidityInfo(): ValidityInfo =
    ValidityInfo(
        activeNow = optFirstBoolean("activeNow"),
        validFrom = optFirstString("validFrom", "from", "B"),
        validTo = optFirstString("validTo", "to", "C"),
        schedule = optFirstString("schedule", "originalSchedule", "rawSchedule", "scheduleRaw", "D"),
        interpretedSchedule = optFirstString(
            "interpretedSchedule",
            "scheduleExplanation",
            "scheduleHuman",
            "humanSchedule"
        ),
        nextActivation = optFirstString("nextActivation", "nextActiveAt", "nextActivationAt"),
        explanation = optFirstString("explanation", "stateExplanation"),
        future = optFirstBoolean("future", "isFuture"),
        expired = optFirstBoolean("expired", "isExpired")
    )

private fun ValidityInfo.hasParserContent(): Boolean =
    activeNow != null ||
        !validFrom.isNullOrBlank() ||
        !validTo.isNullOrBlank() ||
        !schedule.isNullOrBlank() ||
        !interpretedSchedule.isNullOrBlank() ||
        !nextActivation.isNullOrBlank() ||
        !explanation.isNullOrBlank() ||
        future != null ||
        expired != null

private fun JSONObject.toScheduleInfo(): ScheduleInfo =
    ScheduleInfo(
        raw = optFirstString("raw", "rawSchedule", "scheduleRaw", "original", "schedule"),
        human = optFirstString("human", "scheduleHuman", "humanSchedule", "interpretedSchedule"),
        activeNow = optFirstBoolean("activeNow"),
        explanation = optFirstString("explanation")
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

private fun AuthorizationInfo.derivedRequired(): Boolean? =
    when {
        procedures.isNotEmpty() || additionalRequirements.isNotEmpty() -> true
        applicability.equals("NONE", ignoreCase = true) -> false
        else -> null
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

private fun JSONObject.authorityObjects(): List<JSONObject> =
    buildList {
        add(this@authorityObjects)
        listOf("authority", "contact", "email").forEach { key ->
            when (val value = opt(key)) {
                is JSONObject -> add(value)
                is String -> value.toJsonObjectOrNull()?.let { add(it) }
            }
        }
    }

private fun List<JSONObject>.firstCleanString(vararg names: String): String? =
    firstNotNullOfOrNull { json ->
        names.firstNotNullOfOrNull { name -> json.optCleanString(name) }
    }

private fun JSONObject.optCleanString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    val value = opt(name)
    if (value is JSONObject || value is JSONArray) return null
    val text = value?.toString()?.trim().orEmpty()
    if (text.isBlank() || text.toJsonObjectOrNull() != null) return null
    return text
}

private fun JSONObject.optAuthorityEmails(): List<String> =
    buildList {
        listOf("emails", "email", "pec", "pecs").forEach { name ->
            when (val value = opt(name)) {
                is JSONArray -> {
                    for (index in 0 until value.length()) {
                        when (val item = value.opt(index)) {
                            is JSONObject -> addAll(item.optAuthorityEmails())
                            else -> add(item?.toString().orEmpty())
                        }
                    }
                }
                is JSONObject -> addAll(value.optAuthorityEmails())
                is String -> add(value)
            }
        }
    }
        .flatMap { it.split(',', ';') }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

private fun String.toJsonObjectOrNull(): JSONObject? =
    trim()
        .takeIf { it.startsWith("{") && it.endsWith("}") }
        ?.let { runCatching { JSONObject(it) }.getOrNull() }

private fun List<String?>.joinDistinctNonBlank(): String? =
    mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .distinctBy { it.lowercase() }
        .joinToString("\n\n")
        .takeIf { it.isNotBlank() }

private fun <T> JSONArray?.toObjectList(transform: (JSONObject) -> T): List<T> =
    this.toMixedList { value ->
        (value as? JSONObject)?.let(transform)
    }

private fun JSONArray?.toStringList(): List<String> =
    this.toMixedList { value ->
        value?.toString()?.takeIf { it.isNotBlank() }
    }

private fun JSONArray?.toNullableBooleanList(): List<Boolean?> {
    if (this == null) return emptyList()
    return (0 until length()).map { index ->
        when (val value = opt(index)) {
            is Boolean -> value
            is Number -> value.toDouble() > 0.0
            is JSONObject -> value.optFirstBoolean("active", "enabled")
                ?: value.optFirstDouble("activeRatio", "ratio")?.let { it > 0.0 }
            else -> null
        }
    }
}

private fun JSONArray?.toTemporalBarEntries(): List<TemporalBarEntry> {
    if (this == null) return emptyList()
    return (0 until length()).map { index ->
        when (val value = opt(index)) {
            is Boolean -> TemporalBarEntry(active = value, activeRatio = if (value) 1f else 0f)
            is Number -> value.toDouble().coerceIn(0.0, 1.0).let {
                TemporalBarEntry(active = it >= 1.0, activeRatio = it.toFloat())
            }
            is JSONObject -> {
                val ratio = value.optFirstDouble("activeRatio", "ratio")?.coerceIn(0.0, 1.0)
                TemporalBarEntry(
                    active = value.optFirstBoolean("active", "enabled") ?: ratio?.let { it >= 1.0 },
                    activeRatio = ratio?.toFloat(),
                    segments = value.optArray("segments").toObjectList { segment ->
                        TemporalBarSegment(
                            start = (segment.optFirstDouble("start") ?: 0.0).coerceIn(0.0, 1.0).toFloat(),
                            end = (segment.optFirstDouble("end") ?: 0.0).coerceIn(0.0, 1.0).toFloat()
                        )
                    }
                )
            }
            else -> TemporalBarEntry(active = null)
        }
    }
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

private fun JSONObject.optFirstDouble(vararg names: String): Double? =
    names.firstNotNullOfOrNull { name ->
        when {
            !has(name) || isNull(name) -> null
            opt(name) is Number -> optDouble(name)
            else -> optString(name).toDoubleOrNull()
        }
    }
