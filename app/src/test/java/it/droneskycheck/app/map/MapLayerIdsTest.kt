package it.droneskycheck.app.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLayerIdsTest {
    @Test
    fun staticLayersUseKwosMirrorAndNotZonesApi() {
        assertTrue(MapLayerIds.STATIC_LAYERS.isNotEmpty())
        MapLayerIds.STATIC_LAYERS.forEach { layer ->
            assertTrue(layer.url.startsWith(MapLayerIds.KWOS_DATA_BASE_URL))
            assertTrue(layer.url.contains("/split/"))
            assertFalse(layer.url.contains("/zones"))
        }
    }

    @Test
    fun staticLayersCoverExpectedSplitGeoJsonFiles() {
        val expected = setOf(
            "split/ATM09_AVIOSUP_REBUILT.geojson",
            "split/ATM09_CTR.geojson",
            "split/ATM09_DANGER.geojson",
            "split/ATM09_LIF.geojson",
            "split/ATM09_OTHER.geojson",
            "split/ATM09_PARKS.geojson",
            "split/ATM09_PRISON.geojson",
            "split/ATM09_RESTRICTED.geojson",
            "split/NFZ_PARKS.geojson",
            "split/Other.geojson",
            "split/P.geojson",
            "split/P_NOTAM.geojson",
            "split/P_NOTAM_DFLIGHT.geojson",
            "split/P_NOTAM_FAA.geojson",
            "split/P_PARKS.geojson",
            "split/P_PARKS_ENR_561.geojson",
            "split/P_SECURITY.geojson",
            "split/P_SUP.geojson"
        )

        assertEquals(expected, MapLayerIds.STATIC_LAYERS.map { it.relativePath }.toSet())
    }

    @Test
    fun aviosuperficiUseRebuiltDatasetWithoutLegacyDuplicate() {
        assertFalse(MapLayerIds.STATIC_LAYERS.any {
            it.relativePath == "split/ATM09_AVIOSUP.geojson"
        })
        assertTrue(MapLayerIds.STATIC_LAYERS.any {
            it.relativePath == "split/ATM09_AVIOSUP_REBUILT.geojson"
        })
    }

    @Test
    fun localDetailLayersBecomeVisibleBeforeStreetLevel() {
        val rebuiltAviosup = MapLayerIds.STATIC_LAYERS.single {
            it.relativePath == "split/ATM09_AVIOSUP_REBUILT.geojson"
        }
        val airports = MapLayerIds.STATIC_LAYERS.single {
            it.relativePath == "split/ATM09_OTHER.geojson"
        }

        assertTrue(rebuiltAviosup.minZoom <= 9.0f)
        assertTrue(airports.minZoom >= 8.0f)
    }

    @Test
    fun everyStaticLayerHasAVisualCategoryVisibleByDefault() {
        MapLayerIds.STATIC_LAYERS.forEach { layer ->
            assertTrue(DscLayerCategory.defaultVisibility[layer.category] == true)
        }
    }

    @Test
    fun airportAndAviosuperficiDatasetsUseSeparateUserCategories() {
        val airportLayer = MapLayerIds.STATIC_LAYERS.single {
            it.relativePath == "split/ATM09_OTHER.geojson"
        }
        val aviosupLayers = MapLayerIds.STATIC_LAYERS.filter {
            it.relativePath == "split/ATM09_AVIOSUP_REBUILT.geojson"
        }

        assertEquals(DscLayerCategory.Airports, airportLayer.category)
        assertEquals(1, aviosupLayers.size)
        assertTrue(aviosupLayers.all { it.category == DscLayerCategory.Aviosuperfici })
    }

    @Test
    fun parksUseTheSameVirtualGroupingAsTheWebapp() {
        val parkPaths = setOf(
            "split/P_PARKS.geojson",
            "split/P_PARKS_ENR_561.geojson",
            "split/ATM09_PARKS.geojson"
        )

        val groupedParkPaths = MapLayerIds.STATIC_LAYERS
            .filter { it.category == DscLayerCategory.Parks }
            .map { it.relativePath }
            .toSet()

        assertEquals(parkPaths, groupedParkPaths)
    }

    @Test
    fun legacyProtectedAndNfzDatasetsAreNotControlledByParksToggle() {
        val otherNfzPaths = MapLayerIds.STATIC_LAYERS
            .filter { it.category == DscLayerCategory.OtherNfz }
            .map { it.relativePath }
            .toSet()

        assertEquals(setOf("split/NFZ_PARKS.geojson", "split/P.geojson"), otherNfzPaths)
    }
}
