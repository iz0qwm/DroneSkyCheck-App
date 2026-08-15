package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.traffic.TrafficAircraft
import it.droneskycheck.app.data.traffic.TrafficAltitude
import it.droneskycheck.app.data.traffic.TrafficAwarenessResponse
import it.droneskycheck.app.data.traffic.TrafficAwarenessState
import it.droneskycheck.app.data.traffic.TrafficAssessment
import it.droneskycheck.app.data.traffic.TrafficCalculationConfidence
import it.droneskycheck.app.data.traffic.TrafficCenter
import it.droneskycheck.app.data.traffic.TrafficIdentifiers
import it.droneskycheck.app.data.traffic.TrafficMotion
import it.droneskycheck.app.data.traffic.TrafficPosition
import it.droneskycheck.app.data.traffic.TrafficProviderStatus
import it.droneskycheck.app.data.traffic.TrafficRelative
import it.droneskycheck.app.data.traffic.TrafficRelevance
import it.droneskycheck.app.data.traffic.TrafficSource
import it.droneskycheck.app.data.traffic.TrafficSummary
import it.droneskycheck.app.data.traffic.TrafficTarget
import it.droneskycheck.app.data.traffic.TrafficTargetKind
import it.droneskycheck.app.data.traffic.TrafficTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrafficAwarenessPresentationTest {
    @Test
    fun buttonDescriptionCoversOffLoadingCountAndErrorWithSnapshot() {
        assertEquals(
            "Traffic Awareness disattivata",
            trafficAwarenessButtonContentDescription(TrafficAwarenessState())
        )

        assertEquals(
            "Traffic Awareness attiva, aggiornamento traffico in corso",
            trafficAwarenessButtonContentDescription(TrafficAwarenessState(enabled = true, loading = true))
        )

        val withSnapshot = TrafficAwarenessState(
            enabled = true,
            loading = false,
            response = responseWithTargets(3)
        )
        assertEquals(3, trafficAwarenessTargetCount(withSnapshot))
        assertEquals(
            "Traffic Awareness attiva, 3 traffici rilevati",
            trafficAwarenessButtonContentDescription(withSnapshot)
        )

        val temporaryError = withSnapshot.copy(error = "Traffic Awareness non disponibile")
        assertEquals(3, trafficAwarenessTargetCount(temporaryError))
        assertNull(trafficAwarenessUnavailableMessage(temporaryError))
    }

    @Test
    fun totalErrorWithoutSnapshotShowsDiscreteUnavailableMessage() {
        val state = TrafficAwarenessState(
            enabled = true,
            loading = false,
            response = null,
            error = "Traffic Awareness non disponibile"
        )

        assertEquals("Traffico temporaneamente non disponibile", trafficAwarenessUnavailableMessage(state))
    }

    @Test
    fun targetSheetRowsUseFallbackNamesAndCarefulUnits() {
        val target = trafficTarget(
            callsign = null,
            registration = null,
            icao24 = null,
            sourceId = "FLRDDDDA9",
            provider = "OGN",
            source = "FREEFLIGHT",
            distanceM = 12_483.3,
            bearingDeg = 296.8,
            sourceM = 68.0,
            speedMps = 35.59,
            headingDeg = 314.0
        )

        assertEquals("FLRDDDDA9", target.trafficSheetTitle())
        assertEquals(
            listOf(
                TrafficAwarenessInfoRow("Distanza", "12,5 km"),
                TrafficAwarenessInfoRow("Direzione", "297°"),
                TrafficAwarenessInfoRow("Quota ricevuta", "68 m"),
                TrafficAwarenessInfoRow("Velocita", "128 km/h"),
                TrafficAwarenessInfoRow("Heading", "314°"),
                TrafficAwarenessInfoRow("Sorgente", "OGN · FREEFLIGHT")
            ),
            target.trafficSheetRows()
        )
    }

    @Test
    fun targetSheetLabelsBarometricGeometricAndAglWithoutInventingMsl() {
        val rows = trafficTarget(
            baroM = 198.12,
            geoM = 312.42,
            aglM = 44.0,
            sourceM = 68.0
        ).trafficSheetRows()

        assertEquals("Quota AGL" to "44 m", rows.first { it.label == "Quota AGL" }.toPair())
        assertEquals("Quota geometrica" to "312 m", rows.first { it.label == "Quota geometrica" }.toPair())
        assertEquals("Quota barometrica" to "198 m", rows.first { it.label == "Quota barometrica" }.toPair())
        assertEquals("Quota ricevuta" to "68 m", rows.first { it.label == "Quota ricevuta" }.toPair())
        assertNull(rows.firstOrNull { it.label.contains("MSL") })
    }

    @Test
    fun distanceUsesMetersBelowOneKilometer() {
        assertEquals("740 m", formatTrafficDistance(740.4))
    }

    @Test
    fun targetSheetShowsDiscreteOperationalRelevanceRows() {
        val rows = trafficTarget().trafficSheetRows(
            TrafficAssessment(
                relevance = TrafficRelevance.ATTENTION,
                currentDistanceM = 2_500.0,
                converging = true,
                relativeBearingDeg = 0.0,
                trackDifferenceDeg = 0.0,
                cpaDistanceM = 1_800.0,
                timeToCpaSec = 80.0,
                calculationConfidence = TrafficCalculationConfidence.HIGH,
                reasons = emptyList()
            )
        )

        assertEquals("Rilevanza operativa" to "Attenzione", rows[0].toPair())
        assertEquals("Traiettoria" to "In avvicinamento", rows[1].toPair())
        assertEquals("Passaggio minimo stimato" to "1,8 km", rows[2].toPair())
        assertEquals("Tempo stimato" to "1 min 20 s", rows[3].toPair())
        assertEquals("Calcolo traiettoria" to "Stima completa", rows[4].toPair())
    }

    @Test
    fun noTrafficAttentionBannerForInformationOrMonitor() {
        val info = trafficTarget(id = "traffic:info")
        val monitor = trafficTarget(id = "traffic:monitor")

        assertNull(
            trafficAttentionPresentation(
                targets = listOf(info, monitor),
                assessments = mapOf(
                    info.id to assessment(TrafficRelevance.INFORMATION),
                    monitor.id to assessment(TrafficRelevance.MONITOR)
                )
            )
        )
    }

    @Test
    fun singleTrafficAttentionBannerUsesFallbackNameDistanceAndTime() {
        val target = trafficTarget(id = "traffic:attention", callsign = "RYR9ZQ", distanceM = 5_600.0)
        val banner = trafficAttentionPresentation(
            targets = listOf(target),
            assessments = mapOf(
                target.id to assessment(
                    relevance = TrafficRelevance.ATTENTION,
                    currentDistanceM = 5_600.0,
                    cpaDistanceM = 1_800.0,
                    timeToCpaSec = 80.0
                )
            )
        )

        assertEquals("traffic:attention", banner?.targetId)
        assertEquals("Traffico in avvicinamento", banner?.title)
        assertEquals("RYR9ZQ - 5,6 km - CPA tra 1 min 20 s", banner?.detail)
        assertEquals(1, banner?.attentionCount)
    }

    @Test
    fun multipleAttentionTargetsPreferLowerPositiveTimeToCpa() {
        val targetA = trafficTarget(id = "traffic:a", callsign = "A", distanceM = 4_000.0)
        val targetB = trafficTarget(id = "traffic:b", callsign = "B", distanceM = 7_000.0)
        val banner = trafficAttentionPresentation(
            targets = listOf(targetA, targetB),
            assessments = mapOf(
                targetA.id to assessment(
                    relevance = TrafficRelevance.ATTENTION,
                    currentDistanceM = 4_000.0,
                    cpaDistanceM = 1_000.0,
                    timeToCpaSec = 150.0
                ),
                targetB.id to assessment(
                    relevance = TrafficRelevance.ATTENTION,
                    currentDistanceM = 7_000.0,
                    cpaDistanceM = 2_500.0,
                    timeToCpaSec = 60.0
                )
            )
        )

        assertEquals("traffic:b", banner?.targetId)
        assertEquals("2 traffici in avvicinamento", banner?.title)
        assertEquals(2, banner?.attentionCount)
    }

    @Test
    fun multipleAttentionTargetsTieBreakByCpaDistanceCurrentDistanceAndId() {
        val farId = trafficTarget(id = "traffic:z", callsign = "Z", distanceM = 8_000.0)
        val nearId = trafficTarget(id = "traffic:a", callsign = "A", distanceM = 8_000.0)
        val banner = trafficAttentionPresentation(
            targets = listOf(farId, nearId),
            assessments = mapOf(
                farId.id to assessment(TrafficRelevance.ATTENTION, currentDistanceM = 3_000.0, cpaDistanceM = 1_000.0, timeToCpaSec = 60.0),
                nearId.id to assessment(TrafficRelevance.ATTENTION, currentDistanceM = 3_000.0, cpaDistanceM = 1_000.0, timeToCpaSec = 60.0)
            )
        )

        assertEquals("traffic:a", banner?.targetId)
    }

    @Test
    fun adsbSheetPresentationPrioritizesOperationalAreaAndKeepsAltitudeSemantics() {
        val target = trafficTarget(
            callsign = "RYR9ZQ",
            provider = "opensky",
            source = "OpenSky",
            baroM = 198.12,
            geoM = 312.42,
            speedMps = 35.59,
            trackDeg = 303.0
        )
        val presentation = target.trafficSheetPresentation(
            assessment(
                relevance = TrafficRelevance.ATTENTION,
                currentDistanceM = 5_600.0,
                cpaDistanceM = 1_800.0,
                timeToCpaSec = 80.0
            )
        )

        assertEquals("RYR9ZQ", presentation.title)
        assertEquals("opensky", presentation.sourceLabel)
        assertEquals("Attenzione", presentation.relevanceLabel)
        assertEquals("Rispetto all'area operativa", presentation.sections[0].title)
        assertEquals("Passaggio minimo stimato" to "1,8 km", presentation.sections[0].rows.first { it.label == "Passaggio minimo stimato" }.toPair())
        assertEquals("Geometrica" to "312 m", presentation.sections.first { it.title == "Quota" }.rows.first { it.label == "Geometrica" }.toPair())
        assertEquals("Barometrica" to "198 m", presentation.sections.first { it.title == "Quota" }.rows.first { it.label == "Barometrica" }.toPair())
    }

    @Test
    fun ognSheetPresentationShowsReceivedAltitudeAndCompactSource() {
        val target = trafficTarget(
            callsign = null,
            icao24 = null,
            sourceId = "FLRDDDDA9",
            provider = "OGN",
            source = "FREEFLIGHT",
            sourceM = 68.0,
            headingDeg = 314.0
        )
        val presentation = target.trafficSheetPresentation(assessment(TrafficRelevance.MONITOR))

        assertEquals("FLRDDDDA9", presentation.title)
        assertEquals("OGN · FREEFLIGHT", presentation.sourceLabel)
        assertEquals("Quota ricevuta" to "68 m", presentation.sections.first { it.title == "Quota" }.rows.single().toPair())
        assertNull(presentation.sections.first { it.title == "Dati traffico" }.rows.firstOrNull { it.value.contains("null") })
    }

    @Test
    fun airSenseDroneSheetPresentationShowsDroneType() {
        val target = trafficTarget(
            id = "airsense:drone-7",
            callsign = "DSC-DRONE-7",
            provider = "AirSense",
            source = "Drone Sky Check",
            aglM = 42.0,
            objectType = "drone"
        )
        val presentation = target.trafficSheetPresentation(assessment(TrafficRelevance.MONITOR))

        assertEquals(TrafficTargetKind.DRONE, presentation.targetKind)
        assertEquals("DSC-DRONE-7", presentation.title)
        assertEquals("Drone rilevato", presentation.secondaryIdentity)
        assertEquals(
            "Tipo" to "Drone AirSense",
            presentation.sections.first { it.title == "Dati traffico" }.rows.first().toPair()
        )
    }

    @Test
    fun adsbHelicopterSheetPresentationShowsHelicopterType() {
        val target = trafficTarget(
            id = "icao:heli",
            callsign = "HELI01",
            icao24 = "300001",
            category = "A7"
        )
        val presentation = target.trafficSheetPresentation(assessment(TrafficRelevance.MONITOR))

        assertEquals(TrafficTargetKind.HELICOPTER, presentation.targetKind)
        assertEquals("Elicottero - ICAO 300001", presentation.secondaryIdentity)
        assertEquals(
            "Tipo" to "Elicottero",
            presentation.sections.first { it.title == "Dati traffico" }.rows.first().toPair()
        )
    }
}

private fun TrafficAwarenessInfoRow.toPair(): Pair<String, String> = label to value

private fun assessment(
    relevance: TrafficRelevance,
    currentDistanceM: Double? = 2_500.0,
    cpaDistanceM: Double? = null,
    timeToCpaSec: Double? = null
): TrafficAssessment =
    TrafficAssessment(
        relevance = relevance,
        currentDistanceM = currentDistanceM,
        converging = if (relevance == TrafficRelevance.ATTENTION) true else null,
        relativeBearingDeg = 296.8,
        trackDifferenceDeg = null,
        cpaDistanceM = cpaDistanceM,
        timeToCpaSec = timeToCpaSec,
        calculationConfidence = TrafficCalculationConfidence.HIGH,
        reasons = emptyList()
    )

private fun responseWithTargets(count: Int): TrafficAwarenessResponse =
    TrafficAwarenessResponse(
        ok = true,
        generatedAt = 1_800_000_000_000,
        servedAt = 1_800_000_000_100,
        center = TrafficCenter(41.9, 12.5),
        radiusKm = 20.0,
        traffic = TrafficSummary(
            count = count,
            targets = (1..count).map { index -> trafficTarget(id = "traffic:$index", callsign = "T$index") }
        ),
        providers = mapOf("opensky" to TrafficProviderStatus("ok", count, null)),
        cache = null
    )

private fun trafficTarget(
    id: String = "icao:test",
    callsign: String? = "ARES44",
    registration: String? = null,
    icao24: String? = "3009bc",
    sourceId: String? = "3009bc",
    provider: String? = "OpenSky",
    source: String? = null,
    distanceM: Double? = 6_859.2,
    bearingDeg: Double? = 4.1,
    baroM: Double? = null,
    geoM: Double? = null,
    aglM: Double? = null,
    sourceM: Double? = null,
    speedMps: Double? = null,
    trackDeg: Double? = null,
    headingDeg: Double? = null,
    category: String? = null,
    aircraftType: String? = null,
    objectType: String? = null
): TrafficTarget =
    TrafficTarget(
        id = id,
        identifiers = TrafficIdentifiers(
            icao24 = icao24,
            callsign = callsign,
            registration = registration,
            sourceId = sourceId
        ),
        position = TrafficPosition(lat = 41.9, lon = 12.5),
        altitude = TrafficAltitude(
            baroM = baroM,
            geoM = geoM,
            mslM = null,
            aglM = aglM,
            sourceM = sourceM,
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
        relative = TrafficRelative(distanceM = distanceM, bearingDeg = bearingDeg),
        provider = provider,
        source = source,
        quality = null,
        sources = listOf(TrafficSource(provider = provider, source = source)),
        provenance = null,
        objectType = objectType
    )
