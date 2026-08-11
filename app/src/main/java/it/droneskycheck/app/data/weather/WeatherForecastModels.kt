package it.droneskycheck.app.data.weather

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

data class WeatherForecastApiResponse(
    val schemaVersion: Int,
    val provider: String,
    val generatedAt: String?,
    val providerFetchedAt: String?,
    val forecastDays: Int?,
    val location: WeatherForecastLocationDto,
    val units: WeatherForecastUnitsDto,
    val forecast: List<WeatherForecastHourDto>,
    val days: List<WeatherForecastDayDto>,
    val warnings: List<WeatherForecastWarningDto>,
    val cache: WeatherForecastCacheDto?
)

data class WeatherForecastLocationDto(
    val requested: WeatherForecastCoordinatesDto?,
    val query: WeatherForecastQueryCoordinatesDto?,
    val provider: WeatherForecastProviderCoordinatesDto?,
    val timezone: String?,
    val timezoneAbbreviation: String?,
    val utcOffsetSeconds: Int?
)

data class WeatherForecastCoordinatesDto(
    val latitude: Double?,
    val longitude: Double?
)

data class WeatherForecastQueryCoordinatesDto(
    val latitude: Double?,
    val longitude: Double?,
    val source: String?,
    val bucketSizeDegrees: Double?
)

data class WeatherForecastProviderCoordinatesDto(
    val latitude: Double?,
    val longitude: Double?,
    val elevationMeters: Double?
)

data class WeatherForecastUnitsDto(
    val temperatureC: String?,
    val windSpeedKmh: String?,
    val windGustsKmh: String?,
    val windDirectionDegrees: String?,
    val precipitationMm: String?,
    val precipitationProbabilityPct: String?,
    val visibilityMeters: String?,
    val cloudCoverPct: String?
)

data class WeatherForecastHourDto(
    val time: String?,
    val utcTime: String?,
    val localTime: String?,
    val utcOffsetSeconds: Int?,
    val temperatureC: Double?,
    val windSpeedKmh: Double?,
    val windGustsKmh: Double?,
    val windDirectionDegrees: Double?,
    val precipitationMm: Double?,
    val precipitationProbabilityPct: Double?,
    val visibilityMeters: Double?,
    val cloudCoverPct: Double?,
    val weatherCode: Int?,
    val missingFields: List<String>
)

data class WeatherForecastDayDto(
    val date: String?,
    val sunrise: String?,
    val sunriseUtc: String?,
    val sunriseLocalTime: String?,
    val sunset: String?,
    val sunsetUtc: String?,
    val sunsetLocalTime: String?,
    val utcOffsetSeconds: Int?,
    val missingFields: List<String>
)

data class WeatherForecastWarningDto(
    val code: String?,
    val field: String?,
    val message: String?
)

data class WeatherForecastCacheDto(
    val hit: Boolean?,
    val ttlSeconds: Int?,
    val key: String?
)

data class WeatherForecast(
    val location: WeatherForecastLocation,
    val timezone: ZoneId?,
    val generatedAt: Instant?,
    val providerFetchedAt: Instant?,
    val hours: List<WeatherForecastHour>,
    val days: List<WeatherForecastDay>,
    val warnings: List<WeatherForecastWarning>,
    val metadata: WeatherForecastMetadata
)

data class WeatherForecastLocation(
    val requested: WeatherCoordinates?,
    val query: WeatherQueryCoordinates?,
    val provider: WeatherProviderCoordinates?,
    val timezoneId: String?,
    val timezoneAbbreviation: String?,
    val utcOffsetSeconds: Int?
)

data class WeatherCoordinates(
    val latitude: Double?,
    val longitude: Double?
)

data class WeatherQueryCoordinates(
    val latitude: Double?,
    val longitude: Double?,
    val source: String?,
    val bucketSizeDegrees: Double?
)

data class WeatherProviderCoordinates(
    val latitude: Double?,
    val longitude: Double?,
    val elevationMeters: Double?
)

data class WeatherForecastHour(
    val instant: Instant,
    val offsetDateTime: OffsetDateTime?,
    val localDateTime: LocalDateTime?,
    val localTimeText: String?,
    val utcOffsetSeconds: Int?,
    val metrics: WeatherMetrics,
    val missingFields: List<String>
)

data class WeatherForecastDay(
    val date: LocalDate?,
    val sunrise: Instant?,
    val sunriseOffsetDateTime: OffsetDateTime?,
    val sunriseLocalTimeText: String?,
    val sunset: Instant?,
    val sunsetOffsetDateTime: OffsetDateTime?,
    val sunsetLocalTimeText: String?,
    val utcOffsetSeconds: Int?,
    val missingFields: List<String>
)

data class WeatherForecastWarning(
    val code: String?,
    val field: String?,
    val message: String?
)

data class WeatherForecastMetadata(
    val schemaVersion: Int,
    val provider: String,
    val forecastDays: Int?,
    val units: WeatherForecastUnitsDto,
    val cache: WeatherForecastCacheDto?
)

sealed class WeatherForecastMappingError(message: String) : Exception(message) {
    class UnsupportedSchemaVersion(val schemaVersion: Int) :
        WeatherForecastMappingError("Unsupported weather forecast schemaVersion $schemaVersion")

    object EmptyForecast :
        WeatherForecastMappingError("Weather forecast is empty")

    class InvalidTime(val value: String?) :
        WeatherForecastMappingError("Invalid weather forecast utcTime: $value")
}

fun parseWeatherForecastApiResponse(json: JSONObject): WeatherForecastApiResponse =
    WeatherForecastApiResponse(
        schemaVersion = json.optInt("schemaVersion", 0),
        provider = json.optStringOrNull("provider").orEmpty(),
        generatedAt = json.optStringOrNull("generatedAt"),
        providerFetchedAt = json.optStringOrNull("providerFetchedAt"),
        forecastDays = json.optIntOrNull("forecastDays"),
        location = json.optJSONObject("location").toWeatherForecastLocationDto(),
        units = json.optJSONObject("units").toWeatherForecastUnitsDto(),
        forecast = json.optJSONArray("forecast").toObjectList { it.toWeatherForecastHourDto() },
        days = json.optJSONArray("days").toObjectList { it.toWeatherForecastDayDto() },
        warnings = json.optJSONArray("warnings").toObjectList { it.toWeatherForecastWarningDto() },
        cache = json.optJSONObject("cache")?.toWeatherForecastCacheDto()
    )

fun WeatherForecastApiResponse.toDomain(): WeatherForecast {
    if (schemaVersion != 1) {
        throw WeatherForecastMappingError.UnsupportedSchemaVersion(schemaVersion)
    }
    if (forecast.isEmpty()) {
        throw WeatherForecastMappingError.EmptyForecast
    }

    val zoneId = location.timezone?.toZoneIdOrNull()
    return WeatherForecast(
        location = location.toDomain(),
        timezone = zoneId,
        generatedAt = generatedAt.toInstantOrNull(),
        providerFetchedAt = providerFetchedAt.toInstantOrNull(),
        hours = forecast.map { it.toDomain() },
        days = days.map { it.toDomain() },
        warnings = warnings.map { it.toDomain() },
        metadata = WeatherForecastMetadata(
            schemaVersion = schemaVersion,
            provider = provider,
            forecastDays = forecastDays,
            units = units,
            cache = cache
        )
    )
}

fun WeatherForecastHour.toWeatherMetrics(): WeatherMetrics = metrics

private fun WeatherForecastLocationDto.toDomain(): WeatherForecastLocation =
    WeatherForecastLocation(
        requested = requested?.let { WeatherCoordinates(it.latitude, it.longitude) },
        query = query?.let {
            WeatherQueryCoordinates(
                latitude = it.latitude,
                longitude = it.longitude,
                source = it.source,
                bucketSizeDegrees = it.bucketSizeDegrees
            )
        },
        provider = provider?.let {
            WeatherProviderCoordinates(
                latitude = it.latitude,
                longitude = it.longitude,
                elevationMeters = it.elevationMeters
            )
        },
        timezoneId = timezone,
        timezoneAbbreviation = timezoneAbbreviation,
        utcOffsetSeconds = utcOffsetSeconds
    )

private fun WeatherForecastHourDto.toDomain(): WeatherForecastHour {
    val instant = utcTime.toInstantOrNull()
        ?: throw WeatherForecastMappingError.InvalidTime(utcTime)
    val offsetDateTime = time.toOffsetDateTimeOrNull()
    return WeatherForecastHour(
        instant = instant,
        offsetDateTime = offsetDateTime,
        localDateTime = localTime.toLocalDateTimeOrNull() ?: offsetDateTime?.toLocalDateTime(),
        localTimeText = localTime,
        utcOffsetSeconds = utcOffsetSeconds,
        metrics = WeatherMetrics(
            windSpeedKmh = windSpeedKmh,
            windGustsKmh = windGustsKmh,
            windDirectionDegrees = windDirectionDegrees,
            precipitationMm = precipitationMm,
            precipitationProbabilityPct = precipitationProbabilityPct,
            visibilityMeters = visibilityMeters,
            weatherCode = weatherCode,
            temperatureC = temperatureC,
            cloudCoverPct = cloudCoverPct
        ),
        missingFields = missingFields
    )
}

private fun WeatherForecastDayDto.toDomain(): WeatherForecastDay {
    val sunriseOffset = sunrise.toOffsetDateTimeOrNull()
    val sunsetOffset = sunset.toOffsetDateTimeOrNull()
    return WeatherForecastDay(
        date = date.toLocalDateOrNull(),
        sunrise = sunriseUtc.toInstantOrNull() ?: sunriseOffset?.toInstant(),
        sunriseOffsetDateTime = sunriseOffset,
        sunriseLocalTimeText = sunriseLocalTime,
        sunset = sunsetUtc.toInstantOrNull() ?: sunsetOffset?.toInstant(),
        sunsetOffsetDateTime = sunsetOffset,
        sunsetLocalTimeText = sunsetLocalTime,
        utcOffsetSeconds = utcOffsetSeconds,
        missingFields = missingFields
    )
}

private fun WeatherForecastWarningDto.toDomain(): WeatherForecastWarning =
    WeatherForecastWarning(
        code = code,
        field = field,
        message = message
    )

private fun JSONObject?.toWeatherForecastLocationDto(): WeatherForecastLocationDto {
    val json = this ?: JSONObject()
    return WeatherForecastLocationDto(
        requested = json.optJSONObject("requested")?.toWeatherForecastCoordinatesDto(),
        query = json.optJSONObject("query")?.toWeatherForecastQueryCoordinatesDto(),
        provider = json.optJSONObject("provider")?.toWeatherForecastProviderCoordinatesDto(),
        timezone = json.optStringOrNull("timezone"),
        timezoneAbbreviation = json.optStringOrNull("timezoneAbbreviation"),
        utcOffsetSeconds = json.optIntOrNull("utcOffsetSeconds")
    )
}

private fun JSONObject.toWeatherForecastCoordinatesDto(): WeatherForecastCoordinatesDto =
    WeatherForecastCoordinatesDto(
        latitude = optDoubleOrNull("latitude"),
        longitude = optDoubleOrNull("longitude")
    )

private fun JSONObject.toWeatherForecastQueryCoordinatesDto(): WeatherForecastQueryCoordinatesDto =
    WeatherForecastQueryCoordinatesDto(
        latitude = optDoubleOrNull("latitude"),
        longitude = optDoubleOrNull("longitude"),
        source = optStringOrNull("source"),
        bucketSizeDegrees = optDoubleOrNull("bucketSizeDegrees")
    )

private fun JSONObject.toWeatherForecastProviderCoordinatesDto(): WeatherForecastProviderCoordinatesDto =
    WeatherForecastProviderCoordinatesDto(
        latitude = optDoubleOrNull("latitude"),
        longitude = optDoubleOrNull("longitude"),
        elevationMeters = optDoubleOrNull("elevationMeters")
    )

private fun JSONObject?.toWeatherForecastUnitsDto(): WeatherForecastUnitsDto {
    val json = this ?: JSONObject()
    return WeatherForecastUnitsDto(
        temperatureC = json.optStringOrNull("temperatureC"),
        windSpeedKmh = json.optStringOrNull("windSpeedKmh"),
        windGustsKmh = json.optStringOrNull("windGustsKmh"),
        windDirectionDegrees = json.optStringOrNull("windDirectionDegrees"),
        precipitationMm = json.optStringOrNull("precipitationMm"),
        precipitationProbabilityPct = json.optStringOrNull("precipitationProbabilityPct"),
        visibilityMeters = json.optStringOrNull("visibilityMeters"),
        cloudCoverPct = json.optStringOrNull("cloudCoverPct")
    )
}

private fun JSONObject.toWeatherForecastHourDto(): WeatherForecastHourDto =
    WeatherForecastHourDto(
        time = optStringOrNull("time"),
        utcTime = optStringOrNull("utcTime"),
        localTime = optStringOrNull("localTime"),
        utcOffsetSeconds = optIntOrNull("utcOffsetSeconds"),
        temperatureC = optDoubleOrNull("temperatureC"),
        windSpeedKmh = optDoubleOrNull("windSpeedKmh"),
        windGustsKmh = optDoubleOrNull("windGustsKmh"),
        windDirectionDegrees = optDoubleOrNull("windDirectionDegrees"),
        precipitationMm = optDoubleOrNull("precipitationMm"),
        precipitationProbabilityPct = optDoubleOrNull("precipitationProbabilityPct"),
        visibilityMeters = optDoubleOrNull("visibilityMeters"),
        cloudCoverPct = optDoubleOrNull("cloudCoverPct"),
        weatherCode = optIntOrNull("weatherCode"),
        missingFields = optJSONArray("missingFields").toStringList()
    )

private fun JSONObject.toWeatherForecastDayDto(): WeatherForecastDayDto =
    WeatherForecastDayDto(
        date = optStringOrNull("date"),
        sunrise = optStringOrNull("sunrise"),
        sunriseUtc = optStringOrNull("sunriseUtc"),
        sunriseLocalTime = optStringOrNull("sunriseLocalTime"),
        sunset = optStringOrNull("sunset"),
        sunsetUtc = optStringOrNull("sunsetUtc"),
        sunsetLocalTime = optStringOrNull("sunsetLocalTime"),
        utcOffsetSeconds = optIntOrNull("utcOffsetSeconds"),
        missingFields = optJSONArray("missingFields").toStringList()
    )

private fun JSONObject.toWeatherForecastWarningDto(): WeatherForecastWarningDto =
    WeatherForecastWarningDto(
        code = optStringOrNull("code"),
        field = optStringOrNull("field"),
        message = optStringOrNull("message")
    )

private fun JSONObject.toWeatherForecastCacheDto(): WeatherForecastCacheDto =
    WeatherForecastCacheDto(
        hit = optBooleanOrNull("hit"),
        ttlSeconds = optIntOrNull("ttlSeconds"),
        key = optStringOrNull("key")
    )

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        opt(index)?.toString()?.takeIf { it.isNotBlank() }
    }
}

private fun <T> JSONArray?.toObjectList(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.let(transform)
    }
}

private fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return opt(name)?.toString()?.takeIf { it.isNotBlank() }
}

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Number -> optDouble(name)
        else -> optString(name).toDoubleOrNull()
    }?.takeIf { it.isFinite() }

private fun JSONObject.optIntOrNull(name: String): Int? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Number -> optInt(name)
        else -> optString(name).toIntOrNull()
    }

private fun JSONObject.optBooleanOrNull(name: String): Boolean? =
    when {
        !has(name) || isNull(name) -> null
        opt(name) is Boolean -> optBoolean(name)
        optString(name).equals("true", ignoreCase = true) -> true
        optString(name).equals("false", ignoreCase = true) -> false
        else -> null
    }

private fun String?.toInstantOrNull(): Instant? =
    this?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun String?.toOffsetDateTimeOrNull(): OffsetDateTime? =
    this?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

private fun String?.toLocalDateTimeOrNull(): LocalDateTime? =
    this?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

private fun String?.toLocalDateOrNull(): LocalDate? =
    this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun String.toZoneIdOrNull(): ZoneId? =
    runCatching { ZoneId.of(this) }.getOrNull()
