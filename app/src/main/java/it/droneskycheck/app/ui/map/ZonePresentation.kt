package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.AuthorizationInfo
import it.droneskycheck.app.data.EnrInfo
import it.droneskycheck.app.data.NotamCalendarDay
import it.droneskycheck.app.data.NotamInfo
import it.droneskycheck.app.data.ScheduleInfo
import it.droneskycheck.app.data.SupInfo
import it.droneskycheck.app.data.TemporalBarEntry
import it.droneskycheck.app.data.ValidityInfo
import it.droneskycheck.app.data.ZoneInfo
import java.time.Instant
import java.time.LocalDate
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
    val reasonText: String?,
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
    val validityCandidates = listOf(validity, notam?.validity, enr?.validity, sup?.validity)
    val sourceValidity = validityCandidates.firstOrNull { it?.hasTemporalContentForUi() == true }
        ?: validityCandidates.firstNotNullOfOrNull { it }
    val schedule = bestSchedule(enr, notam, sup, sourceValidity)
    val interpreted = schedule?.readableSchedule()
        ?: sourceValidity?.interpretedSchedule
        ?: sourceValidity?.schedule.readableScheduleText()
    val raw = schedule?.raw ?: sourceValidity?.schedule

    return TemporalDetailsPresentation(
        status = primaryStatusPresentation()?.label ?: sourceValidity?.statusLabelForUi(),
        activitySchedule = interpreted.cleanItalianUiTextOrNull(),
        originalSchedule = raw.cleanItalianUiTextOrNull()
            ?.takeUnless { it.equals(interpreted.cleanItalianUiTextOrNull(), ignoreCase = true) },
        validity = sourceValidity?.validityRangeLabel(),
        nextActivation = sourceValidity?.nextActivation.formatUtcDateForUi(),
        explanation = sourceValidity?.explanation.cleanItalianUiTextOrNull(),
        weekSchedule = enr?.weekSchedule?.takeIf { it.isNotEmpty() } ?: notam?.weekSchedule.orEmpty(),
        daySchedule = enr?.daySchedule?.takeIf { it.isNotEmpty() } ?: notam?.daySchedule.orEmpty()
    )
}

internal fun ZoneInfo.activityScheduleLabel(): String? =
    temporalDetailsPresentation().activitySchedule

internal fun EnrInfo.activityScheduleForUi(): String? =
    schedule?.readableSchedule()
        ?: validity?.schedule.readableScheduleText()

internal fun NotamInfo.presentation(): NotamPresentation {
    val severityCode = severity?.trim()?.uppercase()
    val hasBlockingEffect = blockingReason != null || severityCode in setOf("HARD", "SOFT", "BLOCKER", "WARNING")
    val isInfo = severityCode in setOf("INFO", "INFORMATION") || !hasBlockingEffect
    val status = when {
        hasBlockingEffect -> "Restrizione temporanea"
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

    val displayBody = if (hasBlockingEffect) {
        "Restrizione temporanea pubblicata tramite NOTAM. Consulta motivo, validita e testo ufficiale prima dell'operazione."
    } else {
        body
    }

    val schedule = schedule?.readableSchedule()
        ?: validity?.interpretedSchedule
        ?: validity?.schedule.readableScheduleText()

    return NotamPresentation(
        code = code.orEmpty(),
        statusLabel = status,
        body = displayBody.cleanItalianUiText(),
        reasonText = notamReasonForUi(),
        activitySchedule = schedule.cleanItalianUiTextOrNull(),
        validity = validity?.validityRangeLabel(),
        operationalStatus = blockingReason?.toDscUserText(),
        official = official
    )
}

private fun NotamInfo.notamReasonForUi(): String? {
    val officialReason = official.notamReasonText()
    return officialReason.toSimpleNotamReasonText()
        ?: activityType.toSimpleNotamReasonText()
        ?: activityType.cleanItalianUiTextOrNull()
        ?: officialReason.cleanItalianUiTextOrNull()
}

internal fun AuthorizationInfo.notamTemporaryRestrictionNotice(): String? {
    val isNotamManualCheck = resolutionStatus.equals("MANUAL_CHECK", ignoreCase = true) &&
        reasonCodes.any { it.equals("NOTAM_REQUIRES_MANUAL_CHECK", ignoreCase = true) }
    if (!isNotamManualCheck) return null

    return "Il sistema automatico di richiesta autorizzazioni di Drone Sky Check non e applicabile alle restrizioni temporanee pubblicate tramite NOTAM."
}

internal fun AuthorizationInfo.manualCheckSummary(): Pair<String, String>? {
    if (!resolutionStatus.equals("MANUAL_CHECK", ignoreCase = true)) return null
    if (notamTemporaryRestrictionNotice() != null) return null

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
    !raw.isNullOrBlank() || !human.isNullOrBlank() || calendarDays.isNotEmpty()

private fun ScheduleInfo.readableSchedule(): String? =
    calendarDays.toItalianNotamCalendarSchedule()
        ?: raw.toItalianScheduleText()
        ?: human.cleanItalianUiTextOrNull()
        ?: raw.cleanItalianUiTextOrNull()

private fun List<NotamCalendarDay>.toItalianNotamCalendarSchedule(): String? =
    takeIf { it.isNotEmpty() }
        ?.mapNotNull { day ->
            val intervals = day.intervals.mapNotNull { interval ->
                val start = interval.start?.takeIf { it.matches(UtcHourMinuteRegex) } ?: return@mapNotNull null
                val end = interval.end?.takeIf { it.matches(UtcHourMinuteRegex) } ?: return@mapNotNull null
                "dalle $start alle $end UTC"
            }
            if (intervals.isEmpty()) return@mapNotNull null
            "${day.notamCalendarDayLabel()}: ${intervals.joinToItalianScheduleList()}"
        }
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString("; ")

private fun NotamCalendarDay.notamCalendarDayLabel(): String {
    val parsedDate = date
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return "giorno ${day.toString().padStart(2, '0')}"
    val month = MonthLabelsByNumber[parsedDate.monthValue] ?: return date
    return "${parsedDate.dayOfMonth} $month ${parsedDate.year}"
}

private fun NotamInfo.hasTemporalContent(): Boolean =
    validity != null ||
        schedule?.hasAnyScheduleText() == true ||
        weekSchedule.isNotEmpty() ||
        daySchedule.isNotEmpty()

private fun ValidityInfo.hasTemporalContentForUi(): Boolean =
    activeNow != null ||
        !validFrom.isNullOrBlank() ||
        !validTo.isNullOrBlank() ||
        !schedule.isNullOrBlank() ||
        !interpretedSchedule.isNullOrBlank() ||
        !nextActivation.isNullOrBlank() ||
        !explanation.isNullOrBlank() ||
        future != null ||
        expired != null

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
    val value = trim()
        .uppercase()
        .replace(ParenthesizedTextRegex, " ")
        .replace(Regex("\\s+"), " ")
        .normalizeBilingualScheduleMarkers()
        .withoutDuplicatedBilingualSchedule()
        .withInferredScheduleClauseSeparators()
        .trim()
    val fromToText = value.toItalianFromToSchedule()
    val notamOnlyText = value.toItalianNotamOnlySchedule()
    val clauseText = value.toItalianScheduleClauses()
    val holidayText = value.toItalianHolidayText()
    val inactiveText = value.toItalianInactivePeriodText()
    val dayText = value.toItalianScheduleDayText()
    val timeText = value.toItalianScheduleTimeText()
    val defaultText = listOfNotNull(dayText, timeText)
        .joinToString(" ")
        .takeIf { it.isNotBlank() }
    val mainText = fromToText ?: clauseText ?: defaultText
    val standaloneHolidayText = holidayText.takeUnless {
        it == "festivi inclusi" && mainText?.contains("festivi", ignoreCase = true) == true
    }

    if (notamOnlyText != null) {
        return listOfNotNull(mainText, standaloneHolidayText, notamOnlyText, inactiveText).joinToString("; ")
    }
    if (fromToText != null || clauseText != null || inactiveText != null) {
        return listOfNotNull(mainText, standaloneHolidayText, inactiveText).joinToString("; ")
    }

    val separator = if (";" in value) "; " else " "
    return listOfNotNull(defaultText, standaloneHolidayText)
        .joinToString(separator)
        .takeIf { it.isNotBlank() }
}

private fun String.withInferredScheduleClauseSeparators(): String =
    replace(AeronauticalScheduleClauseBoundaryRegex, "$1; ")

private fun String?.readableScheduleText(): String? =
    toItalianScheduleText() ?: cleanItalianUiTextOrNull()

private fun String.toItalianFromToSchedule(): String? {
    val match = FromToDayTimeRegex.find(this) ?: return null
    val startDay = DayLabels[match.groupValues[1]]?.lowercase() ?: return null
    val startTime = formatUtcHourMinute(match.groupValues[2], match.groupValues[3]) ?: return null
    val endDay = DayLabels[match.groupValues[4]]?.lowercase() ?: return null
    val endTime = formatUtcHourMinute(match.groupValues[5], match.groupValues[6]) ?: return null

    return "Da $startDay $startTime UTC a $endDay $endTime UTC"
}

private fun String.toItalianNotamOnlySchedule(): String? {
    val soloNoticeIndex = indexOf("ATTIVA SOLO CON PREAVVISO")
    val announcedNoticeIndex = listOf(
        indexOf("ANNOUNCED BY NOTAM"),
        indexOf("ANNONCED BY NOTAM")
    ).filter { it >= 0 }.minOrNull() ?: -1
    val noticeIndex = listOf(soloNoticeIndex, announcedNoticeIndex)
        .filter { it >= 0 }
        .minOrNull()
        ?: return null
    if ("NOTAM" !in substring(noticeIndex)) return null

    val prefix = substring(0, noticeIndex)
        .substringAfterLast(";")
        .substringAfterLast(".")
        .removePrefix("INOLTRE")
        .trim()

    val dayClauses = if (TimeRangeRegex.containsMatchIn(prefix)) {
        emptyList()
    } else {
        DayExpressionRegex.find(prefix)
            ?.value
            ?.let { formatDayExpression(it) }
            ?.let { listOf(it) }
            .orEmpty()
    }
    val timeClauses = TimeRangeRegex.findAll(prefix).mapNotNull { match ->
        val day = prefix.substring(0, match.range.first)
            .let { DayCodeRegex.findAll(it).lastOrNull()?.value }
            ?.let { DayLabels[it] }
            ?: return@mapNotNull null
        val start = formatUtcHourMinute(match.groupValues[1], match.groupValues[2]) ?: return@mapNotNull null
        val end = formatUtcHourMinute(match.groupValues[3], match.groupValues[4]) ?: return@mapNotNull null
        "$day dalle $start alle $end UTC"
    }.toList()
    val holidayClause = if (Regex("""\bHOL\b""").containsMatchIn(prefix)) "festivi" else null
    val subject = (timeClauses + dayClauses + listOfNotNull(holidayClause)).joinToItalianList() ?: return null

    val noticeText = if (soloNoticeIndex >= 0 && soloNoticeIndex == noticeIndex) {
        "attiva solo con preavviso NOTAM"
    } else {
        "attiva con preavviso NOTAM"
    }

    return "$subject: $noticeText"
}

private fun String.toItalianScheduleClauses(): String? {
    val matches = DayClauseRegex.findAll(this).toList()
    if (matches.isEmpty()) return toItalianDelimitedScheduleClauses()

    val clauses = matches.mapIndexedNotNull { index, match ->
        val dayText = formatDayExpression(match.groupValues[1]) ?: return@mapIndexedNotNull null
        val nextStart = matches.getOrNull(index + 1)?.range?.first ?: length
        val body = substring(match.range.last + 1, nextStart)
        val timeText = body.toItalianScheduleTimeText() ?: return@mapIndexedNotNull null
        "$dayText $timeText"
    }

    return clauses.takeIf { it.isNotEmpty() }?.joinToString("; ")
}

private fun String.toItalianDelimitedScheduleClauses(): String? {
    val clauses = split(DelimitedScheduleClauseSeparatorRegex).mapNotNull { segment ->
        val dayMatch = DayExpressionRegex.find(segment) ?: return@mapNotNull null
        val dayText = formatDayExpression(dayMatch.value) ?: return@mapNotNull null
        val body = segment.substring(dayMatch.range.last + 1)
        val timeText = body.toItalianScheduleTimeText() ?: return@mapNotNull null
        "$dayText $timeText"
    }

    return clauses.takeIf { it.size > 1 }?.joinToString("; ")
}

private fun String.toItalianScheduleDayText(): String? {
    if (Regex("""\b(DAILY|DLY|EVERY DAY)\b""").containsMatchIn(this)) return "Ogni giorno"
    val holidayText = toItalianHolidayText()
    DayExpressionRegex.find(this)?.let { match ->
        val selectors = ScheduleSelectorCodeRegex.findAll(match.value).map { it.value }.toList()
        val isExcludedHolidayOnly = holidayText == "festivi esclusi" && selectors.all { it == "HOL" }
        if (!isExcludedHolidayOnly) return formatDayExpression(match.value)
    }

    return if (Regex("""\bHOL\b""").containsMatchIn(this) && holidayText != "festivi esclusi") {
        "Festivi"
    } else {
        null
    }
}

private fun formatDayExpression(value: String): String? {
    val days = ScheduleSelectorCodeRegex.findAll(value)
        .map { it.value }
        .distinct()
        .toList()

    if (days.isEmpty()) return null
    if (StrictDayRangeRegex.matches(value.trim()) && days.size == 2) {
        return formatDayRange(days[0], days[1])
    }
    if (days == WeekdayCodes) return "Da lunedì a venerdì"
    if (days == WeekendCodes) return "Sabato e domenica"
    if (days.size == 7) return "Ogni giorno"

    return days.mapNotNull { code ->
        if (code == "HOL") "festivi" else DayLabels[code]
    }.joinToItalianList()
}

private fun String.toItalianScheduleTimeText(): String? {
    if (Regex("""\bH24\b""").containsMatchIn(this)) return "24 ore su 24 UTC"
    if (Regex("""\bHJ\b""").containsMatchIn(this)) return "da alba a tramonto"
    if (Regex("""\bHN\b""").containsMatchIn(this)) return "da tramonto ad alba"

    val ranges = AeronauticalTimeRangeRegex
        .findAll(this)
        .mapNotNull { match ->
            formatAeronauticalTimeRange(match.groupValues[1], match.groupValues[2])
        }
        .toList()

    return ranges.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

private fun formatAeronauticalTimeRange(startToken: String, endToken: String): String? {
    val start = formatAeronauticalRangeToken(startToken, isStart = true, otherToken = endToken) ?: return null
    val end = formatAeronauticalRangeToken(endToken, isStart = false, otherToken = startToken) ?: return null
    return "$start $end".trim()
}

private fun formatAeronauticalRangeToken(token: String, isStart: Boolean, otherToken: String): String? {
    val time = token.toUtcHourMinuteToken()
    if (time != null) {
        val otherIsTime = otherToken.toUtcHourMinuteToken() != null
        val suffix = if (!isStart || !otherIsTime) " UTC" else ""
        return "${if (isStart) "dalle" else "alle"} $time$suffix"
    }

    val value = token.uppercase()
    return when {
        value == "SR" || value == "SUNRISE" -> if (isStart) "dall'alba" else "all'alba"
        value == "SS" || value == "SUNSET" -> if (isStart) "dal tramonto" else "al tramonto"
        value.startsWith("SR") || value.startsWith("SUNRISE") ->
            if (isStart) "dall'alba secondo offset pubblicato" else "all'alba secondo offset pubblicato"
        value.startsWith("SS") || value.startsWith("SUNSET") ->
            if (isStart) "dal tramonto secondo offset pubblicato" else "al tramonto secondo offset pubblicato"
        else -> null
    }
}

private fun String.toUtcHourMinuteToken(): String? {
    val match = Regex("""^(\d{2})(\d{2})$""").matchEntire(this) ?: return null
    return formatUtcHourMinute(match.groupValues[1], match.groupValues[2])
}

private fun String.toItalianHolidayText(): String? {
    val hasHoliday = Regex("""\bHOL\b""").containsMatchIn(this)
    if (!hasHoliday) return null

    val excludesHoliday = Regex("""\b(EXC|EXCEPT)\s+HOL\b""").containsMatchIn(this) ||
        Regex("""\bHOL\b(?:(?![.;:]).)*\b(ESCLUSI|ESCLUSO|EXCLUDED|EXCEPTED|EXC|EXCEPT)\b""")
            .containsMatchIn(this)

    return if (excludesHoliday) "festivi esclusi" else "festivi inclusi"
}

private fun String.toItalianInactivePeriodText(): String? {
    val match = InactivePeriodRegex.find(this) ?: return null
    val fromMonth = MonthLabels[match.groupValues[2]] ?: return null
    val toMonth = MonthLabels[match.groupValues[4]] ?: return null

    return "non attiva dal ${match.groupValues[1].toInt()} $fromMonth al ${match.groupValues[3].toInt()} $toMonth"
}

private fun String.withoutDuplicatedBilingualSchedule(): String {
    val parts = split("/")
    if (parts.size < 2) return this

    val first = parts.first().trim()
    val second = parts.drop(1).joinToString("/").trim()
    val firstHasSchedule = DayCodeRegex.containsMatchIn(first) && TimeRangeRegex.containsMatchIn(first)
    val secondHasSchedule = DayCodeRegex.containsMatchIn(second) && TimeRangeRegex.containsMatchIn(second)

    return if (firstHasSchedule && secondHasSchedule) first else this
}

private fun String.normalizeBilingualScheduleMarkers(): String =
    replace(Regex("""\bATTIVA\s*/\s*ACTIVE\b"""), "ATTIVA")
        .replace(Regex("""\bNON\s+ATTIVA\s*:?\s*/\s*NOT\s+ACTIVE\s*:"""), "NON ATTIVA:")
        .replace(Regex("""\bE\s*/\s*AND\b"""), "E")
        .replace(Regex("""\bDA\s*/\s*FROM\b"""), "DA")
        .replace(Regex("""\bA\s*/\s*TO\b"""), "A")
        .replace(Regex("""\bESCLUSI\s*/\s*EXCLUDED\b"""), "ESCLUSI")
        .replace(Regex("""\bESCLUSO\s*/\s*EXCLUDED\b"""), "ESCLUSO")
        .replace(Regex("""\bINOLTRE\s*/\s*MOREOVER\b"""), "INOLTRE")
        .replace(Regex("""\s*/\s*ACTIVE BY NOTAM\b"""), " NOTAM")

private fun formatDayRange(startCode: String, endCode: String): String? {
    val startIndex = DayOrder.indexOf(startCode)
    val endIndex = DayOrder.indexOf(endCode)
    if (startIndex == -1 || endIndex == -1) return null

    val days = if (startIndex <= endIndex) {
        DayOrder.subList(startIndex, endIndex + 1)
    } else {
        DayOrder.subList(startIndex, DayOrder.size) + DayOrder.subList(0, endIndex + 1)
    }

    return when (days) {
        WeekdayCodes -> "Da lunedì a venerdì"
        WeekendCodes -> "Sabato e domenica"
        else -> "Da ${DayLabels.getValue(startCode).lowercase()} a ${DayLabels.getValue(endCode).lowercase()}"
    }
}

private fun formatUtcHourMinute(hourText: String, minuteText: String): String? {
    val hour = hourText.toIntOrNull() ?: return null
    val minute = minuteText.toIntOrNull() ?: return null
    if (minute !in 0..59) return null
    if (hour !in 0..24 || hour == 24 && minute != 0) return null

    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

private fun List<String>.joinToItalianList(): String? =
    when (size) {
        0 -> null
        1 -> first()
        2 -> "${this[0]} e ${this[1].lowercase()}"
        else -> first() + drop(1).dropLast(1).joinToString(
            prefix = ", ",
            separator = ", ",
            transform = { it.lowercase() }
        ) + " e ${last().lowercase()}"
    }

private fun List<String>.joinToItalianScheduleList(): String? =
    when (size) {
        0 -> null
        1 -> first()
        2 -> "${this[0]} e ${this[1]}"
        else -> first() + drop(1).dropLast(1).joinToString(
            prefix = ", ",
            separator = ", "
        ) + " e ${last()}"
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

private fun it.droneskycheck.app.data.OfficialInfo?.notamReasonText(): String? {
    if (this == null) return null
    fields.firstOrNull { it.key.equals("E", ignoreCase = true) }?.value?.let { return it }
    sourceText?.let { text ->
        Regex("""(?:^|\s)E\)\s*([\s\S]*?)(?=(?:\s(?:F|G)\)\s)|$)""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }
    sourceText
        ?.takeUnless { Regex("""(?:^|\s)(?:Q|A|B|C|D|E|F|G)\)""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    return null
}

private fun String?.toSimpleNotamReasonText(): String? {
    val normalized = this
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("NIL", ignoreCase = true) }
        ?.uppercase()
        ?.replace(Regex("\\s+"), " ")
        ?: return null

    return when {
        Regex("""\bGLIDERS?\s+COMPETITION\b|\bGLIDERS?\b.*\bCOMPETITION\b""").containsMatchIn(normalized) ->
            "Competizione di alianti nella zona delimitata dal NOTAM"
        Regex("""(?:\bMIL\b|\bMILITARY\b).*?(?:\bUNMANNED\b|\bUAS\b|\bUAV\b|\bRPAS\b|\bDRONE\b)|(?:\bUNMANNED\b|\bUAS\b|\bUAV\b|\bRPAS\b|\bDRONE\b).*?(?:\bMIL\b|\bMILITARY\b)""")
            .containsMatchIn(normalized) ->
            "Utilizzo di aeromobili militari a pilotaggio remoto"
        Regex("""ASCENT\s+OF\s+FREE\s+BALLOONS?|FREE\s+BALLOONS?|PALLONI\s+AEROSTATICI""").containsMatchIn(normalized) ->
            "Palloni aerostatici nell'area"
        Regex("""CAPTIVE\s+BALLOONS?|PALLONE\s+VINCOLATO""").containsMatchIn(normalized) ->
            "Pallone vincolato nell'area"
        Regex("""MILITARY\s+FIRING|FIRING\s+AREA|FIRING|LIVE\s*FIRE|GUNNERY|SHOOTING|TIRO""").containsMatchIn(normalized) ->
            "Esercitazioni militari a fuoco"
        Regex("""PARACHUTE|PARA|PARACADUT|PJE""").containsMatchIn(normalized) ->
            "Lanci paracadutistici nell'area"
        Regex("""AIR\s*DISPLAY|AIRSHOW|FLYPAST|AEROBATIC|MANIFESTAZION[EI]\s+AERE""").containsMatchIn(normalized) ->
            "Manifestazione aerea nell'area"
        Regex("""UAS|UAV|DRONE|CIV\s+UNMANNED|UNMANNED\s+ACFT|RPAS|OPERAZIONI\s+CON\s+DRONI""").containsMatchIn(normalized) ->
            "Operazioni con droni nella zona delimitata dal NOTAM"
        Regex("""SAR|SEARCH\s+AND\s+RESCUE|RICERCA\s+E\s+SOCCORSO""").containsMatchIn(normalized) ->
            "Operazioni di ricerca e soccorso nell'area"
        Regex("""LASER""").containsMatchIn(normalized) ->
            "Impiego di laser nell'area"
        Regex("""\bMIL\b|\bMILITARY\b|MIL\s+OPS|MILITARY\s+OPS|ATTIVIT[AÀ]\s+MILITAR""").containsMatchIn(normalized) ->
            "Attivit\u00E0 militare nell'area"
        Regex("""ATTIVIT[AÀ]\s+OPERATIVE\s+SPECIFICATE\s+NEL\s+NOTAM|GENERIC""").containsMatchIn(normalized) ->
            "Attivit\u00E0 operative specificate nel NOTAM"
        else -> null
    }
}

private fun String.normalizedForUiDedup(): String =
    lowercase()
        .replace(Regex("[^a-z0-9àèéìòù]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private val DayOrder = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val WeekdayCodes = DayOrder.take(5)
private val WeekendCodes = DayOrder.takeLast(2)
private val DayCodeRegex = Regex("""\b(MON|TUE|WED|THU|FRI|SAT|SUN)\b""")
private val ScheduleSelectorCodeRegex = Regex("""\b(MON|TUE|WED|THU|FRI|SAT|SUN|HOL)\b""")
private val DayExpressionRegex = Regex("""\b(?:MON|TUE|WED|THU|FRI|SAT|SUN|HOL)(?:\s*(?:-|,|\s|AND|E)\s*(?:MON|TUE|WED|THU|FRI|SAT|SUN|HOL))*\b""")
private val DayClauseRegex = Regex("""\b((?:MON|TUE|WED|THU|FRI|SAT|SUN|HOL)(?:\s*(?:-|,|\s|AND|E)\s*(?:MON|TUE|WED|THU|FRI|SAT|SUN|HOL))*)\s*:""")
private val StrictDayRangeRegex = Regex("""^(MON|TUE|WED|THU|FRI|SAT|SUN)\s*-\s*(MON|TUE|WED|THU|FRI|SAT|SUN)$""")
private val TimeRangeRegex = Regex("""\b(\d{2})(\d{2})\s*-\s*(\d{2})(\d{2})\b""")
private val UtcHourMinuteRegex = Regex("""^\d{2}:\d{2}$""")
private val AeronauticalTimeRangeRegex = Regex("""\b(\d{4}|SR(?:[+-]\d+)?|SS(?:[+-]\d+)?|SUNRISE(?:[+-]\d+)?|SUNSET(?:[+-]\d+)?)\s*-\s*(\d{4}|SR(?:[+-]\d+)?|SS(?:[+-]\d+)?|SUNRISE(?:[+-]\d+)?|SUNSET(?:[+-]\d+)?)\b""")
private val AeronauticalScheduleClauseBoundaryRegex = Regex("""\b(H24|HJ|(?:\d{4}|SR(?:[+-]\d+)?|SS(?:[+-]\d+)?|SUNRISE(?:[+-]\d+)?|SUNSET(?:[+-]\d+)?)\s*-\s*(?:\d{4}|SR(?:[+-]\d+)?|SS(?:[+-]\d+)?|SUNRISE(?:[+-]\d+)?|SUNSET(?:[+-]\d+)?))\s+(?=(?:MON|TUE|WED|THU|FRI|SAT|SUN|HOL)\b)""")
private val DelimitedScheduleClauseSeparatorRegex = Regex(""";\s*|,\s*(?=(?:MON|TUE|WED|THU|FRI|SAT|SUN)\b)""")
private val FromToDayTimeRegex = Regex("""\b(?:DA|FROM)\s+(MON|TUE|WED|THU|FRI|SAT|SUN)\s+(\d{2})(\d{2})\s+(?:A|TO)\s+(MON|TUE|WED|THU|FRI|SAT|SUN)\s+(\d{2})(\d{2})\b""")
private val InactivePeriodRegex = Regex("""\bNON\s+ATTIVA\s*:?\s*(\d{1,2})\s+([A-Z]{3})\s*-\s*(\d{1,2})\s+([A-Z]{3})\b""")
private val ParenthesizedTextRegex = Regex("""\([^)]*\)""")
private val DayLabels = mapOf(
    "MON" to "Lunedì",
    "TUE" to "Martedì",
    "WED" to "Mercoledì",
    "THU" to "Giovedì",
    "FRI" to "Venerdì",
    "SAT" to "Sabato",
    "SUN" to "Domenica"
)
private val MonthLabels = mapOf(
    "JAN" to "gennaio",
    "FEB" to "febbraio",
    "MAR" to "marzo",
    "APR" to "aprile",
    "MAY" to "maggio",
    "MAG" to "maggio",
    "JUN" to "giugno",
    "GIU" to "giugno",
    "JUL" to "luglio",
    "LUG" to "luglio",
    "AUG" to "agosto",
    "AGO" to "agosto",
    "SEP" to "settembre",
    "SET" to "settembre",
    "OCT" to "ottobre",
    "OTT" to "ottobre",
    "NOV" to "novembre",
    "DEC" to "dicembre",
    "DIC" to "dicembre"
)
private val MonthLabelsByNumber = mapOf(
    1 to "gennaio",
    2 to "febbraio",
    3 to "marzo",
    4 to "aprile",
    5 to "maggio",
    6 to "giugno",
    7 to "luglio",
    8 to "agosto",
    9 to "settembre",
    10 to "ottobre",
    11 to "novembre",
    12 to "dicembre"
)

private val UiUtcFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC)
