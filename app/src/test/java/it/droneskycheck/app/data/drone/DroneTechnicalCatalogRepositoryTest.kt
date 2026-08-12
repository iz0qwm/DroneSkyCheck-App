package it.droneskycheck.app.data.drone

import it.droneskycheck.app.data.DscApiConfig
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DroneTechnicalCatalogRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-12T08:00:00Z"), ZoneOffset.UTC)

    @Test
    fun noCacheUsesEmbeddedSeedWithoutNetwork() = runBlocking {
        val http = FakeDroneCatalogHttpClient()
        val repository = repository(seedVersion = 1, http = http)

        val catalog = repository.getCurrentCatalog()

        assertEquals(1, catalog.catalogVersion)
        assertEquals("DJI Seed 1", catalog.drones.single().displayName)
        assertEquals(0, http.calls.size)
    }

    @Test
    fun validCacheWinsOverSeedWhenOffline() = runBlocking {
        val storage = storage(seedVersion = 1)
        storage.writeCatalogAtomically(catalogJson(catalogVersion = 12, model = "Cached 12"))
        val repository = repository(storage = storage, http = FakeDroneCatalogHttpClient(error = DroneCatalogRemoteError.Network("offline")))

        val catalog = repository.getCurrentCatalog()
        val update = repository.checkForUpdates()

        assertEquals(12, catalog.catalogVersion)
        assertEquals("DJI Cached 12", catalog.drones.single().displayName)
        assertTrue(update is DroneCatalogUpdateResult.Failed)
        assertEquals(12, repository.getCurrentCatalog().catalogVersion)
    }

    @Test
    fun manifestSameVersionDoesNotDownloadCatalog() = runBlocking {
        val http = FakeDroneCatalogHttpClient(
            responses = mapOf(
                ManifestUrl to DroneCatalogHttpResponse(200, manifestJson(catalogVersion = 12, catalogJson = catalogJson(catalogVersion = 12)))
            )
        )
        val storage = storage(seedVersion = 1)
        storage.writeCatalogAtomically(catalogJson(catalogVersion = 12))
        val repository = repository(storage = storage, http = http)

        val update = repository.checkForUpdates()

        assertTrue(update is DroneCatalogUpdateResult.Skipped)
        assertEquals(listOf(ManifestUrl), http.calls)
    }

    @Test
    fun newerManifestDownloadsValidatesChecksumAndInstallsCatalog() = runBlocking {
        val remoteCatalog = catalogJson(catalogVersion = 13, model = "Remote 13")
        val http = FakeDroneCatalogHttpClient(
            responses = mapOf(
                ManifestUrl to DroneCatalogHttpResponse(200, manifestJson(catalogVersion = 13, catalogJson = remoteCatalog)),
                CatalogUrl to DroneCatalogHttpResponse(200, remoteCatalog)
            )
        )
        val storage = storage(seedVersion = 1)
        storage.writeCatalogAtomically(catalogJson(catalogVersion = 12))
        val repository = repository(storage = storage, http = http)

        val update = repository.checkForUpdates()
        val current = repository.getCurrentCatalog()

        assertTrue(update is DroneCatalogUpdateResult.Installed)
        assertEquals(13, current.catalogVersion)
        assertEquals("DJI Remote 13", current.drones.single().displayName)
        assertFalse(File(storage.directory, "${DroneTechnicalCatalogRepository.CatalogCacheFileName}.tmp").exists())
    }

    @Test
    fun wrongShaKeepsPreviousCache() = runBlocking {
        val remoteCatalog = catalogJson(catalogVersion = 13)
        val http = FakeDroneCatalogHttpClient(
            responses = mapOf(
                ManifestUrl to DroneCatalogHttpResponse(200, manifestJson(catalogVersion = 13, catalogJson = remoteCatalog, sha = "0".repeat(64))),
                CatalogUrl to DroneCatalogHttpResponse(200, remoteCatalog)
            )
        )
        val storage = storage(seedVersion = 1)
        storage.writeCatalogAtomically(catalogJson(catalogVersion = 12))
        val repository = repository(storage = storage, http = http)

        val update = repository.checkForUpdates()

        assertTrue(update is DroneCatalogUpdateResult.Failed)
        assertEquals(12, repository.getCurrentCatalog().catalogVersion)
    }

    @Test
    fun invalidCatalogJsonKeepsPreviousCache() = runBlocking {
        val invalidCatalog = "{not valid"
        val http = FakeDroneCatalogHttpClient(
            responses = mapOf(
                ManifestUrl to DroneCatalogHttpResponse(200, manifestJson(catalogVersion = 13, catalogJson = invalidCatalog)),
                CatalogUrl to DroneCatalogHttpResponse(200, invalidCatalog)
            )
        )
        val storage = storage(seedVersion = 1)
        storage.writeCatalogAtomically(catalogJson(catalogVersion = 12))
        val repository = repository(storage = storage, http = http)

        val update = repository.checkForUpdates()

        assertTrue(update is DroneCatalogUpdateResult.Failed)
        assertEquals(12, repository.getCurrentCatalog().catalogVersion)
    }

    @Test
    fun invalidManifestIsIgnored() = runBlocking {
        val http = FakeDroneCatalogHttpClient(
            responses = mapOf(ManifestUrl to DroneCatalogHttpResponse(200, """{"schemaVersion":2}"""))
        )
        val repository = repository(seedVersion = 1, http = http)

        val update = repository.checkForUpdates()

        assertTrue(update is DroneCatalogUpdateResult.Failed)
    }

    @Test
    fun timeoutAndHttpFailuresAreBestEffort() = runBlocking {
        val timeout = repository(seedVersion = 1, http = FakeDroneCatalogHttpClient(error = DroneCatalogRemoteError.Timeout("timeout")))
        val notFound = repository(
            seedVersion = 1,
            http = FakeDroneCatalogHttpClient(responses = mapOf(ManifestUrl to DroneCatalogHttpResponse(404, "")))
        )
        val server = repository(
            seedVersion = 1,
            http = FakeDroneCatalogHttpClient(responses = mapOf(ManifestUrl to DroneCatalogHttpResponse(500, "")))
        )

        assertTrue(timeout.checkForUpdates() is DroneCatalogUpdateResult.Failed)
        assertTrue(notFound.checkForUpdates() is DroneCatalogUpdateResult.Failed)
        assertTrue(server.checkForUpdates() is DroneCatalogUpdateResult.Failed)
    }

    @Test
    fun supportedAndUnsupportedSchemaVersionsAreHandled() = runBlocking {
        val supportedCatalog = catalogJson(schemaVersion = 2, catalogVersion = 13)
        val supported = repository(
            seedVersion = 1,
            http = FakeDroneCatalogHttpClient(
                responses = mapOf(
                    ManifestUrl to DroneCatalogHttpResponse(200, manifestJson(schemaVersion = 2, catalogVersion = 13, catalogJson = supportedCatalog)),
                    CatalogUrl to DroneCatalogHttpResponse(200, supportedCatalog)
                )
            )
        )
        val unsupported = repository(
            seedVersion = 1,
            http = FakeDroneCatalogHttpClient(
                responses = mapOf(
                    ManifestUrl to DroneCatalogHttpResponse(200, manifestJson(schemaVersion = 3, catalogVersion = 13, catalogJson = supportedCatalog))
                )
            )
        )

        assertTrue(supported.checkForUpdates() is DroneCatalogUpdateResult.Installed)
        assertTrue(unsupported.checkForUpdates() is DroneCatalogUpdateResult.Failed)
    }

    @Test
    fun manifestCatalogUrlMustBeHttpsAndAllowedHost() = runBlocking {
        val remoteCatalog = catalogJson(catalogVersion = 13)
        val notHttps = repository(
            seedVersion = 1,
            http = FakeDroneCatalogHttpClient(
                responses = mapOf(
                    ManifestUrl to DroneCatalogHttpResponse(
                        200,
                        manifestJson(catalogVersion = 13, catalogJson = remoteCatalog, catalogUrl = "http://www.kwos.org/appoggio/droni/DroneSkyCheck/catalog/drone_technical_catalog.json")
                    )
                )
            )
        )
        val wrongHost = repository(
            seedVersion = 1,
            http = FakeDroneCatalogHttpClient(
                responses = mapOf(
                    ManifestUrl to DroneCatalogHttpResponse(
                        200,
                        manifestJson(catalogVersion = 13, catalogJson = remoteCatalog, catalogUrl = "https://example.com/appoggio/droni/DroneSkyCheck/catalog/drone_technical_catalog.json")
                    )
                )
            )
        )

        assertTrue(notHttps.checkForUpdates() is DroneCatalogUpdateResult.Failed)
        assertTrue(wrongHost.checkForUpdates() is DroneCatalogUpdateResult.Failed)
    }

    @Test
    fun checkForUpdatesIfDueSkipsFreshMetadata() = runBlocking {
        val storage = storage(seedVersion = 1)
        storage.writeMetadata(DroneCatalogMetadata(lastCheckedAt = clock.instant().minus(Duration.ofHours(2)).toString()))
        val http = FakeDroneCatalogHttpClient()
        val repository = repository(storage = storage, http = http)

        val update = repository.checkForUpdatesIfDue()

        assertTrue(update is DroneCatalogUpdateResult.Skipped)
        assertEquals(0, http.calls.size)
    }

    @Test
    fun emptyLocalCatalogForcesRemoteCheckEvenWhenLastCheckIsFresh() = runBlocking {
        val remoteCatalog = catalogJson(catalogVersion = 2, model = "Remote Recovery")
        val storage = TestDroneCatalogStorage(
            directory = Files.createTempDirectory("drone-catalog-empty-test").toFile(),
            seedCatalogJson = """{"schemaVersion":2,"catalogVersion":1,"drones":[]}"""
        )
        storage.writeMetadata(DroneCatalogMetadata(lastCheckedAt = clock.instant().minus(Duration.ofHours(2)).toString()))
        val http = FakeDroneCatalogHttpClient(
            responses = mapOf(
                ManifestUrl to DroneCatalogHttpResponse(200, manifestJson(catalogVersion = 2, catalogJson = remoteCatalog)),
                CatalogUrl to DroneCatalogHttpResponse(200, remoteCatalog)
            )
        )
        val repository = repository(storage = storage, http = http)

        val update = repository.checkForUpdatesIfDue()

        assertTrue(update is DroneCatalogUpdateResult.Installed)
        assertEquals(2, repository.getCurrentCatalog().catalogVersion)
        assertEquals("DJI Remote Recovery", repository.getCurrentCatalog().drones.single().displayName)
        assertEquals(listOf(ManifestUrl, CatalogUrl), http.calls)
    }

    @Test
    fun getCurrentCatalogDoesNotWaitForRemoteCheck() = runBlocking {
        val http = FakeDroneCatalogHttpClient(error = DroneCatalogRemoteError.Timeout("timeout"))
        val repository = repository(seedVersion = 1, http = http)

        val catalog = repository.getCurrentCatalog()

        assertEquals(1, catalog.catalogVersion)
        assertEquals(0, http.calls.size)
    }

    @Test
    fun previousCacheIsPreservedAfterFailedUpdate() = runBlocking {
        val storage = storage(seedVersion = 1)
        storage.writeCatalogAtomically(catalogJson(catalogVersion = 12, model = "Cached 12"))
        val remoteCatalog = catalogJson(catalogVersion = 13, model = "")
        val http = FakeDroneCatalogHttpClient(
            responses = mapOf(
                ManifestUrl to DroneCatalogHttpResponse(200, manifestJson(catalogVersion = 13, catalogJson = remoteCatalog)),
                CatalogUrl to DroneCatalogHttpResponse(200, remoteCatalog)
            )
        )
        val repository = repository(storage = storage, http = http)

        val update = repository.checkForUpdates()

        assertTrue(update is DroneCatalogUpdateResult.Failed)
        assertEquals("DJI Cached 12", repository.getCurrentCatalog().drones.single().displayName)
    }

    @Test
    fun resolverUsesInstalledRemoteCatalogAndKeepsManualOverridePriority() = runBlocking {
        val remoteCatalog = catalogJson(catalogVersion = 13, model = "Remote Only", windMs = 12.0)
        val repository = repository(
            seedVersion = 1,
            http = FakeDroneCatalogHttpClient(
                responses = mapOf(
                    ManifestUrl to DroneCatalogHttpResponse(200, manifestJson(catalogVersion = 13, catalogJson = remoteCatalog)),
                    CatalogUrl to DroneCatalogHttpResponse(200, remoteCatalog)
                )
            )
        )

        repository.checkForUpdates()
        val capabilities = repository.resolver().capabilitiesFor(
            it.droneskycheck.app.data.LocalDrone(
                id = "remote",
                manufacturer = "DJI",
                model = "Remote Only",
                manualMaxWindResistanceMs = 8.0
            )
        ).first

        assertEquals(8.0, capabilities.maxWindResistanceMs ?: -1.0, 0.0)
        assertTrue(capabilities.manualWindResistanceOverride)
    }

    private fun repository(
        seedVersion: Int = 1,
        storage: TestDroneCatalogStorage = storage(seedVersion),
        http: FakeDroneCatalogHttpClient
    ): DroneTechnicalCatalogRepository =
        DroneTechnicalCatalogRepository(
            storage = storage,
            manifestUrl = ManifestUrl,
            httpClient = http,
            clock = clock,
            checkInterval = Duration.ofHours(24)
        )

    private fun storage(seedVersion: Int): TestDroneCatalogStorage =
        TestDroneCatalogStorage(
            directory = Files.createTempDirectory("drone-catalog-test").toFile(),
            seedCatalogJson = catalogJson(catalogVersion = seedVersion, model = "Seed $seedVersion")
        )

    private fun catalogJson(
        schemaVersion: Int = 2,
        catalogVersion: Int,
        model: String = "Mini $catalogVersion",
        windMs: Double = 10.7
    ): String =
        """
        {
          "schemaVersion": $schemaVersion,
          "catalogVersion": $catalogVersion,
          "version": $schemaVersion,
          "updatedAt": "2026-08-12T00:00:00Z",
          "drones": [
            {
              "manufacturer": "DJI",
              "model": "$model",
              "segment": "CONSUMER",
              "aliases": ["$model alias"],
              "windResistance": {"generalMs": $windMs},
              "operatingTemperatureMinC": -10.0,
              "operatingTemperatureMaxC": 40.0,
              "ingressProtectionRating": null,
              "precipitationCapability": "NOT_DECLARED",
              "source": {
                "type": "MANUFACTURER",
                "name": "DJI $model Specifications",
                "reference": "https://www.dji.com/support/product/neo",
                "verifiedAt": "2026-08-12"
              }
            }
          ]
        }
        """.trimIndent()

    private fun manifestJson(
        schemaVersion: Int = 2,
        catalogVersion: Int,
        catalogJson: String,
        catalogUrl: String = CatalogUrl,
        sha: String = sha256Hex(catalogJson)
    ): String =
        """
        {
          "schemaVersion": $schemaVersion,
          "catalogVersion": $catalogVersion,
          "updatedAt": "2026-08-12T00:00:00Z",
          "catalogUrl": "$catalogUrl",
          "sha256": "$sha"
        }
        """.trimIndent()

    private companion object {
        const val ManifestUrl = DscApiConfig.DroneCatalogManifestUrl
        const val CatalogUrl = DscApiConfig.DroneCatalogUrl
    }
}

private class TestDroneCatalogStorage(
    val directory: File,
    seedCatalogJson: String
) : DroneCatalogStorage by FileDroneCatalogStorage(directory, { seedCatalogJson })

private class FakeDroneCatalogHttpClient(
    private val responses: Map<String, DroneCatalogHttpResponse> = emptyMap(),
    private val error: Throwable? = null
) : DroneCatalogHttpClient {
    val calls = mutableListOf<String>()

    override fun get(url: String, headers: Map<String, String>, timeoutMillis: Int): DroneCatalogHttpResponse {
        calls += url
        error?.let { throw it }
        return responses[url] ?: DroneCatalogHttpResponse(404, "")
    }
}
