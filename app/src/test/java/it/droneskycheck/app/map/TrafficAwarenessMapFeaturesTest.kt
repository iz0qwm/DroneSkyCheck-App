package it.droneskycheck.app.map

import it.droneskycheck.app.data.traffic.TrafficAircraft
import it.droneskycheck.app.data.traffic.TrafficAltitude
import it.droneskycheck.app.data.traffic.TrafficAssessment
import it.droneskycheck.app.data.traffic.TrafficCalculationConfidence
import it.droneskycheck.app.data.traffic.TrafficFeedType
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
import org.maplibre.geojson.LineString
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
        assertEquals("traffic-awareness-marker-layer", MapLayerIds.TRAFFIC_AWARENESS_MARKER_LAYER_ID)
        assertEquals("traffic-awareness-glyph-source", MapLayerIds.TRAFFIC_AWARENESS_GLYPH_SOURCE_ID)
        assertEquals("traffic-awareness-attention-glyph-layer", MapLayerIds.TRAFFIC_AWARENESS_ATTENTION_GLYPH_LAYER_ID)
        assertEquals("traffic-awareness-glyph-layer", MapLayerIds.TRAFFIC_AWARENESS_GLYPH_LAYER_ID)
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
    fun classifiesTrafficAltitudeBandFromAglOnly() {
        assertEquals(TrafficAltitudeBand.VERY_LOW, trafficAltitudeBandForAgl(0.0))
        assertEquals(TrafficAltitudeBand.VERY_LOW, trafficAltitudeBandForAgl(299.0))
        assertEquals(TrafficAltitudeBand.LOW, trafficAltitudeBandForAgl(300.0))
        assertEquals(TrafficAltitudeBand.LOW, trafficAltitudeBandForAgl(999.0))
        assertEquals(TrafficAltitudeBand.HIGH, trafficAltitudeBandForAgl(1_000.0))
        assertEquals(TrafficAltitudeBand.HIGH, trafficAltitudeBandForAgl(8_000.0))
        assertEquals(TrafficAltitudeBand.UNKNOWN, trafficAltitudeBandForAgl(null))
        assertEquals(TrafficAltitudeBand.UNKNOWN, trafficAltitudeBandForAgl(Double.NaN))
    }

    @Test
    fun geoJsonIncludesAltitudeBandIndependentlyFromRelevance() {
        val collection = trafficTargetsFeatureCollection(
            targets = listOf(
                trafficTarget(id = "traffic:very-low-info", aglM = 180.0),
                trafficTarget(id = "traffic:low-attention", aglM = 750.0)
            ),
            assessments = mapOf(
                "traffic:very-low-info" to assessment(TrafficRelevance.INFORMATION),
                "traffic:low-attention" to assessment(TrafficRelevance.ATTENTION)
            )
        )
        val featuresById = collection.features().orEmpty()
            .associateBy { it.properties()?.get(TrafficAwarenessMapProperties.TargetId)?.asString }

        assertEquals(
            "VERY_LOW",
            featuresById["traffic:very-low-info"]?.properties()?.get(TrafficAwarenessMapProperties.AltitudeBand)?.asString
        )
        assertEquals(
            "INFORMATION",
            featuresById["traffic:very-low-info"]?.properties()?.get(TrafficAwarenessMapProperties.Relevance)?.asString
        )
        assertEquals(
            "LOW",
            featuresById["traffic:low-attention"]?.properties()?.get(TrafficAwarenessMapProperties.AltitudeBand)?.asString
        )
        assertEquals(
            "ATTENTION",
            featuresById["traffic:low-attention"]?.properties()?.get(TrafficAwarenessMapProperties.Relevance)?.asString
        )
    }

    @Test
    fun geoJsonMarksAirSenseDronesWithDroneTargetKind() {
        val collection = trafficTargetsFeatureCollection(
            targets = listOf(
                trafficTarget(
                    id = "airsense:drone-7",
                    callsign = "DSC-DRONE-7",
                    provider = "AirSense",
                    source = "AirSense",
                    objectType = "drone",
                    aglM = 42.0
                ),
                trafficTarget(id = "icao:3009bc", provider = "opensky", source = "OpenSky")
            )
        )
        val featuresById = collection.features().orEmpty()
            .associateBy { it.properties()?.get(TrafficAwarenessMapProperties.TargetId)?.asString }

        assertEquals(
            "DRONE",
            featuresById["airsense:drone-7"]?.properties()?.get(TrafficAwarenessMapProperties.TargetKind)?.asString
        )
        assertEquals(
            "AIRCRAFT",
            featuresById["icao:3009bc"]?.properties()?.get(TrafficAwarenessMapProperties.TargetKind)?.asString
        )
    }

    @Test
    fun geoJsonDiscriminatesAdsbHelicopterAndUavCategories() {
        val collection = trafficTargetsFeatureCollection(
            targets = listOf(
                trafficTarget(id = "icao:heli", category = "A7"),
                trafficTarget(id = "icao:uav", category = "B6")
            )
        )
        val featuresById = collection.features().orEmpty()
            .associateBy { it.properties()?.get(TrafficAwarenessMapProperties.TargetId)?.asString }

        assertEquals(
            "HELICOPTER",
            featuresById["icao:heli"]?.properties()?.get(TrafficAwarenessMapProperties.TargetKind)?.asString
        )
        assertEquals(
            "DRONE",
            featuresById["icao:uav"]?.properties()?.get(TrafficAwarenessMapProperties.TargetKind)?.asString
        )
    }

    @Test
    fun altitudeBandDoesNotUseGeometricAltitudeAsAglFallback() {
        val feature = trafficTargetsFeatureCollection(
            listOf(trafficTarget(id = "traffic:geo-only", geoM = 11_000.0, aglM = null))
        ).features().orEmpty().single()

        assertEquals(
            "UNKNOWN",
            feature.properties()?.get(TrafficAwarenessMapProperties.AltitudeBand)?.asString
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
    fun geoJsonClassifiesReliableTrafficFeedTypes() {
        val collection = trafficTargetsFeatureCollection(
            listOf(
                trafficTarget(id = "icao:3009bc", provider = "opensky", source = "OpenSky"),
                trafficTarget(id = "ogn:fanet", provider = "OGN", source = "FANET"),
                trafficTarget(id = "ogn:flarm", provider = "OGN", source = "FLARM"),
                trafficTarget(id = "ogn:freeflight", provider = "OGN", source = "FREEFLIGHT")
            )
        )
        val types = collection.features().orEmpty()
            .map { it.properties()?.get(TrafficAwarenessMapProperties.FeedType)?.asString }

        assertEquals(
            listOf(
                TrafficFeedType.ADSB.name,
                TrafficFeedType.FANET.name,
                TrafficFeedType.FLARM.name,
                TrafficFeedType.FREEFLIGHT.name
            ),
            types
        )
    }

    @Test
    fun altitudeLabelUsesAglAndFallsBackToIdentifiedAvailableAltitude() {
        val agl = trafficTargetsFeatureCollection(
            listOf(trafficTarget(id = "traffic:agl", aglM = 849.6))
        ).features().orEmpty().single()
        val msl = trafficTargetsFeatureCollection(
            listOf(trafficTarget(id = "traffic:no-agl", mslM = 2_450.0, aglM = null))
        ).features().orEmpty().single()

        assertEquals("850 m AGL", agl.properties()?.get(TrafficAwarenessMapProperties.AltitudeLabel)?.asString)
        assertEquals("2450 m AMSL", msl.properties()?.get(TrafficAwarenessMapProperties.AltitudeLabel)?.asString)
    }

    @Test
    fun radarLabelCombinesTypeAglAndSpeedOnTargetFeature() {
        val feature = trafficTargetsFeatureCollection(
            listOf(
                trafficTarget(
                    id = "traffic:label",
                    aglM = 720.0,
                    speedMps = 74.595
                )
            )
        ).features().orEmpty().single()

        assertEquals(
            "AEREO · 720 m AGL\n145 kt",
            feature.properties()?.get(TrafficAwarenessMapProperties.RadarLabel)?.asString
        )
        assertEquals("AEREO", feature.properties()?.get(TrafficAwarenessMapProperties.TrafficTypeLabel)?.asString)
        assertEquals("145 kt", feature.properties()?.get(TrafficAwarenessMapProperties.SpeedLabel)?.asString)
    }

    @Test
    fun radarLabelFallsBackToMslWithoutInventingAgl() {
        val feature = trafficTargetsFeatureCollection(
            listOf(
                trafficTarget(
                    id = "traffic:msl",
                    provider = null,
                    source = null,
                    callsign = null,
                    aglM = null,
                    mslM = 2_450.0
                )
            )
        ).features().orEmpty().single()

        assertEquals(
            "TRAFFICO · 2450 m AMSL",
            feature.properties()?.get(TrafficAwarenessMapProperties.RadarLabel)?.asString
        )
    }

    @Test
    fun radarLabelOmitsMissingAndZeroSpeed() {
        val features = trafficTargetsFeatureCollection(
            listOf(
                trafficTarget(id = "traffic:missing-speed", source = "FLARM", provider = "OGN", aglM = 950.0, speedMps = null),
                trafficTarget(id = "traffic:zero-speed", source = "FREEFLIGHT", provider = "OGN", aglM = 640.0, speedMps = 0.0)
            )
        ).features().orEmpty()

        assertEquals(
            "GLIDER · 950 m AGL",
            features[0].properties()?.get(TrafficAwarenessMapProperties.RadarLabel)?.asString
        )
        assertEquals(
            "FREEFLIGHT · 640 m AGL",
            features[1].properties()?.get(TrafficAwarenessMapProperties.RadarLabel)?.asString
        )
        assertFalse(features.any {
            it.properties()?.get(TrafficAwarenessMapProperties.RadarLabel)?.asString?.contains("0 kt") == true
        })
    }

    @Test
    fun radarLabelDoesNotRenderNullOrNanText() {
        val feature = trafficTargetsFeatureCollection(
            listOf(
                trafficTarget(
                    id = "traffic:nulls",
                    provider = null,
                    source = null,
                    callsign = null,
                    aglM = Double.NaN,
                    geoM = Double.NaN,
                    speedMps = Double.NaN
                )
            )
        ).features().orEmpty().single()
        val radarLabel = feature.properties()?.get(TrafficAwarenessMapProperties.RadarLabel)?.asString.orEmpty()

        assertEquals("TRAFFICO", radarLabel)
        assertFalse(radarLabel.contains("null", ignoreCase = true))
        assertFalse(radarLabel.contains("NaN", ignoreCase = true))
    }

    @Test
    fun futureVectorUsesSpeedAndTrackAndSkipsMissingMotion() {
        val collection = trafficDirectionVectorFeatureCollection(
            listOf(
                trafficTarget(id = "moving", speedMps = 20.0, trackDeg = 90.0),
                trafficTarget(id = "missing-speed", speedMps = null, trackDeg = 90.0),
                trafficTarget(id = "missing-heading", speedMps = 20.0, trackDeg = null, headingDeg = null)
            ),
            projectionSeconds = 45.0
        )

        val features = collection.features().orEmpty()
        assertEquals(1, features.size)
        assertEquals("moving", features.single().properties()?.get(TrafficAwarenessMapProperties.TargetId)?.asString)
        val line = features.single().geometry() as LineString
        val points = line.coordinates()
        assertTrue(points.first().longitude() > 12.5)
        assertTrue(points.last().longitude() > points.first().longitude())
    }

    @Test
    fun trafficGlyphUsesLineSegmentsForVisibleMapIcons() {
        val features = trafficAircraftGlyphFeatureCollection(
            listOf(
                trafficTarget(id = "aircraft", trackDeg = 90.0),
                trafficTarget(id = "helicopter", category = "A7", trackDeg = 90.0),
                trafficTarget(id = "drone", category = "B6", trackDeg = 90.0),
                trafficTarget(id = "flarm", source = "FLARM", provider = "OGN", trackDeg = 90.0),
                trafficTarget(id = "freeflight", source = "FREEFLIGHT", provider = "OGN", trackDeg = 90.0)
            )
        ).features().orEmpty()

        val countByTarget = features.groupingBy {
            it.properties()?.get(TrafficAwarenessMapProperties.TargetId)?.asString
        }.eachCount()
        assertEquals(5, countByTarget["aircraft"])
        assertEquals(5, countByTarget["helicopter"])
        assertEquals(10, countByTarget["drone"])
        assertEquals(5, countByTarget["flarm"])
        assertEquals(8, countByTarget["freeflight"])
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
    speedMps: Double? = null,
    category: String? = null,
    aircraftType: String? = null,
    geoM: Double? = null,
    mslM: Double? = null,
    aglM: Double? = null,
    objectType: String? = null
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
            mslM = mslM,
            aglM = aglM,
            sourceM = null,
            sourceReference = null
        ),
        motion = TrafficMotion(
            groundSpeedMps = speedMps,
            verticalRateMps = null,
            trackDeg = trackDeg,
            headingDeg = headingDeg
        ),
        aircraft = TrafficAircraft(category = category, type = aircraftType),
        time = TrafficTime(timestamp = null, ageSec = null),
        relative = TrafficRelative(distanceM = null, bearingDeg = null),
        provider = provider,
        source = source,
        quality = null,
        sources = listOf(TrafficSource(provider = provider, source = source)),
        provenance = TrafficProvenance(
            sources = listOf(TrafficSource(provider = provider, source = source)),
            contributions = emptyList()
        ),
        objectType = objectType
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
