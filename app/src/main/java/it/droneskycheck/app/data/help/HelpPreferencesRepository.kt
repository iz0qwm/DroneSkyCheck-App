package it.droneskycheck.app.data.help

import android.content.Context

interface HelpPreferences {
    fun getLastSeenOnboardingVersion(): Int
    fun setLastSeenOnboardingVersion(version: Int)
    fun getLastSeenContentVersion(): Int
    fun setLastSeenContentVersion(version: Int)
}

class HelpPreferencesRepository(
    context: Context
) : HelpPreferences {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    override fun getLastSeenOnboardingVersion(): Int =
        preferences.getInt(KeyLastSeenOnboardingVersion, 0)

    override fun setLastSeenOnboardingVersion(version: Int) {
        preferences.edit()
            .putInt(KeyLastSeenOnboardingVersion, version)
            .apply()
    }

    override fun getLastSeenContentVersion(): Int =
        preferences.getInt(KeyLastSeenContentVersion, 0)

    override fun setLastSeenContentVersion(version: Int) {
        preferences.edit()
            .putInt(KeyLastSeenContentVersion, version)
            .apply()
    }

    private companion object {
        const val PreferencesName = "dsc_help_preferences"
        const val KeyLastSeenOnboardingVersion = "last_seen_onboarding_version"
        const val KeyLastSeenContentVersion = "last_seen_content_version"
    }
}

class InMemoryHelpPreferences(
    initialLastSeenOnboardingVersion: Int = 0,
    initialLastSeenContentVersion: Int = 0
) : HelpPreferences {
    private var lastSeenOnboardingVersion = initialLastSeenOnboardingVersion
    private var lastSeenContentVersion = initialLastSeenContentVersion

    override fun getLastSeenOnboardingVersion(): Int = lastSeenOnboardingVersion

    override fun setLastSeenOnboardingVersion(version: Int) {
        lastSeenOnboardingVersion = version
    }

    override fun getLastSeenContentVersion(): Int = lastSeenContentVersion

    override fun setLastSeenContentVersion(version: Int) {
        lastSeenContentVersion = version
    }
}
