package it.droneskycheck.app.map

import it.droneskycheck.app.data.traffic.TrafficAircraft
import it.droneskycheck.app.data.traffic.TrafficAltitude
import it.droneskycheck.app.data.traffic.TrafficAssessment
import it.droneskycheck.app.data.traffic.TrafficCalculationConfidence
import it.droneskycheck.app.data.traffic.TrafficIdentifiers
import it.droneskycheck.app.data.traffic.TrafficMotion
import it.droneskycheck.app.data.traffic.TrafficPosition
import it.droneskycheck.app.data.traffic.TrafficProvenance
import it.droneskycheck.app.data.traffic.TrafficRelative
import it.droneskycheck.app.data.traffic.TrafficRelevance
import it.droneskycheck.app.data.traffic.TrafficSource
import it.droneskycheck.app.data.traffic.TrafficTarget
import it.droneskycheck.app.data.traffic.TrafficTime
import it.droneskycheck.app.ui.map.MapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

class TrafficAwarenessMapFeaturesTest {
    @Test
    fun mapsTrafficTargetToGeoJsonFeature() {
        val collection = trafficTargetsFeatureCollection(
            listOf(
                trafficTarget(
                    id = "icao:3009bc",
                    lat = 41.9278,
                    lon = 12.426,
                    callsign = "ARES44",
                    provider = "opensky",
                    source = "OpenSky",
                    trackDeg = 90.0
                )
            )
        )
        val feature = collection.features().orEmpty().single()
        val point = feature.geometry() as Point
        val properties = feature.properties()

        assertEquals(12.426, point.longitude(), 0.0)
        assertEquals(41.9278, point.latitude(), 0.0)
        assertEquals("icao:3009bc", properties?.get(TrafficAwarenessMapProperties.TargetId)?.asString)
        assertEquals("ARES44", properties?.get(TrafficAwarenessMapProperties.Callsign)?.asString)
        assertEquals("opensky", properties?.get(TrafficAwarenessMapProperties.Provider)?.asString)
        assertEquals("OpenSky", properties?.get(TrafficAwarenessMapProperties.Source)?.asString)
        assertEquals(90.0, properties?.get(TrafficAwarenessMapProperties.RotationDeg)?.asDouble ?: -1.0, 0.0)
        assertTrue(properties?.get(TrafficAwarenessMapProperties.HasRotation)?.asBoolean == true)
    }

    @Test
    fun geoJsonCoordinatesUseLongitudeLatitudeOrder() {
        val point = trafficTargetsFeatureCollection(
            listOf(trafficTarget(lat = 41.9, lon = 12.5))
        ).features().orEmpty().single().geometry() as Point

        assertEquals(12.5, point.coordinates()[0], 0.0)
        assertEquals(41.9, point.coordinates()[1], 0.0)
    }

    @Test
    fun invalidTargetDoesNotRemoveValidFeature() {
        val collection = trafficTargetsFeatureCollection(
            listOf(
                trafficTarget(id = "invalid", lat = Double.NaN, lon = 12.5),
                trafficTarget(id = "valid", lat = 41.9, lon = 12.5)
            )
        )

        assertEquals(1, collection.features().orEmpty().size)
        assertEquals(
            "valid",
            collection.features().orEmpty().single().properties()?.get(TrafficAwarenessMapProperties.TargetId)?.asString
        )
    }

    @Test
    fun layerIdsAreConsistentWithInstallAndUpdateNames() {
        assertEquals("traffic-awareness-source", MapLayerIds.TRAFFIC_AWARENESS_SOURCE_ID)
        assertEquals("traffic-awareness-symbol-layer", MapLayerIds.TRAFFIC_AWARENESS_SYMBOL_LAYER_ID)
        assertEquals("traffic-awareness-attention-halo-layer", MapLayerIds.TRAFFIC_AWARENESS_ATTENTION_HALO_LAYER_ID)
        assertEquals("traffic-awareness-radius-source", MapLayerIds.TRAFFIC_AWARENESS_RADIUS_SOURCE_ID)
        assertEquals("traffic-awareness-radius-fill-layer", MapLayerIds.TRAFFIC_AWARENESS_RADIUS_FILL_LAYER_ID)
        assertEquals("traffic-awareness-radius-line-layer", MapLayerIds.TRAFFIC_AWARENESS_RADIUS_LINE_LAYER_ID)
    }

    @Test
    fun geoJsonIncludesRelevanceFromAssessments() {
        val collection = trafficTargetsFeatureCollection(
            targets = listOf(
                trafficTarget(id = "traffic:attention"),
                trafficTarget(id = "traffic:monitor")
            ),
            assessments = mapOf(
                "traffic:attention" to assessment(TrafficRelevance.ATTENTION),
                "traffic:monitor" to assessment(TrafficRelevance.MONITOR)
            )
        )
        val featuresById = collection.features().orEmpty()
            .associateBy { it.properties()?.get(TrafficAwarenessMapProperties.TargetId)?.asString }

        assertEquals(
            "ATTENTION",
            featuresById["traffic:attention"]?.properties()?.get(TrafficAwarenessMapProperties.Relevance)?.asString
        )
        assertEquals(
            "MONITOR",
            featuresById["traffic:monitor"]?.properties()?.get(TrafficAwarenessMapProperties.Relevance)?.asString
        )
    }

    @Test
    fun missingAssessmentDefaultsToInformationRelevance() {
        val feature = trafficTargetsFeatureCollection(
            listOf(trafficTarget(id = "traffic:unknown"))
        ).features().orEmpty().single()

        assertEquals(
            "INFORMATION",
            feature.properties()?.get(TrafficAwarenessMapProperties.Relevance)?.asString
        )
    }

    @Test
    fun mapsCardinalTrackRotationsWithoutOffset() {
        listOf(0.0, 90.0, 180.0, 270.0).forEach { rotation ->
            val feature = trafficTargetsFeatureCollection(
                listOf(trafficTarget(trackDeg = rotation))
            ).features().orEmpty().single()

            assertEquals(
                rotation,
                feature.properties()?.get(TrafficAwarenessMapProperties.RotationDeg)?.asDouble ?: -1.0,
                0.0
            )
        }
    }

    @Test
    fun usesHeadingWhenTrackIsMissingAndLeavesUnrotatedWhenBothAreMissing() {
        val headingFeature = trafficTargetsFeatureCollection(
            listOf(trafficTarget(trackDeg = null, headingDeg = 314.0))
        ).features().orEmpty().single()
        assertEquals(
            314.0,
            headingFeature.properties()?.get(TrafficAwarenessMapProperties.RotationDeg)?.asDouble ?: -1.0,
            0.0
        )
        assertTrue(headingFeature.properties()?.get(TrafficAwarenessMapProperties.HasRotation)?.asBoolean == true)

        val unrotatedFeature = trafficTargetsFeatureCollection(
            listOf(trafficTarget(trackDeg = null, headingDeg = null))
        ).features().orEmpty().single()
        assertEquals(
            0.0,
            unrotatedFeature.properties()?.get(TrafficAwarenessMapProperties.RotationDeg)?.asDouble ?: -1.0,
            0.0
        )
        assertFalse(unrotatedFeature.properties()?.get(TrafficAwarenessMapProperties.HasRotation)?.asBoolean ?: true)
    }

    @Test
    fun mapsAdsbAndOgnTargetsInSingleLogicalCollection() {
        val collection = trafficTargetsFeatureCollection(
            listOf(
                trafficTarget(id = "icao:3009bc", callsign = "ARES44", provider = "opensky", source = "OpenSky"),
                trafficTarget(id = "ogn:FLRDDDDA9", callsign = "FLRDDDDA9", provider = "OGN", source = "FREEFLIGHT")
            )
        )
        val providers = collection.features().orEmpty()
            .mapNotNull { it.properties()?.get(TrafficAwarenessMapProperties.Provider)?.asString }

        assertEquals(listOf("opensky", "OGN"), providers)
    }

    @Test
    fun highAltitudeTargetIsNotFiltered() {
        val collection = trafficTargetsFeatureCollection(
            targets = listOf(
                trafficTarget(id = "traffic:high", geoM = 11_000.0)
            ),
            assessments = mapOf("traffic:high" to assessment(TrafficRelevance.ATTENTION))
        )

        assertEquals(1, collection.features().orEmpty().size)
        assertEquals(
            "ATTENTION",
            collection.features().orEmpty().single().properties()?.get(TrafficAwarenessMapProperties.Relevance)?.asString
        )
    }

    @Test
    fun radiusCollectionUsesSelectedCenterAndCanBeCleared() {
        val radius = trafficRadiusFeatureCollection(center = MapPoint(41.9, 12.5))
        val polygon = radius.features().orEmpty().single().geometry() as Polygon

        assertNotNull(polygon.coordinates().firstOrNull())
        assertTrue(polygon.coordinates().first().size > 8)
        assertTrue(trafficRadiusFeatureCollection(center = null).features().orEmpty().isEmpty())
        assertTrue(emptyTrafficFeatureCollection().features().orEmpty().isEmpty())
    }
}

private fun trafficTarget(
    id: String = "icao:test",
    lat: Double = 41.9,
    lon: Double = 12.5,
    callsign: String? = "TEST",
    provider: String? = "opensky",
    source: String? = "OpenSky",
    trackDeg: Double? = 0.0,
    headingDeg: Double? = null,
    geoM: Double? = null
): TrafficTarget =
    TrafficTarget(
        id = id,
        identifiers = TrafficIdentifiers(
            icao24 = id.removePrefix("icao:").takeIf { id.startsWith("icao:") },
            callsign = callsign,
            registration = null,
            sourceId = id.substringAfter(':', id)
        ),
        position = TrafficPosition(lat = lat, lon = lon),
        altitude = TrafficAltitude(
            baroM = null,
            geoM = geoM,
            mslM = null,
            aglM = null,
            sourceM = null,
            sourceReference = null
        ),
        motion = TrafficMotion(
            groundSpeedMps = null,
            verticalRateMps = null,
            trackDeg = trackDeg,
            headingDeg = headingDeg
        ),
        aircraft = TrafficAircraft(category = null, type = null),
        time = TrafficTime(timestamp = null, ageSec = null),
        relative = TrafficRelative(distanceM = null, bearingDeg = null),
        provider = provider,
        source = source,
        quality = null,
        sources = listOf(TrafficSource(provider = provider, source = source)),
        provenance = TrafficProvenance(
            sources = listOf(TrafficSource(provider = provider, source = source)),
            contributions = emptyList()
        )
    )

private fun assessment(relevance: TrafficRelevance): TrafficAssessment =
    TrafficAssessment(
        relevance = relevance,
        currentDistanceM = 1_000.0,
        converging = true,
        relativeBearingDeg = null,
        trackDifferenceDeg = null,
        cpaDistanceM = 500.0,
        timeToCpaSec = 30.0,
        calculationConfidence = TrafficCalculationConfidence.HIGH,
        reasons = emptyList()
    )
