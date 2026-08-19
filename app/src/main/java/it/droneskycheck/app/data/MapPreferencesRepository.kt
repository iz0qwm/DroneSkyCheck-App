package it.droneskycheck.app.data

import android.content.Context
import it.droneskycheck.app.data.traffic.TrafficFeedType

interface MapPreferences {
    fun isWeatherAnalysisEnabled(): Boolean
    fun setWeatherAnalysisEnabled(enabled: Boolean)
    fun isLargeTextEnabled(): Boolean
    fun setLargeTextEnabled(enabled: Boolean)
    fun isMapDarkeningEnabled(): Boolean
    fun setMapDarkeningEnabled(enabled: Boolean)
    fun isEnhancedZoneOutlinesEnabled(): Boolean
    fun setEnhancedZoneOutlinesEnabled(enabled: Boolean)
    fun getAppThemeMode(): AppThemeMode
    fun setAppThemeMode(mode: AppThemeMode)
    fun isTrafficAlertSoundEnabled(): Boolean
    fun setTrafficAlertSoundEnabled(enabled: Boolean)
    fun isTrafficAlertVibrationEnabled(): Boolean
    fun setTrafficAlertVibrationEnabled(enabled: Boolean)
    fun isHighAltitudeTrafficAlertEnabled(): Boolean
    fun setHighAltitudeTrafficAlertEnabled(enabled: Boolean)
    fun isTrafficAwarenessPositionLocked(): Boolean
    fun setTrafficAwarenessPositionLocked(locked: Boolean)
    fun isTrafficFeedEnabled(type: TrafficFeedType): Boolean
    fun setTrafficFeedEnabled(type: TrafficFeedType, enabled: Boolean)
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

    override fun isLargeTextEnabled(): Boolean =
        preferences.getBoolean(KeyLargeTextEnabled, false)

    override fun setLargeTextEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KeyLargeTextEnabled, enabled)
            .apply()
    }

    override fun isMapDarkeningEnabled(): Boolean =
        preferences.getBoolean(KeyMapDarkeningEnabled, false)

    override fun setMapDarkeningEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KeyMapDarkeningEnabled, enabled)
            .apply()
    }

    override fun isEnhancedZoneOutlinesEnabled(): Boolean =
        preferences.getBoolean(KeyEnhancedZoneOutlinesEnabled, false)

    override fun setEnhancedZoneOutlinesEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KeyEnhancedZoneOutlinesEnabled, enabled)
            .apply()
    }

    override fun getAppThemeMode(): AppThemeMode =
        AppThemeMode.fromPreferenceValue(preferences.getString(KeyAppThemeMode, null))

    override fun setAppThemeMode(mode: AppThemeMode) {
        preferences.edit()
            .putString(KeyAppThemeMode, mode.preferenceValue)
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

    override fun isHighAltitudeTrafficAlertEnabled(): Boolean =
        preferences.getBoolean(KeyHighAltitudeTrafficAlertEnabled, false)

    override fun setHighAltitudeTrafficAlertEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KeyHighAltitudeTrafficAlertEnabled, enabled)
            .apply()
    }

    override fun isTrafficAwarenessPositionLocked(): Boolean =
        preferences.getBoolean(KeyTrafficAwarenessPositionLocked, false)

    override fun setTrafficAwarenessPositionLocked(locked: Boolean) {
        preferences.edit()
            .putBoolean(KeyTrafficAwarenessPositionLocked, locked)
            .apply()
    }

    override fun isTrafficFeedEnabled(type: TrafficFeedType): Boolean =
        preferences.getBoolean(type.preferenceKey(), true)

    override fun setTrafficFeedEnabled(type: TrafficFeedType, enabled: Boolean) {
        preferences.edit()
            .putBoolean(type.preferenceKey(), enabled)
            .apply()
    }

    private companion object {
        const val PreferencesName = "dsc_map_preferences"
        const val KeyWeatherAnalysisEnabled = "weather_analysis_enabled"
        const val KeyLargeTextEnabled = "large_text_enabled"
        const val KeyMapDarkeningEnabled = "map_darkening_enabled"
        const val KeyEnhancedZoneOutlinesEnabled = "enhanced_zone_outlines_enabled"
        const val KeyAppThemeMode = "app_theme_mode"
        const val KeyTrafficAlertSoundEnabled = "traffic_alert_sound_enabled"
        const val KeyTrafficAlertVibrationEnabled = "traffic_alert_vibration_enabled"
        const val KeyHighAltitudeTrafficAlertEnabled = "traffic_alert_high_altitude_enabled"
        const val KeyTrafficAwarenessPositionLocked = "traffic_awareness_position_locked"
    }
}

class InMemoryMapPreferences(
    initialWeatherAnalysisEnabled: Boolean = false,
    initialLargeTextEnabled: Boolean = false,
    initialMapDarkeningEnabled: Boolean = false,
    initialEnhancedZoneOutlinesEnabled: Boolean = false,
    initialAppThemeMode: AppThemeMode = AppThemeMode.System,
    initialTrafficAlertSoundEnabled: Boolean = true,
    initialTrafficAlertVibrationEnabled: Boolean = true,
    initialHighAltitudeTrafficAlertEnabled: Boolean = false,
    initialTrafficAwarenessPositionLocked: Boolean = false,
    initialTrafficFeedEnabled: Map<TrafficFeedType, Boolean> = TrafficFeedType.filterableTypes.associateWith { true }
) : MapPreferences {
    private var weatherAnalysisEnabled = initialWeatherAnalysisEnabled
    private var largeTextEnabled = initialLargeTextEnabled
    private var mapDarkeningEnabled = initialMapDarkeningEnabled
    private var enhancedZoneOutlinesEnabled = initialEnhancedZoneOutlinesEnabled
    private var appThemeMode = initialAppThemeMode
    private var trafficAlertSoundEnabled = initialTrafficAlertSoundEnabled
    private var trafficAlertVibrationEnabled = initialTrafficAlertVibrationEnabled
    private var highAltitudeTrafficAlertEnabled = initialHighAltitudeTrafficAlertEnabled
    private var trafficAwarenessPositionLocked = initialTrafficAwarenessPositionLocked
    private var trafficFeedEnabled = initialTrafficFeedEnabled.toMutableMap()

    override fun isWeatherAnalysisEnabled(): Boolean = weatherAnalysisEnabled

    override fun setWeatherAnalysisEnabled(enabled: Boolean) {
        weatherAnalysisEnabled = enabled
    }

    override fun isLargeTextEnabled(): Boolean = largeTextEnabled

    override fun setLargeTextEnabled(enabled: Boolean) {
        largeTextEnabled = enabled
    }

    override fun isMapDarkeningEnabled(): Boolean = mapDarkeningEnabled

    override fun setMapDarkeningEnabled(enabled: Boolean) {
        mapDarkeningEnabled = enabled
    }

    override fun isEnhancedZoneOutlinesEnabled(): Boolean = enhancedZoneOutlinesEnabled

    override fun setEnhancedZoneOutlinesEnabled(enabled: Boolean) {
        enhancedZoneOutlinesEnabled = enabled
    }

    override fun getAppThemeMode(): AppThemeMode = appThemeMode

    override fun setAppThemeMode(mode: AppThemeMode) {
        appThemeMode = mode
    }

    override fun isTrafficAlertSoundEnabled(): Boolean = trafficAlertSoundEnabled

    override fun setTrafficAlertSoundEnabled(enabled: Boolean) {
        trafficAlertSoundEnabled = enabled
    }

    override fun isTrafficAlertVibrationEnabled(): Boolean = trafficAlertVibrationEnabled

    override fun setTrafficAlertVibrationEnabled(enabled: Boolean) {
        trafficAlertVibrationEnabled = enabled
    }

    override fun isHighAltitudeTrafficAlertEnabled(): Boolean = highAltitudeTrafficAlertEnabled

    override fun setHighAltitudeTrafficAlertEnabled(enabled: Boolean) {
        highAltitudeTrafficAlertEnabled = enabled
    }

    override fun isTrafficAwarenessPositionLocked(): Boolean = trafficAwarenessPositionLocked

    override fun setTrafficAwarenessPositionLocked(locked: Boolean) {
        trafficAwarenessPositionLocked = locked
    }

    override fun isTrafficFeedEnabled(type: TrafficFeedType): Boolean =
        trafficFeedEnabled[type] ?: true

    override fun setTrafficFeedEnabled(type: TrafficFeedType, enabled: Boolean) {
        trafficFeedEnabled[type] = enabled
    }
}

val TrafficFeedType.Companion.filterableTypes: List<TrafficFeedType>
    get() = listOf(
        TrafficFeedType.ADSB,
        TrafficFeedType.FANET,
        TrafficFeedType.FLARM,
        TrafficFeedType.FREEFLIGHT
    )

private fun TrafficFeedType.preferenceKey(): String =
    "traffic_feed_${name.lowercase()}_enabled"

enum class AppThemeMode(
    val preferenceValue: String,
    val label: String,
    val description: String
) {
    System(
        preferenceValue = "system",
        label = "Sistema",
        description = "Segue il tema chiaro o scuro del dispositivo."
    ),
    Light(
        preferenceValue = "light",
        label = "Chiaro",
        description = "Usa sempre il tema chiaro."
    ),
    Dark(
        preferenceValue = "dark",
        label = "Scuro",
        description = "Usa sempre il tema scuro."
    );

    companion object {
        fun fromPreferenceValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.preferenceValue == value } ?: System
    }
}
