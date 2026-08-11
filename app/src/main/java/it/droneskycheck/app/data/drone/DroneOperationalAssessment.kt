package it.droneskycheck.app.data.drone

import it.droneskycheck.app.data.LocalDrone
import it.droneskycheck.app.data.weather.OpenMeteoWeatherCodeClassifier
import it.droneskycheck.app.data.weather.WeatherAssessment
import it.droneskycheck.app.data.weather.WeatherCodeCategory
import it.droneskycheck.app.data.weather.WeatherMetrics
import it.droneskycheck.app.data.weather.WeatherState
import kotlin.math.max
import kotlin.math.roundToInt

data class DroneOperationalCapabilities(
    val droneId: String,
    val displayName: String,
    val manufacturer: String = "",
    val model: String = "",
    val massGrams: Double? = null,
    val euClass: String? = null,
    val maxWindResistanceMs: Double? = null,
    val minOperatingTemperatureC: Double? = null,
    val maxOperatingTemperatureC: Double? = null,
    val weatherProtection: DroneWeatherProtection = DroneWeatherProtection.UNKNOWN,
    val precipitationCapability: DronePrecipitationCapability = DronePrecipitationCapability.UNKNOWN,
    val massSource: DroneCapabilitySource = DroneCapabilitySource.UNKNOWN,
    val euClassSource: DroneCapabilitySource = DroneCapabilitySource.UNKNOWN,
    val windResistanceSource: DroneCapabilitySource = DroneCapabilitySource.UNKNOWN,
    val temperatureRangeSource: DroneCapabilitySource = DroneCapabilitySource.UNKNOWN,
    val precipitationSource: DroneCapabilitySource = DroneCapabilitySource.UNKNOWN
)

enum class DroneCapabilitySource {
    MANUFACTURER,
    USER_PROVIDED,
    CATALOG,
    UNKNOWN
}

enum class DroneWeatherProtection {
    UNKNOWN,
    NONE_DECLARED,
    PROTECTED
}

enum class DronePrecipitationCapability {
    UNKNOWN,
    NOT_DECLARED,
    LIGHT_PRECIPITATION,
    RAIN_RESISTANT
}

data class DroneOperationalAssessment(
    val level: DroneOperationalLevel,
    val score: Int?,
    val factors: List<DroneOperationalFactor>,
    val warnings: List<String>,
    val dataCompleteness: DroneDataCompleteness,
    val capabilities: DroneOperationalCapabilities
)

enum class DroneOperationalLevel {
    FAVORABLE,
    ACCEPTABLE,
    CAUTION,
    UNFAVORABLE,
    UNKNOWN
}

enum class DroneDataCompleteness {
    FULL,
    PARTIAL,
    MINIMAL
}

data class DroneOperationalFactor(
    val type: DroneOperationalFactorType,
    val level: DroneOperationalLevel,
    val title: String,
    val message: String,
    val observedValue: Double? = null,
    val limitValue: Double? = null,
    val unit: String? = null,
    val source: DroneCapabilitySource = DroneCapabilitySource.UNKNOWN
)

enum class DroneOperationalFactorType {
    WIND,
    GUSTS,
    TEMPERATURE,
    PRECIPITATION,
    VISIBILITY,
    CLOUDS,
    WEATHER,
    DATA
}

data class DroneOperationalAssessmentConfig(
    val wideMarginRatio: Double = 0.50,
    val cautionRatio: Double = 0.70,
    val reducedMarginRatio: Double = 0.85,
    val overLimitRatio: Double = 1.00,
    val highGustSpreadMs: Double = 5.0,
    val highGustRatio: Double = 1.8,
    val gustRatioMinimumWindMs: Double = 2.2,
    val temperatureMarginC: Double = 3.0,
    val favorableScore: Int = 80,
    val acceptableScore: Int = 65,
    val cautionScore: Int = 45
) {
    companion object {
        val DEFAULT = DroneOperationalAssessmentConfig()
    }
}

class DroneOperationalAssessmentEngine(
    private val config: DroneOperationalAssessmentConfig = DroneOperationalAssessmentConfig.DEFAULT
) {
    fun assess(
        metrics: WeatherMetrics?,
        capabilities: DroneOperationalCapabilities?,
        weatherAssessment: WeatherAssessment? = null
    ): DroneOperationalAssessment? {
        if (capabilities == null) return null
        if (metrics == null) {
            return DroneOperationalAssessment(
                level = DroneOperationalLevel.UNKNOWN,
                score = null,
                factors = listOf(
                    DroneOperationalFactor(
                        type = DroneOperationalFactorType.DATA,
                        level = DroneOperationalLevel.UNKNOWN,
                        title = "Dati meteo",
                        message = "Dati meteo insufficienti per valutare il drone."
                    )
                ),
                warnings = listOf("Assessment drone sospeso: dati meteo non disponibili."),
                dataCompleteness = capabilities.dataCompleteness(),
                capabilities = capabilities
            )
        }

        val factors = mutableListOf<DroneOperationalFactor>()
        val warnings = mutableListOf<String>()
        var score = 100
        var cap = 100

        fun applyPenalty(
            penalty: Int,
            capAt: Int? = null,
            warning: String? = null
        ) {
            score -= penalty
            if (capAt != null) cap = minOf(cap, capAt)
            if (warning != null) warnings += warning
        }

        val completeness = capabilities.dataCompleteness()

        evaluateWind(metrics, capabilities, factors, warnings) { penalty, capAt ->
            applyPenalty(penalty, capAt)
        }
        evaluateTemperature(metrics, capabilities, factors, warnings) { penalty, capAt ->
            applyPenalty(penalty, capAt)
        }
        evaluatePrecipitation(metrics, capabilities, factors, warnings) { penalty, capAt ->
            applyPenalty(penalty, capAt)
        }
        evaluateGeneralWeather(weatherAssessment, factors) { penalty, capAt ->
            applyPenalty(penalty, capAt)
        }

        if (metrics.windSpeedKmh == null || metrics.windGustsKmh == null || metrics.weatherCode == null) {
            factors += DroneOperationalFactor(
                type = DroneOperationalFactorType.DATA,
                level = DroneOperationalLevel.UNKNOWN,
                title = "Dati meteo",
                message = "Mancano vento, raffiche o codice meteo: valutazione parziale."
            )
            applyPenalty(25, 70, "Dati meteo incompleti per il confronto con il drone.")
        }

        if (completeness == DroneDataCompleteness.MINIMAL && factors.none { it.level == DroneOperationalLevel.UNFAVORABLE }) {
            return DroneOperationalAssessment(
                level = DroneOperationalLevel.UNKNOWN,
                score = null,
                factors = factors.ifEmpty {
                    listOf(
                        DroneOperationalFactor(
                            type = DroneOperationalFactorType.DATA,
                            level = DroneOperationalLevel.UNKNOWN,
                            title = "Profilo tecnico",
                            message = "Nel profilo drone non sono presenti limiti meteo dichiarati."
                        )
                    )
                },
                warnings = warnings + "Profilo tecnico drone incompleto: nessun limite meteo dichiarato.",
                dataCompleteness = completeness,
                capabilities = capabilities
            )
        }

        val finalScore = score.coerceIn(0, cap).coerceIn(0, 100)
        val level = when {
            factors.any { it.level == DroneOperationalLevel.UNFAVORABLE } -> DroneOperationalLevel.UNFAVORABLE
            finalScore >= config.favorableScore -> DroneOperationalLevel.FAVORABLE
            finalScore >= config.acceptableScore -> DroneOperationalLevel.ACCEPTABLE
            finalScore >= config.cautionScore -> DroneOperationalLevel.CAUTION
            else -> DroneOperationalLevel.UNFAVORABLE
        }

        return DroneOperationalAssessment(
            level = level,
            score = finalScore,
            factors = factors.ifEmpty {
                listOf(
                    DroneOperationalFactor(
                        type = DroneOperationalFactorType.WEATHER,
                        level = level,
                        title = "Condizioni",
                        message = "Nessuna criticita specifica per i dati tecnici disponibili."
                    )
                )
            },
            warnings = warnings.distinct(),
            dataCompleteness = completeness,
            capabilities = capabilities
        )
    }

    private fun evaluateWind(
        metrics: WeatherMetrics,
        capabilities: DroneOperationalCapabilities,
        factors: MutableList<DroneOperationalFactor>,
        warnings: MutableList<String>,
        applyPenalty: (Int, Int?) -> Unit
    ) {
        val maxWind = capabilities.maxWindResistanceMs
        val wind = metrics.windSpeedKmh?.kmhToMs()
        val gust = metrics.windGustsKmh?.kmhToMs()
        if (maxWind == null || maxWind <= 0.0) {
            factors += DroneOperationalFactor(
                type = DroneOperationalFactorType.WIND,
                level = DroneOperationalLevel.UNKNOWN,
                title = "Vento",
                message = "Resistenza massima al vento non dichiarata nel profilo drone."
            )
            warnings += "Resistenza al vento del drone non disponibile."
            return
        }

        wind?.let {
            val ratio = it / maxWind
            val level = ratioLevel(ratio)
            factors += DroneOperationalFactor(
                type = DroneOperationalFactorType.WIND,
                level = level,
                title = "Vento medio",
                message = windMessage(ratio),
                observedValue = it,
                limitValue = maxWind,
                unit = "m/s",
                source = capabilities.windResistanceSource
            )
            val penalty = when {
                ratio >= config.overLimitRatio -> 35
                ratio >= config.reducedMarginRatio -> 20
                ratio >= config.cautionRatio -> 12
                ratio >= config.wideMarginRatio -> 6
                else -> 0
            }
            val capAt = when {
                ratio >= config.overLimitRatio -> 45
                ratio >= config.reducedMarginRatio -> 55
                ratio >= config.cautionRatio -> 70
                else -> null
            }
            if (penalty > 0) applyPenalty(penalty, capAt)
        }

        gust?.let {
            val ratio = it / maxWind
            val level = ratioLevel(ratio)
            factors += DroneOperationalFactor(
                type = DroneOperationalFactorType.GUSTS,
                level = level,
                title = "Raffiche",
                message = gustMessage(ratio),
                observedValue = it,
                limitValue = maxWind,
                unit = "m/s",
                source = capabilities.windResistanceSource
            )
            val penalty = when {
                ratio >= config.overLimitRatio -> 55
                ratio >= config.reducedMarginRatio -> 40
                ratio >= config.cautionRatio -> 25
                ratio >= config.wideMarginRatio -> 10
                else -> 0
            }
            val capAt = when {
                ratio >= config.overLimitRatio -> 35
                ratio >= config.reducedMarginRatio -> 45
                ratio >= config.cautionRatio -> 65
                else -> null
            }
            if (penalty > 0) applyPenalty(penalty, capAt)
            if (ratio >= config.reducedMarginRatio) {
                warnings += if (ratio >= config.overLimitRatio) {
                    "Le raffiche previste superano il valore dichiarato per il drone."
                } else {
                    "Margine operativo ridotto sulle raffiche."
                }
            }
        }

        if (wind != null && gust != null) {
            val spread = max(0.0, gust - wind)
            val ratio = if (wind >= config.gustRatioMinimumWindMs) gust / wind else null
            if (spread >= config.highGustSpreadMs || (ratio != null && ratio >= config.highGustRatio)) {
                factors += DroneOperationalFactor(
                    type = DroneOperationalFactorType.GUSTS,
                    level = DroneOperationalLevel.CAUTION,
                    title = "Variabilita raffiche",
                    message = "Raffiche sensibilmente superiori al vento medio.",
                    observedValue = spread,
                    unit = "m/s"
                )
                applyPenalty(15, 70)
            }
        }
    }

    private fun evaluateTemperature(
        metrics: WeatherMetrics,
        capabilities: DroneOperationalCapabilities,
        factors: MutableList<DroneOperationalFactor>,
        warnings: MutableList<String>,
        applyPenalty: (Int, Int?) -> Unit
    ) {
        val temperature = metrics.temperatureC ?: return
        val minTemp = capabilities.minOperatingTemperatureC
        val maxTemp = capabilities.maxOperatingTemperatureC
        if (minTemp == null || maxTemp == null) {
            factors += DroneOperationalFactor(
                type = DroneOperationalFactorType.TEMPERATURE,
                level = DroneOperationalLevel.UNKNOWN,
                title = "Temperatura",
                message = "Range operativo temperatura non dichiarato nel profilo drone."
            )
            return
        }

        val outside = temperature < minTemp || temperature > maxTemp
        val nearLimit = temperature - minTemp <= config.temperatureMarginC ||
            maxTemp - temperature <= config.temperatureMarginC
        val level = when {
            outside -> DroneOperationalLevel.UNFAVORABLE
            nearLimit -> DroneOperationalLevel.CAUTION
            else -> DroneOperationalLevel.FAVORABLE
        }
        factors += DroneOperationalFactor(
            type = DroneOperationalFactorType.TEMPERATURE,
            level = level,
            title = "Temperatura",
            message = when {
                outside -> "Temperatura fuori dal range operativo dichiarato."
                nearLimit -> "Temperatura vicina al limite operativo dichiarato."
                else -> "Temperatura dentro il range operativo dichiarato."
            },
            observedValue = temperature,
            unit = "C",
            source = capabilities.temperatureRangeSource
        )
        when {
            outside -> {
                applyPenalty(45, 40)
                warnings += "Temperatura fuori dal range operativo dichiarato."
            }
            nearLimit -> applyPenalty(18, 70)
        }
    }

    private fun evaluatePrecipitation(
        metrics: WeatherMetrics,
        capabilities: DroneOperationalCapabilities,
        factors: MutableList<DroneOperationalFactor>,
        warnings: MutableList<String>,
        applyPenalty: (Int, Int?) -> Unit
    ) {
        val precipitation = metrics.precipitationMm?.takeIf { it >= 0.0 }
        val category = OpenMeteoWeatherCodeClassifier.classify(metrics.weatherCode)
        val indicatesRain = precipitation?.let { it > 0.0 } == true || category.indicatesPrecipitationForDrone()
        if (!indicatesRain) {
            factors += DroneOperationalFactor(
                type = DroneOperationalFactorType.PRECIPITATION,
                level = DroneOperationalLevel.FAVORABLE,
                title = "Precipitazioni",
                message = "Nessuna precipitazione rilevante nei dati meteo.",
                observedValue = precipitation,
                unit = "mm/h"
            )
            return
        }

        val capability = capabilities.precipitationCapability
        val level = when (capability) {
            DronePrecipitationCapability.RAIN_RESISTANT -> DroneOperationalLevel.ACCEPTABLE
            DronePrecipitationCapability.LIGHT_PRECIPITATION -> if ((precipitation ?: 0.0) <= 0.3) {
                DroneOperationalLevel.ACCEPTABLE
            } else {
                DroneOperationalLevel.CAUTION
            }
            DronePrecipitationCapability.UNKNOWN,
            DronePrecipitationCapability.NOT_DECLARED -> DroneOperationalLevel.UNFAVORABLE
        }
        factors += DroneOperationalFactor(
            type = DroneOperationalFactorType.PRECIPITATION,
            level = level,
            title = "Precipitazioni",
            message = when (capability) {
                DronePrecipitationCapability.UNKNOWN -> "Compatibilita con precipitazioni non dichiarata."
                DronePrecipitationCapability.NOT_DECLARED -> "Il profilo non dichiara capacita in precipitazione."
                DronePrecipitationCapability.LIGHT_PRECIPITATION -> "Compatibilita limitata: serve prudenza."
                DronePrecipitationCapability.RAIN_RESISTANT -> "Capacita dichiarata per precipitazioni, da valutare con prudenza."
            },
            observedValue = precipitation,
            unit = "mm/h",
            source = capabilities.precipitationSource
        )
        when (level) {
            DroneOperationalLevel.UNFAVORABLE -> {
                applyPenalty(35, 55)
                warnings += "Compatibilita con precipitazioni non dichiarata."
            }
            DroneOperationalLevel.CAUTION -> applyPenalty(20, 70)
            DroneOperationalLevel.ACCEPTABLE -> applyPenalty(8, null)
            else -> Unit
        }
    }

    private fun evaluateGeneralWeather(
        weatherAssessment: WeatherAssessment?,
        factors: MutableList<DroneOperationalFactor>,
        applyPenalty: (Int, Int?) -> Unit
    ) {
        when (weatherAssessment?.state) {
            WeatherState.UNFAVORABLE -> {
                factors += DroneOperationalFactor(
                    type = DroneOperationalFactorType.WEATHER,
                    level = DroneOperationalLevel.CAUTION,
                    title = "Meteo generale",
                    message = "Le condizioni meteo generali sono sfavorevoli."
                )
                applyPenalty(20, 55)
            }
            WeatherState.CAUTION -> applyPenalty(8, 75)
            WeatherState.INSUFFICIENT_DATA -> applyPenalty(20, 70)
            WeatherState.FAVORABLE,
            null -> Unit
        }
    }

    private fun ratioLevel(ratio: Double): DroneOperationalLevel =
        when {
            ratio >= config.overLimitRatio -> DroneOperationalLevel.UNFAVORABLE
            ratio >= config.reducedMarginRatio -> DroneOperationalLevel.UNFAVORABLE
            ratio >= config.cautionRatio -> DroneOperationalLevel.CAUTION
            ratio >= config.wideMarginRatio -> DroneOperationalLevel.ACCEPTABLE
            else -> DroneOperationalLevel.FAVORABLE
        }

    private fun windMessage(ratio: Double): String =
        when {
            ratio >= config.overLimitRatio -> "Vento medio oltre il valore dichiarato."
            ratio >= config.reducedMarginRatio -> "Vento medio molto vicino al valore dichiarato."
            ratio >= config.cautionRatio -> "Vento medio con margine ridotto."
            ratio >= config.wideMarginRatio -> "Vento medio con margine moderato."
            else -> "Vento medio con margine ampio."
        }

    private fun gustMessage(ratio: Double): String =
        when {
            ratio >= config.overLimitRatio -> "Raffiche oltre il valore dichiarato."
            ratio >= config.reducedMarginRatio -> "Raffiche molto vicine al valore dichiarato."
            ratio >= config.cautionRatio -> "Raffiche con margine ridotto."
            ratio >= config.wideMarginRatio -> "Raffiche con margine moderato."
            else -> "Raffiche con margine ampio."
        }
}

fun LocalDrone.toOperationalCapabilities(): DroneOperationalCapabilities =
    DroneOperationalCapabilities(
        droneId = id,
        displayName = displayName,
        manufacturer = manufacturer,
        model = model,
        massGrams = weight,
        euClass = classLabel.takeIf { it.isNotBlank() },
        massSource = weight?.let { DroneCapabilitySource.USER_PROVIDED } ?: DroneCapabilitySource.UNKNOWN,
        euClassSource = classLabel.takeIf { it.isNotBlank() }?.let { DroneCapabilitySource.USER_PROVIDED }
            ?: DroneCapabilitySource.UNKNOWN
    )

fun DroneOperationalCapabilities.dataCompleteness(): DroneDataCompleteness {
    val hasWind = maxWindResistanceMs != null && maxWindResistanceMs > 0.0
    val hasTemperature = minOperatingTemperatureC != null && maxOperatingTemperatureC != null
    val hasPrecipitation = precipitationCapability != DronePrecipitationCapability.UNKNOWN &&
        precipitationCapability != DronePrecipitationCapability.NOT_DECLARED
    return when {
        hasWind && hasTemperature && hasPrecipitation -> DroneDataCompleteness.FULL
        hasWind || hasTemperature || hasPrecipitation -> DroneDataCompleteness.PARTIAL
        else -> DroneDataCompleteness.MINIMAL
    }
}

private fun Double.kmhToMs(): Double =
    this / 3.6

fun Double.msToKmh(): Double =
    this * 3.6

fun Double.formatOneDecimal(): String =
    if (this == roundToInt().toDouble()) roundToInt().toString() else "%.1f".format(this)

private fun WeatherCodeCategory.indicatesPrecipitationForDrone(): Boolean =
    when (this) {
        WeatherCodeCategory.DRIZZLE,
        WeatherCodeCategory.RAIN,
        WeatherCodeCategory.HEAVY_RAIN,
        WeatherCodeCategory.SNOW,
        WeatherCodeCategory.SHOWERS,
        WeatherCodeCategory.THUNDERSTORM,
        WeatherCodeCategory.THUNDERSTORM_WITH_HAIL -> true
        WeatherCodeCategory.BENIGN,
        WeatherCodeCategory.FOG,
        WeatherCodeCategory.UNKNOWN -> false
    }
