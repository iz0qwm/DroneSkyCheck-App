package it.droneskycheck.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import it.droneskycheck.app.data.traffic.TrafficFeedType
import it.droneskycheck.app.data.traffic.TrafficHeatmapMaxAgl

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
    fun trafficFeedFiltersDefaultToOnAndPersistLocally() {
        val preferences = InMemoryMapPreferences()

        TrafficFeedType.filterableTypes.forEach { type ->
            assertTrue(preferences.isTrafficFeedEnabled(type))
        }

        preferences.setTrafficFeedEnabled(TrafficFeedType.FLARM, false)

        assertFalse(preferences.isTrafficFeedEnabled(TrafficFeedType.FLARM))
        assertTrue(preferences.isTrafficFeedEnabled(TrafficFeedType.ADSB))
    }

    @Test
    fun largeTextToggleDefaultsToOffAndPersistsLocally() {
        val preferences = InMemoryMapPreferences()

        assertFalse(preferences.isLargeTextEnabled())

        preferences.setLargeTextEnabled(true)

        assertTrue(preferences.isLargeTextEnabled())
    }

    @Test
    fun mapDarkeningToggleDefaultsToOffAndPersistsLocally() {
        val preferences = InMemoryMapPreferences()

        assertFalse(preferences.isMapDarkeningEnabled())

        preferences.setMapDarkeningEnabled(true)

        assertTrue(preferences.isMapDarkeningEnabled())
    }

    @Test
    fun enhancedZoneOutlinesToggleDefaultsToOffAndPersistsLocally() {
        val preferences = InMemoryMapPreferences()

        assertFalse(preferences.isEnhancedZoneOutlinesEnabled())

        preferences.setEnhancedZoneOutlinesEnabled(true)

        assertTrue(preferences.isEnhancedZoneOutlinesEnabled())
    }

    @Test
    fun trafficHeatmapDefaultsToOffWithBelow500AglAndPersistsLocally() {
        val preferences = InMemoryMapPreferences()

        assertFalse(preferences.isTrafficHeatmapEnabled())
        assertTrue(preferences.getTrafficHeatmapMaxAgl() == TrafficHeatmapMaxAgl.Below500)

        preferences.setTrafficHeatmapEnabled(true)
        preferences.setTrafficHeatmapMaxAgl(TrafficHeatmapMaxAgl.Below120)

        assertTrue(preferences.isTrafficHeatmapEnabled())
        assertTrue(preferences.getTrafficHeatmapMaxAgl() == TrafficHeatmapMaxAgl.Below120)
    }

    @Test
    fun invalidTrafficHeatmapAglPreferenceFallsBackToBelow500() {
        assertTrue(TrafficHeatmapMaxAgl.fromPreferenceValue("bad") == TrafficHeatmapMaxAgl.Below500)
        assertTrue(TrafficHeatmapMaxAgl.fromPreferenceValue(null) == TrafficHeatmapMaxAgl.Below500)
    }
}
