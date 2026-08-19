package it.droneskycheck.app.map

import it.droneskycheck.app.data.traffic.TrafficAssessment
import it.droneskycheck.app.data.traffic.TrafficRelevance
import it.droneskycheck.app.data.traffic.TrafficTarget

data class TrafficRadarLabelTarget(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val trafficTypeLabel: String,
    val altitudeLabel: String?,
    val speedLabel: String?,
    val relevance: TrafficRelevance
) {
    val firstLine: String =
        listOfNotNull(trafficTypeLabel, altitudeLabel)
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
            ?: trafficTypeLabel

    val displayLines: List<String> =
        listOfNotNull(firstLine, speedLabel)
            .filter { it.isNotBlank() }
}

data class TrafficRadarLabelBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun intersects(other: TrafficRadarLabelBounds): Boolean =
        left < other.right &&
            right > other.left &&
            top < other.bottom &&
            bottom > other.top
}

data class TrafficRadarLabelPlacement(
    val id: String,
    val relevance: TrafficRelevance,
    val bounds: TrafficRadarLabelBounds
)

fun List<TrafficTarget>.toTrafficRadarLabelTargets(
    assessments: Map<String, TrafficAssessment>
): List<TrafficRadarLabelTarget> =
    mapNotNull { target ->
        if (!target.position.lat.isFinite() || !target.position.lon.isFinite()) return@mapNotNull null
        TrafficRadarLabelTarget(
            id = target.id,
            latitude = target.position.lat,
            longitude = target.position.lon,
            trafficTypeLabel = target.mapTrafficTypeLabel(),
            altitudeLabel = target.mapRadarOverlayAltitudeLabel(),
            speedLabel = target.mapSpeedLabel(),
            relevance = assessments[target.id]?.relevance ?: TrafficRelevance.INFORMATION
        ).takeIf { it.displayLines.isNotEmpty() }
    }

fun sortTrafficRadarLabelTargets(targets: List<TrafficRadarLabelTarget>): List<TrafficRadarLabelTarget> =
    targets.sortedWith(
        compareByDescending<TrafficRadarLabelTarget> { it.relevance.radarLabelPriority() }
            .thenBy { it.id }
    )

fun shouldDrawTrafficRadarLabel(
    accepted: List<TrafficRadarLabelPlacement>,
    candidate: TrafficRadarLabelPlacement
): Boolean =
    candidate.relevance != TrafficRelevance.INFORMATION ||
        accepted.none { it.bounds.intersects(candidate.bounds) }

private fun TrafficTarget.mapRadarOverlayAltitudeLabel(): String? =
    mapAltitudeLabel()
        ?.takeUnless { label ->
            label == "0 m ALT" &&
                altitude.aglM?.isFinite() != true &&
                altitude.mslM?.isFinite() != true &&
                altitude.geoM?.isFinite() != true &&
                altitude.baroM?.isFinite() != true
        }

private fun TrafficRelevance.radarLabelPriority(): Int =
    when (this) {
        TrafficRelevance.INFORMATION -> 0
        TrafficRelevance.MONITOR -> 1
        TrafficRelevance.ATTENTION -> 2
    }
