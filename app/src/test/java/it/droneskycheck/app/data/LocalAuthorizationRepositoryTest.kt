package it.droneskycheck.app.data

import kotlinx.coroutines.runBlocking
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAuthorizationRepositoryTest {
    @Test
    fun createsAtm05Draft() = runBlocking {
        val repository = seededRepository()

        val result = repository.createDraftFromZone(atmZone("ATM05"), lat = 41.9, lon = 12.5)

        val draft = (result as CreateAuthorizationDraftResult.Created).draft
        assertEquals("ATM05", draft.procedureType)
        assertEquals("Roma CTR", draft.zoneName)
    }

    @Test
    fun createsAtm09Draft() = runBlocking {
        val repository = seededRepository(certificateCategories = "STS_01")

        val result = repository.createDraftFromZone(atmZone("ATM09"), lat = 41.9, lon = 12.5)

        val draft = (result as CreateAuthorizationDraftResult.Created).draft
        assertEquals("ATM09", draft.procedureType)
        assertEquals("Riprese video e foto a carattere professionale - VLOS", draft.requestData.activityType)
    }

    @Test
    fun rejectsManualCheck() = runBlocking {
        val repository = seededRepository()

        val result = repository.createDraftFromZone(
            atmZone("ATM05", resolutionStatus = "MANUAL_CHECK"),
            lat = 41.9,
            lon = 12.5
        )

        assertTrue(result is CreateAuthorizationDraftResult.Unsupported)
    }

    @Test
    fun rejectsBlocked() = runBlocking {
        val repository = seededRepository()

        val result = repository.createDraftFromZone(
            atmZone("ATM09", resolutionStatus = "BLOCKED"),
            lat = 41.9,
            lon = 12.5
        )

        assertTrue(result is CreateAuthorizationDraftResult.Unsupported)
    }

    @Test
    fun rejectsEnteParcoWithoutDocumentProcedure() = runBlocking {
        val repository = seededRepository()

        val result = repository.createDraftFromZone(enteParcoOnlyZone(), lat = 41.9, lon = 12.5)

        assertTrue(result is CreateAuthorizationDraftResult.Unsupported)
    }

    @Test
    fun acceptsInactiveWhenActiveApplicability() = runBlocking {
        val repository = seededRepository()

        val result = repository.createDraftFromZone(
            atmZone("ATM05", activeNow = false, applicability = "WHEN_ACTIVE"),
            lat = 41.9,
            lon = 12.5
        )

        assertTrue(result is CreateAuthorizationDraftResult.Created)
    }

    @Test
    fun freezesPilotSnapshot() = runBlocking {
        val dao = FakeLocalPilotDao()
        val pilotRepository = seedPilotWorkspace(dao)
        val repository = LocalAuthorizationRepository(dao, pilotRepository, testAirportRepository())

        val draft = (repository.createDraftFromZone(atmZone("ATM05"), 41.9, 12.5) as CreateAuthorizationDraftResult.Created).draft

        val pilot = JSONObject(draft.pilotSnapshotJson)
        assertEquals("Raffaello", pilot.optString("firstName"))
        assertEquals("Di Martino", pilot.optString("lastName"))
    }

    @Test
    fun freezesOperatorSnapshot() = runBlocking {
        val repository = seededRepository()

        val draft = (repository.createDraftFromZone(atmZone("ATM05"), 41.9, 12.5) as CreateAuthorizationDraftResult.Created).draft

        val operator = JSONObject(draft.operatorSnapshotJson)
        assertEquals("ITA1234567890", operator.optString("easaOperatorCode"))
        assertEquals("raffa@pec.it", operator.optString("pec"))
    }

    @Test
    fun freezesDroneSnapshot() = runBlocking {
        val repository = seededRepository()

        val draft = (repository.createDraftFromZone(atmZone("ATM09"), 41.9, 12.5) as CreateAuthorizationDraftResult.Created).draft

        val drone = JSONObject(draft.droneSnapshotJson)
        assertEquals("DJI", drone.optString("manufacturer"))
        assertEquals("Air 3S", drone.optString("model"))
    }

    @Test
    fun changingProfileAfterCreationDoesNotAlterExistingSnapshot() = runBlocking {
        val dao = FakeLocalPilotDao()
        val pilotRepository = seedPilotWorkspace(dao)
        val repository = LocalAuthorizationRepository(dao, pilotRepository, testAirportRepository())
        val draft = (repository.createDraftFromZone(atmZone("ATM05"), 41.9, 12.5) as CreateAuthorizationDraftResult.Created).draft

        pilotRepository.saveProfile(LocalPilotProfile(firstName = "Nuovo", lastName = "Nome"))
        val reloadedDraft = repository.getDraft(draft.id)

        assertEquals("Raffaello", JSONObject(reloadedDraft!!.pilotSnapshotJson).optString("firstName"))
    }

    @Test
    fun requestDataIsEditableWithoutChangingSnapshot() = runBlocking {
        val repository = seededRepository()
        val draft = (repository.createDraftFromZone(atmZone("ATM05"), 41.9, 12.5) as CreateAuthorizationDraftResult.Created).draft

        val updated = repository.updateRequestData(
            draft.id,
            draft.requestData.copy(contactEmail = "pratica@pec.it")
        )

        assertEquals("pratica@pec.it", updated?.requestData?.contactEmail)
        assertEquals("raffa@pec.it", JSONObject(updated!!.operatorSnapshotJson).optString("pec"))
    }

    @Test
    fun stampNumberOrStampImageSatisfiesValidation() {
        val base = completeRequestData().copy(stampNumber = "", stampImageLocalPath = "")

        val missingStamp = AuthorizationDraftValidator.validate("ATM05", base)
        val withNumber = AuthorizationDraftValidator.validate("ATM05", base.copy(stampNumber = "MB-123"))
        val withImage = AuthorizationDraftValidator.validate("ATM05", base.copy(stampImageLocalPath = "/private/stamp.jpg"))

        assertTrue(missingStamp.missingFields.any { it.key == "request.stamp" })
        assertFalse(withNumber.missingFields.any { it.key == "request.stamp" })
        assertFalse(withImage.missingFields.any { it.key == "request.stamp" })
    }

    @Test
    fun incompleteDraftStaysDraft() = runBlocking {
        val repository = seededRepository()

        val draft = (repository.createDraftFromZone(atmZone("ATM05"), 41.9, 12.5) as CreateAuthorizationDraftResult.Created).draft

        assertEquals(AuthorizationDraftStatuses.Draft, draft.status)
        assertTrue(draft.missingFields.isNotEmpty())
    }

    @Test
    fun completeDraftBecomesReady() = runBlocking {
        val repository = seededRepository()
        val draft = (repository.createDraftFromZone(atmZone("ATM05"), 41.9, 12.5) as CreateAuthorizationDraftResult.Created).draft

        val ready = repository.updateRequestData(draft.id, completeRequestData())

        assertEquals(AuthorizationDraftStatuses.Ready, ready?.status)
        assertTrue(ready?.missingFields?.isEmpty() == true)
    }

    @Test
    fun persistsAfterRepositoryReload() = runBlocking {
        val dao = FakeLocalPilotDao()
        val pilotRepository = seedPilotWorkspace(dao)
        val repository = LocalAuthorizationRepository(dao, pilotRepository, testAirportRepository())
        val draft = (repository.createDraftFromZone(atmZone("ATM09"), 41.9, 12.5) as CreateAuthorizationDraftResult.Created).draft

        val reloaded = LocalAuthorizationRepository(dao, LocalPilotRepository(dao), testAirportRepository()).getDraft(draft.id)

        assertEquals(draft.id, reloaded?.id)
        assertEquals("ATM09", reloaded?.procedureType)
    }

    @Test
    fun deletesDraft() = runBlocking {
        val repository = seededRepository()
        val draft = (repository.createDraftFromZone(atmZone("ATM05"), 41.9, 12.5) as CreateAuthorizationDraftResult.Created).draft

        repository.deleteDraft(draft.id)

        assertNull(repository.getDraft(draft.id))
    }

    @Test
    fun draftWithValidCoordinatesDoesNotMissAirportEnrichment() = runBlocking {
        val repository = seededRepository()

        val draft = (repository.createDraftFromZone(atmZone("ATM05"), 41.9, 12.5) as CreateAuthorizationDraftResult.Created).draft

        assertEquals("LIRA", draft.requestData.airportIcao)
        assertEquals("LIRA Roma", draft.requestData.airportName)
        assertEquals("13.8", draft.requestData.airportDistanceKm)
        assertEquals("7.4", draft.requestData.airportDistanceNm)
        assertFalse(draft.missingFields.any { it.key == "enrichment.airportName" })
        assertFalse(draft.missingFields.any { it.key == "enrichment.airportDistanceNm" })
        assertFalse(draft.missingFields.any { it.key == "enrichment.airportDistanceKm" })
    }

    @Test
    fun updatingTakeoffRecalculatesAirportEnrichment() = runBlocking {
        val repository = seededRepository()
        val draft = (repository.createDraftFromZone(atmZone("ATM05"), 41.9, 12.5) as CreateAuthorizationDraftResult.Created).draft

        val updated = repository.updateOperation(
            draft.id,
            AuthorizationOperationData(
                takeoffLat = 45.4642,
                takeoffLon = 9.19,
                requestedAltitudeMeters = 45
            )
        )

        assertEquals("LIML", updated?.requestData?.airportIcao)
        assertEquals("LIML Milano", updated?.requestData?.airportName)
        assertEquals("7.1", updated?.requestData?.airportDistanceKm)
        assertEquals("3.8", updated?.requestData?.airportDistanceNm)
    }

    @Test
    fun areaCenterFallbackEnrichesAirportWhenTakeoffIsMissing() = runBlocking {
        val repository = seededRepository()
        val draft = (repository.createDraftFromZone(atmZone("ATM05"), 41.9, 12.5) as CreateAuthorizationDraftResult.Created).draft

        val updated = repository.updateOperation(
            draft.id,
            AuthorizationOperationData(
                areaPoints = listOf(
                    AuthorizationGeoPoint(44.49, 11.32),
                    AuthorizationGeoPoint(44.50, 11.36)
                ),
                requestedAltitudeMeters = 45
            )
        )

        assertEquals("LIPE", updated?.requestData?.airportIcao)
        assertEquals("LIPE Bologna", updated?.requestData?.airportName)
        assertFalse(updated?.missingFields.orEmpty().any { it.key == "enrichment.airportName" })
    }

    @Test
    fun sameActiveZoneRequestResumesExistingDraftWithoutDuplicates() = runBlocking {
        val repository = seededRepository()
        val zone = atmZone("ATM05")

        val first = (repository.createDraftFromZone(zone) as CreateAuthorizationDraftResult.Created).draft
        val second = (repository.createDraftFromZone(zone) as CreateAuthorizationDraftResult.Created).draft

        assertEquals(first.id, second.id)
        assertEquals(1, repository.getDrafts().size)
    }

    @Test
    fun differentActiveZoneRequestReturnsConflictWithoutCreatingDuplicate() = runBlocking {
        val repository = seededRepository()
        val first = (repository.createDraftFromZone(atmZone("ATM05")) as CreateAuthorizationDraftResult.Created).draft

        val result = repository.createDraftFromZone(atmZone("ATM09").copy(id = "zone-2", name = "Milano ATZ"))

        assertTrue(result is CreateAuthorizationDraftResult.ActiveDraftConflict)
        assertEquals(first.id, (result as CreateAuthorizationDraftResult.ActiveDraftConflict).activeDraft.id)
        assertEquals(1, repository.getDrafts().size)
    }

    @Test
    fun cancelActiveDraftAllowsCreatingAnotherRequest() = runBlocking {
        val repository = seededRepository(certificateCategories = "STS_01")
        repository.createDraftFromZone(atmZone("ATM05"))

        repository.cancelActiveDraft()
        val next = repository.createDraftFromZone(atmZone("ATM09").copy(id = "zone-2", name = "Milano ATZ"))

        assertTrue(next is CreateAuthorizationDraftResult.Created)
        assertEquals("Milano ATZ", (next as CreateAuthorizationDraftResult.Created).draft.zoneName)
        assertEquals(1, repository.getDrafts().size)
    }

    @Test
    fun takeoffSelectionMovesDraftToAreaPlanning() = runBlocking {
        val repository = seededRepository()
        val draft = (repository.createDraftFromZone(atmZone("ATM05")) as CreateAuthorizationDraftResult.Created).draft

        val updated = repository.setTakeoff(draft.id, 41.9, 12.5)

        assertEquals(AuthorizationWorkflowSteps.Area, updated?.workflowStep)
        assertEquals(41.9, updated?.operationData?.takeoffLat ?: 0.0, 0.00001)
        assertEquals("41.90000 12.50000", updated?.requestData?.takeoff)
        assertEquals("LIRA", updated?.requestData?.airportIcao)
    }

    @Test
    fun finishAreaRequiresAtLeastThreeVertices() = runBlocking {
        val repository = seededRepository()
        val draft = (repository.createDraftFromZone(atmZone("ATM05")) as CreateAuthorizationDraftResult.Created).draft
        val withTakeoff = repository.setTakeoff(draft.id, 41.9, 12.5)!!
        repository.addAreaPoint(withTakeoff.id, 41.901, 12.501)
        repository.addAreaPoint(withTakeoff.id, 41.902, 12.502)

        val notFinished = repository.finishArea(withTakeoff.id)

        assertEquals(AuthorizationWorkflowSteps.Area, notFinished?.workflowStep)
        assertFalse(notFinished?.operationData?.areaClosed ?: true)
    }

    @Test
    fun undoRestartAndFinishAreaPersistLocalPlanningState() = runBlocking {
        val dao = FakeLocalPilotDao()
        val pilotRepository = seedPilotWorkspace(dao)
        val repository = LocalAuthorizationRepository(dao, pilotRepository, testAirportRepository())
        val draft = (repository.createDraftFromZone(atmZone("ATM05")) as CreateAuthorizationDraftResult.Created).draft
        val withTakeoff = repository.setTakeoff(draft.id, 41.9, 12.5)!!
        repository.addAreaPoint(withTakeoff.id, 41.901, 12.501)
        repository.addAreaPoint(withTakeoff.id, 41.902, 12.502)

        val afterUndo = repository.undoAreaPoint(withTakeoff.id)!!
        assertEquals(1, afterUndo.operationData.areaPoints.size)

        val afterRestart = repository.restartArea(withTakeoff.id)!!
        assertEquals(AuthorizationWorkflowSteps.Area, afterRestart.workflowStep)
        assertTrue(afterRestart.operationData.areaPoints.isEmpty())

        repository.addAreaPoint(withTakeoff.id, 41.901, 12.501)
        repository.addAreaPoint(withTakeoff.id, 41.902, 12.502)
        repository.addAreaPoint(withTakeoff.id, 41.903, 12.503)
        val finished = repository.finishArea(withTakeoff.id)!!
        val reloaded = LocalAuthorizationRepository(dao, LocalPilotRepository(dao), testAirportRepository()).getActiveDraft()

        assertEquals(AuthorizationWorkflowSteps.Form, finished.workflowStep)
        assertTrue(finished.operationData.areaClosed)
        assertEquals(listOf("Roma CTR"), finished.operationData.involvedZones)
        assertEquals("45 m", finished.requestData.verticalUpper)
        assertEquals(finished.id, reloaded?.id)
        assertEquals(3, reloaded?.operationData?.areaPoints?.size)
        assertTrue(reloaded?.operationData?.areaClosed == true)
    }

    @Test
    fun finishAreaUsesLowestOverlyingPositiveZoneAsUpperLimitExcludingRequestedZone() = runBlocking {
        val repository = seededRepository()
        val zone = atmZone("ATM05").copy(
            id = "li-p244",
            name = "LI P244 Roma",
            type = "P",
            limitMetersAgl = 0,
            verticalLimits = VerticalLimits(
                lower = "GND",
                upper = "0 m",
                lowerMetersAgl = 0,
                upperMetersAgl = 0
            )
        )
        val draft = (repository.createDraftFromZone(zone) as CreateAuthorizationDraftResult.Created).draft
        val withTakeoff = repository.setTakeoff(draft.id, 41.83, 12.58)!!
        val detected = listOf(
            AuthorizationZoneReference(id = "li-p244", name = "LI P244 Roma", type = "P", lowerLimitMeters = 0),
            AuthorizationZoneReference(id = "lira-15-33", name = "LIRA Roma/Ciampino 15/33", type = "ATM09_OTHER", lowerLimitMeters = 45),
            AuthorizationZoneReference(id = "lirr-ctr", name = "LIRR Roma CTR", type = "ATM09_CTR", lowerLimitMeters = 60)
        )

        repository.addAreaPoint(withTakeoff.id, 41.831, 12.581, detected)
        repository.addAreaPoint(withTakeoff.id, 41.832, 12.582, detected)
        repository.addAreaPoint(withTakeoff.id, 41.833, 12.583, detected)
        val finished = repository.finishArea(withTakeoff.id)!!

        assertEquals("45 m", finished.requestData.verticalUpper)
        assertTrue(finished.operationData.zoneAnalysisSummary.contains("LIRA Roma/Ciampino 15/33"))
        assertEquals(
            listOf("LI P244 Roma", "LIRA Roma/Ciampino 15/33", "LIRR Roma CTR"),
            finished.operationData.involvedZones
        )
    }

    private suspend fun seededRepository(certificateCategories: String = "A1_A3"): LocalAuthorizationRepository {
        val dao = FakeLocalPilotDao()
        seedPilotWorkspace(dao, certificateCategories)
        return LocalAuthorizationRepository(dao, LocalPilotRepository(dao), testAirportRepository())
    }

    private suspend fun seedPilotWorkspace(
        dao: FakeLocalPilotDao,
        certificateCategories: String = "A1_A3"
    ): LocalPilotRepository {
        val pilotRepository = LocalPilotRepository(dao)
        pilotRepository.saveProfile(
            LocalPilotProfile(
                firstName = "Raffaello",
                lastName = "Di Martino",
                city = "Roma",
                phone = "+3906000000",
                email = "raffa@example.com"
            )
        )
        pilotRepository.saveCertificate(
            LocalPilotCertificate(
                categories = certificateCategories,
                certificateNumber = "CERT-1",
                issuingAuthority = "ENAC",
                expiryDate = "2030-12-31"
            )
        )
        pilotRepository.saveOperator(
            LocalUasOperator(
                name = "Raffaello Di Martino",
                easaOperatorCode = "ITA1234567890",
                pec = "raffa@pec.it",
                insuranceCompany = "Assicurazione",
                insurancePolicyNumber = "POL-1",
                insuranceExpiresAt = "2030-12-31"
            )
        )
        pilotRepository.saveDrone(
            LocalDrone(
                manufacturer = "DJI",
                model = "Air 3S",
                classLabel = "C1",
                weight = 724.0,
                serialNumber = "ABC123",
                isSelected = true
            )
        )
        return pilotRepository
    }

    private fun atmZone(
        procedureType: String,
        resolutionStatus: String = "RESOLVED",
        applicability: String = "WHEN_ACTIVE",
        activeNow: Boolean = true
    ): ZoneInfo =
        ZoneInfo(
            id = "zone-1",
            name = "Roma CTR",
            family = "CTR",
            type = "ATM09_CTR",
            classification = "Controlled airspace",
            limitMetersAgl = 45,
            verticalLimits = VerticalLimits(
                lower = "GND",
                upper = "45 m",
                lowerMetersAgl = 0,
                upperMetersAgl = 45
            ),
            description = null,
            validity = ValidityInfo(
                activeNow = activeNow,
                validFrom = null,
                validTo = null,
                schedule = null,
                interpretedSchedule = null,
                explanation = null,
                future = null,
                expired = null
            ),
            authorization = AuthorizationInfo(
                required = null,
                requirement = null,
                operationMode = null,
                operationCategory = null,
                requiredLicense = null,
                explanation = null,
                applicability = applicability,
                resolutionStatus = resolutionStatus,
                procedures = listOf(
                    AuthorizationProcedure(
                        type = procedureType,
                        version = 1,
                        label = procedureType,
                        reasonCode = "TEST_REASON"
                    )
                ),
                reasonCodes = listOf("TEST_REASON"),
                resolverVersion = 1
            ),
            authority = AuthorityInfo(
                name = "ENAC",
                code = "ENAC",
                contact = "protocollo@pec.enac.gov.it",
                source = "test"
            ),
            authorizationRequired = true,
            activeNow = activeNow
        )

    private fun enteParcoOnlyZone(): ZoneInfo =
        atmZone("ATM05").copy(
            authorization = AuthorizationInfo(
                required = null,
                requirement = null,
                operationMode = null,
                operationCategory = null,
                requiredLicense = null,
                explanation = null,
                applicability = "WHEN_ACTIVE",
                resolutionStatus = "RESOLVED",
                procedures = emptyList(),
                additionalRequirements = listOf(
                    AuthorizationAdditionalRequirement(
                        type = "ENTE_PARCO",
                        label = "Ente Parco",
                        reasonCode = "PROTECTED_AREA_ENTE_PARCO"
                    )
                ),
                reasonCodes = listOf("PROTECTED_AREA_ENTE_PARCO"),
                resolverVersion = 1
            )
        )

    private fun completeRequestData(): AuthorizationRequestData =
        AuthorizationRequestData(
            requester = "Raffaello Di Martino - Att: A1/A3 - EASA: ITA1234567890",
            name = "Raffaello Di Martino",
            license = "A1/A3",
            easaOperatorCode = "ITA1234567890",
            phone = "+3906000000",
            contactEmail = "raffa@pec.it",
            activityType = "Foto e video senza scopo di lucro - VLOS",
            aircraftType = "DJI Air 3S (C1)",
            selectedDroneId = "drone-1",
            zoneName = "Roma CTR",
            takeoff = "Via Roma - 41.90000 12.50000",
            landing = "Via Roma - 41.90000 12.50000",
            areaDescription = "Area operativa in prossimita di Roma",
            verticalLower = "0 m (GND)",
            verticalUpper = "45 m",
            operationStartDateTime = "2026-08-10T10:00",
            operationEndDateTime = "2026-08-10T12:00",
            stampNumber = "MB-123",
            airportIcao = "LIRA",
            airportName = "LIRA Roma",
            airportCity = "Roma",
            airportDistanceNm = "7.4",
            airportDistanceKm = "13.8",
            notes = "VLOS"
        )

    private fun testAirportRepository(): AirportRepository =
        AirportRepository { airportAssetJson() }

    private fun airportAssetJson(): String {
        val candidates = listOf(
            File("app/src/main/assets/icao-it.json"),
            File("src/main/assets/icao-it.json")
        )
        return candidates.first { it.exists() }.readText(Charsets.UTF_8)
    }
}
