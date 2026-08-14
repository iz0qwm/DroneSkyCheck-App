package it.droneskycheck.app.ui.map

import it.droneskycheck.app.data.flight.FlightLightPreference
import it.droneskycheck.app.data.flight.FlightOpportunityMode
import it.droneskycheck.app.data.flight.FlightOpportunityReasonCode
import it.droneskycheck.app.data.flight.FlightOpportunityResult
import it.droneskycheck.app.data.flight.FlightOpportunityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightOpportunityPresentationTest {
    @Test
    fun technicalPlanningUsesPlanningCopyAndMandatoryDisclaimer() {
        assertEquals("Pianificazione tecnica", FlightOpportunityMode.TECHNICAL_PLANNING.summaryTitle())
        assertEquals("Migliore finestra tecnica", FlightOpportunityMode.TECHNICAL_PLANNING.mainWindowLabel())
        assertFalse(
            FlightOpportunityMode.TECHNICAL_PLANNING.mainWindowLabel()
                .equals("Migliore finestra di volo", ignoreCase = true)
        )
        assertTrue(
            FlightOpportunityMode.TECHNICAL_PLANNING
                .statusText(FlightOpportunityStatus.READY, FlightLightPreference.DAYLIGHT)
                .contains("pianificazione", ignoreCase = true)
        )
        assertTrue(TechnicalPlanningDisclaimerText.contains("Non indica che il volo sia autorizzato"))
        assertTrue(TechnicalPlanningDisclaimerText.contains("autorizzazioni"))
    }

    @Test
    fun technicalPlanningIntroKeepsUnavailableAndUnknownStrict() {
        val unavailable = noOpenResult(FlightOpportunityReasonCode.LEGAL_UNAVAILABLE)
        val unknown = noOpenResult(FlightOpportunityReasonCode.LEGAL_UNKNOWN)

        assertTrue(unavailable.technicalPlanningIntroText().contains("sole condizioni tecniche"))
        assertTrue(unavailable.technicalPlanningIntroText().contains("non disponibile"))
        assertTrue(unknown.technicalPlanningIntroText().contains("sole condizioni tecniche"))
        assertTrue(unknown.technicalPlanningIntroText().contains("non determinato"))
    }

    private fun noOpenResult(blocker: FlightOpportunityReasonCode): FlightOpportunityResult =
        FlightOpportunityResult(
            mode = FlightOpportunityMode.OPEN,
            status = FlightOpportunityStatus.NO_OPEN_WINDOW,
            bestOpportunity = null,
            nextOpportunity = null,
            weekendOpportunities = emptyList(),
            alternatives = emptyList(),
            horizonFrom = null,
            horizonTo = null,
            warnings = emptyList(),
            blockers = listOf(blocker),
            technicalPlanningAvailable = true
        )
}
