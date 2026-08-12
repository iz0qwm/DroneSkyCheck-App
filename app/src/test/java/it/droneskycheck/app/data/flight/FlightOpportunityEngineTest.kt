package it.droneskycheck.app.data.flight

import it.droneskycheck.app.data.LegalTimelineSegment
import it.droneskycheck.app.data.LegalTimelineState
import it.droneskycheck.app.data.drone.DroneDataCompleteness
import it.droneskycheck.app.data.drone.DroneOperationalAssessment
import it.droneskycheck.app.data.drone.DroneOperationalCapabilities
import it.droneskycheck.app.data.drone.DroneOperationalLevel
import it.droneskycheck.app.data.weather.WeatherAssessment
import it.droneskycheck.app.data.weather.WeatherCodeCategory
import it.droneskycheck.app.data.weather.WeatherConfidence
import it.droneskycheck.app.data.weather.WeatherConfidenceLevel
import it.droneskycheck.app.data.weather.WeatherReasonCode
import it.droneskycheck.app.data.weather.WeatherState
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightOpportunityEngineTest {
    private val engine = FlightOpportunityEngine()
    private val zoneId = ZoneId.of("Europe/Rome")
    private val now = Instant.parse("2026-08-14T06:00:00Z")

    @Test
    fun legalGateUsesOnlyOpenSegmentsAndPreservesAltitudeLimit() {
        val result = engine.evaluate(
            input(
                legalSegments = listOf(
                    segment("2026-08-14T06:00:00Z", "2026-08-14T07:00:00Z", LegalTimelineState.AUTH_REQUIRED),
                    segment(
                        "2026-08-14T07:00:00Z",
                        "2026-08-14T08:00:00Z",
                        LegalTimelineState.AVAILABLE_WITH_LIMIT,
                        maxAltitudeAgl = 60
                    ),
                    segment("2026-08-14T08:00:00Z", "2026-08-14T09:00:00Z", LegalTimelineState.UNAVAILABLE)
                ),
                weatherSlots = listOf(slot("2026-08-14T06:00:00Z", "2026-08-14T09:00:00Z"))
            )
        )

        assertEquals(FlightOpportunityStatus.READY, result.status)
        assertEquals(Instant.parse("2026-08-14T07:00:00Z"), result.bestOpportunity?.from)
        assertEquals(Instant.parse("2026-08-14T08:00:00Z"), result.bestOpportunity?.to)
        assertEquals(60, result.bestOpportunity?.maxAltitudeAgl)
        assertTrue(result.bestOpportunity?.reasons?.contains(FlightOpportunityReasonCode.ALTITUDE_LIMIT) == true)
    }

    @Test
    fun closedLegalStatesDoNotBecomeOpportunities() {
        val result = engine.evaluate(
            input(
                legalSegments = listOf(
                    segment("2026-08-14T06:00:00Z", "2026-08-14T07:00:00Z", LegalTimelineState.AUTH_REQUIRED),
                    segment("2026-08-14T07:00:00Z", "2026-08-14T08:00:00Z", LegalTimelineState.UNKNOWN),
                    segment("2026-08-14T08:00:00Z", "2026-08-14T09:00:00Z", LegalTimelineState.UNAVAILABLE)
                ),
                weatherSlots = listOf(slot("2026-08-14T06:00:00Z", "2026-08-14T09:00:00Z"))
            )
        )

        assertEquals(FlightOpportunityStatus.NO_OPEN_WINDOW, result.status)
        assertNull(result.bestOpportunity)
        assertTrue(result.blockers.contains(FlightOpportunityReasonCode.AUTHORIZATION_REQUIRED))
        assertTrue(result.blockers.contains(FlightOpportunityReasonCode.LEGAL_UNKNOWN))
        assertTrue(result.blockers.contains(FlightOpportunityReasonCode.LEGAL_UNAVAILABLE))
    }

    @Test
    fun scoreUsesWeakestWeatherOrDroneComponentAndRanksBestWindow() {
        val result = engine.evaluate(
            input(
                legalSegments = listOf(segment("2026-08-14T06:00:00Z", "2026-08-14T10:00:00Z")),
                weatherSlots = listOf(
                    slot(
                        "2026-08-14T06:00:00Z",
                        "2026-08-14T07:00:00Z",
                        weather = weather(score = 95),
                        drone = drone(score = 55, level = DroneOperationalLevel.CAUTION)
                    ),
                    slot(
                        "2026-08-14T07:00:00Z",
                        "2026-08-14T10:00:00Z",
                        weather = weather(score = 78, state = WeatherState.CAUTION),
                        drone = drone(score = 82, level = DroneOperationalLevel.FAVORABLE)
                    )
                )
            )
        )

        assertEquals(FlightOpportunityStatus.READY, result.status)
        assertEquals(Instant.parse("2026-08-14T07:00:00Z"), result.bestOpportunity?.from)
        assertEquals(68, result.bestOpportunity?.opportunityScore)
        assertEquals(78, result.bestOpportunity?.weatherScore)
        assertEquals(82, result.bestOpportunity?.droneScore)
        assertTrue(result.bestOpportunity?.warnings?.contains(FlightOpportunityWarning.VARIABLE_DAY_CAP) == true)
    }

    @Test
    fun excellentHourlyWindowIsCappedByVariableDayWeatherAndDroneScores() {
        val result = engine.evaluate(
            input(
                legalSegments = listOf(segment("2026-08-15T05:00:00Z", "2026-08-15T16:00:00Z")),
                weatherSlots = listOf(
                    slot(
                        "2026-08-15T05:00:00Z",
                        "2026-08-15T06:00:00Z",
                        weather = weather(score = 45, state = WeatherState.UNFAVORABLE),
                        drone = drone(score = 40, level = DroneOperationalLevel.CAUTION)
                    ),
                    slot(
                        "2026-08-15T06:00:00Z",
                        "2026-08-15T07:00:00Z",
                        weather = weather(score = 100),
                        drone = drone(score = 100)
                    ),
                    slot(
                        "2026-08-15T07:00:00Z",
                        "2026-08-15T08:00:00Z",
                        weather = weather(score = 100),
                        drone = drone(score = 100)
                    ),
                    slot(
                        "2026-08-15T08:00:00Z",
                        "2026-08-15T09:00:00Z",
                        weather = weather(score = 100),
                        drone = drone(score = 100)
                    ),
                    slot(
                        "2026-08-15T09:00:00Z",
                        "2026-08-15T10:00:00Z",
                        weather = weather(score = 60, state = WeatherState.CAUTION),
                        drone = drone(score = 54, level = DroneOperationalLevel.CAUTION)
                    )
                )
            )
        )

        assertEquals(FlightOpportunityStatus.READY, result.status)
        assertEquals(73, result.bestOpportunity?.opportunityScore)
        assertEquals(FlightOpportunityLevel.GOOD, result.bestOpportunity?.opportunityLevel)
        assertTrue(result.bestOpportunity?.warnings?.contains(FlightOpportunityWarning.VARIABLE_DAY_CAP) == true)
    }

    @Test
    fun daytimeWeekendWindowIsPreferredOverEarlierEveningWindow() {
        val result = engine.evaluate(
            input(
                legalSegments = listOf(
                    segment("2026-08-14T18:00:00Z", "2026-08-14T20:00:00Z"),
                    segment("2026-08-15T07:00:00Z", "2026-08-15T09:00:00Z")
                ),
                weatherSlots = listOf(
                    slot(
                        "2026-08-14T18:00:00Z",
                        "2026-08-14T20:00:00Z",
                        weather = weather(score = 96),
                        drone = drone(score = 96)
                    ),
                    slot(
                        "2026-08-15T07:00:00Z",
                        "2026-08-15T09:00:00Z",
                        weather = weather(score = 82),
                        drone = drone(score = 82)
                    )
                )
            )
        )

        assertEquals(FlightOpportunityStatus.READY, result.status)
        assertEquals(Instant.parse("2026-08-15T07:00:00Z"), result.bestOpportunity?.from)
        assertEquals(FlightOpportunityTimePreference.DAYTIME, result.bestOpportunity?.timePreference)
        assertEquals(Instant.parse("2026-08-14T18:00:00Z"), result.nextOpportunity?.from)
        assertTrue(result.alternatives.any { it.timePreference == FlightOpportunityTimePreference.EVENING })
    }

    @Test
    fun eveningWindowCanBeBestOnlyWhenNoDaytimeWindowExists() {
        val result = engine.evaluate(
            input(
                legalSegments = listOf(segment("2026-08-14T18:00:00Z", "2026-08-14T20:00:00Z")),
                weatherSlots = listOf(slot("2026-08-14T18:00:00Z", "2026-08-14T20:00:00Z"))
            )
        )

        assertEquals(FlightOpportunityStatus.READY, result.status)
        assertEquals(FlightOpportunityTimePreference.EVENING, result.bestOpportunity?.timePreference)
        assertTrue(
            result.bestOpportunity?.warnings?.contains(FlightOpportunityWarning.EVENING_OR_NIGHT_OPERATION) == true
        )
    }

    @Test
    fun unknownDroneKeepsOpportunityPartial() {
        val result = engine.evaluate(
            input(
                legalSegments = listOf(segment("2026-08-14T06:00:00Z", "2026-08-14T08:00:00Z")),
                weatherSlots = listOf(
                    slot(
                        "2026-08-14T06:00:00Z",
                        "2026-08-14T08:00:00Z",
                        drone = drone(
                            score = null,
                            level = DroneOperationalLevel.UNKNOWN,
                            completeness = DroneDataCompleteness.MINIMAL
                        )
                    )
                )
            )
        )

        assertEquals(FlightOpportunityStatus.PARTIAL, result.status)
        assertEquals(FlightOpportunityLevel.PARTIAL, result.bestOpportunity?.opportunityLevel)
        assertNull(result.bestOpportunity?.opportunityScore)
        assertFalse(result.bestOpportunity?.droneAssessmentAvailable ?: true)
        assertTrue(result.bestOpportunity?.warnings?.contains(FlightOpportunityWarning.DRONE_PROFILE_INCOMPLETE) == true)
    }

    @Test
    fun missingDroneAssessmentKeepsOpportunityPartial() {
        val result = engine.evaluate(
            input(
                legalSegments = listOf(segment("2026-08-14T06:00:00Z", "2026-08-14T08:00:00Z")),
                weatherSlots = listOf(slot("2026-08-14T06:00:00Z", "2026-08-14T08:00:00Z", drone = null))
            )
        )

        assertEquals(FlightOpportunityStatus.PARTIAL, result.status)
        assertEquals(FlightOpportunityLevel.PARTIAL, result.bestOpportunity?.opportunityLevel)
        assertNull(result.bestOpportunity?.opportunityScore)
        assertTrue(result.bestOpportunity?.warnings?.contains(FlightOpportunityWarning.DRONE_NOT_EVALUATED) == true)
    }

    @Test
    fun missingDroneAssessmentDoesNotExposeWeatherScoreAsOpportunityScore() {
        val result = engine.evaluate(
            input(
                legalSegments = listOf(segment("2026-08-14T06:00:00Z", "2026-08-14T08:00:00Z")),
                weatherSlots = listOf(
                    slot(
                        "2026-08-14T06:00:00Z",
                        "2026-08-14T08:00:00Z",
                        weather = weather(score = 100),
                        drone = null
                    )
                )
            )
        )

        assertEquals(100, result.bestOpportunity?.weatherScore)
        assertNull(result.bestOpportunity?.opportunityScore)
        assertEquals(FlightOpportunityLevel.PARTIAL, result.bestOpportunity?.opportunityLevel)
    }

    @Test
    fun favorableWeatherWithUnfavorableDroneIsBlockedByDrone() {
        val result = engine.evaluate(
            input(
                legalSegments = listOf(segment("2026-08-14T06:00:00Z", "2026-08-14T08:00:00Z")),
                weatherSlots = listOf(
                    slot(
                        "2026-08-14T06:00:00Z",
                        "2026-08-14T08:00:00Z",
                        weather = weather(score = 90, state = WeatherState.FAVORABLE),
                        drone = drone(score = 30, level = DroneOperationalLevel.UNFAVORABLE)
                    )
                )
            )
        )

        assertEquals(FlightOpportunityStatus.DRONE_UNFAVORABLE, result.status)
        assertNull(result.bestOpportunity)
        assertEquals(listOf(FlightOpportunityReasonCode.DRONE_UNFAVORABLE), result.blockers)
    }

    @Test
    fun weekendOpportunitiesAreReturnedByLocalDate() {
        val result = engine.evaluate(
            input(
                legalSegments = listOf(segment("2026-08-15T06:00:00Z", "2026-08-16T10:00:00Z")),
                weatherSlots = listOf(
                    slot("2026-08-15T06:00:00Z", "2026-08-15T08:00:00Z"),
                    slot("2026-08-16T06:00:00Z", "2026-08-16T08:00:00Z")
                )
            )
        )

        assertEquals(FlightOpportunityStatus.READY, result.status)
        assertEquals(2, result.weekendOpportunities.size)
        assertEquals(Instant.parse("2026-08-15T06:00:00Z"), result.weekendOpportunities.first().from)
    }

    @Test
    fun emptyWeatherSlotsReturnInsufficientData() {
        val result = engine.evaluate(
            input(
                legalSegments = listOf(segment("2026-08-14T06:00:00Z", "2026-08-14T08:00:00Z")),
                weatherSlots = emptyList()
            )
        )

        assertEquals(FlightOpportunityStatus.INSUFFICIENT_DATA, result.status)
        assertEquals(listOf(FlightOpportunityReasonCode.WEATHER_DATA_MISSING), result.blockers)
    }

    private fun input(
        legalSegments: List<LegalTimelineSegment>,
        weatherSlots: List<FlightOpportunityWeatherSlot>
    ): FlightOpportunityInput =
        FlightOpportunityInput(
            legalSegments = legalSegments,
            weatherSlots = weatherSlots,
            zoneId = zoneId,
            now = now
        )

    private fun segment(
        from: String,
        to: String,
        state: LegalTimelineState = LegalTimelineState.AVAILABLE,
        maxAltitudeAgl: Int? = 120
    ): LegalTimelineSegment =
        LegalTimelineSegment(
            from = Instant.parse(from),
            to = Instant.parse(to),
            state = state,
            rawState = state.name,
            maxAltitudeAgl = maxAltitudeAgl,
            authorization = null,
            contributors = emptyList(),
            warnings = emptyList(),
            confidence = "HIGH",
            reasonCodes = emptyList()
        )

    private fun slot(
        from: String,
        to: String,
        weather: WeatherAssessment = weather(),
        drone: DroneOperationalAssessment? = drone()
    ): FlightOpportunityWeatherSlot =
        FlightOpportunityWeatherSlot(
            from = Instant.parse(from),
            to = Instant.parse(to),
            weatherAssessment = weather,
            droneAssessment = drone
        )

    private fun weather(
        score: Int = 90,
        state: WeatherState = WeatherState.FAVORABLE,
        confidenceLevel: WeatherConfidenceLevel = WeatherConfidenceLevel.HIGH,
        reasons: List<WeatherReasonCode> = emptyList()
    ): WeatherAssessment =
        WeatherAssessment(
            score = score,
            state = state,
            confidence = WeatherConfidence(score = score, level = confidenceLevel, reasons = emptyList()),
            reasons = reasons,
            missingData = emptyList(),
            weatherCodeCategory = WeatherCodeCategory.BENIGN,
            gustSpreadKmh = null,
            gustRatio = null
        )

    private fun drone(
        score: Int? = 90,
        level: DroneOperationalLevel = DroneOperationalLevel.FAVORABLE,
        completeness: DroneDataCompleteness = DroneDataCompleteness.FULL
    ): DroneOperationalAssessment =
        DroneOperationalAssessment(
            level = level,
            score = score,
            factors = emptyList(),
            warnings = emptyList(),
            dataCompleteness = completeness,
            capabilities = DroneOperationalCapabilities(
                droneId = "fixture",
                displayName = "Fixture Drone"
            )
        )
}
