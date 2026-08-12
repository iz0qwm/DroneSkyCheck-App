package it.droneskycheck.app.data.drone

import it.droneskycheck.app.data.LocalDrone
import it.droneskycheck.app.data.weather.WeatherAssessmentEngine
import it.droneskycheck.app.data.weather.WeatherMetrics
import java.io.File
import java.net.URI
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DroneTechnicalCatalogResolverTest {
    private val catalog = parseDroneTechnicalCatalog(catalogJson())
    private val resolver = DroneTechnicalCatalogResolver(catalog)

    @Test
    fun normalizesManufacturerVariants() {
        listOf("DJI", "dji", "Dji", "D.J.I.", "d j i", "DJI®").forEach {
            assertEquals("dji", normalizeManufacturer(it))
        }
        listOf("AUTEL", "Autel", "Autel Robotics", "autelrobotics").forEach {
            assertEquals("autel", normalizeManufacturer(it))
        }
    }

    @Test
    fun normalizesModelNoiseButKeepsSemanticTokens() {
        listOf("Air 3S", "AIR3S", "air 3s", "Air-3S", "Air_3_S", "DJI Air 3S", "dji air3s").forEach {
            assertEquals("air3s", normalizeDroneModel(it, "DJI"))
        }

        assertEquals("evonanoplus", normalizeDroneModel("EVO Nano+", "AUTEL"))
        assertEquals("evo2prov3", normalizeDroneModel("EVO II Pro V3", "Autel Robotics"))
        assertNotEquals(normalizeDroneModel("Mini 3", "DJI"), normalizeDroneModel("Mini 3 Pro", "DJI"))
        assertNotEquals(normalizeDroneModel("EVO II Pro", "AUTEL"), normalizeDroneModel("EVO II Pro V3", "AUTEL"))
    }

    @Test
    fun resolvesRegressionAndNewAliases() {
        mapOf(
            "AIR3S" to "DJI Air 3S",
            "Air 3S" to "DJI Air 3S",
            "Air-3S" to "DJI Air 3S",
            "dji air 3 s" to "DJI Air 3S",
            "mini5pro" to "DJI Mini 5 Pro",
            "Mini 5 Pro" to "DJI Mini 5 Pro",
            "M350 RTK" to "DJI Matrice 350 RTK",
            "Matrice350" to "DJI Matrice 350 RTK",
            "M4T" to "DJI Matrice 4T"
        ).forEach { (input, expectedDisplayName) ->
            val result = resolver.resolve("DJI", input)
            assertTrue("DJI $input", result.status == DroneCatalogMatchStatus.EXACT || result.status == DroneCatalogMatchStatus.ALIAS)
            assertEquals(expectedDisplayName, result.matchedDrone?.displayName)
        }

        mapOf(
            "EVO2PROV3" to "AUTEL EVO II Pro V3",
            "Evo II Pro V3" to "AUTEL EVO II Pro V3"
        ).forEach { (input, expectedDisplayName) ->
            val result = resolver.resolve("Autel Robotics", input)
            assertTrue("AUTEL $input", result.status == DroneCatalogMatchStatus.EXACT || result.status == DroneCatalogMatchStatus.ALIAS)
            assertEquals(expectedDisplayName, result.matchedDrone?.displayName)
        }
    }

    @Test
    fun shortAmbiguousInputDoesNotAutoAssignProfile() {
        listOf("3", "3S", "4", "M4", "Pro", "RTK").forEach { model ->
            val result = resolver.resolve("DJI", model)
            assertTrue(result.status == DroneCatalogMatchStatus.NOT_FOUND || result.status == DroneCatalogMatchStatus.SUGGESTED)
            assertNull(result.matchedDrone)
        }
    }

    @Test
    fun catalogValidation() {
        assertEquals(2, catalog.version)
        assertEquals(1, catalog.catalogVersion)
        assertTrue(catalog.drones.size >= 40)

        val canonical = mutableSetOf<String>()
        val aliasMap = mutableMapOf<String, MutableSet<String>>()
        catalog.drones.forEach { drone ->
            assertTrue(drone.manufacturer.isNotBlank())
            assertTrue(drone.model.isNotBlank())
            assertNotEquals(DroneCatalogSegment.UNKNOWN, drone.segment)
            assertNotEquals(DroneCapabilitySource.UNKNOWN, drone.source.type)
            assertSourceUrl(drone.source.reference)
            assertVerifiedAt(drone.source.verifiedAt)
            drone.ingressProtectionRating?.let { assertTrue(it.matches(Regex("IP[0-6X][0-9X]"))) }

            val key = "${normalizeManufacturer(drone.manufacturer)}:${normalizeDroneModel(drone.model, drone.manufacturer)}"
            assertTrue("duplicate canonical model $key", canonical.add(key))

            listOf(
                drone.windResistance.generalMs,
                drone.windResistance.generalMinMs,
                drone.windResistance.generalMaxMs,
                drone.windResistance.takeoffLandingMs,
                drone.windResistance.cruiseMs
            ).filterNotNull().forEach { assertTrue("${drone.displayName} wind must be positive", it > 0.0) }

            if (drone.windResistance.generalMinMs != null && drone.windResistance.generalMaxMs != null) {
                assertTrue(drone.windResistance.generalMinMs <= drone.windResistance.generalMaxMs)
            }

            if (drone.operatingTemperatureMinC != null && drone.operatingTemperatureMaxC != null) {
                assertTrue(drone.operatingTemperatureMinC < drone.operatingTemperatureMaxC)
            }

            assertTrue(
                drone.precipitationCapability in setOf(
                    DronePrecipitationCapability.UNKNOWN,
                    DronePrecipitationCapability.NOT_DECLARED,
                    DronePrecipitationCapability.LIGHT_PRECIPITATION,
                    DronePrecipitationCapability.RAIN_RESISTANT
                )
            )

            val names = listOf(drone.model) + drone.aliases
            names.forEach { alias ->
                val aliasKey = "${normalizeManufacturer(drone.manufacturer)}:${normalizeDroneModel(alias, drone.manufacturer)}"
                aliasMap.getOrPut(aliasKey) { mutableSetOf() }.add(drone.displayName)
            }
        }

        val collisions = aliasMap.filterValues { it.size > 1 }
        assertTrue("ambiguous aliases: $collisions", collisions.isEmpty())
    }

    @Test
    fun jsonNullIpRatingIsTreatedAsMissingIpRating() {
        val neo = resolver.resolve("DJI", "Neo").matchedDrone

        assertNotNull(neo)
        assertNull(neo?.ingressProtectionRating)
        assertTrue(validateDroneTechnicalCatalog(catalog).isEmpty())
    }

    @Test
    fun existingRecordsWereReverifiedAndMappedToV2Wind() {
        val expected = mapOf(
            "Neo" to 8.0,
            "Mini 2" to 8.5,
            "Mini 3" to 10.7,
            "Mini 3 Pro" to 10.7,
            "Mini 4 Pro" to 10.7,
            "Air 2S" to 10.7,
            "Air 3" to 12.0,
            "Air 3S" to 12.0,
            "Mavic 3" to 12.0,
            "Mavic 3 Pro" to 12.0,
            "Avata 2" to 10.7
        )

        expected.forEach { (model, wind) ->
            val match = resolver.resolve("DJI", model)
            assertEquals("model $model", DroneCatalogMatchStatus.EXACT, match.status)
            assertEquals(wind, match.matchedDrone?.maxWindResistanceMs ?: -1.0, 0.0)
            assertNotNull(match.matchedDrone?.operatingTemperatureMinC)
            assertNotNull(match.matchedDrone?.operatingTemperatureMaxC)
            assertEquals(DroneCapabilitySource.MANUFACTURER, match.matchedDrone?.source?.type)
        }
    }

    @Test
    fun capabilitiesUseCatalogManualOverrideOrUnknown() {
        val catalogDrone = LocalDrone(id = "air3s", manufacturer = "DJI", model = "AIR3S")
        val catalogCapabilities = resolver.capabilitiesFor(catalogDrone).first

        assertEquals(12.0, catalogCapabilities.maxWindResistanceMs ?: -1.0, 0.0)
        assertEquals(OperationalWindResistanceBasis.GENERAL, catalogCapabilities.operationalWindResistanceBasis)
        assertEquals(DroneCapabilitySource.MANUFACTURER, catalogCapabilities.windResistanceSource)
        assertEquals(2, catalogCapabilities.technicalCatalogSchemaVersion)
        assertEquals(1, catalogCapabilities.technicalCatalogVersion)

        val manualOverride = resolver.capabilitiesFor(catalogDrone.copy(manualMaxWindResistanceMs = 9.5)).first
        assertEquals(9.5, manualOverride.maxWindResistanceMs ?: -1.0, 0.0)
        assertEquals(DroneCapabilitySource.USER_PROVIDED, manualOverride.windResistanceSource)
        assertTrue(manualOverride.manualWindResistanceOverride)

        val unknownManual = resolver.capabilitiesFor(
            LocalDrone(id = "x", manufacturer = "Custom", model = "Unknown", manualMaxWindResistanceMs = 8.0)
        ).first
        assertEquals(8.0, unknownManual.maxWindResistanceMs ?: -1.0, 0.0)

        val unknown = resolver.capabilitiesFor(LocalDrone(id = "x", manufacturer = "Custom", model = "Unknown")).first
        assertNull(unknown.maxWindResistanceMs)
    }

    @Test
    fun phaseSpecificWindUsesTakeoffLandingForAssessmentButPreservesCruise() {
        val capabilities = resolver.capabilitiesFor(
            LocalDrone(id = "max4t", manufacturer = "AUTEL", model = "EVO Max 4T")
        ).first

        assertEquals(10.7, capabilities.maxWindResistanceMs ?: -1.0, 0.0)
        assertEquals(10.7, capabilities.windResistance.takeoffLandingMs ?: -1.0, 0.0)
        assertEquals(12.0, capabilities.windResistance.cruiseMs ?: -1.0, 0.0)
        assertEquals(OperationalWindResistanceBasis.TAKEOFF_LANDING, capabilities.operationalWindResistanceBasis)
    }

    @Test
    fun sameSyntheticWeatherProducesDifferentAssessmentsFromCatalogRecords() {
        val metrics = WeatherMetrics(
            windSpeedKmh = 25.2,
            windGustsKmh = 36.0,
            precipitationMm = 0.0,
            precipitationProbabilityPct = 5.0,
            visibilityMeters = 12_000.0,
            weatherCode = 0,
            temperatureC = 22.0,
            cloudCoverPct = 20.0
        )
        val weather = WeatherAssessmentEngine().assess(metrics)
        val engine = DroneOperationalAssessmentEngine()
        val nano = resolver.capabilitiesFor(LocalDrone(id = "nano", manufacturer = "AUTEL", model = "EVO Nano")).first
        val neo = resolver.capabilitiesFor(LocalDrone(id = "neo", manufacturer = "DJI", model = "Neo")).first
        val air = resolver.capabilitiesFor(LocalDrone(id = "air", manufacturer = "DJI", model = "Air 3S")).first

        val nanoAssessment = engine.assess(metrics, nano, weather)!!
        val neoAssessment = engine.assess(metrics, neo, weather)!!
        val airAssessment = engine.assess(metrics, air, weather)!!

        assertTrue((airAssessment.score ?: 0) > (neoAssessment.score ?: 100))
        assertTrue((airAssessment.score ?: 0) > (nanoAssessment.score ?: 100))
        assertEquals(DroneOperationalLevel.UNFAVORABLE, nanoAssessment.level)
    }

    @Test
    fun catalogCoverageReport() {
        val total = catalog.drones.size
        val djiConsumer = catalog.drones.count { it.manufacturer == "DJI" && it.segment == DroneCatalogSegment.CONSUMER }
        val djiEnterprise = catalog.drones.count { it.manufacturer == "DJI" && it.segment == DroneCatalogSegment.ENTERPRISE }
        val autelConsumer = catalog.drones.count { it.manufacturer == "AUTEL" && it.segment == DroneCatalogSegment.CONSUMER }
        val autelEnterprise = catalog.drones.count { it.manufacturer == "AUTEL" && it.segment == DroneCatalogSegment.ENTERPRISE }
        val wind = catalog.drones.count { it.maxWindResistanceMs != null }
        val temperature = catalog.drones.count { it.operatingTemperatureMinC != null && it.operatingTemperatureMaxC != null }
        val ip = catalog.drones.count { it.ingressProtectionRating != null }
        val precipitation = catalog.drones.count {
            it.precipitationCapability != DronePrecipitationCapability.UNKNOWN &&
                it.precipitationCapability != DronePrecipitationCapability.NOT_DECLARED
        }

        println(
            """
            Catalog coverage
            Total models: $total
            DJI consumer: $djiConsumer
            DJI enterprise: $djiEnterprise
            Autel consumer: $autelConsumer
            Autel enterprise: $autelEnterprise
            With wind data: $wind
            With temperature data: $temperature
            With IP rating: $ip
            With precipitation capability: $precipitation
            """.trimIndent()
        )

        assertEquals(43, total)
        assertEquals(22, djiConsumer)
        assertEquals(11, djiEnterprise)
        assertEquals(5, autelConsumer)
        assertEquals(5, autelEnterprise)
        assertEquals(total, wind)
        assertEquals(total, temperature)
        assertEquals(8, ip)
        assertEquals(0, precipitation)
    }

    private fun assertSourceUrl(reference: String?) {
        if (reference == null) fail("missing source reference")
        val url = reference!!
        val uri = URI(url)
        assertTrue(url, uri.scheme == "https")
        assertTrue(url, uri.host.endsWith("dji.com") || uri.host.endsWith("autelrobotics.com"))
    }

    private fun assertVerifiedAt(value: String?) {
        if (value == null) fail("missing verifiedAt")
        val parsed = value!!
        LocalDate.parse(parsed)
    }

    private fun catalogJson(): String {
        val candidates = listOf(
            File("app/src/main/assets/drone_technical_catalog.json"),
            File("src/main/assets/drone_technical_catalog.json")
        )
        return candidates.first { it.exists() }.readText(Charsets.UTF_8)
    }
}
