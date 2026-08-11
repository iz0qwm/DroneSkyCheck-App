package it.droneskycheck.app.data

import android.content.Context

interface MapPreferences {
    fun isWeatherAnalysisEnabled(): Boolean
    fun setWeatherAnalysisEnabled(enabled: Boolean)
}

class MapPreferencesRepository(
    context: Context
) : MapPreferences {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    override fun isWeatherAnalysisEnabled(): Boolean =
        preferences.getBoolean(KeyWeatherAnalysisEnabled, false)

    override fun setWeatherAnalysisEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KeyWeatherAnalysisEnabled, enabled)
            .apply()
    }

    private companion object {
        const val PreferencesName = "dsc_map_preferences"
        const val KeyWeatherAnalysisEnabled = "weather_analysis_enabled"
    }
}

class InMemoryMapPreferences(
    initialWeatherAnalysisEnabled: Boolean = false
) : MapPreferences {
    private var weatherAnalysisEnabled = initialWeatherAnalysisEnabled

    override fun isWeatherAnalysisEnabled(): Boolean = weatherAnalysisEnabled

    override fun setWeatherAnalysisEnabled(enabled: Boolean) {
        weatherAnalysisEnabled = enabled
    }
}
