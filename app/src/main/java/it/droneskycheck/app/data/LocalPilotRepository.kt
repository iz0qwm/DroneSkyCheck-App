package it.droneskycheck.app.data

import android.content.Context
import java.util.UUID

interface LocalPilotStore {
    suspend fun getDrones(): List<LocalDrone>
    suspend fun getSelectedDrone(): LocalDrone?
    suspend fun selectDrone(id: String)
}

class LocalPilotRepository(
    private val dao: LocalPilotDao
) : LocalPilotStore {
    constructor(context: Context) : this(
        LocalPilotDatabase.getInstance(context).localPilotDao()
    )

    suspend fun getSnapshot(): LocalPilotSnapshot =
        LocalPilotSnapshot(
            profile = getProfile(),
            certificates = getCertificates(),
            operator = getOperator(),
            drones = getDrones()
        )

    suspend fun getProfile(): LocalPilotProfile? =
        dao.getProfileEntity()?.toModel()

    suspend fun saveProfile(profile: LocalPilotProfile): LocalPilotProfile {
        val existing = dao.getProfileEntity()
        val now = System.currentTimeMillis()
        val entity = PilotProfileEntity(
            firstName = profile.firstName.trim(),
            lastName = profile.lastName.trim(),
            city = profile.city.trim(),
            phone = profile.phone.trim(),
            email = profile.email.trim(),
            profilePhoto = profile.profilePhoto.trim(),
            skipPilotCompetencyChecks = profile.skipPilotCompetencyChecks,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        dao.upsertProfile(entity)
        return entity.toModel()
    }

    suspend fun getCertificates(): List<LocalPilotCertificate> =
        dao.getCertificateEntities().map { it.toModel() }

    suspend fun saveCertificate(certificate: LocalPilotCertificate): LocalPilotCertificate {
        val id = certificate.id.ifBlank { localId("pilot-certificate") }
        val now = System.currentTimeMillis()
        val entity = PilotCertificateEntity(
            id = id,
            issuingAuthority = certificate.issuingAuthority.trim(),
            certificateNumber = certificate.certificateNumber.trim(),
            issueDate = certificate.issueDate.trim(),
            expiryDate = certificate.expiryDate.trim(),
            categories = normalizeCategoryText(certificate.categories),
            notes = certificate.notes,
            createdAt = now,
            updatedAt = now
        )
        dao.upsertCertificate(entity)
        return entity.toModel()
    }

    suspend fun deleteCertificate(id: String) {
        dao.deleteCertificateById(id)
    }

    suspend fun getOperator(): LocalUasOperator? =
        dao.getOperatorEntity()?.toModel()

    suspend fun saveOperator(operator: LocalUasOperator): LocalUasOperator {
        val existing = dao.getOperatorEntity()
        val now = System.currentTimeMillis()
        val entity = UasOperatorEntity(
            type = normalizeOperatorType(operator.type),
            name = operator.name.trim(),
            easaOperatorCode = normalizeEasaOperatorCode(operator.easaOperatorCode),
            pec = operator.pec.trim().lowercase(),
            insuranceCompany = operator.insuranceCompany.trim(),
            insurancePolicyNumber = operator.insurancePolicyNumber.trim(),
            insuranceExpiresAt = operator.insuranceExpiresAt.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        dao.upsertOperator(entity)
        return entity.toModel()
    }

    override suspend fun getDrones(): List<LocalDrone> =
        dao.getDroneEntities().map { it.toModel() }

    override suspend fun getSelectedDrone(): LocalDrone? =
        getDrones().firstOrNull { it.isSelected } ?: getDrones().firstOrNull()

    suspend fun saveDrone(drone: LocalDrone): LocalDrone {
        val id = drone.id.ifBlank { localId("drone") }
        val previous = dao.getDroneEntity(id)
        val now = System.currentTimeMillis()
        val entity = LocalDroneEntity(
            id = id,
            manufacturer = drone.manufacturer.trim(),
            model = drone.model.trim(),
            classLabel = normalizeDroneClass(drone.classLabel),
            weight = drone.weight,
            manualMaxWindResistanceMs = drone.manualMaxWindResistanceMs,
            serialNumber = drone.serialNumber.trim(),
            remoteControllers = normalizeSerials(drone.remoteControllers),
            batteries = normalizeSerials(drone.batteries),
            cameras = normalizeSerials(drone.cameras),
            remoteId = drone.remoteId,
            euSts01Registered = drone.euSts01Registered && normalizeDroneClass(drone.classLabel) == "C5",
            euSts01DeclarationDate = drone.euSts01DeclarationDate.trim(),
            euSts02Registered = drone.euSts02Registered && normalizeDroneClass(drone.classLabel) == "C6",
            euSts02DeclarationDate = drone.euSts02DeclarationDate.trim(),
            notes = drone.notes.trim(),
            isSelected = drone.isSelected,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now
        )
        if (entity.isSelected) dao.clearSelectedDrone()
        dao.upsertDrone(entity)
        return entity.toModel()
    }

    suspend fun deleteDrone(id: String) {
        val wasSelected = dao.getDroneEntity(id)?.isSelected == true
        dao.deleteDroneById(id)
        if (wasSelected) {
            dao.getDroneEntities().firstOrNull()?.let {
                selectDrone(it.id)
            }
        }
    }

    override suspend fun selectDrone(id: String) {
        dao.clearSelectedDrone()
        dao.setSelectedDrone(id)
    }

    private fun localId(prefix: String): String =
        "$prefix-${UUID.randomUUID()}"
}

class InMemoryLocalPilotStore(
    initialDrones: List<LocalDrone> = emptyList()
) : LocalPilotStore {
    private var drones: List<LocalDrone> = normalizeSelection(initialDrones)

    override suspend fun getDrones(): List<LocalDrone> = drones

    override suspend fun getSelectedDrone(): LocalDrone? =
        drones.firstOrNull { it.isSelected } ?: drones.firstOrNull()

    override suspend fun selectDrone(id: String) {
        drones = drones.map { drone ->
            drone.copy(isSelected = drone.id == id)
        }
    }

    private fun normalizeSelection(input: List<LocalDrone>): List<LocalDrone> {
        if (input.isEmpty() || input.any { it.isSelected }) return input
        return input.mapIndexed { index, drone -> drone.copy(isSelected = index == 0) }
    }
}

private fun PilotProfileEntity.toModel(): LocalPilotProfile =
    LocalPilotProfile(
        firstName = firstName,
        lastName = lastName,
        city = city,
        phone = phone,
        email = email,
        profilePhoto = profilePhoto,
        skipPilotCompetencyChecks = skipPilotCompetencyChecks
    )

private fun PilotCertificateEntity.toModel(): LocalPilotCertificate =
    LocalPilotCertificate(
        id = id,
        issuingAuthority = issuingAuthority,
        certificateNumber = certificateNumber,
        issueDate = issueDate,
        expiryDate = expiryDate,
        categories = categories,
        notes = notes
    )

private fun UasOperatorEntity.toModel(): LocalUasOperator =
    LocalUasOperator(
        name = name,
        type = type,
        easaOperatorCode = easaOperatorCode,
        pec = pec,
        insuranceCompany = insuranceCompany,
        insurancePolicyNumber = insurancePolicyNumber,
        insuranceExpiresAt = insuranceExpiresAt
    )

private fun LocalDroneEntity.toModel(): LocalDrone =
    LocalDrone(
        id = id,
        manufacturer = manufacturer,
        model = model,
        classLabel = classLabel,
        weight = weight,
        manualMaxWindResistanceMs = manualMaxWindResistanceMs,
        serialNumber = serialNumber,
        remoteControllers = remoteControllers,
        batteries = batteries,
        cameras = cameras,
        remoteId = remoteId,
        euSts01Registered = euSts01Registered,
        euSts01DeclarationDate = euSts01DeclarationDate,
        euSts02Registered = euSts02Registered,
        euSts02DeclarationDate = euSts02DeclarationDate,
        notes = notes,
        isSelected = isSelected
    )

private fun normalizeCategoryText(value: String): String =
    value.split('\n', ',', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n") { normalizeCertificateCategory(it) }

private fun normalizeCertificateCategory(value: String): String {
    val normalized = value.trim().uppercase().replace('-', '_').replace('/', '_')
    return when (normalized) {
        "A1_A3" -> "A1_A3"
        "A2" -> "A2"
        "EU_STS_01", "STS_01" -> "STS_01"
        "EU_STS_02", "STS_02" -> "STS_02"
        else -> normalized.replace(Regex("[^A-Z0-9_]+"), "_")
    }
}

private fun normalizeOperatorType(value: String): String =
    when (value) {
        LocalOperatorTypes.Association,
        LocalOperatorTypes.Company,
        LocalOperatorTypes.PublicBody -> value
        else -> LocalOperatorTypes.Individual
    }

private fun normalizeEasaOperatorCode(value: String): String {
    val compact = value.trim().replace(Regex("\\s+"), "")
    return if (Regex("^.+-[A-Za-z0-9]{3}$").matches(compact)) {
        ""
    } else {
        compact.uppercase()
    }
}

private fun normalizeDroneClass(value: String): String {
    val normalized = value.trim().uppercase().replace('-', '_').replace(' ', '_')
    return when (normalized) {
        "C0", "C1", "C2", "C3", "C4", "C5", "C6" -> normalized
        "LEGACY" -> "LEGACY"
        "PRIVATELY_BUILT" -> "PRIVATELY_BUILT"
        "UNMARKED" -> "UNMARKED"
        else -> ""
    }
}

private fun normalizeSerials(value: String): String =
    value.split('\n', ',', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .joinToString("\n")
