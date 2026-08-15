package it.droneskycheck.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPreferencesRepositoryTest {
    @Test
    fun weatherToggleDefaultsToOffAndPersistsInLocalPreferences() {
        val preferences = InMemoryMapPreferences()

        assertFalse(preferences.isWeatherAnalysisEnabled())

        preferences.setWeatherAnalysisEnabled(true)

        assertTrue(preferences.isWeatherAnalysisEnabled())
    }

    @Test
    fun trafficAlertPreferencesDefaultToOnAndPersistLocally() {
        val preferences = InMemoryMapPreferences()

        assertTrue(preferences.isTrafficAlertSoundEnabled())
        assertTrue(preferences.isTrafficAlertVibrationEnabled())
        assertFalse(preferences.isHighAltitudeTrafficAlertEnabled())

        preferences.setTrafficAlertSoundEnabled(false)
        preferences.setTrafficAlertVibrationEnabled(false)
        preferences.setHighAltitudeTrafficAlertEnabled(true)

        assertFalse(preferences.isTrafficAlertSoundEnabled())
        assertFalse(preferences.isTrafficAlertVibrationEnabled())
        assertTrue(preferences.isHighAltitudeTrafficAlertEnabled())
    }

    @Test
    fun largeTextToggleDefaultsToOffAndPersistsLocally() {
        val preferences = InMemoryMapPreferences()

        assertFalse(preferences.isLargeTextEnabled())

        preferences.setLargeTextEnabled(true)

        assertTrue(preferences.isLargeTextEnabled())
    }
}
