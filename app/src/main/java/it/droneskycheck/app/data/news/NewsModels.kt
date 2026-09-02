package it.droneskycheck.app.data.news

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONObject

data class NewsItem(
    val id: Long,
    val title: String,
    val summary: String,
    val source: String,
    val sourceName: String,
    val sourceType: String,
    val publishedAt: String,
    val category: String,
    val scope: String,
    val contentKind: String,
    val featured: Boolean,
    val originalUrl: String,
    val language: String
)

data class NewsFeedResponse(
    val items: List<NewsItem>,
    val limit: Int,
    val offset: Int,
    val total: Int
)

data class NewsFeedRequest(
    val limit: Int,
    val offset: Int = 0,
    val category: String? = null,
    val scope: String? = null,
    val sourceType: String? = null
) {
    init {
        require(limit in 1..100)
        require(offset >= 0)
    }
}

enum class NewsFilter(
    val label: String,
    val category: String? = null,
    val scope: String? = null,
    val sourceType: String? = null
) {
    ALL("TUTTE"),
    ITALY("ITALIA", scope = "ITALY"),
    EUROPE("EUROPA", scope = "EUROPE"),
    WORLD("MONDO", scope = "INTERNATIONAL"),
    REGULATION("NORMATIVA", category = "REGULATION"),
    FPV("FPV", sourceType = "TECHNICAL"),
    TECHNOLOGY("TECNOLOGIA", category = "TECHNOLOGY"),
    OPERATIONS("OPERAZIONI", category = "OPERATIONS"),
    SAFETY("SICUREZZA", category = "SAFETY")
}

data class NewsBadge(
    val label: String,
    val kind: NewsBadgeKind
)

enum class NewsBadgeKind {
    Geographic,
    Regulation,
    Safety,
    Technology,
    Operations,
    Market,
    Events,
    Fpv,
    Defence,
    Default
}

fun newsBadge(item: NewsItem): NewsBadge = when {
    item.scope == "ITALY" -> NewsBadge("ITALIA", NewsBadgeKind.Geographic)
    item.category == "REGULATION" -> NewsBadge("NORMATIVA", NewsBadgeKind.Regulation)
    item.category == "PUBLIC_SAFETY" || item.category == "SAFETY" ->
        NewsBadge("SICUREZZA", NewsBadgeKind.Safety)
    item.category == "AAM_USPACE" -> NewsBadge("U-SPACE", NewsBadgeKind.Technology)
    item.category == "DEFENCE_SECURITY" -> NewsBadge("DIFESA", NewsBadgeKind.Defence)
    item.sourceType == "TECHNICAL" -> NewsBadge("FPV", NewsBadgeKind.Fpv)
    item.scope == "EUROPE" -> NewsBadge("EUROPA", NewsBadgeKind.Geographic)
    item.scope == "INTERNATIONAL" -> NewsBadge("MONDO", NewsBadgeKind.Geographic)
    item.category == "TECHNOLOGY" -> NewsBadge("TECNOLOGIA", NewsBadgeKind.Technology)
    item.category == "OPERATIONS" -> NewsBadge("OPERAZIONI", NewsBadgeKind.Operations)
    item.category == "MARKET" -> NewsBadge("MERCATO", NewsBadgeKind.Market)
    item.category == "EVENTS" -> NewsBadge("EVENTI", NewsBadgeKind.Events)
    else -> NewsBadge("DRONI", NewsBadgeKind.Default)
}

fun NewsFilter.toRequest(limit: Int, offset: Int = 0): NewsFeedRequest =
    NewsFeedRequest(
        limit = limit,
        offset = offset,
        category = category,
        scope = scope,
        sourceType = sourceType
    )

fun parseNewsFeed(json: String): NewsFeedResponse {
    val root = JSONObject(json)
    val rawItems = root.optJSONArray("items")
        ?: throw IllegalArgumentException("News response missing items")
    val items = buildList {
        for (index in 0 until rawItems.length()) {
            rawItems.optJSONObject(index)?.toNewsItemOrNull()?.let(::add)
        }
    }
    return NewsFeedResponse(
        items = items,
        limit = root.optInt("limit", items.size).coerceAtLeast(0),
        offset = root.optInt("offset", 0).coerceAtLeast(0),
        total = root.optInt("total", items.size).coerceAtLeast(items.size)
    )
}

fun unseenNewsCount(items: List<NewsItem>, lastSeenNewsId: Long): Int =
    items.count { it.id > lastSeenNewsId }

fun latestNewsId(items: List<NewsItem>): Long? = items.maxOfOrNull(NewsItem::id)

fun formatNewsDate(publishedAt: String): String =
    runCatching { ItalianNewsDateFormatter.format(Instant.parse(publishedAt)) }
        .getOrDefault(publishedAt)

private fun JSONObject.toNewsItemOrNull(): NewsItem? {
    val id = optLong("id", Long.MIN_VALUE).takeIf { it >= 0L } ?: return null
    val title = stringOrNull("title") ?: return null
    val originalUrl = stringOrNull("original_url") ?: return null
    return NewsItem(
        id = id,
        title = title,
        summary = stringOrNull("summary").orEmpty(),
        source = stringOrNull("source").orEmpty(),
        sourceName = stringOrNull("source_name").orEmpty(),
        sourceType = stringOrNull("source_type").orEmpty(),
        publishedAt = stringOrNull("published_at").orEmpty(),
        category = stringOrNull("category").orEmpty(),
        scope = stringOrNull("scope").orEmpty(),
        contentKind = stringOrNull("content_kind").orEmpty(),
        featured = optBoolean("featured", false),
        originalUrl = originalUrl,
        language = stringOrNull("language").orEmpty()
    )
}

private fun JSONObject.stringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null
    else optString(name).trim().takeIf { it.isNotBlank() && it != "null" }

private val ItalianNewsDateFormatter: DateTimeFormatter =
    DateTimeFormatter
        .ofPattern("d MMMM uuuu", Locale.ITALIAN)
        .withZone(ZoneId.of("Europe/Rome"))
