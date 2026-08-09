package it.droneskycheck.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.droneskycheck.app.data.AuthorizationDraft
import it.droneskycheck.app.data.AuthorizationDraftStatuses
import it.droneskycheck.app.data.LocalAuthorizationRepository
import it.droneskycheck.app.data.LocalDrone
import it.droneskycheck.app.data.LocalOperatorTypes
import it.droneskycheck.app.data.LocalPilotCertificate
import it.droneskycheck.app.data.LocalPilotProfile
import it.droneskycheck.app.data.LocalPilotRepository
import it.droneskycheck.app.data.LocalPilotSnapshot
import it.droneskycheck.app.data.LocalUasOperator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ProfileEditor {
    Pilot,
    Certificate,
    Operator,
    Drone
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilotProfileSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { LocalPilotRepository(context.applicationContext) }
    val authorizationRepository = remember(context) { LocalAuthorizationRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var snapshot by remember { mutableStateOf(LocalPilotSnapshot()) }
    var drafts by remember { mutableStateOf(emptyList<AuthorizationDraft>()) }
    var editor by remember { mutableStateOf<ProfileEditor?>(null) }
    var profileDraft by remember { mutableStateOf(LocalPilotProfile()) }
    var certificateDraft by remember { mutableStateOf(LocalPilotCertificate(categories = "A1_A3")) }
    var operatorDraft by remember { mutableStateOf(LocalUasOperator()) }
    var droneDraft by remember { mutableStateOf(LocalDrone(classLabel = "C1")) }

    fun reload() {
        scope.launch {
            snapshot = repository.getSnapshot()
            drafts = authorizationRepository.getDrafts()
        }
    }

    LaunchedEffect(repository, authorizationRepository) {
        snapshot = repository.getSnapshot()
        drafts = authorizationRepository.getDrafts()
    }

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
                text = "Profilo pilota",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Dati FREE locali, salvati solo su questo dispositivo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (editor) {
                ProfileEditor.Pilot -> PilotProfileForm(
                    draft = profileDraft,
                    onDraftChange = { profileDraft = it },
                    onCancel = { editor = null },
                    onSave = {
                        scope.launch {
                            repository.saveProfile(profileDraft)
                            editor = null
                            snapshot = repository.getSnapshot()
                        }
                    }
                )
                ProfileEditor.Certificate -> CertificateForm(
                    draft = certificateDraft,
                    onDraftChange = { certificateDraft = it },
                    onCancel = { editor = null },
                    onSave = {
                        scope.launch {
                            repository.saveCertificate(certificateDraft)
                            editor = null
                            snapshot = repository.getSnapshot()
                        }
                    }
                )
                ProfileEditor.Operator -> OperatorForm(
                    draft = operatorDraft,
                    onDraftChange = { operatorDraft = it },
                    onCancel = { editor = null },
                    onSave = {
                        scope.launch {
                            repository.saveOperator(operatorDraft)
                            editor = null
                            snapshot = repository.getSnapshot()
                        }
                    }
                )
                ProfileEditor.Drone -> DroneForm(
                    draft = droneDraft,
                    onDraftChange = { droneDraft = it },
                    onCancel = { editor = null },
                    onSave = {
                        scope.launch {
                            repository.saveDrone(droneDraft)
                            editor = null
                            snapshot = repository.getSnapshot()
                        }
                    }
                )
                null -> {
                    PilotSummaryCard(
                        profile = snapshot.profile,
                        onEdit = {
                            profileDraft = snapshot.profile ?: LocalPilotProfile()
                            editor = ProfileEditor.Pilot
                        }
                    )
                    CertificatesCard(
                        certificates = snapshot.certificates,
                        onAdd = {
                            certificateDraft = LocalPilotCertificate(categories = "A1_A3")
                            editor = ProfileEditor.Certificate
                        },
                        onEdit = {
                            certificateDraft = it
                            editor = ProfileEditor.Certificate
                        },
                        onDelete = { certificate ->
                            scope.launch {
                                repository.deleteCertificate(certificate.id)
                                snapshot = repository.getSnapshot()
                            }
                        }
                    )
                    OperatorCard(
                        operator = snapshot.operator,
                        onEdit = {
                            operatorDraft = snapshot.operator ?: LocalUasOperator(
                                name = snapshot.profile?.displayName.orEmpty()
                            )
                            editor = ProfileEditor.Operator
                        }
                    )
                    DronesCard(
                        drones = snapshot.drones,
                        selectedDrone = snapshot.selectedDrone,
                        onAdd = {
                            droneDraft = LocalDrone(classLabel = "C1", isSelected = snapshot.drones.isEmpty())
                            editor = ProfileEditor.Drone
                        },
                        onEdit = {
                            droneDraft = it
                            editor = ProfileEditor.Drone
                        },
                        onDelete = { drone ->
                            scope.launch {
                                repository.deleteDrone(drone.id)
                                snapshot = repository.getSnapshot()
                            }
                        },
                        onSelect = { drone ->
                            scope.launch {
                                repository.selectDrone(drone.id)
                                snapshot = repository.getSnapshot()
                            }
                        }
                    )
                    AuthorizationDraftsCard(drafts, onRefresh = { reload() })
                    TextButton(
                        onClick = {
                            reload()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Ricarica dati locali")
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorizationDraftsCard(
    drafts: List<AuthorizationDraft>,
    onRefresh: () -> Unit
) {
    val activeDraft = drafts.firstOrNull {
        it.status == AuthorizationDraftStatuses.Draft || it.status == AuthorizationDraftStatuses.Ready
    }
    ProfileCard(title = "Richieste di autorizzazione", actionLabel = "Aggiorna", onAction = onRefresh) {
        if (activeDraft == null) {
            EmptyText("Nessuna richiesta locale attiva.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${activeDraft.procedureType} - ${activeDraft.zoneName.ifBlank { "Zona" }}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                SummaryLine("Stato", activeDraft.status)
                SummaryLine("Creata", formatDraftDate(activeDraft.createdAt))
            }
        }
    }
}

@Composable
private fun PilotSummaryCard(
    profile: LocalPilotProfile?,
    onEdit: () -> Unit
) {
    ProfileCard(title = "Pilota", actionLabel = if (profile == null) "Completa" else "Modifica", onAction = onEdit) {
        Text(
            text = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Profilo non compilato",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        SummaryLine("Citta", profile?.city)
        SummaryLine("Telefono", profile?.phone)
        SummaryLine("Email", profile?.email)
        SummaryLine("Foto profilo", profile?.profilePhoto)
    }
}

@Composable
private fun CertificatesCard(
    certificates: List<LocalPilotCertificate>,
    onAdd: () -> Unit,
    onEdit: (LocalPilotCertificate) -> Unit,
    onDelete: (LocalPilotCertificate) -> Unit
) {
    ProfileCard(title = "Attestati", actionLabel = "Aggiungi", onAction = onAdd) {
        if (certificates.isEmpty()) {
            EmptyText("Nessun attestato di competenza registrato.")
        } else {
            certificates.forEachIndexed { index, certificate ->
                if (index > 0) HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = certificate.categoryList.joinToString(", ").ifBlank { "Attestato" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        SummaryLine("Numero", certificate.certificateNumber)
                        SummaryLine("Ente", certificate.issuingAuthority)
                        SummaryLine("Scadenza", certificate.expiryDate)
                    }
                    TextButton(onClick = { onEdit(certificate) }) { Text("Modifica") }
                    TextButton(onClick = { onDelete(certificate) }) { Text("Elimina") }
                }
            }
        }
    }
}

@Composable
private fun OperatorCard(
    operator: LocalUasOperator?,
    onEdit: () -> Unit
) {
    ProfileCard(title = "Operatore UAS", actionLabel = if (operator == null) "Configura" else "Modifica", onAction = onEdit) {
        Text(
            text = operator?.name?.takeIf { it.isNotBlank() } ?: "Operatore non configurato",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        SummaryLine("Tipo", operator?.type?.formatOperatorType())
        SummaryLine("Codice EASA", operator?.easaOperatorCode)
        SummaryLine("PEC", operator?.pec)
        SummaryLine("Assicurazione", listOfNotNull(
            operator?.insuranceCompany?.takeIf { it.isNotBlank() },
            operator?.insurancePolicyNumber?.takeIf { it.isNotBlank() },
            operator?.insuranceExpiresAt?.takeIf { it.isNotBlank() }
        ).joinToString(" - "))
    }
}

@Composable
private fun DronesCard(
    drones: List<LocalDrone>,
    selectedDrone: LocalDrone?,
    onAdd: () -> Unit,
    onEdit: (LocalDrone) -> Unit,
    onDelete: (LocalDrone) -> Unit,
    onSelect: (LocalDrone) -> Unit
) {
    ProfileCard(title = "Droni", actionLabel = "Aggiungi", onAction = onAdd) {
        if (drones.isEmpty()) {
            EmptyText("Nessun drone registrato.")
        } else {
            drones.forEachIndexed { index, drone ->
                if (index > 0) HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = drone.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        SummaryLine("Classe", drone.classLabel)
                        SummaryLine("Peso", drone.weight?.let { "$it g" })
                        SummaryLine("Seriale", drone.serialNumber)
                        if (selectedDrone?.id == drone.id) {
                            Text(
                                text = "Drone predefinito",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    TextButton(onClick = { onSelect(drone) }) { Text("Predef.") }
                    TextButton(onClick = { onEdit(drone) }) { Text("Modifica") }
                    TextButton(onClick = { onDelete(drone) }) { Text("Elimina") }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
            content()
        }
    }
}

@Composable
private fun PilotProfileForm(
    draft: LocalPilotProfile,
    onDraftChange: (LocalPilotProfile) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    EditCard(title = "Dati pilota", onCancel = onCancel, onSave = onSave) {
        ProfileTextField("Nome", draft.firstName) { onDraftChange(draft.copy(firstName = it)) }
        ProfileTextField("Cognome", draft.lastName) { onDraftChange(draft.copy(lastName = it)) }
        ProfileTextField("Citta", draft.city) { onDraftChange(draft.copy(city = it)) }
        ProfileTextField("Telefono", draft.phone) { onDraftChange(draft.copy(phone = it)) }
        ProfileTextField("Email", draft.email) { onDraftChange(draft.copy(email = it)) }
        ProfileTextField("Foto profilo", draft.profilePhoto) { onDraftChange(draft.copy(profilePhoto = it)) }
    }
}

@Composable
private fun CertificateForm(
    draft: LocalPilotCertificate,
    onDraftChange: (LocalPilotCertificate) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    EditCard(title = "Attestato", onCancel = onCancel, onSave = onSave) {
        ProfileTextField("Categorie abilitate", draft.categories) {
            onDraftChange(draft.copy(categories = it))
        }
        ProfileTextField("Numero attestato", draft.certificateNumber) {
            onDraftChange(draft.copy(certificateNumber = it))
        }
        ProfileTextField("Ente emittente", draft.issuingAuthority) {
            onDraftChange(draft.copy(issuingAuthority = it))
        }
        ProfileTextField("Data conseguimento", draft.issueDate) {
            onDraftChange(draft.copy(issueDate = it))
        }
        ProfileTextField("Data scadenza", draft.expiryDate) {
            onDraftChange(draft.copy(expiryDate = it))
        }
        ProfileTextField("Note", draft.notes) {
            onDraftChange(draft.copy(notes = it))
        }
    }
}

@Composable
private fun OperatorForm(
    draft: LocalUasOperator,
    onDraftChange: (LocalUasOperator) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    EditCard(title = "Operatore UAS", onCancel = onCancel, onSave = onSave) {
        ProfileTextField("Nome operatore", draft.name) { onDraftChange(draft.copy(name = it)) }
        ProfileTextField("Tipo", draft.type) { onDraftChange(draft.copy(type = it)) }
        ProfileTextField("Codice operatore EASA", draft.easaOperatorCode) {
            onDraftChange(draft.copy(easaOperatorCode = it))
        }
        ProfileTextField("PEC", draft.pec) { onDraftChange(draft.copy(pec = it)) }
        ProfileTextField("Compagnia assicurativa", draft.insuranceCompany) {
            onDraftChange(draft.copy(insuranceCompany = it))
        }
        ProfileTextField("Numero polizza", draft.insurancePolicyNumber) {
            onDraftChange(draft.copy(insurancePolicyNumber = it))
        }
        ProfileTextField("Scadenza polizza", draft.insuranceExpiresAt) {
            onDraftChange(draft.copy(insuranceExpiresAt = it))
        }
    }
}

@Composable
private fun DroneForm(
    draft: LocalDrone,
    onDraftChange: (LocalDrone) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    EditCard(title = "Drone", onCancel = onCancel, onSave = onSave) {
        ProfileTextField("Produttore", draft.manufacturer) { onDraftChange(draft.copy(manufacturer = it)) }
        ProfileTextField("Modello", draft.model) { onDraftChange(draft.copy(model = it)) }
        ProfileTextField("Classe", draft.classLabel) { onDraftChange(draft.copy(classLabel = it)) }
        ProfileTextField("Peso in grammi", draft.weight?.toString().orEmpty()) {
            onDraftChange(draft.copy(weight = it.replace(",", ".").toDoubleOrNull()))
        }
        ProfileTextField("Aircraft SN", draft.serialNumber) { onDraftChange(draft.copy(serialNumber = it)) }
        ProfileTextField("Radiocomandi", draft.remoteControllers) { onDraftChange(draft.copy(remoteControllers = it)) }
        ProfileTextField("Batterie", draft.batteries) { onDraftChange(draft.copy(batteries = it)) }
        ProfileTextField("Camere", draft.cameras) { onDraftChange(draft.copy(cameras = it)) }
        CheckboxLine("Remote ID", draft.remoteId) { onDraftChange(draft.copy(remoteId = it)) }
        CheckboxLine("Dichiarazione EU-STS-01", draft.euSts01Registered) {
            onDraftChange(draft.copy(euSts01Registered = it))
        }
        ProfileTextField("Data EU-STS-01", draft.euSts01DeclarationDate) {
            onDraftChange(draft.copy(euSts01DeclarationDate = it))
        }
        CheckboxLine("Dichiarazione EU-STS-02", draft.euSts02Registered) {
            onDraftChange(draft.copy(euSts02Registered = it))
        }
        ProfileTextField("Data EU-STS-02", draft.euSts02DeclarationDate) {
            onDraftChange(draft.copy(euSts02DeclarationDate = it))
        }
        CheckboxLine("Drone predefinito", draft.isSelected) {
            onDraftChange(draft.copy(isSelected = it))
        }
        ProfileTextField("Note", draft.notes) { onDraftChange(draft.copy(notes = it)) }
    }
}

@Composable
private fun EditCard(
    title: String,
    onCancel: () -> Unit,
    onSave: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text("Annulla")
                }
                Spacer(modifier = Modifier.size(10.dp))
                Button(onClick = onSave) {
                    Text("Salva")
                }
            }
        }
    }
}

@Composable
private fun ProfileTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = label != "Note" && label != "Categorie abilitate"
    )
}

@Composable
private fun CheckboxLine(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@Composable
private fun SummaryLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return

    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun String.formatOperatorType(): String =
    when (this) {
        LocalOperatorTypes.Association -> "Associazione"
        LocalOperatorTypes.Company -> "Azienda"
        LocalOperatorTypes.PublicBody -> "Ente pubblico"
        else -> "Operatore personale"
    }

private fun formatDraftDate(value: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(value))
