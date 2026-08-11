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
import it.droneskycheck.app.data.Meta
import it.droneskycheck.app.data.Position
import it.droneskycheck.app.data.Verdict
import it.droneskycheck.app.data.ZoneCheckV3Client
import it.droneskycheck.app.data.ZoneCheckV3Response
import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.data.weather.WeatherForecastClient
import java.time.Clock
import java.time.Instant
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
        val weather = FakeWeatherClient()
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
        val weather = FakeWeatherClient()
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
        waitUntil { weather.calls == 1 }

        assertTrue(viewModel.uiState.value.isOperationalContextRequested)
        assertTrue(viewModel.uiState.value.isWeatherAnalysisEnabled)
        assertEquals(1, legal.calls)
        assertEquals(MapPoint(41.9, 12.5), legal.lastPoint)
        assertEquals(MapPoint(41.9, 12.5), weather.lastPoint)
        scope.cancel()
    }

    @Test
    fun newSelectionClearsPreviousOperationalContext() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = viewModel(
            scope = scope,
            weather = FakeWeatherClient(),
            preferences = InMemoryMapPreferences()
        )

        viewModel.onMapTapped(selection(41.9, 12.5))
        viewModel.onOperationalContextRequested()
        waitUntil { viewModel.uiState.value.weatherError != null }
        viewModel.onMapTapped(selection(42.0, 12.6))

        assertFalse(viewModel.uiState.value.isOperationalContextRequested)
        assertFalse(viewModel.uiState.value.isWeatherAnalysisEnabled)
        assertFalse(viewModel.uiState.value.isWeatherAnalysisLoading)
        assertNull(viewModel.uiState.value.weatherForecast)
        assertNull(viewModel.uiState.value.weatherAssessment)
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

    private fun viewModel(
        scope: CoroutineScope,
        legal: FakeLegalTimelineClient = FakeLegalTimelineClient(),
        weather: FakeWeatherClient = FakeWeatherClient(),
        preferences: InMemoryMapPreferences
    ): MapViewModel =
        MapViewModel(
            zoneCheckRepository = FakeZoneCheckClient(),
            legalTimelineRepository = legal,
            weatherForecastRepository = weather,
            mapPreferences = preferences,
            clock = clock,
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

    override suspend fun getLegalTimeline(
        lat: Double,
        lon: Double,
        from: Instant,
        to: Instant
    ): Result<LegalTimelineResponse> {
        calls += 1
        lastPoint = MapPoint(lat, lon)
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

private class FakeWeatherClient : WeatherForecastClient {
    var calls: Int = 0
        private set
    var lastPoint: MapPoint? = null
        private set

    override suspend fun getForecast(latitude: Double, longitude: Double): Result<WeatherForecast> {
        calls += 1
        lastPoint = MapPoint(latitude, longitude)
        return Result.failure(IllegalStateException("weather fixture"))
    }
}
