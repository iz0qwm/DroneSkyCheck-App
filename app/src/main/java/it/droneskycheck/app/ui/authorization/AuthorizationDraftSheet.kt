package it.droneskycheck.app.ui.authorization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.droneskycheck.app.data.AuthorizationDraft
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorizationDraftSheet(
    draft: AuthorizationDraft,
    onOpenProfile: () -> Unit,
    onCancelDraft: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCancelConfirm by remember { mutableStateOf(false) }
    val zone = JSONObject(draft.zoneSnapshotJson)
    val pilot = JSONObject(draft.pilotSnapshotJson)
    val operator = JSONObject(draft.operatorSnapshotJson)
    val drone = JSONObject(draft.droneSnapshotJson)
    val request = draft.requestData

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Richiesta ATM05/ATM09",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = draft.zoneName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusCard(draft)

            DraftSection("Zona") {
                DraftBlock(zone.optString("name"))
                DraftLine("Procedura", draft.procedureType)
                DraftLine("Tipo", listOf(zone.optString("family"), zone.optString("type")).filter { it.isNotBlank() }.joinToString(" / "))
                DraftLine("Motivo", zone.optString("reasonCode"))
                DraftLine("Applicabilita", zone.optString("applicability"))
            }

            DraftSection("Limiti") {
                DraftLine("Limite inferiore", request.verticalLower)
                DraftLine("Limite superiore", request.verticalUpper)
            }

            DraftSection("Destinatari") {
                val authority = zone.optJSONObject("authority")
                DraftLine("A", authority?.optString("contact"))
                DraftLine("Fonte", authority?.optString("source"))
                DraftLine("CC", "protocollo@pec.enac.gov.it, mobilita.innovativa@enac.gov.it")
            }

            DraftSection("Aeroporto di riferimento") {
                DraftBlock(
                    listOf(request.airportName, request.airportCity)
                        .filter { it.isNotBlank() && it !in request.airportName }
                        .joinToString(" - ")
                        .ifBlank { request.airportName }
                )
                DraftLine("ICAO", request.airportIcao)
                DraftLine("Distanza", listOf(
                    request.airportDistanceKm.takeIf { it.isNotBlank() }?.let { "$it km" },
                    request.airportDistanceNm.takeIf { it.isNotBlank() }?.let { "$it NM" }
                ).filterNotNull().joinToString(" / "))
            }

            DraftSection("Pilota") {
                DraftBlock(listOf(pilot.optString("firstName"), pilot.optString("lastName")).filter { it.isNotBlank() }.joinToString(" "))
                DraftLine("Telefono", pilot.optString("phone"))
                DraftLine("Email", pilot.optString("email"))
                DraftLine("Attestato", request.license)
            }

            DraftSection("Operatore UAS") {
                DraftBlock(operator.optString("name"))
                DraftLine("Codice EASA", operator.optString("easaOperatorCode"))
                DraftLine("PEC", operator.optString("pec"))
                DraftLine("Assicurazione", listOf(
                    operator.optString("insuranceCompany"),
                    operator.optString("insurancePolicyNumber"),
                    operator.optString("insuranceExpiresAt")
                ).filter { it.isNotBlank() }.joinToString(" - "))
            }

            DraftSection("Drone") {
                DraftBlock(listOf(drone.optString("manufacturer"), drone.optString("model")).filter { it.isNotBlank() }.joinToString(" "))
                DraftLine("Classe", drone.optString("classLabel"))
                DraftLine("Seriale", drone.optString("serialNumber"))
                DraftLine("Peso", drone.optDouble("weight").takeIf { it > 0 }?.let { "${it.toInt()} g" })
            }

            DraftSection("Operazione") {
                DraftLine("Decollo", request.takeoff)
                DraftLine("Atterraggio", request.landing)
                DraftLine("Vertici area", request.areaDescription)
                DraftLine("Zone coinvolte", draft.operationData.involvedZones.joinToString(", "))
                DraftLine("Analisi", draft.operationData.zoneAnalysisSummary)
                DraftLine("Tipo attivita", request.activityType)
                DraftLine("Periodo", listOf(request.operationStartDateTime, request.operationEndDateTime).filter { it.isNotBlank() }.joinToString(" - "))
            }

            if (draft.missingFields.isNotEmpty()) {
                DraftSection("Dati mancanti") {
                    draft.missingFields.groupBy { it.group }.forEach { (group, fields) ->
                        Text(
                            text = group,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        fields.forEach { field ->
                            Text(
                                text = "- ${field.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(onClick = onOpenProfile) {
                        Text("Vai al Profilo pilota")
                    }
                }
            }

            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Completa richiesta")
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Chiudi / torna alla mappa")
            }

            OutlinedButton(
                onClick = { showCancelConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Annulla richiesta")
            }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Annullare la richiesta?") },
            text = { Text("La bozza locale e i dati temporanei della pratica verranno eliminati.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirm = false
                        onCancelDraft()
                    }
                ) {
                    Text("Annulla richiesta")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("Mantieni")
                }
            }
        )
    }
}

@Composable
private fun StatusCard(draft: AuthorizationDraft) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DraftLine("Stato", draft.status)
            DraftLine("Creata", formatTimestamp(draft.createdAt))
            DraftLine("Campi mancanti", draft.missingFields.size.toString())
        }
    }
}

@Composable
private fun DraftSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun DraftBlock(value: String?) {
    if (value.isNullOrBlank()) return
    Text(
        text = value,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun DraftLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatTimestamp(value: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(value))

@Suppress("unused")
private fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
