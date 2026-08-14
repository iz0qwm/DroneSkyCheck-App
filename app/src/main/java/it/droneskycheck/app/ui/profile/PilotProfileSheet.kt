package it.droneskycheck.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import it.droneskycheck.app.data.AuthorizationDraft
import it.droneskycheck.app.data.AuthorizationDraftStatuses
import it.droneskycheck.app.data.DscLogger
import it.droneskycheck.app.data.LocalAuthorizationRepository
import it.droneskycheck.app.data.LocalDrone
import it.droneskycheck.app.data.LocalOperatorTypes
import it.droneskycheck.app.data.LocalPilotCertificate
import it.droneskycheck.app.data.LocalPilotProfile
import it.droneskycheck.app.data.LocalPilotRepository
import it.droneskycheck.app.data.LocalPilotSnapshot
import it.droneskycheck.app.data.LocalUasOperator
import it.droneskycheck.app.data.drone.DroneCatalogMatchStatus
import it.droneskycheck.app.data.drone.DroneCatalogUpdateResult
import it.droneskycheck.app.data.drone.DroneTechnicalCatalogRepository
import it.droneskycheck.app.data.drone.DroneTechnicalCatalogResolver
import it.droneskycheck.app.data.drone.formatOneDecimal
import it.droneskycheck.app.data.drone.msToKmh
import it.droneskycheck.app.data.help.HelpManifest
import it.droneskycheck.app.ui.help.HelpBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private enum class ProfileEditor {
    Pilot,
    Certificate,
    Operator,
    Drone
}

private enum class DeleteTargetKind {
    Certificate,
    Drone
}

private data class DeleteTarget(
    val kind: DeleteTargetKind,
    val id: String,
    val title: String
)

private data class SelectOption(
    val value: String,
    val label: String
)

private val CertificateOptions = listOf(
    SelectOption("A1_A3", "A1/A3"),
    SelectOption("A2", "A2"),
    SelectOption("STS_01", "EU-STS-01"),
    SelectOption("STS_02", "EU-STS-02")
)

private val DroneClassOptions = listOf(
    SelectOption("C0", "C0"),
    SelectOption("C1", "C1"),
    SelectOption("C2", "C2"),
    SelectOption("C3", "C3"),
    SelectOption("C4", "C4"),
    SelectOption("C5", "C5"),
    SelectOption("C6", "C6"),
    SelectOption("LEGACY", "Legacy"),
    SelectOption("PRIVATELY_BUILT", "Privately Built"),
    SelectOption("UNMARKED", "Unmarked")
)

private val ProfileEditorSaver = Saver<ProfileEditor?, String>(
    save = { editor -> editor?.name.orEmpty() },
    restore = { value -> value.takeIf { it.isNotBlank() }?.let(ProfileEditor::valueOf) }
)

private val PilotProfileSaver = listSaver<LocalPilotProfile, String>(
    save = {
        listOf(
            it.firstName,
            it.lastName,
            it.city,
            it.phone,
            it.email,
            it.profilePhoto,
            it.skipPilotCompetencyChecks.toString()
        )
    },
    restore = {
        LocalPilotProfile(
            firstName = it.getOrNull(0).orEmpty(),
            lastName = it.getOrNull(1).orEmpty(),
            city = it.getOrNull(2).orEmpty(),
            phone = it.getOrNull(3).orEmpty(),
            email = it.getOrNull(4).orEmpty(),
            profilePhoto = it.getOrNull(5).orEmpty(),
            skipPilotCompetencyChecks = it.getOrNull(6).toBoolean()
        )
    }
)

private val CertificateSaver = listSaver<LocalPilotCertificate, String>(
    save = {
        listOf(
            it.id,
            it.issuingAuthority,
            it.certificateNumber,
            it.issueDate,
            it.expiryDate,
            it.categories,
            it.notes
        )
    },
    restore = {
        LocalPilotCertificate(
            id = it.getOrNull(0).orEmpty(),
            issuingAuthority = it.getOrNull(1).orEmpty(),
            certificateNumber = it.getOrNull(2).orEmpty(),
            issueDate = it.getOrNull(3).orEmpty(),
            expiryDate = it.getOrNull(4).orEmpty(),
            categories = it.getOrNull(5).orEmpty(),
            notes = it.getOrNull(6).orEmpty()
        )
    }
)

private val OperatorSaver = listSaver<LocalUasOperator, String>(
    save = {
        listOf(
            it.name,
            it.type,
            it.easaOperatorCode,
            it.pec,
            it.insuranceCompany,
            it.insurancePolicyNumber,
            it.insuranceExpiresAt
        )
    },
    restore = {
        LocalUasOperator(
            name = it.getOrNull(0).orEmpty(),
            type = it.getOrNull(1).orEmpty().ifBlank { LocalOperatorTypes.Individual },
            easaOperatorCode = it.getOrNull(2).orEmpty(),
            pec = it.getOrNull(3).orEmpty(),
            insuranceCompany = it.getOrNull(4).orEmpty(),
            insurancePolicyNumber = it.getOrNull(5).orEmpty(),
            insuranceExpiresAt = it.getOrNull(6).orEmpty()
        )
    }
)

private val DroneSaver = listSaver<LocalDrone, String>(
    save = {
        listOf(
            it.id,
            it.manufacturer,
            it.model,
            it.classLabel,
            it.weight?.toString().orEmpty(),
            it.manualMaxWindResistanceMs?.toString().orEmpty(),
            it.serialNumber,
            it.remoteControllers,
            it.batteries,
            it.cameras,
            it.remoteId.toString(),
            it.euSts01Registered.toString(),
            it.euSts01DeclarationDate,
            it.euSts02Registered.toString(),
            it.euSts02DeclarationDate,
            it.notes,
            it.isSelected.toString()
        )
    },
    restore = {
        LocalDrone(
            id = it.getOrNull(0).orEmpty(),
            manufacturer = it.getOrNull(1).orEmpty(),
            model = it.getOrNull(2).orEmpty(),
            classLabel = it.getOrNull(3).orEmpty(),
            weight = it.getOrNull(4)?.toDoubleOrNull(),
            manualMaxWindResistanceMs = it.getOrNull(5)?.toDoubleOrNull(),
            serialNumber = it.getOrNull(6).orEmpty(),
            remoteControllers = it.getOrNull(7).orEmpty(),
            batteries = it.getOrNull(8).orEmpty(),
            cameras = it.getOrNull(9).orEmpty(),
            remoteId = it.getOrNull(10).toBoolean(),
            euSts01Registered = it.getOrNull(11).toBoolean(),
            euSts01DeclarationDate = it.getOrNull(12).orEmpty(),
            euSts02Registered = it.getOrNull(13).toBoolean(),
            euSts02DeclarationDate = it.getOrNull(14).orEmpty(),
            notes = it.getOrNull(15).orEmpty(),
            isSelected = it.getOrNull(16).toBoolean()
        )
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilotProfileSheet(
    helpManifest: HelpManifest = HelpManifest.empty(),
    isHelpRefreshing: Boolean = false,
    helpRefreshMessage: String? = null,
    largeTextEnabled: Boolean = false,
    onLargeTextEnabledChanged: (Boolean) -> Unit = {},
    onRefreshHelp: () -> Unit = {},
    onRepeatTour: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { LocalPilotRepository(context.applicationContext) }
    val authorizationRepository = remember(context) { LocalAuthorizationRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var snapshot by remember { mutableStateOf(LocalPilotSnapshot()) }
    var drafts by remember { mutableStateOf(emptyList<AuthorizationDraft>()) }
    var editor by rememberSaveable(stateSaver = ProfileEditorSaver) { mutableStateOf<ProfileEditor?>(null) }
    var profileDraft by rememberSaveable(stateSaver = PilotProfileSaver) { mutableStateOf(LocalPilotProfile()) }
    var certificateDraft by rememberSaveable(stateSaver = CertificateSaver) {
        mutableStateOf(LocalPilotCertificate(categories = CertificateOptions.first().value))
    }
    var operatorDraft by rememberSaveable(stateSaver = OperatorSaver) { mutableStateOf(LocalUasOperator()) }
    var droneDraft by rememberSaveable(stateSaver = DroneSaver) { mutableStateOf(LocalDrone(classLabel = "C1")) }
    var formErrors by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var notice by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }
    var photoCropUri by rememberSaveable { mutableStateOf<String?>(null) }
    var personalExpanded by rememberSaveable { mutableStateOf(true) }
    var certificatesExpanded by rememberSaveable { mutableStateOf(true) }
    var operatorExpanded by rememberSaveable { mutableStateOf(true) }
    var dronesExpanded by rememberSaveable { mutableStateOf(true) }
    var draftsExpanded by rememberSaveable { mutableStateOf(false) }
    var isHelpSheetVisible by rememberSaveable { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            snapshot = repository.getSnapshot()
            drafts = authorizationRepository.getDrafts()
        }
    }

    fun showEditor(next: ProfileEditor) {
        formErrors = emptyList()
        notice = ""
        editor = next
    }

    LaunchedEffect(repository, authorizationRepository) {
        snapshot = repository.getSnapshot()
        drafts = authorizationRepository.getDrafts()
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) photoCropUri = uri.toString()
    }

    photoCropUri?.let { uriString ->
        ProfilePhotoCropDialog(
            sourceUri = uriString.toUri(),
            onDismiss = { photoCropUri = null },
            onConfirm = { cropped ->
                scope.launch {
                    val storedPath = withContext(Dispatchers.IO) {
                        saveProfilePhoto(context.applicationContext, cropped)
                    }
                    val baseProfile = if (editor == ProfileEditor.Pilot) {
                        profileDraft
                    } else {
                        snapshot.profile ?: LocalPilotProfile()
                    }
                    val updatedProfile = baseProfile.copy(profilePhoto = storedPath)
                    profileDraft = updatedProfile
                    repository.saveProfile(updatedProfile)
                    snapshot = repository.getSnapshot()
                    notice = "Foto profilo aggiornata."
                    photoCropUri = null
                }
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Conferma eliminazione") },
            text = { Text("Eliminare ${target.title}? L'azione rimuove il dato locale da questo dispositivo.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            when (target.kind) {
                                DeleteTargetKind.Certificate -> repository.deleteCertificate(target.id)
                                DeleteTargetKind.Drone -> repository.deleteDrone(target.id)
                            }
                            snapshot = repository.getSnapshot()
                            notice = "${target.title} eliminato."
                            pendingDelete = null
                        }
                    }
                ) {
                    Text("Elimina")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Annulla")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (editor) {
                ProfileEditor.Pilot -> PilotProfileForm(
                    draft = profileDraft,
                    errors = formErrors,
                    onPickPhoto = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemovePhoto = { profileDraft = profileDraft.copy(profilePhoto = "") },
                    onDraftChange = { profileDraft = it },
                    onCancel = { editor = null },
                    onSave = {
                        val errors = validateProfile(profileDraft)
                        formErrors = errors
                        if (errors.isEmpty()) {
                            scope.launch {
                                repository.saveProfile(profileDraft)
                                editor = null
                                snapshot = repository.getSnapshot()
                                notice = "Dati personali salvati."
                            }
                        }
                    }
                )

                ProfileEditor.Certificate -> CertificateForm(
                    draft = certificateDraft,
                    errors = formErrors,
                    onDraftChange = { certificateDraft = it },
                    onCancel = { editor = null },
                    onSave = {
                        val errors = validateCertificate(certificateDraft)
                        formErrors = errors
                        if (errors.isEmpty()) {
                            scope.launch {
                                repository.saveCertificate(certificateDraft)
                                editor = null
                                snapshot = repository.getSnapshot()
                                notice = "Attestato salvato."
                            }
                        }
                    }
                )

                ProfileEditor.Operator -> OperatorForm(
                    draft = operatorDraft.copy(type = LocalOperatorTypes.Individual),
                    errors = formErrors,
                    onDraftChange = { operatorDraft = it.copy(type = LocalOperatorTypes.Individual) },
                    onCancel = { editor = null },
                    onSave = {
                        val normalizedDraft = operatorDraft.copy(type = LocalOperatorTypes.Individual)
                        val errors = validateOperator(normalizedDraft)
                        formErrors = errors
                        if (errors.isEmpty()) {
                            scope.launch {
                                repository.saveOperator(normalizedDraft)
                                editor = null
                                snapshot = repository.getSnapshot()
                                notice = "Operatore UAS salvato."
                            }
                        }
                    }
                )

                ProfileEditor.Drone -> DroneForm(
                    draft = droneDraft,
                    errors = formErrors,
                    onDraftChange = { droneDraft = it },
                    onCancel = { editor = null },
                    onSave = {
                        val errors = validateDrone(droneDraft)
                        formErrors = errors
                        if (errors.isEmpty()) {
                            scope.launch {
                                repository.saveDrone(constrainEuStsForClass(droneDraft))
                                editor = null
                                snapshot = repository.getSnapshot()
                                notice = "Drone salvato."
                            }
                        }
                    }
                )

                null -> {
                    PilotHeader(
                        profile = snapshot.profile,
                        onEdit = {
                            profileDraft = snapshot.profile ?: LocalPilotProfile()
                            showEditor(ProfileEditor.Pilot)
                        },
                        onPickPhoto = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    if (notice.isNotBlank()) {
                        NoticeBanner(notice)
                    }
                    PersonalDataCard(
                        profile = snapshot.profile,
                        expanded = personalExpanded,
                        onExpandedChange = { personalExpanded = it },
                        onEdit = {
                            profileDraft = snapshot.profile ?: LocalPilotProfile()
                            showEditor(ProfileEditor.Pilot)
                        }
                    )
                    CertificatesSection(
                        certificates = snapshot.certificates,
                        expanded = certificatesExpanded,
                        onExpandedChange = { certificatesExpanded = it },
                        onAdd = {
                            certificateDraft = LocalPilotCertificate(categories = CertificateOptions.first().value)
                            showEditor(ProfileEditor.Certificate)
                        },
                        onEdit = {
                            certificateDraft = it.copy(categories = primaryCertificateCategory(it))
                            showEditor(ProfileEditor.Certificate)
                        },
                        onDelete = { certificate ->
                            pendingDelete = DeleteTarget(
                                kind = DeleteTargetKind.Certificate,
                                id = certificate.id,
                                title = certificate.titleLabel()
                            )
                        }
                    )
                    OperatorSection(
                        operator = snapshot.operator,
                        expanded = operatorExpanded,
                        onExpandedChange = { operatorExpanded = it },
                        onEdit = {
                            operatorDraft = snapshot.operator ?: LocalUasOperator(
                                name = snapshot.profile?.displayName.orEmpty(),
                                type = LocalOperatorTypes.Individual
                            )
                            showEditor(ProfileEditor.Operator)
                        }
                    )
                    DroneFleetSection(
                        drones = snapshot.drones,
                        selectedDrone = snapshot.selectedDrone,
                        expanded = dronesExpanded,
                        onExpandedChange = { dronesExpanded = it },
                        onAdd = {
                            droneDraft = LocalDrone(classLabel = "C1", isSelected = snapshot.drones.isEmpty())
                            showEditor(ProfileEditor.Drone)
                        },
                        onEdit = {
                            droneDraft = it
                            showEditor(ProfileEditor.Drone)
                        },
                        onDelete = { drone ->
                            pendingDelete = DeleteTarget(
                                kind = DeleteTargetKind.Drone,
                                id = drone.id,
                                title = drone.displayName
                            )
                        },
                        onSelect = { drone ->
                            scope.launch {
                                repository.selectDrone(drone.id)
                                snapshot = repository.getSnapshot()
                                notice = "${drone.displayName} impostato come predefinito."
                            }
                        }
                    )
                    AuthorizationDraftsCard(
                        drafts = drafts,
                        expanded = draftsExpanded,
                        onExpandedChange = { draftsExpanded = it },
                        onRefresh = { reload() }
                    )
                    AccessibilityCard(
                        largeTextEnabled = largeTextEnabled,
                        onLargeTextEnabledChanged = onLargeTextEnabledChanged
                    )
                    HelpAccessCard(
                        onOpenHelp = { isHelpSheetVisible = true },
                        onRepeatTour = {
                            onDismiss()
                            onRepeatTour()
                        }
                    )
                    OutlinedButton(
                        onClick = { reload() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ricarica")
                    }
                }
            }
        }
    }

    if (isHelpSheetVisible) {
        HelpBottomSheet(
            manifest = helpManifest,
            isRefreshInProgress = isHelpRefreshing,
            refreshMessage = helpRefreshMessage,
            onRefresh = onRefreshHelp,
            onDismiss = { isHelpSheetVisible = false }
        )
    }
}

@Composable
private fun PilotHeader(
    profile: LocalPilotProfile?,
    onEdit: () -> Unit,
    onPickPhoto: () -> Unit
) {
    val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Profilo pilota"
    val stackActions = LocalDensity.current.fontScale >= 1.5f
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileAvatar(
                profile = profile,
                sizeDp = 108,
                onClick = onPickPhoto
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = profile?.city?.takeIf { it.isNotBlank() } ?: "Dati FREE locali su questo dispositivo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (stackActions) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onPickPhoto, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (profile?.profilePhoto.isNullOrBlank()) "Aggiungi foto" else "Cambia foto")
                    }
                    Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Modifica")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onPickPhoto) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (profile?.profilePhoto.isNullOrBlank()) "Aggiungi foto" else "Cambia foto")
                    }
                    Button(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Modifica")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(
    profile: LocalPilotProfile?,
    sizeDp: Int,
    onClick: (() -> Unit)? = null
) {
    val photoPath = profile?.profilePhoto.orEmpty()
    val initials = profile.initials()
    val bitmap = remember(photoPath) {
        loadProfileBitmap(photoPath)
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Foto profilo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initials,
                style = if (sizeDp >= 90) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun NoticeBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PersonalDataCard(
    profile: LocalPilotProfile?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    ProfileCard(
        title = "Dati personali",
        subtitle = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Nome, contatti e foto profilo",
        icon = Icons.Default.Person,
        actionIcon = Icons.Default.Edit,
        actionLabel = if (profile == null) "Completa dati personali" else "Modifica dati personali",
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        onAction = onEdit
    ) {
        if (profile == null || listOf(profile.firstName, profile.lastName, profile.city, profile.phone, profile.email).all { it.isBlank() }) {
            EmptyText("Completa i dati del pilota per averli pronti nelle richieste locali.")
        } else {
            SummaryValue("Nome e Cognome", profile.displayName)
            SummaryValue("Citta", profile.city)
            SummaryValue("Telefono", profile.phone)
            SummaryValue("Email", profile.email)
        }
    }
}

@Composable
private fun CertificatesSection(
    certificates: List<LocalPilotCertificate>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onEdit: (LocalPilotCertificate) -> Unit,
    onDelete: (LocalPilotCertificate) -> Unit
) {
    val subtitle = when (certificates.size) {
        0 -> "Nessun attestato salvato"
        1 -> "1 attestato salvato"
        else -> "${certificates.size} attestati salvati"
    }
    ProfileCard(
        title = "Attestati",
        subtitle = subtitle,
        icon = Icons.Default.Badge,
        actionIcon = Icons.Default.Add,
        actionLabel = "Aggiungi attestato",
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        onAction = onAdd
    ) {
        if (certificates.isEmpty()) {
            EmptyText("Nessun attestato di competenza registrato.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                certificates.forEach { certificate ->
                    CertificateRow(
                        certificate = certificate,
                        onEdit = { onEdit(certificate) },
                        onDelete = { onDelete(certificate) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CertificateRow(
    certificate: LocalPilotCertificate,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                ProfileIconChip(
                    icon = Icons.Default.Badge,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = certificate.titleLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = certificate.issuingAuthority.ifBlank { "Ente non indicato" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifica attestato")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Elimina attestato")
                    }
                }
            }
            SummaryValue("Numero", certificate.certificateNumber)
            SummaryValue("Conseguito", formatDateForDisplay(certificate.issueDate))
            SummaryValue("Scadenza", formatDateForDisplay(certificate.expiryDate))
            SummaryValue("Note", certificate.notes)
        }
    }
}

@Composable
private fun OperatorSection(
    operator: LocalUasOperator?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    ProfileCard(
        title = "Operatore UAS",
        subtitle = operator?.easaOperatorCode?.takeIf { it.isNotBlank() } ?: "Codice UAS, PEC e assicurazione",
        icon = Icons.Default.Shield,
        actionIcon = Icons.Default.Edit,
        actionLabel = if (operator == null) "Configura operatore UAS" else "Modifica operatore UAS",
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        onAction = onEdit
    ) {
        if (operator == null || listOf(operator.name, operator.easaOperatorCode, operator.pec, operator.insuranceCompany, operator.insurancePolicyNumber).all { it.isBlank() }) {
            SummaryValue("Tipo", formatOperatorType(operator?.type ?: LocalOperatorTypes.Individual))
            EmptyText("Aggiungi codice operatore, PEC e assicurazione quando disponibili.")
        } else {
            SummaryValue("Tipo", formatOperatorType(operator.type))
            SummaryValue("Nome operatore", operator.name)
            SummaryValue("Codice operatore UAS", operator.easaOperatorCode)
            SummaryValue("PEC", operator.pec)
            SummaryValue(
                "Assicurazione",
                listOf(
                    operator.insuranceCompany,
                    operator.insurancePolicyNumber,
                    formatDateForDisplay(operator.insuranceExpiresAt)
                ).filter { it.isNotBlank() }.joinToString(" - ")
            )
        }
    }
}

@Composable
private fun DroneFleetSection(
    drones: List<LocalDrone>,
    selectedDrone: LocalDrone?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onEdit: (LocalDrone) -> Unit,
    onDelete: (LocalDrone) -> Unit,
    onSelect: (LocalDrone) -> Unit
) {
    val subtitle = selectedDrone?.displayName?.let { "Predefinito: $it" } ?: when (drones.size) {
        0 -> "Nessun drone salvato"
        1 -> "1 drone salvato"
        else -> "${drones.size} droni salvati"
    }
    ProfileCard(
        title = "I miei droni",
        subtitle = subtitle,
        icon = Icons.Default.FlightTakeoff,
        actionIcon = Icons.Default.Add,
        actionLabel = "Aggiungi drone",
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        onAction = onAdd
    ) {
        if (drones.isEmpty()) {
            EmptyText("Nessun drone registrato.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                drones.forEach { drone ->
                    DroneRow(
                        drone = drone,
                        isSelected = selectedDrone?.id == drone.id,
                        onSelect = { onSelect(drone) },
                        onEdit = { onEdit(drone) },
                        onDelete = { onDelete(drone) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DroneRow(
    drone: LocalDrone,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSelect, enabled = !isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = if (isSelected) "Drone predefinito" else "Imposta drone predefinito"
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifica drone")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina drone")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                ProfileIconChip(
                    icon = Icons.Default.FlightTakeoff,
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    }
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = drone.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatDroneClass(drone.classLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SummaryValue("S/N", drone.serialNumber)
                }
            }
            if (isSelected) {
                AssistChip(
                    onClick = {},
                    label = { Text("Predefinito") }
                )
            }
            SummaryValue("Dichiarazione EU-STS", formatEuStsSummary(drone))
            CompactSerialSummary("S/N Batterie", drone.batteries)
            CompactSerialSummary("S/N Radiocomandi", drone.remoteControllers)
            CompactSerialSummary("S/N Camere", drone.cameras)
        }
    }
}

@Composable
private fun AuthorizationDraftsCard(
    drafts: List<AuthorizationDraft>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    val activeDraft = drafts.firstOrNull {
        it.status == AuthorizationDraftStatuses.Draft || it.status == AuthorizationDraftStatuses.Ready
    }
    ProfileCard(
        title = "Richieste",
        subtitle = activeDraft?.zoneName?.takeIf { it.isNotBlank() } ?: "Autorizzazioni locali salvate",
        icon = Icons.Default.Description,
        actionIcon = Icons.Default.Refresh,
        actionLabel = "Aggiorna richieste",
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        onAction = onRefresh
    ) {
        if (activeDraft == null) {
            EmptyText("Nessuna richiesta locale attiva.")
        } else {
            Text(
                text = "${activeDraft.procedureType} - ${activeDraft.zoneName.ifBlank { "Zona" }}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            SummaryValue("Stato", activeDraft.status)
            SummaryValue("Creata", formatDraftDate(activeDraft.createdAt))
        }
    }
}

@Composable
private fun HelpAccessCard(
    onOpenHelp: () -> Unit,
    onRepeatTour: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileIconChip(icon = Icons.Default.Description)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Guida Drone Sky Check",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tour guidato e spiegazioni operative",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenHelp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Apri guida"
                    )
                }
            }
            Text(
                text = "Apri la guida generale oppure ripeti il tour iniziale.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onOpenHelp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guida e informazioni")
            }
            OutlinedButton(
                onClick = onRepeatTour,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ripeti tour guidato")
            }
        }
    }
}

@Composable
private fun AccessibilityCard(
    largeTextEnabled: Boolean,
    onLargeTextEnabledChanged: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "ACCESSIBILITA",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileIconChip(icon = Icons.Default.Visibility)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Testo piu grande",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Aumenta la leggibilita dei testi nell'app Drone Sky Check.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = largeTextEnabled,
                    onCheckedChange = onLargeTextEnabledChanged
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    actionIcon: ImageVector,
    actionLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAction: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileIconChip(icon = icon)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onAction) {
                    Icon(actionIcon, contentDescription = actionLabel)
                }
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Chiudi $title" else "Apri $title"
                    )
                }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun ProfileIconChip(
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun PilotProfileForm(
    draft: LocalPilotProfile,
    errors: List<String>,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDraftChange: (LocalPilotProfile) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    EditCard(title = "Modifica profilo", errors = errors, onCancel = onCancel, onSave = onSave) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProfileAvatar(profile = draft, sizeDp = 76, onClick = onPickPhoto)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onPickPhoto, modifier = Modifier.fillMaxWidth()) {
                    Text(if (draft.profilePhoto.isBlank()) "Scegli foto" else "Cambia foto")
                }
                if (draft.profilePhoto.isNotBlank()) {
                    TextButton(onClick = onRemovePhoto, modifier = Modifier.fillMaxWidth()) {
                        Text("Rimuovi foto")
                    }
                }
            }
        }
        ProfileTextField("Nome", draft.firstName, placeholder = "Raffaello") {
            onDraftChange(draft.copy(firstName = it))
        }
        ProfileTextField("Cognome", draft.lastName, placeholder = "Di Martino") {
            onDraftChange(draft.copy(lastName = it))
        }
        ProfileTextField("Citta", draft.city, placeholder = "Roma") {
            onDraftChange(draft.copy(city = it))
        }
        ProfileTextField(
            label = "Telefono",
            value = draft.phone,
            placeholder = "+39 ...",
            keyboardType = KeyboardType.Phone
        ) {
            onDraftChange(draft.copy(phone = it))
        }
        ProfileTextField(
            label = "Email",
            value = draft.email,
            placeholder = "nome@example.com",
            keyboardType = KeyboardType.Email,
            isError = errors.any { it.contains("email", ignoreCase = true) }
        ) {
            onDraftChange(draft.copy(email = it))
        }
    }
}

@Composable
private fun CertificateForm(
    draft: LocalPilotCertificate,
    errors: List<String>,
    onDraftChange: (LocalPilotCertificate) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    EditCard(title = if (draft.id.isBlank()) "Aggiungi attestato" else "Modifica attestato", errors = errors, onCancel = onCancel, onSave = onSave) {
        OptionSelector(
            label = "Tipologia",
            value = primaryCertificateCategory(draft),
            options = CertificateOptions,
            placeholder = "Seleziona attestato",
            onValueChange = { onDraftChange(draft.copy(categories = it)) }
        )
        ProfileTextField("Numero attestato", draft.certificateNumber, placeholder = "ITA-...") {
            onDraftChange(draft.copy(certificateNumber = it))
        }
        ProfileTextField("Ente", draft.issuingAuthority, placeholder = "ENAC") {
            onDraftChange(draft.copy(issuingAuthority = it))
        }
        ProfileTextField(
            label = "Data conseguimento",
            value = draft.issueDate,
            placeholder = "AAAA-MM-GG",
            keyboardType = KeyboardType.Number
        ) {
            onDraftChange(draft.copy(issueDate = it))
        }
        ProfileTextField(
            label = "Data scadenza",
            value = draft.expiryDate,
            placeholder = "AAAA-MM-GG",
            keyboardType = KeyboardType.Number
        ) {
            onDraftChange(draft.copy(expiryDate = it))
        }
        ProfileTextField("Note", draft.notes, singleLine = false, minLines = 3) {
            onDraftChange(draft.copy(notes = it))
        }
    }
}

@Composable
private fun OperatorForm(
    draft: LocalUasOperator,
    errors: List<String>,
    onDraftChange: (LocalUasOperator) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    EditCard(title = "Modifica Operatore UAS", errors = errors, onCancel = onCancel, onSave = onSave) {
        ReadOnlyField("Tipo", formatOperatorType(LocalOperatorTypes.Individual))
        ProfileTextField("Nome operatore", draft.name, placeholder = "Operatore personale") {
            onDraftChange(draft.copy(name = it))
        }
        ProfileTextField("Codice operatore UAS", draft.easaOperatorCode, placeholder = "ITA...") {
            onDraftChange(draft.copy(easaOperatorCode = it))
        }
        Text(
            text = "Inserisci solo la parte pubblica del codice, senza i tre caratteri segreti finali.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ProfileTextField("PEC", draft.pec, placeholder = "pec@example.it", keyboardType = KeyboardType.Email) {
            onDraftChange(draft.copy(pec = it))
        }
        HorizontalDivider()
        Text("Assicurazione", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        ProfileTextField("Compagnia", draft.insuranceCompany) {
            onDraftChange(draft.copy(insuranceCompany = it))
        }
        ProfileTextField("Numero polizza", draft.insurancePolicyNumber) {
            onDraftChange(draft.copy(insurancePolicyNumber = it))
        }
        ProfileTextField("Scadenza polizza", draft.insuranceExpiresAt, placeholder = "AAAA-MM-GG", keyboardType = KeyboardType.Number) {
            onDraftChange(draft.copy(insuranceExpiresAt = it))
        }
    }
}

@Composable
private fun DroneForm(
    draft: LocalDrone,
    errors: List<String>,
    onDraftChange: (LocalDrone) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val catalogRepository = remember(context) { DroneTechnicalCatalogRepository(context.applicationContext) }
    var catalogResolver by remember { mutableStateOf(DroneTechnicalCatalogResolver.empty()) }
    LaunchedEffect(catalogRepository) {
        catalogResolver = withContext(Dispatchers.IO) { catalogRepository.resolver() }
        DscLogger.debug(
            "DroneCatalog",
            "DroneCatalog: profile sheet using catalog schema=${catalogResolver.catalog.schemaVersion} " +
                "catalog=${catalogResolver.catalog.catalogVersion} drones=${catalogResolver.catalog.drones.size}"
        )
        val update = withContext(Dispatchers.IO) { catalogRepository.checkForUpdatesIfDue() }
        DscLogger.debug("DroneCatalog", "DroneCatalog: profile sheet update result=$update")
        if (update is DroneCatalogUpdateResult.Installed) {
            catalogResolver = withContext(Dispatchers.IO) { catalogRepository.resolver() }
            DscLogger.debug(
                "DroneCatalog",
                "DroneCatalog: profile sheet switched to catalog schema=${catalogResolver.catalog.schemaVersion} " +
                    "catalog=${catalogResolver.catalog.catalogVersion} drones=${catalogResolver.catalog.drones.size}"
            )
        }
    }
    val catalogMatch = remember(catalogResolver, draft.manufacturer, draft.model) {
        catalogResolver.resolve(draft.manufacturer, draft.model)
    }
    EditCard(title = if (draft.id.isBlank()) "Aggiungi drone" else "Modifica drone", errors = errors, onCancel = onCancel, onSave = onSave) {
        ProfileTextField("Produttore", draft.manufacturer, placeholder = "DJI") {
            onDraftChange(draft.copy(manufacturer = it))
        }
        ProfileTextField("Modello", draft.model, placeholder = "Air 3S") {
            onDraftChange(draft.copy(model = it))
        }
        OptionSelector(
            label = "Classe",
            value = normalizeDroneClassForUi(draft.classLabel),
            options = DroneClassOptions,
            placeholder = "Seleziona classe",
            onValueChange = { onDraftChange(constrainEuStsForClass(draft.copy(classLabel = it))) }
        )
        ProfileTextField(
            label = "Peso (g)",
            value = draft.weight?.toString().orEmpty(),
            placeholder = "724",
            keyboardType = KeyboardType.Decimal
        ) {
            onDraftChange(draft.copy(weight = it.replace(",", ".").toDoubleOrNull()))
        }
        DroneOperationalDataSection(
            draft = draft,
            catalogMatch = catalogMatch,
            onDraftChange = onDraftChange
        )
        ProfileTextField("Aircraft S/N", draft.serialNumber, placeholder = "Numero di serie drone") {
            onDraftChange(draft.copy(serialNumber = it))
        }
        SerialListEditor(
            title = "S/N Batterie",
            description = "Inserisci i numeri di serie, se disponibili.",
            value = draft.batteries,
            onValueChange = { onDraftChange(draft.copy(batteries = it)) }
        )
        SerialListEditor(
            title = "S/N Radiocomandi",
            description = "Opzionale.",
            value = draft.remoteControllers,
            onValueChange = { onDraftChange(draft.copy(remoteControllers = it)) }
        )
        SerialListEditor(
            title = "S/N Camere",
            description = "Opzionale.",
            value = draft.cameras,
            onValueChange = { onDraftChange(draft.copy(cameras = it)) }
        )
        CheckboxLine("Remote ID disponibile", draft.remoteId) {
            onDraftChange(draft.copy(remoteId = it))
        }
        EuStsEditor(
            draft = draft,
            onDraftChange = onDraftChange
        )
        CheckboxLine("Drone predefinito", draft.isSelected) {
            onDraftChange(draft.copy(isSelected = it))
        }
        ProfileTextField("Note", draft.notes, singleLine = false, minLines = 3) {
            onDraftChange(draft.copy(notes = it))
        }
    }
}

@Composable
private fun DroneOperationalDataSection(
    draft: LocalDrone,
    catalogMatch: it.droneskycheck.app.data.drone.DroneCatalogMatchResult,
    onDraftChange: (LocalDrone) -> Unit
) {
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dati operativi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = catalogMatch.profileSummaryText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        catalogMatch.matchedDrone?.let { matched ->
            Text(
                text = listOfNotNull(
                    matched.maxWindResistanceMs?.windLimitText()?.let { "Vento $it" },
                    matched.operatingTemperatureMinC?.formatOneDecimal()?.let { min ->
                        matched.operatingTemperatureMaxC?.formatOneDecimal()?.let { max -> "Temperatura $min / $max C" }
                    },
                    matched.ingressProtectionRating?.let { "Protezione $it" },
                    matched.source.name
                ).joinToString(" - "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ProfileTextField(
            label = "Resistenza max vento dichiarata (m/s)",
            value = draft.manualMaxWindResistanceMs?.toString().orEmpty(),
            placeholder = catalogMatch.matchedDrone?.maxWindResistanceMs?.formatOneDecimal() ?: "10.7",
            keyboardType = KeyboardType.Decimal
        ) {
            onDraftChange(draft.copy(manualMaxWindResistanceMs = it.replace(",", ".").toDoubleOrNull()))
        }
        Text(
            text = if (draft.manualMaxWindResistanceMs != null) {
                val kmh = draft.manualMaxWindResistanceMs.msToKmh().formatOneDecimal()
                "Dato personalizzato: ${draft.manualMaxWindResistanceMs.formatOneDecimal()} m/s ($kmh km/h)."
            } else {
                "Campo opzionale. Se compilato, prevale sul catalogo."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EuStsEditor(
    draft: LocalDrone,
    onDraftChange: (LocalDrone) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Dichiarazione EU-STS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Registrazione D-Flight. Validita: 2 anni dalla data della dichiarazione.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        EuStsScenarioLine(
            label = "EU-STS-01",
            requiredClass = "C5",
            classLabel = draft.classLabel,
            registered = draft.euSts01Registered,
            declarationDate = draft.euSts01DeclarationDate,
            onRegisteredChange = {
                onDraftChange(draft.copy(euSts01Registered = it, euSts01DeclarationDate = if (it) draft.euSts01DeclarationDate else ""))
            },
            onDateChange = { onDraftChange(draft.copy(euSts01DeclarationDate = it)) }
        )
        EuStsScenarioLine(
            label = "EU-STS-02",
            requiredClass = "C6",
            classLabel = draft.classLabel,
            registered = draft.euSts02Registered,
            declarationDate = draft.euSts02DeclarationDate,
            onRegisteredChange = {
                onDraftChange(draft.copy(euSts02Registered = it, euSts02DeclarationDate = if (it) draft.euSts02DeclarationDate else ""))
            },
            onDateChange = { onDraftChange(draft.copy(euSts02DeclarationDate = it)) }
        )
    }
}

@Composable
private fun EuStsScenarioLine(
    label: String,
    requiredClass: String,
    classLabel: String,
    registered: Boolean,
    declarationDate: String,
    onRegisteredChange: (Boolean) -> Unit,
    onDateChange: (String) -> Unit
) {
    val eligible = normalizeDroneClassForUi(classLabel) == requiredClass
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(
                    checked = eligible && registered,
                    enabled = eligible,
                    onCheckedChange = onRegisteredChange
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("$label registrata su D-Flight", fontWeight = FontWeight.Medium)
                    Text(
                        text = if (eligible) "Classe compatibile: $requiredClass" else "$label richiede un drone classe $requiredClass",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ProfileTextField(
                label = "Data dichiarazione $label",
                value = if (eligible && registered) declarationDate else "",
                placeholder = "AAAA-MM-GG",
                keyboardType = KeyboardType.Number,
                enabled = eligible && registered
            ) {
                onDateChange(it)
            }
            if (eligible && registered && declarationDate.isNotBlank()) {
                Text(
                    text = "Valida fino al ${formatDateForDisplay(addYearsToIsoDate(declarationDate, 2))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SerialListEditor(
    title: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var newSerial by rememberSaveable(title) { mutableStateOf("") }
    val serials = splitSerials(value)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (serials.isEmpty()) {
            EmptyText("Nessun numero di serie inserito.")
        } else {
            serials.forEach { serial ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = serial,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { onValueChange((serials - serial).joinToString("\n")) }) {
                        Text("Rimuovi")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newSerial,
                onValueChange = { newSerial = it },
                label = { Text("Nuovo S/N") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = {
                    val normalized = newSerial.trim()
                    if (normalized.isNotBlank()) {
                        onValueChange((serials + normalized).distinctBy { it.lowercase(Locale.ITALY) }.joinToString("\n"))
                        newSerial = ""
                    }
                }
            ) {
                Text("Aggiungi")
            }
        }
    }
}

@Composable
private fun ProfilePhotoCropDialog(
    sourceUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(sourceUri) { mutableStateOf(false) }
    var zoom by rememberSaveable(sourceUri.toString()) { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable(sourceUri.toString(), "x") { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable(sourceUri.toString(), "y") { mutableFloatStateOf(0f) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(sourceUri) {
        bitmap = withContext(Dispatchers.IO) {
            decodeBitmap(context.applicationContext, sourceUri)
        }
        loadFailed = bitmap == null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ritaglia foto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Trascina e pizzica l'immagine. Il ritaglio sara quadrato e mostrato come avatar circolare.",
                    style = MaterialTheme.typography.bodyMedium
                )
                val source = bitmap
                when {
                    loadFailed -> EmptyText("Non riesco a leggere questa immagine.")
                    source == null -> EmptyText("Caricamento immagine...")
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .onGloballyPositioned { previewSize = it.size }
                                .pointerInput(source) {
                                    detectTransformGestures { _, pan, gestureZoom, _ ->
                                        zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = source.asImageBitmap(),
                                contentDescription = "Anteprima ritaglio",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = zoom,
                                        scaleY = zoom,
                                        translationX = offsetX,
                                        translationY = offsetY
                                    ),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = zoom <= 1.05f,
                                onClick = {
                                    zoom = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                },
                                label = { Text("Reset") }
                            )
                            Text(
                                text = "Zoom ${String.format(Locale.ITALY, "%.1fx", zoom)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = bitmap != null,
                onClick = {
                    bitmap?.let {
                        onConfirm(cropBitmapToSquare(it, zoom, Offset(offsetX, offsetY), previewSize))
                    }
                }
            ) {
                Text("Conferma")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}

@Composable
private fun EditCard(
    title: String,
    errors: List<String>,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (errors.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        errors.forEach { error ->
                            Text(error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            content()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Annulla")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(onClick = onSave) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        isError = isError
    )
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = value,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun OptionSelector(
    label: String,
    value: String,
    options: List<SelectOption>,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.value == value }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selected?.label ?: placeholder,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("v")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onValueChange(option.value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
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
private fun ActionRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun SummaryValue(label: String, value: String?) {
    if (value.isNullOrBlank()) return

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactSerialSummary(label: String, value: String) {
    val serials = splitSerials(value)
    if (serials.isEmpty()) return
    SummaryValue(label, serials.joinToString(", "))
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun LocalPilotProfile?.initials(): String {
    val first = this?.firstName?.firstOrNull()?.uppercaseChar()
    val last = this?.lastName?.firstOrNull()?.uppercaseChar()
    return listOfNotNull(first, last).joinToString("").ifBlank { "P" }
}

private fun LocalPilotCertificate.titleLabel(): String {
    val labels = categoryList.map(::formatCertificateCategory).filter { it.isNotBlank() }
    return labels.joinToString(", ").ifBlank { "Attestato di competenza" }
}

private fun primaryCertificateCategory(certificate: LocalPilotCertificate): String =
    certificate.categoryList.lastOrNull()?.let(::normalizeCertificateCategoryForUi)
        ?: normalizeCertificateCategoryForUi(certificate.categories)
        ?: CertificateOptions.first().value

private fun formatCertificateCategory(value: String): String =
    when (normalizeCertificateCategoryForUi(value)) {
        "A1_A3" -> "A1/A3"
        "A2" -> "A2"
        "STS_01" -> "EU-STS-01"
        "STS_02" -> "EU-STS-02"
        else -> value
    }

private fun normalizeCertificateCategoryForUi(value: String): String? {
    val normalized = value.trim().uppercase(Locale.ITALY).replace('-', '_').replace('/', '_')
    return when (normalized) {
        "A1_A3" -> "A1_A3"
        "A2" -> "A2"
        "EU_STS_01", "STS_01" -> "STS_01"
        "EU_STS_02", "STS_02" -> "STS_02"
        else -> null
    }
}

private fun formatOperatorType(value: String): String =
    when (value) {
        LocalOperatorTypes.Association -> "Associazione"
        LocalOperatorTypes.Company -> "Azienda"
        LocalOperatorTypes.PublicBody -> "Ente pubblico"
        else -> "Personale"
    }

private fun normalizeDroneClassForUi(value: String): String =
    when (value.trim().uppercase(Locale.ITALY).replace('-', '_').replace(' ', '_')) {
        "C0", "C1", "C2", "C3", "C4", "C5", "C6" -> value.trim().uppercase(Locale.ITALY)
        "LEGACY" -> "LEGACY"
        "PRIVATELY_BUILT" -> "PRIVATELY_BUILT"
        "UNMARKED" -> "UNMARKED"
        "PRIVATELYBUILT" -> "PRIVATELY_BUILT"
        else -> ""
    }

private fun formatDroneClass(value: String): String =
    DroneClassOptions.firstOrNull { it.value == normalizeDroneClassForUi(value) }?.label
        ?: value.ifBlank { "Classe non indicata" }

private fun constrainEuStsForClass(drone: LocalDrone): LocalDrone {
    val classLabel = normalizeDroneClassForUi(drone.classLabel)
    return drone.copy(
        classLabel = classLabel,
        euSts01Registered = drone.euSts01Registered && classLabel == "C5",
        euSts01DeclarationDate = if (classLabel == "C5") drone.euSts01DeclarationDate else "",
        euSts02Registered = drone.euSts02Registered && classLabel == "C6",
        euSts02DeclarationDate = if (classLabel == "C6") drone.euSts02DeclarationDate else ""
    )
}

private fun formatEuStsSummary(drone: LocalDrone): String {
    val constrained = constrainEuStsForClass(drone)
    val parts = listOf(
        formatEuStsDeclaration("EU-STS-01", "C5", constrained.euSts01Registered, constrained.euSts01DeclarationDate),
        formatEuStsDeclaration("EU-STS-02", "C6", constrained.euSts02Registered, constrained.euSts02DeclarationDate)
    )
    return parts.joinToString(" - ")
}

private fun formatEuStsDeclaration(label: String, requiredClass: String, registered: Boolean, declarationDate: String): String =
    if (!registered) {
        "$label: non registrata"
    } else if (declarationDate.isBlank()) {
        "$label: data dichiarazione mancante"
    } else {
        "$label: valida fino al ${formatDateForDisplay(addYearsToIsoDate(declarationDate, 2))}"
    }

private fun splitSerials(value: String): List<String> =
    value.split('\n', ',', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ITALY) }

private fun validateProfile(profile: LocalPilotProfile): List<String> {
    val errors = mutableListOf<String>()
    if (profile.email.isNotBlank() && !profile.email.contains("@")) {
        errors += "Email non valida."
    }
    return errors
}

private fun validateCertificate(certificate: LocalPilotCertificate): List<String> {
    val errors = mutableListOf<String>()
    if (primaryCertificateCategory(certificate).isBlank()) {
        errors += "Seleziona una tipologia di attestato."
    }
    return errors
}

private fun validateOperator(operator: LocalUasOperator): List<String> {
    val errors = mutableListOf<String>()
    if (operator.pec.isNotBlank() && !operator.pec.contains("@")) {
        errors += "PEC non valida."
    }
    if (Regex("^.+-[A-Za-z0-9]{3}$").matches(operator.easaOperatorCode.trim().replace(Regex("\\s+"), ""))) {
        errors += "Non inserire i tre caratteri segreti finali del codice operatore UAS."
    }
    return errors
}

private fun validateDrone(drone: LocalDrone): List<String> {
    val errors = mutableListOf<String>()
    val classLabel = normalizeDroneClassForUi(drone.classLabel)
    if (drone.manufacturer.isBlank()) errors += "Il produttore e obbligatorio."
    if (drone.model.isBlank()) errors += "Il modello e obbligatorio."
    if (classLabel.isBlank()) errors += "Seleziona una classe valida."
    if (drone.euSts01Registered && classLabel != "C5") errors += "EU-STS-01 richiede un drone classe C5."
    if (drone.euSts02Registered && classLabel != "C6") errors += "EU-STS-02 richiede un drone classe C6."
    if (drone.euSts01Registered && drone.euSts01DeclarationDate.isBlank()) errors += "Inserisci la data della dichiarazione EU-STS-01."
    if (drone.euSts02Registered && drone.euSts02DeclarationDate.isBlank()) errors += "Inserisci la data della dichiarazione EU-STS-02."
    drone.manualMaxWindResistanceMs?.let {
        if (it <= 0.0 || it > 30.0) errors += "La resistenza al vento deve essere un valore in m/s plausibile."
    }
    return errors
}

private fun formatDateForDisplay(value: String?): String {
    val text = value?.trim().orEmpty()
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(text)
    return if (match != null) {
        val (year, month, day) = match.destructured
        "$day/$month/$year"
    } else {
        text
    }
}

private fun addYearsToIsoDate(value: String, years: Int): String {
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(value.trim()) ?: return ""
    val (year, month, day) = match.destructured
    return "${year.toInt() + years}-$month-$day"
}

private fun formatDraftDate(value: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(value))

private fun loadProfileBitmap(path: String): Bitmap? {
    val trimmed = path.trim()
    if (trimmed.isBlank()) return null
    return runCatching {
        BitmapFactory.decodeFile(trimmed)
    }.getOrNull()
}

private fun decodeBitmap(context: Context, uri: Uri): Bitmap? =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }
    }.getOrNull()

private fun it.droneskycheck.app.data.drone.DroneCatalogMatchResult.profileSummaryText(): String =
    when (status) {
        DroneCatalogMatchStatus.EXACT -> matchedDrone?.displayName?.let { "Profilo tecnico: $it riconosciuto." }
            ?: "Profilo tecnico riconosciuto."
        DroneCatalogMatchStatus.ALIAS -> matchedDrone?.displayName?.let { "Profilo tecnico: $it riconosciuto da alias." }
            ?: "Profilo tecnico riconosciuto da alias."
        DroneCatalogMatchStatus.SUGGESTED -> suggestions.takeIf { it.isNotEmpty() }?.joinToString(
            prefix = "Profilo tecnico non associato. Forse intendevi: "
        ) { it.displayName } ?: "Profilo tecnico non associato."
        DroneCatalogMatchStatus.AMBIGUOUS -> suggestions.takeIf { it.isNotEmpty() }?.joinToString(
            prefix = "Profilo tecnico ambiguo. Possibili profili: "
        ) { it.displayName } ?: "Profilo tecnico ambiguo."
        DroneCatalogMatchStatus.NOT_FOUND -> "Profilo tecnico non riconosciuto. Puoi inserire manualmente la resistenza al vento."
    }

private fun Double.windLimitText(): String =
    "${formatOneDecimal()} m/s (${msToKmh().formatOneDecimal()} km/h)"

private fun cropBitmapToSquare(source: Bitmap, zoom: Float, offset: Offset, previewSize: IntSize): Bitmap {
    val viewport = min(
        previewSize.width.takeIf { it > 0 } ?: source.width,
        previewSize.height.takeIf { it > 0 } ?: source.height
    ).toFloat().coerceAtLeast(1f)
    val baseScale = max(viewport / source.width.toFloat(), viewport / source.height.toFloat()) * zoom.coerceAtLeast(1f)
    val renderedWidth = source.width * baseScale
    val renderedHeight = source.height * baseScale
    val imageLeft = (viewport - renderedWidth) / 2f + offset.x
    val imageTop = (viewport - renderedHeight) / 2f + offset.y
    val cropSize = (viewport / baseScale).coerceAtMost(min(source.width, source.height).toFloat())
    val left = ((-imageLeft) / baseScale).coerceIn(0f, source.width - cropSize)
    val top = ((-imageTop) / baseScale).coerceIn(0f, source.height - cropSize)
    val outputSize = 512
    val output = createBitmap(outputSize, outputSize)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(
        source,
        Rect(left.toInt(), top.toInt(), (left + cropSize).toInt(), (top + cropSize).toInt()),
        RectF(0f, 0f, outputSize.toFloat(), outputSize.toFloat()),
        paint
    )
    return output
}

private fun saveProfilePhoto(context: Context, bitmap: Bitmap): String {
    val directory = File(context.filesDir, "profile")
    if (!directory.exists()) directory.mkdirs()
    val file = File(directory, "profile_photo.jpg")
    file.outputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
    }
    return file.absolutePath
}
