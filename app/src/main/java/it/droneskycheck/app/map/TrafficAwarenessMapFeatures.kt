package it.droneskycheck.app.map

import it.droneskycheck.app.data.DscLogger
import it.droneskycheck.app.data.traffic.TrafficAltitude
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
    const val AltitudeLabel = "altitudeLabel"
    const val TrafficTypeLabel = "trafficTypeLabel"
    const val SpeedLabel = "speedLabel"
    const val RadarLabel = "radarLabel"
    const val RadarLabelPriority = "radarLabelPriority"
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
            target.mapAltitudeLabel()?.let {
                addStringProperty(TrafficAwarenessMapProperties.AltitudeLabel, it)
            }
            addStringProperty(TrafficAwarenessMapProperties.TrafficTypeLabel, target.mapTrafficTypeLabel())
            target.mapSpeedLabel()?.let {
                addStringProperty(TrafficAwarenessMapProperties.SpeedLabel, it)
            }
            target.mapRadarLabel()?.let {
                addStringProperty(TrafficAwarenessMapProperties.RadarLabel, it)
            }
            addNumberProperty(
                TrafficAwarenessMapProperties.RadarLabelPriority,
                target.mapRadarLabelPriority(assessments[target.id]?.relevance ?: TrafficRelevance.INFORMATION)
            )
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
        val vector = target.projectedVectorFromGlyphNose(projectionSeconds) ?: return@mapNotNull null
        Feature.fromGeometry(
            org.maplibre.geojson.LineString.fromLngLats(
                listOf(
                    vector.start,
                    vector.end
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

fun trafficAircraftGlyphFeatureCollection(
    targets: List<TrafficTarget>,
    assessments: Map<String, TrafficAssessment> = emptyMap()
): FeatureCollection {
    val features = targets.flatMap { target ->
        if (!target.position.lat.isFinite() || !target.position.lon.isFinite()) {
            return@flatMap emptyList()
        }
        val bearingDeg = target.mapRotationDeg()
        target.trafficGlyphShape().segments.map { segment ->
            Feature.fromGeometry(
                LineString.fromLngLats(
                    listOf(
                        target.position.offsetByGlyphPoint(segment.start, bearingDeg),
                        target.position.offsetByGlyphPoint(segment.end, bearingDeg)
                    )
                )
            ).apply {
                addStringProperty(TrafficAwarenessMapProperties.TargetId, target.id)
                addStringProperty(
                    TrafficAwarenessMapProperties.Relevance,
                    (assessments[target.id]?.relevance ?: TrafficRelevance.INFORMATION).name
                )
                addStringProperty(TrafficAwarenessMapProperties.AltitudeBand, target.trafficAltitudeBand().name)
                addStringProperty(TrafficAwarenessMapProperties.FeedType, target.trafficFeedType().name)
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
    altitude.mapDisplayAltitudeLabel()

fun TrafficTarget.mapRadarLabel(): String? {
    val altitudeLabel = mapAltitudeLabel()
    val firstLine = listOfNotNull(mapTrafficTypeLabel(), altitudeLabel)
        .joinToString(" · ")
        .takeIf { it.isNotBlank() }
        ?: return null
    return listOfNotNull(firstLine, mapSpeedLabel()).joinToString("\n")
}

fun TrafficTarget.mapTrafficTypeLabel(): String =
    when (trafficFeedType()) {
        TrafficFeedType.FLARM -> "GLIDER"
        TrafficFeedType.FANET,
        TrafficFeedType.FREEFLIGHT -> "FREEFLIGHT"
        TrafficFeedType.ADSB -> when (trafficTargetKind()) {
            TrafficTargetKind.AIRCRAFT -> "AEREO"
            TrafficTargetKind.HELICOPTER -> "ELICOTTERO"
            TrafficTargetKind.DRONE -> "DRONE"
        }
        TrafficFeedType.UNKNOWN -> when (trafficTargetKind()) {
            TrafficTargetKind.HELICOPTER -> "ELICOTTERO"
            TrafficTargetKind.DRONE -> "DRONE"
            TrafficTargetKind.AIRCRAFT -> "TRAFFICO"
        }
    }

fun TrafficTarget.mapSpeedLabel(): String? {
    val knots = motion.groundSpeedMps
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.let { (it * METERS_PER_SECOND_TO_KNOTS).roundToInt() }
        ?.takeIf { it > 0 }
        ?: return null
    return "$knots kt"
}

private fun TrafficTarget.mapRadarLabelPriority(relevance: TrafficRelevance): Double =
    when (relevance) {
        TrafficRelevance.INFORMATION -> 0.0
        TrafficRelevance.MONITOR -> 10.0
        TrafficRelevance.ATTENTION -> 20.0
    }

private fun TrafficAltitude.mapDisplayAltitudeLabel(): String? =
    mapDisplayAltitude()
        ?.roundToInt()
        ?.let { "${it} m ${mapDisplayAltitudeReference()}" }

private fun TrafficAltitude.mapDisplayAltitude(): Double? =
    when {
        aglM?.isFinite() == true -> aglM
        mslM?.isFinite() == true -> mslM
        geoM?.isFinite() == true -> geoM
        baroM?.isFinite() == true -> baroM
        sourceM?.isFinite() == true -> sourceM
        else -> null
    }

private fun TrafficAltitude.mapDisplayAltitudeReference(): String =
    when {
        aglM?.isFinite() == true -> "AGL"
        mslM?.isFinite() == true -> "AMSL"
        geoM?.isFinite() == true -> "GEO"
        baroM?.isFinite() == true -> "BARO"
        sourceM?.isFinite() == true -> sourceReference.mapSourceAltitudeReferenceLabel()
        else -> "ALT"
    }

private fun String?.mapSourceAltitudeReferenceLabel(): String =
    when {
        this == null -> "ALT"
        contains("agl", ignoreCase = true) -> "AGL"
        contains("msl", ignoreCase = true) || contains("amsl", ignoreCase = true) -> "AMSL"
        contains("geo", ignoreCase = true) -> "GEO"
        contains("baro", ignoreCase = true) -> "BARO"
        else -> "ALT"
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

private data class TrafficGlyphShape(
    val segments: List<TrafficGlyphSegment>,
    val vectorStartForwardMeters: Double,
    val labelBehindMeters: Double
)

private data class TrafficVectorPoints(
    val start: Point,
    val end: Point
)

private fun TrafficTarget.projectedVectorFromGlyphNose(projectionSeconds: Double): TrafficVectorPoints? {
    val speedMps = motion.groundSpeedMps?.takeIf { it.isFinite() && it >= MIN_TRAFFIC_VECTOR_SPEED_MPS }
        ?: return null
    val bearingDeg = motion.trackDeg?.takeIf { it.isFinite() }
        ?: motion.headingDeg?.takeIf { it.isFinite() }
        ?: return null
    if (!position.lat.isFinite() || !position.lon.isFinite()) return null

    val shape = trafficGlyphShape()
    val distanceM = (speedMps * projectionSeconds)
        .coerceIn(MIN_TRAFFIC_VECTOR_DISTANCE_M, MAX_TRAFFIC_VECTOR_DISTANCE_M)
    val startForward = shape.vectorStartForwardMeters
    val endForward = max(distanceM, startForward + MIN_TRAFFIC_VECTOR_VISIBLE_LENGTH_M)
    return TrafficVectorPoints(
        start = position.offsetByGlyphPoint(TrafficGlyphPoint(0.0, startForward), bearingDeg),
        end = position.offsetByGlyphPoint(TrafficGlyphPoint(0.0, endForward), bearingDeg)
    )
}

private fun TrafficTarget.trafficGlyphShape(): TrafficGlyphShape =
    when (trafficFeedType()) {
        TrafficFeedType.FANET -> freeFlightGlyphShape()
        TrafficFeedType.FLARM -> gliderGlyphShape()
        TrafficFeedType.FREEFLIGHT -> freeFlightGlyphShape()
        TrafficFeedType.ADSB,
        TrafficFeedType.UNKNOWN -> when (trafficTargetKind()) {
            TrafficTargetKind.DRONE -> droneGlyphShape()
            TrafficTargetKind.HELICOPTER -> helicopterGlyphShape()
            TrafficTargetKind.AIRCRAFT -> aircraftGlyphShape()
        }
    }

private fun aircraftGlyphShape(): TrafficGlyphShape =
    TrafficGlyphShape(
        vectorStartForwardMeters = 290.0,
        labelBehindMeters = 405.0,
        segments = listOf(
            TrafficGlyphSegment(TrafficGlyphPoint(0.0, 285.0), TrafficGlyphPoint(0.0, -220.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-245.0, 35.0), TrafficGlyphPoint(245.0, 35.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-122.0, -172.0), TrafficGlyphPoint(122.0, -172.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-46.0, 206.0), TrafficGlyphPoint(0.0, 285.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(46.0, 206.0), TrafficGlyphPoint(0.0, 285.0))
        )
    )

private fun helicopterGlyphShape(): TrafficGlyphShape =
    TrafficGlyphShape(
        vectorStartForwardMeters = 235.0,
        labelBehindMeters = 385.0,
        segments = listOf(
            TrafficGlyphSegment(TrafficGlyphPoint(0.0, 225.0), TrafficGlyphPoint(0.0, -150.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-245.0, 82.0), TrafficGlyphPoint(245.0, 82.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-170.0, 128.0), TrafficGlyphPoint(170.0, 36.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(0.0, -130.0), TrafficGlyphPoint(0.0, -278.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-58.0, -278.0), TrafficGlyphPoint(58.0, -278.0))
        )
    )

private fun droneGlyphShape(): TrafficGlyphShape =
    TrafficGlyphShape(
        vectorStartForwardMeters = 195.0,
        labelBehindMeters = 350.0,
        segments = listOf(
            TrafficGlyphSegment(TrafficGlyphPoint(-165.0, 165.0), TrafficGlyphPoint(165.0, -165.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(165.0, 165.0), TrafficGlyphPoint(-165.0, -165.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-210.0, 165.0), TrafficGlyphPoint(-120.0, 165.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-165.0, 210.0), TrafficGlyphPoint(-165.0, 120.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(210.0, 165.0), TrafficGlyphPoint(120.0, 165.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(165.0, 210.0), TrafficGlyphPoint(165.0, 120.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-210.0, -165.0), TrafficGlyphPoint(-120.0, -165.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-165.0, -210.0), TrafficGlyphPoint(-165.0, -120.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(210.0, -165.0), TrafficGlyphPoint(120.0, -165.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(165.0, -210.0), TrafficGlyphPoint(165.0, -120.0))
        )
    )

private fun gliderGlyphShape(): TrafficGlyphShape =
    TrafficGlyphShape(
        vectorStartForwardMeters = 245.0,
        labelBehindMeters = 390.0,
        segments = listOf(
            TrafficGlyphSegment(TrafficGlyphPoint(-315.0, 24.0), TrafficGlyphPoint(315.0, 24.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(0.0, 240.0), TrafficGlyphPoint(0.0, -190.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-105.0, -144.0), TrafficGlyphPoint(105.0, -144.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-315.0, 24.0), TrafficGlyphPoint(-180.0, -18.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(315.0, 24.0), TrafficGlyphPoint(180.0, -18.0))
        )
    )

private fun freeFlightGlyphShape(): TrafficGlyphShape =
    TrafficGlyphShape(
        vectorStartForwardMeters = 230.0,
        labelBehindMeters = 370.0,
        segments = listOf(
            TrafficGlyphSegment(TrafficGlyphPoint(-285.0, 92.0), TrafficGlyphPoint(-148.0, 168.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-148.0, 168.0), TrafficGlyphPoint(0.0, 218.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(0.0, 218.0), TrafficGlyphPoint(148.0, 168.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(148.0, 168.0), TrafficGlyphPoint(285.0, 92.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-150.0, 86.0), TrafficGlyphPoint(0.0, -150.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(150.0, 86.0), TrafficGlyphPoint(0.0, -150.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(0.0, 25.0), TrafficGlyphPoint(0.0, -190.0)),
            TrafficGlyphSegment(TrafficGlyphPoint(-42.0, -82.0), TrafficGlyphPoint(42.0, -82.0))
        )
    )

private fun TrafficPosition.offsetByGlyphPoint(point: TrafficGlyphPoint, bearingDeg: Double): Point {
    val bearing = bearingDeg.toRadians()
    val eastMeters = point.rightMeters * cos(bearing) + point.forwardMeters * sin(bearing)
    val northMeters = point.forwardMeters * cos(bearing) - point.rightMeters * sin(bearing)
    val lat = lat + northMeters / 111_320.0
    val lon = lon + eastMeters / (111_320.0 * cos(this.lat.toRadians()).coerceAtLeast(0.2))
    return Point.fromLngLat(normalizeLongitude(lon), lat.coerceIn(-90.0, 90.0))
}

private const val EARTH_RADIUS_KM = 6371.0
private const val TRAFFIC_VECTOR_PROJECTION_SECONDS = 45.0
private const val MIN_TRAFFIC_VECTOR_SPEED_MPS = 1.0
private const val MIN_TRAFFIC_VECTOR_DISTANCE_M = 25.0
private const val MIN_TRAFFIC_VECTOR_VISIBLE_LENGTH_M = 150.0
private const val MAX_TRAFFIC_VECTOR_DISTANCE_M = 3_000.0
private const val METERS_PER_SECOND_TO_KNOTS = 1.9438444924406
