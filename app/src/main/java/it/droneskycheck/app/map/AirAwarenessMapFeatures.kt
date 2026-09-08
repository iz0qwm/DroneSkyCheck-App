package it.droneskycheck.app.map

import it.droneskycheck.app.data.traffic.TrafficRelevance
import it.droneskycheck.app.data.traffic.TrafficRelevanceThresholds
import it.droneskycheck.app.ui.map.MapPoint
import org.maplibre.geojson.FeatureCollection

data class AirAwarenessAlertRingSpec(
    val id: String,
    val radiusMeters: Double,
    val relevance: TrafficRelevance
)

object AirAwarenessAlertRingProperties {
    const val Id = "ringId"
    const val Relevance = "relevance"
    const val RadiusMeters = "radiusMeters"
}

val AirAwarenessAlertRingSpecs = listOf(
    AirAwarenessAlertRingSpec(
        id = "drone-proximity-attention",
        radiusMeters = TrafficRelevanceThresholds.DroneAttentionDistanceM,
        relevance = TrafficRelevance.ATTENTION
    ),
    AirAwarenessAlertRingSpec(
        id = "cpa-attention",
        radiusMeters = TrafficRelevanceThresholds.AttentionCpaDistanceM,
        relevance = TrafficRelevance.ATTENTION
    ),
    AirAwarenessAlertRingSpec(
        id = "monitor",
        radiusMeters = TrafficRelevanceThresholds.MonitorDistanceM,
        relevance = TrafficRelevance.MONITOR
    )
)

fun airAwarenessAlertRingsFeatureCollection(
    center: MapPoint?,
    enabled: Boolean
): FeatureCollection {
    if (!enabled || center == null || !center.lat.isFinite() || !center.lon.isFinite()) {
        return emptyTrafficFeatureCollection()
    }

    val features = AirAwarenessAlertRingSpecs.flatMap { spec ->
        trafficRadiusFeatureCollection(
            center = center,
            radiusKm = spec.radiusMeters / METERS_PER_KILOMETER
        ).features().orEmpty().map { feature ->
            feature.apply {
                addStringProperty(AirAwarenessAlertRingProperties.Id, spec.id)
                addStringProperty(AirAwarenessAlertRingProperties.Relevance, spec.relevance.name)
                addNumberProperty(AirAwarenessAlertRingProperties.RadiusMeters, spec.radiusMeters)
            }
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private const val METERS_PER_KILOMETER = 1_000.0
