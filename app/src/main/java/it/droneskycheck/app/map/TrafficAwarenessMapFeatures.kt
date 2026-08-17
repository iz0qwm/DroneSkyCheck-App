package it.droneskycheck.app.map

import it.droneskycheck.app.data.DscLogger
import it.droneskycheck.app.data.traffic.TrafficAwarenessDefaults
import it.droneskycheck.app.data.traffic.TrafficAwarenessLogTag
import it.droneskycheck.app.data.traffic.TrafficAssessment
import it.droneskycheck.app.data.traffic.TrafficFeedType
import it.droneskycheck.app.data.traffic.TrafficPosition
import it.droneskycheck.app.data.traffic.TrafficRelevance
import it.droneskycheck.app.data.traffic.TrafficTarget
import it.droneskycheck.app.data.traffic.coarseTraffic
import it.droneskycheck.app.data.traffic.trafficFeedType
import it.droneskycheck.app.data.traffic.trafficTargetKind
import it.droneskycheck.app.data.traffic.TrafficTargetKind
import it.droneskycheck.app.ui.map.MapPoint
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object TrafficAwarenessMapProperties {
    const val TargetId = "targetId"
    const val Callsign = "callsign"
    const val Provider = "provider"
    const val Source = "source"
    const val RotationDeg = "rotationDeg"
    const val HasRotation = "hasRotation"
    const val Relevance = "relevance"
    const val AltitudeBand = "altitudeBand"
    const val TargetKind = "targetKind"
    const val FeedType = "feedType"
    const val IconImage = "iconImage"
    const val AltitudeLabel = "altitudeLabel"
}

enum class TrafficAltitudeBand {
    VERY_LOW,
    LOW,
    HIGH,
    UNKNOWN
}

fun trafficTargetsFeatureCollection(
    targets: List<TrafficTarget>,
    assessments: Map<String, TrafficAssessment> = emptyMap()
): FeatureCollection {
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
            addStringProperty(
                TrafficAwarenessMapProperties.Relevance,
                (assessments[target.id]?.relevance ?: TrafficRelevance.INFORMATION).name
            )
            addStringProperty(TrafficAwarenessMapProperties.AltitudeBand, target.trafficAltitudeBand().name)
            addStringProperty(TrafficAwarenessMapProperties.TargetKind, target.trafficTargetKind().name)
            addStringProperty(TrafficAwarenessMapProperties.FeedType, target.trafficFeedType().name)
            addStringProperty(TrafficAwarenessMapProperties.IconImage, target.trafficIconImageId())
            target.mapAltitudeLabel()?.let {
                addStringProperty(TrafficAwarenessMapProperties.AltitudeLabel, it)
            }
        }.also {
            DscLogger.trace(
                TrafficAwarenessLogTag,
                "feature id=${target.id} lat=${target.position.lat.coarseTraffic(4)} " +
                    "lon=${target.position.lon.coarseTraffic(4)} rotation=${target.mapRotationDeg().coarseTraffic(2)}"
            )
        }
    }
    // Paused noisy traffic GeoJSON diagnostics during field testing.
    // DscLogger.trace(
    //     TrafficAwarenessLogTag,
    //     "GeoJSON build inputTargets=${targets.size} features=${features.size}"
    // )
    return FeatureCollection.fromFeatures(features)
}

fun trafficDirectionVectorFeatureCollection(
    targets: List<TrafficTarget>,
    projectionSeconds: Double = TRAFFIC_VECTOR_PROJECTION_SECONDS
): FeatureCollection {
    val features = targets.mapNotNull { target ->
        val futurePosition = target.projectedFuturePosition(projectionSeconds) ?: return@mapNotNull null
        Feature.fromGeometry(
            org.maplibre.geojson.LineString.fromLngLats(
                listOf(
                    Point.fromLngLat(target.position.lon, target.position.lat),
                    Point.fromLngLat(futurePosition.lon, futurePosition.lat)
                )
            )
        ).apply {
            addStringProperty(TrafficAwarenessMapProperties.TargetId, target.id)
            addStringProperty(TrafficAwarenessMapProperties.Relevance, TrafficRelevance.INFORMATION.name)
            addStringProperty(TrafficAwarenessMapProperties.AltitudeBand, target.trafficAltitudeBand().name)
            addStringProperty(TrafficAwarenessMapProperties.FeedType, target.trafficFeedType().name)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

fun trafficAircraftGlyphFeatureCollection(targets: List<TrafficTarget>): FeatureCollection {
    val features = targets.flatMap { target ->
        if (!target.position.lat.isFinite() || !target.position.lon.isFinite()) {
            return@flatMap emptyList()
        }
        val bearingDeg = target.mapRotationDeg()
        val segments = listOf(
            TrafficGlyphSegment(TrafficGlyphPoint(0.0, 220.0), TrafficGlyphPoint(0.0, -150.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-165.0, 20.0), TrafficGlyphPoint(165.0, 20.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-88.0, -125.0), TrafficGlyphPoint(88.0, -125.0))
        )
        segments.map { segment ->
            Feature.fromGeometry(
                LineString.fromLngLats(
                    listOf(
                        target.position.offsetByGlyphPoint(segment.start, bearingDeg),
                        target.position.offsetByGlyphPoint(segment.end, bearingDeg)
                    )
                )
            ).apply {
                addStringProperty(TrafficAwarenessMapProperties.TargetId, target.id)
                addStringProperty(TrafficAwarenessMapProperties.AltitudeBand, target.trafficAltitudeBand().name)
                addStringProperty(TrafficAwarenessMapProperties.FeedType, target.trafficFeedType().name)
                addStringProperty(TrafficAwarenessMapProperties.IconImage, target.trafficIconImageId())
            }
        }
    }
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

fun TrafficTarget.trafficAltitudeBand(): TrafficAltitudeBand =
    trafficAltitudeBandForAgl(altitude.aglM)

fun TrafficTarget.mapAltitudeLabel(): String? =
    altitude.aglM
        ?.takeIf { it.isFinite() }
        ?.roundToInt()
        ?.let { "$it m" }

fun TrafficTarget.trafficIconImageId(): String {
    val band = trafficAltitudeBand()
    return when (trafficFeedType()) {
        TrafficFeedType.ADSB -> when (trafficTargetKind()) {
            TrafficTargetKind.DRONE -> TrafficMapIconIds.Drone
            TrafficTargetKind.HELICOPTER -> TrafficMapIconIds.helicopter(band)
            TrafficTargetKind.AIRCRAFT -> TrafficMapIconIds.aircraft(band)
        }
        TrafficFeedType.FANET -> TrafficMapIconIds.feed(TrafficFeedType.FANET, band)
        TrafficFeedType.FLARM -> TrafficMapIconIds.feed(TrafficFeedType.FLARM, band)
        TrafficFeedType.FREEFLIGHT -> TrafficMapIconIds.feed(TrafficFeedType.FREEFLIGHT, band)
        TrafficFeedType.UNKNOWN -> when (trafficTargetKind()) {
            TrafficTargetKind.DRONE -> TrafficMapIconIds.Drone
            TrafficTargetKind.HELICOPTER -> TrafficMapIconIds.helicopter(band)
            TrafficTargetKind.AIRCRAFT -> TrafficMapIconIds.aircraft(band)
        }
    }
}

fun TrafficTarget.projectedFuturePosition(projectionSeconds: Double): MapPoint? {
    val speedMps = motion.groundSpeedMps?.takeIf { it.isFinite() && it >= MIN_TRAFFIC_VECTOR_SPEED_MPS }
        ?: return null
    val bearingDeg = motion.trackDeg?.takeIf { it.isFinite() }
        ?: motion.headingDeg?.takeIf { it.isFinite() }
        ?: return null
    if (!position.lat.isFinite() || !position.lon.isFinite()) return null

    val distanceM = (speedMps * projectionSeconds)
        .coerceIn(MIN_TRAFFIC_VECTOR_DISTANCE_M, MAX_TRAFFIC_VECTOR_DISTANCE_M)
    val angularDistance = distanceM / (EARTH_RADIUS_KM * 1_000.0)
    val bearing = bearingDeg.toRadians()
    val lat1 = position.lat.toRadians()
    val lon1 = position.lon.toRadians()
    val lat2 = asin(
        sin(lat1) * cos(angularDistance) +
            cos(lat1) * sin(angularDistance) * cos(bearing)
    )
    val lon2 = lon1 + atan2(
        sin(bearing) * sin(angularDistance) * cos(lat1),
        cos(angularDistance) - sin(lat1) * sin(lat2)
    )

    return MapPoint(
        lat = lat2.toDegrees().coerceIn(-90.0, 90.0),
        lon = normalizeLongitude(lon2.toDegrees())
    )
}

fun trafficAltitudeBandForAgl(aglM: Double?): TrafficAltitudeBand =
    when {
        aglM?.isFinite() != true -> TrafficAltitudeBand.UNKNOWN
        aglM < 300.0 -> TrafficAltitudeBand.VERY_LOW
        aglM < 1_000.0 -> TrafficAltitudeBand.LOW
        else -> TrafficAltitudeBand.HIGH
    }

private fun Double.toRadians(): Double = this * PI / 180.0

private fun Double.toDegrees(): Double = this * 180.0 / PI

private fun normalizeLongitude(lon: Double): Double =
    ((lon + 540.0) % 360.0) - 180.0

private data class TrafficGlyphPoint(
    val rightMeters: Double,
    val forwardMeters: Double
)

private data class TrafficGlyphSegment(
    val start: TrafficGlyphPoint,
    val end: TrafficGlyphPoint
)

private fun TrafficPosition.offsetByGlyphPoint(point: TrafficGlyphPoint, bearingDeg: Double): Point {
    val bearing = bearingDeg.toRadians()
    val eastMeters = point.rightMeters * cos(bearing) + point.forwardMeters * sin(bearing)
    val northMeters = point.forwardMeters * cos(bearing) - point.rightMeters * sin(bearing)
    val lat = lat + northMeters / 111_320.0
    val lon = lon + eastMeters / (111_320.0 * cos(this.lat.toRadians()).coerceAtLeast(0.2))
    return Point.fromLngLat(normalizeLongitude(lon), lat.coerceIn(-90.0, 90.0))
}

object TrafficMapIconIds {
    const val Drone = "dsc-traffic-awareness-drone"

    fun aircraft(band: TrafficAltitudeBand): String =
        "dsc-traffic-awareness-aircraft-${band.iconSuffix()}"

    fun helicopter(band: TrafficAltitudeBand): String =
        "dsc-traffic-awareness-helicopter-${band.iconSuffix()}"

    fun feed(type: TrafficFeedType, band: TrafficAltitudeBand): String =
        "dsc-traffic-awareness-${type.name.lowercase()}-${band.iconSuffix()}"
}

private fun TrafficAltitudeBand.iconSuffix(): String =
    when (this) {
        TrafficAltitudeBand.VERY_LOW -> "very-low"
        TrafficAltitudeBand.LOW -> "low"
        TrafficAltitudeBand.HIGH -> "high"
        TrafficAltitudeBand.UNKNOWN -> "unknown"
    }

private const val EARTH_RADIUS_KM = 6371.0
private const val TRAFFIC_VECTOR_PROJECTION_SECONDS = 45.0
private const val MIN_TRAFFIC_VECTOR_SPEED_MPS = 1.0
private const val MIN_TRAFFIC_VECTOR_DISTANCE_M = 25.0
private const val MAX_TRAFFIC_VECTOR_DISTANCE_M = 3_000.0
