package it.droneskycheck.app.data.weatherAlerts

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherAlertPresentationTest {
    private val now = Instant.parse("2026-09-02T12:00:00Z")

    @Test
    fun bolognaTodayThunderstormShowsYellowBanner() {
        val banner = weatherAlertBanner(
            response(
                today = criticality(
                    level = CriticalityLevel.YELLOW,
                    risks = risks(thunderstorm = CriticalityLevel.YELLOW)
                ),
                vigilanceToday = VigilanceLevel.WEAK
            ),
            now
        )

        assertEquals(CriticalityLevel.YELLOW, banner?.criticalityLevel)
        assertEquals("Allerta gialla per temporali · Collina bolognese", banner?.detail)
    }

    @Test
    fun noCriticalityAndNoVigilanceHidesBanner() {
        assertNull(weatherAlertBanner(response(), now))
    }

    @Test
    fun tomorrowCriticalityIsNotPresentedAsCurrent() {
        val banner = weatherAlertBanner(
            response(
                tomorrow = criticality(
                    level = CriticalityLevel.YELLOW,
                    risks = risks(thunderstorm = CriticalityLevel.YELLOW),
                    onset = null,
                    expires = null
                )
            ),
            now
        )

        assertEquals(WeatherBannerKind.CRITICALITY_TOMORROW, banner?.kind)
        assertEquals("Domani allerta gialla per temporali · Collina bolognese", banner?.detail)
    }

    @Test
    fun weakVigilanceStaysOnlyInDetails() {
        assertNull(weatherAlertBanner(response(vigilanceToday = VigilanceLevel.WEAK), now))
    }

    @Test
    fun moderateVigilanceShowsInformationalBanner() {
        val banner = weatherAlertBanner(response(vigilanceToday = VigilanceLevel.MODERATE), now)

        assertEquals(WeatherBannerKind.VIGILANCE, banner?.kind)
        assertEquals("Vigilanza meteorologica", banner?.headline)
    }

    @Test
    fun criticalityHasPriorityOverHeavyPrecipitation() {
        val banner = weatherAlertBanner(
            response(
                today = criticality(CriticalityLevel.YELLOW, risks(thunderstorm = CriticalityLevel.YELLOW)),
                vigilanceToday = VigilanceLevel.HEAVY
            ),
            now
        )

        assertEquals(WeatherBannerKind.CRITICALITY_TODAY, banner?.kind)
        assertEquals(CriticalityLevel.YELLOW, banner?.criticalityLevel)
    }

    @Test
    fun expiredTodayIsNotActive() {
        val expired = criticality(
            CriticalityLevel.YELLOW,
            risks(thunderstorm = CriticalityLevel.YELLOW),
            onset = Instant.parse("2026-09-02T08:00:00Z"),
            expires = Instant.parse("2026-09-02T11:59:59Z")
        )

        assertNull(weatherAlertBanner(response(today = expired), now))
    }

    @Test
    fun futureTodayIsNotActive() {
        val future = criticality(
            CriticalityLevel.YELLOW,
            risks(thunderstorm = CriticalityLevel.YELLOW),
            onset = Instant.parse("2026-09-02T13:00:00Z"),
            expires = Instant.parse("2026-09-02T20:00:00Z")
        )

        assertNull(weatherAlertBanner(response(today = future), now))
    }

    @Test
    fun unchangedFingerprintProducesNoChangeEvent() {
        val original = response(
            today = criticality(CriticalityLevel.YELLOW, risks(thunderstorm = CriticalityLevel.YELLOW))
        )

        assertNull(localWeatherChange(original, original.copy(), now))
    }

    @Test
    fun changedFingerprintProducesChangeEvent() {
        val yellow = response(
            today = criticality(CriticalityLevel.YELLOW, risks(thunderstorm = CriticalityLevel.YELLOW))
        )
        val orange = response(
            today = criticality(CriticalityLevel.ORANGE, risks(thunderstorm = CriticalityLevel.ORANGE))
        )

        val change = localWeatherChange(yellow, orange, now)

        assertNotNull(change)
        assertEquals(CriticalityLevel.YELLOW, change?.previous?.criticalityLevel)
        assertEquals(CriticalityLevel.ORANGE, change?.current?.criticalityLevel)
    }

    @Test
    fun riskFormatterHandlesSingleAndMultipleRisks() {
        val single = criticality(CriticalityLevel.YELLOW, risks(thunderstorm = CriticalityLevel.YELLOW))
        val multiple = criticality(
            CriticalityLevel.ORANGE,
            risks(
                thunderstorm = CriticalityLevel.YELLOW,
                hydrogeological = CriticalityLevel.ORANGE,
                hydraulic = CriticalityLevel.YELLOW
            )
        )

        assertEquals("temporali", formatActiveRisks(single))
        assertEquals(
            "temporali, rischio idrogeologico e rischio idraulico",
            formatActiveRisks(multiple)
        )
        assertEquals(CriticalityLevel.ORANGE, multiple.maximumLevel)
    }

    private fun response(
        today: WeatherCriticalityPeriod? = criticality(CriticalityLevel.NONE, risks()),
        tomorrow: WeatherCriticalityPeriod? = criticality(
            CriticalityLevel.NONE,
            risks(),
            onset = null,
            expires = null
        ),
        vigilanceToday: VigilanceLevel = VigilanceLevel.NONE
    ): WeatherAlertResponse = WeatherAlertResponse(
        point = WeatherAlertPoint(44.49, 11.34),
        criticality = WeatherCriticality(
            zoneCode = "Emil-C2",
            zoneName = "Collina bolognese",
            periods = buildMap {
                today?.let { put("TODAY", it) }
                tomorrow?.let { put("TOMORROW", it) }
            }
        ),
        vigilance = WeatherVigilance(
            zoneId = 84,
            zoneName = "Appennino emiliano romagnolo",
            periods = mapOf(
                "TODAY" to WeatherVigilancePeriod(
                    WeatherPrecipitation(vigilanceToday, vigilanceLevelLabel(vigilanceToday))
                )
            )
        ),
        sources = null,
        disclaimer = null
    )

    private fun criticality(
        level: CriticalityLevel,
        risks: Map<WeatherRisk, CriticalityLevel>,
        onset: Instant? = Instant.parse("2026-09-02T10:00:00Z"),
        expires: Instant? = Instant.parse("2026-09-02T21:59:59Z")
    ) = WeatherCriticalityPeriod(onset, expires, level, risks)

    private fun risks(
        thunderstorm: CriticalityLevel = CriticalityLevel.NONE,
        hydrogeological: CriticalityLevel = CriticalityLevel.NONE,
        hydraulic: CriticalityLevel = CriticalityLevel.NONE
    ) = mapOf(
        WeatherRisk.THUNDERSTORM to thunderstorm,
        WeatherRisk.HYDROGEOLOGICAL to hydrogeological,
        WeatherRisk.HYDRAULIC to hydraulic
    )
}
