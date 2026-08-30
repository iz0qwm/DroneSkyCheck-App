package it.droneskycheck.app.data.ai

import it.droneskycheck.app.data.LocalDrone
import org.json.JSONArray
import org.json.JSONObject

data class AiAssistantLocation(
    val lat: Double,
    val lon: Double
)

data class AiAssistantContext(
    val location: AiAssistantLocation? = null,
    val aircraftModel: String? = null,
    val classMark: String? = null,
    val massGrams: Double? = null,
    val cameraPresent: Boolean? = null
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            location?.let {
                put(
                    "location",
                    JSONObject()
                        .put("lat", it.lat)
                        .put("lon", it.lon)
                )
            }
            aircraftModel?.let { put("aircraftModel", it) }
            classMark?.let { put("classMark", it) }
            massGrams?.let { put("massGrams", it) }
            cameraPresent?.let { put("cameraPresent", it) }
        }

    companion object {
        fun from(location: AiAssistantLocation?, drone: LocalDrone?): AiAssistantContext {
            val aircraftModel = drone?.let {
                listOf(it.manufacturer, it.model)
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .joinToString(" ")
                    .takeIf(String::isNotBlank)
            }
            val massGrams = drone?.weight?.takeIf { it.isFinite() && it > 0.0 }
            val classMark = drone?.classLabel?.trim()?.takeIf(String::isNotBlank)
            val cameraPresent = drone?.cameras?.trim()?.takeIf(String::isNotBlank)?.let { true }
            return AiAssistantContext(
                location = location,
                aircraftModel = aircraftModel,
                classMark = classMark,
                massGrams = massGrams,
                cameraPresent = cameraPresent
            )
        }
    }
}

data class AiAssistantRequest(
    val query: String,
    val includeSources: Boolean = true,
    val includeDiagnostics: Boolean = false,
    val context: AiAssistantContext = AiAssistantContext()
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("query", query)
            .put("includeSources", includeSources)
            .put("includeDiagnostics", includeDiagnostics)
            .put("context", context.toJson())
}

data class AiAssistantResponse(
    val kind: AiAssistantResponseKind,
    val displayText: String,
    val sources: List<AiAssistantSource> = emptyList(),
    val status: String? = null,
    val route: String? = null,
    val mappedTextSource: String,
    val quota: AiAssistantQuota? = null
)

data class AiAssistantQuota(
    val capacity: Int,
    val remaining: Int,
    val unlimited: Boolean,
    val nextCreditAt: String? = null,
    val refillSeconds: Int? = null
)

enum class AiAssistantResponseKind {
    Answer,
    RegulatoryAnswer,
    ProductAnswer,
    Both,
    OperationalAnswer,
    NeedsContext,
    OutOfScope,
    Ambiguous,
    OperationalUnavailable,
    Error
}

data class AiAssistantSource(
    val group: AiAssistantSourceGroup? = null,
    val authority: String? = null,
    val document: String? = null,
    val section: String? = null,
    val page: String? = null,
    val title: String? = null
) {
    val hasUserVisibleContent: Boolean
        get() = listOf(authority, document, section, page, title).any { !it.isNullOrBlank() }
}

enum class AiAssistantSourceGroup {
    Regulatory,
    Product
}

fun parseAiAssistantResponse(json: JSONObject): AiAssistantResponse {
    val status = json.optStringOrNull("status")
    val route = json.optStringOrNull("route")
    val responseType = json.optStringOrNull("type")
        ?: json.optStringOrNull("kind")
    val kindRaw = status
        ?: responseType
        ?: route
        ?: "ANSWER"
    val regulatoryAnswer = json.optJSONObject("regulatoryAnswer")
    val productAnswer = json.optJSONObject("productAnswer")
    val kind = when (kindRaw.uppercase()) {
        "BOTH" -> AiAssistantResponseKind.Both
        "REGULATORY" -> AiAssistantResponseKind.RegulatoryAnswer
        "DSC_PRODUCT", "PRODUCT" -> AiAssistantResponseKind.ProductAnswer
        "OPERATIONAL_ANSWER" -> AiAssistantResponseKind.OperationalAnswer
        "DSC_OPERATIONAL_REQUIRED" -> AiAssistantResponseKind.OperationalAnswer
        "NEEDS_CONTEXT" -> AiAssistantResponseKind.NeedsContext
        "OUT_OF_SCOPE", "OUT_OF_DOMAIN", "UNSUPPORTED_QUERY", "UNSUPPORTED" -> AiAssistantResponseKind.OutOfScope
        "AMBIGUOUS", "UNCLEAR", "LOW_CONFIDENCE", "CLARIFICATION_NEEDED" -> AiAssistantResponseKind.Ambiguous
        "OPERATIONAL_UNAVAILABLE" -> AiAssistantResponseKind.OperationalUnavailable
        "ERROR" -> AiAssistantResponseKind.Error
        "OK" -> kindFromRouteOrNestedAnswers(route, regulatoryAnswer, productAnswer)
        else -> when {
            regulatoryAnswer != null && productAnswer != null -> AiAssistantResponseKind.Both
            regulatoryAnswer != null -> AiAssistantResponseKind.RegulatoryAnswer
            productAnswer != null -> AiAssistantResponseKind.ProductAnswer
            route?.uppercase() == "DSC_OPERATIONAL_REQUIRED" -> AiAssistantResponseKind.OperationalAnswer
            else -> AiAssistantResponseKind.Answer
        }
    }

    val mapped: MappedAiText = when (kind) {
        AiAssistantResponseKind.Both -> bothAnswerText(regulatoryAnswer, productAnswer)
        AiAssistantResponseKind.RegulatoryAnswer -> mappedText(
            regulatoryAnswer?.optStringOrNull("answer"),
            "regulatoryAnswer.answer"
        ) ?: topLevelAnswerText(json)
            ?: fallbackMappedText()
        AiAssistantResponseKind.ProductAnswer -> mappedText(
            productAnswer?.optStringOrNull("answer"),
            "productAnswer.answer"
        ) ?: topLevelAnswerText(json)
            ?: fallbackMappedText()
        AiAssistantResponseKind.OperationalAnswer -> mappedText(
            json.optJSONObject("operationalSummary")?.toOperationalSummaryText(),
            "operationalSummary"
        ) ?: mappedText(json.optStringOrNull("operationalAnswer"), "operationalAnswer")
            ?: mappedText(json.optStringOrNull("answer"), "answer")
            ?: fallbackMappedText()
        AiAssistantResponseKind.NeedsContext -> mappedText(json.optStringOrNull("nextQuestion"), "nextQuestion")
            ?: mappedText(json.optStringOrNull("answer"), "answer")
            ?: MappedAiText("Mi serve qualche dettaglio in più per rispondere con prudenza.", "fallback")
        AiAssistantResponseKind.OutOfScope -> MappedAiText(AiAssistantOutOfScopeMessage, "status.outOfScope")
        AiAssistantResponseKind.Ambiguous -> MappedAiText(AiAssistantAmbiguousQuestionMessage, "status.ambiguous")
        AiAssistantResponseKind.OperationalUnavailable -> mappedText(json.optStringOrNull("message"), "message")
            ?: fallbackMappedText()
        AiAssistantResponseKind.Error -> mappedText(json.optStringOrNull("message"), "message")
            ?: fallbackMappedText()
        AiAssistantResponseKind.Answer -> topLevelAnswerText(json)
            ?: mappedText(regulatoryAnswer?.optStringOrNull("answer"), "regulatoryAnswer.answer")
            ?: mappedText(productAnswer?.optStringOrNull("answer"), "productAnswer.answer")
            ?: fallbackMappedText()
    }

    return AiAssistantResponse(
        kind = kind,
        displayText = mapped.text,
        sources = json.collectAiSources(),
        status = status,
        route = route,
        mappedTextSource = mapped.path,
        quota = json.optJSONObject("quota")?.toAiAssistantQuota()
    )
}

fun parseAiAssistantQuotaResponse(json: JSONObject): AiAssistantQuota =
    (json.optJSONObject("quota") ?: json).toAiAssistantQuota()

const val AiAssistantUnavailableMessage = "L'Assistente DSC non è disponibile in questo momento. Riprova."
const val AiAssistantSelectPointMessage = "Posso verificarlo, ma ho bisogno del punto preciso. Selezionalo sulla mappa e poi chiedimi \"Posso volare qui?\"."
const val AiAssistantOutOfScopeMessage = "Non sono riuscito a collegare bene la domanda alle informazioni che posso verificare. Prova a riformularla in modo più specifico.\n\nPuoi chiedermi informazioni sulle regole di volo, sull'uso di Drone Sky Check o sul punto selezionato sulla mappa."
const val AiAssistantAmbiguousQuestionMessage = "Non sono sicuro di aver capito cosa vuoi sapere. Prova a riformulare la domanda aggiungendo qualche dettaglio."

fun localAiAssistantResponseFor(query: String, context: AiAssistantContext): AiAssistantResponse? {
    if (context.location != null) return null
    if (!query.isLocalizedOperationalFlightQuestion()) return null
    return AiAssistantResponse(
        kind = AiAssistantResponseKind.NeedsContext,
        displayText = AiAssistantSelectPointMessage,
        status = "NEEDS_CONTEXT",
        route = "LOCAL_CONTEXT_GATE",
        mappedTextSource = "local.context.location"
    )
}

private fun JSONObject.toAiAssistantQuota(): AiAssistantQuota =
    AiAssistantQuota(
        capacity = optInt("capacity", 5).coerceAtLeast(1),
        remaining = optInt("remaining", 0).coerceAtLeast(0),
        unlimited = optBoolean("unlimited", false),
        nextCreditAt = optStringOrNull("nextCreditAt"),
        refillSeconds = if (has("refillSeconds") && !isNull("refillSeconds")) optInt("refillSeconds") else null
    )

private fun JSONObject.toOperationalSummaryText(): String =
    listOfNotNull(
        optStringOrNull("headline"),
        optStringOrNull("summary"),
        optJSONArray("details")?.toStringList()?.joinToString("\n")
            ?: optStringOrNull("details")
    ).joinToString("\n\n").ifBlank { AiAssistantUnavailableMessage }

private data class MappedAiText(
    val text: String,
    val path: String
)

private fun mappedText(value: String?, path: String): MappedAiText? =
    value?.takeIf(String::isNotBlank)?.let { MappedAiText(it, path) }

private fun fallbackMappedText(): MappedAiText =
    MappedAiText(AiAssistantUnavailableMessage, "fallback")

private fun topLevelAnswerText(json: JSONObject): MappedAiText? =
    mappedText(json.optStringOrNull("answer"), "answer")
        ?: mappedText(json.optStringOrNull("response"), "response")
        ?: mappedText(json.optStringOrNull("text"), "text")

private fun bothAnswerText(
    regulatoryAnswer: JSONObject?,
    productAnswer: JSONObject?
): MappedAiText {
    val regulatory = regulatoryAnswer?.optStringOrNull("answer")
    val product = productAnswer?.optStringOrNull("answer")
    val sections = listOfNotNull(
        regulatory?.let { "Normativa\n\n$it" },
        product?.let { "Drone Sky Check\n\n$it" }
    )
    if (sections.isNotEmpty()) {
        return MappedAiText(
            text = sections.joinToString("\n\n"),
            path = listOfNotNull(
                "regulatoryAnswer.answer".takeIf { regulatory != null },
                "productAnswer.answer".takeIf { product != null }
            ).joinToString("+")
        )
    }
    return MappedAiText(AiAssistantUnavailableMessage, "fallback")
}

private fun JSONObject.collectAiSources(): List<AiAssistantSource> =
    buildList {
        addAll(optJSONArray("citations").toSourceList(null))
        addAll(optJSONArray("sources").toSourceList(null))
        addAll(optJSONObject("answer")?.optJSONArray("citations").toSourceList(null))
        addAll(optJSONObject("answer")?.optJSONArray("sources").toSourceList(null))
        val regulatory = optJSONObject("regulatoryAnswer")
        addAll(regulatory?.optJSONArray("citations").toSourceList(AiAssistantSourceGroup.Regulatory))
        addAll(regulatory?.optJSONArray("sources").toSourceList(AiAssistantSourceGroup.Regulatory))
        val product = optJSONObject("productAnswer")
        addAll(product?.optJSONArray("citations").toSourceList(AiAssistantSourceGroup.Product))
        addAll(product?.optJSONArray("sources").toSourceList(AiAssistantSourceGroup.Product))
    }
        .filter { it.hasUserVisibleContent }
        .distinct()

private fun JSONArray?.toSourceList(group: AiAssistantSourceGroup?): List<AiAssistantSource> {
    val array = this ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.toAiAssistantSource(group)
    }
}

private fun JSONObject.toAiAssistantSource(group: AiAssistantSourceGroup?): AiAssistantSource =
    AiAssistantSource(
        group = group,
        authority = optStringOrNull("authority"),
        document = optStringOrNull("document"),
        section = optStringOrNull("section"),
        page = optStringOrNull("page") ?: optStringOrNull("pages"),
        title = optStringOrNull("title")
    )

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { index ->
        opt(index)?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    }

private fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return opt(name)?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
}

fun JSONObject.toAiAssistantShapeLogSummary(): String {
    val regulatoryAnswer = optJSONObject("regulatoryAnswer")
    val productAnswer = optJSONObject("productAnswer")
    val hasBoth = regulatoryAnswer != null && productAnswer != null
    return listOf(
        "status=${optStringOrNull("status") ?: "missing"}",
        "route=${optStringOrNull("route") ?: "missing"}",
        "keys=${topLevelKeys().joinToString(prefix = "[", postfix = "]")}",
        "hasRegulatoryAnswer=${regulatoryAnswer != null}",
        "hasProductAnswer=${productAnswer != null}",
        "hasOperationalAnswer=${has("operationalAnswer") && !isNull("operationalAnswer")}",
        "hasOperationalSummary=${has("operationalSummary") && !isNull("operationalSummary")}",
        "operational=${optJSONObject("operationalSummary").operationalSummaryShape()}",
        "hasNextQuestion=${has("nextQuestion") && !isNull("nextQuestion")}",
        "both=$hasBoth",
        "regulatory=${regulatoryAnswer.answerShape()}",
        "product=${productAnswer.answerShape()}"
    ).joinToString(" ")
}

private fun kindFromRouteOrNestedAnswers(
    route: String?,
    regulatoryAnswer: JSONObject?,
    productAnswer: JSONObject?
): AiAssistantResponseKind =
    when (route?.uppercase()) {
        "BOTH" -> AiAssistantResponseKind.Both
        "REGULATORY" -> AiAssistantResponseKind.RegulatoryAnswer
        "DSC_PRODUCT", "PRODUCT" -> AiAssistantResponseKind.ProductAnswer
        "DSC_OPERATIONAL_REQUIRED" -> AiAssistantResponseKind.OperationalAnswer
        else -> when {
            regulatoryAnswer != null && productAnswer != null -> AiAssistantResponseKind.Both
            regulatoryAnswer != null -> AiAssistantResponseKind.RegulatoryAnswer
            productAnswer != null -> AiAssistantResponseKind.ProductAnswer
            else -> AiAssistantResponseKind.Answer
        }
    }

private fun String.isLocalizedOperationalFlightQuestion(): Boolean {
    val normalized = trim().lowercase()
    if (normalized.isBlank()) return false
    val asksFlightPermission = PermissionFlightRegex.containsMatchIn(normalized)
    if (!asksFlightPermission) return false
    return PlaceWithoutCoordinatesRegex.findAll(normalized).any { match ->
        val candidate = match.groupValues.getOrNull(1).orEmpty()
        candidate !in NonPlaceQuestionWords
    }
}

private val PermissionFlightRegex = Regex(
    """(?:\b(?:posso|possiamo|puo|può)\b.{0,40}\bvolare\b)|(?:\bsi\s+puo\b.{0,40}\bvolare\b)|(?:\bsi\s+può\b.{0,40}\bvolare\b)|(?:\bvolare\b.{0,40}\b(?:posso|possiamo|puo|può)\b)"""
)

private val PlaceWithoutCoordinatesRegex = Regex(
    """\b(?:a|ad)\s+([a-zà-ÿ][a-zà-ÿ'’.-]{2,})(?:\s+[a-zà-ÿ][a-zà-ÿ'’.-]{2,}){0,3}"""
)

private val NonPlaceQuestionWords = setOf(
    "che",
    "chi",
    "cosa",
    "dove",
    "quanto",
    "quale",
    "quali",
    "quando"
)

private fun JSONObject.topLevelKeys(): List<String> =
    keys().asSequence().toList().sorted()

private fun JSONObject?.answerShape(): String {
    val json = this ?: return "{present=false}"
    return listOf(
        "present=true",
        "hasAnswer=${json.has("answer") && !json.isNull("answer")}",
        "answerLength=${json.optStringOrNull("answer")?.length ?: 0}",
        "sources=${json.optJSONArray("sources")?.length() ?: 0}",
        "citations=${json.optJSONArray("citations")?.length() ?: 0}"
    ).joinToString(prefix = "{", postfix = "}")
}

private fun JSONObject?.operationalSummaryShape(): String {
    val json = this ?: return "{present=false}"
    return listOf(
        "present=true",
        "headlineLength=${json.optStringOrNull("headline")?.length ?: 0}",
        "summaryLength=${json.optStringOrNull("summary")?.length ?: 0}",
        "detailsType=${json.opt("details")?.javaClass?.simpleName ?: "missing"}",
        "detailsCount=${json.optJSONArray("details")?.length() ?: 0}"
    ).joinToString(prefix = "{", postfix = "}")
}
