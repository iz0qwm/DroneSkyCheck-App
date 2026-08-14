package it.droneskycheck.app.data.solar

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarLightCalculatorTest {
    private val calculator = SolarLightCalculator()
    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun sunriseAndSunsetAreCoherentForRomeDateAndTimezone() {
        val window = calculator.windowForDate(
            latitude = 41.9,
            longitude = 12.5,
            zoneId = rome,
            date = LocalDate.parse("2026-08-14")
        )

        assertEquals(LocalDate.parse("2026-08-14"), window.date)
        assertBetween(
            actual = requireNotNull(window.sunrise),
            earliest = "2026-08-14T04:00:00Z",
            latest = "2026-08-14T04:40:00Z"
        )
        assertBetween(
            actual = requireNotNull(window.sunset),
            earliest = "2026-08-14T17:50:00Z",
            latest = "2026-08-14T18:40:00Z"
        )
        assertEquals(6, window.sunrise?.atZone(rome)?.hour)
        assertEquals(20, window.sunset?.atZone(rome)?.hour)
    }

    @Test
    fun morningBlueAndGoldenHourSurroundSunrise() {
        val window = calculator.windowForDate(41.9, 12.5, rome, LocalDate.parse("2026-08-14"))

        assertTrue(requireNotNull(window.morningBlueHour).from.isBefore(requireNotNull(window.morningBlueHour).to))
        assertTrue(requireNotNull(window.morningGoldenHour).from.isBefore(requireNotNull(window.sunrise)))
        assertTrue(requireNotNull(window.morningGoldenHour).to.isAfter(requireNotNull(window.sunrise)))
        assertTrue(requireNotNull(window.morningBlueHour).to == window.morningGoldenHour?.from)
    }

    @Test
    fun eveningGoldenAndBlueHourSurroundSunset() {
        val window = calculator.windowForDate(41.9, 12.5, rome, LocalDate.parse("2026-08-14"))

        assertTrue(requireNotNull(window.eveningGoldenHour).from.isBefore(requireNotNull(window.sunset)))
        assertTrue(requireNotNull(window.eveningGoldenHour).to.isAfter(requireNotNull(window.sunset)))
        assertTrue(requireNotNull(window.eveningBlueHour).from == window.eveningGoldenHour?.to)
        assertTrue(requireNotNull(window.eveningBlueHour).to.isAfter(requireNotNull(window.eveningBlueHour).from))
    }

    @Test
    fun nextDayUsesItsOwnSolarEvents() {
        val windows = calculator.windowsForRange(
            latitude = 41.9,
            longitude = 12.5,
            zoneId = rome,
            from = Instant.parse("2026-08-14T00:00:00Z"),
            to = Instant.parse("2026-08-16T00:00:00Z")
        )

        assertTrue(windows.size >= 2)
        assertEquals(LocalDate.parse("2026-08-15"), windows[1].date)
        assertNotEquals(windows[0].sunrise, windows[1].sunrise)
    }

    @Test
    fun daylightSavingTransitionKeepsLocalDateAndValidBands() {
        val beforeDst = calculator.windowForDate(41.9, 12.5, rome, LocalDate.parse("2026-03-28"))
        val dstDay = calculator.windowForDate(41.9, 12.5, rome, LocalDate.parse("2026-03-29"))

        assertEquals(LocalDate.parse("2026-03-28"), beforeDst.sunrise?.atZone(rome)?.toLocalDate())
        assertEquals(LocalDate.parse("2026-03-29"), dstDay.sunrise?.atZone(rome)?.toLocalDate())
        assertNotNull(dstDay.morningBlueHour)
        assertNotNull(dstDay.eveningBlueHour)
        assertTrue(Duration.between(requireNotNull(dstDay.sunrise), requireNotNull(dstDay.sunset)).toHours() > 10)
    }

    private fun assertBetween(actual: Instant, earliest: String, latest: String) {
        assertTrue("$actual should be after $earliest", !actual.isBefore(Instant.parse(earliest)))
        assertTrue("$actual should be before $latest", !actual.isAfter(Instant.parse(latest)))
    }
}
