package it.droneskycheck.app.data.solar

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

data class TimeWindow(
    val from: Instant,
    val to: Instant
) {
    fun overlaps(other: TimeWindow): Boolean =
        to.isAfter(other.from) && from.isBefore(other.to)
}

enum class SolarLightPhase {
    NIGHT,
    BLUE_HOUR_MORNING,
    GOLDEN_HOUR_MORNING,
    DAYLIGHT,
    GOLDEN_HOUR_EVENING,
    BLUE_HOUR_EVENING
}

data class SolarWindow(
    val date: LocalDate,
    val zoneId: ZoneId,
    val sunrise: Instant?,
    val sunset: Instant?,
    val blueHourMorningStart: Instant?,
    val goldenHourMorningStart: Instant?,
    val goldenHourMorningEnd: Instant?,
    val goldenHourEveningStart: Instant?,
    val blueHourEveningStart: Instant?,
    val blueHourEveningEnd: Instant?,
    val morningBlueHour: TimeWindow?,
    val morningGoldenHour: TimeWindow?,
    val daylight: TimeWindow?,
    val eveningGoldenHour: TimeWindow?,
    val eveningBlueHour: TimeWindow?
)

data class SolarLightConfig(
    val sunriseSunsetElevationDegrees: Double = -0.833,
    val blueHourLowElevationDegrees: Double = -6.0,
    val blueGoldenBoundaryElevationDegrees: Double = -4.0,
    val goldenDaylightBoundaryElevationDegrees: Double = 6.0,
    val samplingStep: Duration = Duration.ofMinutes(5),
    val refinementIterations: Int = 14
) {
    companion object {
        /*
         * These photographic light bands are intentionally centralized.
         * There is no single universal Golden/Blue Hour definition; Drone Sky Check uses:
         * blue hour  = sun elevation from -6 to -4 degrees
         * golden hour = sun elevation from -4 to +6 degrees
         * sunrise/sunset = apparent solar center at -0.833 degrees
         */
        val DEFAULT = SolarLightConfig()
    }
}

class SolarLightCalculator(
    private val config: SolarLightConfig = SolarLightConfig.DEFAULT
) {
    fun windowsForRange(
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId,
        from: Instant,
        to: Instant
    ): List<SolarWindow> {
        if (!latitude.isFinite() || !longitude.isFinite() || !to.isAfter(from)) return emptyList()
        val firstDate = from.atZone(zoneId).toLocalDate()
        val lastDate = to.minusMillis(1).atZone(zoneId).toLocalDate()
        return generateSequence(firstDate) { date ->
            date.plusDays(1).takeIf { !it.isAfter(lastDate) }
        }.map { date ->
            windowForDate(latitude, longitude, zoneId, date)
        }.toList()
    }

    fun windowForDate(
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId,
        date: LocalDate
    ): SolarWindow {
        val dayStart = date.atStartOfDay(zoneId).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val crossings = crossingsForDay(latitude, longitude, dayStart, dayEnd)

        fun rising(threshold: Double): Instant? =
            crossings.firstOrNull { it.threshold == threshold && it.direction == SolarCrossingDirection.RISING }?.instant

        fun setting(threshold: Double): Instant? =
            crossings.firstOrNull { it.threshold == threshold && it.direction == SolarCrossingDirection.SETTING }?.instant

        val morningBlueStart = rising(config.blueHourLowElevationDegrees)
        val morningGoldenStart = rising(config.blueGoldenBoundaryElevationDegrees)
        val sunrise = rising(config.sunriseSunsetElevationDegrees)
        val morningGoldenEnd = rising(config.goldenDaylightBoundaryElevationDegrees)
        val eveningGoldenStart = setting(config.goldenDaylightBoundaryElevationDegrees)
        val sunset = setting(config.sunriseSunsetElevationDegrees)
        val eveningBlueStart = setting(config.blueGoldenBoundaryElevationDegrees)
        val eveningBlueEnd = setting(config.blueHourLowElevationDegrees)

        return SolarWindow(
            date = date,
            zoneId = zoneId,
            sunrise = sunrise,
            sunset = sunset,
            blueHourMorningStart = morningBlueStart,
            goldenHourMorningStart = morningGoldenStart,
            goldenHourMorningEnd = morningGoldenEnd,
            goldenHourEveningStart = eveningGoldenStart,
            blueHourEveningStart = eveningBlueStart,
            blueHourEveningEnd = eveningBlueEnd,
            morningBlueHour = timeWindowOrNull(morningBlueStart, morningGoldenStart),
            morningGoldenHour = timeWindowOrNull(morningGoldenStart, morningGoldenEnd),
            daylight = timeWindowOrNull(sunrise, sunset),
            eveningGoldenHour = timeWindowOrNull(eveningGoldenStart, eveningBlueStart),
            eveningBlueHour = timeWindowOrNull(eveningBlueStart, eveningBlueEnd)
        )
    }

    private fun crossingsForDay(
        latitude: Double,
        longitude: Double,
        dayStart: Instant,
        dayEnd: Instant
    ): List<SolarCrossing> {
        val thresholds = listOf(
            config.blueHourLowElevationDegrees,
            config.blueGoldenBoundaryElevationDegrees,
            config.sunriseSunsetElevationDegrees,
            config.goldenDaylightBoundaryElevationDegrees
        )
        val crossings = mutableListOf<SolarCrossing>()
        var cursor = dayStart
        var previousElevation = solarElevationDegrees(cursor, latitude, longitude)

        while (cursor.isBefore(dayEnd)) {
            val next = minInstant(cursor.plus(config.samplingStep), dayEnd)
            val nextElevation = solarElevationDegrees(next, latitude, longitude)
            thresholds.forEach { threshold ->
                if ((previousElevation < threshold && nextElevation >= threshold) ||
                    (previousElevation >= threshold && nextElevation < threshold)
                ) {
                    val direction = if (nextElevation > previousElevation) {
                        SolarCrossingDirection.RISING
                    } else {
                        SolarCrossingDirection.SETTING
                    }
                    val instant = refineCrossing(
                        latitude = latitude,
                        longitude = longitude,
                        threshold = threshold,
                        from = cursor,
                        to = next,
                        direction = direction
                    )
                    crossings += SolarCrossing(threshold, direction, instant)
                }
            }
            cursor = next
            previousElevation = nextElevation
        }
        return crossings.sortedBy { it.instant }
    }

    private fun refineCrossing(
        latitude: Double,
        longitude: Double,
        threshold: Double,
        from: Instant,
        to: Instant,
        direction: SolarCrossingDirection
    ): Instant {
        var low = from
        var high = to
        repeat(config.refinementIterations) {
            val midpoint = low.plusMillis(Duration.between(low, high).toMillis() / 2)
            val elevation = solarElevationDegrees(midpoint, latitude, longitude)
            val isBelow = elevation < threshold
            if (direction == SolarCrossingDirection.RISING) {
                if (isBelow) low = midpoint else high = midpoint
            } else {
                if (isBelow) high = midpoint else low = midpoint
            }
        }
        return high
    }
}

private data class SolarCrossing(
    val threshold: Double,
    val direction: SolarCrossingDirection,
    val instant: Instant
)

private enum class SolarCrossingDirection {
    RISING,
    SETTING
}

private fun timeWindowOrNull(from: Instant?, to: Instant?): TimeWindow? =
    if (from != null && to != null && to.isAfter(from)) TimeWindow(from, to) else null

private fun solarElevationDegrees(
    instant: Instant,
    latitudeDegrees: Double,
    longitudeDegrees: Double
): Double {
    val utc = instant.atZone(ZoneId.of("UTC"))
    val fractionalHour = utc.hour + utc.minute / 60.0 + utc.second / 3600.0
    val gamma = 2.0 * PI / 365.0 * (utc.dayOfYear - 1 + (fractionalHour - 12.0) / 24.0)
    val equationOfTimeMinutes = 229.18 * (
        0.000075 +
            0.001868 * cos(gamma) -
            0.032077 * sin(gamma) -
            0.014615 * cos(2.0 * gamma) -
            0.040849 * sin(2.0 * gamma)
        )
    val declinationRadians =
        0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2.0 * gamma) +
            0.000907 * sin(2.0 * gamma) -
            0.002697 * cos(3.0 * gamma) +
            0.00148 * sin(3.0 * gamma)
    val trueSolarTimeMinutes = fractionalHour * 60.0 + equationOfTimeMinutes + 4.0 * longitudeDegrees
    val hourAngleDegrees = normalizeHourAngle(trueSolarTimeMinutes / 4.0 - 180.0)
    val latitudeRadians = latitudeDegrees.toRadians()
    val hourAngleRadians = hourAngleDegrees.toRadians()
    return asin(
        sin(latitudeRadians) * sin(declinationRadians) +
            cos(latitudeRadians) * cos(declinationRadians) * cos(hourAngleRadians)
    ).toDegrees()
}

private fun normalizeHourAngle(degrees: Double): Double =
    when {
        degrees < -180.0 -> degrees + 360.0
        degrees > 180.0 -> degrees - 360.0
        else -> degrees
    }

private fun Double.toRadians(): Double = this / 180.0 * PI

private fun Double.toDegrees(): Double = this * 180.0 / PI

private fun minInstant(first: Instant, second: Instant): Instant =
    if (first.isBefore(second)) first else second
