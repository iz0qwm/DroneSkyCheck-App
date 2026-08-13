package it.droneskycheck.app.data

import android.content.Context
import java.time.Duration
import java.time.Instant

interface PeriodicNoticePreferences {
    fun getLastPeriodicNoticeShownAt(): Instant?
    fun setLastPeriodicNoticeShownAt(shownAt: Instant)
}

/**
 * App-level local preference for occasional notices.
 *
 * Kept separate from Help preferences because the notice is not part of the
 * guide, onboarding, or versioned help content.
 */
class PeriodicNoticePreferencesRepository(
    context: Context
) : PeriodicNoticePreferences {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    override fun getLastPeriodicNoticeShownAt(): Instant? {
        val epochMillis = preferences.getLong(KeyLastShownAt, MissingTimestamp)
        return epochMillis
            .takeUnless { it == MissingTimestamp }
            ?.let(Instant::ofEpochMilli)
    }

    override fun setLastPeriodicNoticeShownAt(shownAt: Instant) {
        preferences.edit()
            .putLong(KeyLastShownAt, shownAt.toEpochMilli())
            .apply()
    }

    private companion object {
        const val PreferencesName = "dsc_periodic_notice_preferences"
        const val KeyLastShownAt = "lastPeriodicNoticeShownAt"
        const val MissingTimestamp = Long.MIN_VALUE
    }
}

object PeriodicNoticePolicy {
    val DefaultInterval: Duration = Duration.ofDays(7)

    fun shouldShow(
        lastShownAt: Instant?,
        now: Instant,
        minimumInterval: Duration = DefaultInterval
    ): Boolean =
        lastShownAt == null || !lastShownAt.plus(minimumInterval).isAfter(now)
}

object PeriodicNoticeLinks {
    const val BuyMeACoffeeUrl = "https://buymeacoffee.com/tuttosuidroni"
}
