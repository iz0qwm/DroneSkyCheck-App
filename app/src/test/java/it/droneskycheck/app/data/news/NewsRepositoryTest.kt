package it.droneskycheck.app.data.news

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsRepositoryTest {
    @Test
    fun handles200PersistsEtagAndUses304CachedPayload() {
        FakeNewsServer(
            listOf(
                Response(200, FeedOne, etag = "\"feed-v1\""),
                Response(304, "")
            )
        ).use { server ->
            val cacheDir = Files.createTempDirectory("news-etag-test").toFile()
            val firstRepository = NewsRepository(cacheDir, server.url)
            val first = firstRepository.getNews(NewsFeedRequest(limit = 12)) as NewsLoadResult.Available

            val recreatedRepository = NewsRepository(cacheDir, server.url)
            val second = recreatedRepository.getNews(NewsFeedRequest(limit = 12)) as NewsLoadResult.Available

            assertEquals(300L, first.feed.items.single().id)
            assertFalse(first.fromCache)
            assertTrue(second.notModified)
            assertFalse(second.fromCache)
            assertEquals(300L, second.feed.items.single().id)
            assertEquals(listOf(null, "\"feed-v1\""), server.ifNoneMatchHeaders)
        }
    }

    @Test
    fun apiErrorFallsBackToLastValidCache() {
        FakeNewsServer(
            listOf(
                Response(200, FeedOne, etag = "\"feed-v1\""),
                Response(503, "temporarily unavailable")
            )
        ).use { server ->
            val repository = NewsRepository(Files.createTempDirectory("news-cache-test").toFile(), server.url)
            repository.getNews(NewsFeedRequest(limit = 20))
            val fallback = repository.getNews(NewsFeedRequest(limit = 20)) as NewsLoadResult.Available

            assertTrue(fallback.fromCache)
            assertFalse(fallback.notModified)
            assertEquals(300L, fallback.feed.items.single().id)
        }
    }

    @Test
    fun apiErrorWithoutCacheIsUnavailable() {
        FakeNewsServer(listOf(Response(500, "failure"))).use { server ->
            val repository = NewsRepository(Files.createTempDirectory("news-empty-test").toFile(), server.url)
            assertEquals(
                NewsLoadResult.Unavailable,
                repository.getNews(NewsFeedRequest(limit = 20))
            )
        }
    }

    @Test
    fun malformedRemoteResponseDoesNotReplaceValidCache() {
        FakeNewsServer(
            listOf(
                Response(200, FeedOne, etag = "\"feed-v1\""),
                Response(200, "{not-json", etag = "\"bad\"")
            )
        ).use { server ->
            val repository = NewsRepository(Files.createTempDirectory("news-malformed-test").toFile(), server.url)
            repository.getNews(NewsFeedRequest(limit = 20))
            val fallback = repository.getNews(NewsFeedRequest(limit = 20)) as NewsLoadResult.Available

            assertTrue(fallback.fromCache)
            assertEquals(300L, fallback.feed.items.single().id)
        }
    }

    @Test
    fun sendsFilterAndPaginationParameters() {
        FakeNewsServer(listOf(Response(200, EmptyFeed))).use { server ->
            val repository = NewsRepository(Files.createTempDirectory("news-query-test").toFile(), server.url)
            repository.getNews(
                NewsFeedRequest(
                    limit = 20,
                    offset = 40,
                    category = "REGULATION",
                    scope = "EUROPE",
                    sourceType = "TECHNICAL"
                )
            )

            val query = server.queries.single()
            assertTrue(query.contains("limit=20"))
            assertTrue(query.contains("offset=40"))
            assertTrue(query.contains("category=REGULATION"))
            assertTrue(query.contains("scope=EUROPE"))
            assertTrue(query.contains("source_type=TECHNICAL"))
        }
    }

    private data class Response(val status: Int, val body: String, val etag: String? = null)

    private class FakeNewsServer(responses: List<Response>) : AutoCloseable {
        private val pending = ArrayDeque(responses)
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val ifNoneMatchHeaders = mutableListOf<String?>()
        val queries = mutableListOf<String>()
        val url: String get() = "http://127.0.0.1:${server.address.port}/api/news"

        init {
            server.createContext("/api/news") { exchange -> respond(exchange) }
            server.executor = Executors.newCachedThreadPool()
            server.start()
        }

        private fun respond(exchange: HttpExchange) {
            ifNoneMatchHeaders += exchange.requestHeaders.getFirst("If-None-Match")
            queries += exchange.requestURI.rawQuery.orEmpty()
            val response = synchronized(pending) {
                pending.removeFirstOrNull() ?: Response(500, "unexpected extra request")
            }
            response.etag?.let { exchange.responseHeaders.add("ETag", it) }
            val bytes = response.body.toByteArray(Charsets.UTF_8)
            if (response.status == 304) {
                exchange.sendResponseHeaders(304, -1)
            } else {
                exchange.sendResponseHeaders(response.status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }

        override fun close() {
            server.stop(0)
        }
    }

    private companion object {
        val FeedOne = """
            {"items":[{
              "id":300,
              "title":"Dronetag integra i dati di Remote ID nella piattaforma Airwise",
              "summary":"Sintesi DSC",
              "source":"DRONEDJ",
              "source_name":"DroneDJ",
              "source_type":"EDITORIAL",
              "published_at":"2026-09-02T10:11:57Z",
              "category":"TECHNOLOGY",
              "scope":"UNKNOWN",
              "content_kind":"NEWS",
              "featured":false,
              "original_url":"https://example.test/news/300",
              "language":"it"
            }],"limit":20,"offset":0,"total":1}
        """.trimIndent()
        val EmptyFeed = """{"items":[],"limit":20,"offset":0,"total":0}"""
    }
}
