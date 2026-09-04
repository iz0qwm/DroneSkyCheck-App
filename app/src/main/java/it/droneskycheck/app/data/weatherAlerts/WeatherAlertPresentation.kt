package it.droneskycheck.app.data.weatherAlerts

import java.time.Instant

val MIN_VIGILANCE_BAR_LEVEL: VigilanceLevel = VigilanceLevel.MODERATE

enum class WeatherBannerKind { CRITICALITY_TODAY, CRITICALITY_TOMORROW, VIGILANCE }

data class WeatherAlertBanner(
    val kind: WeatherBannerKind,
    val criticalityLevel: CriticalityLevel?,
    val vigilanceLevel: VigilanceLevel?,
    val headline: String,
    val detail: String,
    val accessibilityText: String,
    val expires: Instant?
)

data class WeatherLocalFingerprint(
    val value: String
)

data class WeatherLocalChange(
    val previous: WeatherAlertBanner?,
    val current: WeatherAlertBanner?
)

fun weatherAlertBanner(
    response: WeatherAlertResponse,
    now: Instant,
    minimumVigilanceLevel: VigilanceLevel = MIN_VIGILANCE_BAR_LEVEL
): WeatherAlertBanner? {
    val today = response.criticality?.periods?.get("TODAY")
    val todayLevel = today?.maximumLevel ?: CriticalityLevel.NONE
    if (today != null && todayLevel.priority > CriticalityLevel.NONE.priority && today.isActiveAt(now)) {
        return criticalityBanner(
            period = today,
            zoneName = response.criticality.zoneName,
            tomorrow = false
        )
    }

    val tomorrow = response.criticality?.periods?.get("TOMORROW")
    val tomorrowLevel = tomorrow?.maximumLevel ?: CriticalityLevel.NONE
    if (tomorrow != null && tomorrowLevel.priority > CriticalityLevel.NONE.priority) {
        return criticalityBanner(
            period = tomorrow,
            zoneName = response.criticality.zoneName,
            tomorrow = true
        )
    }

    val precipitation = response.vigilance?.periods?.get("TODAY")?.precipitation
    if (precipitation != null && precipitation.level.priority >= minimumVigilanceLevel.priority) {
        val zone = response.vigilance.zoneName?.takeIf(String::isNotBlank)
        val detail = buildString {
            append("Precipitazioni ")
            append(vigilanceLevelLabel(precipitation.level))
            zone?.let { append(" · ").append(it) }
        }
        return WeatherAlertBanner(
            kind = WeatherBannerKind.VIGILANCE,
            criticalityLevel = null,
            vigilanceLevel = precipitation.level,
            headline = "Vigilanza meteorologica",
            detail = detail,
            accessibilityText = "DSC Meteo. Vigilanza meteorologica. $detail",
            expires = null
        )
    }

    val nationalPeriod = response.vigilanceNational?.periods?.get("TODAY")
    if (nationalPeriod?.appliesToPoint == true && nationalPeriod.isActiveAt(now)) {
        val regions = nationalPeriod.matchedRegions.joinToString(" e ")
        val detail = if (regions.isNotBlank()) {
            "Fenomeni segnalati per $regions"
        } else {
            "Fenomeni segnalati per l'area visualizzata"
        }
        return WeatherAlertBanner(
            kind = WeatherBannerKind.VIGILANCE,
            criticalityLevel = null,
            vigilanceLevel = null,
            headline = "Vigilanza meteorologica",
            detail = detail,
            accessibilityText = "DSC Meteo. Vigilanza meteorologica. $detail",
            expires = nationalPeriod.expires
        )
    }
    return null
}

fun WeatherCriticalityPeriod.isActiveAt(now: Instant): Boolean {
    val start = onset ?: return false
    val end = expires ?: return false
    return !now.isBefore(start) && !now.isAfter(end)
}

fun WeatherNationalVigilancePeriod.isActiveAt(now: Instant): Boolean {
    val start = onset ?: return false
    val end = expires ?: return false
    return !now.isBefore(start) && !now.isAfter(end)
}

fun localWeatherFingerprint(response: WeatherAlertResponse): WeatherLocalFingerprint {
    fun criticalityPeriod(name: String): String {
        val period = response.criticality?.periods?.get(name)
        return buildString {
            append(period?.overallLevel?.name ?: "MISSING")
            WeatherRisk.entries.forEach { risk ->
                append('|').append(risk.apiName).append('=')
                append(period?.risks?.get(risk)?.name ?: "MISSING")
            }
        }
    }
    fun vigilancePeriod(name: String): String =
        response.vigilance?.periods?.get(name)?.precipitation?.level?.name ?: "MISSING"
    fun nationalVigilancePeriod(name: String): String {
        val period = response.vigilanceNational?.periods?.get(name)
        return listOf(
            period?.appliesToPoint?.toString() ?: "MISSING",
            period?.matchedRegions?.joinToString(",").orEmpty(),
            period?.precipitationText.orEmpty(),
            period?.onset?.toString().orEmpty(),
            period?.expires?.toString().orEmpty()
        ).joinToString("|")
    }

    return WeatherLocalFingerprint(
        listOf(
            response.criticality?.zoneCode.orEmpty(),
            response.criticality?.zoneName.orEmpty(),
            criticalityPeriod("TODAY"),
            criticalityPeriod("TOMORROW"),
            response.vigilance?.zoneId?.toString().orEmpty(),
            response.vigilance?.zoneName.orEmpty(),
            vigilancePeriod("TODAY"),
            vigilancePeriod("TOMORROW"),
            vigilancePeriod("AFTER_TOMORROW"),
            nationalVigilancePeriod("TODAY"),
            nationalVigilancePeriod("TOMORROW"),
            nationalVigilancePeriod("AFTER_TOMORROW")
        ).joinToString(";")
    )
}

fun localWeatherChange(
    previousResponse: WeatherAlertResponse,
    currentResponse: WeatherAlertResponse,
    now: Instant
): WeatherLocalChange? {
    if (localWeatherFingerprint(previousResponse) == localWeatherFingerprint(currentResponse)) return null
    return WeatherLocalChange(
        previous = weatherAlertBanner(previousResponse, now),
        current = weatherAlertBanner(currentResponse, now)
    )
}

fun criticalityLevelLabel(level: CriticalityLevel): String = when (level) {
    CriticalityLevel.YELLOW -> "gialla"
    CriticalityLevel.ORANGE -> "arancione"
    CriticalityLevel.RED -> "rossa"
    CriticalityLevel.NONE -> "assente"
    CriticalityLevel.UNKNOWN -> "non disponibile"
}

fun vigilanceLevelLabel(level: VigilanceLevel): String = when (level) {
    VigilanceLevel.WEAK -> "deboli"
    VigilanceLevel.MODERATE -> "moderate"
    VigilanceLevel.HEAVY -> "forti"
    VigilanceLevel.VERY_HEAVY -> "molto forti"
    VigilanceLevel.NONE -> "assenti"
    VigilanceLevel.UNKNOWN -> "non disponibili"
}

fun formatActiveRisks(period: WeatherCriticalityPeriod): String {
    val active = WeatherRisk.entries.filter { risk ->
        (period.risks[risk] ?: CriticalityLevel.NONE).priority > CriticalityLevel.NONE.priority
    }.map(WeatherRisk::italianLabel)
    return when (active.size) {
        0 -> ""
        1 -> active.single()
        2 -> active.joinToString(" e ")
        else -> active.dropLast(1).joinToString(", ") + " e " + active.last()
    }
}

private fun criticalityBanner(
    period: WeatherCriticalityPeriod,
    zoneName: String?,
    tomorrow: Boolean
): WeatherAlertBanner {
    val level = period.maximumLevel
    val riskText = formatActiveRisks(period)
    val prefix = if (tomorrow) "Domani allerta" else "Allerta"
    val alertText = "$prefix ${criticalityLevelLabel(level)}" +
        riskText.takeIf(String::isNotBlank)?.let { " per $it" }.orEmpty()
    val detail = alertText + zoneName?.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
    return WeatherAlertBanner(
        kind = if (tomorrow) WeatherBannerKind.CRITICALITY_TOMORROW else WeatherBannerKind.CRITICALITY_TODAY,
        criticalityLevel = level,
        vigilanceLevel = null,
        headline = if (tomorrow) "DSC METEO · DOMANI" else "DSC METEO",
        detail = detail,
        accessibilityText = "$alertText. ${zoneName.orEmpty()}".trim(),
        expires = period.expires
    )
}
