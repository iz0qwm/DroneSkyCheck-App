package it.droneskycheck.app.data.traffic

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class TrafficOperationCenter(
    val lat: Double,
    val lon: Double
)

data class TrafficAssessment(
    val relevance: TrafficRelevance,
    val currentDistanceM: Double?,
    val converging: Boolean?,
    val relativeBearingDeg: Double?,
    val trackDifferenceDeg: Double?,
    val cpaDistanceM: Double?,
    val timeToCpaSec: Double?,
    val calculationConfidence: TrafficCalculationConfidence,
    val reasons: List<TrafficRelevanceReason>
)

enum class TrafficRelevance {
    INFORMATION,
    MONITOR,
    ATTENTION
}

enum class TrafficCalculationConfidence {
    HIGH,
    PARTIAL,
    INSUFFICIENT
}

enum class TrafficRelevanceReason {
    WITHIN_MONITOR_DISTANCE,
    CONVERGING,
    CPA_WITHIN_ATTENTION_DISTANCE,
    CPA_WITHIN_ATTENTION_TIME,
    CPA_WITHIN_LOOKAHEAD,
    INSUFFICIENT_MOTION_DATA,
    STALE_MOTION_DATA,
    MISSING_TIME_DATA,
    HEADING_FALLBACK,
    DIVERGING
}

object TrafficRelevanceThresholds {
    const val MonitorDistanceM = 10_000.0
    const val AttentionCpaDistanceM = 3_000.0
    const val AttentionTimeToCpaSec = 180.0
    const val CpaLookaheadSec = 300.0
    const val MinimumGroundSpeedMps = 1.0
    const val FreshMotionMaxAgeSec = 60.0
}

class TrafficRelevanceEngine(
    private val thresholds: TrafficRelevanceThresholds = TrafficRelevanceThresholds
) {
    fun assessTraffic(
        target: TrafficTarget,
        operationCenter: TrafficOperationCenter,
        nowMillis: Long
    ): TrafficAssessment {
        if (!operationCenter.lat.isFinite() || !operationCenter.lon.isFinite()) {
            return insufficient()
        }

        val relativeVector = relativeVectorMeters(operationCenter, target.position)
        if (relativeVector == null) return insufficient()

        val currentDistanceM = target.relative.distanceM
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: hypot(relativeVector.eastM, relativeVector.northM)
        val relativeBearingDeg = target.relative.bearingDeg
            ?.takeIf { it.isFinite() }
            ?.normalizeDegrees()
            ?: bearingFromVector(relativeVector.eastM, relativeVector.northM)

        val reasons = mutableListOf<TrafficRelevanceReason>()
        if (currentDistanceM <= thresholds.MonitorDistanceM) {
            reasons += TrafficRelevanceReason.WITHIN_MONITOR_DISTANCE
        }

        val ageSec = target.motionAgeSec(nowMillis)
        val stale = ageSec != null && ageSec > thresholds.FreshMotionMaxAgeSec
        if (stale) reasons += TrafficRelevanceReason.STALE_MOTION_DATA
        if (ageSec == null) reasons += TrafficRelevanceReason.MISSING_TIME_DATA

        val motion = target.usableMotion()
        if (motion == null || stale) {
            if (motion == null) reasons += TrafficRelevanceReason.INSUFFICIENT_MOTION_DATA
            return TrafficAssessment(
                relevance = if (currentDistanceM <= thresholds.MonitorDistanceM) {
                    TrafficRelevance.MONITOR
                } else {
                    TrafficRelevance.INFORMATION
                },
                currentDistanceM = currentDistanceM,
                converging = null,
                relativeBearingDeg = relativeBearingDeg,
                trackDifferenceDeg = null,
                cpaDistanceM = null,
                timeToCpaSec = null,
                calculationConfidence = if (motion == null) {
                    TrafficCalculationConfidence.INSUFFICIENT
                } else {
                    TrafficCalculationConfidence.PARTIAL
                },
                reasons = reasons.distinct()
            )
        }

        if (motion.usesHeadingFallback) reasons += TrafficRelevanceReason.HEADING_FALLBACK

        val velocity = velocityVectorMps(motion.speedMps, motion.directionDeg)
        val dot = relativeVector.eastM * velocity.eastM + relativeVector.northM * velocity.northM
        val converging = dot < 0.0
        reasons += if (converging) TrafficRelevanceReason.CONVERGING else TrafficRelevanceReason.DIVERGING

        val timeToCpaSec = if (converging) {
            -dot / (motion.speedMps * motion.speedMps)
        } else {
            null
        }?.takeIf { it > 0.0 && it.isFinite() }

        val cpaDistanceM = timeToCpaSec?.let { time ->
            hypot(
                relativeVector.eastM + velocity.eastM * time,
                relativeVector.northM + velocity.northM * time
            )
        }

        if (timeToCpaSec != null && timeToCpaSec <= thresholds.CpaLookaheadSec) {
            reasons += TrafficRelevanceReason.CPA_WITHIN_LOOKAHEAD
        }
        if (cpaDistanceM != null && cpaDistanceM <= thresholds.AttentionCpaDistanceM) {
            reasons += TrafficRelevanceReason.CPA_WITHIN_ATTENTION_DISTANCE
        }
        if (timeToCpaSec != null && timeToCpaSec <= thresholds.AttentionTimeToCpaSec) {
            reasons += TrafficRelevanceReason.CPA_WITHIN_ATTENTION_TIME
        }

        val attention = cpaDistanceM?.let { cpa ->
            timeToCpaSec?.let { time ->
                cpa <= thresholds.AttentionCpaDistanceM &&
                    time <= thresholds.AttentionTimeToCpaSec
            }
        } == true
        val monitor = currentDistanceM <= thresholds.MonitorDistanceM ||
            (converging && timeToCpaSec != null && timeToCpaSec <= thresholds.CpaLookaheadSec)

        return TrafficAssessment(
            relevance = when {
                attention -> TrafficRelevance.ATTENTION
                monitor -> TrafficRelevance.MONITOR
                else -> TrafficRelevance.INFORMATION
            },
            currentDistanceM = currentDistanceM,
            converging = converging,
            relativeBearingDeg = relativeBearingDeg,
            trackDifferenceDeg = trackDifferenceDeg(motion.directionDeg, relativeBearingDeg),
            cpaDistanceM = cpaDistanceM,
            timeToCpaSec = timeToCpaSec,
            calculationConfidence = when {
                motion.usesHeadingFallback || ageSec == null -> TrafficCalculationConfidence.PARTIAL
                else -> TrafficCalculationConfidence.HIGH
            },
            reasons = reasons.distinct()
        )
    }

    fun assessTrafficBatch(
        targets: List<TrafficTarget>,
        operationCenter: TrafficOperationCenter,
        nowMillis: Long
    ): Map<String, TrafficAssessment> =
        targets.associate { target ->
            target.id to assessTraffic(target, operationCenter, nowMillis)
        }

    private fun insufficient(): TrafficAssessment =
        TrafficAssessment(
            relevance = TrafficRelevance.INFORMATION,
            currentDistanceM = null,
            converging = null,
            relativeBearingDeg = null,
            trackDifferenceDeg = null,
            cpaDistanceM = null,
            timeToCpaSec = null,
            calculationConfidence = TrafficCalculationConfidence.INSUFFICIENT,
            reasons = listOf(TrafficRelevanceReason.INSUFFICIENT_MOTION_DATA)
        )
}

private data class RelativeVector(
    val eastM: Double,
    val northM: Double
)

private data class UsableMotion(
    val speedMps: Double,
    val directionDeg: Double,
    val usesHeadingFallback: Boolean
)

private fun relativeVectorMeters(center: TrafficOperationCenter, position: TrafficPosition): RelativeVector? {
    if (!position.lat.isFinite() || !position.lon.isFinite()) return null

    val lat1 = center.lat.toRadians()
    val lat2 = position.lat.toRadians()
    val dLat = lat2 - lat1
    val dLon = (position.lon - center.lon).toRadians()
    val avgLat = (lat1 + lat2) / 2.0

    return RelativeVector(
        eastM = EarthRadiusM * dLon * cos(avgLat),
        northM = EarthRadiusM * dLat
    )
}

private fun velocityVectorMps(speedMps: Double, trackDeg: Double): RelativeVector {
    val radians = trackDeg.toRadians()
    return RelativeVector(
        eastM = speedMps * sin(radians),
        northM = speedMps * cos(radians)
    )
}

private fun TrafficTarget.usableMotion(): UsableMotion? {
    val speed = motion.groundSpeedMps?.takeIf {
        it.isFinite() && it >= TrafficRelevanceThresholds.MinimumGroundSpeedMps
    } ?: return null
    motion.trackDeg?.takeIf { it.isFinite() }?.let { track ->
        return UsableMotion(speed, track.normalizeDegrees(), usesHeadingFallback = false)
    }
    motion.headingDeg?.takeIf { it.isFinite() }?.let { heading ->
        return UsableMotion(speed, heading.normalizeDegrees(), usesHeadingFallback = true)
    }
    return null
}

private fun TrafficTarget.motionAgeSec(nowMillis: Long): Double? {
    time.ageSec?.takeIf { it.isFinite() && it >= 0.0 }?.let { return it }
    val timestamp = time.timestamp?.takeIf { it >= 0L } ?: return null
    return ((nowMillis - timestamp).coerceAtLeast(0L)) / 1000.0
}

private fun trackDifferenceDeg(trackDeg: Double, relativeBearingDeg: Double): Double {
    val bearingFromTargetToCenter = (relativeBearingDeg + 180.0).normalizeDegrees()
    return angularDifferenceDeg(trackDeg, bearingFromTargetToCenter)
}

private fun angularDifferenceDeg(left: Double, right: Double): Double {
    val diff = ((left - right + 540.0) % 360.0) - 180.0
    return kotlin.math.abs(diff)
}

private fun bearingFromVector(eastM: Double, northM: Double): Double =
    atan2(eastM, northM).toDegrees().normalizeDegrees()

private fun Double.normalizeDegrees(): Double =
    ((this % 360.0) + 360.0) % 360.0

private fun Double.toRadians(): Double = this * PI / 180.0

private fun Double.toDegrees(): Double = this * 180.0 / PI

private const val EarthRadiusM = 6_371_000.0
