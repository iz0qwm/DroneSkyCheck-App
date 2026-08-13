package it.droneskycheck.app.data

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodicNoticePolicyTest {
    private val now = Instant.parse("2026-08-13T12:00:00Z")

    @Test
    fun neverShownShouldShow() {
        assertTrue(
            PeriodicNoticePolicy.shouldShow(
                lastShownAt = null,
                now = now
            )
        )
    }

    @Test
    fun shownOneDayAgoShouldNotShow() {
        assertFalse(
            PeriodicNoticePolicy.shouldShow(
                lastShownAt = now.minus(Duration.ofDays(1)),
                now = now
            )
        )
    }

    @Test
    fun shownSixDaysAgoShouldNotShow() {
        assertFalse(
            PeriodicNoticePolicy.shouldShow(
                lastShownAt = now.minus(Duration.ofDays(6)),
                now = now
            )
        )
    }

    @Test
    fun shownSevenDaysAgoShouldShow() {
        assertTrue(
            PeriodicNoticePolicy.shouldShow(
                lastShownAt = now.minus(Duration.ofDays(7)),
                now = now
            )
        )
    }

    @Test
    fun shownEightDaysAgoShouldShow() {
        assertTrue(
            PeriodicNoticePolicy.shouldShow(
                lastShownAt = now.minus(Duration.ofDays(8)),
                now = now
            )
        )
    }
}
