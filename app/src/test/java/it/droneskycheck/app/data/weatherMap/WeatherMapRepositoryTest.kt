package it.droneskycheck.app.data.weatherMap

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherMapRepositoryTest {
    @Test
    fun parsesValidOperationalV1Response() {
        val forecast = parseWeatherMapResponse(weatherMapFixture())

        assertEquals(1, forecast.schemaVersion)
        assertEquals("operational", forecast.mode)
        assertEquals(81, forecast.grid.nodeCount)
        assertEquals(81, forecast.nodes.size)
        assertEquals(72, forecast.times.size)
        assertEquals(5832, forecast.windSpeedKmh.size)
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        val error = runCatching {
            parseWeatherMapResponse(weatherMapFixture(schemaVersion = 2))
        }.exceptionOrNull()

        assertTrue(error is WeatherMapMappingError.UnsupportedSchemaVersion)
        assertEquals(2, (error as WeatherMapMappingError.UnsupportedSchemaVersion).schemaVersion)
    }

    @Test
    fun rejectsNonOperationalMode() {
        val error = runCatching {
            parseWeatherMapResponse(weatherMapFixture(mode = "preview"))
        }.exceptionOrNull()

        assertTrue(error is WeatherMapMappingError.UnsupportedMode)
    }

    @Test
    fun rejectsWrongNodeCount() {
        val json = weatherMapFixture()
        json.getJSONObject("grid").put("nodeCount", 80)

        val error = runCatching { parseWeatherMapResponse(json) }.exceptionOrNull()

        assertTrue(error is WeatherMapMappingError.InvalidGrid)
    }

    @Test
    fun rejectsWrongTimelineLength() {
        val json = weatherMapFixture()
        json.put("times", JSONArray().put("2026-08-25T00:00:00Z"))

        val error = runCatching { parseWeatherMapResponse(json) }.exceptionOrNull()

        assertTrue(error is WeatherMapMappingError.InvalidTimes)
    }

    @Test
    fun centralValueIndexUsesTimeThenNode() {
        val forecast = parseWeatherMapResponse(weatherMapFixture())

        assertEquals(0, forecast.valueIndex(timeIndex = 0, nodeIndex = 0))
        assertEquals(80, forecast.valueIndex(timeIndex = 0, nodeIndex = 80))
        assertEquals(81, forecast.valueIndex(timeIndex = 1, nodeIndex = 0))
        assertEquals(5831, forecast.valueIndex(timeIndex = 71, nodeIndex = 80))
    }

    @Test
    fun rejectsInsufficientWindArrays() {
        val json = weatherMapFixture()
        json.getJSONObject("data").put("windSpeed", JSONArray().put(12.0))

        val error = runCatching { parseWeatherMapResponse(json) }.exceptionOrNull()

        assertTrue(error is WeatherMapMappingError.InsufficientWindData)
    }

    @Test
    fun rejectsMalformedTimestamp() {
        val json = weatherMapFixture()
        json.getJSONArray("times").put(5, "25/08/2026 10:00")

        val error = runCatching { parseWeatherMapResponse(json) }.exceptionOrNull()

        assertTrue(error is WeatherMapMappingError.InvalidTimes)
    }

    @Test
    fun displayDirectionUsesOppositeMeteorologicalDirection() {
        assertEquals(180.0, meteorologicalToDisplayDirection(0.0), 0.0)
        assertEquals(270.0, meteorologicalToDisplayDirection(90.0), 0.0)
        assertEquals(0.0, meteorologicalToDisplayDirection(180.0), 0.0)
        assertEquals(90.0, meteorologicalToDisplayDirection(270.0), 0.0)
    }

    @Test
    fun timelineMatchingUsesInstantAndRomeConversion() {
        val forecast = parseWeatherMapResponse(weatherMapFixture(start = Instant.parse("2026-08-25T00:00:00Z")))
        val romeInstant = LocalDateTime.parse("2026-08-25T16:00:00")
            .atZone(ZoneId.of("Europe/Rome"))
            .toInstant()

        assertEquals(14, forecast.nearestTimeIndex(romeInstant))
    }

    @Test
    fun timelineMatchingSupportsNextDay() {
        val forecast = parseWeatherMapResponse(weatherMapFixture(start = Instant.parse("2026-08-25T00:00:00Z")))

        assertEquals(42, forecast.nearestTimeIndex(Instant.parse("2026-08-26T18:00:00Z")))
    }

    @Test
    fun timelineMatchingHandlesDstInstant() {
        val forecast = parseWeatherMapResponse(weatherMapFixture(start = Instant.parse("2026-10-25T00:00:00Z")))
        val afterDstChange = LocalDateTime.parse("2026-10-25T03:00:00")
            .atZone(ZoneId.of("Europe/Rome"))
            .toInstant()

        assertEquals(2, forecast.nearestTimeIndex(afterDstChange))
    }

    @Test
    fun timelineMatchingReturnsNullOutsideTolerance() {
        val forecast = parseWeatherMapResponse(weatherMapFixture(start = Instant.parse("2026-08-25T00:00:00Z")))

        assertNull(forecast.nearestTimeIndex(Instant.parse("2026-08-24T23:14:00Z")))
    }

    @Test
    fun windColorsFollowConfiguredSpeedBands() {
        assertEquals("#4FC3F7", windSpeedColorHex(4.0))
        assertEquals("#66BB6A", windSpeedColorHex(10.0))
        assertEquals("#FDD835", windSpeedColorHex(20.0))
        assertEquals("#FB8C00", windSpeedColorHex(30.0))
        assertEquals("#E53935", windSpeedColorHex(40.0))
    }

    @Test
    fun zoomThinningKeepsReadableWindFieldSubsets() {
        val forecast = parseWeatherMapResponse(weatherMapFixture())
        val time = forecast.times.first()

        assertEquals(25, forecast.windFieldFor(time, zoom = 9.0)?.vectors?.size)
        assertEquals(49, forecast.windFieldFor(time, zoom = 10.0)?.vectors?.size)
        assertEquals(81, forecast.windFieldFor(time, zoom = 10.5)?.vectors?.size)
        assertEquals(81, forecast.windFieldFor(time, zoom = 13.5)?.vectors?.size)
    }

    @Test
    fun featureCollectionBuildsArrowSegments() {
        val forecast = parseWeatherMapResponse(weatherMapFixture())
        val field = forecast.windFieldFor(forecast.times.first(), zoom = 13.5)

        assertEquals(81 * 3, weatherWindFieldToFeatureCollection(field).features()?.size)
    }

    @Test
    fun windComponentsUseMeteorologicalFromDirection() {
        assertVector(0.0, expectedU = 0.0, expectedV = -10.0)
        assertVector(90.0, expectedU = -10.0, expectedV = 0.0)
        assertVector(180.0, expectedU = 0.0, expectedV = 10.0)
        assertVector(270.0, expectedU = 10.0, expectedV = 0.0)
    }

    @Test
    fun particleFieldSamplesUniformCenterAndCorners() {
        val forecast = parseWeatherMapResponse(weatherMapFixture())
        val field = forecast.particleVectorFieldFor(forecast.times.first()) ?: error("Expected particle field")
        val center = WeatherLocalPoint(
            xKm = (field.minXKm + field.maxXKm) / 2.0,
            yKm = (field.minYKm + field.maxYKm) / 2.0
        )

        assertTrue(field.sample(center)?.speedKmh?.isFinite() == true)
        assertTrue(field.sample(WeatherLocalPoint(field.minXKm, field.minYKm))?.speedKmh?.isFinite() == true)
        assertTrue(field.sample(WeatherLocalPoint(field.maxXKm, field.maxYKm))?.speedKmh?.isFinite() == true)
    }

    @Test
    fun particleInterpolationDoesNotAverageDirectionsAcrossNorth() {
        val field = WeatherParticleVectorField(
            originLat = 41.9,
            originLon = 12.5,
            rows = 2,
            cols = 2,
            minXKm = 0.0,
            maxXKm = 1.0,
            minYKm = 0.0,
            maxYKm = 1.0,
            vectors = listOf(359.0, 1.0, 359.0, 1.0).map {
                meteorologicalWindToComponents(10.0, it) ?: error("component")
            }
        )

        val sample = field.sample(WeatherLocalPoint(0.5, 0.5)) ?: error("Expected sample")

        assertTrue(sample.v < -9.0)
        assertTrue(kotlin.math.abs(sample.u) < 1.0)
    }

    @Test
    fun particleEngineRespawnsOnMaxAgeAndOutOfArea() {
        val forecast = parseWeatherMapResponse(weatherMapFixture())
        val field = forecast.particleVectorFieldFor(forecast.times.first()) ?: error("Expected particle field")
        val engine = WeatherParticleEngine(particleCount = 4, random = Random(7))
        engine.reset(field)

        repeat(120) { engine.step(field, 1.0 / 30.0) }

        assertEquals(4, engine.snapshot().size)
        assertTrue(engine.snapshot().all { field.contains(WeatherLocalPoint(it.xKm, it.yKm)) })
    }

    @Test
    fun cameraFitContainsGridWhenRequestedCenterIsGridCenter() {
        val forecast = parseWeatherMapResponse(weatherMapFixture())
        val fit = forecast.cameraFitFor(4L, 41.9, 12.5) ?: error("Expected fit")

        assertTrue(fit.containsAll(forecast.nodes))
        assertEquals(41.9, fit.targetLat, 0.0)
        assertEquals(12.5, fit.targetLon, 0.0)
    }

    @Test
    fun cameraFitContainsGridWhenRequestedCenterIsOffset() {
        val forecast = parseWeatherMapResponse(weatherMapFixture())
        val fit = forecast.cameraFitFor(5L, 41.731, 12.302) ?: error("Expected fit")

        assertTrue(fit.containsAll(forecast.nodes))
        assertEquals(41.731, fit.targetLat, 0.0)
        assertEquals(12.302, fit.targetLon, 0.0)
    }

    @Test
    fun repositoryMapsInvalidJson() = runBlocking {
        val repository = WeatherMapRepository(
            endpointUrl = "https://example.test/appWeatherMap",
            apiKey = "test",
            httpClient = FakeWeatherMapHttpClient(WeatherMapHttpResponse(200, "not-json"))
        )

        val error = repository.getWeatherMap(41.9, 12.5).exceptionOrNull()

        assertTrue(error is WeatherMapRepositoryError.InvalidJson)
    }

    @Test
    fun repositoryCachesSamePointInMemory() = runBlocking {
        val client = FakeWeatherMapHttpClient(WeatherMapHttpResponse(200, weatherMapFixture().toString()))
        val repository = WeatherMapRepository(
            endpointUrl = "https://example.test/appWeatherMap",
            apiKey = "test",
            httpClient = client
        )

        repository.getWeatherMap(41.9, 12.5)
        repository.getWeatherMap(41.9, 12.5)

        assertEquals(1, client.calls)
    }

    private class FakeWeatherMapHttpClient(
        private val response: WeatherMapHttpResponse
    ) : WeatherMapHttpClient {
        var calls: Int = 0
            private set

        override fun get(url: String, headers: Map<String, String>, timeoutMillis: Int): WeatherMapHttpResponse {
            calls += 1
            return response
        }
    }

    private fun assertVector(direction: Double, expectedU: Double, expectedV: Double) {
        val vector = meteorologicalWindToComponents(10.0, direction) ?: error("Expected vector")
        assertEquals(expectedU, vector.u, 0.0001)
        assertEquals(expectedV, vector.v, 0.0001)
    }
}

private fun weatherMapFixture(
    schemaVersion: Int = 1,
    mode: String = "operational",
    start: Instant = Instant.parse("2026-08-25T00:00:00Z")
): JSONObject {
    val nodeCount = 81
    val timeSteps = 72
    val values = nodeCount * timeSteps
    return JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("mode", mode)
        .put("requestedCenter", JSONObject().put("lat", 41.9).put("lon", 12.5))
        .put(
            "grid",
            JSONObject()
                .put("centerLat", 41.9)
                .put("centerLon", 12.5)
                .put("rows", 9)
                .put("cols", 9)
                .put("nodeCount", nodeCount)
                .put("stepKm", 2.5)
                .put("widthKm", 20.0)
                .put("heightKm", 20.0)
        )
        .put(
            "forecast",
            JSONObject()
                .put("hours", timeSteps)
                .put("timestepMinutes", 60)
                .put("timezone", "UTC")
                .put("displayTimezone", "Europe/Rome")
        )
        .put("units", JSONObject().put("windSpeedKmh", "km/h").put("windDirectionDegrees", "degrees").put("windGustsKmh", "km/h"))
        .put("times", JSONArray((0 until timeSteps).map { start.plusSeconds(it * 3600L).toString() }))
        .put("nodes", JSONArray((0 until nodeCount).map { index ->
            val row = index / 9
            val col = index % 9
            JSONObject()
                .put("lat", 41.8 + row * 0.025)
                .put("lon", 12.4 + col * 0.025)
                .put("providerLat", 41.8 + row * 0.025)
                .put("providerLon", 12.4 + col * 0.025)
                .put("elevationMeters", 10.0)
        }))
        .put(
            "data",
            JSONObject()
                .put("windSpeed", JSONArray((0 until values).map { (it % 50).toDouble() }))
                .put("windDirection", JSONArray((0 until values).map { ((it % 4) * 90).toDouble() }))
                .put("windGusts", JSONArray((0 until values).map { (it % 50).toDouble() + 5.0 }))
        )
        .put("cache", JSONObject().put("hit", false).put("ageMs", 0L).put("ttlMs", 900_000L))
}
