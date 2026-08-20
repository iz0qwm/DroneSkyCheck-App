package it.droneskycheck.app.data.beginner

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BeginnerGuideRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun loadGuide_cachesManifestAndImagesForOfflineUse() = runBlocking {
        val storage = FileBeginnerGuideStorage(temp.newFolder("guide"))
        val onlineRepository = BeginnerGuideRepository(
            storage = storage,
            manifestUrl = "https://example.test/manifest.json",
            imagesBaseUrl = "https://example.test/principiante/",
            httpClient = FakeBeginnerGuideHttpClient(
                manifestBody = sampleManifest,
                imageBytes = mapOf(
                    "https://example.test/principiante/01.png" to byteArrayOf(1, 2, 3)
                )
            )
        )

        val online = onlineRepository.loadGuide()

        assertTrue(online is BeginnerGuideLoadResult.Available)
        val onlineContent = (online as BeginnerGuideLoadResult.Available).content
        assertEquals("1.0.0", onlineContent.manifest.contentVersion)
        assertNotNull(onlineRepository.cachedImageFile(onlineContent.manifest, onlineContent.manifest.pages.first()))

        val offlineRepository = BeginnerGuideRepository(
            storage = storage,
            manifestUrl = "https://example.test/manifest.json",
            imagesBaseUrl = "https://example.test/principiante/",
            httpClient = FailingBeginnerGuideHttpClient
        )

        val offline = offlineRepository.loadGuide()

        assertTrue(offline is BeginnerGuideLoadResult.Available)
        val offlineContent = (offline as BeginnerGuideLoadResult.Available).content
        assertTrue(offlineContent.degraded)
        assertEquals("1.0.0", offlineContent.manifest.contentVersion)
        assertTrue(offlineContent.unavailableImages.isEmpty())
    }

    private class FakeBeginnerGuideHttpClient(
        private val manifestBody: String,
        private val imageBytes: Map<String, ByteArray>
    ) : BeginnerGuideHttpClient {
        override fun get(url: String, timeoutMillis: Int): BeginnerGuideHttpResponse =
            BeginnerGuideHttpResponse(statusCode = 200, body = manifestBody)

        override fun getBytes(url: String, timeoutMillis: Int): BeginnerGuideBytesResponse =
            BeginnerGuideBytesResponse(
                statusCode = if (url in imageBytes) 200 else 404,
                bytes = imageBytes[url] ?: ByteArray(0)
            )
    }

    private object FailingBeginnerGuideHttpClient : BeginnerGuideHttpClient {
        override fun get(url: String, timeoutMillis: Int): BeginnerGuideHttpResponse {
            throw BeginnerGuideRemoteError.Network("offline")
        }

        override fun getBytes(url: String, timeoutMillis: Int): BeginnerGuideBytesResponse {
            throw BeginnerGuideRemoteError.Network("offline")
        }
    }
}

private val sampleManifest = """
    {
      "schemaVersion": 1,
      "contentVersion": "1.0.0",
      "id": "principiante",
      "title": "Prima di volare",
      "description": "Le cose essenziali da sapere prima di usare un drone.",
      "language": "it-IT",
      "country": "IT",
      "audience": "beginner",
      "pages": [
        {
          "id": "01",
          "image": "01.png",
          "title": "Hai comprato un drone?",
          "accessibilityText": "Hai comprato un drone? Prima si controlla, poi si vola.",
          "order": 1
        }
      ]
    }
""".trimIndent()
