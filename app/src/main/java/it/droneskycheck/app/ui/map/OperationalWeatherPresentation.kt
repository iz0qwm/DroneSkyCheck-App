package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.data.weather.WeatherForecastHour
import it.droneskycheck.app.data.weather.WeatherMetrics
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal val OperationalWeatherZoneId: ZoneId = ZoneId.of("Europe/Rome")

internal data class OperationalWeatherDay(
    val date: LocalDate,
    val label: String,
    val slots: List<OperationalWeatherHourSlot>
)

internal data class OperationalWeatherHourSlot(
    val forecastHour: WeatherForecastHour,
    val date: LocalDate,
    val time: LocalTime,
    val localDateTime: LocalDateTime,
    val timeLabel: String
) {
    val key: String = forecastHour.instant.toString()
}

internal data class OperationalWeatherCondition(
    val description: String,
    val icon: OperationalWeatherIcon,
    val contentDescription: String = description
)

internal enum class OperationalWeatherIcon {
    ClearDay,
    ClearNight,
    PartlyCloudyDay,
    PartlyCloudyNight,
    Cloudy,
    Overcast,
    Fog,
    Drizzle,
    Rain,
    Showers,
    Snow,
    Thunderstorm,
    Unknown
}

internal enum class OperationalWindLevel {
    Missing,
    Weak,
    Moderate,
    Sustained,
    Strong
}

internal fun buildOperationalWeatherDays(
    forecast: WeatherForecast,
    now: Instant,
    zoneId: ZoneId = OperationalWeatherZoneId
): List<OperationalWeatherDay> {
    val today = now.atZone(zoneId).toLocalDate()
    return forecast.hours
        .map { hour ->
            val local = hour.instant.atZone(zoneId).toLocalDateTime()
            OperationalWeatherHourSlot(
                forecastHour = hour,
                date = local.toLocalDate(),
                time = local.toLocalTime(),
                localDateTime = local,
                timeLabel = HourFormatter.format(local)
            )
        }
        .groupBy { it.date }
        .toSortedMap()
        .map { (date, slots) ->
            OperationalWeatherDay(
                date = date,
                label = operationalWeatherDayLabel(date, today),
                slots = slots.sortedBy { it.localDateTime }
            )
        }
}

internal fun selectInitialOperationalWeatherSlot(
    days: List<OperationalWeatherDay>,
    now: Instant
): OperationalWeatherHourSlot? =
    days.asSequence()
        .flatMap { it.slots.asSequence() }
        .minByOrNull { slot -> abs(Duration.between(now, slot.forecastHour.instant).toMillis()) }

internal fun selectOperationalWeatherSlotForDay(
    day: OperationalWeatherDay,
    previousSlot: OperationalWeatherHourSlot?
): OperationalWeatherHourSlot? {
    if (day.slots.isEmpty()) return null
    val previousTime = previousSlot?.time ?: return day.slots.first()
    return day.slots.minByOrNull { slot ->
        abs(Duration.between(previousTime, slot.time).toMillis())
    }
}

internal fun operationalWeatherCondition(
    weatherCode: Int?,
    isDaylight: Boolean
): OperationalWeatherCondition =
    when (weatherCode) {
        0 -> OperationalWeatherCondition(
            description = "Sereno",
            icon = if (isDaylight) OperationalWeatherIcon.ClearDay else OperationalWeatherIcon.ClearNight
        )
        1 -> OperationalWeatherCondition(
            description = "Poco nuvoloso",
            icon = if (isDaylight) OperationalWeatherIcon.PartlyCloudyDay else OperationalWeatherIcon.PartlyCloudyNight
        )
        2 -> OperationalWeatherCondition(
            description = "Nuvoloso",
            icon = OperationalWeatherIcon.Cloudy
        )
        3 -> OperationalWeatherCondition(
            description = "Coperto",
            icon = OperationalWeatherIcon.Overcast
        )
        45, 48 -> OperationalWeatherCondition(
            description = "Nebbia",
            icon = OperationalWeatherIcon.Fog
        )
        51, 53, 55, 56, 57 -> OperationalWeatherCondition(
            description = "Pioviggine",
            icon = OperationalWeatherIcon.Drizzle
        )
        61, 63, 66 -> OperationalWeatherCondition(
            description = "Pioggia",
            icon = OperationalWeatherIcon.Rain
        )
        65, 67 -> OperationalWeatherCondition(
            description = "Pioggia intensa",
            icon = OperationalWeatherIcon.Rain
        )
        71, 73, 75, 77, 85, 86 -> OperationalWeatherCondition(
            description = "Neve",
            icon = OperationalWeatherIcon.Snow
        )
        80, 81, 82 -> OperationalWeatherCondition(
            description = "Rovesci",
            icon = OperationalWeatherIcon.Showers
        )
        95, 96, 99 -> OperationalWeatherCondition(
            description = "Temporale",
            icon = OperationalWeatherIcon.Thunderstorm
        )
        else -> OperationalWeatherCondition(
            description = "Condizioni non disponibili",
            icon = OperationalWeatherIcon.Unknown
        )
    }

internal fun isOperationalWeatherDaylight(slot: OperationalWeatherHourSlot): Boolean =
    slot.time >= LocalTime.of(6, 0) && slot.time < LocalTime.of(20, 0)

internal fun windLevel(valueKmh: Double?): OperationalWindLevel =
    when {
        valueKmh == null || !valueKmh.isFinite() -> OperationalWindLevel.Missing
        valueKmh < 12.0 -> OperationalWindLevel.Weak
        valueKmh < 24.0 -> OperationalWindLevel.Moderate
        valueKmh < 36.0 -> OperationalWindLevel.Sustained
        else -> OperationalWindLevel.Strong
    }

internal fun WeatherMetrics.temperatureText(): String =
    temperatureC?.takeIf { it.isFinite() }?.let { "${it.roundToInt()} °C" } ?: "-- °C"

internal fun WeatherMetrics.windText(): String =
    windSpeedKmh.formatKmhOrDash()

internal fun WeatherMetrics.gustText(): String =
    windGustsKmh.formatKmhOrDash()

internal fun WeatherMetrics.precipitationText(): String =
    precipitationMm?.takeIf { it.isFinite() && it >= 0.0 }?.let { "${OneDecimalFormatter.format(it)} mm" } ?: "-- mm"

internal fun WeatherMetrics.precipitationProbabilityText(): String? =
    precipitationProbabilityPct?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let { "${it.roundToInt()} %" }

internal fun WeatherMetrics.cloudCoverText(): String =
    cloudCoverPct?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let { "${it.roundToInt()} %" } ?: "-- %"

internal fun WeatherMetrics.visibilityText(): String? =
    visibilityMeters?.takeIf { it.isFinite() && it >= 0.0 }?.let { meters ->
        "${OneDecimalFormatter.format(meters / 1_000.0)} km"
    }

internal fun WeatherMetrics.windDirectionText(): String? =
    windDirectionDegrees?.takeIf { it.isFinite() }?.let { "${it.roundToInt()}°" }

private fun Double?.formatKmhOrDash(): String =
    this?.takeIf { it.isFinite() && it >= 0.0 }?.let { "${it.roundToInt()} km/h" } ?: "-- km/h"

private fun operationalWeatherDayLabel(date: LocalDate, today: LocalDate): String =
    when (date) {
        today -> "OGGI"
        today.plusDays(1) -> "DOMANI"
        else -> DayFormatter.format(date).uppercase(Locale.ITALY)
    }

private val HourFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALY)
private val DayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d", Locale.ITALY)
private val OneDecimalFormatter = java.text.DecimalFormat("0.0")
