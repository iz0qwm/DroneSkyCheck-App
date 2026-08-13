package it.droneskycheck.app.data.help

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime

data class HelpManifestParseResult(
    val manifest: HelpManifest?,
    val warnings: List<HelpManifestWarning> = emptyList()
) {
    val isValid: Boolean
        get() = manifest != null
}

data class HelpManifestWarning(
    val code: HelpManifestWarningCode,
    val message: String
)

enum class HelpManifestWarningCode {
    JSON_MALFORMED,
    UNSUPPORTED_SCHEMA,
    MISSING_FIELD,
    INVALID_FIELD,
    DUPLICATE_ID,
    UNKNOWN_TARGET,
    UNKNOWN_ACTION,
    INVALID_ORDER
}

object HelpManifestParser {
    const val SupportedSchemaVersion = 1

    fun parse(json: String): HelpManifestParseResult {
        val warnings = mutableListOf<HelpManifestWarning>()
        val root = try {
            JSONObject(json.ifBlank { "{}" })
        } catch (err: JSONException) {
            return HelpManifestParseResult(
                manifest = null,
                warnings = listOf(HelpManifestWarning(HelpManifestWarningCode.JSON_MALFORMED, err.message.orEmpty()))
            )
        }

        val schemaVersion = root.optInt("schemaVersion", 0)
        if (schemaVersion != SupportedSchemaVersion) {
            return HelpManifestParseResult(
                manifest = null,
                warnings = listOf(
                    HelpManifestWarning(
                        HelpManifestWarningCode.UNSUPPORTED_SCHEMA,
                        "Unsupported schemaVersion $schemaVersion"
                    )
                )
            )
        }

        val contentVersion = root.optInt("contentVersion", 0)
        if (contentVersion <= 0) {
            warnings += HelpManifestWarning(HelpManifestWarningCode.MISSING_FIELD, "Missing contentVersion")
            return HelpManifestParseResult(null, warnings)
        }
        val onboardingVersion = root.optInt("onboardingVersion", 0).takeIf { it > 0 } ?: contentVersion
        val updatedAt = root.optStringOrNull("updatedAt")?.also {
            if (!it.isHelpDateOrDateTime()) {
                warnings += HelpManifestWarning(HelpManifestWarningCode.INVALID_FIELD, "Invalid updatedAt")
            }
        }

        val steps = parseSteps(root.optJSONObject("onboarding")?.optJSONArray("steps"), warnings)
        val topics = parseTopics(root.optJSONArray("topics"), warnings)
        return HelpManifestParseResult(
            manifest = HelpManifest(
                schemaVersion = schemaVersion,
                contentVersion = contentVersion,
                updatedAt = updatedAt,
                onboardingVersion = onboardingVersion,
                onboardingSteps = steps,
                topics = topics
            ),
            warnings = warnings
        )
    }

    private fun parseSteps(
        array: JSONArray?,
        warnings: MutableList<HelpManifestWarning>
    ): List<HelpOnboardingStep> {
        if (array == null) {
            warnings += HelpManifestWarning(HelpManifestWarningCode.MISSING_FIELD, "Missing onboarding.steps")
            return emptyList()
        }

        val ids = mutableSetOf<String>()
        val orders = mutableSetOf<Int>()
        return array.toObjectList().mapNotNull { item ->
            val id = item.optString("id").trim()
            val title = item.optString("title").trim()
            val text = item.optString("text").trim()
            val targetName = item.optString("target").trim()
            val actionName = item.optStringOrNull("action")
            val order = item.optInt("order", 0)
            val target = HelpTourTarget.fromWireName(targetName)
            val action = HelpTourAction.fromWireName(actionName)

            when {
                id.isBlank() -> {
                    warnings += HelpManifestWarning(HelpManifestWarningCode.MISSING_FIELD, "Onboarding step missing id")
                    null
                }
                !ids.add(id) -> {
                    warnings += HelpManifestWarning(HelpManifestWarningCode.DUPLICATE_ID, "Duplicate onboarding step $id")
                    null
                }
                title.isBlank() || text.isBlank() -> {
                    warnings += HelpManifestWarning(HelpManifestWarningCode.MISSING_FIELD, "Onboarding step $id missing title/text")
                    null
                }
                target == null -> {
                    warnings += HelpManifestWarning(HelpManifestWarningCode.UNKNOWN_TARGET, "Unknown target $targetName")
                    null
                }
                order <= 0 -> {
                    warnings += HelpManifestWarning(HelpManifestWarningCode.INVALID_ORDER, "Invalid order for step $id")
                    null
                }
                else -> {
                    if (!orders.add(order)) {
                        warnings += HelpManifestWarning(HelpManifestWarningCode.INVALID_ORDER, "Duplicate onboarding order $order")
                    }
                    if (action == null) {
                        warnings += HelpManifestWarning(HelpManifestWarningCode.UNKNOWN_ACTION, "Unknown action $actionName")
                    }
                    HelpOnboardingStep(
                        id = id,
                        target = target,
                        action = action ?: HelpTourAction.NONE,
                        title = title,
                        text = text,
                        order = order
                    )
                }
            }
        }.sortedWith(compareBy<HelpOnboardingStep> { it.order }.thenBy { it.id })
    }

    private fun parseTopics(
        array: JSONArray?,
        warnings: MutableList<HelpManifestWarning>
    ): List<HelpTopic> {
        if (array == null) {
            warnings += HelpManifestWarning(HelpManifestWarningCode.MISSING_FIELD, "Missing topics")
            return emptyList()
        }

        val ids = mutableSetOf<String>()
        val orders = mutableSetOf<Int>()
        return array.toObjectList().mapNotNull { item ->
            val id = item.optString("id").trim()
            val title = item.optString("title").trim()
            val summary = item.optString("summary").trim()
            val order = item.optInt("order", 0)
            when {
                id.isBlank() -> {
                    warnings += HelpManifestWarning(HelpManifestWarningCode.MISSING_FIELD, "Topic missing id")
                    null
                }
                !ids.add(id) -> {
                    warnings += HelpManifestWarning(HelpManifestWarningCode.DUPLICATE_ID, "Duplicate topic $id")
                    null
                }
                title.isBlank() || summary.isBlank() -> {
                    warnings += HelpManifestWarning(HelpManifestWarningCode.MISSING_FIELD, "Topic $id missing title/summary")
                    null
                }
                order <= 0 -> {
                    warnings += HelpManifestWarning(HelpManifestWarningCode.INVALID_ORDER, "Invalid topic order for $id")
                    null
                }
                else -> {
                    if (!orders.add(order)) {
                        warnings += HelpManifestWarning(HelpManifestWarningCode.INVALID_ORDER, "Duplicate topic order $order")
                    }
                    HelpTopic(
                        id = id,
                        title = title,
                        summary = summary,
                        introduction = item.optStringOrNull("introduction"),
                        blocks = parseBlocks(item.optJSONArray("blocks"), warnings, id),
                        image = item.optStringOrNull("image"),
                        imageAlt = item.optStringOrNull("imageAlt"),
                        introducedInVersion = item.optIntOrNull("introducedInVersion"),
                        order = order
                    )
                }
            }
        }.sortedWith(compareBy<HelpTopic> { it.order }.thenBy { it.id })
    }

    private fun parseBlocks(
        array: JSONArray?,
        warnings: MutableList<HelpManifestWarning>,
        topicId: String
    ): List<HelpContentBlock> {
        val blocks = array ?: return emptyList()
        return blocks.toObjectList().mapNotNull { item ->
            when (item.optString("type").trim().lowercase()) {
                "paragraph" -> item.optString("text").trim()
                    .takeIf { it.isNotBlank() }
                    ?.let(HelpContentBlock::Paragraph)
                    ?: run {
                        warnings += HelpManifestWarning(HelpManifestWarningCode.MISSING_FIELD, "Paragraph in $topicId missing text")
                        null
                    }
                "bulletlist", "bullet_list" -> item.optJSONArray("items").toStringList()
                    .takeIf { it.isNotEmpty() }
                    ?.let(HelpContentBlock::BulletList)
                    ?: run {
                        warnings += HelpManifestWarning(HelpManifestWarningCode.MISSING_FIELD, "Bullet list in $topicId missing items")
                        null
                    }
                "note" -> item.optString("text").trim()
                    .takeIf { it.isNotBlank() }
                    ?.let(HelpContentBlock::Note)
                    ?: run {
                        warnings += HelpManifestWarning(HelpManifestWarningCode.MISSING_FIELD, "Note in $topicId missing text")
                        null
                    }
                "image" -> item.optString("src").trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { src ->
                        HelpContentBlock.Image(
                            src = src,
                            alt = item.optStringOrNull("alt")
                        )
                    }
                    ?: run {
                        warnings += HelpManifestWarning(HelpManifestWarningCode.MISSING_FIELD, "Image in $topicId missing src")
                        null
                    }
                else -> {
                    warnings += HelpManifestWarning(HelpManifestWarningCode.INVALID_FIELD, "Unknown block type in $topicId")
                    null
                }
            }
        }
    }
}

private fun JSONArray?.toObjectList(): List<JSONObject> {
    val array = this ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

private fun JSONArray?.toStringList(): List<String> {
    val array = this ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optString(index).trim().takeIf { it.isNotBlank() }
    }
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).trim().takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name).takeIf { it > 0 }

private fun String.isHelpDateOrDateTime(): Boolean =
    runCatching { LocalDate.parse(this) }.isSuccess ||
        runCatching { OffsetDateTime.parse(this) }.isSuccess
