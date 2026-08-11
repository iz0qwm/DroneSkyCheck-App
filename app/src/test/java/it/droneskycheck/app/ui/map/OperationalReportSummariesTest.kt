package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.LegalTimelineSegment
import it.droneskycheck.app.data.LegalTimelineState
import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.data.weather.WeatherForecastCacheDto
import it.droneskycheck.app.data.weather.WeatherForecastHour
import it.droneskycheck.app.data.weather.WeatherForecastLocation
import it.droneskycheck.app.data.weather.WeatherForecastMetadata
import it.droneskycheck.app.data.weather.WeatherForecastUnitsDto
import it.droneskycheck.app.data.weather.WeatherMetrics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalReportSummariesTest {
    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun legalTimelineWindowIncludesNextWeekendFromWeekdaysAndWeekend() {
        assertEquals(
            Instant.parse("2026-08-16T22:00:00Z"),
            legalTimelineEndIncludingWeekend(Instant.parse("2026-08-10T10:00:00Z"), rome)
        )
        assertEquals(
            Instant.parse("2026-08-16T22:00:00Z"),
            legalTimelineEndIncludingWeekend(Instant.parse("2026-08-11T06:00:00Z"), rome)
        )
        assertEquals(
            Instant.parse("2026-08-16T22:00:00Z"),
            legalTimelineEndIncludingWeekend(Instant.parse("2026-08-14T10:00:00Z"), rome)
        )
        assertEquals(
            Instant.parse("2026-08-16T22:00:00Z"),
            legalTimelineEndIncludingWeekend(Instant.parse("2026-08-15T08:00:00Z"), rome)
        )
        assertEquals(
            Instant.parse("2026-08-16T22:00:00Z"),
            legalTimelineEndIncludingWeekend(Instant.parse("2026-08-16T08:00:00Z"), rome)
        )
    }

    @Test
    fun legalTimelineWindowHandlesMonthAndYearChanges() {
        assertEquals(
            Instant.parse("2026-02-01T23:00:00Z"),
            legalTimelineEndIncludingWeekend(Instant.parse("2026-01-30T10:00:00Z"), rome)
        )
        assertEquals(
            Instant.parse("2027-01-03T23:00:00Z"),
            legalTimelineEndIncludingWeekend(Instant.parse("2026-12-30T10:00:00Z"), rome)
        )
    }

    @Test
    fun legalDailySummarySplitsSegmentsAtMidnightAndKeepsMultipleWindows() {
        val summaries = summarizeLegalTimelineByDay(
            segments = listOf(
                segment("2026-08-11T20:00:00Z", "2026-08-11T22:00:00Z", LegalTimelineState.AVAILABLE_WITH_LIMIT, 45),
                segment("2026-08-11T22:00:00Z", "2026-08-12T02:00:00Z", LegalTimelineState.UNAVAILABLE, 0),
                segment("2026-08-12T02:00:00Z", "2026-08-12T05:00:00Z", LegalTimelineState.AUTH_REQUIRED, null),
                segment("2026-08-12T05:00:00Z", "2026-08-12T08:00:00Z", LegalTimelineState.UNKNOWN, null)
            ),
            zoneId = ZoneOffset.UTC,
            from = Instant.parse("2026-08-11T20:00:00Z"),
            to = Instant.parse("2026-08-12T08:00:00Z")
        )

        assertEquals(2, summaries.size)
        assertEquals(LocalDate.parse("2026-08-11"), summaries[0].date)
        assertEquals(2, summaries[0].windows.size)
        assertEquals(LegalTimelineState.AVAILABLE_WITH_LIMIT, summaries[0].windows[0].state)
        assertEquals(45, summaries[0].windows[0].maxAltitudeAgl)
        assertEquals(LegalTimelineState.UNAVAILABLE, summaries[0].windows[1].state)
        assertEquals(3, summaries[1].windows.size)
        assertEquals(LegalTimelineState.AUTH_REQUIRED, summaries[1].windows[1].state)
        assertEquals(LegalTimelineState.UNKNOWN, summaries[1].windows[2].state)
    }

    @Test
    fun legalDailySummaryMergesFullDayAvailabilityAndUnavailability() {
        val available = summarizeLegalTimelineByDay(
            segments = listOf(segment("2026-08-13T00:00:00Z", "2026-08-14T00:00:00Z", LegalTimelineState.AVAILABLE, 120)),
            zoneId = ZoneOffset.UTC,
            from = Instant.parse("2026-08-13T00:00:00Z"),
            to = Instant.parse("2026-08-14T00:00:00Z")
        ).single()
        val unavailable = summarizeLegalTimelineByDay(
            segments = listOf(segment("2026-08-13T00:00:00Z", "2026-08-14T00:00:00Z", LegalTimelineState.UNAVAILABLE, 0)),
            zoneId = ZoneOffset.UTC,
            from = Instant.parse("2026-08-13T00:00:00Z"),
            to = Instant.parse("2026-08-14T00:00:00Z")
        ).single()

        assertEquals(1, available.windows.size)
        assertEquals(LegalTimelineState.AVAILABLE, available.windows.single().state)
        assertEquals(120, available.windows.single().maxAltitudeAgl)
        assertEquals(LegalTimelineState.UNAVAILABLE, unavailable.windows.single().state)
    }

    @Test
    fun weatherTrendSummarizesUniformFavorableAndUnfavorableDays() {
        val favorable = summarizeWeatherTrendByDay(
            forecast = forecast(dayHours("2026-08-12", 8..11, goodMetrics())),
            now = Instant.parse("2026-08-12T06:00:00Z")
        ).single()
        val unfavorable = summarizeWeatherTrendByDay(
            forecast = forecast(dayHours("2026-08-12", 8..11, stormMetrics())),
            now = Instant.parse("2026-08-12T06:00:00Z")
        ).single()

        assertEquals(WeatherDailyTrendLabel.FAVORABLE, favorable.label)
        assertTrue((favorable.score ?: 0) >= 80)
        assertNotNull(favorable.bestWindow)
        assertEquals(WeatherDailyTrendLabel.UNFAVORABLE, unfavorable.label)
        assertTrue((unfavorable.score ?: 100) < 50)
    }

    @Test
    fun weatherTrendDoesNotHideVariableDay() {
        val trend = summarizeWeatherTrendByDay(
            forecast = forecast(
                dayHours("2026-08-12", 8..11, goodMetrics()) +
                    dayHours("2026-08-12", 14..17, stormMetrics())
            ),
            now = Instant.parse("2026-08-12T06:00:00Z")
        ).single()

        assertEquals(WeatherDailyTrendLabel.VARIABLE, trend.label)
        assertTrue(trend.variable)
        assertTrue((trend.score ?: 100) <= 65)
        assertNotNull(trend.bestWindow)
    }

    @Test
    fun weatherTrendFlagsPartialGustsRainMissingDataReliabilityAndWeekend() {
        val now = Instant.parse("2026-08-11T06:00:00Z")
        val trends = summarizeWeatherTrendByDay(
            forecast = forecast(
                dayHours("2026-08-15", 8..10, goodMetrics()) +
                    listOf(hour("2026-08-15", 15, goodMetrics().copy(windGustsKmh = 52.0, precipitationMm = 1.0))) +
                    dayHours("2026-08-16", 8..10, goodMetrics().copy(windSpeedKmh = null))
            ),
            now = now
        )

        val saturday = trends.first { it.date == LocalDate.parse("2026-08-15") }
        val sunday = trends.first { it.date == LocalDate.parse("2026-08-16") }
        assertTrue(saturday.isWeekend)
        assertEquals(ForecastReliability.INDICATIVE, saturday.reliability)
        assertTrue(saturday.notes.isNotEmpty())
        assertTrue(sunday.isWeekend)
        assertEquals(WeatherDailyTrendLabel.INSUFFICIENT, sunday.label)
        assertNull(sunday.score)
    }

    @Test
    fun weatherTrendReturnsEmptyWhenForecastOrFutureHoursAreUnavailable() {
        assertTrue(summarizeWeatherTrendByDay(null, Instant.parse("2026-08-11T06:00:00Z")).isEmpty())
        assertTrue(
            summarizeWeatherTrendByDay(
                forecast = forecast(dayHours("2026-08-10", 8..9, goodMetrics())),
                now = Instant.parse("2026-08-11T06:00:00Z")
            ).isEmpty()
        )
    }

    private fun segment(
        from: String,
        to: String,
        state: LegalTimelineState,
        maxAltitudeAgl: Int?
    ): LegalTimelineSegment =
        LegalTimelineSegment(
            from = Instant.parse(from),
            to = Instant.parse(to),
            state = state,
            rawState = state.name,
            maxAltitudeAgl = maxAltitudeAgl,
            authorization = null,
            contributors = emptyList(),
            warnings = emptyList(),
            confidence = "HIGH",
            reasonCodes = emptyList()
        )

    private fun forecast(hours: List<WeatherForecastHour>): WeatherForecast =
        WeatherForecast(
            location = WeatherForecastLocation(null, null, null, "Europe/Rome", "CEST", 7200),
            timezone = rome,
            generatedAt = Instant.parse("2026-08-11T06:00:00Z"),
            providerFetchedAt = Instant.parse("2026-08-11T06:00:00Z"),
            hours = hours,
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

    private fun dayHours(date: String, hours: IntRange, metrics: WeatherMetrics): List<WeatherForecastHour> =
        hours.map { hour -> hour(date, hour, metrics) }

    private fun hour(date: String, hour: Int, metrics: WeatherMetrics): WeatherForecastHour {
        val local = LocalDate.parse(date).atTime(hour, 0)
        return WeatherForecastHour(
            instant = local.atZone(rome).toInstant(),
            offsetDateTime = null,
            localDateTime = local,
            localTimeText = local.toString(),
            utcOffsetSeconds = 7200,
            metrics = metrics,
            missingFields = emptyList()
        )
    }

    private fun goodMetrics(): WeatherMetrics =
        WeatherMetrics(
            windSpeedKmh = 8.0,
            windGustsKmh = 12.0,
            precipitationMm = 0.0,
            precipitationProbabilityPct = 5.0,
            visibilityMeters = 12_000.0,
            weatherCode = 0,
            temperatureC = 22.0,
            cloudCoverPct = 20.0
        )

    private fun stormMetrics(): WeatherMetrics =
        goodMetrics().copy(
            windSpeedKmh = 32.0,
            windGustsKmh = 48.0,
            precipitationMm = 3.0,
            precipitationProbabilityPct = 90.0,
            weatherCode = 95
        )
}
