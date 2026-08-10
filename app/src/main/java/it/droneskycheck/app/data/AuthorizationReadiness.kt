package it.droneskycheck.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class AuthorizationReadiness(
    val requiredOperationCategory: String,
    val scenario: String?,
    val blockingMessages: List<AuthorizationReadinessMessage>,
    val warningMessages: List<AuthorizationReadinessMessage>
) {
    val canGeneratePdf: Boolean
        get() = blockingMessages.isEmpty()
}

data class AuthorizationReadinessMessage(
    val title: String,
    val body: String
)

object AuthorizationReadinessEvaluator {
    fun evaluate(draft: AuthorizationDraft): AuthorizationReadiness {
        val zone = JSONObject(draft.zoneSnapshotJson)
        val certificates = JSONArray(draft.certificateSnapshotJson)
        val drone = JSONObject(draft.droneSnapshotJson)
        val requiredCategory = resolveRequiredOperationCategory(zone)
        val selectedScenario = resolveSelectedScenario(draft.requestData.license)
        val hasSts01 = certificates.hasCertificateCategory("STS_01", "EU_STS_01")
        val hasSts02 = certificates.hasCertificateCategory("STS_02", "EU_STS_02")
        val hasAnySts = hasSts01 || hasSts02
        val blockers = mutableListOf<AuthorizationReadinessMessage>()
        val warnings = mutableListOf<AuthorizationReadinessMessage>()

        if (requiredCategory == "SPECIFIC") {
            if (!hasAnySts) {
                blockers += AuthorizationReadinessMessage(
                    title = "Operazione in categoria Specific",
                    body = "Per questa operazione e richiesta la categoria SPECIFIC.\n\n" +
                        "Nel tuo profilo non risultano attestati per scenari standard europei.\n\n" +
                        "Per procedere tramite STS e necessario possedere i titoli previsti per lo scenario applicabile. " +
                        "In alternativa l'operazione puo richiedere una specifica Autorizzazione Operativa ENAC o altro titolo abilitante."
                )
            } else {
                when (selectedScenario) {
                    "EU-STS-01" -> evaluateStsScenario(
                        scenario = selectedScenario,
                        requiredClass = "C5",
                        hasScenarioTitle = hasSts01,
                        hasScenarioDeclaration = drone.optBoolean("euSts01Registered", false),
                        droneClass = drone.optString("classLabel"),
                        blockers = blockers,
                        warnings = warnings
                    )
                    "EU-STS-02" -> evaluateStsScenario(
                        scenario = selectedScenario,
                        requiredClass = "C6",
                        hasScenarioTitle = hasSts02,
                        hasScenarioDeclaration = drone.optBoolean("euSts02Registered", false),
                        droneClass = drone.optString("classLabel"),
                        blockers = blockers,
                        warnings = warnings
                    )
                    else -> warnings += AuthorizationReadinessMessage(
                        title = "Base operativa da confermare",
                        body = "Il profilo contiene almeno un titolo STS, ma questa pratica non identifica con certezza lo scenario applicabile. " +
                            "Verifica se procedere tramite STS, Autorizzazione Operativa ENAC, PDRA/SORA o LUC prima dell'invio."
                    )
                }
            }
        }

        return AuthorizationReadiness(
            requiredOperationCategory = requiredCategory,
            scenario = selectedScenario,
            blockingMessages = blockers,
            warningMessages = warnings
        )
    }

    fun resolveRequiredOperationCategory(zone: JSONObject): String {
        val direct = zone.optString("requiredOperationCategory")
            .ifBlank { zone.optString("operationCategory") }
            .normalizeCategory()
        if (direct == "SPECIFIC" || direct == "OPEN") return direct

        val reasonCodes = zone.optJSONArray("reasonCodes").toStringSet()
        return when {
            reasonCodes.any { it.contains("SPECIFIC_REQUIRED") } -> "SPECIFIC"
            reasonCodes.any { it.contains("OPEN_AUTH") || it == "OPEN_AUTH" } -> "OPEN"
            else -> "Non determinata"
        }
    }

    private fun resolveSelectedScenario(license: String): String? {
        val normalized = license.uppercase(Locale.ROOT)
            .replace('-', '_')
            .replace('/', '_')
            .replace(' ', '_')
        return when {
            "STS_02" in normalized -> "EU-STS-02"
            "STS_01" in normalized -> "EU-STS-01"
            else -> null
        }
    }

    private fun evaluateStsScenario(
        scenario: String,
        requiredClass: String,
        hasScenarioTitle: Boolean,
        hasScenarioDeclaration: Boolean,
        droneClass: String,
        blockers: MutableList<AuthorizationReadinessMessage>,
        warnings: MutableList<AuthorizationReadinessMessage>
    ) {
        if (!hasScenarioTitle) {
            blockers += AuthorizationReadinessMessage(
                title = "Titolo $scenario mancante",
                body = "Per il percorso $scenario e necessario il titolo previsto per lo scenario applicabile."
            )
        }

        if (!droneClass.equals(requiredClass, ignoreCase = true)) {
            blockers += AuthorizationReadinessMessage(
                title = "Drone non coerente con $scenario",
                body = "$scenario richiede un UAS di classe $requiredClass. Nel profilo il drone selezionato risulta ${droneClass.ifBlank { "non classificato" }}."
            )
        }

        if (!hasScenarioDeclaration) {
            warnings += AuthorizationReadinessMessage(
                title = "Dichiarazione $scenario da verificare",
                body = "Nel profilo non risulta registrata una dichiarazione operativa $scenario per il drone selezionato. Verifica il dato prima di inviare la pratica."
            )
        }
    }
}

private fun String.normalizeCategory(): String =
    uppercase(Locale.ROOT).let {
        when (it) {
            "SPECIFIC_REQUIRED", "SPECIFIC" -> "SPECIFIC"
            "OPEN_WITH_AUTH", "OPEN_AUTH", "OPEN" -> "OPEN"
            else -> ""
        }
    }

private fun JSONArray?.toStringSet(): Set<String> {
    if (this == null) return emptySet()
    return (0 until length())
        .mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
        .map { it.uppercase(Locale.ROOT) }
        .toSet()
}

private fun JSONArray.hasCertificateCategory(vararg expected: String): Boolean {
    val expectedSet = expected.toSet()
    for (index in 0 until length()) {
        val categories = optJSONObject(index)?.optJSONArray("categories") ?: continue
        for (categoryIndex in 0 until categories.length()) {
            val category = categories.optString(categoryIndex)
                .uppercase(Locale.ROOT)
                .replace('-', '_')
                .replace('/', '_')
            if (category in expectedSet) return true
        }
    }
    return false
}
