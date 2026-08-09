package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.AuthorizationInfo
import it.droneskycheck.app.data.EnrInfo
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
}
