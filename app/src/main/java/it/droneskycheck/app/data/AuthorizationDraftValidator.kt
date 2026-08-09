package it.droneskycheck.app.data

data class AuthorizationDraftValidation(
    val status: String,
    val missingFields: List<AuthorizationMissingField>
)

object AuthorizationDraftValidator {
    fun validate(
        procedureType: String,
        requestData: AuthorizationRequestData
    ): AuthorizationDraftValidation {
        val missing = buildList {
            requireField(requestData.name, "pilot.name", "Pilota", "Nome e cognome")
            requireField(requestData.license, "pilot.certificate", "Pilota", "Attestato")
            requireField(requestData.phone, "pilot.phone", "Pilota", "Telefono")
            requireField(requestData.easaOperatorCode, "operator.easaCode", "Operatore UAS", "Codice operatore UAS")
            requireField(requestData.contactEmail, "operator.pec", "Operatore UAS", "PEC")
            requireField(requestData.aircraftType, "drone.aircraftType", "Drone", "Drone")
            requireField(requestData.zoneName, "zone.name", "Zona", "Nome zona")
            requireField(requestData.verticalUpper, "zone.verticalUpper", "Zona", "Limite superiore")
            requireField(requestData.takeoff, "operation.takeoff", "Operazione", "Decollo")
            requireField(requestData.landing, "operation.landing", "Operazione", "Atterraggio")
            requireField(requestData.areaDescription, "operation.areaDescription", "Operazione", "Descrizione area")
            requireField(requestData.operationStartDateTime, "operation.startDateTime", "Operazione", "Inizio operazioni")
            requireField(requestData.operationEndDateTime, "operation.endDateTime", "Operazione", "Fine operazioni")
            requireField(requestData.airportName, "enrichment.airportName", "Arricchimento", "Aeroporto piu vicino")
            requireField(requestData.airportDistanceNm, "enrichment.airportDistanceNm", "Arricchimento", "Distanza aeroporto NM")
            requireField(requestData.airportDistanceKm, "enrichment.airportDistanceKm", "Arricchimento", "Distanza aeroporto KM")
            if (procedureType == "ATM05") {
                requireField(requestData.activityType, "request.activityType", "Richiesta", "Tipo operazione")
            }
            if (requestData.stampNumber.isBlank() && requestData.stampImageLocalPath.isBlank()) {
                add(AuthorizationMissingField("request.stamp", "Richiesta", "Marca da bollo"))
            }
        }

        return AuthorizationDraftValidation(
            status = if (missing.isEmpty()) AuthorizationDraftStatuses.Ready else AuthorizationDraftStatuses.Draft,
            missingFields = missing
        )
    }
}

private fun MutableList<AuthorizationMissingField>.requireField(
    value: String,
    key: String,
    group: String,
    label: String
) {
    if (value.isBlank()) {
        add(AuthorizationMissingField(key, group, label))
    }
}
