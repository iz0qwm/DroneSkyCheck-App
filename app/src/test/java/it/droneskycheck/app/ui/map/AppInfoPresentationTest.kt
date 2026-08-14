package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.UasDatasetUpdates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInfoPresentationTest {
    @Test
    fun headerIsTappableWhenNoOperationalState() {
        assertTrue(mapTitleAppInfoEnabled(statusMessage = null, trafficAttention = null))
    }

    @Test
    fun headerIsNotTappableWhenPriorityStateExists() {
        assertFalse(mapTitleAppInfoEnabled(statusMessage = "Ultima copia disponibile", trafficAttention = null))
        assertFalse(
            mapTitleAppInfoEnabled(
                statusMessage = null,
                trafficAttention = TrafficAttentionPresentation(
                    targetId = "abc",
                    title = "Traffico in avvicinamento",
                    detail = "I-TEST - 120 m",
                    attentionCount = 1
                )
            )
        )
    }

    @Test
    fun appBuildInfoUsesProvidedVersionNameAndCode() {
        val info = buildAppBuildInfoPresentation(
            appName = "Drone Sky Check",
            versionName = "3.0-beta4",
            versionCode = 19,
            androidRelease = "15",
            sdkInt = 35
        )

        assertEquals("Drone Sky Check", info.appName)
        assertEquals("3.0-beta4", info.versionName)
        assertEquals(19, info.versionCode)
        assertEquals("Android 15 (SDK 35)", info.platform)
    }

    @Test
    fun datasetInfoFallsBackWhenMetadataIsUnavailable() {
        val info = uasDatasetInfoPresentation(mapStatusMessage = null)

        assertEquals("Dati UAS disponibili", info.availabilityLabel)
        assertEquals(null, info.datasetVersion)
        assertEquals(null, info.sourceUpdatedAt)
        assertEquals(null, info.cachedOnDeviceAt)
        assertEquals("Versione e date dataset non disponibili nell'app", info.metadataFallbackLabel)
    }

    @Test
    fun datasetInfoUsesUpdatesJsonTimestampAsDatasetBuild() {
        val info = uasDatasetInfoPresentation(
            mapStatusMessage = null,
            updates = UasDatasetUpdates(
                sourceUpdatedAt = "2026-08-14T08:23:49.067110Z",
                addedCount = 2,
                removedCount = 1,
                modifiedCount = 3,
                cachedOnDeviceAt = "2026-08-14T08:24:02Z",
                degraded = false
            )
        )

        assertEquals("Build 14/08/2026 08:23 UTC", info.datasetVersion)
        assertEquals("14/08/2026 08:23 UTC", info.sourceUpdatedAt)
        assertEquals("14/08/2026 08:24 UTC", info.cachedOnDeviceAt)
        assertEquals("Variazioni ultimo build: +2 / -1 / ~3", info.metadataFallbackLabel)
    }

    @Test
    fun diagnosticTextContainsOnlyAvailableAppAndDatasetStatus() {
        val text = appInfoDiagnosticText(
            AppInfoPresentation(
                build = buildAppBuildInfoPresentation(
                    appName = "Drone Sky Check",
                    versionName = "3.0-beta4",
                    versionCode = 19,
                    androidRelease = "15",
                    sdkInt = 35
                ),
                dataset = uasDatasetInfoPresentation(mapStatusMessage = null)
            )
        )

        assertTrue(text.contains("Versione app: 3.0-beta4 (19)"))
        assertTrue(text.contains("Dati UAS: Dati UAS disponibili"))
        assertTrue(text.contains("Versione dataset UAS: non disponibile"))
        assertTrue(text.contains("Metadata UAS: Versione e date dataset non disponibili nell'app"))
        assertFalse(text.contains("Aggiornamento sorgente UAS:"))
        assertFalse(text.contains("Cache dispositivo UAS:"))
    }
}
