package it.droneskycheck.app.data.beginner

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class BeginnerGuideManifest(
    val schemaVersion: Int,
    val contentVersion: String,
    val id: String,
    val title: String,
    val description: String,
    val language: String,
    val country: String,
    val audience: String,
    val pages: List<BeginnerGuidePage>
) {
    companion object {
        fun empty(): BeginnerGuideManifest =
            BeginnerGuideManifest(
                schemaVersion = 1,
                contentVersion = "",
                id = "",
                title = "Prima di volare",
                description = "Le cose essenziali da sapere prima di usare un drone.",
                language = "it-IT",
                country = "IT",
                audience = "beginner",
                pages = emptyList()
            )
    }
}

data class BeginnerGuidePage(
    val id: String,
    val image: String,
    val title: String,
    val accessibilityText: String,
    val order: Int
)

data class BeginnerGuideParseResult(
    val manifest: BeginnerGuideManifest?,
    val warnings: List<BeginnerGuideWarning> = emptyList()
)

data class BeginnerGuideWarning(
    val code: BeginnerGuideWarningCode,
    val message: String
)

enum class BeginnerGuideWarningCode {
    JSON_MALFORMED,
    UNSUPPORTED_SCHEMA,
    MISSING_FIELD,
    INVALID_FIELD,
    DUPLICATE_ID,
    INVALID_ORDER
}

object BeginnerGuideManifestParser {
    const val SupportedSchemaVersion = 1

    fun parse(json: String): BeginnerGuideParseResult {
        val warnings = mutableListOf<BeginnerGuideWarning>()
        val root = try {
            JSONObject(json.ifBlank { "{}" })
        } catch (err: JSONException) {
            return BeginnerGuideParseResult(
                manifest = null,
                warnings = listOf(
                    BeginnerGuideWarning(
                        BeginnerGuideWarningCode.JSON_MALFORMED,
                        err.message.orEmpty()
                    )
                )
            )
        }

        val schemaVersion = root.optInt("schemaVersion", 0)
        if (schemaVersion != SupportedSchemaVersion) {
            return BeginnerGuideParseResult(
                manifest = null,
                warnings = listOf(
                    BeginnerGuideWarning(
                        BeginnerGuideWarningCode.UNSUPPORTED_SCHEMA,
                        "Unsupported schemaVersion $schemaVersion"
                    )
                )
            )
        }

        val contentVersion = root.optVersionString("contentVersion")
        val id = root.optCleanString("id")
        val title = root.optCleanString("title")
        val description = root.optCleanString("description")
        val language = root.optCleanString("language")
        val country = root.optCleanString("country")
        val audience = root.optCleanString("audience")
        if (contentVersion.isBlank()) warnings += missing("Missing contentVersion")
        if (id.isBlank()) warnings += missing("Missing id")
        if (title.isBlank()) warnings += missing("Missing title")
        if (description.isBlank()) warnings += missing("Missing description")
        if (language.isBlank()) warnings += missing("Missing language")
        if (country.isBlank()) warnings += missing("Missing country")
        if (audience.isBlank()) warnings += missing("Missing audience")

        val pages = parsePages(root.optJSONArray("pages"), warnings)
        if (contentVersion.isBlank() || id.isBlank() || title.isBlank() || description.isBlank() || pages.isEmpty()) {
            return BeginnerGuideParseResult(null, warnings)
        }

        return BeginnerGuideParseResult(
            manifest = BeginnerGuideManifest(
                schemaVersion = schemaVersion,
                contentVersion = contentVersion,
                id = id,
                title = title,
                description = description,
                language = language,
                country = country,
                audience = audience,
                pages = pages
            ),
            warnings = warnings
        )
    }

    private fun parsePages(
        array: JSONArray?,
        warnings: MutableList<BeginnerGuideWarning>
    ): List<BeginnerGuidePage> {
        if (array == null) {
            warnings += missing("Missing pages")
            return emptyList()
        }

        val ids = mutableSetOf<String>()
        val orders = mutableSetOf<Int>()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: run {
                warnings += invalid("Invalid page at index $index")
                return@mapNotNull null
            }
            val id = item.optCleanString("id")
            val image = item.optCleanString("image")
            val title = item.optCleanString("title")
            val accessibilityText = item.optCleanString("accessibilityText")
            val order = item.optInt("order", 0)

            when {
                id.isBlank() -> {
                    warnings += missing("Page missing id")
                    null
                }
                !ids.add(id) -> {
                    warnings += BeginnerGuideWarning(BeginnerGuideWarningCode.DUPLICATE_ID, "Duplicate page $id")
                    null
                }
                image.isBlank() || title.isBlank() || accessibilityText.isBlank() -> {
                    warnings += missing("Page $id missing image/title/accessibilityText")
                    null
                }
                order <= 0 -> {
                    warnings += BeginnerGuideWarning(BeginnerGuideWarningCode.INVALID_ORDER, "Invalid order for page $id")
                    null
                }
                else -> {
                    if (!orders.add(order)) {
                        warnings += BeginnerGuideWarning(
                            BeginnerGuideWarningCode.INVALID_ORDER,
                            "Duplicate page order $order"
                        )
                    }
                    BeginnerGuidePage(
                        id = id,
                        image = image,
                        title = title,
                        accessibilityText = accessibilityText,
                        order = order
                    )
                }
            }
        }.sortedWith(compareBy<BeginnerGuidePage> { it.order }.thenBy { it.id })
    }
}

private fun missing(message: String): BeginnerGuideWarning =
    BeginnerGuideWarning(BeginnerGuideWarningCode.MISSING_FIELD, message)

private fun invalid(message: String): BeginnerGuideWarning =
    BeginnerGuideWarning(BeginnerGuideWarningCode.INVALID_FIELD, message)

private fun JSONObject.optCleanString(name: String): String =
    if (!has(name) || isNull(name)) "" else optString(name).trim().takeIf { it != "null" }.orEmpty()

private fun JSONObject.optVersionString(name: String): String {
    if (!has(name) || isNull(name)) return ""
    return opt(name)?.toString()?.trim().takeUnless { it == "null" }.orEmpty()
}
