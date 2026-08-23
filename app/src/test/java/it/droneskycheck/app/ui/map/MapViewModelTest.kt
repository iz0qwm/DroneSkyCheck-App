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
import it.droneskycheck.app.data.flight.DroneWindowCompatibility
import it.droneskycheck.app.data.flight.FlightLightPreference
import it.droneskycheck.app.data.flight.FlightOpportunityMode
import it.droneskycheck.app.data.flight.FlightOpportunityReasonCode
import it.droneskycheck.app.data.flight.FlightOpportunityStatus
import it.droneskycheck.app.data.help.HelpManifest
import it.droneskycheck.app.data.help.HelpManifestClient
import it.droneskycheck.app.data.help.HelpOnboardingStep
import it.droneskycheck.app.data.help.HelpTourAction
import it.droneskycheck.app.data.help.HelpTourTarget
import it.droneskycheck.app.data.help.InMemoryHelpManifestClient
import it.droneskycheck.app.data.help.InMemoryHelpPreferences
import it.droneskycheck.app.data.traffic.TrafficAwarenessClient
import it.droneskycheck.app.data.traffic.TrafficAwarenessResponse
import it.droneskycheck.app.data.traffic.TrafficCacheInfo
import it.droneskycheck.app.data.traffic.TrafficCenter
import it.droneskycheck.app.data.traffic.TrafficAircraft
import it.droneskycheck.app.data.traffic.TrafficAltitude
import it.droneskycheck.app.data.traffic.TrafficIdentifiers
import it.droneskycheck.app.data.traffic.TrafficMotion
import it.droneskycheck.app.data.traffic.TrafficPosition
import it.droneskycheck.app.data.traffic.TrafficProviderStatus
import it.droneskycheck.app.data.traffic.TrafficRelative
import it.droneskycheck.app.data.traffic.TrafficSource
import it.droneskycheck.app.data.traffic.TrafficSummary
import it.droneskycheck.app.data.traffic.TrafficTarget
import it.droneskycheck.app.data.traffic.TrafficTime
import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.data.weather.WeatherForecastCacheDto
import it.droneskycheck.app.data.weather.WeatherForecastClient
import it.droneskycheck.app.data.weather.WeatherForecastHour
import it.droneskycheck.app.data.weather.WeatherForecastLocation
import it.droneskycheck.app.data.weather.WeatherForecastMetadata
import it.droneskycheck.app.data.weather.WeatherForecastUnitsDto
import it.droneskycheck.app.data.weather.WeatherMetrics
import it.droneskycheck.app.data.weather.NearbyMetar
import it.droneskycheck.app.data.weather.NearbyMetarClient
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun enablingLocationAnalyzesFirstUserLocation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(
            scope = scope,
            preferences = InMemoryMapPreferences()
        )

        viewModel.onLocationEnabled()
        viewModel.onUserLocationUpdated(
            UserLocation(
                point = MapPoint(43.0, 10.0),
                accuracyMeters = 8f,
                isPrecise = true
            )
        )
        waitUntil { viewModel.uiState.value.selectedPoint != null && !viewModel.uiState.value.isVerdictLoading }

        assertEquals(MapPoint(43.0, 10.0), viewModel.uiState.value.selectedPoint)
        assertTrue(viewModel.uiState.value.isZoneSheetVisible)
        assertTrue(viewModel.uiState.value.shouldCenterOnUserLocation)
        scope.cancel()
    }

    @Test
    fun automaticLocationPreferenceIsLoadedAndPersisted() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val preferences = InMemoryMapPreferences(initialAutomaticLocationEnabled = true)
        val viewModel = viewModel(
            scope = scope,
            preferences = preferences
        )

        assertTrue(viewModel.uiState.value.isAutomaticLocationEnabled)

        viewModel.onAutomaticLocationEnabledChanged(false)

        assertFalse(viewModel.uiState.value.isAutomaticLocationEnabled)
        assertFalse(preferences.isAutomaticLocationEnabled())
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
    fun noOpenAuthRequiredProposesTechnicalPlanningWithoutDeclaringOpen() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(
            scope = scope,
            legal = FakeLegalTimelineClient(state = LegalTimelineState.AUTH_REQUIRED),
            weather = FakeWeatherClient(forecast = weatherForecast(hours = 2)),
            preferences = InMemoryMapPreferences(),
            pilotStore = FakePilotStore(
                listOf(LocalDrone(id = "mini", manufacturer = "DJI", model = "Mini 3 Pro", manualMaxWindResistanceMs = 10.0, isSelected = true))
            )
        )

        waitUntil { viewModel.uiState.value.selectedDrone != null }
        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.onOperationalContextRequested()
        waitUntil { viewModel.uiState.value.flightOpportunityResult != null }

        val result = requireNotNull(viewModel.uiState.value.flightOpportunityResult)
        assertEquals(FlightOpportunityMode.OPEN, viewModel.uiState.value.flightOpportunityMode)
        assertEquals(FlightOpportunityMode.OPEN, result.mode)
        assertEquals(FlightOpportunityStatus.NO_OPEN_WINDOW, result.status)
        assertTrue(result.technicalPlanningAvailable)
        assertNull(result.bestOpportunity)
        assertTrue(result.blockers.contains(FlightOpportunityReasonCode.AUTHORIZATION_REQUIRED))

        viewModel.onTechnicalPlanningRequested()
        waitUntil { viewModel.uiState.value.flightOpportunityResult?.mode == FlightOpportunityMode.TECHNICAL_PLANNING }

        val technical = requireNotNull(viewModel.uiState.value.flightOpportunityResult)
        assertEquals(FlightOpportunityMode.TECHNICAL_PLANNING, viewModel.uiState.value.flightOpportunityMode)
        assertEquals(FlightOpportunityStatus.READY, technical.status)
        assertEquals(LegalTimelineState.AUTH_REQUIRED, technical.bestOpportunity?.legalState)
        assertFalse(technical.bestOpportunity?.reasons?.contains(FlightOpportunityReasonCode.LEGAL_OPEN) == true)
        assertTrue(technical.blockers.contains(FlightOpportunityReasonCode.AUTHORIZATION_REQUIRED))
        scope.cancel()
    }

    @Test
    fun newSelectionRequiresExplicitWeatherRequestAgain() = runBlocking {
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
        waitUntil { !viewModel.uiState.value.isVerdictLoading }

        assertFalse(viewModel.uiState.value.isOperationalContextRequested)
        assertFalse(viewModel.uiState.value.isWeatherAnalysisEnabled)
        assertFalse(viewModel.uiState.value.isWeatherAnalysisLoading)
        assertEquals(1, legal.calls)
        assertEquals(1, weather.calls)
        assertEquals(MapPoint(41.9, 12.5), legal.lastPoint)
        assertEquals(MapPoint(41.9, 12.5), weather.lastPoint)
        assertEquals(MapPoint(42.0, 12.6), viewModel.uiState.value.selectedPoint)
        assertNull(viewModel.uiState.value.weatherForecast)
        assertNull(viewModel.uiState.value.weatherAssessment)
        assertEquals(FlightOpportunityStatus.IDLE, viewModel.uiState.value.flightOpportunityStatus)
        assertNull(viewModel.uiState.value.flightOpportunityResult)
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
        waitUntil { viewModel.uiState.value.selectedPoint != null }
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
    fun changingLightPreferenceRecalculatesOpportunityWithoutReloadingData() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val legal = FakeLegalTimelineClient()
        val weather = FakeWeatherClient(
            forecast = weatherForecast(
                start = Instant.parse("2026-08-14T04:00:00Z"),
                hours = 18
            )
        )
        val viewModel = viewModel(
            scope = scope,
            legal = legal,
            weather = weather,
            preferences = InMemoryMapPreferences()
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        viewModel.onOperationalContextRequested()
        waitUntil { viewModel.uiState.value.flightOpportunityResult?.bestOpportunity != null }
        val selectedPoint = viewModel.uiState.value.selectedPoint
        val daylightBest = viewModel.uiState.value.flightOpportunityResult?.bestOpportunity?.from
        val legalCalls = legal.calls
        val weatherCalls = weather.calls

        viewModel.onFlightLightPreferenceSelected(FlightLightPreference.SUNSET)
        waitUntil { viewModel.uiState.value.selectedLightPreference == FlightLightPreference.SUNSET }

        assertEquals(selectedPoint, viewModel.uiState.value.selectedPoint)
        assertEquals(legalCalls, legal.calls)
        assertEquals(weatherCalls, weather.calls)
        assertNotEquals(daylightBest, viewModel.uiState.value.flightOpportunityResult?.bestOpportunity?.from)
        scope.cancel()
    }

    @Test
    fun flightOpportunityRecommendsBestDroneFromFleetForWind() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(
            scope = scope,
            weather = FakeWeatherClient(forecast = weatherForecast(windKmh = 8.0, gustKmh = 12.0)),
            preferences = InMemoryMapPreferences(),
            pilotStore = FakePilotStore(
                listOf(
                    LocalDrone(
                        id = "mini",
                        manufacturer = "DJI",
                        model = "Mini 3 Pro",
                        weight = 249.0,
                        manualMaxWindResistanceMs = 6.0,
                        isSelected = true
                    ),
                    LocalDrone(
                        id = "air",
                        manufacturer = "DJI",
                        model = "AIR 3S",
                        weight = 724.0,
                        manualMaxWindResistanceMs = 12.0
                    )
                )
            )
        )

        waitUntil { viewModel.uiState.value.selectedDrone != null }
        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.onOperationalContextRequested()
        waitUntil { viewModel.uiState.value.flightOpportunityResult?.droneRecommendation != null }

        val recommendation = viewModel.uiState.value.flightOpportunityResult?.droneRecommendation
        assertEquals("air", recommendation?.recommended?.droneId)
        assertEquals("air", recommendation?.bestOperationalMargin?.droneId)
        assertEquals("mini", recommendation?.lightestCompatible?.droneId)
        assertEquals(2, recommendation?.usableCount)
        assertEquals(DroneWindowCompatibility.USABLE, recommendation?.compared?.firstOrNull { it.droneId == "mini" }?.compatibility)
        assertEquals(DroneWindowCompatibility.USABLE, recommendation?.compared?.firstOrNull { it.droneId == "air" }?.compatibility)
        assertEquals(2, recommendation?.compared?.size)
        assertTrue(
            (recommendation?.recommended?.droneScore ?: 0) >=
                (recommendation?.compared?.firstOrNull { it.droneId == "mini" }?.droneScore ?: 0)
        )
        scope.cancel()
    }

    @Test
    fun technicalPlanningKeepsAllCompatibleFleetCandidatesVisible() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(
            scope = scope,
            legal = FakeLegalTimelineClient(state = LegalTimelineState.AUTH_REQUIRED),
            weather = FakeWeatherClient(forecast = weatherForecast(windKmh = 8.0, gustKmh = 12.0, hours = 2)),
            preferences = InMemoryMapPreferences(),
            pilotStore = FakePilotStore(
                listOf(
                    LocalDrone(
                        id = "mini",
                        manufacturer = "DJI",
                        model = "Mini 3 Pro",
                        weight = 249.0,
                        manualMaxWindResistanceMs = 10.0,
                        isSelected = true
                    ),
                    LocalDrone(
                        id = "air",
                        manufacturer = "DJI",
                        model = "AIR 3S",
                        weight = 724.0,
                        manualMaxWindResistanceMs = 12.0
                    )
                )
            )
        )

        waitUntil { viewModel.uiState.value.selectedDrone != null }
        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.onOperationalContextRequested()
        waitUntil { viewModel.uiState.value.flightOpportunityResult?.technicalPlanningAvailable == true }
        viewModel.onTechnicalPlanningRequested()
        waitUntil { viewModel.uiState.value.flightOpportunityResult?.droneRecommendation != null }

        val result = requireNotNull(viewModel.uiState.value.flightOpportunityResult)
        val recommendation = requireNotNull(result.droneRecommendation)
        assertEquals(FlightOpportunityMode.TECHNICAL_PLANNING, result.mode)
        assertEquals(2, recommendation.usableCount)
        assertEquals(2, recommendation.compared.size)
        assertEquals(DroneWindowCompatibility.USABLE, recommendation.compared.first { it.droneId == "mini" }.compatibility)
        assertEquals(DroneWindowCompatibility.USABLE, recommendation.compared.first { it.droneId == "air" }.compatibility)
        assertEquals("mini", recommendation.lightestCompatible?.droneId)
        scope.cancel()
    }

    @Test
    fun lightDroneAtGustLimitIsCautionNotLightestCompatible() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = fleetRecommendationViewModel(
            scope = scope,
            windKmh = 10.0,
            gustKmh = 17.0,
            drones = listOf(
                LocalDrone(id = "mini", manufacturer = "DJI", model = "Mini 3 Pro", weight = 249.0, manualMaxWindResistanceMs = 6.0, isSelected = true),
                LocalDrone(id = "air", manufacturer = "DJI", model = "AIR 3S", weight = 724.0, manualMaxWindResistanceMs = 12.0)
            )
        )

        val recommendation = requireNotNull(viewModel.uiState.value.flightOpportunityResult?.droneRecommendation)
        assertEquals(DroneWindowCompatibility.USABLE_WITH_CAUTION, recommendation.compared.first { it.droneId == "mini" }.compatibility)
        assertEquals(DroneWindowCompatibility.USABLE, recommendation.compared.first { it.droneId == "air" }.compatibility)
        assertEquals("air", recommendation.lightestCompatible?.droneId)
        assertEquals("air", recommendation.bestOperationalMargin?.droneId)
        scope.cancel()
    }

    @Test
    fun onlyOneCompatibleDroneDoesNotHideOtherFleetStatuses() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = fleetRecommendationViewModel(
            scope = scope,
            windKmh = 18.0,
            gustKmh = 24.0,
            drones = listOf(
                LocalDrone(id = "mini", manufacturer = "DJI", model = "Mini 3 Pro", weight = 249.0, manualMaxWindResistanceMs = 6.0, isSelected = true),
                LocalDrone(id = "air", manufacturer = "DJI", model = "AIR 3S", weight = 724.0, manualMaxWindResistanceMs = 14.0)
            )
        )

        val recommendation = requireNotNull(viewModel.uiState.value.flightOpportunityResult?.droneRecommendation)
        assertEquals(1, recommendation.usableCount)
        assertEquals("air", recommendation.lightestCompatible?.droneId)
        assertEquals("air", recommendation.bestOperationalMargin?.droneId)
        assertEquals(DroneWindowCompatibility.NOT_COMPATIBLE, recommendation.compared.first { it.droneId == "mini" }.compatibility)
        scope.cancel()
    }

    @Test
    fun noCompatibleDroneKeepsMainReasonVisible() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = fleetRecommendationViewModel(
            scope = scope,
            windKmh = 25.0,
            gustKmh = 40.0,
            drones = listOf(
                LocalDrone(id = "mini", manufacturer = "DJI", model = "Mini 3 Pro", weight = 249.0, manualMaxWindResistanceMs = 6.0, isSelected = true),
                LocalDrone(id = "neo", manufacturer = "DJI", model = "Neo", weight = 135.0, manualMaxWindResistanceMs = 5.0)
            )
        )

        val recommendation = requireNotNull(viewModel.uiState.value.flightOpportunityResult?.droneRecommendation)
        assertEquals(0, recommendation.usableCount)
        assertEquals(0, recommendation.cautionCount)
        assertNull(recommendation.lightestCompatible)
        assertNull(recommendation.bestOperationalMargin)
        assertTrue(recommendation.compared.all { it.compatibility == DroneWindowCompatibility.NOT_COMPATIBLE })
        assertTrue(recommendation.compared.any { !it.compatibilityReason.isNullOrBlank() })
        scope.cancel()
    }

    @Test
    fun compatibleDroneWithoutWeightIsNotAssumedLightest() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = fleetRecommendationViewModel(
            scope = scope,
            windKmh = 8.0,
            gustKmh = 12.0,
            drones = listOf(
                LocalDrone(id = "unknown-weight", manufacturer = "DJI", model = "Mini 3 Pro", weight = null, manualMaxWindResistanceMs = 6.0, isSelected = true),
                LocalDrone(id = "air", manufacturer = "DJI", model = "AIR 3S", weight = 724.0, manualMaxWindResistanceMs = 12.0)
            )
        )

        val recommendation = requireNotNull(viewModel.uiState.value.flightOpportunityResult?.droneRecommendation)
        assertEquals(2, recommendation.usableCount)
        assertEquals("air", recommendation.lightestCompatible?.droneId)
        assertEquals(DroneWindowCompatibility.USABLE, recommendation.compared.first { it.droneId == "unknown-weight" }.compatibility)
        scope.cancel()
    }

    @Test
    fun lowerScoreDoesNotMakeDroneNotRecommendedWhenUsable() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = fleetRecommendationViewModel(
            scope = scope,
            windKmh = 9.0,
            gustKmh = 13.0,
            drones = listOf(
                LocalDrone(id = "mini", manufacturer = "DJI", model = "Mini 3 Pro", weight = 249.0, manualMaxWindResistanceMs = 6.0, isSelected = true),
                LocalDrone(id = "air", manufacturer = "DJI", model = "AIR 3S", weight = 724.0, manualMaxWindResistanceMs = 12.0)
            )
        )

        val recommendation = requireNotNull(viewModel.uiState.value.flightOpportunityResult?.droneRecommendation)
        val mini = recommendation.compared.first { it.droneId == "mini" }
        val air = recommendation.compared.first { it.droneId == "air" }
        assertEquals(DroneWindowCompatibility.USABLE, mini.compatibility)
        assertEquals(DroneWindowCompatibility.USABLE, air.compatibility)
        assertTrue((mini.droneScore ?: 0) < (air.droneScore ?: 0))
        assertEquals("mini", recommendation.lightestCompatible?.droneId)
        assertEquals("air", recommendation.bestOperationalMargin?.droneId)
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

    @Test
    fun enablingTrafficAwarenessStartsImmediateFetchForSelectedPoint() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val traffic = FakeTrafficAwarenessClient()
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 10_000
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { traffic.calls == 1 && !viewModel.uiState.value.trafficAwareness.loading }

        assertTrue(viewModel.uiState.value.trafficAwareness.enabled)
        assertEquals(MapPoint(41.9, 12.5), traffic.lastPoint)
        assertEquals(20.0, traffic.lastRadiusKm ?: -1.0, 0.0)
        assertEquals(1, viewModel.uiState.value.trafficAwareness.response?.traffic?.count)
        scope.cancel()
    }

    @Test
    fun lockedTrafficAwarenessKeepsOriginalCenterWhenSelectingAnotherMapPoint() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val traffic = FakeTrafficAwarenessClient()
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 10_000
        )

        val originalPoint = MapPoint(41.9, 12.5)
        val exploredPoint = MapPoint(42.1, 12.7)
        viewModel.onMapTapped(selection(originalPoint.lat, originalPoint.lon))
        waitUntil { viewModel.uiState.value.selectedPoint == originalPoint }
        viewModel.enableTrafficAwareness()
        waitUntil { traffic.calls == 1 && !viewModel.uiState.value.trafficAwareness.loading }
        viewModel.onTrafficAwarenessPositionLockedChanged(true)

        viewModel.onMapTapped(selection(exploredPoint.lat, exploredPoint.lon))
        delay(80)

        assertTrue(viewModel.uiState.value.trafficAwareness.enabled)
        assertEquals(exploredPoint, viewModel.uiState.value.selectedPoint)
        assertEquals(originalPoint, viewModel.uiState.value.trafficAwarenessCenter)
        assertEquals(originalPoint, traffic.lastPoint)
        assertEquals(1, traffic.calls)
        scope.cancel()
    }

    @Test
    fun unlockedTrafficAwarenessMovesCenterWhenSelectingAnotherMapPoint() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val traffic = FakeTrafficAwarenessClient()
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 10_000
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { traffic.calls == 1 && !viewModel.uiState.value.trafficAwareness.loading }

        val exploredPoint = MapPoint(42.1, 12.7)
        viewModel.onMapTapped(selection(exploredPoint.lat, exploredPoint.lon))
        waitUntil { traffic.calls == 2 && !viewModel.uiState.value.trafficAwareness.loading }

        assertEquals(exploredPoint, viewModel.uiState.value.trafficAwarenessCenter)
        assertEquals(exploredPoint, traffic.lastPoint)
        scope.cancel()
    }

    @Test
    fun enablingTrafficAwarenessWithoutSelectedPointDoesNotRequestInvalidCoordinates() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val traffic = FakeTrafficAwarenessClient()
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 20
        )

        viewModel.enableTrafficAwareness()
        delay(80)

        assertEquals(0, traffic.calls)
        assertFalse(viewModel.uiState.value.trafficAwareness.enabled)
        assertEquals("Seleziona un punto sulla mappa", viewModel.uiState.value.trafficAwareness.error)
        assertEquals("Seleziona un punto sulla mappa", viewModel.uiState.value.mapStatusMessage)
        scope.cancel()
    }

    @Test
    fun enablingTrafficAwarenessWithoutSelectedPointUsesMapCenterWhenAvailable() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val traffic = FakeTrafficAwarenessClient()
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 10_000
        )

        viewModel.onCameraIdle(
            CameraBounds(
                zoom = 12.0,
                north = 42.0,
                south = 41.8,
                east = 12.6,
                west = 12.4
            )
        )
        viewModel.enableTrafficAwareness()
        waitUntil { traffic.calls == 1 && !viewModel.uiState.value.trafficAwareness.loading }

        assertTrue(viewModel.uiState.value.trafficAwareness.enabled)
        assertEquals(MapPoint(41.9, 12.5), viewModel.uiState.value.selectedPoint)
        assertEquals(MapPoint(41.9, 12.5), traffic.lastPoint)
        scope.cancel()
    }

    @Test
    fun trafficAwarenessPollsPeriodicallyWithoutFiveSecondSleep() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val traffic = FakeTrafficAwarenessClient()
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 20
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { traffic.calls >= 2 }

        assertTrue(viewModel.uiState.value.trafficAwareness.enabled)
        assertEquals(MapPoint(41.9, 12.5), traffic.lastPoint)
        scope.cancel()
    }

    @Test
    fun disablingTrafficAwarenessCancelsPolling() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val traffic = FakeTrafficAwarenessClient()
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 20
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { traffic.calls == 1 }
        viewModel.disableTrafficAwareness()
        val callsAfterDisable = traffic.calls
        delay(80)

        assertFalse(viewModel.uiState.value.trafficAwareness.enabled)
        assertEquals(callsAfterDisable, traffic.calls)
        scope.cancel()
    }

    @Test
    fun trafficAwarenessPollingDoesNotOverlapSlowFetches() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val traffic = FakeTrafficAwarenessClient(delayMillis = 80)
            val viewModel = viewModel(
                scope = scope,
                traffic = traffic,
                preferences = InMemoryMapPreferences(),
                trafficPollingIntervalMillis = 10
            )

            viewModel.onMapTapped(selection(41.9, 12.5))
            waitUntil { viewModel.uiState.value.selectedPoint != null }
            viewModel.enableTrafficAwareness()
            delay(40)

            assertEquals(1, traffic.calls)
            assertEquals(1, traffic.maxConcurrentCalls)
            waitUntil { traffic.calls >= 2 }
            assertEquals(1, traffic.maxConcurrentCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun trafficAwarenessTemporaryErrorKeepsLastGoodSnapshotAndRetries() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val traffic = FakeTrafficAwarenessClient(
            results = ArrayDeque(
                listOf(
                    Result.success(trafficResponse(count = 2)),
                    Result.failure(IllegalStateException("temporary")),
                    Result.success(trafficResponse(count = 3))
                )
            )
        )
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 20
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { traffic.calls == 1 && viewModel.uiState.value.trafficAwareness.response?.traffic?.count == 2 }
        waitUntil { traffic.calls == 2 && viewModel.uiState.value.trafficAwareness.error != null }

        assertTrue(viewModel.uiState.value.trafficAwareness.enabled)
        assertEquals(2, viewModel.uiState.value.trafficAwareness.response?.traffic?.count)

        waitUntil { traffic.calls >= 3 && viewModel.uiState.value.trafficAwareness.response?.traffic?.count == 3 }
        assertNull(viewModel.uiState.value.trafficAwareness.error)
        scope.cancel()
    }

    @Test
    fun trafficAwarenessKeepsAirSenseDronesBetweenSparseBeacons() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val sparseDrone = trafficTarget(
            id = "airsense:drone-7",
            lat = 41.901,
            lon = 12.501,
            callsign = "DSC-DRONE-7",
            provider = "AirSense",
            source = "AirSense",
            objectType = "drone",
            ageSec = 5.0
        )
        val disappearingAircraft = trafficTarget(
            id = "icao:old",
            lat = 41.902,
            lon = 12.502,
            callsign = "OLD",
            provider = "opensky",
            source = "OpenSky"
        )
        val freshAircraft = trafficTarget(
            id = "icao:fresh",
            lat = 41.903,
            lon = 12.503,
            callsign = "FRESH",
            provider = "opensky",
            source = "OpenSky"
        )
        val traffic = FakeTrafficAwarenessClient(
            results = ArrayDeque(
                listOf(
                    Result.success(trafficResponse(targets = listOf(sparseDrone, disappearingAircraft))),
                    Result.success(trafficResponse(targets = listOf(freshAircraft))),
                    Result.success(trafficResponse(targets = listOf(freshAircraft)))
                )
            )
        )
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 20
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { traffic.calls >= 2 && viewModel.uiState.value.trafficAwareness.response?.traffic?.targets?.any { it.id == "icao:fresh" } == true }

        val visibleIds = viewModel.uiState.value.trafficAwareness.response?.traffic?.targets.orEmpty().map { it.id }
        assertTrue(visibleIds.contains("airsense:drone-7"))
        assertTrue(visibleIds.contains("icao:fresh"))
        assertFalse(visibleIds.contains("icao:old"))
        assertEquals(2, viewModel.uiState.value.trafficAwareness.response?.traffic?.count)
        scope.cancel()
    }

    @Test
    fun trafficAwarenessDropsPersistedAirSenseDronesAfterPersistenceWindow() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val staleDrone = trafficTarget(
            id = "airsense:stale-drone",
            lat = 41.901,
            lon = 12.501,
            callsign = "DSC-STALE",
            provider = "AirSense",
            source = "AirSense",
            objectType = "drone",
            ageSec = 240.0
        )
        val freshAircraft = trafficTarget(
            id = "icao:fresh",
            lat = 41.903,
            lon = 12.503,
            callsign = "FRESH",
            provider = "opensky",
            source = "OpenSky"
        )
        val traffic = FakeTrafficAwarenessClient(
            results = ArrayDeque(
                listOf(
                    Result.success(trafficResponse(targets = listOf(staleDrone))),
                    Result.success(trafficResponse(targets = listOf(freshAircraft))),
                    Result.success(trafficResponse(targets = listOf(freshAircraft)))
                )
            )
        )
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 20
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { traffic.calls >= 2 && viewModel.uiState.value.trafficAwareness.response?.traffic?.targets?.any { it.id == "icao:fresh" } == true }

        val visibleIds = viewModel.uiState.value.trafficAwareness.response?.traffic?.targets.orEmpty().map { it.id }
        assertFalse(visibleIds.contains("airsense:stale-drone"))
        assertEquals(listOf("icao:fresh"), visibleIds)
        scope.cancel()
    }

    @Test
    fun closingZoneSheetKeepsTrafficAwarenessEnabledAndPolling() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val traffic = FakeTrafficAwarenessClient()
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 20
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { traffic.calls >= 1 }
        viewModel.onZoneSheetDismissed()
        waitUntil { traffic.calls >= 2 }

        assertFalse(viewModel.uiState.value.isZoneSheetVisible)
        assertTrue(viewModel.uiState.value.trafficAwareness.enabled)
        assertEquals(MapPoint(41.9, 12.5), viewModel.uiState.value.selectedPoint)
        assertEquals(MapPoint(41.9, 12.5), traffic.lastPoint)
        scope.cancel()
    }

    @Test
    fun closingZoneSheetKeepsSelectedPointAvailableForWeatherWhenTrafficIsOff() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(
            scope = scope,
            preferences = InMemoryMapPreferences()
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.onZoneSheetDismissed()

        assertFalse(viewModel.uiState.value.isZoneSheetVisible)
        assertFalse(viewModel.uiState.value.trafficAwareness.enabled)
        assertEquals(MapPoint(41.9, 12.5), viewModel.uiState.value.selectedPoint)
        scope.cancel()
    }

    @Test
    fun changingSelectedPointWhileTrafficAwarenessIsOnFetchesNewPointAndClearsOldSnapshot() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val traffic = FakeTrafficAwarenessClient(
            results = ArrayDeque(
                listOf(
                    Result.success(trafficResponse(count = 2, center = MapPoint(41.9, 12.5))),
                    Result.success(trafficResponse(count = 3, center = MapPoint(42.0, 12.6)))
                )
            )
        )
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 10_000
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { traffic.calls == 1 && viewModel.uiState.value.trafficAwareness.response?.traffic?.count == 2 }

        viewModel.onMapTapped(selection(42.0, 12.6))
        assertTrue(viewModel.uiState.value.trafficAwareness.enabled)
        assertNull(viewModel.uiState.value.trafficAwareness.response)
        waitUntil { traffic.calls == 2 && traffic.lastPoint == MapPoint(42.0, 12.6) }
        waitUntil { viewModel.uiState.value.trafficAwareness.response?.traffic?.count == 3 }

        assertEquals(MapPoint(42.0, 12.6), viewModel.uiState.value.selectedPoint)
        assertEquals(MapPoint(42.0, 12.6), traffic.lastPoint)
        scope.cancel()
    }

    @Test
    fun selectedTrafficTargetIsClosedWhenTrafficAwarenessIsDisabled() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val traffic = FakeTrafficAwarenessClient(
            results = ArrayDeque(listOf(Result.success(trafficResponse(count = 1))))
        )
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 100
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { viewModel.uiState.value.trafficAwareness.response?.traffic?.targets?.isNotEmpty() == true }
        viewModel.onTrafficTargetSelected("traffic:1")
        waitUntil { viewModel.uiState.value.selectedTrafficTarget != null }
        viewModel.disableTrafficAwareness()
        val callsAfterDisable = traffic.calls
        delay(80)

        assertFalse(viewModel.uiState.value.trafficAwareness.enabled)
        assertNull(viewModel.uiState.value.trafficAwareness.response)
        assertNull(viewModel.uiState.value.selectedTrafficTarget)
        assertEquals(callsAfterDisable, traffic.calls)
        scope.cancel()
    }

    @Test
    fun trafficAttentionBannerTargetUsesExistingTargetSelectionFlow() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val traffic = FakeTrafficAwarenessClient(
            results = ArrayDeque(
                listOf(
                    Result.success(
                        trafficResponse(
                            center = MapPoint(41.9, 12.5),
                            targets = listOf(
                                trafficTarget(
                                    id = "traffic:attention",
                                    lat = 41.9 + metersToLatDegreesForTest(2_000.0),
                                    lon = 12.5,
                                    callsign = "RYR9ZQ",
                                    speedMps = 40.0,
                                    trackDeg = 180.0,
                                    ageSec = 2.0
                                )
                            )
                        )
                    )
                )
            )
        )
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 10_000
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { viewModel.uiState.value.trafficAssessments["traffic:attention"] != null }
        viewModel.onTrafficTargetSelected("traffic:attention")

        assertEquals("traffic:attention", viewModel.uiState.value.selectedTrafficTarget?.id)
        assertFalse(viewModel.uiState.value.isZoneSheetVisible)
        scope.cancel()
    }

    @Test
    fun trafficAssessmentsAreComputedForLatestSnapshotAndSelectedPoint() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val traffic = FakeTrafficAwarenessClient(
            results = ArrayDeque(
                listOf(
                    Result.success(
                        trafficResponse(
                            center = MapPoint(41.9, 12.5),
                            targets = listOf(
                                trafficTarget(
                                    id = "traffic:attention",
                                    lat = 41.9 + metersToLatDegreesForTest(2_000.0),
                                    lon = 12.5,
                                    callsign = "T1",
                                    speedMps = 40.0,
                                    trackDeg = 180.0,
                                    ageSec = 2.0
                                )
                            )
                        )
                    )
                )
            )
        )
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 10_000
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { viewModel.uiState.value.trafficAssessments["traffic:attention"] != null }

        assertEquals(
            it.droneskycheck.app.data.traffic.TrafficRelevance.ATTENTION,
            viewModel.uiState.value.trafficAssessments["traffic:attention"]?.relevance
        )
        scope.cancel()
    }

    @Test
    fun highAltitudeAircraftIsHiddenUnlessUserEnablesIt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val highAircraft = trafficTarget(
            id = "traffic:high-aircraft",
            lat = 41.9 + metersToLatDegreesForTest(2_000.0),
            lon = 12.5,
            callsign = "HIGH",
            speedMps = 40.0,
            trackDeg = 180.0,
            ageSec = 2.0,
            aglM = 1_200.0
        )
        val traffic = FakeTrafficAwarenessClient(
            results = ArrayDeque(
                listOf(
                    Result.success(trafficResponse(center = MapPoint(41.9, 12.5), targets = listOf(highAircraft))),
                    Result.success(trafficResponse(center = MapPoint(41.9, 12.5), targets = listOf(highAircraft)))
                )
            )
        )
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 20
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { viewModel.uiState.value.trafficAwareness.response != null }

        assertTrue(viewModel.uiState.value.trafficAwareness.response?.traffic?.targets.orEmpty().isEmpty())
        assertNull(viewModel.uiState.value.trafficAssessments["traffic:high-aircraft"])
        assertNull(viewModel.uiState.value.trafficVisualAssessments["traffic:high-aircraft"])

        viewModel.onHighAltitudeTrafficAlertEnabledChanged(true)
        waitUntil {
            viewModel.uiState.value.trafficAssessments["traffic:high-aircraft"]?.relevance ==
                it.droneskycheck.app.data.traffic.TrafficRelevance.ATTENTION
        }

        assertTrue(viewModel.uiState.value.highAltitudeTrafficAlertEnabled)
        assertEquals(
            listOf("traffic:high-aircraft"),
            viewModel.uiState.value.trafficAwareness.response?.traffic?.targets.orEmpty().map { it.id }
        )
        assertEquals(
            it.droneskycheck.app.data.traffic.TrafficRelevance.ATTENTION,
            viewModel.uiState.value.trafficVisualAssessments["traffic:high-aircraft"]?.relevance
        )
        scope.cancel()
    }

    @Test
    fun highAltitudeTrafficFilterKeepsLowAndUnknownAglTargetsVisible() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val lowAircraft = trafficTarget(
            id = "traffic:low-aircraft",
            lat = 41.9 + metersToLatDegreesForTest(1_900.0),
            lon = 12.5,
            callsign = "LOW",
            speedMps = 40.0,
            trackDeg = 180.0,
            ageSec = 2.0,
            aglM = 999.0
        )
        val unknownAltitudeAircraft = trafficTarget(
            id = "traffic:unknown-aircraft",
            lat = 41.9 + metersToLatDegreesForTest(2_100.0),
            lon = 12.5,
            callsign = "UNK",
            speedMps = 40.0,
            trackDeg = 180.0,
            ageSec = 2.0,
            aglM = null
        )
        val viewModel = viewModel(
            scope = scope,
            traffic = FakeTrafficAwarenessClient(
                results = ArrayDeque(
                    listOf(
                        Result.success(
                            trafficResponse(
                                center = MapPoint(41.9, 12.5),
                                targets = listOf(lowAircraft, unknownAltitudeAircraft)
                            )
                        )
                    )
                )
            ),
            preferences = InMemoryMapPreferences(initialHighAltitudeTrafficAlertEnabled = false),
            trafficPollingIntervalMillis = 10_000
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { viewModel.uiState.value.trafficAwareness.response?.traffic?.targets?.size == 2 }

        assertEquals(
            listOf("traffic:low-aircraft", "traffic:unknown-aircraft"),
            viewModel.uiState.value.trafficAwareness.response?.traffic?.targets.orEmpty().map { it.id }
        )
        assertTrue(viewModel.uiState.value.trafficVisualAssessments.keys.containsAll(listOf("traffic:low-aircraft", "traffic:unknown-aircraft")))
        scope.cancel()
    }

    @Test
    fun trafficAlertPreferencesLoadAndPersistWithoutChangingTrafficAwareness() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val preferences = InMemoryMapPreferences(
            initialTrafficAlertSoundEnabled = false,
            initialTrafficAlertVibrationEnabled = true,
            initialHighAltitudeTrafficAlertEnabled = false
        )
        val viewModel = viewModel(
            scope = scope,
            preferences = preferences,
            trafficPollingIntervalMillis = 10_000
        )

        assertFalse(viewModel.uiState.value.trafficAlertSoundEnabled)
        assertTrue(viewModel.uiState.value.trafficAlertVibrationEnabled)
        assertFalse(viewModel.uiState.value.highAltitudeTrafficAlertEnabled)

        viewModel.onTrafficAlertSettingsRequested()
        viewModel.onTrafficAlertSoundEnabledChanged(true)
        viewModel.onTrafficAlertVibrationEnabledChanged(false)
        viewModel.onHighAltitudeTrafficAlertEnabledChanged(true)
        viewModel.onTrafficAlertSettingsDismissed()

        assertTrue(preferences.isTrafficAlertSoundEnabled())
        assertFalse(preferences.isTrafficAlertVibrationEnabled())
        assertTrue(preferences.isHighAltitudeTrafficAlertEnabled())
        assertTrue(viewModel.uiState.value.trafficAlertSoundEnabled)
        assertFalse(viewModel.uiState.value.trafficAlertVibrationEnabled)
        assertTrue(viewModel.uiState.value.highAltitudeTrafficAlertEnabled)
        assertFalse(viewModel.uiState.value.isTrafficAlertSettingsSheetVisible)
        assertFalse(viewModel.uiState.value.trafficAwareness.enabled)
        scope.cancel()
    }

    @Test
    fun enhancedZoneOutlinesAndMapDarkeningAreMutuallyExclusive() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val preferences = InMemoryMapPreferences(initialMapDarkeningEnabled = true)
        val viewModel = viewModel(
            scope = scope,
            preferences = preferences,
            trafficPollingIntervalMillis = 10_000
        )

        assertTrue(viewModel.uiState.value.isMapDarkeningEnabled)
        assertFalse(viewModel.uiState.value.isEnhancedZoneOutlinesEnabled)

        viewModel.onEnhancedZoneOutlinesEnabledChanged(true)

        assertTrue(preferences.isEnhancedZoneOutlinesEnabled())
        assertFalse(preferences.isMapDarkeningEnabled())
        assertTrue(viewModel.uiState.value.isEnhancedZoneOutlinesEnabled)
        assertFalse(viewModel.uiState.value.isMapDarkeningEnabled)

        viewModel.onMapDarkeningEnabledChanged(true)

        assertTrue(preferences.isMapDarkeningEnabled())
        assertFalse(preferences.isEnhancedZoneOutlinesEnabled())
        assertTrue(viewModel.uiState.value.isMapDarkeningEnabled)
        assertFalse(viewModel.uiState.value.isEnhancedZoneOutlinesEnabled)

        viewModel.onMapDarkeningEnabledChanged(false)

        assertFalse(preferences.isMapDarkeningEnabled())
        assertFalse(preferences.isEnhancedZoneOutlinesEnabled())
        assertFalse(viewModel.uiState.value.isMapDarkeningEnabled)
        assertFalse(viewModel.uiState.value.isEnhancedZoneOutlinesEnabled)
        scope.cancel()
    }

    @Test
    fun trafficAlertEventIsEmittedForActiveCollectorsOnlyOnAttentionEntry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(
            scope = scope,
            traffic = FakeTrafficAwarenessClient(
                results = ArrayDeque(
                    listOf(
                        Result.success(
                            trafficResponse(
                                center = MapPoint(41.9, 12.5),
                                targets = listOf(
                                    trafficTarget(
                                        id = "traffic:attention",
                                        lat = 41.9 + metersToLatDegreesForTest(2_000.0),
                                        lon = 12.5,
                                        callsign = "T1",
                                        speedMps = 40.0,
                                        trackDeg = 180.0,
                                        ageSec = 2.0
                                    )
                                )
                            )
                        )
                    )
                )
            ),
            preferences = InMemoryMapPreferences(
                initialTrafficAlertSoundEnabled = false,
                initialTrafficAlertVibrationEnabled = false
            ),
            trafficPollingIntervalMillis = 10_000
        )
        var events = 0
        val collector = launch {
            viewModel.trafficAlertEvents.collect {
                events += 1
            }
        }
        delay(50)

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { events == 1 }

        assertEquals(1, events)
        assertFalse(viewModel.uiState.value.trafficAlertSoundEnabled)
        assertFalse(viewModel.uiState.value.trafficAlertVibrationEnabled)
        assertEquals(
            it.droneskycheck.app.data.traffic.TrafficRelevance.ATTENTION,
            viewModel.uiState.value.trafficAssessments["traffic:attention"]?.relevance
        )
        collector.cancel()
        var replayedEvents = 0
        val lateCollector = launch {
            viewModel.trafficAlertEvents.collect {
                replayedEvents += 1
            }
        }
        viewModel.onTrafficAlertSettingsRequested()
        delay(50)

        assertEquals(0, replayedEvents)
        lateCollector.cancel()
        scope.cancel()
    }

    @Test
    fun viewModelClearedCancelsTrafficAwarenessPolling() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val traffic = FakeTrafficAwarenessClient()
        val viewModel = viewModel(
            scope = scope,
            traffic = traffic,
            preferences = InMemoryMapPreferences(),
            trafficPollingIntervalMillis = 20
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.enableTrafficAwareness()
        waitUntil { traffic.calls >= 1 }
        MapViewModel::class.java.getDeclaredMethod("onCleared")
            .apply { isAccessible = true }
            .invoke(viewModel)
        val callsAfterClear = traffic.calls
        delay(80)

        assertEquals(callsAfterClear, traffic.calls)
        scope.cancel()
    }

    @Test
    fun helpTourSelectsMapCenterWhenOpeningZoneControlWithoutSelectedPoint() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(
            scope = scope,
            preferences = InMemoryMapPreferences(),
            helpRepository = InMemoryHelpManifestClient(helpTourManifest())
        )

        viewModel.onCameraIdle(
            CameraBounds(
                zoom = 12.0,
                north = 44.0,
                south = 42.0,
                east = 11.0,
                west = 9.0
            )
        )
        viewModel.requestHelpOnboardingReplay()
        waitUntil { viewModel.uiState.value.activeHelpOnboarding != null }
        viewModel.onHelpOnboardingNext()
        waitUntil { viewModel.uiState.value.selectedPoint != null }

        assertEquals(MapPoint(43.0, 10.0), viewModel.uiState.value.selectedPoint)
        assertTrue(viewModel.uiState.value.isZoneSheetVisible)
        assertEquals(1, viewModel.uiState.value.activeHelpOnboarding?.currentIndex)
        scope.cancel()
    }

    @Test
    fun helpTourClosesZoneControlBeforeOpeningLayerSelectorAfterWeather() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(
            scope = scope,
            preferences = InMemoryMapPreferences(),
            helpRepository = InMemoryHelpManifestClient(zoneWeatherLayersTourManifest())
        )

        viewModel.onCameraIdle(
            CameraBounds(
                zoom = 12.0,
                north = 44.0,
                south = 42.0,
                east = 11.0,
                west = 9.0
            )
        )
        viewModel.requestHelpOnboardingReplay()
        waitUntil { viewModel.uiState.value.activeHelpOnboarding != null }
        viewModel.onHelpOnboardingNext()
        waitUntil { viewModel.uiState.value.isZoneSheetVisible }
        viewModel.onHelpOnboardingNext()
        waitUntil { viewModel.uiState.value.isOperationalReportExpanded }
        viewModel.onHelpOnboardingNext()
        waitUntil { viewModel.uiState.value.isLayerSheetVisible }

        assertEquals(3, viewModel.uiState.value.activeHelpOnboarding?.currentIndex)
        assertFalse(viewModel.uiState.value.isZoneSheetVisible)
        assertTrue(viewModel.uiState.value.isLayerSheetVisible)
        scope.cancel()
    }

    private fun viewModel(
        scope: CoroutineScope,
        legal: FakeLegalTimelineClient = FakeLegalTimelineClient(),
        weather: FakeWeatherClient = FakeWeatherClient(),
        metar: NearbyMetarClient = FakeNearbyMetarClient(),
        traffic: TrafficAwarenessClient = FakeTrafficAwarenessClient(),
        preferences: InMemoryMapPreferences,
        pilotStore: LocalPilotStore = FakePilotStore(),
        catalog: DroneTechnicalCatalogClient = InMemoryDroneTechnicalCatalogClient(),
        helpRepository: HelpManifestClient = InMemoryHelpManifestClient(),
        trafficPollingIntervalMillis: Long = 5_000L,
        loadHelpOnInit: Boolean = false
    ): MapViewModel =
        MapViewModel(
            zoneCheckRepository = FakeZoneCheckClient(),
            legalTimelineRepository = legal,
            weatherForecastRepository = weather,
            nearbyMetarRepository = metar,
            trafficAwarenessRepository = traffic,
            mapPreferences = preferences,
            helpRepository = helpRepository,
            helpPreferences = InMemoryHelpPreferences(),
            localPilotStore = pilotStore,
            droneTechnicalCatalog = catalog,
            clock = clock,
            timelineZoneId = ZoneId.of("Europe/Rome"),
            trafficAwarenessPollingIntervalMillis = trafficPollingIntervalMillis,
            loadHelpOnInit = loadHelpOnInit,
            externalScope = scope
        )

    private suspend fun fleetRecommendationViewModel(
        scope: CoroutineScope,
        windKmh: Double,
        gustKmh: Double,
        drones: List<LocalDrone>
    ): MapViewModel {
        val viewModel = viewModel(
            scope = scope,
            weather = FakeWeatherClient(forecast = weatherForecast(windKmh = windKmh, gustKmh = gustKmh)),
            preferences = InMemoryMapPreferences(),
            pilotStore = FakePilotStore(drones)
        )
        waitUntil { viewModel.uiState.value.selectedDrone != null }
        viewModel.onMapTapped(selection(41.9, 12.5))
        waitUntil { viewModel.uiState.value.selectedPoint != null }
        viewModel.onOperationalContextRequested()
        waitUntil { viewModel.uiState.value.flightOpportunityResult?.droneRecommendation != null }
        return viewModel
    }

    private fun helpTourManifest(): HelpManifest =
        HelpManifest(
            schemaVersion = 1,
            contentVersion = 1,
            updatedAt = "2026-08-13",
            onboardingVersion = 1,
            onboardingSteps = listOf(
                HelpOnboardingStep(
                    id = "map",
                    target = HelpTourTarget.MAP,
                    title = "Mappa",
                    text = "Testo",
                    order = 1
                ),
                HelpOnboardingStep(
                    id = "zone",
                    target = HelpTourTarget.SELECTED_POINT_PANEL,
                    action = HelpTourAction.OPEN_SELECTED_POINT_DETAILS,
                    title = "Zona",
                    text = "Testo",
                    order = 2
                )
            ),
            topics = emptyList()
        )

    private fun zoneWeatherLayersTourManifest(): HelpManifest =
        HelpManifest(
            schemaVersion = 1,
            contentVersion = 1,
            updatedAt = "2026-08-13",
            onboardingVersion = 1,
            onboardingSteps = listOf(
                HelpOnboardingStep(
                    id = "map",
                    target = HelpTourTarget.MAP,
                    title = "Mappa",
                    text = "Testo",
                    order = 1
                ),
                HelpOnboardingStep(
                    id = "zone",
                    target = HelpTourTarget.SELECTED_POINT_PANEL,
                    action = HelpTourAction.OPEN_SELECTED_POINT_DETAILS,
                    title = "Zona",
                    text = "Testo",
                    order = 2
                ),
                HelpOnboardingStep(
                    id = "weather",
                    target = HelpTourTarget.FLIGHT_OPPORTUNITY_CARD,
                    action = HelpTourAction.OPEN_WEATHER,
                    title = "Meteo",
                    text = "Testo",
                    order = 3
                ),
                HelpOnboardingStep(
                    id = "layers",
                    target = HelpTourTarget.ZONES_BUTTON,
                    action = HelpTourAction.OPEN_ZONES,
                    title = "Zone",
                    text = "Testo",
                    order = 4
                )
            ),
            topics = emptyList()
        )

    private fun selection(lat: Double, lon: Double): MapTapSelection =
        MapTapSelection(
            point = MapPoint(lat, lon),
            zone = null
        )

    private suspend fun waitUntil(predicate: () -> Boolean) {
        withTimeout(10_000) {
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
    private val failure: Throwable? = null,
    private val state: LegalTimelineState = LegalTimelineState.AVAILABLE
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
                    state = state,
                    rawState = state.name,
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

private class FakeNearbyMetarClient(
    private val metar: NearbyMetar? = null
) : NearbyMetarClient {
    var calls: Int = 0
        private set
    var lastPoint: MapPoint? = null
        private set

    override suspend fun getNearbyMetar(latitude: Double, longitude: Double, radiusKm: Double): Result<NearbyMetar?> {
        calls += 1
        lastPoint = MapPoint(latitude, longitude)
        return Result.success(metar)
    }
}

private class FakeTrafficAwarenessClient(
    private val delayMillis: Long = 0L,
    private val results: ArrayDeque<Result<TrafficAwarenessResponse>> = ArrayDeque()
) : TrafficAwarenessClient {
    var calls: Int = 0
        private set
    var activeCalls: Int = 0
        private set
    var maxConcurrentCalls: Int = 0
        private set
    var lastPoint: MapPoint? = null
        private set
    var lastRadiusKm: Double? = null
        private set

    override suspend fun getTrafficAwareness(
        lat: Double,
        lon: Double,
        radiusKm: Double
    ): Result<TrafficAwarenessResponse> {
        calls += 1
        activeCalls += 1
        maxConcurrentCalls = maxOf(maxConcurrentCalls, activeCalls)
        lastPoint = MapPoint(lat, lon)
        lastRadiusKm = radiusKm
        delay(delayMillis)
        activeCalls -= 1
        return if (results.isNotEmpty()) {
            results.removeFirst()
        } else {
            Result.success(trafficResponse(count = 1))
        }
    }
}

private fun trafficResponse(
    count: Int = 1,
    center: MapPoint = MapPoint(41.9, 12.5),
    targets: List<TrafficTarget>? = null
): TrafficAwarenessResponse =
    TrafficAwarenessResponse(
        ok = true,
        generatedAt = 1_800_000_000_000,
        servedAt = 1_800_000_000_100,
        center = TrafficCenter(center.lat, center.lon),
        radiusKm = 20.0,
        traffic = TrafficSummary(
            count = targets?.size ?: count,
            targets = targets ?: (1..count).map { index ->
                trafficTarget(
                    id = "traffic:$index",
                    lat = center.lat + index * 0.001,
                    lon = center.lon + index * 0.001,
                    callsign = "T$index"
                )
            }
        ),
        providers = mapOf(
            "opensky" to TrafficProviderStatus(
                status = if (count > 0) "ok" else "zero_results",
                count = count,
                errorCode = null
            )
        ),
        cache = TrafficCacheInfo(
            hit = false,
            ageMs = 0,
            ttlMs = 5_000,
            singleFlight = false
        )
    )

private fun trafficTarget(
    id: String,
    lat: Double,
    lon: Double,
    callsign: String,
    speedMps: Double? = null,
    trackDeg: Double? = null,
    headingDeg: Double? = null,
    ageSec: Double? = null,
    aglM: Double? = null,
    provider: String = "opensky",
    source: String = "OpenSky",
    objectType: String? = null
): TrafficTarget =
    TrafficTarget(
        id = id,
        identifiers = TrafficIdentifiers(
            icao24 = null,
            callsign = callsign,
            registration = null,
            sourceId = id
        ),
        position = TrafficPosition(lat = lat, lon = lon),
        altitude = TrafficAltitude(
            baroM = null,
            geoM = null,
            mslM = null,
            aglM = aglM,
            sourceM = null,
            sourceReference = null
        ),
        motion = TrafficMotion(
            groundSpeedMps = speedMps,
            verticalRateMps = null,
            trackDeg = trackDeg,
            headingDeg = headingDeg
        ),
        aircraft = TrafficAircraft(category = null, type = null),
        time = TrafficTime(timestamp = null, ageSec = ageSec),
        relative = TrafficRelative(distanceM = null, bearingDeg = null),
        provider = provider,
        source = source,
        quality = null,
        sources = listOf(TrafficSource(provider = provider, source = source)),
        provenance = null,
        objectType = objectType
    )

private fun metersToLatDegreesForTest(meters: Double): Double =
    meters / 111_320.0

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
    gustKmh: Double = 12.0,
    start: Instant? = null,
    hours: Int = 1
): WeatherForecast {
    val rome = ZoneId.of("Europe/Rome")
    val firstInstant = start ?: LocalDate.parse("2026-08-11").atTime(8, 0).atZone(rome).toInstant()
    return WeatherForecast(
        location = WeatherForecastLocation(null, null, null, "Europe/Rome", "CEST", 7200),
        timezone = rome,
        generatedAt = Instant.parse("2026-08-11T06:00:00Z"),
        providerFetchedAt = Instant.parse("2026-08-11T06:00:00Z"),
        hours = (0 until hours).map { index ->
            val instant = firstInstant.plusSeconds(index * 3600L)
            val local = instant.atZone(rome).toLocalDateTime()
            WeatherForecastHour(
                instant = instant,
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
        },
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
