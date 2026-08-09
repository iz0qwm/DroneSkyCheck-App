package it.droneskycheck.app.data

data class LocalPilotSnapshot(
    val profile: LocalPilotProfile? = null,
    val certificates: List<LocalPilotCertificate> = emptyList(),
    val operator: LocalUasOperator? = null,
    val drones: List<LocalDrone> = emptyList()
) {
    val selectedDrone: LocalDrone?
        get() = drones.firstOrNull { it.isSelected } ?: drones.firstOrNull()
}

data class LocalPilotProfile(
    val firstName: String = "",
    val lastName: String = "",
    val city: String = "",
    val phone: String = "",
    val email: String = "",
    val profilePhoto: String = "",
    val skipPilotCompetencyChecks: Boolean = false
) {
    val displayName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}

data class LocalPilotCertificate(
    val id: String = "",
    val issuingAuthority: String = "",
    val certificateNumber: String = "",
    val issueDate: String = "",
    val expiryDate: String = "",
    val categories: String = "",
    val notes: String = ""
) {
    val categoryList: List<String>
        get() = categories.split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
}

data class LocalUasOperator(
    val name: String = "",
    val type: String = LocalOperatorTypes.Individual,
    val easaOperatorCode: String = "",
    val pec: String = "",
    val insuranceCompany: String = "",
    val insurancePolicyNumber: String = "",
    val insuranceExpiresAt: String = ""
)

object LocalOperatorTypes {
    const val Individual = "individual"
    const val Association = "association"
    const val Company = "company"
    const val PublicBody = "public_body"
}

data class LocalDrone(
    val id: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val classLabel: String = "",
    val weight: Double? = null,
    val serialNumber: String = "",
    val remoteControllers: String = "",
    val batteries: String = "",
    val cameras: String = "",
    val remoteId: Boolean = false,
    val euSts01Registered: Boolean = false,
    val euSts01DeclarationDate: String = "",
    val euSts02Registered: Boolean = false,
    val euSts02DeclarationDate: String = "",
    val notes: String = "",
    val isSelected: Boolean = false
) {
    val displayName: String
        get() = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { "Drone" }
}
