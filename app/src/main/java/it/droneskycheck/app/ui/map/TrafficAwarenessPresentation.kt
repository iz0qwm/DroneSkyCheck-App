package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.traffic.TrafficAwarenessState
import it.droneskycheck.app.data.traffic.TrafficAssessment
import it.droneskycheck.app.data.traffic.TrafficCalculationConfidence
import it.droneskycheck.app.data.traffic.TrafficRelevance
import it.droneskycheck.app.data.traffic.TrafficRelevanceReason
import it.droneskycheck.app.data.traffic.TrafficTarget
import it.droneskycheck.app.data.traffic.TrafficTargetKind
import it.droneskycheck.app.data.traffic.selectPrimaryTrafficAttentionTargetId
import it.droneskycheck.app.data.traffic.trafficTargetKind
import it.droneskycheck.app.map.displayName
import java.util.Locale
import kotlin.math.roundToInt

data class TrafficAwarenessInfoRow(
    val label: String,
    val value: String
)

data class TrafficAttentionPresentation(
    val targetId: String,
    val title: String,
    val detail: String,
    val attentionCount: Int
)

data class TrafficTargetSheetPresentation(
    val title: String,
    val targetKind: TrafficTargetKind,
    val sourceLabel: String?,
    val secondaryIdentity: String?,
    val relevanceLabel: String,
    val sections: List<TrafficTargetSheetSection>
)

data class TrafficTargetSheetSection(
    val title: String,
    val rows: List<TrafficAwarenessInfoRow>,
    val note: String? = null
)

fun trafficAwarenessTargetCount(state: TrafficAwarenessState): Int =
    if (state.enabled) state.response?.traffic?.targets?.size ?: 0 else 0

fun trafficAwarenessButtonContentDescription(state: TrafficAwarenessState): String =
    when {
        !state.enabled -> "Traffic Awareness disattivata"
        state.loading && state.response == null -> "Traffic Awareness attiva, aggiornamento traffico in corso"
        else -> {
            val count = trafficAwarenessTargetCount(state)
            "Traffic Awareness attiva, $count ${if (count == 1) "traffico rilevato" else "traffici rilevati"}"
        }
    }

fun trafficAwarenessUnavailableMessage(state: TrafficAwarenessState): String? =
    "Traffico temporaneamente non disponibile"
        .takeIf { state.enabled && state.error != null && state.response == null && !state.loading }

fun TrafficTarget.trafficSheetTitle(): String = displayName()

fun trafficAttentionPresentation(
    targets: List<TrafficTarget>,
    assessments: Map<String, TrafficAssessment>
): TrafficAttentionPresentation? {
    val attentionTargets = targets
        .mapNotNull { target ->
            val assessment = assessments[target.id] ?: return@mapNotNull null
            if (assessment.relevance == TrafficRelevance.ATTENTION) target to assessment else null
        }
    val primaryTargetId = selectPrimaryTrafficAttentionTargetId(
        targetIds = attentionTargets.map { it.first.id },
        assessments = assessments,
        currentDistanceByTargetId = attentionTargets.associate { it.first.id to it.first.relative.distanceM }
    ) ?: return null
    val selected = attentionTargets.first { it.first.id == primaryTargetId }

    val target = selected.first
    val assessment = selected.second
    val distance = assessment.currentDistanceM
        ?: target.relative.distanceM
    val time = assessment.timeToCpaSec
    val detailParts = buildList {
        if (attentionTargets.size > 1) add("Principale: ${target.displayName()}")
            else add(target.displayName())
        distance?.let { add(formatTrafficDistance(it)) }
        time?.let { add("CPA tra ${formatTrafficDuration(it)}") }
    }

    return TrafficAttentionPresentation(
        targetId = target.id,
        title = if (attentionTargets.size > 1) {
            "${attentionTargets.size} traffici in avvicinamento"
        } else {
            "Traffico in avvicinamento"
        },
        detail = detailParts.joinToString(" - "),
        attentionCount = attentionTargets.size
    )
}

fun TrafficTarget.trafficSheetPresentation(assessment: TrafficAssessment? = null): TrafficTargetSheetPresentation {
    val sourceLabel = trafficSourceText()
    val targetKind = trafficTargetKind()
    val relationRows = buildList {
        (assessment?.currentDistanceM ?: relative.distanceM)?.let {
            add(TrafficAwarenessInfoRow("Distanza", formatTrafficDistance(it)))
        }
        (assessment?.relativeBearingDeg ?: relative.bearingDeg)?.let {
            add(TrafficAwarenessInfoRow("Direzione", formatTrafficDegrees(it)))
        }
        assessment?.converging?.let {
            add(TrafficAwarenessInfoRow("Traiettoria", if (it) "In avvicinamento" else "In allontanamento"))
        }
        assessment?.cpaDistanceM?.let {
            add(TrafficAwarenessInfoRow("Passaggio minimo stimato", formatTrafficDistance(it)))
        }
        assessment?.timeToCpaSec?.let {
            add(TrafficAwarenessInfoRow("Tempo stimato", formatTrafficDuration(it)))
        }
    }
    val relationNote = when {
        assessment == null -> null
        assessment.reasons.contains(TrafficRelevanceReason.STALE_MOTION_DATA) -> "Dato non recente"
        assessment.cpaDistanceM == null && assessment.timeToCpaSec == null -> "Previsione traiettoria non disponibile"
        else -> null
    }

    val movementRows = buildList {
        motion.groundSpeedMps?.let { add(TrafficAwarenessInfoRow("Velocita", formatTrafficSpeedKmh(it))) }
        motion.trackDeg?.let { add(TrafficAwarenessInfoRow("Rotta", formatTrafficDegrees(it))) }
            ?: motion.headingDeg?.let { add(TrafficAwarenessInfoRow("Heading", formatTrafficDegrees(it))) }
    }

    val altitudeRows = buildList {
        altitude.geoM?.let { add(TrafficAwarenessInfoRow("Geometrica", formatTrafficMeters(it))) }
        altitude.baroM?.let { add(TrafficAwarenessInfoRow("Barometrica", formatTrafficMeters(it))) }
        altitude.sourceM?.let { add(TrafficAwarenessInfoRow("Quota ricevuta", formatTrafficMeters(it))) }
        altitude.aglM?.let { add(TrafficAwarenessInfoRow("AGL", formatTrafficMeters(it))) }
    }

    val dataRows = buildList {
        add(TrafficAwarenessInfoRow("Tipo", targetKind.presentationLabel()))
        sourceLabel?.let { add(TrafficAwarenessInfoRow("Sorgente", it)) }
        identifiers.icao24?.let { add(TrafficAwarenessInfoRow("ICAO", it.uppercase(Locale.US))) }
        time.ageSec?.let { add(TrafficAwarenessInfoRow("Aggiornamento", "Aggiornato ${formatTrafficDuration(it)} fa")) }
    }

    return TrafficTargetSheetPresentation(
        title = displayName(),
        targetKind = targetKind,
        sourceLabel = sourceLabel,
        secondaryIdentity = when (targetKind) {
            TrafficTargetKind.DRONE -> "Drone rilevato"
            TrafficTargetKind.AIRCRAFT -> identifiers.icao24?.let { "ICAO ${it.uppercase(Locale.US)}" }
        },
        relevanceLabel = (assessment?.relevance ?: TrafficRelevance.INFORMATION).presentationLabel(),
        sections = listOf(
            TrafficTargetSheetSection("Rispetto all'area operativa", relationRows, relationNote),
            TrafficTargetSheetSection("Movimento", movementRows),
            TrafficTargetSheetSection("Quota", altitudeRows),
            TrafficTargetSheetSection("Dati traffico", dataRows)
        ).filter { it.rows.isNotEmpty() || it.note != null }
    )
}

fun TrafficTarget.trafficSheetRows(assessment: TrafficAssessment? = null): List<TrafficAwarenessInfoRow> =
    buildList {
        assessment?.trafficAssessmentRows()?.let(::addAll)
        relative.distanceM?.let { add(TrafficAwarenessInfoRow("Distanza", formatTrafficDistance(it))) }
        relative.bearingDeg?.let { add(TrafficAwarenessInfoRow("Direzione", formatTrafficDegrees(it))) }
        altitude.aglM?.let { add(TrafficAwarenessInfoRow("Quota AGL", formatTrafficMeters(it))) }
        altitude.geoM?.let { add(TrafficAwarenessInfoRow("Quota geometrica", formatTrafficMeters(it))) }
        altitude.baroM?.let { add(TrafficAwarenessInfoRow("Quota barometrica", formatTrafficMeters(it))) }
        altitude.sourceM?.let { add(TrafficAwarenessInfoRow("Quota ricevuta", formatTrafficMeters(it))) }
        motion.groundSpeedMps?.let { add(TrafficAwarenessInfoRow("Velocita", formatTrafficSpeedKmh(it))) }
        motion.trackDeg?.let { add(TrafficAwarenessInfoRow("Rotta", formatTrafficDegrees(it))) }
            ?: motion.headingDeg?.let { add(TrafficAwarenessInfoRow("Heading", formatTrafficDegrees(it))) }
        trafficSourceText()?.let { add(TrafficAwarenessInfoRow("Sorgente", it)) }
    }

fun TrafficAssessment.trafficAssessmentRows(): List<TrafficAwarenessInfoRow> =
    buildList {
        add(TrafficAwarenessInfoRow("Rilevanza operativa", relevance.presentationLabel()))
        converging?.let {
            add(
                TrafficAwarenessInfoRow(
                    "Traiettoria",
                    if (it) "In avvicinamento" else "In allontanamento"
                )
            )
        }
        cpaDistanceM?.let {
            add(TrafficAwarenessInfoRow("Passaggio minimo stimato", formatTrafficDistance(it)))
        }
        timeToCpaSec?.let {
            add(TrafficAwarenessInfoRow("Tempo stimato", formatTrafficDuration(it)))
        }
        add(TrafficAwarenessInfoRow("Calcolo traiettoria", calculationConfidence.presentationLabel()))
    }

fun TrafficTarget.trafficSourceText(): String? {
    val direct = sourcePair(provider, source)
    if (direct != null) return direct

    val sourceTexts = sources
        .mapNotNull { sourcePair(it.provider, it.source) }
        .distinct()
    return when (sourceTexts.size) {
        0 -> null
        1 -> sourceTexts.first()
        else -> sourceTexts.joinToString(" + ")
    }
}

fun formatTrafficDistance(distanceM: Double): String =
    if (distanceM < 1_000.0) {
        "${distanceM.roundToInt()} m"
    } else {
        String.format(Locale.ITALY, "%.1f km", distanceM / 1_000.0)
    }

fun formatTrafficSpeedKmh(speedMps: Double): String =
    "${(speedMps * 3.6).roundToInt()} km/h"

fun formatTrafficDegrees(degrees: Double): String =
    "${degrees.roundToInt()}°"

private fun formatTrafficMeters(meters: Double): String =
    "${meters.roundToInt()} m"

fun formatTrafficDuration(seconds: Double): String {
    val totalSeconds = seconds.roundToInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes} min ${String.format(Locale.ITALY, "%02d", remainingSeconds)} s"
    } else {
        "$remainingSeconds s"
    }
}

private fun TrafficRelevance.presentationLabel(): String =
    when (this) {
        TrafficRelevance.INFORMATION -> "Informativo"
        TrafficRelevance.MONITOR -> "Da monitorare"
        TrafficRelevance.ATTENTION -> "Attenzione"
    }

private fun TrafficTargetKind.presentationLabel(): String =
    when (this) {
        TrafficTargetKind.AIRCRAFT -> "Aeromobile"
        TrafficTargetKind.DRONE -> "Drone AirSense"
    }

private fun TrafficCalculationConfidence.presentationLabel(): String =
    when (this) {
        TrafficCalculationConfidence.HIGH -> "Stima completa"
        TrafficCalculationConfidence.PARTIAL -> "Stima parziale"
        TrafficCalculationConfidence.INSUFFICIENT -> "Dati insufficienti"
    }

private fun sourcePair(provider: String?, source: String?): String? {
    val providerText = provider?.takeIf { it.isNotBlank() }
    val sourceText = source?.takeIf { it.isNotBlank() }
    return when {
        providerText != null && sourceText != null && !providerText.equals(sourceText, ignoreCase = true) ->
            "$providerText · $sourceText"
        providerText != null -> providerText
        sourceText != null -> sourceText
        else -> null
    }
}
