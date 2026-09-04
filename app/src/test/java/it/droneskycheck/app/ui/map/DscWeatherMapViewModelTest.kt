package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.Baseline
import it.droneskycheck.app.data.Meta
import it.droneskycheck.app.data.Position
import it.droneskycheck.app.data.Verdict
import it.droneskycheck.app.data.ZoneCheckV3Client
import it.droneskycheck.app.data.ZoneCheckV3Response
import it.droneskycheck.app.data.weatherAlerts.CriticalityLevel
import it.droneskycheck.app.data.weatherAlerts.VigilanceLevel
import it.droneskycheck.app.data.weatherAlerts.WeatherAlertLoadResult
import it.droneskycheck.app.data.weatherAlerts.WeatherAlertPoint
import it.droneskycheck.app.data.weatherAlerts.WeatherAlertResponse
import it.droneskycheck.app.data.weatherAlerts.WeatherAlertsClient
import it.droneskycheck.app.data.weatherAlerts.WeatherCriticality
import it.droneskycheck.app.data.weatherAlerts.WeatherCriticalityPeriod
import it.droneskycheck.app.data.weatherAlerts.WeatherRisk
import it.droneskycheck.app.data.weatherAlerts.WeatherStatus
import it.droneskycheck.app.data.weatherAlerts.WeatherVigilance
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DscWeatherMapViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun pollingRunsOnlyWhileResumedAndChecksImmediatelyOnReturn() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val weather = FakeDscWeatherClient()
        val viewModel = viewModel(scope, weather)
        viewModel.onCameraIdle(bounds(41.90, 12.50))

        viewModel.onDscWeatherSessionResumed()
        waitUntil { weather.statusCalls >= 1 && weather.alertCalls >= 1 }
        viewModel.onDscWeatherSessionPaused()
        val pausedStatusCalls = weather.statusCalls
        delay(80)
        assertEquals(pausedStatusCalls, weather.statusCalls)

        viewModel.onDscWeatherSessionResumed()
        waitUntil { weather.statusCalls > pausedStatusCalls }
        viewModel.onDscWeatherSessionPaused()
        scope.cancel()
    }

    @Test
    fun weatherFollowsCameraCenterEvenWhenASelectedPointExists() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val weather = FakeDscWeatherClient()
        val viewModel = viewModel(scope, weather)
        viewModel.onCameraIdle(bounds(41.90, 12.50))
        viewModel.onDscWeatherSessionResumed()
        waitUntil { weather.lastPoint == MapPoint(41.9, 12.5) }

        viewModel.onMapTapped(MapTapSelection(MapPoint(44.49, 11.34), null))
        waitUntil { weather.alertCalls >= 2 }
        assertEquals(MapPoint(41.9, 12.5), weather.lastPoint)

        viewModel.onCameraIdle(bounds(38.111, 15.650))
        waitUntil { weather.lastPoint == MapPoint(38.111, 15.65) }

        viewModel.onZoneSheetDismissed()
        delay(40)
        assertEquals(MapPoint(44.49, 11.34), viewModel.uiState.value.selectedPoint)
        assertEquals(MapPoint(38.111, 15.65), weather.lastPoint)
        viewModel.onDscWeatherSessionPaused()
        scope.cancel()
    }

    @Test
    fun olderResponseCannotOverwriteNewerPoint() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val weather = FakeDscWeatherClient(delaysByLatitude = mapOf(44.49 to 140L, 41.90 to 0L))
        val viewModel = viewModel(scope, weather)
        viewModel.onCameraIdle(bounds(42.0, 12.0))
        viewModel.onDscWeatherSessionResumed()

        viewModel.onCameraIdle(bounds(44.49, 11.34))
        delay(30)
        viewModel.onCameraIdle(bounds(41.90, 12.50))
        waitUntil { viewModel.uiState.value.dscWeather.data?.point?.lat == 41.90 }
        delay(180)

        assertEquals(41.90, viewModel.uiState.value.dscWeather.data?.point?.lat ?: -1.0, 0.0)
        viewModel.onDscWeatherSessionPaused()
        scope.cancel()
    }

    @Test
    fun revisionChangeWithLocalChangeCreatesFeedbackButBaselineDoesNot() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val weather = FakeDscWeatherClient(
            statuses = ArrayDeque(
                listOf(
                    WeatherStatus("c1", "v1"),
                    WeatherStatus("c2", "v1")
                )
            ),
            responses = ArrayDeque(
                listOf(
                    response(41.9, 12.5, CriticalityLevel.YELLOW),
                    response(41.9, 12.5, CriticalityLevel.ORANGE)
                )
            )
        )
        val viewModel = viewModel(scope, weather)
        viewModel.onCameraIdle(bounds(41.90, 12.50))
        viewModel.onDscWeatherSessionResumed()

        waitUntil { viewModel.uiState.value.dscWeather.changeMessage != null }

        assertNotNull(viewModel.uiState.value.dscWeather.changeMessage)
        assertEquals(CriticalityLevel.ORANGE, viewModel.uiState.value.dscWeather.banner?.criticalityLevel)
        viewModel.onDscWeatherSessionPaused()
        scope.cancel()
    }

    private fun viewModel(scope: CoroutineScope, weather: WeatherAlertsClient) = MapViewModel(
        zoneCheckRepository = FakeZoneCheckClient(),
        weatherAlertsRepository = weather,
        clock = clock,
        weatherStatusPollingIntervalMillis = 30L,
        weatherCameraDebounceMillis = 20L,
        weatherTimeRefreshMillis = 20L,
        loadHelpOnInit = false,
        externalScope = scope
    )

    private fun bounds(lat: Double, lon: Double) = CameraBounds(
        zoom = 10.0,
        north = lat + 0.1,
        south = lat - 0.1,
        east = lon + 0.1,
        west = lon - 0.1
    )

    private suspend fun waitUntil(predicate: () -> Boolean) {
        withTimeout(5_000) {
            while (!predicate()) delay(5)
        }
    }

    private class FakeZoneCheckClient : ZoneCheckV3Client {
        override fun check(lat: Double, lon: Double) = ZoneCheckV3Response(
            position = Position(lat, lon),
            verdict = Verdict("OPEN", 120, "BASE", "Fixture"),
            zones = emptyList(),
            blockers = emptyList(),
            warnings = emptyList(),
            baseline = Baseline(120, false),
            meta = Meta("DSC", "v3")
        )
    }

    private class FakeDscWeatherClient(
        private val delaysByLatitude: Map<Double, Long> = emptyMap(),
        private val statuses: ArrayDeque<WeatherStatus> = ArrayDeque(listOf(WeatherStatus("c1", "v1"))),
        private val responses: ArrayDeque<WeatherAlertResponse> = ArrayDeque()
    ) : WeatherAlertsClient {
        var alertCalls = 0
        var statusCalls = 0
        var lastPoint: MapPoint? = null

        override suspend fun getAlerts(lat: Double, lon: Double): WeatherAlertLoadResult {
            alertCalls += 1
            lastPoint = MapPoint(lat, lon)
            withContext(NonCancellable) { delay(delaysByLatitude[lat] ?: 0L) }
            val value = if (responses.isNotEmpty()) responses.removeFirst() else response(lat, lon)
            return WeatherAlertLoadResult.Available(value, Instant.parse("2026-09-02T12:00:00Z"), false)
        }

        override suspend fun getStatus(): Result<WeatherStatus> {
            statusCalls += 1
            val value = if (statuses.size > 1) statuses.removeFirst() else statuses.first()
            return Result.success(value)
        }
    }

    private companion object {
        fun response(
            lat: Double,
            lon: Double,
            level: CriticalityLevel = CriticalityLevel.NONE
        ) = WeatherAlertResponse(
            point = WeatherAlertPoint(lat, lon),
            criticality = WeatherCriticality(
                zoneCode = "zone",
                zoneName = "Zona",
                periods = mapOf(
                    "TODAY" to WeatherCriticalityPeriod(
                        onset = Instant.parse("2026-09-02T10:00:00Z"),
                        expires = Instant.parse("2026-09-02T20:00:00Z"),
                        overallLevel = level,
                        risks = WeatherRisk.entries.associateWith {
                            if (it == WeatherRisk.THUNDERSTORM) level else CriticalityLevel.NONE
                        }
                    )
                )
            ),
            vigilance = WeatherVigilance(null, null, emptyMap()),
            sources = null,
            disclaimer = null
        )
    }
}
