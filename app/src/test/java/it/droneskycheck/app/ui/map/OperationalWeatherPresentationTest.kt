package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.data.weather.WeatherForecastCacheDto
import it.droneskycheck.app.data.weather.WeatherForecastDay
import it.droneskycheck.app.data.weather.WeatherForecastHour
import it.droneskycheck.app.data.weather.WeatherForecastLocation
import it.droneskycheck.app.data.weather.WeatherForecastMetadata
import it.droneskycheck.app.data.weather.WeatherForecastUnitsDto
import it.droneskycheck.app.data.weather.WeatherMetrics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OperationalWeatherPresentationTest {
    @Test
    fun weatherCodeMappingCoversMainWmoFamilies() {
        assertEquals(
            OperationalWeatherCondition("Sereno", OperationalWeatherIcon.ClearDay),
            operationalWeatherCondition(0, isDaylight = true)
        )
        assertEquals(OperationalWeatherIcon.ClearNight, operationalWeatherCondition(0, isDaylight = false).icon)
        assertEquals(OperationalWeatherIcon.PartlyCloudyDay, operationalWeatherCondition(1, isDaylight = true).icon)
        assertEquals("Nuvoloso", operationalWeatherCondition(2, isDaylight = true).description)
        assertEquals("Coperto", operationalWeatherCondition(3, isDaylight = true).description)
        assertEquals(OperationalWeatherIcon.Fog, operationalWeatherCondition(45, isDaylight = true).icon)
        assertEquals(OperationalWeatherIcon.Drizzle, operationalWeatherCondition(51, isDaylight = true).icon)
        assertEquals(OperationalWeatherIcon.Rain, operationalWeatherCondition(61, isDaylight = true).icon)
        assertEquals(OperationalWeatherIcon.Showers, operationalWeatherCondition(80, isDaylight = true).icon)
        assertEquals(OperationalWeatherIcon.Snow, operationalWeatherCondition(71, isDaylight = true).icon)
        assertEquals(OperationalWeatherIcon.Thunderstorm, operationalWeatherCondition(95, isDaylight = true).icon)
        assertEquals(OperationalWeatherIcon.Unknown, operationalWeatherCondition(null, isDaylight = true).icon)
    }

    @Test
    fun groupsUtcHoursByEuropeRomeLocalDay() {
        val now = Instant.parse("2026-08-25T08:00:00Z")
        val forecast = forecast(
            "2026-08-24T21:00:00Z",
            "2026-08-24T22:00:00Z",
            "2026-08-25T21:00:00Z"
        )

        val days = buildOperationalWeatherDays(forecast, now)

        assertEquals(2, days.size)
        assertEquals(LocalDate.parse("2026-08-24"), days[0].date)
        assertEquals(LocalDate.parse("2026-08-25"), days[1].date)
        assertEquals("OGGI", days[1].label)
        assertEquals("00:00", days[1].slots.first().timeLabel)
    }

    @Test
    fun initialSelectionUsesClosestForecastHourToNow() {
        val now = Instant.parse("2026-08-25T10:20:00Z")
        val days = buildOperationalWeatherDays(
            forecast(
                "2026-08-25T09:00:00Z",
                "2026-08-25T10:00:00Z",
                "2026-08-25T11:00:00Z"
            ),
            now
        )

        val selected = selectInitialOperationalWeatherSlot(days, now)
            ?: error("Expected an initial weather slot")
        assertEquals(Instant.parse("2026-08-25T10:00:00Z"), selected.forecastHour.instant)
    }

    @Test
    fun dayChangeKeepsClosestPreviousLocalHour() {
        val now = Instant.parse("2026-08-25T08:00:00Z")
        val days = buildOperationalWeatherDays(
            forecast(
                "2026-08-25T12:00:00Z",
                "2026-08-26T07:00:00Z",
                "2026-08-26T12:00:00Z",
                "2026-08-26T16:00:00Z"
            ),
            now
        )
        val previous = days.first().slots.first()

        val selected = selectOperationalWeatherSlotForDay(days[1], previous)
            ?: error("Expected a weather slot for the selected day")
        assertEquals("14:00", previous.timeLabel)
        assertEquals("14:00", selected.timeLabel)
    }

    @Test
    fun timelineHandlesFullAndPartialDays() {
        val firstDayHours = (0 until 24).map { hour ->
            "2026-08-25T${hour.toString().padStart(2, '0')}:00:00Z"
        }
        val forecast = forecast(*(firstDayHours + listOf(
            "2026-08-26T00:00:00Z",
            "2026-08-26T01:00:00Z",
            "2026-08-26T02:00:00Z"
        )).toTypedArray())

        val days = buildOperationalWeatherDays(forecast, Instant.parse("2026-08-25T08:00:00Z"))

        assertEquals(2, days.size)
        assertEquals(22, days[0].slots.size)
        assertEquals(5, days[1].slots.size)
    }

    @Test
    fun missingMetricsStayDisplayable() {
        val metrics = WeatherMetrics()

        assertEquals("-- °C", metrics.temperatureText())
        assertEquals("-- km/h", metrics.windText())
        assertEquals("-- mm", metrics.precipitationText())
        assertEquals("-- %", metrics.cloudCoverText())
        assertNull(metrics.visibilityText())
        assertEquals("Condizioni non disponibili", operationalWeatherCondition(metrics.weatherCode, true).description)
    }

    private fun forecast(
        vararg utcInstants: String,
        metrics: WeatherMetrics = WeatherMetrics(
            temperatureC = 24.0,
            windSpeedKmh = 16.0,
            windGustsKmh = 24.0,
            precipitationMm = 0.0,
            precipitationProbabilityPct = 10.0,
            visibilityMeters = 10_000.0,
            weatherCode = 0,
            cloudCoverPct = 25.0
        )
    ): WeatherForecast =
        WeatherForecast(
            location = WeatherForecastLocation(
                requested = null,
                query = null,
                provider = null,
                timezoneId = "Europe/Rome",
                timezoneAbbreviation = "CEST",
                utcOffsetSeconds = 7_200
            ),
            timezone = ZoneId.of("Europe/Rome"),
            generatedAt = null,
            providerFetchedAt = null,
            hours = utcInstants.map { instant ->
                WeatherForecastHour(
                    instant = Instant.parse(instant),
                    offsetDateTime = null,
                    localDateTime = null,
                    localTimeText = null,
                    utcOffsetSeconds = null,
                    metrics = metrics,
                    missingFields = emptyList()
                )
            },
            days = emptyList<WeatherForecastDay>(),
            warnings = emptyList(),
            metadata = WeatherForecastMetadata(
                schemaVersion = 1,
                provider = "open-meteo",
                forecastDays = 3,
                units = WeatherForecastUnitsDto(null, null, null, null, null, null, null, null),
                cache = WeatherForecastCacheDto(false, null, null)
            )
        )
}
