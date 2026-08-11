package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.LegalTimelineSegment
import it.droneskycheck.app.data.LegalTimelineState
import it.droneskycheck.app.data.drone.DroneDataCompleteness
import it.droneskycheck.app.data.drone.DroneOperationalAssessment
import it.droneskycheck.app.data.drone.DroneOperationalAssessmentEngine
import it.droneskycheck.app.data.drone.DroneOperationalCapabilities
import it.droneskycheck.app.data.drone.DroneOperationalLevel
import it.droneskycheck.app.data.weather.WeatherAssessment
import it.droneskycheck.app.data.weather.WeatherAssessmentEngine
import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.data.weather.WeatherForecastHour
import it.droneskycheck.app.data.weather.WeatherReasonCode
import it.droneskycheck.app.data.weather.WeatherState
import it.droneskycheck.app.data.weather.toWeatherMetrics
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val MaxLegalTimelineWindow = Duration.ofHours(168)

fun legalTimelineEndIncludingWeekend(
    from: Instant,
    zoneId: ZoneId,
    maxWindow: Duration = MaxLegalTimelineWindow
): Instant {
    val localFrom = from.atZone(zoneId)
    val daysUntilSunday = Math.floorMod(
        DayOfWeek.SUNDAY.value - localFrom.dayOfWeek.value,
        7
    )
    val endOfRelevantSunday = localFrom
        .toLocalDate()
        .plusDays(daysUntilSunday.toLong())
        .plusDays(1)
        .atStartOfDay(zoneId)
        .toInstant()
    val backendCap = from.plus(maxWindow)
    return if (endOfRelevantSunday.isAfter(backendCap)) backendCap else endOfRelevantSunday
}

data class LegalDailySummary(
    val date: LocalDate,
    val isWeekend: Boolean,
    val windows: List<LegalDailyWindow>
)

data class LegalDailyWindow(
    val start: LocalTime,
    val end: LocalTime,
    val state: LegalTimelineState,
    val maxAltitudeAgl: Int?
)

fun summarizeLegalTimelineByDay(
    segments: List<LegalTimelineSegment>,
    zoneId: ZoneId,
    from: Instant,
    to: Instant
): List<LegalDailySummary> {
    if (!to.isAfter(from)) return emptyList()

    val firstDate = from.atZone(zoneId).toLocalDate()
    val lastDate = to.minusMillis(1).atZone(zoneId).toLocalDate()
    val dates = generateSequence(firstDate) { date ->
        date.plusDays(1).takeIf { !it.isAfter(lastDate) }
    }.toList()

    return dates.mapNotNull { date ->
        val dayStart = date.atStartOfDay(zoneId).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val rangeStart = maxInstant(from, dayStart)
        val rangeEnd = minInstant(to, dayEnd)
        if (!rangeEnd.isAfter(rangeStart)) return@mapNotNull null

        val windows = segments
            .asSequence()
            .filter { segment -> segment.to.isAfter(rangeStart) && segment.from.isBefore(rangeEnd) }
            .map { segment ->
                LegalDailyWindow(
                    start = maxInstant(segment.from, rangeStart).atZone(zoneId).toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                    end = minInstant(segment.to, rangeEnd).atZone(zoneId).toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                    state = segment.state,
                    maxAltitudeAgl = segment.maxAltitudeAgl
                )
            }
            .sortedBy { it.start }
            .fold(emptyList<LegalDailyWindow>()) { acc, window ->
                val previous = acc.lastOrNull()
                if (previous != null &&
                    previous.end == window.start &&
                    previous.state == window.state &&
                    previous.maxAltitudeAgl == window.maxAltitudeAgl
                ) {
                    acc.dropLast(1) + previous.copy(end = window.end)
                } else {
                    acc + window
                }
            }

        if (windows.isEmpty()) null else LegalDailySummary(
            date = date,
            isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY,
            windows = windows
        )
    }
}

data class WeatherDailyTrend(
    val date: LocalDate,
    val isWeekend: Boolean,
    val label: WeatherDailyTrendLabel,
    val score: Int?,
    val reliability: ForecastReliability,
    val bestWindow: WeatherTrendWindow?,
    val notes: List<WeatherReasonCode>,
    val variable: Boolean,
    val availableHours: Int
)

data class WeatherTrendWindow(
    val start: LocalTime,
    val end: LocalTime
)

data class DroneDailyOperationalTrend(
    val date: LocalDate,
    val isWeekend: Boolean,
    val level: DroneOperationalLevel,
    val score: Int?,
    val dataCompleteness: DroneDataCompleteness,
    val bestWindow: WeatherTrendWindow?,
    val factors: List<String>,
    val warnings: List<String>,
    val variable: Boolean,
    val availableHours: Int
)

enum class WeatherDailyTrendLabel {
    FAVORABLE,
    CAUTION,
    UNFAVORABLE,
    VARIABLE,
    INSUFFICIENT
}

enum class ForecastReliability {
    HIGH,
    MEDIUM,
    INDICATIVE
}

fun summarizeWeatherTrendByDay(
    forecast: WeatherForecast?,
    now: Instant,
    engine: WeatherAssessmentEngine = WeatherAssessmentEngine()
): List<WeatherDailyTrend> {
    if (forecast == null) return emptyList()
    val zoneId = forecast.timezone ?: ZoneId.systemDefault()
    val today = now.atZone(zoneId).toLocalDate()

    return forecast.hours
        .filter { !it.instant.isBefore(now) }
        .groupBy { it.localDate(zoneId) }
        .toSortedMap()
        .map { (date, hours) ->
            val assessed = hours.map { hour -> hour to engine.assess(hour.toWeatherMetrics()) }
            val scores = assessed.map { it.second.score }
            val minimum = scores.minOrNull()
            val maximum = scores.maxOrNull()
            val average = scores.takeIf { it.isNotEmpty() }?.average()
            val hasInsufficient = assessed.any { it.second.state == WeatherState.INSUFFICIENT_DATA }
            val variable = minimum != null && maximum != null && minimum < 50 && maximum >= 80
            val label = when {
                hasInsufficient -> WeatherDailyTrendLabel.INSUFFICIENT
                variable -> WeatherDailyTrendLabel.VARIABLE
                maximum != null && maximum < 50 -> WeatherDailyTrendLabel.UNFAVORABLE
                minimum != null && minimum >= 80 -> WeatherDailyTrendLabel.FAVORABLE
                average != null && average < 50 -> WeatherDailyTrendLabel.UNFAVORABLE
                average != null && average >= 80 && (minimum ?: 0) >= 65 -> WeatherDailyTrendLabel.FAVORABLE
                else -> WeatherDailyTrendLabel.CAUTION
            }
            val representativeScore = when {
                hasInsufficient -> null
                average == null || minimum == null -> null
                variable -> minOf(65, ((average * 0.55) + (minimum * 0.45)).roundToInt())
                else -> ((average * 0.7) + (minimum * 0.3)).roundToInt().coerceIn(0, 100)
            }

            WeatherDailyTrend(
                date = date,
                isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY,
                label = label,
                score = representativeScore,
                reliability = forecastReliabilityFor(date, today, assessed.map { it.second }),
                bestWindow = bestWeatherWindow(assessed, zoneId),
                notes = assessed
                    .flatMap { it.second.reasons }
                    .distinct()
                    .take(3),
                variable = variable,
                availableHours = hours.size
            )
        }
}

fun summarizeDroneOperationalTrendByDay(
    forecast: WeatherForecast?,
    capabilities: DroneOperationalCapabilities?,
    now: Instant,
    droneEngine: DroneOperationalAssessmentEngine = DroneOperationalAssessmentEngine(),
    weatherEngine: WeatherAssessmentEngine = WeatherAssessmentEngine()
): List<DroneDailyOperationalTrend> {
    if (forecast == null || capabilities == null) return emptyList()
    val zoneId = forecast.timezone ?: ZoneId.systemDefault()

    return forecast.hours
        .filter { !it.instant.isBefore(now) }
        .groupBy { it.localDate(zoneId) }
        .toSortedMap()
        .mapNotNull { (date, hours) ->
            val assessed = hours.mapNotNull { hour ->
                val metrics = hour.toWeatherMetrics()
                val weather = weatherEngine.assess(metrics)
                droneEngine.assess(metrics, capabilities, weather)?.let { hour to it }
            }
            if (assessed.isEmpty()) return@mapNotNull null

            val scores = assessed.mapNotNull { it.second.score }
            val levels = assessed.map { it.second.level }
            val minimum = scores.minOrNull()
            val maximum = scores.maxOrNull()
            val average = scores.takeIf { it.isNotEmpty() }?.average()
            val variable = minimum != null && maximum != null && minimum < 50 && maximum >= 75
            val level = when {
                levels.any { it == DroneOperationalLevel.UNFAVORABLE } -> DroneOperationalLevel.UNFAVORABLE
                levels.all { it == DroneOperationalLevel.UNKNOWN } -> DroneOperationalLevel.UNKNOWN
                variable -> DroneOperationalLevel.CAUTION
                average == null -> DroneOperationalLevel.UNKNOWN
                average >= 80 && (minimum ?: 0) >= 65 -> DroneOperationalLevel.FAVORABLE
                average >= 65 -> DroneOperationalLevel.ACCEPTABLE
                average >= 45 -> DroneOperationalLevel.CAUTION
                else -> DroneOperationalLevel.UNFAVORABLE
            }
            val representativeScore = when {
                average == null || minimum == null -> null
                variable -> minOf(65, ((average * 0.55) + (minimum * 0.45)).roundToInt())
                else -> ((average * 0.7) + (minimum * 0.3)).roundToInt().coerceIn(0, 100)
            }

            DroneDailyOperationalTrend(
                date = date,
                isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY,
                level = level,
                score = representativeScore,
                dataCompleteness = assessed.map { it.second.dataCompleteness }.leastComplete(),
                bestWindow = bestDroneWeatherWindow(assessed, zoneId),
                factors = assessed
                    .flatMap { it.second.factors }
                    .filter { it.level != DroneOperationalLevel.FAVORABLE }
                    .map { it.title }
                    .distinct()
                    .take(3),
                warnings = assessed
                    .flatMap { it.second.warnings }
                    .distinct()
                    .take(2),
                variable = variable,
                availableHours = hours.size
            )
        }
}

private fun forecastReliabilityFor(
    date: LocalDate,
    today: LocalDate,
    assessments: List<WeatherAssessment>
): ForecastReliability {
    if (assessments.isEmpty() ||
        assessments.any { it.state == WeatherState.INSUFFICIENT_DATA } ||
        assessments.any { it.confidence.score < 60 }
    ) {
        return ForecastReliability.INDICATIVE
    }

    val daysAhead = ChronoUnit.DAYS.between(today, date)
    return when {
        daysAhead <= 1 -> ForecastReliability.HIGH
        daysAhead <= 3 -> ForecastReliability.MEDIUM
        else -> ForecastReliability.INDICATIVE
    }
}

private fun bestWeatherWindow(
    assessed: List<Pair<WeatherForecastHour, WeatherAssessment>>,
    zoneId: ZoneId
): WeatherTrendWindow? {
    val favorable = longestRun(assessed) { it.score >= 80 && it.state == WeatherState.FAVORABLE }
        ?: longestRun(assessed) { it.score >= 70 && it.state != WeatherState.UNFAVORABLE }
        ?: return null
    val start = favorable.first().first.localTime(zoneId)
    val end = favorable.last().first.instant.plus(Duration.ofHours(1)).atZone(zoneId).toLocalTime()
    return WeatherTrendWindow(
        start = start.truncatedTo(ChronoUnit.MINUTES),
        end = end.truncatedTo(ChronoUnit.MINUTES)
    )
}

private fun bestDroneWeatherWindow(
    assessed: List<Pair<WeatherForecastHour, DroneOperationalAssessment>>,
    zoneId: ZoneId
): WeatherTrendWindow? {
    val favorable = longestDroneRun(assessed) {
        it.score != null &&
            it.score >= 70 &&
            (it.level == DroneOperationalLevel.FAVORABLE || it.level == DroneOperationalLevel.ACCEPTABLE)
    } ?: return null
    val start = favorable.first().first.localTime(zoneId)
    val end = favorable.last().first.instant.plus(Duration.ofHours(1)).atZone(zoneId).toLocalTime()
    return WeatherTrendWindow(
        start = start.truncatedTo(ChronoUnit.MINUTES),
        end = end.truncatedTo(ChronoUnit.MINUTES)
    )
}

private fun longestRun(
    assessed: List<Pair<WeatherForecastHour, WeatherAssessment>>,
    predicate: (WeatherAssessment) -> Boolean
): List<Pair<WeatherForecastHour, WeatherAssessment>>? {
    var best = emptyList<Pair<WeatherForecastHour, WeatherAssessment>>()
    var current = emptyList<Pair<WeatherForecastHour, WeatherAssessment>>()
    for (item in assessed.sortedBy { it.first.instant }) {
        current = if (predicate(item.second)) current + item else emptyList()
        if (current.size > best.size) best = current
    }
    return best.takeIf { it.isNotEmpty() }
}

private fun longestDroneRun(
    assessed: List<Pair<WeatherForecastHour, DroneOperationalAssessment>>,
    predicate: (DroneOperationalAssessment) -> Boolean
): List<Pair<WeatherForecastHour, DroneOperationalAssessment>>? {
    var best = emptyList<Pair<WeatherForecastHour, DroneOperationalAssessment>>()
    var current = emptyList<Pair<WeatherForecastHour, DroneOperationalAssessment>>()
    for (item in assessed.sortedBy { it.first.instant }) {
        current = if (predicate(item.second)) current + item else emptyList()
        if (current.size > best.size) best = current
    }
    return best.takeIf { it.isNotEmpty() }
}

private fun List<DroneDataCompleteness>.leastComplete(): DroneDataCompleteness =
    when {
        any { it == DroneDataCompleteness.MINIMAL } -> DroneDataCompleteness.MINIMAL
        any { it == DroneDataCompleteness.PARTIAL } -> DroneDataCompleteness.PARTIAL
        else -> DroneDataCompleteness.FULL
    }

private fun WeatherForecastHour.localDate(zoneId: ZoneId): LocalDate =
    localDateTime?.toLocalDate() ?: instant.atZone(zoneId).toLocalDate()

private fun WeatherForecastHour.localTime(zoneId: ZoneId): LocalTime =
    localDateTime?.toLocalTime() ?: instant.atZone(zoneId).toLocalTime()

private fun maxInstant(first: Instant, second: Instant): Instant =
    if (first.isAfter(second)) first else second

private fun minInstant(first: Instant, second: Instant): Instant =
    if (first.isBefore(second)) first else second
