package it.droneskycheck.app.data.weather

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherForecastRepositoryTest {
    @Test
    fun completeV1ResponseMapsToDomain() {
        val forecast = parseFixture().toDomain()

        assertEquals(1, forecast.metadata.schemaVersion)
        assertEquals("open-meteo", forecast.metadata.provider)
        assertEquals(3, forecast.hours.size)
        assertEquals(2, forecast.days.size)
        assertEquals(Instant.parse("2026-08-11T08:00:00.000Z"), forecast.generatedAt)
    }

    @Test
    fun mapsRequestedQueryAndProviderCoordinates() {
        val location = parseFixture().toDomain().location

        assertEquals(41.9028, location.requested?.latitude ?: -1.0, 0.0)
        assertEquals(12.4964, location.requested?.longitude ?: -1.0, 0.0)
        assertEquals(41.925, location.query?.latitude ?: -1.0, 0.0)
        assertEquals(12.475, location.query?.longitude ?: -1.0, 0.0)
        assertEquals("bucket-center", location.query?.source)
        assertEquals(0.05, location.query?.bucketSizeDegrees ?: -1.0, 0.0)
        assertEquals(41.9375, location.provider?.latitude ?: -1.0, 0.0)
        assertEquals(12.5, location.provider?.longitude ?: -1.0, 0.0)
    }

    @Test
    fun mapsTimezone() {
        val forecast = parseFixture().toDomain()

        assertEquals("Europe/Rome", forecast.location.timezoneId)
        assertEquals("Europe/Rome", forecast.timezone?.id)
        assertEquals("GMT+2", forecast.location.timezoneAbbreviation)
        assertEquals(7200, forecast.location.utcOffsetSeconds)
    }

    @Test
    fun usesUtcTimeAsCanonicalInstant() {
        val hour = parseFixture().toDomain().hours.first()

        assertEquals(Instant.parse("2026-08-10T22:00:00.000Z"), hour.instant)
    }

    @Test
    fun mapsLocalHourWithPlusTwoOffset() {
        val hour = parseFixture().toDomain().hours.first()

        assertEquals(LocalDateTime.parse("2026-08-11T00:00:00"), hour.localDateTime)
        assertEquals(ZoneOffset.ofHours(2), hour.offsetDateTime?.offset)
        assertEquals("2026-08-11T00:00:00", hour.localTimeText)
    }

    @Test
    fun supportsDifferentDstOffset() {
        val forecast = parseFixture(
            firstHourTime = "2026-12-11T08:00:00+01:00",
            firstHourUtcTime = "2026-12-11T07:00:00.000Z",
            firstHourLocalTime = "2026-12-11T08:00:00",
            firstHourUtcOffsetSeconds = 3600
        ).toDomain()

        val hour = forecast.hours.first()
        assertEquals(Instant.parse("2026-12-11T07:00:00.000Z"), hour.instant)
        assertEquals(ZoneOffset.ofHours(1), hour.offsetDateTime?.offset)
        assertEquals(LocalDateTime.parse("2026-12-11T08:00:00"), hour.localDateTime)
    }

    @Test
    fun mapsSunriseAndSunset() {
        val day = parseFixture().toDomain().days.first()

        assertEquals(LocalDate.parse("2026-08-11"), day.date)
        assertEquals(Instant.parse("2026-08-11T04:13:00.000Z"), day.sunrise)
        assertEquals(Instant.parse("2026-08-11T18:16:00.000Z"), day.sunset)
        assertEquals("2026-08-11T06:13:00", day.sunriseLocalTimeText)
        assertEquals("2026-08-11T20:16:00", day.sunsetLocalTimeText)
    }

    @Test
    fun mapsWindSpeed() {
        assertEquals(1.5, firstMetrics().windSpeedKmh ?: -1.0, 0.0)
    }

    @Test
    fun mapsWindGusts() {
        assertEquals(6.5, firstMetrics().windGustsKmh ?: -1.0, 0.0)
    }

    @Test
    fun mapsPrecipitation() {
        assertEquals(0.0, firstMetrics().precipitationMm ?: -1.0, 0.0)
        assertEquals(0.0, firstMetrics().precipitationProbabilityPct ?: -1.0, 0.0)
    }

    @Test
    fun mapsVisibility() {
        assertEquals(35_880.0, firstMetrics().visibilityMeters ?: -1.0, 0.0)
    }

    @Test
    fun mapsWeatherCode() {
        assertEquals(0, firstMetrics().weatherCode)
    }

    @Test
    fun preservesNullValues() {
        val metrics = parseFixture(secondHourVisibilityMeters = null, secondHourPrecipitationMm = null)
            .toDomain()
            .hours[1]
            .metrics

        assertNull(metrics.visibilityMeters)
        assertNull(metrics.precipitationMm)
    }

    @Test
    fun visibilityNullDoesNotBecomeZero() {
        val metrics = parseFixture(secondHourVisibilityMeters = null)
            .toDomain()
            .hours[1]
            .metrics

        assertNull(metrics.visibilityMeters)
    }

    @Test
    fun precipitationNullDoesNotBecomeZero() {
        val metrics = parseFixture(secondHourPrecipitationMm = null)
            .toDomain()
            .hours[1]
            .metrics

        assertNull(metrics.precipitationMm)
    }

    @Test
    fun rejectsEmptyForecast() {
        val result = runCatching { parseFixture(forecastBody = "[]").toDomain() }

        assertTrue(result.exceptionOrNull() is WeatherForecastMappingError.EmptyForecast)
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        val result = runCatching { parseFixture(schemaVersion = 2).toDomain() }

        val error = result.exceptionOrNull()
        assertTrue(error is WeatherForecastMappingError.UnsupportedSchemaVersion)
        assertEquals(2, (error as WeatherForecastMappingError.UnsupportedSchemaVersion).schemaVersion)
    }

    @Test
    fun mapsBackendWarnings() {
        val warning = parseFixture(
            warningsBody = """[{"code":"MISSING_HOURLY_FIELD","field":"visibility","message":"Missing visibility"}]"""
        ).toDomain().warnings.first()

        assertEquals("MISSING_HOURLY_FIELD", warning.code)
        assertEquals("visibility", warning.field)
        assertEquals("Missing visibility", warning.message)
    }

    @Test
    fun repositoryMapsHttp400() = runBlocking {
        val error = repositoryWith(FakeWeatherClient(WeatherForecastHttpResponse(400, """{"error":"bad"}""")))
            .getForecast(41.9, 12.5)
            .exceptionOrNull()

        assertHttpError(error, 400)
    }

    @Test
    fun repositoryMapsHttp502() = runBlocking {
        val error = repositoryWith(FakeWeatherClient(WeatherForecastHttpResponse(502, """{"error":"provider"}""")))
            .getForecast(41.9, 12.5)
            .exceptionOrNull()

        assertHttpError(error, 502)
    }

    @Test
    fun repositoryMapsHttp504() = runBlocking {
        val error = repositoryWith(FakeWeatherClient(WeatherForecastHttpResponse(504, """{"error":"timeout"}""")))
            .getForecast(41.9, 12.5)
            .exceptionOrNull()

        assertHttpError(error, 504)
    }

    @Test
    fun repositoryMapsTimeoutAndNetworkErrors() = runBlocking {
        val timeout = repositoryWith(FakeWeatherClient(error = WeatherForecastRepositoryError.Timeout("timeout")))
            .getForecast(41.9, 12.5)
            .exceptionOrNull()
        val network = repositoryWith(FakeWeatherClient(error = WeatherForecastRepositoryError.Network("offline")))
            .getForecast(41.9, 12.5)
            .exceptionOrNull()

        assertTrue(timeout is WeatherForecastRepositoryError.Timeout)
        assertTrue(network is WeatherForecastRepositoryError.Network)
    }

    @Test
    fun repositoryMapsInvalidJson() = runBlocking {
        val error = repositoryWith(FakeWeatherClient(WeatherForecastHttpResponse(200, "not json")))
            .getForecast(41.9, 12.5)
            .exceptionOrNull()

        assertTrue(error is WeatherForecastRepositoryError.InvalidJson)
    }

    @Test
    fun repositoryRejectsUnsupportedSchemaVersion() = runBlocking {
        val error = repositoryWith(FakeWeatherClient(WeatherForecastHttpResponse(200, fixtureJson(schemaVersion = 9))))
            .getForecast(41.9, 12.5)
            .exceptionOrNull()

        assertTrue(error is WeatherForecastRepositoryError.UnsupportedSchemaVersion)
    }

    @Test
    fun repositoryRejectsEmptyForecast() = runBlocking {
        val error = repositoryWith(FakeWeatherClient(WeatherForecastHttpResponse(200, fixtureJson(forecastBody = "[]"))))
            .getForecast(41.9, 12.5)
            .exceptionOrNull()

        assertTrue(error is WeatherForecastRepositoryError.EmptyForecast)
    }

    @Test
    fun repositoryUsesSameSecurityHeaderAsOtherAppEndpoints() = runBlocking {
        val client = FakeWeatherClient(WeatherForecastHttpResponse(200, fixtureJson()))
        val forecast = WeatherForecastRepository(
            endpointUrl = "https://example.test/appWeatherForecast",
            apiKey = "dsc_app_key",
            httpClient = client
        ).getForecast(41.9, 12.5)

        assertTrue(forecast.isSuccess)
        assertEquals("application/json", client.lastHeaders["Accept"])
        assertEquals("dsc_app_key", client.lastHeaders["x-api-key"])
        assertTrue(client.lastUrl.contains("appWeatherForecast"))
        assertTrue(client.lastUrl.contains("lat=41.9"))
        assertTrue(client.lastUrl.contains("lon=12.5"))
    }

    @Test
    fun mapsWeatherForecastHourToWeatherMetrics() {
        val metrics = parseFixture().toDomain().hours[1].toWeatherMetrics()

        assertEquals(22.0, metrics.windSpeedKmh ?: -1.0, 0.0)
        assertEquals(25.0, metrics.windGustsKmh ?: -1.0, 0.0)
        assertEquals(180.0, metrics.windDirectionDegrees ?: -1.0, 0.0)
        assertEquals(0.0, metrics.precipitationMm ?: -1.0, 0.0)
        assertEquals(0.0, metrics.precipitationProbabilityPct ?: -1.0, 0.0)
        assertEquals(20_000.0, metrics.visibilityMeters ?: -1.0, 0.0)
        assertEquals(0, metrics.weatherCode)
        assertEquals(27.0, metrics.temperatureC ?: -1.0, 0.0)
        assertEquals(10.0, metrics.cloudCoverPct ?: -1.0, 0.0)
    }

    @Test
    fun weatherMetricsCanBePassedToWeatherAssessmentEngine() {
        val assessment = WeatherAssessmentEngine().assess(parseFixture().toDomain().hours.first().toWeatherMetrics())

        assertEquals(WeatherState.FAVORABLE, assessment.state)
    }

    @Test
    fun fixtureToDomainToWeatherAssessmentCoversThreeRepresentativeHours() = runBlocking {
        val forecast = repositoryWith(FakeWeatherClient(WeatherForecastHttpResponse(200, fixtureJson())))
            .getForecast(41.9, 12.5)
            .getOrThrow()
        val assessments = forecast.hours.map { WeatherAssessmentEngine().assess(it.toWeatherMetrics()) }

        assertEquals(WeatherState.FAVORABLE, assessments[0].state)
        assertEquals(WeatherState.FAVORABLE, assessments[1].state)
        assertEquals(84, assessments[1].score)
        assertTrue(assessments[1].reasons.contains(WeatherReasonCode.STRONG_WIND))
        assertEquals(WeatherState.UNFAVORABLE, assessments[2].state)
        assertTrue(assessments[2].reasons.contains(WeatherReasonCode.THUNDERSTORM))
    }

    private fun firstMetrics(): WeatherMetrics =
        parseFixture().toDomain().hours.first().metrics

    private fun parseFixture(
        schemaVersion: Int = 1,
        forecastBody: String? = null,
        warningsBody: String = "[]",
        firstHourTime: String = "2026-08-11T00:00:00+02:00",
        firstHourUtcTime: String = "2026-08-10T22:00:00.000Z",
        firstHourLocalTime: String = "2026-08-11T00:00:00",
        firstHourUtcOffsetSeconds: Int = 7200,
        secondHourVisibilityMeters: Double? = 20_000.0,
        secondHourPrecipitationMm: Double? = 0.0
    ): WeatherForecastApiResponse =
        parseWeatherForecastApiResponse(
            org.json.JSONObject(
                fixtureJson(
                    schemaVersion = schemaVersion,
                    forecastBody = forecastBody,
                    warningsBody = warningsBody,
                    firstHourTime = firstHourTime,
                    firstHourUtcTime = firstHourUtcTime,
                    firstHourLocalTime = firstHourLocalTime,
                    firstHourUtcOffsetSeconds = firstHourUtcOffsetSeconds,
                    secondHourVisibilityMeters = secondHourVisibilityMeters,
                    secondHourPrecipitationMm = secondHourPrecipitationMm
                )
            )
        )

    private fun fixtureJson(
        schemaVersion: Int = 1,
        forecastBody: String? = null,
        warningsBody: String = "[]",
        firstHourTime: String = "2026-08-11T00:00:00+02:00",
        firstHourUtcTime: String = "2026-08-10T22:00:00.000Z",
        firstHourLocalTime: String = "2026-08-11T00:00:00",
        firstHourUtcOffsetSeconds: Int = 7200,
        secondHourVisibilityMeters: Double? = 20_000.0,
        secondHourPrecipitationMm: Double? = 0.0
    ): String {
        val forecast = forecastBody ?: """
            [
              {
                "time": "$firstHourTime",
                "utcTime": "$firstHourUtcTime",
                "localTime": "$firstHourLocalTime",
                "utcOffsetSeconds": $firstHourUtcOffsetSeconds,
                "temperatureC": 26.8,
                "windSpeedKmh": 1.5,
                "windGustsKmh": 6.5,
                "windDirectionDegrees": 225,
                "precipitationMm": 0,
                "precipitationProbabilityPct": 0,
                "visibilityMeters": 35880,
                "cloudCoverPct": 0,
                "weatherCode": 0,
                "missingFields": []
              },
              {
                "time": "2026-08-11T10:00:00+02:00",
                "utcTime": "2026-08-11T08:00:00.000Z",
                "localTime": "2026-08-11T10:00:00",
                "utcOffsetSeconds": 7200,
                "temperatureC": 27,
                "windSpeedKmh": 22,
                "windGustsKmh": 25,
                "windDirectionDegrees": 180,
                "precipitationMm": ${secondHourPrecipitationMm?.toString() ?: "null"},
                "precipitationProbabilityPct": 0,
                "visibilityMeters": ${secondHourVisibilityMeters?.toString() ?: "null"},
                "cloudCoverPct": 10,
                "weatherCode": 0,
                "missingFields": []
              },
              {
                "time": "2026-08-11T17:00:00+02:00",
                "utcTime": "2026-08-11T15:00:00.000Z",
                "localTime": "2026-08-11T17:00:00",
                "utcOffsetSeconds": 7200,
                "temperatureC": 28,
                "windSpeedKmh": 8,
                "windGustsKmh": 12,
                "windDirectionDegrees": 200,
                "precipitationMm": 0,
                "precipitationProbabilityPct": 20,
                "visibilityMeters": 20000,
                "cloudCoverPct": 30,
                "weatherCode": 95,
                "missingFields": []
              }
            ]
        """.trimIndent()

        return """
            {
              "schemaVersion": $schemaVersion,
              "provider": "open-meteo",
              "generatedAt": "2026-08-11T08:00:00.000Z",
              "providerFetchedAt": "2026-08-11T08:00:00.000Z",
              "forecastDays": 7,
              "location": {
                "requested": { "latitude": 41.9028, "longitude": 12.4964 },
                "query": {
                  "latitude": 41.925,
                  "longitude": 12.475,
                  "source": "bucket-center",
                  "bucketSizeDegrees": 0.05
                },
                "provider": {
                  "latitude": 41.9375,
                  "longitude": 12.5,
                  "elevationMeters": 59
                },
                "timezone": "Europe/Rome",
                "timezoneAbbreviation": "GMT+2",
                "utcOffsetSeconds": 7200
              },
              "units": {
                "temperatureC": "celsius",
                "windSpeedKmh": "km/h",
                "windGustsKmh": "km/h",
                "windDirectionDegrees": "degrees",
                "precipitationMm": "mm",
                "precipitationProbabilityPct": "%",
                "visibilityMeters": "m",
                "cloudCoverPct": "%"
              },
              "forecast": $forecast,
              "days": [
                {
                  "date": "2026-08-11",
                  "sunrise": "2026-08-11T06:13:00+02:00",
                  "sunriseUtc": "2026-08-11T04:13:00.000Z",
                  "sunriseLocalTime": "2026-08-11T06:13:00",
                  "sunset": "2026-08-11T20:16:00+02:00",
                  "sunsetUtc": "2026-08-11T18:16:00.000Z",
                  "sunsetLocalTime": "2026-08-11T20:16:00",
                  "utcOffsetSeconds": 7200,
                  "missingFields": []
                },
                {
                  "date": "2026-08-12",
                  "sunrise": "2026-08-12T06:14:00+02:00",
                  "sunriseUtc": "2026-08-12T04:14:00.000Z",
                  "sunriseLocalTime": "2026-08-12T06:14:00",
                  "sunset": "2026-08-12T20:15:00+02:00",
                  "sunsetUtc": "2026-08-12T18:15:00.000Z",
                  "sunsetLocalTime": "2026-08-12T20:15:00",
                  "utcOffsetSeconds": 7200,
                  "missingFields": []
                }
              ],
              "warnings": $warningsBody,
              "cache": {
                "hit": false,
                "ttlSeconds": 5400,
                "key": "open-meteo|schema:1|vars:app-weather-forecast-v1|days:7|bucket:0.05|lat:41.925|lon:12.475"
              }
            }
        """.trimIndent()
    }

    private fun repositoryWith(client: FakeWeatherClient): WeatherForecastRepository =
        WeatherForecastRepository(
            endpointUrl = "https://example.test/appWeatherForecast",
            apiKey = "dsc_test",
            httpClient = client
        )

    private fun assertHttpError(error: Throwable?, statusCode: Int) {
        assertTrue(error is WeatherForecastRepositoryError.HttpError)
        assertEquals(statusCode, (error as WeatherForecastRepositoryError.HttpError).statusCode)
    }

    private class FakeWeatherClient(
        private val response: WeatherForecastHttpResponse? = null,
        private val error: Throwable? = null
    ) : WeatherForecastHttpClient {
        var lastUrl: String = ""
        var lastHeaders: Map<String, String> = emptyMap()

        override fun get(
            url: String,
            headers: Map<String, String>,
            timeoutMillis: Int
        ): WeatherForecastHttpResponse {
            lastUrl = url
            lastHeaders = headers
            error?.let { throw it }
            return response ?: WeatherForecastHttpResponse(200, "{}")
        }
    }
}
