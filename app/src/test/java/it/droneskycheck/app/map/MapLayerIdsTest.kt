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
            assertFalse(layer.url.contains("/zones"))
        }
    }

    @Test
    fun tacticalAndCorridorLayersUseDynamicZonesApiTypes() {
        val dynamicTypes = MapLayerIds.DYNAMIC_ZONES_LAYERS.map { it.zonesType }.toSet()

        assertEquals(setOf("TACTICAL", "CORRIDOR"), dynamicTypes)
        assertTrue(MapLayerIds.DYNAMIC_ZONES_LAYERS.all { it.minZoom >= 6.0f })
    }

    @Test
    fun tacticalAndCorridorFeatureTypesResolveToUserCategories() {
        assertEquals(DscLayerCategory.Tactical, MapLayerIds.categoryForFeatureType("TACTICAL"))
        assertEquals(DscLayerCategory.Tactical, MapLayerIds.categoryForFeatureType("TACTICAL_ENR_5_2_2_6"))
        assertEquals(DscLayerCategory.Corridors, MapLayerIds.categoryForFeatureType("CORRIDOR"))
        assertEquals(DscLayerCategory.Corridors, MapLayerIds.categoryForFeatureType("CORRIDOR_ENR_5_2_2_5"))
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
            "split/Other.geojson",
            "split/P_NOTAM.geojson",
            "split/P_NOTAM_DFLIGHT.geojson",
            "split/P_NOTAM_FAA.geojson",
            "split/P_PARKS.geojson",
            "split/P_PARKS_ENR_561.geojson",
            "split/P_SECURITY.geojson",
            "split/P_SUP.geojson",
            "layer_parchi.geojson"
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
    fun everyStaticLayerHasAVisualCategoryAndOnlyEnvironmentalAreasAreHiddenByDefault() {
        MapLayerIds.STATIC_LAYERS.forEach { layer ->
            val expectedVisible = layer.category != DscLayerCategory.EnvironmentalProtectedAreas

            assertEquals(expectedVisible, DscLayerCategory.defaultVisibility[layer.category])
        }
    }

    @Test
    fun environmentalProtectedAreasUseDedicatedStaticLayerAndCategory() {
        val layer = MapLayerIds.STATIC_LAYERS.single { it.key == "parks-env" }

        assertEquals("layer_parchi.geojson", layer.relativePath)
        assertEquals(DscLayerCategory.EnvironmentalProtectedAreas, layer.category)
        assertEquals("dsc-parks-env-source", layer.sourceId)
        assertEquals("dsc-parks-env-fill", layer.fillLayerId)
        assertEquals("dsc-parks-env-line", layer.lineLayerId)
        assertTrue(layer.isEnvironmentalProtectedArea)
        assertEquals(DscLayerCategory.EnvironmentalProtectedAreas, MapLayerIds.categoryForFeatureType("PARKS_ENV"))
        assertFalse(DscLayerCategory.defaultVisibility.getValue(DscLayerCategory.EnvironmentalProtectedAreas))
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
    fun noLegacyOtherNfzDatasetsAreLoaded() {
        val otherNfzPaths = MapLayerIds.STATIC_LAYERS
            .filter { it.category == DscLayerCategory.OtherNfz }
            .map { it.relativePath }
            .toSet()

        assertEquals(emptySet<String>(), otherNfzPaths)
    }

    @Test
    fun deprecatedLegacyDatasetsAreNeverLoaded() {
        val loadedPaths = MapLayerIds.STATIC_LAYERS.map { it.relativePath }.toSet()

        assertFalse("split/P.geojson" in loadedPaths)
        assertFalse("split/NFZ_PARKS.geojson" in loadedPaths)
    }
}
