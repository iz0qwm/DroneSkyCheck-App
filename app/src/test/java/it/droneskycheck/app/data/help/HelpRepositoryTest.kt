package it.droneskycheck.app.data.help

import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun noCacheUsesEmbeddedFallbackWithoutNetwork() = runBlocking {
        val http = FakeHelpHttpClient(error = HelpRemoteError.Network("offline"))
        val repository = repository(embeddedVersion = 1, http = http)

        val manifest = repository.getCurrentManifest()

        assertEquals(1, manifest.contentVersion)
        assertEquals(0, http.calls.size)
    }

    @Test
    fun validRemoteInstallsAndBecomesCurrent() = runBlocking {
        val remote = manifestJson(contentVersion = 3, title = "Remoto")
        val http = FakeHelpHttpClient(
            responses = mapOf(ManifestUrl to HelpManifestHttpResponse(200, remote))
        )
        val repository = repository(embeddedVersion = 1, http = http)

        val update = repository.checkForUpdates()

        assertTrue(update is HelpManifestUpdateResult.Installed)
        assertEquals(3, repository.getCurrentManifest().contentVersion)
        assertEquals("Remoto", repository.getCurrentManifest().topics.single().title)
    }

    @Test
    fun remoteFailsWithCachePresentUsesCache() = runBlocking {
        val storage = storage(embeddedVersion = 1)
        storage.writeManifestAtomically(manifestJson(contentVersion = 2, title = "Cache"))
        val repository = repository(
            storage = storage,
            http = FakeHelpHttpClient(error = HelpRemoteError.Network("offline"))
        )

        val current = repository.getCurrentManifest()
        val update = repository.checkForUpdates()

        assertEquals(2, current.contentVersion)
        assertTrue(update is HelpManifestUpdateResult.Failed)
        assertEquals(2, repository.getCurrentManifest().contentVersion)
    }

    @Test
    fun remoteFailsWithCacheAbsentUsesEmbedded() = runBlocking {
        val repository = repository(
            embeddedVersion = 1,
            http = FakeHelpHttpClient(error = HelpRemoteError.Timeout("timeout"))
        )

        val current = repository.getCurrentManifest()
        val update = repository.checkForUpdates()

        assertEquals(1, current.contentVersion)
        assertTrue(update is HelpManifestUpdateResult.Failed)
    }

    @Test
    fun corruptedCacheFallsBackToEmbedded() = runBlocking {
        val storage = storage(embeddedVersion = 1)
        storage.writeManifestAtomically("{not valid")
        val repository = repository(storage = storage, http = FakeHelpHttpClient())

        val current = repository.getCurrentManifest()

        assertEquals(1, current.contentVersion)
        assertEquals("Embedded", current.topics.single().title)
    }

    @Test
    fun newerRemoteReplacesCachedManifest() = runBlocking {
        val storage = storage(embeddedVersion = 1)
        storage.writeManifestAtomically(manifestJson(contentVersion = 2, title = "Cache"))
        val http = FakeHelpHttpClient(
            responses = mapOf(ManifestUrl to HelpManifestHttpResponse(200, manifestJson(contentVersion = 4, title = "Nuovo")))
        )
        val repository = repository(storage = storage, http = http)

        val update = repository.checkForUpdates()

        assertTrue(update is HelpManifestUpdateResult.Installed)
        assertEquals(4, repository.getCurrentManifest().contentVersion)
        assertEquals("Nuovo", repository.getCurrentManifest().topics.single().title)
    }

    @Test
    fun olderRemoteDoesNotReplaceCachedManifest() = runBlocking {
        val storage = storage(embeddedVersion = 1)
        storage.writeManifestAtomically(manifestJson(contentVersion = 4, title = "Cache"))
        val http = FakeHelpHttpClient(
            responses = mapOf(ManifestUrl to HelpManifestHttpResponse(200, manifestJson(contentVersion = 3, title = "Vecchio")))
        )
        val repository = repository(storage = storage, http = http)

        val update = repository.checkForUpdates()

        assertTrue(update is HelpManifestUpdateResult.Skipped)
        assertEquals(4, repository.getCurrentManifest().contentVersion)
        assertEquals("Cache", repository.getCurrentManifest().topics.single().title)
    }

    @Test
    fun checkForUpdatesIfDueSkipsFreshMetadata() = runBlocking {
        val storage = storage(embeddedVersion = 1)
        storage.writeMetadata(HelpManifestMetadata(lastCheckedAt = clock.instant().minus(Duration.ofHours(2)).toString()))
        val http = FakeHelpHttpClient()
        val repository = repository(storage = storage, http = http)

        val update = repository.checkForUpdatesIfDue()

        assertTrue(update is HelpManifestUpdateResult.Skipped)
        assertEquals(0, http.calls.size)
    }

    private fun repository(
        embeddedVersion: Int = 1,
        storage: TestHelpStorage = storage(embeddedVersion),
        http: FakeHelpHttpClient = FakeHelpHttpClient()
    ): HelpRepository =
        HelpRepository(
            storage = storage,
            manifestUrl = ManifestUrl,
            httpClient = http,
            clock = clock
        )

    private fun storage(embeddedVersion: Int): TestHelpStorage =
        TestHelpStorage(
            directory = Files.createTempDirectory("help-repository-test").toFile(),
            embeddedJson = manifestJson(contentVersion = embeddedVersion, title = "Embedded")
        )
}

private class TestHelpStorage(
    val directory: File,
    private val embeddedJson: String
) : HelpManifestStorage {
    private val manifestFile = File(directory, HelpRepository.ManifestCacheFileName)
    private val metadataFile = File(directory, HelpRepository.MetadataFileName)

    override fun readManifestJson(): String? =
        manifestFile.takeIf { it.exists() }?.readText(Charsets.UTF_8)

    override fun readEmbeddedManifestJson(): String = embeddedJson

    override fun writeManifestAtomically(json: String) {
        directory.mkdirs()
        manifestFile.writeText(json, Charsets.UTF_8)
    }

    override fun readMetadata(): HelpManifestMetadata? =
        metadataFile.takeIf { it.exists() }
            ?.let { HelpManifestMetadata(lastCheckedAt = it.readText(Charsets.UTF_8)) }

    override fun writeMetadata(metadata: HelpManifestMetadata) {
        directory.mkdirs()
        metadataFile.writeText(metadata.lastCheckedAt.orEmpty(), Charsets.UTF_8)
    }
}

private class FakeHelpHttpClient(
    private val responses: Map<String, HelpManifestHttpResponse> = emptyMap(),
    private val error: RuntimeException? = null
) : HelpManifestHttpClient {
    val calls = mutableListOf<String>()

    override fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int
    ): HelpManifestHttpResponse {
        calls += url
        error?.let { throw it }
        return responses[url] ?: HelpManifestHttpResponse(404, "")
    }
}

private const val ManifestUrl = "https://www.kwos.org/appoggio/droni/DroneSkyCheck/help/manifest.json"

private fun manifestJson(contentVersion: Int, title: String): String =
    """
        {
          "schemaVersion": 1,
          "contentVersion": $contentVersion,
          "updatedAt": "2026-08-13",
          "onboardingVersion": $contentVersion,
          "onboarding": {
            "steps": [
              {"id":"map","target":"map","title":"Controlla","text":"Tocca","order":1}
            ]
          },
          "topics": [
            {"id":"weather","title":"$title","summary":"Sintesi","order":1}
          ]
        }
    """.trimIndent()
