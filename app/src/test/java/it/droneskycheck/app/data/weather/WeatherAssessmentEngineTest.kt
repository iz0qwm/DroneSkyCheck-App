package it.droneskycheck.app.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAssessmentEngineTest {
    private val engine = WeatherAssessmentEngine()

    @Test
    fun perfectWeatherIsFavorableWithHighScore() {
        val assessment = engine.assess(perfectMetrics())

        assertEquals(WeatherState.FAVORABLE, assessment.state)
        assertTrue(assessment.score >= 95)
        assertEquals(WeatherConfidenceLevel.HIGH, assessment.confidence.level)
        assertTrue(assessment.reasons.isEmpty())
    }

    @Test
    fun strongWindAppliesPenaltyAndCap() {
        val assessment = engine.assess(perfectMetrics(windSpeedKmh = 39.0, windGustsKmh = 42.0))

        assertContains(assessment.reasons, WeatherReasonCode.STRONG_WIND)
        assertTrue(assessment.score <= WeatherAssessmentConfig.DEFAULT.windSpeed.criticalCap)
        assertEquals(WeatherState.UNFAVORABLE, assessment.state)
    }

    @Test
    fun strongGustsWithWeakWindProduceHighGustSpread() {
        val assessment = engine.assess(perfectMetrics(windSpeedKmh = 12.0, windGustsKmh = 30.0))

        assertEquals(18.0, assessment.gustSpreadKmh ?: -1.0, 0.0)
        assertContains(assessment.reasons, WeatherReasonCode.HIGH_GUST_SPREAD)
    }

    @Test
    fun thunderstormCannotBeFavorableEvenWhenOtherMetricsArePerfect() {
        val assessment = engine.assess(perfectMetrics(weatherCode = 95))

        assertContains(assessment.reasons, WeatherReasonCode.THUNDERSTORM)
        assertTrue(assessment.score <= 25)
        assertEquals(WeatherState.UNFAVORABLE, assessment.state)
    }

    @Test
    fun intenseRainWithWeakWindCannotBeFavorable() {
        val assessment = engine.assess(
            perfectMetrics(
                windSpeedKmh = 4.0,
                windGustsKmh = 6.0,
                precipitationMm = 3.0,
                weatherCode = 65
            )
        )

        assertContains(assessment.reasons, WeatherReasonCode.INTENSE_PRECIPITATION)
        assertContains(assessment.reasons, WeatherReasonCode.HEAVY_RAIN)
        assertFalse(assessment.state == WeatherState.FAVORABLE)
    }

    @Test
    fun veryLowVisibilityAppliesCriticalCap() {
        val assessment = engine.assess(perfectMetrics(visibilityMeters = 900.0))

        assertContains(assessment.reasons, WeatherReasonCode.LOW_VISIBILITY)
        assertTrue(assessment.score <= WeatherAssessmentConfig.DEFAULT.visibility.criticalCap)
        assertEquals(WeatherState.UNFAVORABLE, assessment.state)
    }

    @Test
    fun dryPrecipitationProbabilitySeventyWithClearWeatherStaysFavorable() {
        val assessment = engine.assess(
            perfectMetrics(
                precipitationMm = 0.0,
                precipitationProbabilityPct = 70.0,
                weatherCode = 0
            )
        )

        assertEquals(WeatherState.FAVORABLE, assessment.state)
        assertTrue(assessment.score >= 90)
        assertContains(assessment.reasons, WeatherReasonCode.HIGH_PRECIPITATION_PROBABILITY)
    }

    @Test
    fun dryPrecipitationProbabilityNinetyWithClearWeatherStaysFavorableWithWarning() {
        val assessment = engine.assess(
            perfectMetrics(
                precipitationMm = 0.0,
                precipitationProbabilityPct = 90.0,
                weatherCode = 0
            )
        )

        assertEquals(WeatherState.FAVORABLE, assessment.state)
        assertTrue(assessment.score >= 85)
        assertContains(assessment.reasons, WeatherReasonCode.HIGH_PRECIPITATION_PROBABILITY)
    }

    @Test
    fun lightRainWithHighProbabilityStaysAtLeastCaution() {
        val assessment = engine.assess(
            perfectMetrics(
                windSpeedKmh = 10.0,
                windGustsKmh = 15.0,
                precipitationMm = 0.4,
                precipitationProbabilityPct = 80.0,
                weatherCode = 61
            )
        )

        assertContains(assessment.reasons, WeatherReasonCode.PRECIPITATION)
        assertContains(assessment.reasons, WeatherReasonCode.HIGH_PRECIPITATION_PROBABILITY)
        assertContains(assessment.reasons, WeatherReasonCode.RAIN)
        assertTrue(assessment.state == WeatherState.CAUTION || assessment.state == WeatherState.UNFAVORABLE)
    }

    @Test
    fun heavyRainTwoMillimetersStaysUnfavorable() {
        val assessment = engine.assess(
            perfectMetrics(
                precipitationMm = 2.0,
                precipitationProbabilityPct = 20.0,
                weatherCode = 65
            )
        )

        assertEquals(WeatherState.UNFAVORABLE, assessment.state)
        assertContains(assessment.reasons, WeatherReasonCode.INTENSE_PRECIPITATION)
        assertContains(assessment.reasons, WeatherReasonCode.HEAVY_RAIN)
    }

    @Test
    fun moderateStableWindStaysFavorableWithWarning() {
        val assessment = engine.assess(
            perfectMetrics(
                windSpeedKmh = 22.0,
                windGustsKmh = 25.0,
                weatherCode = 0
            )
        )

        assertEquals(WeatherState.FAVORABLE, assessment.state)
        assertTrue(assessment.score in 82..88)
        assertContains(assessment.reasons, WeatherReasonCode.STRONG_WIND)
    }

    @Test
    fun missingTemperatureDoesNotPreventAssessment() {
        val assessment = engine.assess(perfectMetrics(temperatureC = null))

        assertEquals(WeatherState.FAVORABLE, assessment.state)
        assertContains(assessment.missingData.map { it.reason }, WeatherReasonCode.TEMPERATURE_MISSING)
        assertFalse(assessment.state == WeatherState.INSUFFICIENT_DATA)
    }

    @Test
    fun missingVisibilityKeepsAssessmentButReducesConfidence() {
        val assessment = engine.assess(perfectMetrics(visibilityMeters = null))

        assertEquals(WeatherState.FAVORABLE, assessment.state)
        assertContains(assessment.missingData.map { it.reason }, WeatherReasonCode.VISIBILITY_MISSING)
        assertTrue(assessment.confidence.score < 100)
    }

    @Test
    fun missingPrecipitationProbabilityKeepsAssessmentButReducesConfidence() {
        val assessment = engine.assess(perfectMetrics(precipitationProbabilityPct = null))

        assertEquals(WeatherState.FAVORABLE, assessment.state)
        assertContains(assessment.missingData.map { it.reason }, WeatherReasonCode.PRECIPITATION_PROBABILITY_MISSING)
        assertTrue(assessment.confidence.score < 100)
    }

    @Test
    fun missingWindIsInsufficientData() {
        val assessment = engine.assess(perfectMetrics(windSpeedKmh = null))

        assertEquals(WeatherState.INSUFFICIENT_DATA, assessment.state)
        assertEquals(WeatherConfidenceLevel.INSUFFICIENT, assessment.confidence.level)
        assertContains(assessment.reasons, WeatherReasonCode.WIND_MISSING)
    }

    @Test
    fun missingGustsIsInsufficientData() {
        val assessment = engine.assess(perfectMetrics(windGustsKmh = null))

        assertEquals(WeatherState.INSUFFICIENT_DATA, assessment.state)
        assertContains(assessment.reasons, WeatherReasonCode.GUSTS_MISSING)
    }

    @Test
    fun missingWeatherCodeIsInsufficientData() {
        val assessment = engine.assess(perfectMetrics(weatherCode = null))

        assertEquals(WeatherState.INSUFFICIENT_DATA, assessment.state)
        assertContains(assessment.reasons, WeatherReasonCode.WEATHER_CODE_MISSING)
    }

    @Test
    fun unknownWeatherCodeIsPrudentButNotMissing() {
        val assessment = engine.assess(perfectMetrics(weatherCode = 1234))

        assertEquals(WeatherCodeCategory.UNKNOWN, assessment.weatherCodeCategory)
        assertContains(assessment.reasons, WeatherReasonCode.UNKNOWN_WEATHER_CODE)
        assertEquals(WeatherState.CAUTION, assessment.state)
        assertFalse(assessment.missingData.any { it.field == WeatherDataField.WEATHER_CODE })
    }

    @Test
    fun windTwentyGustTwentyTwoDoesNotWarnStrongInstability() {
        val assessment = engine.assess(perfectMetrics(windSpeedKmh = 20.0, windGustsKmh = 22.0))

        assertEquals(2.0, assessment.gustSpreadKmh ?: -1.0, 0.0)
        assertFalse(assessment.reasons.contains(WeatherReasonCode.HIGH_GUST_SPREAD))
        assertFalse(assessment.reasons.contains(WeatherReasonCode.HIGH_GUST_RATIO))
    }

    @Test
    fun windTwelveGustThirtyWarnsHighGustSpread() {
        val assessment = engine.assess(perfectMetrics(windSpeedKmh = 12.0, windGustsKmh = 30.0))

        assertContains(assessment.reasons, WeatherReasonCode.HIGH_GUST_SPREAD)
    }

    @Test
    fun windTwelveGustThirtyStaysWorseThanWindTwentyGustTwentyTwo() {
        val stable = engine.assess(perfectMetrics(windSpeedKmh = 20.0, windGustsKmh = 22.0))
        val irregular = engine.assess(perfectMetrics(windSpeedKmh = 12.0, windGustsKmh = 30.0))

        assertTrue(irregular.score < stable.score)
        assertContains(irregular.reasons, WeatherReasonCode.HIGH_GUST_SPREAD)
    }

    @Test
    fun windTwentyFiveGustFortyStaysUnfavorable() {
        val assessment = engine.assess(perfectMetrics(windSpeedKmh = 25.0, windGustsKmh = 40.0))

        assertEquals(WeatherState.UNFAVORABLE, assessment.state)
    }

    @Test
    fun windThirtyGustThirtyFiveStaysUnfavorable() {
        val assessment = engine.assess(perfectMetrics(windSpeedKmh = 30.0, windGustsKmh = 35.0))

        assertEquals(WeatherState.UNFAVORABLE, assessment.state)
    }

    @Test
    fun criticalEventsAreNotCompensatedByGoodMetrics() {
        val thunderstorm = engine.assess(perfectMetrics(weatherCode = 99))
        val lowVisibility = engine.assess(perfectMetrics(visibilityMeters = 300.0))

        assertEquals(WeatherState.UNFAVORABLE, thunderstorm.state)
        assertTrue(thunderstorm.score <= 20)
        assertEquals(WeatherState.UNFAVORABLE, lowVisibility.state)
        assertTrue(lowVisibility.score <= 40)
    }

    @Test
    fun scoreIsAlwaysClampedBetweenZeroAndOneHundred() {
        val perfect = engine.assess(perfectMetrics())
        val severe = engine.assess(
            perfectMetrics(
                windSpeedKmh = 200.0,
                windGustsKmh = 260.0,
                precipitationMm = 50.0,
                precipitationProbabilityPct = 100.0,
                visibilityMeters = 0.0,
                weatherCode = 99,
                temperatureC = 70.0,
                cloudCoverPct = 100.0
            )
        )

        assertTrue(perfect.score in 0..100)
        assertTrue(severe.score in 0..100)
    }

    @Test
    fun customConfigChangesEngineBehavior() {
        val relaxed = WeatherAssessmentEngine(
            WeatherAssessmentConfig.DEFAULT.copy(
                windSpeed = WeatherAssessmentConfig.DEFAULT.windSpeed.copy(
                    warningStart = 30.0,
                    warningPenalty = 2
                )
            )
        )

        val defaultAssessment = engine.assess(perfectMetrics(windSpeedKmh = 20.0, windGustsKmh = 22.0))
        val relaxedAssessment = relaxed.assess(perfectMetrics(windSpeedKmh = 20.0, windGustsKmh = 22.0))

        assertTrue(relaxedAssessment.score > defaultAssessment.score)
        assertFalse(relaxedAssessment.reasons.contains(WeatherReasonCode.STRONG_WIND))
    }

    @Test
    fun dryPrecipitationProbabilityDoesNotChangeConfidence() {
        val assessment = engine.assess(
            perfectMetrics(
                precipitationMm = 0.0,
                precipitationProbabilityPct = 90.0,
                weatherCode = 0
            )
        )

        assertEquals(100, assessment.confidence.score)
        assertEquals(WeatherConfidenceLevel.HIGH, assessment.confidence.level)
        assertTrue(assessment.confidence.reasons.isEmpty())
    }

    @Test
    fun pathologicalInputsAreTreatedAsMissingOrClamped() {
        val assessment = engine.assess(
            perfectMetrics(
                windSpeedKmh = -1.0,
                windGustsKmh = Double.NaN,
                precipitationMm = -0.5,
                precipitationProbabilityPct = 150.0,
                visibilityMeters = -10.0,
                temperatureC = Double.NaN,
                cloudCoverPct = -2.0
            )
        )

        assertEquals(WeatherState.INSUFFICIENT_DATA, assessment.state)
        assertContains(assessment.reasons, WeatherReasonCode.WIND_MISSING)
        assertContains(assessment.reasons, WeatherReasonCode.GUSTS_MISSING)
        assertTrue(assessment.score in 0..100)
    }

    @Test
    fun allDataMissingIsInsufficientDataWithInsufficientConfidence() {
        val assessment = engine.assess(WeatherMetrics())

        assertEquals(WeatherState.INSUFFICIENT_DATA, assessment.state)
        assertEquals(WeatherConfidenceLevel.INSUFFICIENT, assessment.confidence.level)
        assertEquals(0, assessment.score)
        assertContains(assessment.reasons, WeatherReasonCode.WIND_MISSING)
        assertContains(assessment.reasons, WeatherReasonCode.GUSTS_MISSING)
        assertContains(assessment.reasons, WeatherReasonCode.WEATHER_CODE_MISSING)
    }

    @Test
    fun classifierMapsOpenMeteoWeatherCodesToCentralCategories() {
        assertEquals(WeatherCodeCategory.BENIGN, OpenMeteoWeatherCodeClassifier.classify(0))
        assertEquals(WeatherCodeCategory.FOG, OpenMeteoWeatherCodeClassifier.classify(45))
        assertEquals(WeatherCodeCategory.DRIZZLE, OpenMeteoWeatherCodeClassifier.classify(51))
        assertEquals(WeatherCodeCategory.RAIN, OpenMeteoWeatherCodeClassifier.classify(61))
        assertEquals(WeatherCodeCategory.HEAVY_RAIN, OpenMeteoWeatherCodeClassifier.classify(65))
        assertEquals(WeatherCodeCategory.SNOW, OpenMeteoWeatherCodeClassifier.classify(71))
        assertEquals(WeatherCodeCategory.SHOWERS, OpenMeteoWeatherCodeClassifier.classify(80))
        assertEquals(WeatherCodeCategory.THUNDERSTORM, OpenMeteoWeatherCodeClassifier.classify(95))
        assertEquals(WeatherCodeCategory.THUNDERSTORM_WITH_HAIL, OpenMeteoWeatherCodeClassifier.classify(99))
        assertEquals(WeatherCodeCategory.UNKNOWN, OpenMeteoWeatherCodeClassifier.classify(999))
    }

    @Test
    fun gustRatioIsSecondaryAndIgnoredWhenBaseWindIsTooLow() {
        val calmWithSpike = engine.assess(perfectMetrics(windSpeedKmh = 1.0, windGustsKmh = 8.0))
        val windyWithSpike = engine.assess(perfectMetrics(windSpeedKmh = 10.0, windGustsKmh = 20.0))

        assertNotNull(calmWithSpike.gustSpreadKmh)
        assertFalse(calmWithSpike.reasons.contains(WeatherReasonCode.HIGH_GUST_RATIO))
        assertContains(windyWithSpike.reasons, WeatherReasonCode.HIGH_GUST_RATIO)
    }

    private fun perfectMetrics(
        windSpeedKmh: Double? = 8.0,
        windGustsKmh: Double? = 12.0,
        windDirectionDegrees: Double? = 180.0,
        precipitationMm: Double? = 0.0,
        precipitationProbabilityPct: Double? = 5.0,
        visibilityMeters: Double? = 12_000.0,
        weatherCode: Int? = 0,
        temperatureC: Double? = 20.0,
        cloudCoverPct: Double? = 20.0
    ): WeatherMetrics =
        WeatherMetrics(
            windSpeedKmh = windSpeedKmh,
            windGustsKmh = windGustsKmh,
            windDirectionDegrees = windDirectionDegrees,
            precipitationMm = precipitationMm,
            precipitationProbabilityPct = precipitationProbabilityPct,
            visibilityMeters = visibilityMeters,
            weatherCode = weatherCode,
            temperatureC = temperatureC,
            cloudCoverPct = cloudCoverPct
        )

    private fun assertContains(values: List<WeatherReasonCode>, expected: WeatherReasonCode) {
        assertTrue("Expected $values to contain $expected", values.contains(expected))
    }
}
