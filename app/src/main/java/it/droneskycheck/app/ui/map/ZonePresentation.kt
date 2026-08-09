package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.AuthorizationInfo
import it.droneskycheck.app.data.EnrInfo
import it.droneskycheck.app.data.NotamInfo
import it.droneskycheck.app.data.ScheduleInfo
import it.droneskycheck.app.data.SupInfo
import it.droneskycheck.app.data.TemporalBarEntry
import it.droneskycheck.app.data.ValidityInfo
import it.droneskycheck.app.data.ZoneInfo
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal data class ZoneStatusPresentation(
    val label: String,
    val active: Boolean?,
    val emphasis: ZoneStatusEmphasis
)

internal enum class ZoneStatusEmphasis {
    Active,
    Inactive,
    Unknown
}

internal data class TemporalDetailsPresentation(
    val status: String?,
    val activitySchedule: String?,
    val originalSchedule: String?,
    val validity: String?,
    val nextActivation: String?,
    val explanation: String?,
    val weekSchedule: List<TemporalBarEntry> = emptyList(),
    val daySchedule: List<Boolean?> = emptyList()
) {
    val hasContent: Boolean
        get() = listOf(status, activitySchedule, originalSchedule, validity, nextActivation, explanation)
            .any { !it.isNullOrBlank() } || weekSchedule.isNotEmpty() || daySchedule.isNotEmpty()
}

internal data class NotamPresentation(
    val code: String,
    val statusLabel: String,
    val body: String,
    val activitySchedule: String?,
    val validity: String?,
    val operationalStatus: String?,
    val official: it.droneskycheck.app.data.OfficialInfo?
)

internal fun ZoneInfo.primaryStatusPresentation(): ZoneStatusPresentation? {
    val active = validity?.activeNow ?: activeNow
    val label = operationalStatus?.toDscUserText()
        ?: validity?.statusLabelForUi()
        ?: active?.let { if (it) "Attiva ora" else "Non attiva in questo momento" }
        ?: return null

    val emphasis = when {
        active == true || label.contains("attiva", ignoreCase = true) &&
            !label.contains("non attiva", ignoreCase = true) -> ZoneStatusEmphasis.Active
        active == false || label.contains("non attiva", ignoreCase = true) ||
            label.contains("scaduta", ignoreCase = true) -> ZoneStatusEmphasis.Inactive
        else -> ZoneStatusEmphasis.Unknown
    }

    return ZoneStatusPresentation(
        label = label.cleanItalianUiText(),
        active = active,
        emphasis = emphasis
    )
}

internal fun ZoneInfo.temporalDetailsPresentation(): TemporalDetailsPresentation {
    val notam = notams.firstOrNull { it.hasTemporalContent() }
    val sourceValidity = validity ?: notam?.validity ?: enr?.validity ?: sup?.validity
    val schedule = bestSchedule(enr, notam, sup, sourceValidity)
    val interpreted = schedule?.human
        ?: sourceValidity?.interpretedSchedule
        ?: sourceValidity?.schedule?.toItalianScheduleText()
    val raw = schedule?.raw ?: sourceValidity?.schedule

    return TemporalDetailsPresentation(
        status = primaryStatusPresentation()?.label ?: sourceValidity?.statusLabelForUi(),
        activitySchedule = interpreted.cleanItalianUiTextOrNull(),
        originalSchedule = raw.cleanItalianUiTextOrNull()
            ?.takeUnless { it.equals(interpreted.cleanItalianUiTextOrNull(), ignoreCase = true) },
        validity = sourceValidity?.validityRangeLabel(),
        nextActivation = sourceValidity?.nextActivation.formatUtcDateForUi(),
        explanation = sourceValidity?.explanation.cleanItalianUiTextOrNull(),
        weekSchedule = enr?.weekSchedule.orEmpty(),
        daySchedule = enr?.daySchedule.orEmpty()
    )
}

internal fun ZoneInfo.activityScheduleLabel(): String? =
    temporalDetailsPresentation().activitySchedule

internal fun NotamInfo.presentation(): NotamPresentation {
    val severityCode = severity?.trim()?.uppercase()
    val hasBlockingEffect = blockingReason != null || severityCode in setOf("HARD", "SOFT", "BLOCKER", "WARNING")
    val isInfo = severityCode in setOf("INFO", "INFORMATION") || !hasBlockingEffect
    val status = when {
        hasBlockingEffect -> "Verifica necessaria"
        isInfo -> "Informativo"
        validity?.activeNow == true -> "Attivo"
        else -> "Da verificare"
    }

    val activity = activityType.cleanItalianUiTextOrNull()
        ?: summary.cleanItalianUiTextOrNull()
        ?: "attività operative nell'area"
    val body = when {
        hasBlockingEffect ->
            "Drone Sky Check non può determinare automaticamente l'effetto operativo completo di questo NOTAM. Consulta il testo ufficiale prima dell'operazione."
        isInfo ->
            "Questo NOTAM segnala $activity. Non introduce automaticamente un divieto di volo. Verifica il testo ufficiale prima dell'operazione."
        else ->
            listOfNotNull(explanation, operationalMeaning)
                .mapNotNull { it.cleanItalianUiTextOrNull() }
                .distinctBy { it.normalizedForUiDedup() }
                .joinToString(" ")
                .ifBlank { "Consulta il testo ufficiale prima dell'operazione." }
    }

    val schedule = schedule?.human
        ?: validity?.interpretedSchedule
        ?: validity?.schedule.toItalianScheduleText()

    return NotamPresentation(
        code = code.orEmpty(),
        statusLabel = status,
        body = body.cleanItalianUiText(),
        activitySchedule = schedule.cleanItalianUiTextOrNull(),
        validity = validity?.validityRangeLabel(),
        operationalStatus = blockingReason?.toDscUserText(),
        official = official
    )
}

internal fun AuthorizationInfo.manualCheckSummary(): Pair<String, String>? {
    if (!resolutionStatus.equals("MANUAL_CHECK", ignoreCase = true)) return null

    return "VERIFICA NECESSARIA" to
        "Drone Sky Check non può determinare automaticamente l'effetto operativo di questa zona. Consulta le informazioni ufficiali prima dell'operazione."
}

private fun bestSchedule(
    enr: EnrInfo?,
    notam: NotamInfo?,
    sup: SupInfo?,
    validity: ValidityInfo?
): ScheduleInfo? =
    enr?.schedule?.takeIf { it.hasAnyScheduleText() }
        ?: notam?.schedule?.takeIf { it.hasAnyScheduleText() }
        ?: sup?.validity?.let {
            ScheduleInfo(
                raw = it.schedule,
                human = it.interpretedSchedule,
                activeNow = it.activeNow,
                explanation = it.explanation
            )
        }
        ?: validity?.let {
            ScheduleInfo(
                raw = it.schedule,
                human = it.interpretedSchedule,
                activeNow = it.activeNow,
                explanation = it.explanation
            )
        }

private fun ScheduleInfo.hasAnyScheduleText(): Boolean =
    !raw.isNullOrBlank() || !human.isNullOrBlank()

private fun NotamInfo.hasTemporalContent(): Boolean =
    validity != null || schedule?.hasAnyScheduleText() == true

internal fun ValidityInfo.statusLabelForUi(): String? =
    when {
        activeNow == true -> "Attiva ora"
        expired == true -> "Scaduta"
        future == true -> "Futura"
        activeNow == false -> "Non attiva ora"
        else -> null
    }

private fun ValidityInfo.validityRangeLabel(): String? {
    val from = validFrom.formatUtcDateForUi()
    val to = validTo.formatUtcDateForUi()

    return when {
        from != null && to != null -> "$from -> $to"
        from != null -> "Da $from"
        to != null -> "Fino a $to"
        else -> null
    }
}

private fun String?.toItalianScheduleText(): String? {
    if (isNullOrBlank()) return null
    val value = trim().uppercase().replace(Regex("\\s+"), " ")
    val dayText = when {
        "DAILY" in value -> "Ogni giorno"
        Regex("\\bMON-FRI\\b").containsMatchIn(value) -> "Da lunedì a venerdì"
        Regex("\\bMON-THU\\b").containsMatchIn(value) -> "Da lunedì a giovedì"
        Regex("\\bSAT-SUN\\b").containsMatchIn(value) -> "Sabato e domenica"
        Regex("\\bSAT\\b").containsMatchIn(value) -> "Sabato"
        Regex("\\bSUN\\b").containsMatchIn(value) -> "Domenica"
        Regex("\\bFRI\\b").containsMatchIn(value) -> "Venerdì"
        Regex("\\bTHU\\b").containsMatchIn(value) -> "Giovedì"
        Regex("\\bWED\\b").containsMatchIn(value) -> "Mercoledì"
        Regex("\\bTUE\\b").containsMatchIn(value) -> "Martedì"
        Regex("\\bMON\\b").containsMatchIn(value) -> "Lunedì"
        else -> null
    }
    val time = Regex("""\b(\d{2})(\d{2})-(\d{2})(\d{2})\b""").find(value)
    val timeText = time?.let {
        "dalle ${it.groupValues[1]}:${it.groupValues[2]} alle ${it.groupValues[3]}:${it.groupValues[4]} UTC"
    } ?: when {
        "H24" in value -> "24 ore su 24 UTC"
        Regex("""\bSR(?:[+-]\d+)?-SS(?:[+-]\d+)?\b""").containsMatchIn(value) ->
            "da alba a tramonto, secondo gli offset pubblicati"
        else -> null
    }

    return listOfNotNull(dayText, timeText)
        .joinToString(" ")
        .ifBlank { value }
}

private fun String?.formatUtcDateForUi(): String? {
    if (isNullOrBlank()) return null
    val value = trim()

    runCatching {
        return UiUtcFormatter.format(Instant.parse(value))
    }

    val compact = Regex("""^(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})$""").matchEntire(value)
        ?: return value.cleanItalianUiText()
    val date = LocalDateTime.of(
        2000 + compact.groupValues[1].toInt(),
        compact.groupValues[2].toInt(),
        compact.groupValues[3].toInt(),
        compact.groupValues[4].toInt(),
        compact.groupValues[5].toInt()
    )
    return UiUtcFormatter.format(date.atOffset(ZoneOffset.UTC))
}

internal fun String?.cleanItalianUiTextOrNull(): String? =
    this?.cleanItalianUiText()
        ?.takeIf { it.isNotBlank() && !it.equals("NIL", ignoreCase = true) }

internal fun String.cleanItalianUiText(): String =
    replace(" e informativo", " è informativo")
        .replace(" e' informativo", " è informativo")
        .replace(" e necessario", " è necessario")
        .replace(" e attiva", " è attiva")
        .replace(" e operativo", " è operativo")
        .replace(" attivita", " attività")
        .replace(" Localita", " Località")
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun String.toDscUserText(): String =
    when (trim().uppercase()) {
        "REQ_AUTHORIZATION", "AUTHORIZATION_REQUIRED" -> "Autorizzazione richiesta"
        "ACTIVE", "ENR_ACTIVE", "NOTAM_ACTIVE", "SUP_ACTIVE" -> "Attiva ora"
        "ACTIVE_LIMITED" -> "Attiva con limite"
        "ENR_INACTIVE_NOW", "NOTAM_INACTIVE_NOW", "SUP_INACTIVE_NOW", "INACTIVE_NOW", "SUP_INACTIVE" ->
            "Non attiva in questo momento"
        "ENR_TEMPORAL_UNKNOWN" -> "Orari non valutabili automaticamente"
        "CHECK_NOTAM" -> "Attivazione da verificare tramite NOTAM"
        "ACTIVE_ENR" -> "ENR attiva"
        "ACTIVE_HARD_NOTAM" -> "NOTAM attivo bloccante"
        "ACTIVE_SOFT_NOTAM" -> "NOTAM attivo da verificare"
        "ACTIVE_SUP_AUTH_REQUIRED" -> "SUP attivo con autorizzazione richiesta"
        "HARD", "BLOCKER" -> "Bloccante"
        "SOFT", "WARNING" -> "Da verificare"
        "INFO", "INFORMATION" -> "Informativa"
        else -> this.cleanItalianUiText()
    }

private fun String.normalizedForUiDedup(): String =
    lowercase()
        .replace(Regex("[^a-z0-9àèéìòù]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private val UiUtcFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC)
