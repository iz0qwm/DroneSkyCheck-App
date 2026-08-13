package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.AuthorizationInfo
import it.droneskycheck.app.data.EnrInfo
import it.droneskycheck.app.data.KeyValueInfo
import it.droneskycheck.app.data.NotamInfo
import it.droneskycheck.app.data.OfficialInfo
import it.droneskycheck.app.data.ScheduleInfo
import it.droneskycheck.app.data.TemporalBarEntry
import it.droneskycheck.app.data.ValidityInfo
import it.droneskycheck.app.data.ZoneInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZonePresentationTest {
    @Test
    fun activeZoneStatusIsPrimaryAndExplicit() {
        val status = zone(activeNow = true).primaryStatusPresentation()

        assertEquals("Attiva ora", status?.label)
        assertEquals(ZoneStatusEmphasis.Active, status?.emphasis)
    }

    @Test
    fun inactiveZoneStatusIsPrimaryAndExplicit() {
        val status = zone(activeNow = false).primaryStatusPresentation()

        assertEquals("Non attiva ora", status?.label)
        assertEquals(ZoneStatusEmphasis.Inactive, status?.emphasis)
    }

    @Test
    fun temporalDetailsExposeScheduleValidityAndNextActivation() {
        val details = zone(
            activeNow = false,
            validity = ValidityInfo(
                activeNow = false,
                validFrom = "2026-08-09T07:00:00Z",
                validTo = "2026-08-09T18:00:00Z",
                schedule = "MON-FRI 0700-1800",
                interpretedSchedule = null,
                nextActivation = "2026-08-10T07:00:00Z",
                explanation = "Zona non attiva ora",
                future = null,
                expired = null
            )
        ).temporalDetailsPresentation()

        assertTrue(details.hasContent)
        assertEquals("Non attiva ora", details.status)
        assertEquals("Da lunedì a venerdì dalle 07:00 alle 18:00 UTC", details.activitySchedule)
        assertEquals("MON-FRI 0700-1800", details.originalSchedule)
        assertEquals("10/08/2026 07:00 UTC", details.nextActivation)
        assertNotNull(details.validity)
    }

    @Test
    fun temporalDetailsParsesBackendTimeRangeWithoutDay() {
        val details = zone(
            activeNow = true,
            validity = ValidityInfo(
                activeNow = true,
                validFrom = null,
                validTo = null,
                schedule = "0700-1600",
                interpretedSchedule = null,
                nextActivation = null,
                explanation = null,
                future = null,
                expired = null
            )
        ).temporalDetailsPresentation()

        assertEquals("dalle 07:00 alle 16:00 UTC", details.activitySchedule)
        assertEquals("0700-1600", details.originalSchedule)
    }

    @Test
    fun temporalDetailsParsesWeekdaySchedulesWithMultipleRanges() {
        val details = zone(
            activeNow = false,
            enr = enr(
                schedule = ScheduleInfo(
                    raw = "MON TUE WED THU FRI 0700-1200 1300-1700 EXC HOL",
                    human = null,
                    activeNow = false,
                    explanation = null
                )
            )
        ).temporalDetailsPresentation()

        assertEquals(
            "Da lunedì a venerdì dalle 07:00 alle 12:00 UTC, dalle 13:00 alle 17:00 UTC festivi esclusi",
            details.activitySchedule
        )
    }

    @Test
    fun temporalDetailsIgnoresParenthesizedLocalTimesAndKeepsDayClauses() {
        val details = zone(
            activeNow = true,
            enr = enr(
                schedule = ScheduleInfo(
                    raw = "MON-TUE-WED-FRI: 0730-1700 (0630-1600) THU: 0730-2200 (0630-2100) HOL ESCLUSI/EXCLUDED. ATTIVA/ACTIVE 1 SEP-30 JUN ORARI DIVERSI CON PREAVVISO A MEZZO NOTAM",
                    human = null,
                    activeNow = true,
                    explanation = null
                )
            )
        ).temporalDetailsPresentation()

        assertEquals(
            "Lunedì, martedì, mercoledì e venerdì dalle 07:30 alle 17:00 UTC; Giovedì dalle 07:30 alle 22:00 UTC; festivi esclusi",
            details.activitySchedule
        )
    }

    @Test
    fun temporalDetailsIgnoresBilingualDuplicateAndExcludedHolidayText() {
        val details = zone(
            activeNow = true,
            enr = enr(
                schedule = ScheduleInfo(
                    raw = "MON-FRI 0630-1800 (0530-1700), HOL E 20 GIUGNO ESCLUSI/MON-FRI 0630-1800 (0530-1700), HOL AND JUNE 20TH EXCLUDED",
                    human = null,
                    activeNow = true,
                    explanation = null
                )
            )
        ).temporalDetailsPresentation()

        assertEquals(
            "Da lunedì a venerdì dalle 06:30 alle 18:00 UTC festivi esclusi",
            details.activitySchedule
        )
    }

    @Test
    fun temporalDetailsParsesFromDayTimeToDayTimeAndNotamOnlyClause() {
        val details = zone(
            activeNow = true,
            enr = enr(
                schedule = ScheduleInfo(
                    raw = "ATTIVA/ACTIVE: 7 JAN-21 JUN E/AND 20 SEP-21 DIC DA/FROM SUN 2300 (2200) A/TO SAT 1200 (1100); HOL ESCLUSI/EXCLUDED. INOLTRE/MOREOVER SAT 1200-2300 (1100-2200) E/AND HOL: ATTIVA SOLO CON PREAVVISO A MEZZO/ACTIVE BY NOTAM",
                    human = null,
                    activeNow = true,
                    explanation = null
                )
            )
        ).temporalDetailsPresentation()

        assertEquals(
            "Da domenica 23:00 UTC a sabato 12:00 UTC; festivi esclusi; Sabato dalle 12:00 alle 23:00 UTC e festivi: attiva solo con preavviso NOTAM",
            details.activitySchedule
        )
    }

    @Test
    fun temporalDetailsParsesAnnouncedByNotamDayClause() {
        val details = zone(
            activeNow = true,
            enr = enr(
                schedule = ScheduleInfo(
                    raw = "FROM 1 SEP TO 30 JUN MON-FRI 0630-2200 (0530-2100); SAT ANNONCED BY NOTAM; HOL ESCLUSI/EXCLUDED",
                    human = null,
                    activeNow = true,
                    explanation = null
                )
            )
        ).temporalDetailsPresentation()

        assertEquals(
            "Da lunedì a venerdì dalle 06:30 alle 22:00 UTC; festivi esclusi; Sabato: attiva con preavviso NOTAM",
            details.activitySchedule
        )
    }

    @Test
    fun temporalDetailsParsesDelimitedDaySchedulesAndInactivePeriod() {
        val details = zone(
            activeNow = true,
            enr = enr(
                schedule = ScheduleInfo(
                    raw = "MON-THU 0700-1900; FRI 0700-1400; HOL EXCLUDED NON ATTIVA :/NOT ACTIVE: 21 JUN - 20 SEP",
                    human = null,
                    activeNow = true,
                    explanation = null
                )
            )
        ).temporalDetailsPresentation()

        assertEquals(
            "Da lunedì a giovedì dalle 07:00 alle 19:00 UTC; Venerdì dalle 07:00 alle 14:00 UTC; festivi esclusi; non attiva dal 21 giugno al 20 settembre",
            details.activitySchedule
        )
    }

    @Test
    fun temporalDetailsParsesH24WithExcludedHolidays() {
        val details = zone(
            activeNow = true,
            enr = enr(
                schedule = ScheduleInfo(
                    raw = "H24; HOL ESCLUSI/EXCLUDED",
                    human = null,
                    activeNow = true,
                    explanation = null
                )
            )
        ).temporalDetailsPresentation()

        assertEquals("24 ore su 24 UTC; festivi esclusi", details.activitySchedule)
    }

    @Test
    fun temporalDetailsParsesAeronauticalSunSchedule() {
        val details = zone(
            activeNow = true,
            enr = enr(
                schedule = ScheduleInfo(
                    raw = "DAILY HJ",
                    human = null,
                    activeNow = true,
                    explanation = null
                )
            )
        ).temporalDetailsPresentation()

        assertEquals("Ogni giorno da alba a tramonto", details.activitySchedule)
    }

    @Test
    fun temporalDetailsParsesDailyScheduleEndingAtSunset() {
        val details = zone(
            activeNow = true,
            enr = enr(
                schedule = ScheduleInfo(
                    raw = "DAILY 1000-SS",
                    human = null,
                    activeNow = true,
                    explanation = null
                )
            )
        ).temporalDetailsPresentation()

        assertEquals("Ogni giorno dalle 10:00 UTC al tramonto", details.activitySchedule)
    }

    @Test
    fun temporalDetailsKeepsSingleWeekdayInCommaSeparatedGroups() {
        val details = zone(
            activeNow = true,
            enr = enr(
                schedule = ScheduleInfo(
                    raw = "MON-THU 0500-2100, FRI 0500-1700",
                    human = null,
                    activeNow = true,
                    explanation = null
                )
            )
        ).temporalDetailsPresentation()

        assertEquals(
            "Da luned\u00EC a gioved\u00EC dalle 05:00 alle 21:00 UTC; Venerd\u00EC dalle 05:00 alle 17:00 UTC",
            details.activitySchedule
        )
    }

    @Test
    fun temporalDetailsFallsBackToBackendHumanScheduleWhenRawIsNotRecognized() {
        val details = zone(
            activeNow = true,
            enr = enr(
                schedule = ScheduleInfo(
                    raw = "As notified by NOTAM",
                    human = "Secondo NOTAM pubblicato",
                    activeNow = true,
                    explanation = null
                )
            )
        ).temporalDetailsPresentation()

        assertEquals("Secondo NOTAM pubblicato", details.activitySchedule)
        assertEquals("As notified by NOTAM", details.originalSchedule)
    }

    @Test
    fun temporalDetailsExposeGraphicSchedules() {
        val details = zone(
            activeNow = false,
            enr = EnrInfo(
                code = "ENR 5.2",
                name = null,
                description = null,
                limitText = null,
                notes = null,
                classification = null,
                activationType = null,
                operationMode = null,
                operationCategory = null,
                requiredLicense = null,
                authorizationRequired = null,
                schedule = null,
                authority = null,
                official = null,
                validity = null,
                explanation = null,
                operationalMeaning = null,
                weekSchedule = listOf(TemporalBarEntry(active = true), TemporalBarEntry(active = false)),
                daySchedule = listOf(false, true)
            )
        ).temporalDetailsPresentation()

        assertTrue(details.hasContent)
        assertEquals(2, details.weekSchedule.size)
        assertEquals(true, details.weekSchedule.first().active)
        assertEquals(listOf(false, true), details.daySchedule)
    }

    @Test
    fun informationalNotamUsesSingleHumanExplanation() {
        val official = OfficialInfo(
            sourceText = "W1234/26 NOTAMN Q) LIXX/QXXXX A) LIXX E) TESTO ORIGINALE",
            sourceReference = null,
            qLine = null
        )
        val presentation = NotamInfo(
            code = "W1234/26",
            fir = "LIXX",
            location = null,
            zoneReference = null,
            activityType = "attivita operative specificate nel NOTAM",
            severity = "INFO",
            summary = "NOTAM informativo",
            explanation = "Il NOTAM e informativo e non introduce da solo un blocco operativo",
            operationalMeaning = "NOTAM informativo: non introduce da solo un blocco operativo",
            blockingReason = null,
            schedule = ScheduleInfo(
                raw = "H24",
                human = null,
                activeNow = true,
                explanation = null
            ),
            official = official,
            validity = null
        ).presentation()

        assertEquals("Informativo", presentation.statusLabel)
        assertTrue(presentation.body.contains("Non introduce automaticamente un divieto di volo"))
        assertFalse(presentation.body.contains("Spiegazione DSC"))
        assertFalse(presentation.body.contains("Significato operativo"))
        assertEquals(official.sourceText, presentation.official?.sourceText)
    }

    @Test
    fun notamPresentationSimplifiesReasonFromOfficialFieldE() {
        val presentation = notamPresentationForReason("UAS ACTIVITY WILL TAKE PLACE")

        assertEquals("Restrizione temporanea", presentation.statusLabel)
        assertEquals("Operazioni con droni nella zona delimitata dal NOTAM", presentation.reasonText)
        assertFalse(presentation.body.contains("non puÃ² determinare automaticamente"))
    }

    @Test
    fun notamPresentationSimplifiesCommonTechnicalReasons() {
        val cases = listOf(
            "GLIDERS COMPETITION WILL TAKE PLACE WITHIN AREA BOUNDED BY LINE JOINING FOLLOW POINTS:" to
                "Competizione di alianti nella zona delimitata dal NOTAM",
            "MIL UNMANNED ACFT ACT WILL TAKE PLACE WI FLW AREA:" to
                "Utilizzo di aeromobili militari a pilotaggio remoto",
            "MILITARY FIRING AREA" to
                "Esercitazioni militari a fuoco",
            "ASCENT OF FREE BALLOONS WILL TAKE PLACE" to
                "Palloni aerostatici nell'area"
        )

        cases.forEach { (rawReason, expectedReason) ->
            assertEquals(expectedReason, notamPresentationForReason(rawReason).reasonText)
        }
    }

    @Test
    fun notamAuthorizationUsesSingleShortNotice() {
        val authorization = AuthorizationInfo(
            required = null,
            requirement = null,
            operationMode = null,
            operationCategory = null,
            requiredLicense = null,
            explanation = "Verifica manuale necessaria",
            resolutionStatus = "MANUAL_CHECK",
            reasonCodes = listOf("NOTAM_REQUIRES_MANUAL_CHECK")
        )

        assertEquals(null, authorization.manualCheckSummary())
        assertEquals(
            "Il sistema automatico di richiesta autorizzazioni di Drone Sky Check non e applicabile alle restrizioni temporanee pubblicate tramite NOTAM.",
            authorization.notamTemporaryRestrictionNotice()
        )
    }

    @Test
    fun manualCheckAuthorizationHasOneOperationalStatus() {
        val summary = AuthorizationInfo(
            required = null,
            requirement = null,
            operationMode = null,
            operationCategory = null,
            requiredLicense = null,
            explanation = "Verifica manuale necessaria",
            resolutionStatus = "MANUAL_CHECK"
        ).manualCheckSummary()

        assertEquals("VERIFICA NECESSARIA", summary?.first)
        assertTrue(summary?.second?.contains("non può determinare automaticamente") == true)
    }

    private fun zone(
        activeNow: Boolean,
        validity: ValidityInfo = ValidityInfo(
            activeNow = activeNow,
            validFrom = null,
            validTo = null,
            schedule = null,
            interpretedSchedule = null,
            explanation = null,
            future = null,
            expired = null
        ),
        enr: EnrInfo? = null
    ): ZoneInfo =
        ZoneInfo(
            id = "zone-1",
            name = "Test zone",
            family = "ENR",
            type = "ATM09_RESTRICTED",
            limitMetersAgl = 0,
            description = null,
            validity = validity,
            enr = enr,
            authorizationRequired = null,
            activeNow = activeNow
        )

    private fun enr(
        schedule: ScheduleInfo? = null,
        weekSchedule: List<TemporalBarEntry> = emptyList(),
        daySchedule: List<Boolean?> = emptyList()
    ): EnrInfo =
        EnrInfo(
            code = "ENR 5.2",
            name = null,
            description = null,
            limitText = null,
            notes = null,
            classification = null,
            activationType = null,
            operationMode = null,
            operationCategory = null,
            requiredLicense = null,
            authorizationRequired = null,
            schedule = schedule,
            authority = null,
            official = null,
            validity = null,
            explanation = null,
            operationalMeaning = null,
            weekSchedule = weekSchedule,
            daySchedule = daySchedule
        )

    private fun notamPresentationForReason(reason: String): NotamPresentation =
        NotamInfo(
            code = "W1234/26",
            fir = "LIXX",
            location = null,
            zoneReference = null,
            activityType = null,
            severity = "HARD",
            summary = null,
            explanation = null,
            operationalMeaning = null,
            blockingReason = "ACTIVE_HARD_NOTAM",
            schedule = ScheduleInfo(
                raw = "DAILY 1000-SS",
                human = null,
                activeNow = true,
                explanation = null
            ),
            official = OfficialInfo(
                sourceText = "W1234/26 NOTAMN Q) LIXX/QXXXX A) LIXX E) $reason",
                sourceReference = null,
                qLine = null,
                fields = listOf(KeyValueInfo("E", reason))
            ),
            validity = null
        ).presentation()
}
