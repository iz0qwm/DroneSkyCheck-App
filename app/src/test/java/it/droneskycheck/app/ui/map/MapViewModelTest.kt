package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.Baseline
import it.droneskycheck.app.data.InMemoryMapPreferences
import it.droneskycheck.app.data.LegalTimelineClient
import it.droneskycheck.app.data.LegalTimelineMeta
import it.droneskycheck.app.data.LegalTimelineQuery
import it.droneskycheck.app.data.LegalTimelineRepositoryError
import it.droneskycheck.app.data.LegalTimelineResponse
import it.droneskycheck.app.data.LegalTimelineSegment
import it.droneskycheck.app.data.LegalTimelineState
import it.droneskycheck.app.data.LegalTimelineWindow
import it.droneskycheck.app.data.LocalDrone
import it.droneskycheck.app.data.LocalPilotStore
import it.droneskycheck.app.data.Meta
import it.droneskycheck.app.data.Position
import it.droneskycheck.app.data.Verdict
import it.droneskycheck.app.data.ZoneCheckV3Client
import it.droneskycheck.app.data.ZoneCheckV3Response
import it.droneskycheck.app.data.drone.DroneCatalogMatchStatus
import it.droneskycheck.app.data.drone.DroneOperationalLevel
import it.droneskycheck.app.data.drone.DroneTechnicalCatalogClient
import it.droneskycheck.app.data.drone.DroneTechnicalCatalogResolver
import it.droneskycheck.app.data.drone.InMemoryDroneTechnicalCatalogClient
import it.droneskycheck.app.data.drone.parseDroneTechnicalCatalog
import it.droneskycheck.app.data.flight.FlightOpportunityStatus
import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.data.weather.WeatherForecastCacheDto
import it.droneskycheck.app.data.weather.WeatherForecastClient
import it.droneskycheck.app.data.weather.WeatherForecastHour
import it.droneskycheck.app.data.weather.WeatherForecastLocation
import it.droneskycheck.app.data.weather.WeatherForecastMetadata
import it.droneskycheck.app.data.weather.WeatherForecastUnitsDto
import it.droneskycheck.app.data.weather.WeatherMetrics
import java.time.LocalDate
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-11T06:00:00Z"), ZoneOffset.UTC)

    @Test
    fun operationalContextDoesNotStartOnPointSelection() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val weather = FakeWeatherClient(forecast = weatherForecast())
        val legal = FakeLegalTimelineClient()
        val viewModel = viewModel(
            scope = scope,
            legal = legal,
            weather = weather,
            preferences = InMemoryMapPreferences()
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { !viewModel.uiState.value.isVerdictLoading && !viewModel.uiState.value.isLegalTimelineLoading }

        assertFalse(viewModel.uiState.value.isOperationalContextRequested)
        assertFalse(viewModel.uiState.value.isWeatherAnalysisEnabled)
        assertEquals(0, legal.calls)
        assertEquals(0, weather.calls)
        assertNull(viewModel.uiState.value.weatherAssessment)
        scope.cancel()
    }

    @Test
    fun requestingOperationalContextStartsTimelineAndWeatherForSelectedPoint() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val weather = FakeWeatherClient(forecast = weatherForecast())
        val legal = FakeLegalTimelineClient()
        val viewModel = viewModel(
            scope = scope,
            legal = legal,
            weather = weather,
            preferences = InMemoryMapPreferences()
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.onOperationalContextRequested()
        waitUntil { viewModel.uiState.value.flightOpportunityResult != null }

        assertTrue(viewModel.uiState.value.isOperationalContextRequested)
        assertTrue(viewModel.uiState.value.isWeatherAnalysisEnabled)
        assertEquals(1, legal.calls)
        assertEquals(MapPoint(41.9, 12.5), legal.lastPoint)
        assertEquals(Instant.parse("2026-08-11T06:00:00Z"), legal.lastFrom)
        assertEquals(Instant.parse("2026-08-16T22:00:00Z"), legal.lastTo)
        assertEquals(MapPoint(41.9, 12.5), weather.lastPoint)
        assertEquals(FlightOpportunityStatus.PARTIAL, viewModel.uiState.value.flightOpportunityStatus)
        scope.cancel()
    }

    @Test
    fun newSelectionRecalculatesOperationalContextWhenWeatherAlreadyEnabled() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val weather = FakeWeatherClient(forecast = weatherForecast())
        val legal = FakeLegalTimelineClient()
        val viewModel = viewModel(
            scope = scope,
            legal = legal,
            weather = weather,
            preferences = InMemoryMapPreferences()
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        viewModel.onOperationalContextRequested()
        waitUntil { viewModel.uiState.value.flightOpportunityResult != null }
        viewModel.onMapTapped(selection(42.0, 12.6))
        waitUntil {
            legal.calls == 2 &&
                weather.calls == 2 &&
                !viewModel.uiState.value.isWeatherAnalysisLoading &&
                viewModel.uiState.value.flightOpportunityResult != null
        }

        assertTrue(viewModel.uiState.value.isOperationalContextRequested)
        assertTrue(viewModel.uiState.value.isWeatherAnalysisEnabled)
        assertFalse(viewModel.uiState.value.isWeatherAnalysisLoading)
        assertEquals(MapPoint(42.0, 12.6), legal.lastPoint)
        assertEquals(MapPoint(42.0, 12.6), weather.lastPoint)
        assertEquals(FlightOpportunityStatus.PARTIAL, viewModel.uiState.value.flightOpportunityStatus)
        assertNull(viewModel.uiState.value.weatherError)
        scope.cancel()
    }

    @Test
    fun olderLegalTimelineResponseDoesNotOverwriteNewerSelection() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val legal = FakeLegalTimelineClient(
            delaysByLatitude = mapOf(1.0 to 120L, 2.0 to 0L)
        )
        val viewModel = viewModel(
            scope = scope,
            legal = legal,
            preferences = InMemoryMapPreferences()
        )

        viewModel.onMapTapped(selection(1.0, 1.0))
        viewModel.onOperationalContextRequested()
        viewModel.onMapTapped(selection(2.0, 2.0))
        viewModel.onOperationalContextRequested()

        waitUntil { viewModel.uiState.value.legalTimeline?.query?.lat == 2.0 }
        delay(180)

        assertEquals(MapPoint(2.0, 2.0), viewModel.uiState.value.selectedPoint)
        assertEquals(2.0, viewModel.uiState.value.legalTimeline?.query?.lat ?: -1.0, 0.0)
        scope.cancel()
    }

    @Test
    fun legalTimelineFailureStaysGenericInUi() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(
            scope = scope,
            legal = FakeLegalTimelineClient(
                failure = LegalTimelineRepositoryError.Timeout("Read timed out")
            ),
            preferences = InMemoryMapPreferences()
        )

        viewModel.onMapTapped(selection(41.1389, 16.7606))
        viewModel.onOperationalContextRequested()
        waitUntil { viewModel.uiState.value.legalTimelineError != null }

        assertEquals("Previsione temporale non disponibile", viewModel.uiState.value.legalTimelineError)
        assertNull(viewModel.uiState.value.legalTimeline)
        scope.cancel()
    }

    @Test
    fun loadsSelectedDroneFromLocalFleet() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pilotStore = FakePilotStore(
            listOf(
                LocalDrone(id = "mini", manufacturer = "DJI", model = "Mini", classLabel = "C0"),
                LocalDrone(id = "air", manufacturer = "DJI", model = "Air", classLabel = "C1", isSelected = true)
            )
        )
        val viewModel = viewModel(
            scope = scope,
            weather = FakeWeatherClient(),
            preferences = InMemoryMapPreferences(),
            pilotStore = pilotStore
        )

        waitUntil { viewModel.uiState.value.selectedDrone != null }

        assertEquals("air", viewModel.uiState.value.selectedDrone?.id)
        assertEquals(2, viewModel.uiState.value.droneFleet.size)
        scope.cancel()
    }

    @Test
    fun changingDronePersistsSelectionAndDoesNotRelaunchTimelineOrWeather() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val weather = FakeWeatherClient(forecast = weatherForecast(windKmh = 10.0, gustKmh = 20.0))
        val legal = FakeLegalTimelineClient()
        val pilotStore = FakePilotStore(
            listOf(
                LocalDrone(id = "mini", manufacturer = "DJI", model = "Mini", manualMaxWindResistanceMs = 12.0, isSelected = true),
                LocalDrone(id = "air", manufacturer = "DJI", model = "Air", manualMaxWindResistanceMs = 4.0)
            )
        )
        val viewModel = viewModel(
            scope = scope,
            legal = legal,
            weather = weather,
            preferences = InMemoryMapPreferences(),
            pilotStore = pilotStore
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        viewModel.onOperationalContextRequested()
        waitUntil { weather.calls == 1 && viewModel.uiState.value.flightOpportunityResult?.bestOpportunity != null }
        val initialDroneScore = viewModel.uiState.value.flightOpportunityResult?.bestOpportunity?.droneScore
        val legalCalls = legal.calls
        val weatherCalls = weather.calls

        viewModel.onDroneSelected("air")
        waitUntil {
            viewModel.uiState.value.selectedDrone?.id == "air" &&
                viewModel.uiState.value.droneOperationalAssessment?.capabilities?.maxWindResistanceMs == 4.0
        }

        assertEquals(1, pilotStore.selectCalls)
        assertEquals(legalCalls, legal.calls)
        assertEquals(weatherCalls, weather.calls)
        assertTrue(initialDroneScore != viewModel.uiState.value.droneOperationalAssessment?.score)
        assertEquals(
            viewModel.uiState.value.flightOpportunityStatus,
            viewModel.uiState.value.flightOpportunityResult?.status
        )
        scope.cancel()
    }

    @Test
    fun meteoOffDoesNotCalculateDroneAssessment() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pilotStore = FakePilotStore(listOf(LocalDrone(id = "mini", manufacturer = "DJI", model = "Mini", isSelected = true)))
        val viewModel = viewModel(
            scope = scope,
            weather = FakeWeatherClient(forecast = weatherForecast()),
            preferences = InMemoryMapPreferences(),
            pilotStore = pilotStore
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }

        assertFalse(viewModel.uiState.value.isWeatherAnalysisEnabled)
        assertNull(viewModel.uiState.value.droneOperationalAssessment)
        assertEquals(FlightOpportunityStatus.IDLE, viewModel.uiState.value.flightOpportunityStatus)
        assertNull(viewModel.uiState.value.flightOpportunityResult)
        scope.cancel()
    }

    @Test
    fun openingOperationalReportDoesNotRelaunchTimelineOrWeather() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val legal = FakeLegalTimelineClient()
        val weather = FakeWeatherClient(forecast = weatherForecast())
        val viewModel = viewModel(
            scope = scope,
            legal = legal,
            weather = weather,
            preferences = InMemoryMapPreferences()
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        viewModel.onOperationalContextRequested()
        waitUntil { legal.calls == 1 && weather.calls == 1 && !viewModel.uiState.value.isWeatherAnalysisLoading }
        val legalCalls = legal.calls
        val weatherCalls = weather.calls

        viewModel.onOperationalReportExpansionChanged(true)
        viewModel.onOperationalReportExpansionChanged(false)

        assertEquals(legalCalls, legal.calls)
        assertEquals(weatherCalls, weather.calls)
        assertFalse(viewModel.uiState.value.isOperationalReportExpanded)
        scope.cancel()
    }

    @Test
    fun flightOpportunityRecommendsBestDroneFromFleetForWind() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(
            scope = scope,
            weather = FakeWeatherClient(forecast = weatherForecast(windKmh = 10.0, gustKmh = 20.0)),
            preferences = InMemoryMapPreferences(),
            pilotStore = FakePilotStore(
                listOf(
                    LocalDrone(
                        id = "mini",
                        manufacturer = "DJI",
                        model = "Mini 3 Pro",
                        manualMaxWindResistanceMs = 6.0,
                        isSelected = true
                    ),
                    LocalDrone(
                        id = "air",
                        manufacturer = "DJI",
                        model = "AIR 3S",
                        manualMaxWindResistanceMs = 12.0
                    )
                )
            )
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        viewModel.onOperationalContextRequested()
        waitUntil { viewModel.uiState.value.flightOpportunityResult?.droneRecommendation != null }

        val recommendation = viewModel.uiState.value.flightOpportunityResult?.droneRecommendation
        assertEquals("air", recommendation?.recommended?.droneId)
        assertEquals(2, recommendation?.compared?.size)
        assertTrue(
            (recommendation?.recommended?.droneScore ?: 0) >=
                (recommendation?.compared?.firstOrNull { it.droneId == "mini" }?.droneScore ?: 0)
        )
        scope.cancel()
    }

    @Test
    fun meteoOnCalculatesPartialDroneAssessmentForSelectedDrone() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(
            scope = scope,
            weather = FakeWeatherClient(forecast = weatherForecast()),
            preferences = InMemoryMapPreferences(),
            pilotStore = FakePilotStore(listOf(LocalDrone(id = "mini", manufacturer = "DJI", model = "Mini", isSelected = true)))
        )

        waitUntil { viewModel.uiState.value.selectedDrone != null }
        viewModel.onMapTapped(selection(41.9, 12.5))
        viewModel.onOperationalContextRequested()
        waitUntil { viewModel.uiState.value.droneOperationalAssessment != null }

        assertEquals("mini", viewModel.uiState.value.selectedDrone?.id)
        assertEquals(DroneOperationalLevel.UNKNOWN, viewModel.uiState.value.droneOperationalAssessment?.level)
        scope.cancel()
    }

    @Test
    fun catalogRecognizedDroneFeedsSpecificDroneAssessment() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(
            scope = scope,
            weather = FakeWeatherClient(forecast = weatherForecast(windKmh = 22.0, gustKmh = 38.0)),
            preferences = InMemoryMapPreferences(),
            pilotStore = FakePilotStore(listOf(LocalDrone(id = "air", manufacturer = "DJI", model = "AIR3S", isSelected = true))),
            catalog = catalogClient()
        )

        waitUntil { viewModel.uiState.value.selectedDroneCatalogMatch != null }
        viewModel.onMapTapped(selection(41.9, 12.5))
        viewModel.onOperationalContextRequested()
        waitUntil { viewModel.uiState.value.droneOperationalAssessment != null }

        assertEquals(DroneCatalogMatchStatus.EXACT, viewModel.uiState.value.selectedDroneCatalogMatch?.status)
        assertEquals(12.0, viewModel.uiState.value.droneOperationalAssessment?.capabilities?.maxWindResistanceMs ?: -1.0, 0.0)
        assertTrue(viewModel.uiState.value.droneOperationalAssessment?.score != null)
        scope.cancel()
    }

    @Test
    fun ambiguousDroneInputDoesNotApplyCatalogCapabilityAutomatically() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(
            scope = scope,
            weather = FakeWeatherClient(forecast = weatherForecast()),
            preferences = InMemoryMapPreferences(),
            pilotStore = FakePilotStore(listOf(LocalDrone(id = "amb", manufacturer = "DJI", model = "3S", isSelected = true))),
            catalog = catalogClient()
        )

        waitUntil { viewModel.uiState.value.selectedDroneCatalogMatch != null }
        viewModel.onMapTapped(selection(41.9, 12.5))
        viewModel.onOperationalContextRequested()
        waitUntil { viewModel.uiState.value.droneOperationalAssessment != null }

        assertNull(viewModel.uiState.value.selectedDroneCatalogMatch?.matchedDrone)
        assertNull(viewModel.uiState.value.droneOperationalAssessment?.capabilities?.maxWindResistanceMs)
        assertEquals(DroneOperationalLevel.UNKNOWN, viewModel.uiState.value.droneOperationalAssessment?.level)
        scope.cancel()
    }

    private fun viewModel(
        scope: CoroutineScope,
        legal: FakeLegalTimelineClient = FakeLegalTimelineClient(),
        weather: FakeWeatherClient = FakeWeatherClient(),
        preferences: InMemoryMapPreferences,
        pilotStore: LocalPilotStore = FakePilotStore(),
        catalog: DroneTechnicalCatalogClient = InMemoryDroneTechnicalCatalogClient()
    ): MapViewModel =
        MapViewModel(
            zoneCheckRepository = FakeZoneCheckClient(),
            legalTimelineRepository = legal,
            weatherForecastRepository = weather,
            mapPreferences = preferences,
            localPilotStore = pilotStore,
            droneTechnicalCatalog = catalog,
            clock = clock,
            timelineZoneId = ZoneId.of("Europe/Rome"),
            externalScope = scope
        )

    private fun selection(lat: Double, lon: Double): MapTapSelection =
        MapTapSelection(
            point = MapPoint(lat, lon),
            zone = null
        )

    private suspend fun waitUntil(predicate: () -> Boolean) {
        withTimeout(1_000) {
            while (!predicate()) {
                delay(10)
            }
        }
    }
}

private fun catalogClient(): DroneTechnicalCatalogClient =
    InMemoryDroneTechnicalCatalogClient(
        DroneTechnicalCatalogResolver(
            parseDroneTechnicalCatalog(
                listOf(
                    File("app/src/main/assets/drone_technical_catalog.json"),
                    File("src/main/assets/drone_technical_catalog.json")
                ).first { it.exists() }.readText(Charsets.UTF_8)
            )
        )
    )

private class FakeZoneCheckClient : ZoneCheckV3Client {
    override fun check(lat: Double, lon: Double): ZoneCheckV3Response =
        ZoneCheckV3Response(
            position = Position(lat, lon),
            verdict = Verdict(
                status = "OPEN",
                maxAltitudeMetersAgl = 120,
                source = "BASE",
                explanation = "Fixture"
            ),
            zones = emptyList(),
            blockers = emptyList(),
            warnings = emptyList(),
            baseline = Baseline(
                maxAltitudeMetersAgl = 120,
                representedAsZone = false
            ),
            meta = Meta(
                engine = "DSC",
                version = "v3"
            )
        )
}

private class FakeLegalTimelineClient(
    private val delaysByLatitude: Map<Double, Long> = emptyMap(),
    private val failure: Throwable? = null
) : LegalTimelineClient {
    var calls: Int = 0
        private set
    var lastPoint: MapPoint? = null
        private set
    var lastFrom: Instant? = null
        private set
    var lastTo: Instant? = null
        private set

    override suspend fun getLegalTimeline(
        lat: Double,
        lon: Double,
        from: Instant,
        to: Instant
    ): Result<LegalTimelineResponse> {
        calls += 1
        lastPoint = MapPoint(lat, lon)
        lastFrom = from
        lastTo = to
        delay(delaysByLatitude[lat] ?: 0L)
        failure?.let { return Result.failure(it) }
        return Result.success(timeline(lat, lon, from, to))
    }

    private fun timeline(lat: Double, lon: Double, from: Instant, to: Instant): LegalTimelineResponse =
        LegalTimelineResponse(
            generatedAt = from,
            query = LegalTimelineQuery(lat, lon),
            window = LegalTimelineWindow(from, to, "UTC"),
            segments = listOf(
                LegalTimelineSegment(
                    from = from,
                    to = to,
                    state = LegalTimelineState.AVAILABLE,
                    rawState = "AVAILABLE",
                    maxAltitudeAgl = 120,
                    authorization = null,
                    contributors = emptyList(),
                    warnings = emptyList(),
                    confidence = "HIGH",
                    reasonCodes = emptyList()
                )
            ),
            diagnostics = emptyList(),
            meta = LegalTimelineMeta(
                schemaVersion = 1,
                engine = "DSC",
                version = "legal-timeline-v1",
                maxWindowHours = 168
            )
        )
}

private class FakeWeatherClient(
    private val forecast: WeatherForecast? = null,
    private val delaysByLatitude: Map<Double, Long> = emptyMap()
) : WeatherForecastClient {
    var calls: Int = 0
        private set
    var lastPoint: MapPoint? = null
        private set

    override suspend fun getForecast(latitude: Double, longitude: Double): Result<WeatherForecast> {
        calls += 1
        lastPoint = MapPoint(latitude, longitude)
        delay(delaysByLatitude[latitude] ?: 0L)
        return forecast?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("weather fixture"))
    }
}

private class FakePilotStore(
    initialDrones: List<LocalDrone> = emptyList()
) : LocalPilotStore {
    private var drones = initialDrones
    var selectCalls: Int = 0
        private set

    override suspend fun getDrones(): List<LocalDrone> = drones

    override suspend fun getSelectedDrone(): LocalDrone? =
        drones.firstOrNull { it.isSelected } ?: drones.firstOrNull()

    override suspend fun selectDrone(id: String) {
        selectCalls += 1
        drones = drones.map { it.copy(isSelected = it.id == id) }
    }
}

private fun weatherForecast(
    windKmh: Double = 8.0,
    gustKmh: Double = 12.0
): WeatherForecast {
    val rome = ZoneId.of("Europe/Rome")
    val local = LocalDate.parse("2026-08-11").atTime(8, 0)
    return WeatherForecast(
        location = WeatherForecastLocation(null, null, null, "Europe/Rome", "CEST", 7200),
        timezone = rome,
        generatedAt = Instant.parse("2026-08-11T06:00:00Z"),
        providerFetchedAt = Instant.parse("2026-08-11T06:00:00Z"),
        hours = listOf(
            WeatherForecastHour(
                instant = local.atZone(rome).toInstant(),
                offsetDateTime = null,
                localDateTime = local,
                localTimeText = local.toString(),
                utcOffsetSeconds = 7200,
                metrics = WeatherMetrics(
                    windSpeedKmh = windKmh,
                    windGustsKmh = gustKmh,
                    precipitationMm = 0.0,
                    precipitationProbabilityPct = 5.0,
                    visibilityMeters = 12_000.0,
                    weatherCode = 0,
                    temperatureC = 22.0,
                    cloudCoverPct = 20.0
                ),
                missingFields = emptyList()
            )
        ),
        days = emptyList(),
        warnings = emptyList(),
        metadata = WeatherForecastMetadata(
            schemaVersion = 1,
            provider = "fixture",
            forecastDays = 7,
            units = WeatherForecastUnitsDto(null, null, null, null, null, null, null, null),
            cache = WeatherForecastCacheDto(false, null, null)
        )
    )
}
