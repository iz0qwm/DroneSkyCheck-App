package it.droneskycheck.app.data.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsModelsTest {
    @Test
    fun parsesResponseAndIgnoresUnknownFields() {
        val feed = parseNewsFeed(
            """
                {
                  "items": [{
                    "id": 270,
                    "title": "Titolo DSC",
                    "summary": "Sintesi",
                    "source": "DRONEXL",
                    "source_name": "DroneXL",
                    "source_type": "EDITORIAL",
                    "published_at": "2026-09-02T05:11:00Z",
                    "category": "SAFETY",
                    "scope": "EUROPE",
                    "content_kind": "NEWS",
                    "featured": true,
                    "original_url": "https://example.test/news",
                    "language": "it",
                    "future_field": {"ignored": true}
                  }],
                  "limit": 20,
                  "offset": 0,
                  "total": 73,
                  "unknown_root": true
                }
            """.trimIndent()
        )

        assertEquals(1, feed.items.size)
        assertEquals(270L, feed.items.single().id)
        assertEquals("DroneXL", feed.items.single().sourceName)
        assertTrue(feed.items.single().featured)
        assertEquals(73, feed.total)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedResponseWithoutItemsArray() {
        parseNewsFeed("""{"limit":20,"total":0}""")
    }

    @Test
    fun parsesEmptyFeed() {
        val feed = parseNewsFeed("""{"items":[],"limit":20,"offset":0,"total":0}""")
        assertTrue(feed.items.isEmpty())
        assertEquals(0, feed.total)
    }

    @Test
    fun skipsMalformedItemsWithoutFailingValidItems() {
        val feed = parseNewsFeed(
            """
                {"items":[
                  {"id":1,"title":"Valida","original_url":"https://example.test/1"},
                  {"id":2,"summary":"Senza titolo"},
                  "not-an-object"
                ],"limit":20,"offset":0,"total":3}
            """.trimIndent()
        )
        assertEquals(listOf(1L), feed.items.map(NewsItem::id))
    }

    @Test
    fun badgeMappingUsesTheSpecifiedPriority() {
        assertEquals("ITALIA", newsBadge(item(scope = "ITALY", category = "REGULATION")).label)
        assertEquals("NORMATIVA", newsBadge(item(category = "REGULATION", scope = "EUROPE")).label)
        assertEquals("SICUREZZA", newsBadge(item(category = "PUBLIC_SAFETY")).label)
        assertEquals("SICUREZZA", newsBadge(item(category = "SAFETY")).label)
        assertEquals("U-SPACE", newsBadge(item(category = "AAM_USPACE")).label)
        assertEquals("DIFESA", newsBadge(item(category = "DEFENCE_SECURITY")).label)
        assertEquals("FPV", newsBadge(item(sourceType = "TECHNICAL", scope = "EUROPE")).label)
        assertEquals("EUROPA", newsBadge(item(scope = "EUROPE")).label)
        assertEquals("MONDO", newsBadge(item(scope = "INTERNATIONAL")).label)
        assertEquals("TECNOLOGIA", newsBadge(item(category = "TECHNOLOGY")).label)
        assertEquals("OPERAZIONI", newsBadge(item(category = "OPERATIONS")).label)
        assertEquals("MERCATO", newsBadge(item(category = "MARKET")).label)
        assertEquals("EVENTI", newsBadge(item(category = "EVENTS")).label)
        assertEquals("DRONI", newsBadge(item()).label)
    }

    @Test
    fun filterMappingUsesServerSideParameters() {
        assertEquals("ITALY", NewsFilter.ITALY.toRequest(20).scope)
        assertEquals("EUROPE", NewsFilter.EUROPE.toRequest(20).scope)
        assertEquals("INTERNATIONAL", NewsFilter.WORLD.toRequest(20).scope)
        assertEquals("REGULATION", NewsFilter.REGULATION.toRequest(20).category)
        assertEquals("TECHNICAL", NewsFilter.FPV.toRequest(20).sourceType)
        assertEquals("TECHNOLOGY", NewsFilter.TECHNOLOGY.toRequest(20).category)
        assertEquals("OPERATIONS", NewsFilter.OPERATIONS.toRequest(20).category)
        assertEquals("SAFETY", NewsFilter.SAFETY.toRequest(20).category)
    }

    @Test
    fun newNewsCountAndLatestSeenIdUseIds() {
        val items = listOf(item(id = 12), item(id = 11), item(id = 8))
        assertEquals(2, unseenNewsCount(items, lastSeenNewsId = 10))
        assertEquals(12L, latestNewsId(items))
        assertEquals(0, unseenNewsCount(items, lastSeenNewsId = 12))
    }

    @Test
    fun formatsItalianCalendarDate() {
        assertEquals("2 settembre 2026", formatNewsDate("2026-09-02T05:11:00Z"))
    }

    private fun item(
        id: Long = 1,
        scope: String = "UNKNOWN",
        category: String = "UNKNOWN",
        sourceType: String = "EDITORIAL"
    ) = NewsItem(
        id = id,
        title = "Titolo",
        summary = "Sintesi",
        source = "SOURCE",
        sourceName = "Fonte",
        sourceType = sourceType,
        publishedAt = "2026-09-02T05:11:00Z",
        category = category,
        scope = scope,
        contentKind = "NEWS",
        featured = false,
        originalUrl = "https://example.test/$id",
        language = "it"
    )
}
