package it.droneskycheck.app.data.weather

import kotlin.math.max
import kotlin.math.min

data class WeatherMetrics(
    val windSpeedKmh: Double? = null,
    val windGustsKmh: Double? = null,
    val windDirectionDegrees: Double? = null,
    val precipitationMm: Double? = null,
    val precipitationProbabilityPct: Double? = null,
    val visibilityMeters: Double? = null,
    val weatherCode: Int? = null,
    val temperatureC: Double? = null,
    val cloudCoverPct: Double? = null
)

data class WeatherAssessment(
    val score: Int,
    val state: WeatherState,
    val confidence: WeatherConfidence,
    val reasons: List<WeatherReasonCode>,
    val missingData: List<WeatherMissingData>,
    val weatherCodeCategory: WeatherCodeCategory,
    val gustSpreadKmh: Double?,
    val gustRatio: Double?
)

enum class WeatherState {
    FAVORABLE,
    CAUTION,
    UNFAVORABLE,
    INSUFFICIENT_DATA
}

data class WeatherConfidence(
    val score: Int,
    val level: WeatherConfidenceLevel,
    val reasons: List<WeatherReasonCode>
)

enum class WeatherConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW,
    INSUFFICIENT
}

enum class WeatherDataField {
    WIND_SPEED_10M,
    WIND_GUSTS_10M,
    WIND_DIRECTION_10M,
    PRECIPITATION,
    PRECIPITATION_PROBABILITY,
    VISIBILITY,
    WEATHER_CODE,
    TEMPERATURE,
    CLOUD_COVER
}

data class WeatherMissingData(
    val field: WeatherDataField,
    val importance: WeatherVariableImportance,
    val reason: WeatherReasonCode
)

enum class WeatherVariableImportance {
    MANDATORY,
    STRONGLY_RECOMMENDED,
    OPTIONAL
}

enum class WeatherReasonCode {
    STRONG_WIND,
    HIGH_GUSTS,
    HIGH_GUST_SPREAD,
    HIGH_GUST_RATIO,
    PRECIPITATION,
    INTENSE_PRECIPITATION,
    HIGH_PRECIPITATION_PROBABILITY,
    FOG,
    DRIZZLE,
    RAIN,
    HEAVY_RAIN,
    SNOW,
    SHOWERS,
    THUNDERSTORM,
    THUNDERSTORM_WITH_HAIL,
    LOW_VISIBILITY,
    EXTREME_TEMPERATURE,
    HIGH_CLOUD_COVER_INFO,
    UNKNOWN_WEATHER_CODE,
    WIND_MISSING,
    GUSTS_MISSING,
    WEATHER_CODE_MISSING,
    PRECIPITATION_MISSING,
    PRECIPITATION_PROBABILITY_MISSING,
    VISIBILITY_MISSING,
    TEMPERATURE_MISSING,
    CLOUD_COVER_MISSING,
    INVALID_METRIC
}

enum class WeatherCodeCategory {
    BENIGN,
    FOG,
    DRIZZLE,
    RAIN,
    HEAVY_RAIN,
    SNOW,
    SHOWERS,
    THUNDERSTORM,
    THUNDERSTORM_WITH_HAIL,
    UNKNOWN
}

data class WeatherAssessmentConfig(
    val windSpeed: StepPenaltyConfig,
    val windGusts: StepPenaltyConfig,
    val gustSpread: StepPenaltyConfig,
    val gustRatio: RatioPenaltyConfig,
    val precipitation: StepPenaltyConfig,
    val precipitationProbability: StepPenaltyConfig,
    val dryPrecipitationProbability: StepPenaltyConfig,
    val visibility: LowValuePenaltyConfig,
    val temperature: OutsideRangePenaltyConfig,
    val cloudCover: HighValuePenaltyConfig,
    val confidence: WeatherConfidenceConfig,
    val favorableScore: Int = 80,
    val cautionScore: Int = 50
) {
    companion object {
        val DEFAULT = WeatherAssessmentConfig(
            windSpeed = StepPenaltyConfig(
                warningStart = 18.0,
                warningPenalty = 8,
                highStart = 28.0,
                highPenalty = 25,
                highCap = 65,
                criticalStart = 38.0,
                criticalPenalty = 40,
                criticalCap = 45
            ),
            windGusts = StepPenaltyConfig(
                warningStart = 25.0,
                warningPenalty = 8,
                highStart = 35.0,
                highPenalty = 30,
                highCap = 60,
                criticalStart = 45.0,
                criticalPenalty = 45,
                criticalCap = 40
            ),
            gustSpread = StepPenaltyConfig(
                warningStart = 12.0,
                warningPenalty = 15,
                highStart = 22.0,
                highPenalty = 22,
                highCap = 70,
                criticalStart = 32.0,
                criticalPenalty = 30,
                criticalCap = 55
            ),
            gustRatio = RatioPenaltyConfig(
                warningStart = 1.8,
                penalty = 5,
                safeMinimumWindKmh = 8.0
            ),
            precipitation = StepPenaltyConfig(
                warningStart = 0.1,
                warningPenalty = 10,
                highStart = 0.5,
                highPenalty = 25,
                highCap = 70,
                criticalStart = 2.0,
                criticalPenalty = 35,
                criticalCap = 45
            ),
            precipitationProbability = StepPenaltyConfig(
                warningStart = 40.0,
                warningPenalty = 8,
                highStart = 65.0,
                highPenalty = 16,
                highCap = 75,
                criticalStart = 80.0,
                criticalPenalty = 20,
                criticalCap = 65
            ),
            dryPrecipitationProbability = StepPenaltyConfig(
                warningStart = 40.0,
                warningPenalty = 3,
                highStart = 65.0,
                highPenalty = 6,
                highCap = 100,
                criticalStart = 80.0,
                criticalPenalty = 10,
                criticalCap = 100
            ),
            visibility = LowValuePenaltyConfig(
                warningBelow = 8_000.0,
                warningPenalty = 8,
                highBelow = 5_000.0,
                highPenalty = 22,
                highCap = 70,
                criticalBelow = 2_000.0,
                criticalPenalty = 35,
                criticalCap = 40
            ),
            temperature = OutsideRangePenaltyConfig(
                lowWarningBelow = -5.0,
                highWarningAbove = 35.0,
                warningPenalty = 8,
                lowCriticalBelow = -10.0,
                highCriticalAbove = 40.0,
                criticalPenalty = 20,
                criticalCap = 60
            ),
            cloudCover = HighValuePenaltyConfig(
                warningStart = 90.0,
                penalty = 3
            ),
            confidence = WeatherConfidenceConfig()
        )
    }
}

data class StepPenaltyConfig(
    val warningStart: Double,
    val warningPenalty: Int,
    val highStart: Double,
    val highPenalty: Int,
    val highCap: Int,
    val criticalStart: Double,
    val criticalPenalty: Int,
    val criticalCap: Int
)

data class RatioPenaltyConfig(
    val warningStart: Double,
    val penalty: Int,
    val safeMinimumWindKmh: Double
)

data class LowValuePenaltyConfig(
    val warningBelow: Double,
    val warningPenalty: Int,
    val highBelow: Double,
    val highPenalty: Int,
    val highCap: Int,
    val criticalBelow: Double,
    val criticalPenalty: Int,
    val criticalCap: Int
)

data class OutsideRangePenaltyConfig(
    val lowWarningBelow: Double,
    val highWarningAbove: Double,
    val warningPenalty: Int,
    val lowCriticalBelow: Double,
    val highCriticalAbove: Double,
    val criticalPenalty: Int,
    val criticalCap: Int
)

data class HighValuePenaltyConfig(
    val warningStart: Double,
    val penalty: Int
)

data class WeatherConfidenceConfig(
    val highAtLeast: Int = 85,
    val mediumAtLeast: Int = 60,
    val lowAtLeast: Int = 30,
    val missingVisibilityPenalty: Int = 15,
    val missingPrecipitationProbabilityPenalty: Int = 8,
    val missingPrecipitationPenalty: Int = 12,
    val missingTemperaturePenalty: Int = 0,
    val missingCloudCoverPenalty: Int = 3,
    val unknownWeatherCodePenalty: Int = 10
)

class WeatherAssessmentEngine(
    private val config: WeatherAssessmentConfig = WeatherAssessmentConfig.DEFAULT
) {
    fun assess(metrics: WeatherMetrics): WeatherAssessment {
        val missingData = buildMissingData(metrics)
        val mandatoryMissing = missingData.any { it.importance == WeatherVariableImportance.MANDATORY }
        val weatherCodeCategory = OpenMeteoWeatherCodeClassifier.classify(metrics.weatherCode)

        if (mandatoryMissing) {
            return WeatherAssessment(
                score = 0,
                state = WeatherState.INSUFFICIENT_DATA,
                confidence = WeatherConfidence(
                    score = 0,
                    level = WeatherConfidenceLevel.INSUFFICIENT,
                    reasons = missingData.map { it.reason }.distinct()
                ),
                reasons = missingData.map { it.reason }.distinct(),
                missingData = missingData,
                weatherCodeCategory = weatherCodeCategory,
                gustSpreadKmh = null,
                gustRatio = null
            )
        }

        val reasons = mutableListOf<WeatherReasonCode>()
        var score = 100
        var cap = 100

        fun applyPenalty(penalty: Int, reason: WeatherReasonCode? = null, criticalCap: Int? = null) {
            score -= penalty
            if (criticalCap != null) cap = min(cap, criticalCap)
            if (reason != null) reasons += reason
        }

        applyHighValuePenalty(metrics.windSpeedKmh.safeNonNegative(), config.windSpeed)?.let {
            applyPenalty(it.penalty, WeatherReasonCode.STRONG_WIND, it.cap)
        }

        applyHighValuePenalty(metrics.windGustsKmh.safeNonNegative(), config.windGusts)?.let {
            applyPenalty(it.penalty, WeatherReasonCode.HIGH_GUSTS, it.cap)
        }

        val gustSpread = computeGustSpread(metrics)
        applyHighValuePenalty(gustSpread, config.gustSpread)?.let {
            applyPenalty(it.penalty, WeatherReasonCode.HIGH_GUST_SPREAD, it.cap)
        }

        val gustRatio = computeGustRatio(metrics)
        if (gustRatio != null && gustRatio >= config.gustRatio.warningStart) {
            applyPenalty(config.gustRatio.penalty, WeatherReasonCode.HIGH_GUST_RATIO)
        }

        applyHighValuePenalty(metrics.precipitationMm.safeNonNegative(), config.precipitation)?.let {
            val reason = if (it.cap != null) WeatherReasonCode.INTENSE_PRECIPITATION else WeatherReasonCode.PRECIPITATION
            applyPenalty(it.penalty, reason, it.cap)
        }

        val precipitationProbabilityConfig = precipitationProbabilityConfig(metrics, weatherCodeCategory)
        applyHighValuePenalty(metrics.precipitationProbabilityPct.safePercent(), precipitationProbabilityConfig)?.let {
            applyPenalty(it.penalty, WeatherReasonCode.HIGH_PRECIPITATION_PROBABILITY, it.cap)
        }

        applyLowValuePenalty(metrics.visibilityMeters.safeNonNegative(), config.visibility)?.let {
            applyPenalty(it.penalty, WeatherReasonCode.LOW_VISIBILITY, it.cap)
        }

        applyTemperaturePenalty(metrics.temperatureC.safeFinite(), config.temperature)?.let {
            applyPenalty(it.penalty, WeatherReasonCode.EXTREME_TEMPERATURE, it.cap)
        }

        if ((metrics.cloudCoverPct.safePercent() ?: 0.0) >= config.cloudCover.warningStart) {
            applyPenalty(config.cloudCover.penalty, WeatherReasonCode.HIGH_CLOUD_COVER_INFO)
        }

        applyWeatherCodePenalty(
            weatherCodeCategory,
            hasQuantitativePrecipitation = metrics.precipitationMm.safeNonNegative() != null
        )?.let {
            applyPenalty(it.penalty, it.reason, it.cap)
        }

        val confidence = computeConfidence(missingData, weatherCodeCategory)
        val finalScore = score.coerceIn(0, cap).coerceIn(0, 100)
        val state = when {
            finalScore < config.cautionScore -> WeatherState.UNFAVORABLE
            finalScore < config.favorableScore -> WeatherState.CAUTION
            weatherCodeCategory.isCritical -> WeatherState.UNFAVORABLE
            reasons.any { it == WeatherReasonCode.UNKNOWN_WEATHER_CODE } -> WeatherState.CAUTION
            else -> WeatherState.FAVORABLE
        }

        return WeatherAssessment(
            score = finalScore,
            state = state,
            confidence = confidence,
            reasons = reasons.distinct(),
            missingData = missingData,
            weatherCodeCategory = weatherCodeCategory,
            gustSpreadKmh = gustSpread,
            gustRatio = gustRatio
        )
    }

    private fun buildMissingData(metrics: WeatherMetrics): List<WeatherMissingData> =
        buildList {
            if (metrics.windSpeedKmh.safeNonNegative() == null) {
                add(WeatherMissingData(WeatherDataField.WIND_SPEED_10M, WeatherVariableImportance.MANDATORY, WeatherReasonCode.WIND_MISSING))
            }
            if (metrics.windGustsKmh.safeNonNegative() == null) {
                add(WeatherMissingData(WeatherDataField.WIND_GUSTS_10M, WeatherVariableImportance.MANDATORY, WeatherReasonCode.GUSTS_MISSING))
            }
            if (metrics.weatherCode == null) {
                add(WeatherMissingData(WeatherDataField.WEATHER_CODE, WeatherVariableImportance.MANDATORY, WeatherReasonCode.WEATHER_CODE_MISSING))
            }
            if (metrics.precipitationMm.safeNonNegative() == null) {
                add(WeatherMissingData(WeatherDataField.PRECIPITATION, WeatherVariableImportance.STRONGLY_RECOMMENDED, WeatherReasonCode.PRECIPITATION_MISSING))
            }
            if (metrics.precipitationProbabilityPct.safePercent() == null) {
                add(WeatherMissingData(WeatherDataField.PRECIPITATION_PROBABILITY, WeatherVariableImportance.STRONGLY_RECOMMENDED, WeatherReasonCode.PRECIPITATION_PROBABILITY_MISSING))
            }
            if (metrics.visibilityMeters.safeNonNegative() == null) {
                add(WeatherMissingData(WeatherDataField.VISIBILITY, WeatherVariableImportance.STRONGLY_RECOMMENDED, WeatherReasonCode.VISIBILITY_MISSING))
            }
            if (metrics.temperatureC.safeFinite() == null) {
                add(WeatherMissingData(WeatherDataField.TEMPERATURE, WeatherVariableImportance.OPTIONAL, WeatherReasonCode.TEMPERATURE_MISSING))
            }
            if (metrics.cloudCoverPct.safePercent() == null) {
                add(WeatherMissingData(WeatherDataField.CLOUD_COVER, WeatherVariableImportance.OPTIONAL, WeatherReasonCode.CLOUD_COVER_MISSING))
            }
        }

    private fun computeGustSpread(metrics: WeatherMetrics): Double? {
        val wind = metrics.windSpeedKmh.safeNonNegative() ?: return null
        val gust = metrics.windGustsKmh.safeNonNegative() ?: return null
        return max(0.0, gust - wind)
    }

    private fun computeGustRatio(metrics: WeatherMetrics): Double? {
        val wind = metrics.windSpeedKmh.safeNonNegative() ?: return null
        val gust = metrics.windGustsKmh.safeNonNegative() ?: return null
        if (wind < config.gustRatio.safeMinimumWindKmh) return null
        return gust / wind
    }

    private fun computeConfidence(
        missingData: List<WeatherMissingData>,
        weatherCodeCategory: WeatherCodeCategory
    ): WeatherConfidence {
        var score = 100
        val reasons = mutableListOf<WeatherReasonCode>()

        for (missing in missingData) {
            val penalty = when (missing.reason) {
                WeatherReasonCode.VISIBILITY_MISSING -> config.confidence.missingVisibilityPenalty
                WeatherReasonCode.PRECIPITATION_PROBABILITY_MISSING -> config.confidence.missingPrecipitationProbabilityPenalty
                WeatherReasonCode.PRECIPITATION_MISSING -> config.confidence.missingPrecipitationPenalty
                WeatherReasonCode.TEMPERATURE_MISSING -> config.confidence.missingTemperaturePenalty
                WeatherReasonCode.CLOUD_COVER_MISSING -> config.confidence.missingCloudCoverPenalty
                else -> 0
            }
            score -= penalty
            if (penalty > 0) reasons += missing.reason
        }

        if (weatherCodeCategory == WeatherCodeCategory.UNKNOWN) {
            score -= config.confidence.unknownWeatherCodePenalty
            reasons += WeatherReasonCode.UNKNOWN_WEATHER_CODE
        }

        val clamped = score.coerceIn(0, 100)
        return WeatherConfidence(
            score = clamped,
            level = when {
                clamped >= config.confidence.highAtLeast -> WeatherConfidenceLevel.HIGH
                clamped >= config.confidence.mediumAtLeast -> WeatherConfidenceLevel.MEDIUM
                clamped >= config.confidence.lowAtLeast -> WeatherConfidenceLevel.LOW
                else -> WeatherConfidenceLevel.INSUFFICIENT
            },
            reasons = reasons.distinct()
        )
    }

    private fun precipitationProbabilityConfig(
        metrics: WeatherMetrics,
        weatherCodeCategory: WeatherCodeCategory
    ): StepPenaltyConfig {
        val precipitation = metrics.precipitationMm.safeNonNegative()
        val hasNoMeasuredPrecipitation = precipitation == 0.0
        val codeDoesNotIndicatePrecipitation = !weatherCodeCategory.indicatesPrecipitation
        return if (hasNoMeasuredPrecipitation && codeDoesNotIndicatePrecipitation) {
            config.dryPrecipitationProbability
        } else {
            config.precipitationProbability
        }
    }

    private fun applyWeatherCodePenalty(
        category: WeatherCodeCategory,
        hasQuantitativePrecipitation: Boolean
    ): AssessmentEffect? =
        when (category) {
            WeatherCodeCategory.BENIGN -> null
            WeatherCodeCategory.FOG -> AssessmentEffect(10, WeatherReasonCode.FOG, 75)
            WeatherCodeCategory.DRIZZLE -> AssessmentEffect(8, WeatherReasonCode.DRIZZLE)
            WeatherCodeCategory.RAIN -> {
                val penalty = if (hasQuantitativePrecipitation) 6 else 18
                AssessmentEffect(penalty, WeatherReasonCode.RAIN)
            }
            WeatherCodeCategory.HEAVY_RAIN -> AssessmentEffect(35, WeatherReasonCode.HEAVY_RAIN, 45)
            WeatherCodeCategory.SNOW -> AssessmentEffect(25, WeatherReasonCode.SNOW, 55)
            WeatherCodeCategory.SHOWERS -> AssessmentEffect(20, WeatherReasonCode.SHOWERS, 65)
            WeatherCodeCategory.THUNDERSTORM -> AssessmentEffect(60, WeatherReasonCode.THUNDERSTORM, 25)
            WeatherCodeCategory.THUNDERSTORM_WITH_HAIL -> AssessmentEffect(70, WeatherReasonCode.THUNDERSTORM_WITH_HAIL, 20)
            WeatherCodeCategory.UNKNOWN -> AssessmentEffect(15, WeatherReasonCode.UNKNOWN_WEATHER_CODE, 70)
        }
}

object OpenMeteoWeatherCodeClassifier {
    fun classify(code: Int?): WeatherCodeCategory =
        when (code) {
            null -> WeatherCodeCategory.UNKNOWN
            0, 1, 2, 3 -> WeatherCodeCategory.BENIGN
            45, 48 -> WeatherCodeCategory.FOG
            51, 53, 55, 56, 57 -> WeatherCodeCategory.DRIZZLE
            61, 63, 66 -> WeatherCodeCategory.RAIN
            65, 67 -> WeatherCodeCategory.HEAVY_RAIN
            71, 73, 75, 77, 85, 86 -> WeatherCodeCategory.SNOW
            80, 81, 82 -> WeatherCodeCategory.SHOWERS
            95 -> WeatherCodeCategory.THUNDERSTORM
            96, 99 -> WeatherCodeCategory.THUNDERSTORM_WITH_HAIL
            else -> WeatherCodeCategory.UNKNOWN
        }
}

private data class AssessmentEffect(
    val penalty: Int,
    val reason: WeatherReasonCode,
    val cap: Int? = null
)

private fun applyHighValuePenalty(value: Double?, config: StepPenaltyConfig): AssessmentEffect? =
    when {
        value == null -> null
        value >= config.criticalStart -> AssessmentEffect(config.criticalPenalty, WeatherReasonCode.INVALID_METRIC, config.criticalCap)
        value >= config.highStart -> AssessmentEffect(config.highPenalty, WeatherReasonCode.INVALID_METRIC, config.highCap)
        value >= config.warningStart -> AssessmentEffect(config.warningPenalty, WeatherReasonCode.INVALID_METRIC)
        else -> null
    }

private fun applyLowValuePenalty(value: Double?, config: LowValuePenaltyConfig): AssessmentEffect? =
    when {
        value == null -> null
        value < config.criticalBelow -> AssessmentEffect(config.criticalPenalty, WeatherReasonCode.INVALID_METRIC, config.criticalCap)
        value < config.highBelow -> AssessmentEffect(config.highPenalty, WeatherReasonCode.INVALID_METRIC, config.highCap)
        value < config.warningBelow -> AssessmentEffect(config.warningPenalty, WeatherReasonCode.INVALID_METRIC)
        else -> null
    }

private fun applyTemperaturePenalty(value: Double?, config: OutsideRangePenaltyConfig): AssessmentEffect? =
    when {
        value == null -> null
        value <= config.lowCriticalBelow || value >= config.highCriticalAbove ->
            AssessmentEffect(config.criticalPenalty, WeatherReasonCode.INVALID_METRIC, config.criticalCap)
        value <= config.lowWarningBelow || value >= config.highWarningAbove ->
            AssessmentEffect(config.warningPenalty, WeatherReasonCode.INVALID_METRIC)
        else -> null
    }

private val WeatherCodeCategory.isCritical: Boolean
    get() = this == WeatherCodeCategory.THUNDERSTORM || this == WeatherCodeCategory.THUNDERSTORM_WITH_HAIL

private val WeatherCodeCategory.indicatesPrecipitation: Boolean
    get() = when (this) {
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

private fun Double?.safeFinite(): Double? =
    this?.takeIf { it.isFinite() }

private fun Double?.safeNonNegative(): Double? =
    safeFinite()?.takeIf { it >= 0.0 }

private fun Double?.safePercent(): Double? =
    safeFinite()?.takeIf { it in 0.0..100.0 }
