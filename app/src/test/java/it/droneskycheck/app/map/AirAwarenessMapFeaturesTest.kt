package it.droneskycheck.app.map

import it.droneskycheck.app.data.traffic.TrafficAwarenessDefaults
import it.droneskycheck.app.data.traffic.TrafficRelevance
import it.droneskycheck.app.data.traffic.TrafficRelevanceThresholds
import it.droneskycheck.app.ui.map.MapPoint
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
    fun activeAirAwarenessUsesTheRealAlertThresholds() {
        val center = MapPoint(41.9028, 12.4964)
        val features = airAwarenessAlertRingsFeatureCollection(center, enabled = true)
            .features().orEmpty()
        val expected = listOf(
            Triple(
                TrafficRelevanceThresholds.DroneAttentionDistanceM,
                TrafficRelevance.ATTENTION,
                "drone-proximity-attention"
            ),
            Triple(
                TrafficRelevanceThresholds.AttentionCpaDistanceM,
                TrafficRelevance.ATTENTION,
                "cpa-attention"
            ),
            Triple(
                TrafficRelevanceThresholds.MonitorDistanceM,
                TrafficRelevance.MONITOR,
                "monitor"
            )
        )

        assertEquals(expected.size, features.size)
        expected.zip(features).forEach { (ring, feature) ->
            val polygon = feature.geometry() as Polygon
            val coordinates = polygon.coordinates().single()
            assertEquals(97, coordinates.size)
            assertEquals(ring.first, distanceMeters(center.lat, center.lon, coordinates.first()), 1.0)
            assertEquals(ring.second.name, feature.getStringProperty(AirAwarenessAlertRingProperties.Relevance))
            assertEquals(ring.third, feature.getStringProperty(AirAwarenessAlertRingProperties.Id))
        }
    }

    @Test
    fun inactiveAirAwarenessClearsAlertRings() {
        val center = MapPoint(41.9028, 12.4964)

        assertTrue(airAwarenessAlertRingsFeatureCollection(center, enabled = false).features().orEmpty().isEmpty())
        assertTrue(airAwarenessAlertRingsFeatureCollection(null, enabled = true).features().orEmpty().isEmpty())
    }

    @Test
    fun acquisitionBoundaryKeepsTheConfiguredTrafficRadius() {
        val center = MapPoint(41.9028, 12.4964)
        val polygon = trafficRadiusFeatureCollection(center, TrafficAwarenessDefaults.DefaultRadiusKm)
            .features().orEmpty().single().geometry() as Polygon

        assertEquals(20.0, TrafficAwarenessDefaults.DefaultRadiusKm, 0.0)
        assertEquals(
            20_000.0,
            distanceMeters(center.lat, center.lon, polygon.coordinates().single().first()),
            1.0
        )
    }

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
