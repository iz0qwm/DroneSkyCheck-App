package it.droneskycheck.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AirportRepositoryTest {
    @Test
    fun loadsAirportJsonAsset() {
        val airports = repository().loadAirports()

        assertEquals(43, airports.size)
        assertEquals("LIEA", airports.first().icao)
        assertEquals("LIMG", airports.last().icao)
    }

    @Test
    fun findsNearestAirportForRomeLikeWeb() {
        val nearest = repository().findNearestAirport(AirportPoint(41.9, 12.5))

        assertNotNull(nearest)
        assertEquals("LIRA", nearest!!.airport.icao)
        assertEquals("Roma", nearest.airport.city)
        assertEquals(13.779299, nearest.distanceKm, 0.000001)
        assertEquals(7.440229, nearest.distanceNm, 0.000001)
    }

    @Test
    fun calculatesHaversineDistanceKm() {
        val distanceKm = repository().calculateDistanceKm(
            AirportPoint(41.9, 12.5),
            AirportPoint(41.799444, 12.597222)
        )

        assertEquals(13.779299, distanceKm, 0.000001)
    }

    @Test
    fun convertsKmToNmUsingLegacyFactor() {
        val nm = repository().calculateDistanceNm(10.0)

        assertEquals(5.39957, nm, 0.000001)
    }

    @Test
    fun prefersTakeoffOverAreaCenter() {
        val nearest = repository().findNearestAirport(
            takeoff = AirportPoint(45.4642, 9.19),
            area = listOf(
                AirportPoint(41.89, 12.49),
                AirportPoint(41.91, 12.51)
            )
        )

        assertEquals("LIML", nearest?.airport?.icao)
    }

    @Test
    fun fallsBackToAreaCenterWhenTakeoffIsMissing() {
        val nearest = repository().findNearestAirport(
            takeoff = null,
            area = listOf(
                AirportPoint(44.49, 11.32),
                AirportPoint(44.50, 11.36)
            )
        )

        assertEquals("LIPE", nearest?.airport?.icao)
    }

    @Test
    fun areaCenterUsesSimpleLatLonAverageLikeWeb() {
        val center = repository().calculateAreaCenter(
            listOf(
                AirportPoint(44.0, 11.0),
                AirportPoint(46.0, 13.0),
                AirportPoint(45.0, 12.0)
            )
        )

        assertEquals(45.0, center?.lat ?: 0.0, 0.000001)
        assertEquals(12.0, center?.lon ?: 0.0, 0.000001)
    }

    @Test
    fun sampleCoordinatesMatchLegacyAirportDataset() {
        val samples = listOf(
            AirportPoint(45.4642, 9.19) to "LIML",
            AirportPoint(44.4949, 11.3426) to "LIPE",
            AirportPoint(39.2238, 9.1217) to "LIEE",
            AirportPoint(38.1157, 13.3615) to "LICJ"
        )

        samples.forEach { (point, expectedIcao) ->
            assertEquals(expectedIcao, repository().findNearestAirport(point)?.airport?.icao)
        }
    }

    @Test
    fun usesOnlyLocalJsonAndDoesNotNeedNetwork() {
        val repository = AirportRepository { airportAssetJson() }

        assertEquals("LIRA", repository.findNearestAirport(AirportPoint(41.9, 12.5))?.airport?.icao)
    }

    private fun repository(): AirportRepository =
        AirportRepository { airportAssetJson() }

    private fun airportAssetJson(): String {
        val candidates = listOf(
            File("app/src/main/assets/icao-it.json"),
            File("src/main/assets/icao-it.json")
        )
        return candidates.first { it.exists() }.readText(Charsets.UTF_8)
    }
}
