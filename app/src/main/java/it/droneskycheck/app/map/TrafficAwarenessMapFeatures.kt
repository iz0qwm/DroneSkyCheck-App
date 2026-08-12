package it.droneskycheck.app.map

import it.droneskycheck.app.data.DscLogger
import it.droneskycheck.app.data.traffic.TrafficAwarenessDefaults
import it.droneskycheck.app.data.traffic.TrafficAwarenessLogTag
import it.droneskycheck.app.data.traffic.TrafficTarget
import it.droneskycheck.app.data.traffic.coarseTraffic
import it.droneskycheck.app.ui.map.MapPoint
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object TrafficAwarenessMapProperties {
    const val TargetId = "targetId"
    const val Callsign = "callsign"
    const val Provider = "provider"
    const val Source = "source"
    const val RotationDeg = "rotationDeg"
    const val HasRotation = "hasRotation"
}

fun trafficTargetsFeatureCollection(targets: List<TrafficTarget>): FeatureCollection {
    val features = targets.mapNotNull { target ->
        if (!target.position.lat.isFinite() || !target.position.lon.isFinite()) {
            DscLogger.warn(TrafficAwarenessLogTag, "target skipped reason=invalid_geojson_position id=${target.id}")
            return@mapNotNull null
        }
        Feature.fromGeometry(
            Point.fromLngLat(target.position.lon, target.position.lat)
        ).apply {
            addStringProperty(TrafficAwarenessMapProperties.TargetId, target.id)
            target.displayName().let { addStringProperty(TrafficAwarenessMapProperties.Callsign, it) }
            target.provider?.let { addStringProperty(TrafficAwarenessMapProperties.Provider, it) }
            target.source?.let { addStringProperty(TrafficAwarenessMapProperties.Source, it) }
            addNumberProperty(TrafficAwarenessMapProperties.RotationDeg, target.mapRotationDeg())
            addBooleanProperty(TrafficAwarenessMapProperties.HasRotation, target.hasMapRotation())
        }.also {
            DscLogger.debug(
                TrafficAwarenessLogTag,
                "feature id=${target.id} lat=${target.position.lat.coarseTraffic(4)} " +
                    "lon=${target.position.lon.coarseTraffic(4)} rotation=${target.mapRotationDeg().coarseTraffic(2)}"
            )
        }
    }
    DscLogger.debug(
        TrafficAwarenessLogTag,
        "GeoJSON build inputTargets=${targets.size} features=${features.size}"
    )
    return FeatureCollection.fromFeatures(features)
}

fun trafficRadiusFeatureCollection(
    center: MapPoint?,
    radiusKm: Double = TrafficAwarenessDefaults.DefaultRadiusKm,
    steps: Int = 96
): FeatureCollection {
    if (center == null || !center.lat.isFinite() || !center.lon.isFinite() || radiusKm <= 0.0) {
        return emptyTrafficFeatureCollection()
    }

    val radiusRadians = radiusKm / EARTH_RADIUS_KM
    val latRadians = center.lat.toRadians()
    val lonRadians = center.lon.toRadians()
    val ring = (0..steps).map { index ->
        val bearing = (2.0 * PI * index / steps)
        val lat = asin(
            sin(latRadians) * cos(radiusRadians) +
                cos(latRadians) * sin(radiusRadians) * cos(bearing)
        )
        val lon = lonRadians + atan2(
            sin(bearing) * sin(radiusRadians) * cos(latRadians),
            cos(radiusRadians) - sin(latRadians) * sin(lat)
        )
        Point.fromLngLat(lon.toDegrees(), lat.toDegrees())
    }

    return FeatureCollection.fromFeature(
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
    )
}

fun emptyTrafficFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(emptyList())

fun TrafficTarget.displayName(): String =
    identifiers.callsign
        ?: identifiers.registration
        ?: identifiers.icao24
        ?: identifiers.sourceId
        ?: "Traffico rilevato"

fun TrafficTarget.mapRotationDeg(): Double =
    motion.trackDeg?.takeIf { it.isFinite() }
        ?: motion.headingDeg?.takeIf { it.isFinite() }
        ?: 0.0

fun TrafficTarget.hasMapRotation(): Boolean =
    motion.trackDeg?.isFinite() == true || motion.headingDeg?.isFinite() == true

private fun Double.toRadians(): Double = this * PI / 180.0

private fun Double.toDegrees(): Double = this * 180.0 / PI

private const val EARTH_RADIUS_KM = 6371.0
