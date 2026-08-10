package it.droneskycheck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizationReadinessEvaluatorTest {
    @Test
    fun `specific category without STS certificate blocks STS path with explicit message`() {
        val draft = draft(
            zoneSnapshotJson = zoneSnapshot(requiredOperationCategory = "SPECIFIC"),
            certificateSnapshotJson = "[]",
            droneSnapshotJson = droneSnapshot(classLabel = "C5", sts01Declaration = true),
            requestData = AuthorizationRequestData(license = "A1/A3")
        )

        val readiness = AuthorizationReadinessEvaluator.evaluate(draft)

        assertEquals("SPECIFIC", readiness.requiredOperationCategory)
        assertFalse(readiness.canGeneratePdf)
        assertTrue(readiness.blockingMessages.first().body.contains("Autorizzazione Operativa ENAC"))
    }

    @Test
    fun `specific STS01 requires C5 drone`() {
        val draft = draft(
            zoneSnapshotJson = zoneSnapshot(requiredOperationCategory = "SPECIFIC"),
            certificateSnapshotJson = certificatesSnapshot("STS_01"),
            droneSnapshotJson = droneSnapshot(classLabel = "C6", sts01Declaration = true),
            requestData = AuthorizationRequestData(license = "EU-STS-01")
        )

        val readiness = AuthorizationReadinessEvaluator.evaluate(draft)

        assertFalse(readiness.canGeneratePdf)
        assertTrue(readiness.blockingMessages.any { it.title.contains("Drone non coerente") })
    }

    @Test
    fun `ATM09 alone does not imply specific category`() {
        val draft = draft(
            procedureType = "ATM09",
            zoneSnapshotJson = zoneSnapshot(requiredOperationCategory = ""),
            certificateSnapshotJson = "[]",
            droneSnapshotJson = droneSnapshot(classLabel = "C2"),
            requestData = AuthorizationRequestData(license = "A2")
        )

        val readiness = AuthorizationReadinessEvaluator.evaluate(draft)

        assertEquals("Non determinata", readiness.requiredOperationCategory)
        assertTrue(readiness.canGeneratePdf)
    }

    private fun draft(
        procedureType: String = "ATM05",
        zoneSnapshotJson: String,
        certificateSnapshotJson: String,
        droneSnapshotJson: String,
        requestData: AuthorizationRequestData
    ): AuthorizationDraft =
        AuthorizationDraft(
            id = "draft-1",
            procedureType = procedureType,
            procedureVersion = 1,
            status = AuthorizationDraftStatuses.Draft,
            zoneSnapshotJson = zoneSnapshotJson,
            operationDataJson = AuthorizationOperationData().toJson(),
            pilotSnapshotJson = "{}",
            operatorSnapshotJson = "{}",
            certificateSnapshotJson = certificateSnapshotJson,
            droneSnapshotJson = droneSnapshotJson,
            requestDataJson = requestData.toJson(),
            missingFields = emptyList(),
            createdAt = 1L,
            updatedAt = 1L
        )

    private fun zoneSnapshot(requiredOperationCategory: String): String =
        """{"name":"Fixture","requiredOperationCategory":"$requiredOperationCategory","reasonCodes":[]}"""

    private fun certificatesSnapshot(vararg categories: String): String =
        """[{"categories":[${categories.joinToString(",") { "\"$it\"" }}]}]"""

    private fun droneSnapshot(
        classLabel: String,
        sts01Declaration: Boolean = false,
        sts02Declaration: Boolean = false
    ): String =
        """{"classLabel":"$classLabel","euSts01Registered":$sts01Declaration,"euSts02Registered":$sts02Declaration}"""
}
