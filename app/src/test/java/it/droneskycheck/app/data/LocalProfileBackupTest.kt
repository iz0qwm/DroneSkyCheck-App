package it.droneskycheck.app.data

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

class LocalProfileBackupTest {
    @Test
    fun serializesAndDeserializesCompleteBackupWithMultipleDronesAndCertificates() {
        val packageData = backupPackage(
            payload = payload(
                certificates = listOf(
                    certificate("cert-a1", "A1_A3"),
                    certificate("cert-sts", "STS_01")
                ),
                drones = listOf(
                    drone("mini", selected = true),
                    drone("air", selected = false)
                )
            )
        )

        val restored = roundTrip(packageData)
        val payload = LocalProfileBackupCodec.validatePackage(restored)

        assertEquals("Raffaello", payload.profile?.firstName)
        assertEquals(2, payload.certificates.size)
        assertEquals(2, payload.drones.size)
        assertEquals("mini", payload.drones.first { it.isSelected }.id)
        assertEquals(1, payload.authorizationDrafts.size)
    }

    @Test
    fun backupWithoutPhotoHasNoAttachments() {
        val packageData = backupPackage(
            payload = payload(profile = profile(photo = ""))
        )

        val restored = roundTrip(packageData)
        val payload = LocalProfileBackupCodec.validatePackage(restored)

        assertTrue(restored.attachments.isEmpty())
        assertEquals("", payload.profile?.profilePhoto)
    }

    @Test
    fun backupWithPhotoStoresAttachmentReferenceAndBytes() {
        val photoBytes = byteArrayOf(1, 2, 3, 4)
        val packageData = backupPackage(
            payload = payload(profile = profile(photo = "profile_photo.jpg")),
            attachmentReader = { path -> if (path == "profile_photo.jpg") photoBytes else null }
        )

        val restored = roundTrip(packageData)
        val payload = LocalProfileBackupCodec.validatePackage(restored)
        val photoPath = requireNotNull(payload.profile).profilePhoto

        assertTrue(photoPath.startsWith("attachments/"))
        assertArrayEquals(photoBytes, restored.attachments.getValue(photoPath))
    }

    @Test
    fun invalidZipIsRejected() {
        val result = runCatching {
            LocalProfileBackupCodec.readZip(ByteArrayInputStream("not a backup".toByteArray()))
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun unsupportedFormatVersionIsRejected() {
        val packageData = backupPackage()
        packageData.json.put("backupFormatVersion", 99)

        val result = runCatching {
            LocalProfileBackupCodec.validatePackage(packageData)
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("non supportata") == true)
    }

    @Test
    fun missingAttachmentIsRejectedBeforeImport() {
        val packageData = backupPackage(
            payload = payload(profile = profile(photo = "profile_photo.jpg")),
            attachmentReader = { byteArrayOf(9) }
        )
        val broken = LocalProfileBackupPackage(
            json = packageData.json,
            attachments = emptyMap()
        )

        val result = runCatching {
            LocalProfileBackupCodec.validatePackage(broken)
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun backupSummaryCountsLocalProfileData() {
        val packageData = backupPackage(
            payload = payload(
                certificates = listOf(certificate("cert-a1", "A1_A3")),
                drones = listOf(drone("mini", selected = true), drone("air", selected = false))
            )
        )

        val summary = LocalProfileBackupCodec.summary(packageData)

        assertTrue(summary.profilePresent)
        assertEquals(1, summary.certificates)
        assertEquals(2, summary.drones)
        assertEquals(1, summary.authorizationDrafts)
    }

    private fun roundTrip(packageData: LocalProfileBackupPackage): LocalProfileBackupPackage {
        val output = ByteArrayOutputStream()
        LocalProfileBackupCodec.writeZip(packageData, output)
        return LocalProfileBackupCodec.readZip(ByteArrayInputStream(output.toByteArray()))
    }

    private fun backupPackage(
        payload: LocalProfileBackupPayload = payload(),
        attachmentReader: (String) -> ByteArray? = { null }
    ): LocalProfileBackupPackage =
        LocalProfileBackupCodec.createPackage(
            payload = payload,
            appVersionName = "3.6",
            appVersionCode = 26,
            now = Instant.parse("2026-08-23T10:15:30Z"),
            attachmentReader = attachmentReader
        )

    private fun payload(
        profile: PilotProfileEntity? = profile(),
        certificates: List<PilotCertificateEntity> = listOf(certificate("cert-a1", "A1_A3")),
        operator: UasOperatorEntity? = UasOperatorEntity(name = "Operatore"),
        drones: List<LocalDroneEntity> = listOf(drone("mini", selected = true)),
        drafts: List<AuthorizationDraftEntity> = listOf(draft())
    ): LocalProfileBackupPayload =
        LocalProfileBackupPayload(
            profile = profile,
            certificates = certificates,
            operator = operator,
            drones = drones,
            authorizationDrafts = drafts
        )

    private fun profile(photo: String = ""): PilotProfileEntity =
        PilotProfileEntity(
            firstName = "Raffaello",
            lastName = "Di Martino",
            email = "raffa@example.com",
            profilePhoto = photo
        )

    private fun certificate(id: String, categories: String): PilotCertificateEntity =
        PilotCertificateEntity(
            id = id,
            issuingAuthority = "ENAC",
            certificateNumber = id.uppercase(),
            categories = categories
        )

    private fun drone(id: String, selected: Boolean): LocalDroneEntity =
        LocalDroneEntity(
            id = id,
            manufacturer = "DJI",
            model = id,
            classLabel = "C1",
            serialNumber = "SN-$id",
            isSelected = selected
        )

    private fun draft(): AuthorizationDraftEntity =
        AuthorizationDraftEntity(
            id = "draft-1",
            procedureType = "ATM09",
            procedureVersion = 1,
            status = AuthorizationDraftStatuses.Draft,
            zoneSnapshotJson = JSONObject().put("name", "Zona").toString(),
            operationDataJson = JSONObject().put("workflowStep", AuthorizationWorkflowSteps.Takeoff).toString(),
            pilotSnapshotJson = JSONObject().put("firstName", "Raffaello").toString(),
            operatorSnapshotJson = JSONObject().put("name", "Operatore").toString(),
            certificateSnapshotJson = "[]",
            droneSnapshotJson = JSONObject().put("id", "mini").toString(),
            requestDataJson = JSONObject().put("selectedDroneId", "mini").toString(),
            missingFieldsJson = "[]"
        )
}
