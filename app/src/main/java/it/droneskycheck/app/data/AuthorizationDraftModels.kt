package it.droneskycheck.app.data

import org.json.JSONArray
import org.json.JSONObject

object AuthorizationDraftStatuses {
    const val Draft = "DRAFT"
    const val Ready = "READY"
}

data class AuthorizationDraft(
    val id: String,
    val procedureType: String,
    val procedureVersion: Int,
    val status: String,
    val zoneSnapshotJson: String,
    val operationDataJson: String,
    val pilotSnapshotJson: String,
    val operatorSnapshotJson: String,
    val certificateSnapshotJson: String,
    val droneSnapshotJson: String,
    val requestDataJson: String,
    val missingFields: List<AuthorizationMissingField>,
    val createdAt: Long,
    val updatedAt: Long
) {
    val zoneName: String
        get() = JSONObject(zoneSnapshotJson).optString("name", "")

    val requestData: AuthorizationRequestData
        get() = AuthorizationRequestData.fromJson(requestDataJson)

    val operationData: AuthorizationOperationData
        get() = AuthorizationOperationData.fromJson(operationDataJson)

    val workflowStep: String
        get() = operationData.workflowStep
}

data class AuthorizationMissingField(
    val key: String,
    val group: String,
    val label: String
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("key", key)
            .put("group", group)
            .put("label", label)

    companion object {
        fun fromJson(json: JSONObject): AuthorizationMissingField =
            AuthorizationMissingField(
                key = json.optString("key"),
                group = json.optString("group"),
                label = json.optString("label")
            )
    }
}

data class AuthorizationOperationData(
    val takeoffLat: Double? = null,
    val takeoffLon: Double? = null,
    val areaPoints: List<AuthorizationGeoPoint> = emptyList(),
    val areaClosed: Boolean = false,
    val workflowStep: String = AuthorizationWorkflowSteps.Takeoff,
    val zoneAnalysisSummary: String = "",
    val calculatedLowerLimitMeters: Int? = null,
    val calculatedUpperLimitMeters: Int? = null,
    val involvedZones: List<String> = emptyList(),
    val detectedZones: List<AuthorizationZoneReference> = emptyList(),
    val operationStartDateTime: String = "",
    val operationEndDateTime: String = "",
    val requestedAltitudeMeters: Int? = null,
    val airportIcao: String = "",
    val airportName: String = "",
    val airportCity: String = "",
    val airportDistanceKm: Double? = null,
    val airportDistanceNm: Double? = null
) {
    fun toJson(): String =
        JSONObject()
            .putNullable("takeoffLat", takeoffLat)
            .putNullable("takeoffLon", takeoffLon)
            .put("areaPoints", JSONArray().also { array ->
                areaPoints.forEach { point -> array.put(point.toJson()) }
            })
            .put("areaClosed", areaClosed)
            .put("workflowStep", workflowStep)
            .put("zoneAnalysisSummary", zoneAnalysisSummary)
            .putNullable("calculatedLowerLimitMeters", calculatedLowerLimitMeters)
            .putNullable("calculatedUpperLimitMeters", calculatedUpperLimitMeters)
            .put("involvedZones", JSONArray(involvedZones))
            .put("detectedZones", JSONArray().also { array ->
                detectedZones.forEach { zone -> array.put(zone.toJson()) }
            })
            .put("operationStartDateTime", operationStartDateTime)
            .put("operationEndDateTime", operationEndDateTime)
            .putNullable("requestedAltitudeMeters", requestedAltitudeMeters)
            .put("airportIcao", airportIcao)
            .put("airportName", airportName)
            .put("airportCity", airportCity)
            .putNullable("airportDistanceKm", airportDistanceKm)
            .putNullable("airportDistanceNm", airportDistanceNm)
            .toString()

    companion object {
        fun fromJson(value: String): AuthorizationOperationData {
            val json = JSONObject(value.ifBlank { "{}" })
            return AuthorizationOperationData(
                takeoffLat = json.optNullableDouble("takeoffLat"),
                takeoffLon = json.optNullableDouble("takeoffLon"),
                areaPoints = authorizationGeoPointsFromJson(json.optJSONArray("areaPoints")),
                areaClosed = json.optBoolean("areaClosed", false),
                workflowStep = json.optString("workflowStep", AuthorizationWorkflowSteps.Takeoff),
                zoneAnalysisSummary = json.optString("zoneAnalysisSummary"),
                calculatedLowerLimitMeters = json.optNullableInt("calculatedLowerLimitMeters"),
                calculatedUpperLimitMeters = json.optNullableInt("calculatedUpperLimitMeters"),
                involvedZones = json.optJSONArray("involvedZones").toStringList(),
                detectedZones = authorizationZoneReferencesFromJson(json.optJSONArray("detectedZones")),
                operationStartDateTime = json.optString("operationStartDateTime"),
                operationEndDateTime = json.optString("operationEndDateTime"),
                requestedAltitudeMeters = json.optNullableInt("requestedAltitudeMeters"),
                airportIcao = json.optString("airportIcao"),
                airportName = json.optString("airportName"),
                airportCity = json.optString("airportCity"),
                airportDistanceKm = json.optNullableDouble("airportDistanceKm"),
                airportDistanceNm = json.optNullableDouble("airportDistanceNm")
            )
        }
    }
}

object AuthorizationWorkflowSteps {
    const val Takeoff = "TAKEOFF"
    const val Area = "AREA"
    const val Analysis = "ANALYSIS"
    const val Form = "FORM"
}

data class AuthorizationGeoPoint(
    val lat: Double,
    val lon: Double
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("lat", lat)
            .put("lon", lon)

    companion object {
        fun fromJson(json: JSONObject): AuthorizationGeoPoint? {
            val lat = json.optDouble("lat", Double.NaN)
            val lon = json.optDouble("lon", json.optDouble("lng", Double.NaN))
            return AuthorizationGeoPoint(lat, lon)
                .takeIf { it.lat.isFinite() && it.lon.isFinite() }
        }
    }
}

data class AuthorizationZoneReference(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val lowerLimitMeters: Int? = null,
    val upperLimitMeters: Int? = null
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("type", type)
            .putNullable("lowerLimitMeters", lowerLimitMeters)
            .putNullable("upperLimitMeters", upperLimitMeters)

    companion object {
        fun fromJson(json: JSONObject): AuthorizationZoneReference =
            AuthorizationZoneReference(
                id = json.optString("id"),
                name = json.optString("name"),
                type = json.optString("type"),
                lowerLimitMeters = json.optNullableInt("lowerLimitMeters"),
                upperLimitMeters = json.optNullableInt("upperLimitMeters")
            )
    }
}

data class AuthorizationRequestData(
    val requester: String = "",
    val name: String = "",
    val license: String = "",
    val easaOperatorCode: String = "",
    val phone: String = "",
    val contactEmail: String = "",
    val activityType: String = "",
    val aircraftType: String = "",
    val selectedDroneId: String = "",
    val zoneName: String = "",
    val takeoff: String = "",
    val landing: String = "",
    val areaDescription: String = "",
    val verticalLower: String = "0 m (GND)",
    val verticalUpper: String = "",
    val operationStartDateTime: String = "",
    val operationEndDateTime: String = "",
    val stampNumber: String = "",
    val stampDate: String = "",
    val stampImageLocalPath: String = "",
    val airportIcao: String = "",
    val airportName: String = "",
    val airportCity: String = "",
    val airportDistanceNm: String = "",
    val airportDistanceKm: String = "",
    val notes: String = ""
) {
    fun toJson(): String =
        JSONObject()
            .put("requester", requester)
            .put("name", name)
            .put("license", license)
            .put("easaOperatorCode", easaOperatorCode)
            .put("phone", phone)
            .put("contactEmail", contactEmail)
            .put("activityType", activityType)
            .put("aircraftType", aircraftType)
            .put("selectedDroneId", selectedDroneId)
            .put("zoneName", zoneName)
            .put("takeoff", takeoff)
            .put("landing", landing)
            .put("areaDescription", areaDescription)
            .put("verticalLower", verticalLower)
            .put("verticalUpper", verticalUpper)
            .put("operationStartDateTime", operationStartDateTime)
            .put("operationEndDateTime", operationEndDateTime)
            .put("stampNumber", stampNumber)
            .put("stampDate", stampDate)
            .put("stampImageLocalPath", stampImageLocalPath)
            .put("airportIcao", airportIcao)
            .put("airportName", airportName)
            .put("airportCity", airportCity)
            .put("airportDistanceNm", airportDistanceNm)
            .put("airportDistanceKm", airportDistanceKm)
            .put("notes", notes)
            .toString()

    companion object {
        fun fromJson(value: String): AuthorizationRequestData {
            val json = JSONObject(value.ifBlank { "{}" })
            return AuthorizationRequestData(
                requester = json.optString("requester"),
                name = json.optString("name"),
                license = json.optString("license"),
                easaOperatorCode = json.optString("easaOperatorCode"),
                phone = json.optString("phone"),
                contactEmail = json.optString("contactEmail"),
                activityType = json.optString("activityType"),
                aircraftType = json.optString("aircraftType"),
                selectedDroneId = json.optString("selectedDroneId"),
                zoneName = json.optString("zoneName"),
                takeoff = json.optString("takeoff"),
                landing = json.optString("landing"),
                areaDescription = json.optString("areaDescription"),
                verticalLower = json.optString("verticalLower", "0 m (GND)"),
                verticalUpper = json.optString("verticalUpper"),
                operationStartDateTime = json.optString("operationStartDateTime"),
                operationEndDateTime = json.optString("operationEndDateTime"),
                stampNumber = json.optString("stampNumber"),
                stampDate = json.optString("stampDate"),
                stampImageLocalPath = json.optString("stampImageLocalPath"),
                airportIcao = json.optString("airportIcao"),
                airportName = json.optString("airportName"),
                airportCity = json.optString("airportCity"),
                airportDistanceNm = json.optString("airportDistanceNm"),
                airportDistanceKm = json.optString("airportDistanceKm"),
                notes = json.optString("notes")
            )
        }
    }
}

fun List<AuthorizationMissingField>.toMissingFieldsJson(): String {
    val array = JSONArray()
    forEach { array.put(it.toJson()) }
    return array.toString()
}

fun missingFieldsFromJson(value: String): List<AuthorizationMissingField> {
    val array = JSONArray(value.ifBlank { "[]" })
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let(AuthorizationMissingField::fromJson)
    }
}

private fun authorizationGeoPointsFromJson(array: JSONArray?): List<AuthorizationGeoPoint> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let(AuthorizationGeoPoint::fromJson)
    }
}

private fun authorizationZoneReferencesFromJson(array: JSONArray?): List<AuthorizationZoneReference> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let(AuthorizationZoneReference::fromJson)
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optString(index).takeIf { it.isNotBlank() }
    }
}

fun AuthorizationDraftEntity.toModel(): AuthorizationDraft =
    AuthorizationDraft(
        id = id,
        procedureType = procedureType,
        procedureVersion = procedureVersion,
        status = status,
        zoneSnapshotJson = zoneSnapshotJson,
        operationDataJson = operationDataJson,
        pilotSnapshotJson = pilotSnapshotJson,
        operatorSnapshotJson = operatorSnapshotJson,
        certificateSnapshotJson = certificateSnapshotJson,
        droneSnapshotJson = droneSnapshotJson,
        requestDataJson = requestDataJson,
        missingFields = missingFieldsFromJson(missingFieldsJson),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (isNull(key)) null else optDouble(key)

private fun JSONObject.optNullableInt(key: String): Int? =
    if (isNull(key)) null else optInt(key)
