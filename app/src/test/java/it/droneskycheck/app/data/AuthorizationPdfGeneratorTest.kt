package it.droneskycheck.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthorizationPdfGeneratorTest {
    @Test
    fun `templates are loaded from Android assets paths`() {
        assertEquals(
            "pdf/mod-atm05-editabile.pdf",
            AuthorizationPdfGenerator.templateAssetPath("ATM05")
        )
        assertEquals(
            "pdf/mod-atm09-editabile.pdf",
            AuthorizationPdfGenerator.templateAssetPath("ATM09")
        )
    }

    @Test
    fun `stamp date stays independent from operation start date`() {
        val draft = AuthorizationDraft(
            id = "draft-1",
            procedureType = "ATM05",
            procedureVersion = 1,
            status = AuthorizationDraftStatuses.Ready,
            zoneSnapshotJson = """{"name":"Fixture","authority":{"contact":"ente@example.test"}}""",
            operationDataJson = AuthorizationOperationData().toJson(),
            pilotSnapshotJson = "{}",
            operatorSnapshotJson = "{}",
            certificateSnapshotJson = "[]",
            droneSnapshotJson = "{}",
            requestDataJson = AuthorizationRequestData(
                operationStartDate = "2026-08-20",
                operationEndDate = "2026-08-21",
                stampNumber = "MB-123",
                stampDate = "2026-08-11"
            ).toJson(),
            missingFields = emptyList(),
            createdAt = 1L,
            updatedAt = 1L
        )

        val fields = authorizationPdfFieldValues(draft)

        assertEquals("Dal 20/08/2026\nAl 21/08/2026", fields.getValue("dateTime_activity"))
        assertEquals("11/08/2026", fields.getValue("stampDate"))
    }

    @Test
    fun `pdf recipient uses only email when stored contact is authority json`() {
        val draft = AuthorizationDraft(
            id = "draft-2",
            procedureType = "ATM05",
            procedureVersion = 1,
            status = AuthorizationDraftStatuses.Ready,
            zoneSnapshotJson = """{"name":"Fixture","authority":{"contact":"{\"emails\":[\"protocollo.prefrm@pec.interno.it\"],\"note\":\"Prefettura di Roma\"}"}}""",
            operationDataJson = AuthorizationOperationData().toJson(),
            pilotSnapshotJson = "{}",
            operatorSnapshotJson = "{}",
            certificateSnapshotJson = "[]",
            droneSnapshotJson = "{}",
            requestDataJson = AuthorizationRequestData().toJson(),
            missingFields = emptyList(),
            createdAt = 1L,
            updatedAt = 1L
        )

        val fields = authorizationPdfFieldValues(draft)

        assertEquals("protocollo.prefrm@pec.interno.it", fields.getValue("to"))
    }
}
