package it.droneskycheck.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

sealed class CreateAuthorizationDraftResult {
    data class Created(val draft: AuthorizationDraft) : CreateAuthorizationDraftResult()
    data class Unsupported(val reason: String) : CreateAuthorizationDraftResult()
    data class ProcedureSelectionRequired(val procedures: List<String>) : CreateAuthorizationDraftResult()
    data class ActiveDraftConflict(val activeDraft: AuthorizationDraft) : CreateAuthorizationDraftResult()
}

class LocalAuthorizationRepository(
    private val dao: LocalPilotDao,
    private val localPilotRepository: LocalPilotRepository,
    private val airportRepository: AirportRepository = AirportRepository(emptyList())
) {
    constructor(context: Context) : this(
        LocalPilotDatabase.getInstance(context).localPilotDao(),
        LocalPilotRepository(context),
        AirportRepository(context)
    )

    suspend fun createDraftFromZone(
        zone: ZoneInfo,
        lat: Double? = null,
        lon: Double? = null,
        requestedProcedureType: String? = null
    ): CreateAuthorizationDraftResult {
        val authorization = zone.authorization
            ?: return CreateAuthorizationDraftResult.Unsupported("missing_authorization")
        if (authorization.resolutionStatus != "RESOLVED") {
            return CreateAuthorizationDraftResult.Unsupported("authorization_not_resolved")
        }

        val supportedProcedures = authorization.procedures
            .mapNotNull { it.type?.uppercase()?.takeIf { type -> type == "ATM05" || type == "ATM09" } }
            .distinct()
        if (supportedProcedures.isEmpty()) {
            return CreateAuthorizationDraftResult.Unsupported("no_supported_document_procedure")
        }
        if (supportedProcedures.size > 1 && requestedProcedureType == null) {
            return CreateAuthorizationDraftResult.ProcedureSelectionRequired(supportedProcedures)
        }

        val procedureType = requestedProcedureType?.uppercase()?.takeIf { it in supportedProcedures }
            ?: supportedProcedures.single()

        getActiveDraftEntity()?.let { active ->
            return if (active.matches(zone, procedureType)) {
                CreateAuthorizationDraftResult.Created(active.toModel())
            } else {
                CreateAuthorizationDraftResult.ActiveDraftConflict(active.toModel())
            }
        }

        val procedure = authorization.procedures.firstOrNull { it.type?.uppercase() == procedureType }
        val snapshot = localPilotRepository.getSnapshot()
        val selectedDrone = snapshot.selectedDrone
        val selectedLicense = selectCertificateLabel(snapshot.certificates, procedureType)
        val operationData = enrichOperationData(
            AuthorizationOperationData(
                takeoffLat = lat,
                takeoffLon = lon,
                workflowStep = if (lat != null && lon != null) {
                    AuthorizationWorkflowSteps.Area
                } else {
                    AuthorizationWorkflowSteps.Takeoff
                },
                requestedAltitudeMeters = zone.limitMetersAgl
            )
        )
        val requestData = enrichRequestData(
            buildInitialRequestData(
                procedureType = procedureType,
                zone = zone,
                profile = snapshot.profile,
                operator = snapshot.operator,
                drone = selectedDrone,
                license = selectedLicense,
                lat = lat,
                lon = lon
            ),
            operationData
        )
        val validation = AuthorizationDraftValidator.validate(procedureType, requestData)
        val now = System.currentTimeMillis()
        val entity = AuthorizationDraftEntity(
            id = "authorization-draft-${UUID.randomUUID()}",
            procedureType = procedureType,
            procedureVersion = procedure?.version ?: 1,
            status = validation.status,
            zoneSnapshotJson = buildZoneSnapshot(zone, procedureType, procedure).toString(),
            operationDataJson = operationData.toJson(),
            pilotSnapshotJson = buildPilotSnapshot(snapshot.profile).toString(),
            operatorSnapshotJson = buildOperatorSnapshot(snapshot.operator).toString(),
            certificateSnapshotJson = buildCertificateSnapshot(snapshot.certificates).toString(),
            droneSnapshotJson = buildDroneSnapshot(selectedDrone).toString(),
            requestDataJson = requestData.toJson(),
            missingFieldsJson = validation.missingFields.toMissingFieldsJson(),
            createdAt = now,
            updatedAt = now
        )
        dao.upsertAuthorizationDraft(entity)
        return CreateAuthorizationDraftResult.Created(entity.toModel())
    }

    suspend fun getDraft(id: String): AuthorizationDraft? =
        dao.getAuthorizationDraftEntity(id)?.toModel()

    suspend fun getDrafts(): List<AuthorizationDraft> =
        dao.getAuthorizationDraftEntities().map { it.toModel() }

    suspend fun getActiveDraft(): AuthorizationDraft? =
        getActiveDraftEntity()?.toModel()

    suspend fun updateOperation(
        id: String,
        operationData: AuthorizationOperationData
    ): AuthorizationDraft? {
        val current = dao.getAuthorizationDraftEntity(id) ?: return null
        val currentRequest = AuthorizationRequestData.fromJson(current.requestDataJson)
        val enrichedOperation = enrichOperationData(operationData)
        val nextRequest = currentRequest.copy(
            operationStartDateTime = enrichedOperation.operationStartDateTime,
            operationEndDateTime = enrichedOperation.operationEndDateTime
        )
        return saveWithRequestData(
            current.copy(operationDataJson = enrichedOperation.toJson()),
            enrichRequestData(nextRequest, enrichedOperation)
        )
    }

    suspend fun updateRequestData(
        id: String,
        requestData: AuthorizationRequestData
    ): AuthorizationDraft? {
        val current = dao.getAuthorizationDraftEntity(id) ?: return null
        val enrichedOperation = enrichOperationData(AuthorizationOperationData.fromJson(current.operationDataJson))
        return saveWithRequestData(
            current.copy(operationDataJson = enrichedOperation.toJson()),
            enrichRequestData(requestData, enrichedOperation)
        )
    }

    suspend fun validateDraft(id: String): AuthorizationDraft? {
        val current = dao.getAuthorizationDraftEntity(id) ?: return null
        val enrichedOperation = enrichOperationData(AuthorizationOperationData.fromJson(current.operationDataJson))
        val requestData = enrichRequestData(
            AuthorizationRequestData.fromJson(current.requestDataJson),
            enrichedOperation
        )
        return saveWithRequestData(
            current.copy(operationDataJson = enrichedOperation.toJson()),
            requestData
        )
    }

    suspend fun deleteDraft(id: String) {
        dao.deleteAuthorizationDraftById(id)
    }

    suspend fun cancelActiveDraft() {
        getActiveDraftEntity()?.let { dao.deleteAuthorizationDraftById(it.id) }
    }

    suspend fun setTakeoff(
        id: String,
        lat: Double,
        lon: Double
    ): AuthorizationDraft? {
        val current = dao.getAuthorizationDraftEntity(id) ?: return null
        val operationData = AuthorizationOperationData.fromJson(current.operationDataJson)
        return updateOperation(
            id,
            operationData.copy(
                takeoffLat = lat,
                takeoffLon = lon,
                areaClosed = false,
                workflowStep = AuthorizationWorkflowSteps.Area
            )
        )
    }

    suspend fun addAreaPoint(
        id: String,
        lat: Double,
        lon: Double,
        detectedZones: List<AuthorizationZoneReference> = emptyList()
    ): AuthorizationDraft? {
        val current = dao.getAuthorizationDraftEntity(id) ?: return null
        val operationData = AuthorizationOperationData.fromJson(current.operationDataJson)
        if (operationData.areaClosed) return current.toModel()
        return updateOperation(
            id,
            operationData.copy(
                areaPoints = operationData.areaPoints + AuthorizationGeoPoint(lat, lon),
                detectedZones = (operationData.detectedZones + detectedZones).distinctBy { it.identityKey() },
                workflowStep = AuthorizationWorkflowSteps.Area
            )
        )
    }

    suspend fun undoAreaPoint(id: String): AuthorizationDraft? {
        val current = dao.getAuthorizationDraftEntity(id) ?: return null
        val operationData = AuthorizationOperationData.fromJson(current.operationDataJson)
        return updateOperation(
            id,
            operationData.copy(
                areaPoints = operationData.areaPoints.dropLast(1),
                areaClosed = false,
                workflowStep = AuthorizationWorkflowSteps.Area
            )
        )
    }

    suspend fun restartArea(id: String): AuthorizationDraft? {
        val current = dao.getAuthorizationDraftEntity(id) ?: return null
        val operationData = AuthorizationOperationData.fromJson(current.operationDataJson)
        return updateOperation(
            id,
            operationData.copy(
                areaPoints = emptyList(),
                areaClosed = false,
                zoneAnalysisSummary = "",
                calculatedLowerLimitMeters = null,
                calculatedUpperLimitMeters = null,
                involvedZones = emptyList(),
                detectedZones = emptyList(),
                workflowStep = if (operationData.takeoffLat != null && operationData.takeoffLon != null) {
                    AuthorizationWorkflowSteps.Area
                } else {
                    AuthorizationWorkflowSteps.Takeoff
                }
            )
        )
    }

    suspend fun finishArea(id: String): AuthorizationDraft? {
        val current = dao.getAuthorizationDraftEntity(id) ?: return null
        val operationData = AuthorizationOperationData.fromJson(current.operationDataJson)
        if (operationData.areaPoints.size < 3) return current.toModel()

        val zoneName = JSONObject(current.zoneSnapshotJson).optString("name")
        val upperZone = operationData.detectedZones
            .filterNot { it.matchesZoneSnapshot(current.zoneSnapshotJson) }
            .filter { (it.lowerLimitMeters ?: 0) > 0 }
            .minByOrNull { it.lowerLimitMeters ?: Int.MAX_VALUE }
        val fallbackUpper = operationData.requestedAltitudeMeters
            ?.takeIf { it > 0 }
            ?: 120
        val calculatedUpper = upperZone?.lowerLimitMeters ?: fallbackUpper
        val involvedZones = operationData.detectedZones
            .map { it.name }
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf(zoneName).filter { it.isNotBlank() } }
        return updateOperation(
            id,
            operationData.copy(
                areaClosed = true,
                workflowStep = AuthorizationWorkflowSteps.Form,
                zoneAnalysisSummary = upperZone?.name
                    ?.let { "Limite superiore impostato dalla zona sovrapposta: $it." }
                    ?: "Limite superiore impostato dal profilo della zona richiesta.",
                calculatedLowerLimitMeters = 0,
                calculatedUpperLimitMeters = calculatedUpper,
                involvedZones = involvedZones
            )
        )
    }

    private fun enrichOperationData(
        operationData: AuthorizationOperationData
    ): AuthorizationOperationData {
        val nearest = airportRepository.findNearestAirport(
            takeoff = operationData.takeoffPoint(),
            area = operationData.areaPoints.map { AirportPoint(it.lat, it.lon) }
        )

        return if (nearest == null) {
            operationData.copy(
                airportIcao = "",
                airportName = "",
                airportCity = "",
                airportDistanceKm = null,
                airportDistanceNm = null
            )
        } else {
            operationData.copy(
                airportIcao = nearest.airport.icao,
                airportName = nearest.airport.name,
                airportCity = nearest.airport.city,
                airportDistanceKm = nearest.distanceKm,
                airportDistanceNm = nearest.distanceNm
            )
        }
    }

    private suspend fun saveWithRequestData(
        current: AuthorizationDraftEntity,
        requestData: AuthorizationRequestData
    ): AuthorizationDraft {
        val validation = AuthorizationDraftValidator.validate(current.procedureType, requestData)
        val entity = current.copy(
            status = validation.status,
            requestDataJson = requestData.toJson(),
            missingFieldsJson = validation.missingFields.toMissingFieldsJson(),
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertAuthorizationDraft(entity)
        return entity.toModel()
    }

    private suspend fun getActiveDraftEntity(): AuthorizationDraftEntity? =
        dao.getAuthorizationDraftEntities()
            .firstOrNull { it.status == AuthorizationDraftStatuses.Draft || it.status == AuthorizationDraftStatuses.Ready }
}

private fun AuthorizationDraftEntity.matches(zone: ZoneInfo, procedureType: String): Boolean {
    val snapshot = JSONObject(zoneSnapshotJson)
    val zoneId = snapshot.optString("id")
    val zoneName = snapshot.optString("name")
    return this.procedureType.equals(procedureType, ignoreCase = true) &&
        (
            zoneId.isNotBlank() && zoneId == zone.id ||
                zoneName.isNotBlank() && zoneName == zone.name
            )
}

private fun AuthorizationZoneReference.identityKey(): String =
    listOf(id, name, type)
        .joinToString("|")
        .trim()
        .lowercase(Locale.ROOT)

private fun AuthorizationZoneReference.matchesZoneSnapshot(zoneSnapshotJson: String): Boolean {
    val snapshot = JSONObject(zoneSnapshotJson)
    val snapshotId = snapshot.optString("id")
    val snapshotName = snapshot.optString("name")
    return id.matchesSnapshotValue(snapshotId) ||
        name.matchesSnapshotValue(snapshotName)
}

private fun String.matchesSnapshotValue(other: String): Boolean {
    val self = normalizeZoneIdentity()
    val target = other.normalizeZoneIdentity()
    return self.isNotBlank() && target.isNotBlank() && self == target
}

private fun String.normalizeZoneIdentity(): String =
    trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun enrichRequestData(
    requestData: AuthorizationRequestData,
    operationData: AuthorizationOperationData
): AuthorizationRequestData {
    val takeoffText = operationData.takeoffPoint()?.let { point ->
        "${point.lat.formatForDraft()} ${point.lon.formatForDraft()}"
    }
    val areaText = operationData.areaPoints.takeIf { it.isNotEmpty() }
        ?.joinToString(separator = " - ") { point ->
            "${point.lat.formatForDraft()} ${point.lon.formatForDraft()}"
        }
    val withOperation = requestData.copy(
        takeoff = takeoffText ?: requestData.takeoff,
        landing = takeoffText ?: requestData.landing,
        areaDescription = areaText?.let { "Area operativa racchiusa dai punti: $it" } ?: requestData.areaDescription,
        verticalLower = operationData.calculatedLowerLimitMeters?.let { "$it m (GND)" } ?: requestData.verticalLower,
        verticalUpper = operationData.calculatedUpperLimitMeters?.let { "$it m" } ?: requestData.verticalUpper
    )
    val distanceKm = operationData.airportDistanceKm
    val distanceNm = operationData.airportDistanceNm
    if (
        operationData.airportIcao.isBlank() ||
        distanceKm == null ||
        distanceNm == null
    ) {
        return withOperation.copy(
            airportIcao = "",
            airportName = "",
            airportCity = "",
            airportDistanceKm = "",
            airportDistanceNm = ""
        )
    }

    return withOperation.copy(
        airportIcao = operationData.airportIcao,
        airportName = listOf(operationData.airportIcao, operationData.airportCity)
            .filter { it.isNotBlank() }
            .joinToString(" "),
        airportCity = operationData.airportCity,
        airportDistanceKm = distanceKm.formatOneDecimal(),
        airportDistanceNm = distanceNm.formatOneDecimal()
    )
}

private fun AuthorizationOperationData.takeoffPoint(): AirportPoint? {
    val lat = takeoffLat
    val lon = takeoffLon
    return if (lat != null && lon != null && lat.isFinite() && lon.isFinite()) {
        AirportPoint(lat, lon)
    } else {
        null
    }
}

private fun buildInitialRequestData(
    procedureType: String,
    zone: ZoneInfo,
    profile: LocalPilotProfile?,
    operator: LocalUasOperator?,
    drone: LocalDrone?,
    license: String,
    lat: Double?,
    lon: Double?
): AuthorizationRequestData {
    val name = profile?.displayName.orEmpty()
    val easa = operator?.easaOperatorCode.orEmpty()
    val requester = listOfNotNull(
        name.takeIf { it.isNotBlank() },
        license.takeIf { it.isNotBlank() }?.let { "Att: $it" },
        easa.takeIf { it.length > 3 }?.let { "EASA: $it" }
    ).joinToString(" - ")
    val aircraftType = drone?.let {
        listOf(
            it.displayName,
            it.classLabel.takeIf(String::isNotBlank)?.let { classLabel -> "($classLabel)" }.orEmpty(),
            droneSpecificLabels(it).takeIf { labels -> labels.isNotBlank() }?.let { labels -> "Specific: $labels" }.orEmpty()
        ).filter { part -> part.isNotBlank() }.joinToString(" ")
    }.orEmpty()
    val verticalUpper = zone.limitMetersAgl?.let { "$it m" }
        ?: zone.verticalLimits?.upperMetersAgl?.let { "$it m" }
        ?: zone.verticalLimits?.upper.orEmpty()

    return AuthorizationRequestData(
        requester = requester,
        name = name,
        license = license,
        easaOperatorCode = easa,
        phone = profile?.phone.orEmpty(),
        contactEmail = operator?.pec.orEmpty(),
        activityType = if (procedureType == "ATM09") {
            "Riprese video e foto a carattere professionale - VLOS"
        } else {
            "Foto e video senza scopo di lucro - VLOS"
        },
        aircraftType = aircraftType,
        selectedDroneId = drone?.id.orEmpty(),
        zoneName = zone.name.orEmpty(),
        takeoff = if (lat != null && lon != null && lat.isFinite() && lon.isFinite()) {
            "${lat.formatForDraft()} ${lon.formatForDraft()}"
        } else {
            ""
        },
        landing = if (lat != null && lon != null && lat.isFinite() && lon.isFinite()) {
            "${lat.formatForDraft()} ${lon.formatForDraft()}"
        } else {
            ""
        },
        verticalLower = "0 m (GND)",
        verticalUpper = verticalUpper,
        notes = "Il volo verra effettuato mantenendo sempre il contatto visivo con il drone (VLOS)."
    )
}

private fun buildZoneSnapshot(
    zone: ZoneInfo,
    procedureType: String,
    procedure: AuthorizationProcedure?
): JSONObject {
    val authority = zone.bestAuthority()
    return JSONObject()
        .put("id", zone.id)
        .put("name", zone.name)
        .put("code", zone.code)
        .put("family", zone.family)
        .put("type", zone.type)
        .put("classification", zone.classification)
        .put("procedureType", procedureType)
        .put("procedureVersion", procedure?.version ?: 1)
        .put("reasonCode", procedure?.reasonCode ?: zone.authorization?.reasonCodes?.firstOrNull().orEmpty())
        .put("reasonCodes", JSONArray(zone.authorization?.reasonCodes.orEmpty()))
        .put("requiredOperationCategory", zone.requiredOperationCategory())
        .put("operationCategory", zone.authorization?.operationCategory ?: zone.enr?.operationCategory ?: zone.sup?.operationCategory ?: zone.sup?.authorization?.operationCategory ?: zone.uasGeographicalZone?.operationCategory)
        .put("applicability", zone.authorization?.applicability)
        .put("resolutionStatus", zone.authorization?.resolutionStatus)
        .put("limitMetersAgl", zone.limitMetersAgl)
        .put("verticalLower", zone.verticalLimits?.lower)
        .put("verticalUpper", zone.verticalLimits?.upper)
        .put("verticalLowerMetersAgl", zone.verticalLimits?.lowerMetersAgl)
        .put("verticalUpperMetersAgl", zone.verticalLimits?.upperMetersAgl)
        .put("authority", JSONObject()
            .put("name", authority?.name)
            .put("code", authority?.code)
            .put("contact", authority?.displayContact())
            .put("emails", JSONArray(authority?.emails.orEmpty()))
            .put("note", authority?.note)
            .put("source", authority?.source)
        )
}

private fun ZoneInfo.bestAuthority(): AuthorityInfo? =
    listOfNotNull(authority, enr?.authority, sup?.authority, uasGeographicalZone?.authority)
        .firstOrNull { it.hasUsableContact() }
        ?: listOfNotNull(authority, enr?.authority, sup?.authority, uasGeographicalZone?.authority).firstOrNull()

private fun AuthorityInfo.hasUsableContact(): Boolean =
    emails.isNotEmpty() || !contact.isNullOrBlank() || !name.isNullOrBlank() || !note.isNullOrBlank()

private fun AuthorityInfo.displayContact(): String =
    emails.joinToString(", ").ifBlank { contact.orEmpty().takeUnless { it.isJsonObjectText() }.orEmpty() }

private fun String.isJsonObjectText(): Boolean {
    val trimmed = trim()
    return trimmed.startsWith("{") && trimmed.endsWith("}")
}

private fun ZoneInfo.requiredOperationCategory(): String {
    val direct = authorization?.operationCategory
        ?: enr?.operationCategory
        ?: sup?.operationCategory
        ?: sup?.authorization?.operationCategory
        ?: uasGeographicalZone?.operationCategory
    direct?.uppercase(Locale.ROOT)?.let {
        return when (it) {
            "SPECIFIC_REQUIRED", "SPECIFIC" -> "SPECIFIC"
            "OPEN_WITH_AUTH", "OPEN_AUTH", "OPEN" -> "OPEN"
            else -> it
        }
    }

    val reasonCodes = authorization?.reasonCodes.orEmpty().map { it.uppercase(Locale.ROOT) }
    return when {
        reasonCodes.any { it.contains("SPECIFIC_REQUIRED") } -> "SPECIFIC"
        reasonCodes.any { it.contains("OPEN_AUTH") || it == "OPEN_AUTH" } -> "OPEN"
        else -> ""
    }
}

private fun buildPilotSnapshot(profile: LocalPilotProfile?): JSONObject =
    JSONObject()
        .put("firstName", profile?.firstName.orEmpty())
        .put("lastName", profile?.lastName.orEmpty())
        .put("city", profile?.city.orEmpty())
        .put("phone", profile?.phone.orEmpty())
        .put("email", profile?.email.orEmpty())

private fun buildOperatorSnapshot(operator: LocalUasOperator?): JSONObject =
    JSONObject()
        .put("name", operator?.name.orEmpty())
        .put("type", operator?.type.orEmpty())
        .put("easaOperatorCode", operator?.easaOperatorCode.orEmpty())
        .put("pec", operator?.pec.orEmpty())
        .put("insuranceCompany", operator?.insuranceCompany.orEmpty())
        .put("insurancePolicyNumber", operator?.insurancePolicyNumber.orEmpty())
        .put("insuranceExpiresAt", operator?.insuranceExpiresAt.orEmpty())

private fun buildCertificateSnapshot(certificates: List<LocalPilotCertificate>): JSONArray =
    JSONArray().also { array ->
        certificates.forEach { certificate ->
            array.put(
                JSONObject()
                    .put("id", certificate.id)
                    .put("issuingAuthority", certificate.issuingAuthority)
                    .put("certificateNumber", certificate.certificateNumber)
                    .put("issueDate", certificate.issueDate)
                    .put("expiryDate", certificate.expiryDate)
                    .put("categories", JSONArray(certificate.categoryList))
                    .put("notes", certificate.notes)
            )
        }
    }

private fun buildDroneSnapshot(drone: LocalDrone?): JSONObject =
    JSONObject()
        .put("id", drone?.id.orEmpty())
        .put("manufacturer", drone?.manufacturer.orEmpty())
        .put("model", drone?.model.orEmpty())
        .put("classLabel", drone?.classLabel.orEmpty())
        .put("weight", drone?.weight)
        .put("serialNumber", drone?.serialNumber.orEmpty())
        .put("remoteId", drone?.remoteId ?: false)
        .put("euSts01Registered", drone?.euSts01Registered ?: false)
        .put("euSts01DeclarationDate", drone?.euSts01DeclarationDate.orEmpty())
        .put("euSts02Registered", drone?.euSts02Registered ?: false)
        .put("euSts02DeclarationDate", drone?.euSts02DeclarationDate.orEmpty())

private fun selectCertificateLabel(certificates: List<LocalPilotCertificate>, procedureType: String): String {
    val labels = certificates.flatMap { it.categoryList }.map { it.toCertificateLabel() }.distinct()
    if (labels.isEmpty()) return ""
    return if (procedureType == "ATM09") {
        labels.firstOrNull { it.contains("STS") } ?: labels.first()
    } else {
        labels.firstOrNull { it == "A1/A3" } ?: labels.first()
    }
}

private fun String.toCertificateLabel(): String =
    when (uppercase().replace('-', '_').replace('/', '_')) {
        "A1_A3" -> "A1/A3"
        "A2" -> "A2"
        "STS_01", "EU_STS_01" -> "EU-STS-01"
        "STS_02", "EU_STS_02" -> "EU-STS-02"
        else -> this
    }

private fun droneSpecificLabels(drone: LocalDrone): String =
    listOfNotNull(
        "EU-STS-01".takeIf { drone.euSts01Registered && drone.classLabel == "C5" },
        "EU-STS-02".takeIf { drone.euSts02Registered && drone.classLabel == "C6" }
    ).joinToString(", ")

private fun Double.formatForDraft(): String =
    String.format(Locale.US, "%.5f", this)

private fun Double.formatOneDecimal(): String =
    String.format(Locale.US, "%.1f", this)
