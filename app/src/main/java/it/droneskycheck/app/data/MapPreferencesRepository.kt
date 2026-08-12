package it.droneskycheck.app.data

import android.content.Context

interface MapPreferences {
    fun isWeatherAnalysisEnabled(): Boolean
    fun setWeatherAnalysisEnabled(enabled: Boolean)
    fun isTrafficAlertSoundEnabled(): Boolean
    fun setTrafficAlertSoundEnabled(enabled: Boolean)
    fun isTrafficAlertVibrationEnabled(): Boolean
    fun setTrafficAlertVibrationEnabled(enabled: Boolean)
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

    override fun isTrafficAlertSoundEnabled(): Boolean =
        preferences.getBoolean(KeyTrafficAlertSoundEnabled, true)

    override fun setTrafficAlertSoundEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KeyTrafficAlertSoundEnabled, enabled)
            .apply()
    }

    override fun isTrafficAlertVibrationEnabled(): Boolean =
        preferences.getBoolean(KeyTrafficAlertVibrationEnabled, true)

    override fun setTrafficAlertVibrationEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KeyTrafficAlertVibrationEnabled, enabled)
            .apply()
    }

    private companion object {
        const val PreferencesName = "dsc_map_preferences"
        const val KeyWeatherAnalysisEnabled = "weather_analysis_enabled"
        const val KeyTrafficAlertSoundEnabled = "traffic_alert_sound_enabled"
        const val KeyTrafficAlertVibrationEnabled = "traffic_alert_vibration_enabled"
    }
}

class InMemoryMapPreferences(
    initialWeatherAnalysisEnabled: Boolean = false,
    initialTrafficAlertSoundEnabled: Boolean = true,
    initialTrafficAlertVibrationEnabled: Boolean = true
) : MapPreferences {
    private var weatherAnalysisEnabled = initialWeatherAnalysisEnabled
    private var trafficAlertSoundEnabled = initialTrafficAlertSoundEnabled
    private var trafficAlertVibrationEnabled = initialTrafficAlertVibrationEnabled

    override fun isWeatherAnalysisEnabled(): Boolean = weatherAnalysisEnabled

    override fun setWeatherAnalysisEnabled(enabled: Boolean) {
        weatherAnalysisEnabled = enabled
    }

    override fun isTrafficAlertSoundEnabled(): Boolean = trafficAlertSoundEnabled

    override fun setTrafficAlertSoundEnabled(enabled: Boolean) {
        trafficAlertSoundEnabled = enabled
    }

    override fun isTrafficAlertVibrationEnabled(): Boolean = trafficAlertVibrationEnabled

    override fun setTrafficAlertVibrationEnabled(enabled: Boolean) {
        trafficAlertVibrationEnabled = enabled
    }
}
