package it.droneskycheck.app.data.flight

import it.droneskycheck.app.data.LegalTimelineSegment
import it.droneskycheck.app.data.LegalTimelineState
import it.droneskycheck.app.data.drone.DroneDataCompleteness
import it.droneskycheck.app.data.drone.DroneOperationalAssessment
import it.droneskycheck.app.data.drone.DroneOperationalLevel
import it.droneskycheck.app.data.solar.SolarLightPhase
import it.droneskycheck.app.data.solar.SolarWindow
import it.droneskycheck.app.data.solar.TimeWindow
import it.droneskycheck.app.data.weather.WeatherAssessment
import it.droneskycheck.app.data.weather.WeatherConfidenceLevel
import it.droneskycheck.app.data.weather.WeatherReasonCode
import it.droneskycheck.app.data.weather.WeatherState
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

data class FlightOpportunityInput(
    val legalSegments: List<LegalTimelineSegment>,
    val weatherSlots: List<FlightOpportunityWeatherSlot>,
    val zoneId: ZoneId,
    val now: Instant,
    val lightPreference: FlightLightPreference = FlightLightPreference.DAYLIGHT,
    val solarWindows: List<SolarWindow> = emptyList()
)

data class FlightOpportunityWeatherSlot(
    val from: Instant,
    val to: Instant,
    val weatherAssessment: WeatherAssessment,
    val droneAssessment: DroneOperationalAssessment? = null
)

data class FlightOpportunityResult(
    val status: FlightOpportunityStatus,
    val bestOpportunity: FlightOpportunity?,
    val nextOpportunity: FlightOpportunity?,
    val weekendOpportunities: List<FlightOpportunity>,
    val alternatives: List<FlightOpportunity>,
    val horizonFrom: Instant?,
    val horizonTo: Instant?,
    val warnings: List<FlightOpportunityWarning>,
    val blockers: List<FlightOpportunityReasonCode>,
    val lightPreference: FlightLightPreference = FlightLightPreference.DAYLIGHT,
    val solarWindows: List<SolarWindow> = emptyList(),
    val droneRecommendation: FlightOpportunityDroneRecommendation? = null
)

data class FlightOpportunityDroneRecommendation(
    val recommended: FlightOpportunityDroneCandidate,
    val compared: List<FlightOpportunityDroneCandidate>,
    val reason: FlightOpportunityDroneRecommendationReason,
    val lightestCompatible: FlightOpportunityDroneCandidate? = null,
    val bestOperationalMargin: FlightOpportunityDroneCandidate? = recommended,
    val usableCount: Int = compared.count { it.compatibility == DroneWindowCompatibility.USABLE },
    val cautionCount: Int = compared.count { it.compatibility == DroneWindowCompatibility.USABLE_WITH_CAUTION }
)

data class FlightOpportunityDroneCandidate(
    val droneId: String,
    val displayName: String,
    val opportunityScore: Int?,
    val opportunityLevel: FlightOpportunityLevel?,
    val droneScore: Int?,
    val droneLevel: DroneOperationalLevel?,
    val windResistanceMs: Double?,
    val massGrams: Double? = null,
    val compatibility: DroneWindowCompatibility = DroneWindowCompatibility.UNKNOWN,
    val compatibilityReason: String? = null,
    val bestFrom: Instant?,
    val bestTo: Instant?
)

enum class DroneWindowCompatibility {
    USABLE,
    USABLE_WITH_CAUTION,
    NOT_RECOMMENDED,
    NOT_COMPATIBLE,
    UNKNOWN
}

enum class FlightOpportunityDroneRecommendationReason {
    BEST_OPERATIONAL_MARGIN,
    LIGHTEST_COMPATIBLE,
    WIND_MARGIN,
    ONLY_USABLE,
    BETTER_WINDOW,
    NO_CLEAR_ADVANTAGE
}

data class FlightOpportunity(
    val from: Instant,
    val to: Instant,
    val legalState: LegalTimelineState,
    val maxAltitudeAgl: Int?,
    val opportunityScore: Int?,
    val opportunityLevel: FlightOpportunityLevel,
    val weatherScore: Int,
    val weatherState: WeatherState,
    val droneScore: Int?,
    val droneLevel: DroneOperationalLevel?,
    val droneAssessmentAvailable: Boolean,
    val forecastConfidence: WeatherConfidenceLevel,
    val reasons: List<FlightOpportunityReasonCode>,
    val warnings: List<FlightOpportunityWarning>,
    val durationMinutes: Long,
    val timePreference: FlightOpportunityTimePreference = FlightOpportunityTimePreference.UNKNOWN,
    val lightPreference: FlightLightPreference = FlightLightPreference.DAYLIGHT,
    val lightPhase: SolarLightPhase? = null,
    val solarWindow: SolarWindow? = null,
    val requestedLightWindow: TimeWindow? = null,
    val dailyConservativeScoreCap: Int? = null
)

enum class FlightLightPreference {
    DAYLIGHT,
    SUNRISE,
    SUNSET,
    NIGHT
}

enum class FlightOpportunityStatus {
    IDLE,
    LOADING,
    READY,
    PARTIAL,
    NO_OPEN_WINDOW,
    NO_FAVORABLE_WEATHER,
    DRONE_UNFAVORABLE,
    INSUFFICIENT_DATA,
    ERROR
}

enum class FlightOpportunityLevel {
    EXCELLENT,
    GOOD,
    MARGINAL,
    POOR,
    PARTIAL
}

enum class FlightOpportunityTimePreference {
    DAYTIME,
    EVENING,
    NIGHT,
    UNKNOWN
}

enum class FlightOpportunityReasonCode {
    LEGAL_OPEN,
    DAYTIME_WINDOW,
    EVENING_OR_NIGHT_WINDOW,
    ALTITUDE_LIMIT,
    WEATHER_FAVORABLE,
    WEATHER_CAUTION,
    WEATHER_UNFAVORABLE,
    LOW_WIND,
    LOW_GUSTS,
    NO_PRECIPITATION,
    DRONE_COMPATIBLE,
    DRONE_PARTIAL,
    DRONE_UNFAVORABLE,
    FORECAST_CONFIDENCE_HIGH,
    FORECAST_CONFIDENCE_MEDIUM,
    FORECAST_CONFIDENCE_LOW,
    FORECAST_CONFIDENCE_INDICATIVE,
    SHORT_WINDOW,
    SUNRISE_LIGHT_WINDOW,
    SUNSET_LIGHT_WINDOW,
    GOLDEN_HOUR_WINDOW,
    BLUE_HOUR_WINDOW,
    NIGHT_WINDOW,
    LIGHT_WINDOW_MISSING,
    AUTHORIZATION_REQUIRED,
    LEGAL_UNAVAILABLE,
    LEGAL_UNKNOWN,
    WEATHER_DATA_MISSING
}

enum class FlightOpportunityWarning {
    SHORT_WINDOW,
    EVENING_OR_NIGHT_OPERATION,
    VARIABLE_DAY_CAP,
    DRONE_NOT_EVALUATED,
    DRONE_PROFILE_INCOMPLETE,
    FORECAST_CONFIDENCE_LOW,
    FORECAST_CONFIDENCE_INDICATIVE,
    WEATHER_UNFAVORABLE,
    DRONE_UNFAVORABLE,
    HORIZON_LIMITED
}

data class FlightOpportunityConfig(
    val excellentScore: Int = 85,
    val goodScore: Int = 70,
    val marginalScore: Int = 50,
    val minimumUsefulDurationMinutes: Long = 20,
    val preferredDurationMinutes: Long = 60,
    val maxAlternatives: Int = 4,
    val daytimeStart: LocalTime = LocalTime.of(7, 0),
    val daytimeEnd: LocalTime = LocalTime.of(18, 0),
    val eveningEnd: LocalTime = LocalTime.of(22, 0)
)

class FlightOpportunityEngine(
    private val config: FlightOpportunityConfig = FlightOpportunityConfig()
) {
    fun evaluate(input: FlightOpportunityInput): FlightOpportunityResult {
        if (input.weatherSlots.isEmpty()) {
            return emptyResult(
                status = FlightOpportunityStatus.INSUFFICIENT_DATA,
                blockers = listOf(FlightOpportunityReasonCode.WEATHER_DATA_MISSING)
            )
        }

        val horizonFrom = maxInstant(
            input.now,
            maxOf(
                input.legalSegments.minOfOrNull { it.from } ?: input.now,
                input.weatherSlots.minOf { it.from }
            )
        )
        val horizonTo = minOf(
            input.legalSegments.maxOfOrNull { it.to } ?: horizonFrom,
            input.weatherSlots.maxOf { it.to }
        )
        if (!horizonTo.isAfter(horizonFrom)) {
            return emptyResult(
                status = FlightOpportunityStatus.INSUFFICIENT_DATA,
                horizonFrom = horizonFrom,
                horizonTo = horizonTo,
                blockers = listOf(FlightOpportunityReasonCode.WEATHER_DATA_MISSING)
            )
        }

        val openSegments = input.legalSegments.filter { it.isOpenOpportunity && it.to.isAfter(horizonFrom) && it.from.isBefore(horizonTo) }
        if (openSegments.isEmpty()) {
            return emptyResult(
                status = FlightOpportunityStatus.NO_OPEN_WINDOW,
                horizonFrom = horizonFrom,
                horizonTo = horizonTo,
                blockers = legalBlockers(input.legalSegments)
            )
        }

        val lightWindows = input.preferenceWindows(horizonFrom, horizonTo)
        val hasOpenLightIntersection = openSegments.any { segment ->
            lightWindows.any { lightWindow ->
                val from = maxInstant(maxInstant(segment.from, lightWindow.window.from), horizonFrom)
                val to = minInstant(minInstant(segment.to, lightWindow.window.to), horizonTo)
                to.isAfter(from)
            }
        }
        val dailyScoreCaps = input.weatherSlots.dailyConservativeScoreCaps(input.zoneId)
        val candidates = openSegments.flatMap { segment ->
            lightWindows.flatMap { lightWindow ->
                input.weatherSlots.mapNotNull { slot ->
                    val from = maxInstant(
                        maxInstant(maxInstant(segment.from, slot.from), lightWindow.window.from),
                        horizonFrom
                    )
                    val to = minInstant(
                        minInstant(minInstant(segment.to, slot.to), lightWindow.window.to),
                        horizonTo
                    )
                    if (!to.isAfter(from)) return@mapNotNull null
                    opportunityFor(
                        segment = segment,
                        slot = slot,
                        from = from,
                        to = to,
                        zoneId = input.zoneId,
                        lightPreference = input.lightPreference,
                        lightWindow = lightWindow,
                        dailyScoreCap = dailyScoreCaps[from.atZone(input.zoneId).toLocalDate()]
                    )
                }
            }
        }.mergeAdjacentEquivalent()

        val viable = candidates.filter { it.opportunityLevel != FlightOpportunityLevel.POOR }
        val ranked = viable.ranked(input)
        val chronological = viable.sortedBy { it.from }
        val best = ranked.firstOrNull()
        val next = chronological.firstOrNull()
        val weekend = ranked
            .filter { it.from.atZone(input.zoneId).dayOfWeek.isWeekend }
            .distinctBy { it.from.atZone(input.zoneId).toLocalDate() }
            .sortedBy { it.from }

        val status = when {
            viable.isNotEmpty() && viable.all { !it.droneAssessmentAvailable } -> FlightOpportunityStatus.PARTIAL
            viable.isNotEmpty() && viable.any { it.opportunityLevel == FlightOpportunityLevel.PARTIAL } -> FlightOpportunityStatus.PARTIAL
            viable.isNotEmpty() -> FlightOpportunityStatus.READY
            candidates.any { it.weatherState == WeatherState.FAVORABLE || it.weatherScore >= config.goodScore } ->
                FlightOpportunityStatus.DRONE_UNFAVORABLE
            candidates.isNotEmpty() -> FlightOpportunityStatus.NO_FAVORABLE_WEATHER
            lightWindows.isEmpty() || !hasOpenLightIntersection -> FlightOpportunityStatus.NO_OPEN_WINDOW
            else -> FlightOpportunityStatus.INSUFFICIENT_DATA
        }

        val blockers = if (viable.isEmpty()) {
            noOpportunityReasons(candidates, lightWindows, hasOpenLightIntersection)
        } else {
            emptyList()
        }
        return FlightOpportunityResult(
            status = status,
            bestOpportunity = best,
            nextOpportunity = next,
            weekendOpportunities = weekend,
            alternatives = ranked
                .filterNot { it == best }
                .take(config.maxAlternatives),
            horizonFrom = horizonFrom,
            horizonTo = horizonTo,
            warnings = candidates.flatMap { it.warnings }.distinct(),
            blockers = blockers,
            lightPreference = input.lightPreference,
            solarWindows = input.solarWindows
        )
    }

    private fun opportunityFor(
        segment: LegalTimelineSegment,
        slot: FlightOpportunityWeatherSlot,
        from: Instant,
        to: Instant,
        zoneId: ZoneId,
        lightPreference: FlightLightPreference,
        lightWindow: FlightPreferenceWindow,
        dailyScoreCap: Int?
    ): FlightOpportunity {
        val durationMinutes = Duration.between(from, to).toMinutes()
        val drone = slot.droneAssessment
        val timePreference = timePreferenceFor(from, to, zoneId, lightWindow.phase, lightPreference)
        val droneAvailable = drone != null && drone.dataCompleteness != DroneDataCompleteness.MINIMAL
        val rawScore = when {
            slot.weatherAssessment.state == WeatherState.INSUFFICIENT_DATA -> null
            drone == null -> null
            drone.score == null -> null
            drone.dataCompleteness == DroneDataCompleteness.MINIMAL -> null
            else -> minOf(slot.weatherAssessment.score, drone.score)
        }
        val score = when {
            rawScore == null -> null
            dailyScoreCap == null -> rawScore
            else -> dailyAdjustedScore(rawScore, dailyScoreCap)
        }
        val warnings = buildList {
            if (durationMinutes < config.minimumUsefulDurationMinutes) add(FlightOpportunityWarning.SHORT_WINDOW)
            if (timePreference != FlightOpportunityTimePreference.DAYTIME) add(FlightOpportunityWarning.EVENING_OR_NIGHT_OPERATION)
            if (dailyScoreCap != null && rawScore != null && dailyScoreCap < rawScore) add(FlightOpportunityWarning.VARIABLE_DAY_CAP)
            if (drone == null) add(FlightOpportunityWarning.DRONE_NOT_EVALUATED)
            if (drone != null && drone.dataCompleteness == DroneDataCompleteness.MINIMAL) add(FlightOpportunityWarning.DRONE_PROFILE_INCOMPLETE)
            if (slot.weatherAssessment.confidence.level == WeatherConfidenceLevel.LOW) add(FlightOpportunityWarning.FORECAST_CONFIDENCE_LOW)
            if (slot.weatherAssessment.confidence.level == WeatherConfidenceLevel.INSUFFICIENT) add(FlightOpportunityWarning.FORECAST_CONFIDENCE_INDICATIVE)
            if (slot.weatherAssessment.state == WeatherState.UNFAVORABLE) add(FlightOpportunityWarning.WEATHER_UNFAVORABLE)
            if (drone?.level == DroneOperationalLevel.UNFAVORABLE) add(FlightOpportunityWarning.DRONE_UNFAVORABLE)
        }
        val level = when {
            score == null -> FlightOpportunityLevel.PARTIAL
            drone?.dataCompleteness == DroneDataCompleteness.MINIMAL -> FlightOpportunityLevel.PARTIAL
            slot.weatherAssessment.state == WeatherState.UNFAVORABLE -> FlightOpportunityLevel.POOR
            drone?.level == DroneOperationalLevel.UNFAVORABLE -> FlightOpportunityLevel.POOR
            score >= config.excellentScore -> FlightOpportunityLevel.EXCELLENT
            score >= config.goodScore -> FlightOpportunityLevel.GOOD
            score >= config.marginalScore -> FlightOpportunityLevel.MARGINAL
            else -> FlightOpportunityLevel.POOR
        }

        return FlightOpportunity(
            from = from,
            to = to,
            legalState = segment.state,
            maxAltitudeAgl = segment.maxAltitudeAgl,
            opportunityScore = score,
            opportunityLevel = level,
            weatherScore = slot.weatherAssessment.score,
            weatherState = slot.weatherAssessment.state,
            droneScore = drone?.score,
            droneLevel = drone?.level,
            droneAssessmentAvailable = droneAvailable,
            forecastConfidence = slot.weatherAssessment.confidence.level,
            reasons = reasonCodes(
                segment = segment,
                slot = slot,
                drone = drone,
                durationMinutes = durationMinutes,
                timePreference = timePreference,
                lightPreference = lightPreference,
                lightPhase = lightWindow.phase
            ),
            warnings = warnings,
            durationMinutes = durationMinutes,
            timePreference = timePreference,
            lightPreference = lightPreference,
            lightPhase = lightWindow.phase,
            solarWindow = lightWindow.solarWindow,
            requestedLightWindow = lightWindow.window,
            dailyConservativeScoreCap = dailyScoreCap
        )
    }

    private fun reasonCodes(
        segment: LegalTimelineSegment,
        slot: FlightOpportunityWeatherSlot,
        drone: DroneOperationalAssessment?,
        durationMinutes: Long,
        timePreference: FlightOpportunityTimePreference,
        lightPreference: FlightLightPreference,
        lightPhase: SolarLightPhase?
    ): List<FlightOpportunityReasonCode> =
        buildList {
            add(FlightOpportunityReasonCode.LEGAL_OPEN)
            add(
                if (timePreference == FlightOpportunityTimePreference.DAYTIME) {
                    FlightOpportunityReasonCode.DAYTIME_WINDOW
                } else {
                    FlightOpportunityReasonCode.EVENING_OR_NIGHT_WINDOW
                }
            )
            addAll(lightReasonCodes(lightPreference, lightPhase))
            if (segment.state == LegalTimelineState.AVAILABLE_WITH_LIMIT || segment.maxAltitudeAgl != null && segment.maxAltitudeAgl < 120) {
                add(FlightOpportunityReasonCode.ALTITUDE_LIMIT)
            }
            add(
                when (slot.weatherAssessment.state) {
                    WeatherState.FAVORABLE -> FlightOpportunityReasonCode.WEATHER_FAVORABLE
                    WeatherState.CAUTION -> FlightOpportunityReasonCode.WEATHER_CAUTION
                    WeatherState.UNFAVORABLE -> FlightOpportunityReasonCode.WEATHER_UNFAVORABLE
                    WeatherState.INSUFFICIENT_DATA -> FlightOpportunityReasonCode.WEATHER_DATA_MISSING
                }
            )
            if (WeatherReasonCode.STRONG_WIND !in slot.weatherAssessment.reasons) add(FlightOpportunityReasonCode.LOW_WIND)
            if (WeatherReasonCode.HIGH_GUSTS !in slot.weatherAssessment.reasons) add(FlightOpportunityReasonCode.LOW_GUSTS)
            if (slot.weatherAssessment.reasons.none { it.isPrecipitationReason }) add(FlightOpportunityReasonCode.NO_PRECIPITATION)
            add(
                when {
                    drone == null || drone.dataCompleteness == DroneDataCompleteness.MINIMAL -> FlightOpportunityReasonCode.DRONE_PARTIAL
                    drone.level == DroneOperationalLevel.UNFAVORABLE -> FlightOpportunityReasonCode.DRONE_UNFAVORABLE
                    else -> FlightOpportunityReasonCode.DRONE_COMPATIBLE
                }
            )
            add(
                when (slot.weatherAssessment.confidence.level) {
                    WeatherConfidenceLevel.HIGH -> FlightOpportunityReasonCode.FORECAST_CONFIDENCE_HIGH
                    WeatherConfidenceLevel.MEDIUM -> FlightOpportunityReasonCode.FORECAST_CONFIDENCE_MEDIUM
                    WeatherConfidenceLevel.LOW -> FlightOpportunityReasonCode.FORECAST_CONFIDENCE_LOW
                    WeatherConfidenceLevel.INSUFFICIENT -> FlightOpportunityReasonCode.FORECAST_CONFIDENCE_INDICATIVE
                }
            )
            if (durationMinutes < config.minimumUsefulDurationMinutes) add(FlightOpportunityReasonCode.SHORT_WINDOW)
        }.distinct()

    private fun List<FlightOpportunity>.ranked(input: FlightOpportunityInput): List<FlightOpportunity> {
        if (input.solarWindows.isNotEmpty()) {
            return sortedWith(
                compareByDescending<FlightOpportunity> { rankingScore(it, input.lightPreference) }
                    .thenBy { it.from }
            )
        }
        return rankedWithDaytimePreference()
    }

    private fun List<FlightOpportunity>.rankedWithDaytimePreference(): List<FlightOpportunity> {
        val daytime = filter { it.timePreference == FlightOpportunityTimePreference.DAYTIME }
        val pool = daytime.ifEmpty { this }
        val rankedPreferred = pool.sortedWith(
            compareByDescending<FlightOpportunity> { rankingScore(it, FlightLightPreference.DAYLIGHT) }
                .thenBy { it.from }
        )
        val rankedAlternatives = filterNot { it in rankedPreferred }.sortedWith(
            compareByDescending<FlightOpportunity> { rankingScore(it, FlightLightPreference.DAYLIGHT) }
                .thenBy { it.from }
        )
        return rankedPreferred + rankedAlternatives
    }

    private fun rankingScore(opportunity: FlightOpportunity, lightPreference: FlightLightPreference): Int {
        val base = opportunity.opportunityScore ?: 45
        val durationBonus = when {
            opportunity.durationMinutes >= config.preferredDurationMinutes -> 8
            opportunity.durationMinutes >= config.minimumUsefulDurationMinutes -> 3
            else -> -15
        }
        val confidenceBonus = when (opportunity.forecastConfidence) {
            WeatherConfidenceLevel.HIGH -> 4
            WeatherConfidenceLevel.MEDIUM -> 2
            WeatherConfidenceLevel.LOW -> -4
            WeatherConfidenceLevel.INSUFFICIENT -> -8
        }
        val dronePenalty = if (!opportunity.droneAssessmentAvailable) -6 else 0
        val timePenalty = when {
            opportunity.lightPhase != null -> 0
            lightPreference != FlightLightPreference.DAYLIGHT -> 0
            opportunity.timePreference == FlightOpportunityTimePreference.DAYTIME -> 0
            opportunity.timePreference == FlightOpportunityTimePreference.EVENING -> -35
            opportunity.timePreference == FlightOpportunityTimePreference.NIGHT -> -45
            else -> -20
        }
        return base + durationBonus + confidenceBonus + dronePenalty + timePenalty
    }

    private fun timePreferenceFor(
        from: Instant,
        to: Instant,
        zoneId: ZoneId,
        lightPhase: SolarLightPhase?,
        lightPreference: FlightLightPreference
    ): FlightOpportunityTimePreference {
        if (lightPhase != null) {
            return when (lightPreference) {
                FlightLightPreference.NIGHT -> FlightOpportunityTimePreference.NIGHT
                FlightLightPreference.SUNSET -> FlightOpportunityTimePreference.EVENING
                FlightLightPreference.SUNRISE,
                FlightLightPreference.DAYLIGHT -> FlightOpportunityTimePreference.DAYTIME
            }
        }
        if (!to.isAfter(from)) return FlightOpportunityTimePreference.UNKNOWN
        val totalMinutes = Duration.between(from, to).toMinutes().coerceAtLeast(1)
        val daytimeMinutes = sampledMinutesInRange(from, to, zoneId, config.daytimeStart, config.daytimeEnd)
        if (daytimeMinutes * 2 >= totalMinutes) return FlightOpportunityTimePreference.DAYTIME

        val eveningMinutes = sampledMinutesInRange(from, to, zoneId, config.daytimeEnd, config.eveningEnd)
        return when {
            eveningMinutes > 0 -> FlightOpportunityTimePreference.EVENING
            else -> FlightOpportunityTimePreference.NIGHT
        }
    }

    private fun sampledMinutesInRange(
        from: Instant,
        to: Instant,
        zoneId: ZoneId,
        start: LocalTime,
        end: LocalTime
    ): Long {
        var cursor = from
        var minutes = 0L
        while (cursor.isBefore(to)) {
            val next = minInstant(cursor.plus(Duration.ofMinutes(5)), to)
            val localTime = cursor.atZone(zoneId).toLocalTime()
            if (!localTime.isBefore(start) && localTime.isBefore(end)) {
                minutes += Duration.between(cursor, next).toMinutes()
            }
            cursor = next
        }
        return minutes
    }

    private fun dailyAdjustedScore(rawScore: Int, dailyScoreCap: Int): Int =
        if (dailyScoreCap >= rawScore) {
            rawScore
        } else {
            ((rawScore * 0.30) + (dailyScoreCap * 0.70))
                .roundToInt()
                .coerceIn(0, rawScore)
        }

    private fun noOpportunityReasons(
        candidates: List<FlightOpportunity>,
        lightWindows: List<FlightPreferenceWindow>,
        hasOpenLightIntersection: Boolean
    ): List<FlightOpportunityReasonCode> =
        when {
            lightWindows.isEmpty() || !hasOpenLightIntersection -> listOf(FlightOpportunityReasonCode.LIGHT_WINDOW_MISSING)
            candidates.any { it.droneLevel == DroneOperationalLevel.UNFAVORABLE } ->
                listOf(FlightOpportunityReasonCode.DRONE_UNFAVORABLE)
            candidates.any { it.weatherState == WeatherState.UNFAVORABLE } ->
                listOf(FlightOpportunityReasonCode.WEATHER_UNFAVORABLE)
            candidates.any { it.weatherState == WeatherState.INSUFFICIENT_DATA } ->
                listOf(FlightOpportunityReasonCode.WEATHER_DATA_MISSING)
            else -> listOf(FlightOpportunityReasonCode.WEATHER_UNFAVORABLE)
        }

    private fun legalBlockers(segments: List<LegalTimelineSegment>): List<FlightOpportunityReasonCode> =
        segments.map {
            when (it.state) {
                LegalTimelineState.AUTH_REQUIRED -> FlightOpportunityReasonCode.AUTHORIZATION_REQUIRED
                LegalTimelineState.UNAVAILABLE -> FlightOpportunityReasonCode.LEGAL_UNAVAILABLE
                LegalTimelineState.UNKNOWN -> FlightOpportunityReasonCode.LEGAL_UNKNOWN
                LegalTimelineState.AVAILABLE,
                LegalTimelineState.AVAILABLE_WITH_LIMIT -> FlightOpportunityReasonCode.LEGAL_OPEN
            }
        }.filter { it != FlightOpportunityReasonCode.LEGAL_OPEN }
            .distinct()
            .ifEmpty { listOf(FlightOpportunityReasonCode.LEGAL_UNKNOWN) }

    private fun emptyResult(
        status: FlightOpportunityStatus,
        horizonFrom: Instant? = null,
        horizonTo: Instant? = null,
        blockers: List<FlightOpportunityReasonCode>
    ): FlightOpportunityResult =
        FlightOpportunityResult(
            status = status,
            bestOpportunity = null,
            nextOpportunity = null,
            weekendOpportunities = emptyList(),
            alternatives = emptyList(),
            horizonFrom = horizonFrom,
            horizonTo = horizonTo,
            warnings = emptyList(),
            blockers = blockers
        )
}

private data class FlightPreferenceWindow(
    val window: TimeWindow,
    val solarWindow: SolarWindow?,
    val phase: SolarLightPhase?
)

private fun FlightOpportunityInput.preferenceWindows(horizonFrom: Instant, horizonTo: Instant): List<FlightPreferenceWindow> {
    if (solarWindows.isEmpty()) {
        return listOf(
            FlightPreferenceWindow(
                window = TimeWindow(horizonFrom, horizonTo),
                solarWindow = null,
                phase = null
            )
        )
    }

    val sortedWindows = solarWindows.sortedBy { it.date }
    val requested = when (lightPreference) {
        FlightLightPreference.DAYLIGHT -> sortedWindows.mapNotNull { solar ->
            solar.daylight?.let { FlightPreferenceWindow(it, solar, SolarLightPhase.DAYLIGHT) }
        }
        FlightLightPreference.SUNRISE -> sortedWindows.flatMap { solar ->
            listOfNotNull(
                solar.morningBlueHour?.let { FlightPreferenceWindow(it, solar, SolarLightPhase.BLUE_HOUR_MORNING) },
                solar.morningGoldenHour?.let { FlightPreferenceWindow(it, solar, SolarLightPhase.GOLDEN_HOUR_MORNING) }
            )
        }
        FlightLightPreference.SUNSET -> sortedWindows.flatMap { solar ->
            listOfNotNull(
                solar.eveningGoldenHour?.let { FlightPreferenceWindow(it, solar, SolarLightPhase.GOLDEN_HOUR_EVENING) },
                solar.eveningBlueHour?.let { FlightPreferenceWindow(it, solar, SolarLightPhase.BLUE_HOUR_EVENING) }
            )
        }
        FlightLightPreference.NIGHT -> sortedWindows.flatMapIndexed { index, solar ->
            val dayStart = solar.date.atStartOfDay(zoneId).toInstant()
            val nextSolar = sortedWindows.getOrNull(index + 1)
            val nextMorningBlueStart = nextSolar?.blueHourMorningStart
                ?: solar.date.plusDays(1).atStartOfDay(zoneId).toInstant()
            listOfNotNull(
                solar.blueHourMorningStart?.let { morningStart ->
                    timeWindowOrNull(dayStart, morningStart)
                }?.let { FlightPreferenceWindow(it, solar, SolarLightPhase.NIGHT) },
                solar.blueHourEveningEnd?.let { eveningEnd ->
                    timeWindowOrNull(eveningEnd, nextMorningBlueStart)
                }?.let { FlightPreferenceWindow(it, solar, SolarLightPhase.NIGHT) }
            )
        }
    }

    val horizon = TimeWindow(horizonFrom, horizonTo)
    return requested.mapNotNull { requestedWindow ->
        requestedWindow.window.intersection(horizon)?.let { intersection ->
            requestedWindow.copy(window = intersection)
        }
    }
}

private fun lightReasonCodes(
    lightPreference: FlightLightPreference,
    lightPhase: SolarLightPhase?
): List<FlightOpportunityReasonCode> =
    buildList {
        when (lightPreference) {
            FlightLightPreference.SUNRISE -> add(FlightOpportunityReasonCode.SUNRISE_LIGHT_WINDOW)
            FlightLightPreference.SUNSET -> add(FlightOpportunityReasonCode.SUNSET_LIGHT_WINDOW)
            FlightLightPreference.NIGHT -> add(FlightOpportunityReasonCode.NIGHT_WINDOW)
            FlightLightPreference.DAYLIGHT -> Unit
        }
        when (lightPhase) {
            SolarLightPhase.BLUE_HOUR_MORNING,
            SolarLightPhase.BLUE_HOUR_EVENING -> add(FlightOpportunityReasonCode.BLUE_HOUR_WINDOW)
            SolarLightPhase.GOLDEN_HOUR_MORNING,
            SolarLightPhase.GOLDEN_HOUR_EVENING -> add(FlightOpportunityReasonCode.GOLDEN_HOUR_WINDOW)
            SolarLightPhase.NIGHT -> add(FlightOpportunityReasonCode.NIGHT_WINDOW)
            SolarLightPhase.DAYLIGHT,
            null -> Unit
        }
    }.distinct()

private val LegalTimelineSegment.isOpenOpportunity: Boolean
    get() = state == LegalTimelineState.AVAILABLE || state == LegalTimelineState.AVAILABLE_WITH_LIMIT

private val DayOfWeek.isWeekend: Boolean
    get() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY

private val WeatherReasonCode.isPrecipitationReason: Boolean
    get() = this == WeatherReasonCode.PRECIPITATION ||
        this == WeatherReasonCode.INTENSE_PRECIPITATION ||
        this == WeatherReasonCode.HIGH_PRECIPITATION_PROBABILITY ||
        this == WeatherReasonCode.DRIZZLE ||
        this == WeatherReasonCode.RAIN ||
        this == WeatherReasonCode.HEAVY_RAIN ||
        this == WeatherReasonCode.SNOW ||
        this == WeatherReasonCode.SHOWERS ||
        this == WeatherReasonCode.THUNDERSTORM ||
        this == WeatherReasonCode.THUNDERSTORM_WITH_HAIL

private fun timeWindowOrNull(from: Instant, to: Instant): TimeWindow? =
    if (to.isAfter(from)) TimeWindow(from, to) else null

private fun TimeWindow.intersection(other: TimeWindow): TimeWindow? {
    val from = maxInstant(from, other.from)
    val to = minInstant(to, other.to)
    return timeWindowOrNull(from, to)
}

private fun List<FlightOpportunity>.mergeAdjacentEquivalent(): List<FlightOpportunity> =
    sortedBy { it.from }.fold(emptyList()) { acc, current ->
        val previous = acc.lastOrNull()
        if (previous != null && previous.canMergeWith(current)) {
            acc.dropLast(1) + previous.copy(
                to = current.to,
                durationMinutes = Duration.between(previous.from, current.to).toMinutes()
            )
        } else {
            acc + current
        }
    }

private fun FlightOpportunity.canMergeWith(other: FlightOpportunity): Boolean =
    to == other.from &&
        legalState == other.legalState &&
        maxAltitudeAgl == other.maxAltitudeAgl &&
        opportunityLevel == other.opportunityLevel &&
        opportunityScore == other.opportunityScore &&
        weatherScore == other.weatherScore &&
        weatherState == other.weatherState &&
        droneScore == other.droneScore &&
        droneLevel == other.droneLevel &&
        droneAssessmentAvailable == other.droneAssessmentAvailable &&
        forecastConfidence == other.forecastConfidence &&
        timePreference == other.timePreference &&
        lightPreference == other.lightPreference &&
        lightPhase == other.lightPhase &&
        requestedLightWindow == other.requestedLightWindow &&
        dailyConservativeScoreCap == other.dailyConservativeScoreCap &&
        reasons == other.reasons &&
        warnings == other.warnings

private fun List<FlightOpportunityWeatherSlot>.dailyConservativeScoreCaps(zoneId: ZoneId): Map<LocalDate, Int> =
    groupBy { it.from.atZone(zoneId).toLocalDate() }
        .mapValues { (_, slots) ->
            val weatherCap = slots.map { it.weatherAssessment.score }.representativeDailyScore()
            val droneCap = slots.mapNotNull { slot ->
                slot.droneAssessment
                    ?.takeIf { it.dataCompleteness != DroneDataCompleteness.MINIMAL }
                    ?.score
            }.representativeDailyScore()
            listOfNotNull(weatherCap, droneCap).minOrNull()
        }
        .filterValues { it != null }
        .mapValues { requireNotNull(it.value) }

private fun List<Int>.representativeDailyScore(): Int? {
    if (isEmpty()) return null
    val minimum = minOrNull() ?: return null
    val maximum = maxOrNull() ?: return null
    val average = average()
    val variable = minimum < 50 && maximum >= 75
    return if (variable) {
        minOf(65, ((average * 0.55) + (minimum * 0.45)).roundToInt())
    } else {
        ((average * 0.7) + (minimum * 0.3)).roundToInt().coerceIn(0, 100)
    }
}

private fun maxInstant(first: Instant, second: Instant): Instant =
    if (first.isAfter(second)) first else second

private fun minInstant(first: Instant, second: Instant): Instant =
    if (first.isBefore(second)) first else second
