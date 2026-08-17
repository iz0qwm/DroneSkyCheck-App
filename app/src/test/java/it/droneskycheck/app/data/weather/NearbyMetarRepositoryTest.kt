package it.droneskycheck.app.data.weather

import it.droneskycheck.app.data.Airport
import it.droneskycheck.app.data.AirportRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyMetarRepositoryTest {
    @Test
    fun returnsClosestMetarInsideRadius() = runBlocking {
        val client = FakeMetarHttpClient(
            """
                [
                  {
                    "icaoId": "LIRA",
                    "lat": 41.7994,
                    "lon": 12.5949,
                    "rawOb": "LIRA 171950Z 25008KT CAVOK 24/18 Q1014",
                    "obsTime": "2026-08-17T19:50:00Z",
                    "temp": 24,
                    "dewp": 18,
                    "wdir": 250,
                    "wspd": 8,
                    "wgst": null,
                    "visib": 10,
                    "fltCat": "VFR"
                  },
                  {
                    "icaoId": "LIRF",
                    "lat": 41.8003,
                    "lon": 12.2389,
                    "rawOb": "LIRF 171950Z 26010KT CAVOK 25/19 Q1014"
                  }
                ]
            """.trimIndent()
        )

        val metar = NearbyMetarRepository(httpClient = client)
            .getNearbyMetar(41.80, 12.59, radiusKm = 20.0)
            .getOrThrow()

        assertEquals("LIRA", metar?.icao)
        assertEquals(24.0, metar?.temperatureC ?: -1.0, 0.0)
        assertTrue(client.lastUrl.contains("format=json"))
        assertTrue(client.lastUrl.contains("bbox="))
        assertTrue(client.lastUrl.contains("hours=2"))
        assertFalse(client.lastUrl.contains("ids="))
    }

    @Test
    fun returnsFiumicinoMetarInsideAirportRadius() = runBlocking {
        val client = FakeMetarHttpClient(
            """
                [
                  {
                    "icaoId": "LIRF",
                    "lat": 41.8003,
                    "lon": 12.2389,
                    "rawOb": "LIRF 172150Z 26010KT CAVOK 25/19 Q1014"
                  }
                ]
            """.trimIndent()
        )

        val metar = NearbyMetarRepository(
            airportRepository = AirportRepository(listOf(fiumicinoAirport())),
            httpClient = client
        )
            .getNearbyMetar(41.80308286164271, 12.256609398535403, radiusKm = 20.0)
            .getOrThrow()

        assertEquals("LIRF", metar?.icao)
        assertTrue(client.lastUrl.contains("ids=LIRF"))
        assertFalse(client.lastUrl.contains("bbox="))
    }

    @Test
    fun returnsNullForNoContentResponse() = runBlocking {
        val client = FakeMetarHttpClient(body = "", statusCode = 204)

        val metar = NearbyMetarRepository(httpClient = client)
            .getNearbyMetar(41.80, 12.59, radiusKm = 20.0)
            .getOrThrow()

        assertNull(metar)
    }

    @Test
    fun returnsNullForEmptySuccessfulResponse() = runBlocking {
        val client = FakeMetarHttpClient(body = "", statusCode = 200)

        val metar = NearbyMetarRepository(httpClient = client)
            .getNearbyMetar(41.80, 12.59, radiusKm = 20.0)
            .getOrThrow()

        assertNull(metar)
    }

    @Test
    fun returnsNullWhenNoStationIsInsideRadius() = runBlocking {
        val client = FakeMetarHttpClient(
            """[{ "icaoId": "FAR", "lat": 44.0, "lon": 12.0, "rawOb": "FAR" }]"""
        )

        val metar = NearbyMetarRepository(httpClient = client)
            .getNearbyMetar(41.80, 12.59, radiusKm = 20.0)
            .getOrThrow()

        assertNull(metar)
    }
}

private fun fiumicinoAirport(): Airport =
    Airport(
        icao = "LIRF",
        name = "Aeroporto Internazionale Leonardo da Vinci",
        city = "Roma",
        lat = 41.800278,
        lon = 12.238889
    )

private class FakeMetarHttpClient(
    private val body: String,
    private val statusCode: Int = 200
) : NearbyMetarHttpClient {
    var lastUrl: String = ""
        private set

    override fun get(url: String, timeoutMillis: Int): NearbyMetarHttpResponse {
        lastUrl = url
        return NearbyMetarHttpResponse(statusCode, body)
    }
}
