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

        preferences.setTrafficAlertSoundEnabled(false)
        preferences.setTrafficAlertVibrationEnabled(false)

        assertFalse(preferences.isTrafficAlertSoundEnabled())
        assertFalse(preferences.isTrafficAlertVibrationEnabled())
    }
}
