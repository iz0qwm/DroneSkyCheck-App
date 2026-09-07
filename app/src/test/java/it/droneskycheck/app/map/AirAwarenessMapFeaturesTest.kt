package it.droneskycheck.app.map

import it.droneskycheck.app.data.airawareness.PublishedDoa
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

class AirAwarenessMapFeaturesTest {
    @Test
    fun activeDoaCreatesGeodeticCircleWithPublishedRadius() {
        val doa = publishedDoa(radiusMeters = 300.0)
        val polygon = airAwarenessDoaFeatureCollection(doa)
            .features().orEmpty().single().geometry() as Polygon
        val ring = polygon.coordinates().single()

        assertEquals(97, ring.size)
        assertEquals(ring.first().longitude(), ring.last().longitude(), 0.0)
        assertEquals(ring.first().latitude(), ring.last().latitude(), 0.0)
        assertEquals(300.0, distanceMeters(doa.lat, doa.lon, ring.first()), 1.0)
    }

    @Test
    fun missingOrInvalidDoaClearsCircle() {
        assertTrue(airAwarenessDoaFeatureCollection(null).features().orEmpty().isEmpty())
        assertTrue(
            airAwarenessDoaFeatureCollection(publishedDoa(radiusMeters = 0.0))
                .features().orEmpty().isEmpty()
        )
    }

    private fun publishedDoa(radiusMeters: Double) = PublishedDoa(
        id = "doa-test",
        closeToken = "close-token",
        lat = 41.9028,
        lon = 12.4964,
        radiusMeters = radiusMeters,
        startTimeMillis = 1_000L,
        endTimeMillis = 3_601_000L
    )

    private fun distanceMeters(lat: Double, lon: Double, point: Point): Double {
        val lat1 = Math.toRadians(lat)
        val lat2 = Math.toRadians(point.latitude())
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(point.longitude() - lon)
        val a = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
        return 2.0 * EARTH_RADIUS_METERS * kotlin.math.asin(sqrt(a))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
