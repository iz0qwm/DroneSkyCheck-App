package it.droneskycheck.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPilotRepositoryTest {
    @Test
    fun createsProfile() = runBlocking {
        val repository = LocalPilotRepository(FakeLocalPilotDao())

        val profile = repository.saveProfile(
            LocalPilotProfile(firstName = "Raffaello", lastName = "Di Martino", email = "raffa@example.com")
        )

        assertEquals("Raffaello Di Martino", profile.displayName)
        assertEquals("raffa@example.com", repository.getProfile()?.email)
    }

    @Test
    fun updatesProfile() = runBlocking {
        val repository = LocalPilotRepository(FakeLocalPilotDao())
        repository.saveProfile(LocalPilotProfile(firstName = "Raffaello", lastName = "Di Martino"))

        repository.saveProfile(LocalPilotProfile(firstName = "Raffa", lastName = "Di Martino", city = "Roma"))

        val profile = repository.getProfile()
        assertEquals("Raffa", profile?.firstName)
        assertEquals("Roma", profile?.city)
    }

    @Test
    fun persistsProfileAcrossRepositoryReload() = runBlocking {
        val dao = FakeLocalPilotDao()
        LocalPilotRepository(dao).saveProfile(LocalPilotProfile(firstName = "Raffaello"))

        val reloaded = LocalPilotRepository(dao).getProfile()

        assertEquals("Raffaello", reloaded?.firstName)
    }

    @Test
    fun addsCertificate() = runBlocking {
        val repository = LocalPilotRepository(FakeLocalPilotDao())

        val certificate = repository.saveCertificate(
            LocalPilotCertificate(categories = "A1/A3", certificateNumber = "A13-001")
        )

        assertTrue(certificate.id.isNotBlank())
        assertEquals("A1_A3", repository.getCertificates().single().categories)
    }

    @Test
    fun updatesCertificate() = runBlocking {
        val repository = LocalPilotRepository(FakeLocalPilotDao())
        val certificate = repository.saveCertificate(LocalPilotCertificate(categories = "A2"))

        repository.saveCertificate(certificate.copy(certificateNumber = "A2-002"))

        assertEquals("A2-002", repository.getCertificates().single().certificateNumber)
    }

    @Test
    fun deletesCertificate() = runBlocking {
        val repository = LocalPilotRepository(FakeLocalPilotDao())
        val certificate = repository.saveCertificate(LocalPilotCertificate(categories = "STS-01"))

        repository.deleteCertificate(certificate.id)

        assertTrue(repository.getCertificates().isEmpty())
    }

    @Test
    fun savesUasOperator() = runBlocking {
        val repository = LocalPilotRepository(FakeLocalPilotDao())

        val operator = repository.saveOperator(
            LocalUasOperator(
                name = "Raffaello Di Martino",
                easaOperatorCode = " ita 1234567890 ",
                pec = "RAFFA@PEC.IT",
                insuranceCompany = "Assicurazione",
                insurancePolicyNumber = "POL-1"
            )
        )

        assertEquals("ITA1234567890", operator.easaOperatorCode)
        assertEquals("raffa@pec.it", repository.getOperator()?.pec)
    }

    @Test
    fun addsDrone() = runBlocking {
        val repository = LocalPilotRepository(FakeLocalPilotDao())

        val drone = repository.saveDrone(
            LocalDrone(manufacturer = "DJI", model = "Air 3S", classLabel = "C1", weight = 724.0)
        )

        assertTrue(drone.id.isNotBlank())
        assertEquals("DJI Air 3S", repository.getDrones().single().displayName)
    }

    @Test
    fun updatesDrone() = runBlocking {
        val repository = LocalPilotRepository(FakeLocalPilotDao())
        val drone = repository.saveDrone(LocalDrone(manufacturer = "DJI", model = "Mini", classLabel = "C0"))

        repository.saveDrone(drone.copy(model = "Mini 4 Pro", serialNumber = "SN-1"))

        val saved = repository.getDrones().single()
        assertEquals("Mini 4 Pro", saved.model)
        assertEquals("SN-1", saved.serialNumber)
    }

    @Test
    fun deletesDrone() = runBlocking {
        val repository = LocalPilotRepository(FakeLocalPilotDao())
        val drone = repository.saveDrone(LocalDrone(manufacturer = "DJI", model = "Air 3S", classLabel = "C1"))

        repository.deleteDrone(drone.id)

        assertTrue(repository.getDrones().isEmpty())
    }

    @Test
    fun selectsDefaultDrone() = runBlocking {
        val repository = LocalPilotRepository(FakeLocalPilotDao())
        val first = repository.saveDrone(LocalDrone(manufacturer = "DJI", model = "Mini", classLabel = "C0"))
        val second = repository.saveDrone(LocalDrone(manufacturer = "DJI", model = "Air 3S", classLabel = "C1"))

        repository.selectDrone(second.id)

        assertFalse(repository.getDrones().first { it.id == first.id }.isSelected)
        assertEquals(second.id, repository.getSelectedDrone()?.id)
    }

    @Test
    fun reloadRepositoryKeepsOperatorCertificatesAndSelectedDrone() = runBlocking {
        val dao = FakeLocalPilotDao()
        val repository = LocalPilotRepository(dao)
        repository.saveOperator(LocalUasOperator(name = "Operatore locale"))
        repository.saveCertificate(LocalPilotCertificate(categories = "A2"))
        val drone = repository.saveDrone(LocalDrone(manufacturer = "DJI", model = "Mavic", classLabel = "C1", isSelected = true))

        val reloaded = LocalPilotRepository(dao)

        assertEquals("Operatore locale", reloaded.getOperator()?.name)
        assertEquals("A2", reloaded.getCertificates().single().categories)
        assertEquals(drone.id, reloaded.getSelectedDrone()?.id)
    }

    @Test
    fun secretSuffixOperatorCodeIsRejectedLikeWebFreeModel() = runBlocking {
        val repository = LocalPilotRepository(FakeLocalPilotDao())

        val operator = repository.saveOperator(LocalUasOperator(easaOperatorCode = "ITA123-ABC"))

        assertEquals("", operator.easaOperatorCode)
    }

    @Test
    fun returnsNullSelectedDroneWhenFleetIsEmpty() = runBlocking {
        val repository = LocalPilotRepository(FakeLocalPilotDao())

        assertNull(repository.getSelectedDrone())
    }

    @Test
    fun replaceLocalProfileDataClearsOldDataAndAvoidsDuplicates() = runBlocking {
        val dao = FakeLocalPilotDao()
        val repository = LocalPilotRepository(dao)
        repository.saveProfile(LocalPilotProfile(firstName = "Vecchio"))
        repository.saveCertificate(LocalPilotCertificate(categories = "A1_A3"))
        repository.saveDrone(LocalDrone(manufacturer = "DJI", model = "Old", classLabel = "C0"))

        val replacementProfile = PilotProfileEntity(firstName = "Nuovo")
        val replacementCertificate = PilotCertificateEntity(id = "cert-new", categories = "A2")
        val replacementDrone = LocalDroneEntity(id = "drone-new", manufacturer = "DJI", model = "New", classLabel = "C1")
        dao.replaceLocalProfileData(
            profile = replacementProfile,
            certificates = listOf(replacementCertificate),
            operator = null,
            drones = listOf(replacementDrone),
            authorizationDrafts = emptyList()
        )
        dao.replaceLocalProfileData(
            profile = replacementProfile,
            certificates = listOf(replacementCertificate),
            operator = null,
            drones = listOf(replacementDrone),
            authorizationDrafts = emptyList()
        )

        assertEquals("Nuovo", repository.getProfile()?.firstName)
        assertEquals(listOf("cert-new"), dao.getCertificateEntities().map { it.id })
        assertEquals(listOf("drone-new"), dao.getDroneEntities().map { it.id })
    }
}

internal class FakeLocalPilotDao : LocalPilotDao {
    private var profile: PilotProfileEntity? = null
    private var operator: UasOperatorEntity? = null
    private val certificates = linkedMapOf<String, PilotCertificateEntity>()
    private val drones = linkedMapOf<String, LocalDroneEntity>()
    private val drafts = linkedMapOf<String, AuthorizationDraftEntity>()
    private val cachedZoneAnalyses = linkedMapOf<String, CachedZoneAnalysisEntity>()

    override suspend fun getProfileEntity(id: String): PilotProfileEntity? =
        profile?.takeIf { it.id == id }

    override suspend fun upsertProfile(entity: PilotProfileEntity) {
        profile = entity
    }

    override suspend fun clearProfiles() {
        profile = null
    }

    override suspend fun getCertificateEntities(): List<PilotCertificateEntity> =
        certificates.values.sortedWith(compareBy<PilotCertificateEntity> { it.expiryDate }.thenBy { it.createdAt })

    override suspend fun upsertCertificate(entity: PilotCertificateEntity) {
        certificates[entity.id] = entity
    }

    override suspend fun upsertCertificates(entities: List<PilotCertificateEntity>) {
        entities.forEach { upsertCertificate(it) }
    }

    override suspend fun deleteCertificateById(id: String) {
        certificates.remove(id)
    }

    override suspend fun clearCertificates() {
        certificates.clear()
    }

    override suspend fun getOperatorEntity(id: String): UasOperatorEntity? =
        operator?.takeIf { it.id == id }

    override suspend fun upsertOperator(entity: UasOperatorEntity) {
        operator = entity
    }

    override suspend fun clearOperators() {
        operator = null
    }

    override suspend fun getDroneEntities(): List<LocalDroneEntity> =
        drones.values
            .filter { it.status != "deleted" }
            .sortedWith(compareByDescending<LocalDroneEntity> { it.isSelected }.thenBy { it.manufacturer }.thenBy { it.model })

    override suspend fun getDroneEntity(id: String): LocalDroneEntity? =
        drones[id]

    override suspend fun upsertDrone(entity: LocalDroneEntity) {
        drones[entity.id] = entity
    }

    override suspend fun upsertDrones(entities: List<LocalDroneEntity>) {
        entities.forEach { upsertDrone(it) }
    }

    override suspend fun deleteDroneById(id: String) {
        drones.remove(id)
    }

    override suspend fun clearDrones() {
        drones.clear()
    }

    override suspend fun clearSelectedDrone() {
        drones.replaceAll { _, drone -> drone.copy(isSelected = false) }
    }

    override suspend fun setSelectedDrone(id: String) {
        drones[id]?.let { drones[id] = it.copy(isSelected = true) }
    }

    override suspend fun getAuthorizationDraftEntities(): List<AuthorizationDraftEntity> =
        drafts.values.sortedByDescending { it.updatedAt }

    override suspend fun getAuthorizationDraftEntity(id: String): AuthorizationDraftEntity? =
        drafts[id]

    override suspend fun upsertAuthorizationDraft(entity: AuthorizationDraftEntity) {
        drafts[entity.id] = entity
    }

    override suspend fun upsertAuthorizationDrafts(entities: List<AuthorizationDraftEntity>) {
        entities.forEach { upsertAuthorizationDraft(it) }
    }

    override suspend fun deleteAuthorizationDraftById(id: String) {
        drafts.remove(id)
    }

    override suspend fun clearAuthorizationDrafts() {
        drafts.clear()
    }

    override suspend fun getCachedZoneAnalysis(
        normalizedLat: String,
        normalizedLon: String
    ): CachedZoneAnalysisEntity? =
        cachedZoneAnalyses["$normalizedLat,$normalizedLon"]

    override suspend fun upsertCachedZoneAnalysis(entity: CachedZoneAnalysisEntity) {
        cachedZoneAnalyses[entity.id] = entity
    }

    override suspend fun trimCachedZoneAnalyses(keep: Int) {
        val keepIds = cachedZoneAnalyses.values
            .sortedByDescending { it.analyzedAtUtc }
            .take(keep)
            .map { it.id }
            .toSet()
        cachedZoneAnalyses.keys.retainAll(keepIds)
    }
}
