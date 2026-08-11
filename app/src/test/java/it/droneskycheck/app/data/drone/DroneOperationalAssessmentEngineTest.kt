package it.droneskycheck.app.data.drone

import it.droneskycheck.app.data.LocalDrone
import it.droneskycheck.app.data.weather.WeatherAssessmentEngine
import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.data.weather.WeatherForecastCacheDto
import it.droneskycheck.app.data.weather.WeatherForecastHour
import it.droneskycheck.app.data.weather.WeatherForecastLocation
import it.droneskycheck.app.data.weather.WeatherForecastMetadata
import it.droneskycheck.app.data.weather.WeatherForecastUnitsDto
import it.droneskycheck.app.data.weather.WeatherMetrics
import it.droneskycheck.app.ui.map.summarizeDroneOperationalTrendByDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DroneOperationalAssessmentEngineTest {
    private val engine = DroneOperationalAssessmentEngine()
    private val weatherEngine = WeatherAssessmentEngine()
    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun droneWithoutTechnicalDataStaysUnknownAndMinimal() {
        val assessment = engine.assess(
            metrics = goodMetrics(),
            capabilities = LocalDrone(id = "mini", manufacturer = "DJI", model = "Mini", classLabel = "C0", weight = 249.0)
                .toOperationalCapabilities(),
            weatherAssessment = weatherEngine.assess(goodMetrics())
        )

        assertNotNull(assessment)
        assertEquals(DroneOperationalLevel.UNKNOWN, assessment!!.level)
        assertNull(assessment.score)
        assertEquals(DroneDataCompleteness.MINIMAL, assessment.dataCompleteness)
    }

    @Test
    fun knownWindResistanceKeepsLowWindFavorable() {
        val assessment = assess(
            metrics = goodMetrics(windKmh = 10.0, gustKmh = 16.0),
            capabilities = capableDrone(maxWindMs = 12.0)
        )

        assertEquals(DroneOperationalLevel.FAVORABLE, assessment.level)
        assertTrue((assessment.score ?: 0) >= 80)
    }

    @Test
    fun windAndGustsNearThresholdAreCautionOrUnfavorable() {
        val assessment = assess(
            metrics = goodMetrics(windKmh = 26.0, gustKmh = 37.0),
            capabilities = capableDrone(maxWindMs = 12.0)
        )

        assertTrue(assessment.level == DroneOperationalLevel.CAUTION || assessment.level == DroneOperationalLevel.UNFAVORABLE)
        assertTrue(assessment.factors.any { it.type == DroneOperationalFactorType.GUSTS })
    }

    @Test
    fun gustsOverDeclaredCapabilityAreUnfavorable() {
        val assessment = assess(
            metrics = goodMetrics(windKmh = 18.0, gustKmh = 45.0),
            capabilities = capableDrone(maxWindMs = 10.0)
        )

        assertEquals(DroneOperationalLevel.UNFAVORABLE, assessment.level)
        assertTrue(assessment.warnings.any { it.contains("superano", ignoreCase = true) })
    }

    @Test
    fun gustsMuchHigherThanAverageAreMoreCriticalThanSteadyWind() {
        val spiky = assess(
            metrics = goodMetrics(windKmh = 14.4, gustKmh = 39.6),
            capabilities = capableDrone(maxWindMs = 12.0)
        )
        val steady = assess(
            metrics = goodMetrics(windKmh = 25.2, gustKmh = 28.8),
            capabilities = capableDrone(maxWindMs = 12.0)
        )

        assertTrue((spiky.score ?: 100) < (steady.score ?: 0))
        assertTrue(spiky.factors.any { it.title == "Variabilita raffiche" })
    }

    @Test
    fun temperatureNormalNearAndOutsideRangeAreSeparated() {
        val normal = assess(goodMetrics(temperatureC = 22.0), capableDrone(minTemp = 0.0, maxTemp = 40.0))
        val near = assess(goodMetrics(temperatureC = 38.0), capableDrone(minTemp = 0.0, maxTemp = 40.0))
        val outside = assess(goodMetrics(temperatureC = 42.0), capableDrone(minTemp = 0.0, maxTemp = 40.0))

        assertEquals(DroneOperationalLevel.FAVORABLE, normal.factors.first { it.type == DroneOperationalFactorType.TEMPERATURE }.level)
        assertEquals(DroneOperationalLevel.CAUTION, near.factors.first { it.type == DroneOperationalFactorType.TEMPERATURE }.level)
        assertEquals(DroneOperationalLevel.UNFAVORABLE, outside.level)
    }

    @Test
    fun precipitationWithoutDeclaredCompatibilityIsUnfavorableWhileNoRainIsFine() {
        val rain = assess(
            metrics = goodMetrics(precipitationMm = 0.4, weatherCode = 61),
            capabilities = capableDrone(precipitation = DronePrecipitationCapability.UNKNOWN)
        )
        val dry = assess(
            metrics = goodMetrics(precipitationMm = 0.0, weatherCode = 0),
            capabilities = capableDrone(precipitation = DronePrecipitationCapability.UNKNOWN)
        )

        assertEquals(DroneOperationalLevel.UNFAVORABLE, rain.level)
        assertTrue(rain.warnings.any { it.contains("precipitazioni", ignoreCase = true) })
        assertNotEquals(DroneOperationalLevel.UNFAVORABLE, dry.level)
    }

    @Test
    fun missingWeatherDataAndIncompleteCapabilitiesStayExplicit() {
        val missingWeather = engine.assess(
            metrics = WeatherMetrics(weatherCode = 0),
            capabilities = capableDrone(maxWindMs = 12.0)
        )
        val partial = assess(goodMetrics(), capableDrone(maxWindMs = 12.0, minTemp = null, maxTemp = null))

        assertTrue((missingWeather?.warnings ?: emptyList()).isNotEmpty())
        assertEquals(DroneDataCompleteness.PARTIAL, partial.dataCompleteness)
    }

    @Test
    fun changingDroneWithSameWeatherProducesDifferentAssessment() {
        val metrics = goodMetrics(windKmh = 18.0, gustKmh = 32.0)
        val strong = assess(metrics, capableDrone(id = "strong", maxWindMs = 15.0))
        val sensitive = assess(metrics, capableDrone(id = "sensitive", maxWindMs = 8.0))

        assertTrue((strong.score ?: 0) > (sensitive.score ?: 100))
        assertEquals(DroneOperationalLevel.UNFAVORABLE, sensitive.level)
    }

    @Test
    fun dailyTrendFindsDroneSpecificBestWindowAndWeekend() {
        val forecast = forecast(
            dayHours("2026-08-15", 7..9, goodMetrics(windKmh = 8.0, gustKmh = 14.0)) +
                dayHours("2026-08-15", 14..16, goodMetrics(windKmh = 22.0, gustKmh = 42.0))
        )
        val trends = summarizeDroneOperationalTrendByDay(
            forecast = forecast,
            capabilities = capableDrone(maxWindMs = 12.0),
            now = Instant.parse("2026-08-15T04:00:00Z")
        )

        val saturday = trends.single()
        assertEquals(LocalDate.parse("2026-08-15"), saturday.date)
        assertTrue(saturday.isWeekend)
        assertTrue(saturday.variable || saturday.level == DroneOperationalLevel.UNFAVORABLE)
        assertNotNull(saturday.bestWindow)
    }

    @Test
    fun trendReturnsEmptyWhenMeteoOffInputsAreMissing() {
        assertTrue(
            summarizeDroneOperationalTrendByDay(
                forecast = null,
                capabilities = capableDrone(maxWindMs = 12.0),
                now = Instant.parse("2026-08-15T04:00:00Z")
            ).isEmpty()
        )
    }

    private fun assess(
        metrics: WeatherMetrics,
        capabilities: DroneOperationalCapabilities
    ): DroneOperationalAssessment =
        engine.assess(metrics, capabilities, weatherEngine.assess(metrics))!!

    private fun capableDrone(
        id: String = "drone",
        maxWindMs: Double? = 12.0,
        minTemp: Double? = 0.0,
        maxTemp: Double? = 40.0,
        precipitation: DronePrecipitationCapability = DronePrecipitationCapability.RAIN_RESISTANT
    ): DroneOperationalCapabilities =
        DroneOperationalCapabilities(
            droneId = id,
            displayName = id,
            manufacturer = "Test",
            model = id,
            maxWindResistanceMs = maxWindMs,
            minOperatingTemperatureC = minTemp,
            maxOperatingTemperatureC = maxTemp,
            precipitationCapability = precipitation,
            windResistanceSource = DroneCapabilitySource.MANUFACTURER,
            temperatureRangeSource = if (minTemp != null && maxTemp != null) DroneCapabilitySource.MANUFACTURER else DroneCapabilitySource.UNKNOWN,
            precipitationSource = DroneCapabilitySource.MANUFACTURER
        )

    private fun goodMetrics(
        windKmh: Double = 8.0,
        gustKmh: Double = 12.0,
        precipitationMm: Double = 0.0,
        weatherCode: Int = 0,
        temperatureC: Double = 22.0
    ): WeatherMetrics =
        WeatherMetrics(
            windSpeedKmh = windKmh,
            windGustsKmh = gustKmh,
            precipitationMm = precipitationMm,
            precipitationProbabilityPct = if (precipitationMm > 0.0) 70.0 else 5.0,
            visibilityMeters = 12_000.0,
            weatherCode = weatherCode,
            temperatureC = temperatureC,
            cloudCoverPct = 20.0
        )

    private fun forecast(hours: List<WeatherForecastHour>): WeatherForecast =
        WeatherForecast(
            location = WeatherForecastLocation(null, null, null, "Europe/Rome", "CEST", 7200),
            timezone = rome,
            generatedAt = Instant.parse("2026-08-15T04:00:00Z"),
            providerFetchedAt = Instant.parse("2026-08-15T04:00:00Z"),
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
}
