package it.droneskycheck.app.ui.authorization

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import it.droneskycheck.app.data.AuthorizationDraft
import it.droneskycheck.app.data.AuthorizationPdfGenerator
import it.droneskycheck.app.data.AuthorizationReadinessEvaluator
import it.droneskycheck.app.data.AuthorizationRequestData
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorizationDraftSheet(
    draft: AuthorizationDraft,
    onOpenProfile: () -> Unit,
    onSaveRequestData: suspend (AuthorizationRequestData) -> AuthorizationDraft?,
    onCancelDraft: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var localDraft by remember(draft.id) { mutableStateOf(draft) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var form by remember(draft.id, draft.updatedAt) { mutableStateOf(draft.requestData) }

    LaunchedEffect(draft.id, draft.updatedAt) {
        localDraft = draft
        form = draft.requestData
    }

    val zone = JSONObject(localDraft.zoneSnapshotJson)
    val pilot = JSONObject(localDraft.pilotSnapshotJson)
    val operator = JSONObject(localDraft.operatorSnapshotJson)
    val drone = JSONObject(localDraft.droneSnapshotJson)
    val operation = localDraft.operationData
    val readiness = AuthorizationReadinessEvaluator.evaluate(localDraft)
    val canGenerate = localDraft.missingFields.isEmpty() && readiness.canGeneratePdf && !isGenerating

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
            HeaderCard(localDraft)

            SummaryCard(
                draft = localDraft,
                requiredOperationCategory = readiness.requiredOperationCategory
            )

            if (readiness.blockingMessages.isNotEmpty() || readiness.warningMessages.isNotEmpty()) {
                DraftSection("Controlli Specific") {
                    readiness.blockingMessages.forEach { message ->
                        MessageBlock(message.title, message.body, isBlocking = true)
                    }
                    readiness.warningMessages.forEach { message ->
                        MessageBlock(message.title, message.body, isBlocking = false)
                    }
                    if (readiness.blockingMessages.isNotEmpty()) {
                        OutlinedButton(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
                            Text("Vai al Profilo pilota")
                        }
                    }
                }
            }

            DraftSection("Area operativa") {
                DraftLine("Zona interessata", zone.optString("name"))
                DraftLine("Codice zona", zone.optString("code"))
                DraftLine("Tipo zona", listOf(zone.optString("family"), zone.optString("type")).filter { it.isNotBlank() }.joinToString(" / "))
                DraftLine("Punto di decollo", form.takeoff)
                DraftLine("Superficie / poligono", "${operation.areaPoints.size} vertici confermati")
                DraftLine("Altezza richiesta", form.verticalUpper)
                DraftLine("Aeroporto piu vicino", form.airportName)
                DraftLine("Distanza aeroporto", listOf(
                    form.airportDistanceKm.takeIf { it.isNotBlank() }?.let { "$it km" },
                    form.airportDistanceNm.takeIf { it.isNotBlank() }?.let { "$it NM" }
                ).filterNotNull().joinToString(" / "))
                DraftLine("Analisi", operation.zoneAnalysisSummary)
            }

            DraftSection("Dati pilota") {
                DraftBlock(listOf(pilot.optString("firstName"), pilot.optString("lastName")).filter { it.isNotBlank() }.joinToString(" "))
                DraftLine("Telefono", pilot.optString("phone"))
                DraftLine("Email", pilot.optString("email"))
                DraftLine("Attestato selezionato", form.license)
                DraftLine("Operatore", operator.optString("name"))
                DraftLine("Codice EASA", operator.optString("easaOperatorCode"))
                DraftLine("PEC", operator.optString("pec"))
            }

            DraftSection("Drone") {
                DraftBlock(listOf(drone.optString("manufacturer"), drone.optString("model")).filter { it.isNotBlank() }.joinToString(" "))
                DraftLine("Classe", drone.optString("classLabel"))
                DraftLine("Dichiarazione STS-01", if (drone.optBoolean("euSts01Registered")) "Registrata" else "Non registrata")
                DraftLine("Dichiarazione STS-02", if (drone.optBoolean("euSts02Registered")) "Registrata" else "Non registrata")
                DraftLine("Seriale", drone.optString("serialNumber"))
            }

            DraftSection("Dati pratica") {
                DraftTextField(
                    value = form.activityType,
                    onValueChange = { form = form.copy(activityType = it) },
                    label = "Tipo attivita"
                )
                DraftTextField(
                    value = form.operationStartDateTime,
                    onValueChange = { form = form.copy(operationStartDateTime = it) },
                    label = "Inizio operazioni",
                    placeholder = "2026-08-10T09:00"
                )
                DraftTextField(
                    value = form.operationEndDateTime,
                    onValueChange = { form = form.copy(operationEndDateTime = it) },
                    label = "Fine operazioni",
                    placeholder = "2026-08-10T11:00"
                )
                DraftTextField(
                    value = form.stampNumber,
                    onValueChange = { form = form.copy(stampNumber = it) },
                    label = "Marca da bollo"
                )
                DraftTextField(
                    value = form.notes,
                    onValueChange = { form = form.copy(notes = it) },
                    label = "Note"
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isSaving = true
                            actionMessage = null
                            val saved = onSaveRequestData(form)
                            if (saved != null) {
                                localDraft = saved
                                form = saved.requestData
                                actionMessage = "Dati pratica salvati."
                            } else {
                                actionMessage = "Non sono riuscito a salvare la pratica."
                            }
                            isSaving = false
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSaving) "Salvataggio..." else "Salva dati pratica")
                }
            }

            if (localDraft.missingFields.isNotEmpty()) {
                DraftSection("Dati mancanti") {
                    localDraft.missingFields.groupBy { it.group }.forEach { (group, fields) ->
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
                    OutlinedButton(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
                        Text("Vai al Profilo pilota")
                    }
                }
            }

            actionMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        isGenerating = true
                        actionMessage = "Generazione PDF in corso..."
                        runCatching {
                            AuthorizationPdfGenerator(context.applicationContext).generate(localDraft)
                        }.onSuccess { file ->
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Condividi PDF autorizzazione"))
                            actionMessage = "PDF generato e pronto per la condivisione."
                        }.onFailure { error ->
                            actionMessage = "Non sono riuscito a generare il PDF: ${error.message ?: "errore sconosciuto"}"
                        }
                        isGenerating = false
                    }
                },
                enabled = canGenerate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isGenerating) "Genero PDF..." else "Genera PDF ${localDraft.procedureType}")
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
private fun HeaderCard(draft: AuthorizationDraft) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Richiesta di autorizzazione",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            ProcedureBadge(draft.procedureType)
            Text(
                text = draft.procedureDescription(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SummaryCard(
    draft: AuthorizationDraft,
    requiredOperationCategory: String
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
            DraftLine("Stato pratica", draft.status)
            DraftLine("Categoria operativa richiesta", requiredOperationCategory)
            DraftLine("Procedura", draft.procedureType)
            DraftLine("Creata", formatTimestamp(draft.createdAt))
            DraftLine("Campi mancanti", draft.missingFields.size.toString())
        }
    }
}

@Composable
private fun ProcedureBadge(type: String) {
    Surface(
        color = if (type == "ATM09") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onError,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = type,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
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

@Composable
private fun DraftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isBlank()) null else ({ Text(placeholder) }),
        modifier = Modifier.fillMaxWidth(),
        singleLine = false
    )
}

@Composable
private fun MessageBlock(title: String, body: String, isBlocking: Boolean) {
    Surface(
        color = if (isBlocking) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (isBlocking) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun AuthorizationDraft.procedureDescription(): String =
    when (procedureType) {
        "ATM05" -> "Richiesta di nulla osta per operazioni nella zona selezionata"
        "ATM09" -> "Richiesta per operazioni UAS in area soggetta a procedura ATM-09"
        else -> "Richiesta di autorizzazione per l'operazione selezionata"
    }

private fun formatTimestamp(value: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(value))
