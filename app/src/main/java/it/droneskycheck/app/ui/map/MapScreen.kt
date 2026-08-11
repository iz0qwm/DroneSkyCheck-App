package it.droneskycheck.app.ui.map

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import it.droneskycheck.app.data.AuthorizationInfo
import it.droneskycheck.app.data.AuthorizationAdditionalRequirement
import it.droneskycheck.app.data.AuthorizationProcedure
import it.droneskycheck.app.data.AuthorizationDraft
import it.droneskycheck.app.data.AuthorizationOperationData
import it.droneskycheck.app.data.AuthorizationWorkflowSteps
import it.droneskycheck.app.data.AuthorizationZoneReference
import it.droneskycheck.app.data.AuthorityInfo
import it.droneskycheck.app.data.CreateAuthorizationDraftResult
import it.droneskycheck.app.data.EnrInfo
import it.droneskycheck.app.data.Issue
import it.droneskycheck.app.data.KeyValueInfo
import it.droneskycheck.app.data.LegalTimelineRepository
import it.droneskycheck.app.data.LegalTimelineResponse
import it.droneskycheck.app.data.LegalTimelineSegment
import it.droneskycheck.app.data.LegalTimelineState
import it.droneskycheck.app.data.LocalAuthorizationRepository
import it.droneskycheck.app.data.MapPreferencesRepository
import it.droneskycheck.app.data.NotamInfo
import it.droneskycheck.app.data.OfficialInfo
import it.droneskycheck.app.data.SupInfo
import it.droneskycheck.app.data.TemporalBarEntry
import it.droneskycheck.app.data.UasGeographicalZoneInfo
import it.droneskycheck.app.data.ValidityInfo
import it.droneskycheck.app.data.ZoneCheckV3Response
import it.droneskycheck.app.data.ZoneInfo
import it.droneskycheck.app.data.ZoneCheckV3Repository
import it.droneskycheck.app.data.formatLocalRange
import it.droneskycheck.app.data.weather.WeatherAssessment
import it.droneskycheck.app.data.weather.WeatherAssessmentEngine
import it.droneskycheck.app.data.weather.WeatherCodeCategory
import it.droneskycheck.app.data.weather.WeatherConfidenceLevel
import it.droneskycheck.app.data.weather.WeatherForecast
import it.droneskycheck.app.data.weather.WeatherForecastHour
import it.droneskycheck.app.data.weather.WeatherForecastRepository
import it.droneskycheck.app.data.weather.WeatherReasonCode
import it.droneskycheck.app.data.weather.WeatherState
import it.droneskycheck.app.map.DscLayerCategory
import it.droneskycheck.app.map.DscZoneMapColors
import it.droneskycheck.app.map.DroneSkyMapView
import it.droneskycheck.app.map.MapLayerIds
import it.droneskycheck.app.ui.authorization.AuthorizationDraftSheet
import it.droneskycheck.app.ui.profile.PilotProfileSheet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MapScreen(
    providedViewModel: MapViewModel? = null
) {
    val context = LocalContext.current
    val viewModel: MapViewModel = providedViewModel ?: viewModel(
        factory = remember(context) {
            MapViewModelFactory(context.applicationContext)
        }
    )
    val coroutineScope = rememberCoroutineScope()
    val authorizationRepository = remember(context) { LocalAuthorizationRepository(context.applicationContext) }
    val activity = context.findActivity()
    val uiState by viewModel.uiState.collectAsState()
    val visibleLayerCategories = uiState.layerVisibility
        .filterValues { it }
        .keys
    val permissionState = currentLocationPermissionState(context)
    var isPilotProfileSheetVisible by remember { mutableStateOf(false) }
    var currentDraft by remember { mutableStateOf<AuthorizationDraft?>(null) }
    var isDraftSheetVisible by remember { mutableStateOf(false) }
    var conflictingDraft by remember { mutableStateOf<AuthorizationDraft?>(null) }
    var pendingConflictZone by remember { mutableStateOf<ZoneInfo?>(null) }
    var draftError by remember { mutableStateOf<String?>(null) }
    var planningWarning by remember { mutableStateOf<String?>(null) }
    var isPlanningCardCompact by remember { mutableStateOf(true) }

    suspend fun reloadActiveDraft() {
        currentDraft = authorizationRepository.getActiveDraft()
    }

    LaunchedEffect(authorizationRepository) {
        reloadActiveDraft()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            viewModel.onLocationEnabled()
        } else {
            val permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            viewModel.onLocationPermissionDenied(permanentlyDenied)
        }
    }

    LocationPermissionRevocationEffect(
        enabled = uiState.isUserLocationEnabled,
        permissionState = permissionState,
        onPermissionRevoked = viewModel::onLocationPermissionRevoked
    )

    UserLocationUpdatesEffect(
        enabled = uiState.isUserLocationEnabled,
        permissionState = permissionState,
        onLocationUpdated = viewModel::onUserLocationUpdated,
        onProviderUnavailable = viewModel::onLocationProviderUnavailable,
        onPermissionRevoked = viewModel::onLocationPermissionRevoked
    )

    Box(modifier = Modifier.fillMaxSize()) {
        DroneSkyMapView(
            visibleLayerCategories = visibleLayerCategories,
            selectedPoint = uiState.selectedPoint,
            authorizationTakeoff = currentDraft?.operationData?.takeoffMapPoint(),
            authorizationAreaPoints = currentDraft?.operationData?.areaPoints.orEmpty().map { MapPoint(it.lat, it.lon) },
            authorizationAreaClosed = currentDraft?.operationData?.areaClosed == true,
            userLocation = uiState.userLocation,
            shouldCenterOnUserLocation = uiState.shouldCenterOnUserLocation,
            onUserLocationCentered = viewModel::onUserLocationCentered,
            onMapTapped = { selection ->
                val draft = currentDraft
                if (draft != null && draft.workflowStep != AuthorizationWorkflowSteps.Form) {
                    when (draft.workflowStep) {
                        AuthorizationWorkflowSteps.Takeoff -> {
                            planningWarning = null
                            coroutineScope.launch {
                                currentDraft = authorizationRepository.setTakeoff(
                                    id = draft.id,
                                    lat = selection.point.lat,
                                    lon = selection.point.lon
                                )
                            }
                        }
                        else -> {
                            val warning = validateAreaPointSelection(draft, selection.zones)
                            if (warning != null) {
                                planningWarning = warning
                            } else {
                                planningWarning = null
                                coroutineScope.launch {
                                    currentDraft = authorizationRepository.addAreaPoint(
                                        id = draft.id,
                                        lat = selection.point.lat,
                                        lon = selection.point.lon,
                                        detectedZones = selection.zones.map { it.toAuthorizationZoneReference() }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    viewModel.onMapTapped(selection)
                }
            },
            onCameraIdle = viewModel::onCameraIdle,
            onMapDataDegraded = viewModel::onMapDataDegraded,
            modifier = Modifier.fillMaxSize()
        )

        MapTitlePill(
            statusMessage = uiState.mapStatusMessage,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        currentDraft?.let { draft ->
            ActiveAuthorizationBanner(
                draft = draft,
                onResume = { isDraftSheetVisible = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 74.dp, start = 16.dp, end = 16.dp)
            )
        }

        currentDraft?.takeIf { it.workflowStep != AuthorizationWorkflowSteps.Form }?.let { draft ->
            PlanningWorkflowCard(
                draft = draft,
                compact = isPlanningCardCompact,
                warning = planningWarning,
                onToggleCompact = { isPlanningCardCompact = !isPlanningCardCompact },
                onUndoPoint = {
                    planningWarning = null
                    coroutineScope.launch {
                        currentDraft = authorizationRepository.undoAreaPoint(draft.id)
                    }
                },
                onRestartArea = {
                    planningWarning = null
                    coroutineScope.launch {
                        currentDraft = authorizationRepository.restartArea(draft.id)
                    }
                },
                onFinishArea = {
                    coroutineScope.launch {
                        currentDraft = authorizationRepository.finishArea(draft.id)
                        isDraftSheetVisible = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .widthIn(max = 340.dp)
            )
        }

        MapControlsToolbar(
            hiddenCount = uiState.layerVisibility.count { !it.value },
            isLocationEnabled = uiState.isUserLocationEnabled,
            hasUserLocation = uiState.userLocation != null,
            onLayersClick = viewModel::onLayerPanelRequested,
            onLocationClick = {
                when {
                    uiState.isUserLocationEnabled -> viewModel.onLocationControlRequested()
                    permissionState.hasForegroundLocation -> viewModel.onLocationEnabled()
                    else -> viewModel.onLocationPermissionExplanationRequested()
                }
            },
            onProfileClick = { isPilotProfileSheetVisible = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
        )

        if (uiState.isLayerSheetVisible) {
            LayerVisibilityBottomSheet(
                layerVisibility = uiState.layerVisibility,
                onVisibilityChanged = viewModel::onLayerCategoryVisibilityChanged,
                onShowAll = viewModel::onShowAllLayerCategories,
                onHideAll = viewModel::onHideAllLayerCategories,
                onDismiss = viewModel::onLayerPanelDismissed
            )
        }

        if (uiState.locationPermissionSheetVisible) {
            LocationPermissionBottomSheet(
                onDismiss = viewModel::onLocationPermissionExplanationDismissed,
                onContinue = {
                    viewModel.onLocationPermissionExplanationDismissed()
                    permissionLauncher.launch(LocationPermissions)
                }
            )
        }

        if (uiState.isLocationControlSheetVisible) {
            LocationControlBottomSheet(
                userLocation = uiState.userLocation,
                message = uiState.locationStatusMessage,
                onRecenter = viewModel::onLocationRecenterRequested,
                onAnalyzeHere = viewModel::onAnalyzeUserLocationRequested,
                onDisable = viewModel::onLocationDisabled,
                onDismiss = viewModel::onLocationControlDismissed
            )
        }

        if (isPilotProfileSheetVisible) {
            PilotProfileSheet(
                onDismiss = { isPilotProfileSheetVisible = false }
            )
        }

        if (isDraftSheetVisible) {
            currentDraft?.let { draft ->
            AuthorizationDraftSheet(
                draft = draft,
                onOpenProfile = {
                    isDraftSheetVisible = false
                    isPilotProfileSheetVisible = true
                },
                onSaveRequestData = { requestData ->
                    authorizationRepository.updateRequestData(draft.id, requestData).also { updated ->
                        if (updated != null) currentDraft = updated
                    }
                },
                onCancelDraft = {
                    coroutineScope.launch {
                        authorizationRepository.deleteDraft(draft.id)
                        currentDraft = null
                        isDraftSheetVisible = false
                    }
                },
                onDismiss = { isDraftSheetVisible = false }
            )
            }
        }

        conflictingDraft?.let { draft ->
            ActiveDraftConflictDialog(
                draft = draft,
                onResume = {
                    conflictingDraft = null
                    currentDraft = draft
                    isDraftSheetVisible = true
                    viewModel.onZoneSheetDismissed()
                },
                onCancelAndCreate = {
                    val selectedZone = pendingConflictZone
                    if (selectedZone != null) {
                        coroutineScope.launch {
                            authorizationRepository.deleteDraft(draft.id)
                            currentDraft = null
                            conflictingDraft = null
                            pendingConflictZone = null
                            when (val result = authorizationRepository.createDraftFromZone(selectedZone)) {
                                is CreateAuthorizationDraftResult.Created -> {
                                    viewModel.onZoneSheetDismissed()
                                    currentDraft = result.draft
                                }
                                else -> draftError = "Non riesco a creare la nuova richiesta."
                            }
                        }
                    }
                },
                onDismiss = {
                    conflictingDraft = null
                    pendingConflictZone = null
                }
            )
        }

        val selectedPoint = uiState.selectedPoint
        if (uiState.isZoneSheetVisible && selectedPoint != null) {
            ZoneBottomSheet(
                point = selectedPoint,
                zone = uiState.selectedZone,
                isLoading = uiState.isVerdictLoading,
                verdict = uiState.verdict,
                error = uiState.verdictError,
                isLegalTimelineLoading = uiState.isLegalTimelineLoading,
                legalTimeline = uiState.legalTimeline,
                legalTimelineError = uiState.legalTimelineError,
                isOperationalContextRequested = uiState.isOperationalContextRequested,
                isWeatherAnalysisLoading = uiState.isWeatherAnalysisLoading,
                weatherForecast = uiState.weatherForecast,
                weatherAssessment = uiState.weatherAssessment,
                weatherError = uiState.weatherError,
                draftError = draftError,
                onRetry = viewModel::onZoneCheckRetryRequested,
                onOperationalContextRequested = viewModel::onOperationalContextRequested,
                onAuthorizationRequest = { zoneInfo ->
                    draftError = null
                    coroutineScope.launch {
                        when (val result = authorizationRepository.createDraftFromZone(
                            zone = zoneInfo
                        )) {
                            is CreateAuthorizationDraftResult.Created -> {
                                viewModel.onZoneSheetDismissed()
                                currentDraft = result.draft
                                isDraftSheetVisible = false
                            }
                            is CreateAuthorizationDraftResult.ProcedureSelectionRequired -> {
                                draftError = "Sono disponibili piu procedure: ${result.procedures.joinToString(", ")}."
                            }
                            is CreateAuthorizationDraftResult.ActiveDraftConflict -> {
                                conflictingDraft = result.activeDraft
                                pendingConflictZone = zoneInfo
                            }
                            is CreateAuthorizationDraftResult.Unsupported -> {
                                draftError = "Questa zona non puo creare una pratica locale: ${result.reason}."
                            }
                        }
                    }
                },
                onDismiss = viewModel::onZoneSheetDismissed
            )
        }
    }
}

@Composable
private fun LayerStackIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        val width = size.width * 0.72f
        val height = size.height * 0.28f
        val left = (size.width - width) / 2f

        listOf(0.08f, 0.34f, 0.60f).forEachIndexed { index, topRatio ->
            drawRoundRect(
                color = color.copy(alpha = 1f - index * 0.18f),
                topLeft = Offset(left, size.height * topRatio),
                size = Size(width, height),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                style = stroke
            )
        }
    }
}

@Composable
private fun MapControlsToolbar(
    hiddenCount: Int,
    isLocationEnabled: Boolean,
    hasUserLocation: Boolean,
    onLayersClick: () -> Unit,
    onLocationClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val actionEnter = fadeIn() + scaleIn(transformOrigin = TransformOrigin(1f, 1f))
    val actionExit = fadeOut() + scaleOut(transformOrigin = TransformOrigin(1f, 1f))

    Box(
        modifier = modifier.size(width = 336.dp, height = 336.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = actionEnter,
            exit = actionExit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(y = (-72).dp)
        ) {
            MapActionFab(
                label = "Zone",
                direction = MapActionDirection.Up,
                onClick = {
                    expanded = false
                    onLayersClick()
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    LayerStackIcon(modifier = Modifier.size(24.dp))
                    if (hiddenCount > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(16.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = hiddenCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = actionEnter,
            exit = actionExit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(y = (-136).dp)
        ) {
            MapActionFab(
                label = "Posizione",
                direction = MapActionDirection.Up,
                containerColor = if (isLocationEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (isLocationEnabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                onClick = {
                    expanded = false
                    onLocationClick()
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    LocationTargetIcon(modifier = Modifier.size(24.dp))
                    if (isLocationEnabled && !hasUserLocation) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(9.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary
                        ) {}
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = actionEnter,
            exit = actionExit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-72).dp)
        ) {
            MapActionFab(
                label = "Pilota",
                direction = MapActionDirection.Left,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = {
                    expanded = false
                    onProfileClick()
                }
            ) {
                PilotProfileIcon(modifier = Modifier.size(24.dp))
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .graphicsLayer {
                    shadowElevation = 10.dp.toPx()
                }
        ) {
            ExpandCornerIcon(
                expanded = expanded,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

private enum class MapActionDirection {
    Up,
    Left
}

@Composable
private fun MapActionFab(
    label: String,
    direction: MapActionDirection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: @Composable () -> Unit
) {
    when (direction) {
        MapActionDirection.Up -> {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MapActionLabel(label)
                MapActionButton(
                    onClick = onClick,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    icon = icon
                )
            }
        }
        MapActionDirection.Left -> {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MapActionLabel(label)
                MapActionButton(
                    onClick = onClick,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    icon = icon
                )
            }
        }
    }
}

@Composable
private fun MapActionLabel(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun MapActionButton(
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    icon: @Composable () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        containerColor = containerColor,
        contentColor = contentColor
    ) {
        icon()
    }
}

@Composable
private fun LocationTargetIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = color,
            radius = size.minDimension * 0.28f,
            center = center,
            style = stroke
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.08f,
            center = center
        )
        drawLine(color, Offset(center.x, 1.dp.toPx()), Offset(center.x, size.height * 0.25f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(center.x, size.height * 0.75f), Offset(center.x, size.height - 1.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(1.dp.toPx(), center.y), Offset(size.width * 0.25f, center.y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.75f, center.y), Offset(size.width - 1.dp.toPx(), center.y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun PilotProfileIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        val centerX = size.width / 2f
        drawCircle(
            color = color,
            radius = size.minDimension * 0.17f,
            center = Offset(centerX, size.height * 0.32f),
            style = stroke
        )
        drawArc(
            color = color,
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(size.width * 0.22f, size.height * 0.46f),
            size = Size(size.width * 0.56f, size.height * 0.44f),
            style = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.28f, size.height * 0.72f),
            end = Offset(size.width * 0.72f, size.height * 0.72f),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ExpandCornerIcon(
    expanded: Boolean,
    modifier: Modifier = Modifier
) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        val strokeWidth = 2.6.dp.toPx()
        if (expanded) {
            drawLine(
                color = color,
                start = Offset(size.width * 0.28f, size.height * 0.28f),
                end = Offset(size.width * 0.72f, size.height * 0.72f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.72f, size.height * 0.28f),
                end = Offset(size.width * 0.28f, size.height * 0.72f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            return@Canvas
        }

        val origin = Offset(size.width * 0.68f, size.height * 0.68f)
        val upEnd = Offset(origin.x, size.height * 0.18f)
        val leftEnd = Offset(size.width * 0.18f, origin.y)
        drawCircle(color = color.copy(alpha = 0.22f), radius = size.minDimension * 0.34f, center = origin)
        drawLine(color, origin, upEnd, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, origin, leftEnd, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, upEnd, Offset(upEnd.x - size.width * 0.12f, upEnd.y + size.height * 0.13f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, upEnd, Offset(upEnd.x + size.width * 0.12f, upEnd.y + size.height * 0.13f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, leftEnd, Offset(leftEnd.x + size.width * 0.13f, leftEnd.y - size.height * 0.12f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, leftEnd, Offset(leftEnd.x + size.width * 0.13f, leftEnd.y + size.height * 0.12f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationPermissionBottomSheet(
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Usa la tua posizione",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Drone Sky Check può usare la tua posizione per mostrarti dove ti trovi sulla mappa e verificare le zone presenti nel punto in cui ti trovi. La posizione viene utilizzata mentre usi questa funzione.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Non ora")
                }
                Button(onClick = onContinue) {
                    Text("Continua")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationControlBottomSheet(
    userLocation: UserLocation?,
    message: String?,
    onRecenter: () -> Unit,
    onAnalyzeHere: () -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Posizione",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            val locationText = userLocation?.let {
                val accuracy = it.accuracyMeters?.let { value -> " · accuratezza ${value.toInt()} m" }.orEmpty()
                val precision = if (it.isPrecise) "precisa" else "approssimativa"
                "Posizione $precision$accuracy"
            } ?: "In attesa del primo fix GPS."
            Text(
                text = message ?: locationText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onDisable) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Disattiva")
                }
                Spacer(modifier = Modifier.size(10.dp))
                OutlinedButton(
                    onClick = onAnalyzeHere,
                    enabled = userLocation != null
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Analizza qui")
                }
                Spacer(modifier = Modifier.size(10.dp))
                Button(
                    onClick = onRecenter,
                    enabled = userLocation != null
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Ricentra")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayerVisibilityBottomSheet(
    layerVisibility: Map<DscLayerCategory, Boolean>,
    onVisibilityChanged: (DscLayerCategory, Boolean) -> Unit,
    onShowAll: () -> Unit,
    onHideAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Zone visualizzate",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Filtro solo grafico: il verdetto operativo resta completo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onHideAll) {
                    Text("Nascondi tutte")
                }
                TextButton(onClick = onShowAll) {
                    Text("Mostra tutte")
                }
            }

            DscLayerCategory.entries.forEach { category ->
                LayerVisibilityRow(
                    category = category,
                    isVisible = layerVisibility[category] ?: true,
                    onVisibilityChanged = { onVisibilityChanged(category, it) }
                )
            }
        }
    }
}

@Composable
private fun LayerVisibilityRow(
    category: DscLayerCategory,
    isVisible: Boolean,
    onVisibilityChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(color = category.swatchColor(), shape = CircleShape)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = category.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.size(4.dp))
        Switch(
            checked = isVisible,
            onCheckedChange = onVisibilityChanged
        )
    }
}

@Composable
private fun MapTitlePill(
    statusMessage: String?,
    modifier: Modifier = Modifier
) {
    val degraded = !statusMessage.isNullOrBlank()
    Surface(
        modifier = modifier.widthIn(max = 300.dp),
        shape = MaterialTheme.shapes.large,
        color = if (degraded) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.94f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        },
        contentColor = if (degraded) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = statusMessage ?: "Drone Sky Check",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (degraded) "Ultima copia disponibile" else "Mappa UAS",
                style = MaterialTheme.typography.bodySmall,
                color = if (degraded) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActiveAuthorizationBanner(
    draft: AuthorizationDraft,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .clickable(onClick = onResume),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 8.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "${draft.procedureType} - Richiesta in corso",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(draft.zoneName, draft.workflowStep.toWorkflowLabel())
                    .filter { it.isNotBlank() }
                    .joinToString(" - "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlanningWorkflowCard(
    draft: AuthorizationDraft,
    compact: Boolean,
    warning: String?,
    onToggleCompact: () -> Unit,
    onUndoPoint: () -> Unit,
    onRestartArea: () -> Unit,
    onFinishArea: () -> Unit,
    modifier: Modifier = Modifier
) {
    val operation = draft.operationData
    val takeoff = operation.takeoffMapPoint()
    val areaCount = operation.areaPoints.size
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (draft.workflowStep == AuthorizationWorkflowSteps.Takeoff) {
                    "1. Punto di decollo"
                } else {
                    "2. Area operativa"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (draft.workflowStep == AuthorizationWorkflowSteps.Takeoff) {
                    "Tocca sulla mappa il punto di decollo."
                } else {
                    "Tocca la mappa per aggiungere i vertici dell'area. Minimo 3 punti."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DraftMapLine("Decollo", takeoff?.formatForPlanning() ?: "Da selezionare")
            DraftMapLine("Vertici area", "$areaCount / 3 minimi")
            warning?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TextButton(
                onClick = onToggleCompact,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (compact) "Mostra comandi" else "Riduci")
            }
            if (!compact) {
                if (operation.zoneAnalysisSummary.isNotBlank()) {
                    DraftMapLine("Analisi", operation.zoneAnalysisSummary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onUndoPoint,
                        enabled = areaCount > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Undo")
                    }
                    OutlinedButton(
                        onClick = onRestartArea,
                        enabled = takeoff != null || areaCount > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ricomincia")
                    }
                }
                Button(
                    onClick = onFinishArea,
                    enabled = areaCount >= 3,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Termina area")
                }
            }
        }
    }
}

@Composable
private fun ActiveDraftConflictDialog(
    draft: AuthorizationDraft,
    onResume: () -> Unit,
    onCancelAndCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Richiesta gia in corso") },
        text = {
            Text(
                "Hai gia una richiesta ${draft.procedureType} per ${draft.zoneName.ifBlank { "un'altra zona" }}. " +
                    "DSC FREE mantiene una sola richiesta attiva alla volta."
            )
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text("Riprendi")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelAndCreate) {
                Text("Annulla e crea nuova")
            }
        }
    )
}

@Composable
private fun DraftMapLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneBottomSheet(
    point: MapPoint,
    zone: DemoZone?,
    isLoading: Boolean,
    verdict: ZoneCheckV3Response?,
    error: String?,
    isLegalTimelineLoading: Boolean,
    legalTimeline: LegalTimelineResponse?,
    legalTimelineError: String?,
    isOperationalContextRequested: Boolean,
    isWeatherAnalysisLoading: Boolean,
    weatherForecast: WeatherForecast?,
    weatherAssessment: WeatherAssessment?,
    weatherError: String?,
    draftError: String?,
    onRetry: () -> Unit,
    onOperationalContextRequested: () -> Unit,
    onAuthorizationRequest: (ZoneInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = ZoneSheetMaxHeight),
            contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 36.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
            if (isLoading) {
                OperationalCheckLoadingCard(point = point, zone = zone)
            }

            verdict?.let { response ->
                VerdictBadge(response)
                response.verdict.explanation
                    .takeUnless { it.isBlank() || it == verdictHeader(response) }
                    ?.let { explanation ->
                        Text(
                            text = explanation.cleanUserText(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                if (response.warnings.isNotEmpty() && response.zones.size > 1) {
                    Text(
                        text = "In questo punto sono presenti piu zone sovrapposte: controlla i dettagli qui sotto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                draftError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (isOperationalContextRequested) {
                    OperationalReportSection(
                        isLoading = isLegalTimelineLoading,
                        timeline = legalTimeline,
                        timelineError = legalTimelineError,
                        isWeatherLoading = isWeatherAnalysisLoading,
                        forecast = weatherForecast,
                        assessment = weatherAssessment,
                        weatherError = weatherError
                    )
                } else {
                    OperationalContextActionSection(
                        onClick = onOperationalContextRequested
                    )
                }
                SelectedMapNotamFallbackSection(
                    selectedZone = zone,
                    response = response
                )
                HorizontalDivider()
                Text(
                    text = "Zone presenti · ${response.zones.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (response.zones.isEmpty()) {
                    Text(
                        text = "Nessuna zona operativa restituita da zoneCheckV3 per questo punto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    response.sortedZones().forEachIndexed { index, zoneInfo ->
                        ZoneInfoCard(
                            zone = zoneInfo,
                            index = index,
                            verdict = response,
                            blockers = response.blockers,
                            warnings = response.warnings,
                            onAuthorizationRequest = { onAuthorizationRequest(zoneInfo) }
                        )
                    }
                }
            }

            error?.let {
                Text(
                    text = "Verdetto non disponibile",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "DSC non e' raggiungibile in questo momento.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Riprova")
                }
            }

            if (verdict == null && !isLoading) {
                zone?.let { tappedZone ->
                    ZoneOptionalDetail("Area selezionata", tappedZone.name.cleanZoneName())
                    ZoneOptionalDetail("Categoria", tappedZone.userCategoryTitle())
                    ZoneOptionalDetail("Anteprima quota", tappedZone.previewAltitudeText())
                }
            }

            if (!isLoading) {
                ZoneDetail(
                    label = "Punto controllato",
                    value = "${point.lat.formatCoordinate()}, ${point.lon.formatCoordinate()}"
                )
            }
                }
            }
        }
    }
}

@Composable
private fun OperationalReportSection(
    isLoading: Boolean,
    timeline: LegalTimelineResponse?,
    timelineError: String?,
    isWeatherLoading: Boolean,
    forecast: WeatherForecast?,
    assessment: WeatherAssessment?,
    weatherError: String?
) {
    var expanded by remember { mutableStateOf(true) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OperationalReportHandle(
                expanded = expanded,
                onToggle = { expanded = !expanded }
            )
            if (expanded) {
                LegalTimelineSection(
                    isLoading = isLoading,
                    timeline = timeline,
                    error = timelineError
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                WeatherAnalysisSection(
                    isLoading = isWeatherLoading,
                    forecast = forecast,
                    assessment = assessment,
                    error = weatherError
                )
            }
        }
    }
}

@Composable
private fun OperationalReportHandle(
    expanded: Boolean,
    onToggle: () -> Unit
) {
    TextButton(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReportIconChip(
                icon = Icons.Default.Info,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Report operativo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (expanded) {
                        "Orari della zona e condizioni meteo sul punto controllato"
                    } else {
                        "Mostra di nuovo meteo e controllo temporale"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (expanded) "\u25B2" else "\u25BC",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LegalTimelineSection(
    isLoading: Boolean,
    timeline: LegalTimelineResponse?,
    error: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ReportSectionTitle(
            icon = Icons.Default.Schedule,
            title = "Controllo temporale",
            subtitle = "Finestre operative calcolate nelle prossime ore"
        )
        when {
            isLoading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "Calcolo gli orari della zona.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            error != null -> Text(
                text = "Previsione temporale non disponibile",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            timeline != null -> LegalTimelineContent(timeline)
            else -> Text(
                text = "Previsione temporale non disponibile",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportSectionTitle(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReportIconChip(
            icon = icon,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportIconChip(
    icon: ImageVector,
    color: Color,
    contentColor: Color
) {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = color,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun LegalTimelineContent(timeline: LegalTimelineResponse) {
    val now = remember(timeline) { Instant.now() }
    val current = timeline.currentSegment(now)
    val zoneId = remember { ZoneId.systemDefault() }

    current?.let { segment ->
        val timelineColor = segment.timelineColor()
        Surface(
            shape = MaterialTheme.shapes.small,
            color = timelineColor,
            contentColor = readableContentColor(timelineColor)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "ORA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = segment.currentTimelineTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = segment.currentTimelineSubtitle(zoneId),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Prossime ore",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        timeline.segments
            .filter { it.to.isAfter(now) }
            .take(MaxTimelineSegments)
            .forEach { segment ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = segment.formatLocalRange(zoneId),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (segment == current) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.widthIn(min = 88.dp)
                )
                Text(
                    text = segment.timelineRowText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    LegalDailySummarySection(
        timeline = timeline,
        now = now,
        zoneId = zoneId
    )
}

@Composable
private fun LegalDailySummarySection(
    timeline: LegalTimelineResponse,
    now: Instant,
    zoneId: ZoneId
) {
    val windowEnd = timeline.window.to ?: timeline.segments.maxOfOrNull { it.to } ?: return
    val summaries = remember(timeline, now, zoneId) {
        summarizeLegalTimelineByDay(
            segments = timeline.segments,
            zoneId = zoneId,
            from = now,
            to = windowEnd
        )
    }
    if (summaries.isEmpty()) return

    val ordinaryDays = summaries.filterNot { it.isWeekend }
    val weekendDays = summaries.filter { it.isWeekend }

    if (ordinaryDays.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Prossimi giorni",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            ordinaryDays.forEach { LegalDailySummaryRow(it) }
        }
    }
    if (weekendDays.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Weekend",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            weekendDays.forEach { LegalDailySummaryRow(it, emphasized = true) }
        }
    }
}

@Composable
private fun LegalDailySummaryRow(
    summary: LegalDailySummary,
    emphasized: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (emphasized) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = summary.date.dayTitle(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (summary.windows.size == 1 && summary.windows.first().coversDisplayedDay()) {
                Text(
                    text = summary.windows.first().legalWindowText(compact = true),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.82f)
                )
            } else {
                summary.windows.take(MaxDailySummaryWindows).forEach { window ->
                    Text(
                        text = "${window.formatLocalTimeRange()} · ${window.legalWindowText()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.82f)
                    )
                }
                if (summary.windows.size > MaxDailySummaryWindows) {
                    Text(
                        text = "Altre finestre nel giorno",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.68f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherAnalysisSection(
    isLoading: Boolean,
    forecast: WeatherForecast?,
    assessment: WeatherAssessment?,
    error: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ReportSectionTitle(
            icon = Icons.Default.Cloud,
            title = "Meteo",
            subtitle = "Valori previsti sull'ora piu vicina"
        )
        when {
            isLoading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "Analisi meteo in corso.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            error != null -> Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            assessment != null -> WeatherAnalysisContent(
                assessment = assessment,
                forecast = forecast
            )
            else -> Text(
                text = "Meteo non disponibile",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeatherAnalysisContent(
    assessment: WeatherAssessment,
    forecast: WeatherForecast?
) {
    val now = remember(forecast) { Instant.now() }
    val hour = remember(forecast, now) { forecast?.closestHour(now) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = assessment.weatherSummary(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        hour?.let { forecastHour ->
            Text(
                text = forecastHour.weatherReferenceText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                forecastHour.weatherFacts(assessment).forEach { fact ->
                    WeatherFactRow(fact)
                }
            }
        }
        Text(
            text = assessment.weatherReasonText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = assessment.weatherConfidenceText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        forecast?.let {
            WeatherTrendSection(
                forecast = it,
                now = now
            )
        }
    }
}

@Composable
private fun WeatherTrendSection(
    forecast: WeatherForecast,
    now: Instant
) {
    val engine = remember { WeatherAssessmentEngine() }
    val trends = remember(forecast, now, engine) {
        summarizeWeatherTrendByDay(
            forecast = forecast,
            now = now,
            engine = engine
        )
    }
    if (trends.isEmpty()) return

    val ordinaryDays = trends.filterNot { it.isWeekend }
    val weekendDays = trends.filter { it.isWeekend }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        text = "Tendenza prossimi giorni",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = forecast.weatherHorizonText(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (ordinaryDays.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ordinaryDays.forEach { WeatherTrendRow(it) }
        }
    }
    if (weekendDays.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Weekend",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            weekendDays.forEach { WeatherTrendRow(it, emphasized = true) }
        }
    }
}

@Composable
private fun WeatherTrendRow(
    trend: WeatherDailyTrend,
    emphasized: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (emphasized) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = trend.date.dayTitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = trend.conditionScoreText(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "Affidabilita previsione: ${trend.reliability.toUserText()}",
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.78f)
            )
            trend.bestWindow?.let { window ->
                Text(
                    text = "Finestra migliore: ${window.formatLocalTimeRange()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.78f)
                )
            }
            val note = trend.weatherTrendNote()
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
private fun WeatherFactRow(fact: WeatherFact) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(26.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = fact.icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Text(
            text = fact.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 74.dp)
        )
        Text(
            text = fact.value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OperationalContextActionSection(
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Meteo e prossime ore",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Controlla meteo e prossime ore")
            }
        }
    }
}

@Composable
private fun OperationalCheckLoadingCard(point: MapPoint, zone: DemoZone?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Sto consultando Drone Sky Check",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Recupero regole, NOTAM e orari per questo punto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            zone?.let {
                Text(
                    text = it.name.cleanZoneName(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = it.previewAltitudeText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Punto ${point.lat.formatCoordinate()}, ${point.lon.formatCoordinate()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ZoneInfoCard(
    zone: ZoneInfo,
    index: Int,
    verdict: ZoneCheckV3Response,
    blockers: List<Issue>,
    warnings: List<Issue>,
    onAuthorizationRequest: () -> Unit
) {
    var expanded by remember(zone.name, zone.type, index) { mutableStateOf(false) }
    val hasBlocker = blockers.any { it.zoneName == zone.name }
    val hasWarning = warnings.any { it.zoneName == zone.name }
    val isResponsible = zone.isVerdictSource == true ||
        verdict.responsibleZone?.id?.let { it == zone.id } == true ||
        verdict.responsibleZone?.name?.let { it == zone.name } == true ||
        verdict.verdict.responsibleZoneId?.let { it == zone.id } == true ||
        verdict.verdict.responsibleZoneName?.let { it == zone.name } == true

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isResponsible) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(zoneSeverityColor(zone, hasBlocker, hasWarning), CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = zone.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (expanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = zoneSubtitle(zone),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Meno" else "Dettagli")
                }
            }

            ZonePrimaryStatusCard(zone)
            ActivityScheduleHighlight(zone)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    zone.limitMetersAgl?.takeUnless { zone.isInactiveNow() }?.let {
                        ZoneLimitPill(it)
                    }
                    zone.userOperationalStatus()?.let {
                        ZoneStatusPill(it, zone.isInactiveNow())
                    }
                    if (zone.authorizationRequired == true) CompactInfoPill("Autorizzazione richiesta")
                }
                if (isResponsible) {
                    Row {
                        CompactInfoPill("Determina il verdetto")
                    }
                }
            }

            if (expanded) {
                TemporalDetailsPanel(zone)
                ZoneNarrativeSection(zone)
                OfficialSection(zone.official)
                NotamSection(zone.notams)
                EnrSection(zone.enr)
                SupSection(zone.sup)
                UasGeographicalZoneSection(zone.uasGeographicalZone)
                AuthorizationSection(zone.authorization, onAuthorizationRequest)
                AuthoritySection(zone.authority)
                zone.blockers.filterRelevantFor(zone).takeIf { it.isNotEmpty() }?.let {
                    ZoneOptionalDetail("Attenzione", it.joinIssues())
                }
                zone.warnings.filterRelevantFor(zone).takeIf { it.isNotEmpty() }?.let {
                    ZoneOptionalDetail("Avvisi", it.joinIssues())
                }
            }
        }
    }
}

@Composable
private fun ZonePrimaryStatusCard(zone: ZoneInfo) {
    val status = zone.primaryStatusPresentation() ?: return
    val colors = MaterialTheme.colorScheme
    val altitudeColor = dscAltitudeColor(zone.limitMetersAgl ?: 120)
    val containerColor = when (status.emphasis) {
        ZoneStatusEmphasis.Active -> altitudeColor
        ZoneStatusEmphasis.Inactive -> InactiveZonePillColor
        ZoneStatusEmphasis.Unknown -> colors.tertiaryContainer
    }
    val contentColor = when (status.emphasis) {
        ZoneStatusEmphasis.Active -> readableContentColor(containerColor)
        ZoneStatusEmphasis.Inactive -> Color.White
        ZoneStatusEmphasis.Unknown -> colors.onTertiaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "STATO",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor.copy(alpha = 0.82f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(contentColor, CircleShape)
                )
                Text(
                    text = status.label.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ActivityScheduleHighlight(zone: ZoneInfo) {
    val schedule = zone.activityScheduleLabel() ?: return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "Orari di attività",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = schedule,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TemporalDetailsPanel(zone: ZoneInfo) {
    val details = zone.temporalDetailsPresentation()
    if (!details.hasContent) return

    var expanded by remember(zone.id, zone.name) { mutableStateOf(false) }

    OfficialAccordion(
        title = "Dettagli temporali",
        expanded = expanded,
        onToggle = { expanded = !expanded }
    ) {
        TemporalWeekScheduleBar(details.weekSchedule)
        TemporalDayScheduleBar(details.daySchedule)
        ZoneOptionalDetail("Stato attuale", details.status)
        ZoneOptionalDetail("Orari di attività", details.activitySchedule)
        ZoneOptionalDetail("Dato originale", details.originalSchedule)
        ZoneOptionalDetail("Validità", details.validity)
        ZoneOptionalDetail("Prossima attivazione", details.nextActivation)
        ZoneOptionalDetail("Nota", details.explanation)
    }
}

@Composable
private fun TemporalWeekScheduleBar(entries: List<TemporalBarEntry>) {
    val week = entries.take(7)
    if (week.isEmpty()) return

    val labels = listOf("L", "M", "M", "G", "V", "S", "D")
    val todayIndex = remember { LocalDate.now().dayOfWeek.value - 1 }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "Settimana",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            week.forEachIndexed { index, entry ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = labels.getOrElse(index) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (index == todayIndex) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TemporalEntryBar(entry)
                }
            }
        }
    }
}

@Composable
private fun TemporalDayScheduleBar(entries: List<Boolean?>) {
    val day = entries.take(24)
    if (day.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "Giornata UTC",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            day.forEach { active ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(temporalStateColor(active), MaterialTheme.shapes.extraSmall)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("00", "06", "12", "18", "24").forEachIndexed { index, label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(if (index == 4) 0.35f else 1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TemporalEntryBar(entry: TemporalBarEntry) {
    val segments = entry.segments
        .filter { it.end > it.start }
        .sortedBy { it.start }

    if (segments.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(11.dp)
                .background(TemporalInactiveColor, MaterialTheme.shapes.extraSmall)
        ) {
            var cursor = 0f
            segments.forEach { segment ->
                val inactiveWeight = (segment.start - cursor).coerceAtLeast(0f)
                if (inactiveWeight > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(inactiveWeight)
                            .fillMaxHeight()
                    )
                }
                Box(
                    modifier = Modifier
                        .weight((segment.end - segment.start).coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(TemporalActiveColor)
                )
                cursor = segment.end
            }
            val tail = (1f - cursor).coerceAtLeast(0f)
            if (tail > 0f) {
                Box(
                    modifier = Modifier
                        .weight(tail)
                        .fillMaxHeight()
                )
            }
        }
        return
    }

    val ratio = entry.activeRatio?.coerceIn(0f, 1f)
    if (ratio != null && ratio > 0f && ratio < 1f) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(11.dp)
                .background(TemporalInactiveColor, MaterialTheme.shapes.extraSmall)
        ) {
            Box(
                modifier = Modifier
                    .weight(ratio)
                    .fillMaxHeight()
                    .background(TemporalActiveColor)
            )
            Box(
                modifier = Modifier
                    .weight(1f - ratio)
                    .fillMaxHeight()
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(11.dp)
            .background(
                color = temporalStateColor(ratio?.let { it >= 1f } ?: entry.active),
                shape = MaterialTheme.shapes.extraSmall
            )
    )
}

private fun temporalStateColor(active: Boolean?): Color =
    when (active) {
        true -> TemporalActiveColor
        false -> TemporalInactiveColor
        null -> TemporalUnknownColor
    }

@Composable
private fun ZoneNarrativeSection(zone: ZoneInfo) {
    val narrative = zone.info
    val description = zone.primaryDescription()
    if (description.isNullOrBlank() &&
        narrative?.explanation.isNullOrBlank() &&
        narrative?.operationalMeaning.isNullOrBlank()
    ) {
        return
    }

    ZoneSection(title = "Situazione operativa") {
        ZoneOptionalDetail("Descrizione", description)
        ZoneOptionalDetail("Spiegazione DSC", narrative?.explanation)
        ZoneOptionalDetail("Significato operativo", narrative?.operationalMeaning)
    }
}

@Composable
private fun OfficialSection(
    official: OfficialInfo?,
    title: String = "Informazioni ufficiali",
    compactUntilOpened: Boolean = false,
    openLabel: String = "Leggi testo completo",
    closeLabel: String = "Comprimi testo",
    sourceText: String? = official?.sourceText,
    includeSourceReference: Boolean = true,
    includeFields: Boolean = true,
    monospace: Boolean = false
) {
    val displayText = sourceText?.takeIf { it.isNotBlank() }
    val hasReference = includeSourceReference && !official?.sourceReference.isNullOrBlank()
    val hasFields = includeFields && official?.fields?.isNotEmpty() == true
    if (displayText == null && !hasReference && !hasFields) return

    var expanded by remember(title, displayText, official?.sourceReference) { mutableStateOf(false) }

    if (compactUntilOpened) {
        OfficialAccordion(title = title, expanded = expanded, onToggle = { expanded = !expanded }) {
            OfficialContent(
                sourceReference = official?.sourceReference?.takeIf { hasReference },
                fields = official?.fields.orEmpty().takeIf { hasFields }.orEmpty(),
                sourceText = displayText,
                monospace = monospace
            )
        }
        return
    }

    ZoneSection(title = title) {
        if (hasReference) {
            ZoneOptionalDetail("Fonte", official?.sourceReference)
        }
        if (hasFields) {
            KeyValueList(official?.fields.orEmpty())
        }
        displayText?.let { text ->
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) closeLabel else openLabel)
            }
            OfficialTextBox(
                text = text,
                expanded = expanded,
                monospace = monospace
            )
        }
    }
}

@Composable
private fun OfficialAccordion(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (expanded) "\u25B2" else "\u25BC",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        if (expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
        }
    }
}

@Composable
private fun OfficialContent(
    sourceReference: String?,
    fields: List<KeyValueInfo>,
    sourceText: String?,
    monospace: Boolean
) {
    ZoneOptionalDetail("Fonte", sourceReference)
    if (fields.isNotEmpty()) {
        KeyValueList(fields)
    }
    sourceText?.let { text ->
        OfficialTextBox(
            text = text,
            expanded = true,
            monospace = monospace
        )
    }
}

@Composable
private fun OfficialTextBox(
    text: String,
    expanded: Boolean,
    monospace: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (monospace) FontFamily.Monospace else null
                ),
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ValiditySection(validity: ValidityInfo?) {
    if (validity == null || !validity.hasContent()) return

    ZoneSection(title = "Validità e orari") {
        ZoneOptionalDetail("Da", validity.validFrom.formatNotamUtcDate())
        ZoneOptionalDetail("A", validity.validTo.formatNotamUtcDate())
        ZoneOptionalDetail("Schedule", validity.schedule)
        ZoneOptionalDetail("Schedule interpretata", validity.interpretedSchedule)
        ZoneOptionalDetail("Prossima attivazione", validity.nextActivation.formatNotamUtcDate())
    }
}

@Composable
private fun NotamSection(notams: List<NotamInfo>) {
    val usefulNotams = notams.filter { it.hasUsefulContent() }
    if (usefulNotams.isEmpty()) return

    ZoneSection(title = "NOTAM") {
        usefulNotams.forEachIndexed { index, notam ->
            if (index > 0) HorizontalDivider()
            val presentation = notam.presentation()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = presentation.code,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                NotamSummaryCard(presentation)
                ZoneOptionalDetail("FIR", notam.fir)
                ZoneOptionalDetail("Località", notam.location)
                ZoneOptionalDetail("Zona", notam.zoneReference)
                ZoneOptionalDetail("Orari", presentation.activitySchedule)
                ZoneOptionalDetail("Validità", presentation.validity)
                ZoneOptionalDetail("Stato operativo", presentation.operationalStatus)
                OfficialSection(
                    official = presentation.official,
                    title = "Testo NOTAM ufficiale",
                    compactUntilOpened = true,
                    openLabel = "Apri testo ufficiale",
                    closeLabel = "Chiudi testo ufficiale",
                    includeSourceReference = false,
                    includeFields = false,
                    monospace = true
                )
                if (notam.blockers.isNotEmpty()) ZoneOptionalDetail("Blocker", notam.blockers.joinIssues())
                if (notam.warnings.isNotEmpty()) ZoneOptionalDetail("Warning", notam.warnings.joinIssues())
            }
        }
    }
}

@Composable
private fun SelectedMapNotamFallbackSection(
    selectedZone: DemoZone?,
    response: ZoneCheckV3Response
) {
    if (selectedZone?.isNotamZone() != true) return
    val officialText = selectedZone.description
        .cleanOfficialSourceText()
        ?.takeUnless { selectedText ->
            response.zones.any { zone ->
                zone.official?.sourceText.containsEquivalent(selectedText) ||
                    zone.notams.any { notam ->
                        notam.official?.sourceText.containsEquivalent(selectedText)
                    }
            }
        }
        ?: return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "NOTAM selezionato dalla mappa",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        ZoneOptionalDetail("Nome", selectedZone.name.cleanZoneName())
        ZoneOptionalDetail("Tipo", selectedZone.userCategoryTitle() ?: selectedZone.type)
        ZoneOptionalDetail(
            label = "Pianificazione temporale",
            value = "Non disponibile nel dettaglio app per questo NOTAM. Verifica date e orari nel testo ufficiale."
        )
        OfficialTextBox(
            text = officialText,
            expanded = true,
            monospace = true
        )
    }
}

@Composable
private fun NotamSummaryCard(presentation: NotamPresentation) {
    val isManual = presentation.statusLabel.equals("Verifica necessaria", ignoreCase = true)
    val containerColor = if (isManual) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (isManual) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = presentation.statusLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = presentation.body,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun EnrSection(enr: EnrInfo?) {
    if (enr == null || !enr.hasContent()) return

    ZoneSection(title = "ENR") {
        ZoneOptionalDetail("Nome", enr.name)
        ZoneOptionalDetail("Riferimento", enr.classification)
        ZoneOptionalDetail("Stato", enr.validity?.statusLabel())
        ZoneOptionalDetail("Orari di attività", enr.schedule?.human ?: enr.validity?.schedule)
        ZoneOptionalDetail("Limiti", enr.limitText.usableUserText())
        ZoneOptionalDetail("Note", enr.notes.usableUserText())
        ZoneOptionalDetail("Attivazione", enr.activationType?.toUserText())
        ZoneOptionalDetail("Operazioni", enr.operationSummary())
        ZoneOptionalDetail("Attestato di competenza minimo richiesto", enr.requiredLicense.formatRequiredLicense())
        ZoneOptionalDetail("Autorizzazione", enr.authorizationRequiredText())
        ZoneOptionalDetail("Spiegazione", enr.explanation.distinctFrom(enr.schedule?.human))
        ZoneOptionalDetail("Significato operativo", enr.operationalMeaning)
        OfficialSection(
            official = enr.official,
            title = "Dettagli ufficiali",
            compactUntilOpened = true,
            openLabel = "Apri dettagli ufficiali",
            closeLabel = "Chiudi dettagli ufficiali",
            sourceText = enr.officialSourceText(),
            includeSourceReference = false,
            includeFields = false
        )
    }
}

@Composable
private fun SupSection(sup: SupInfo?) {
    if (sup == null || !sup.hasContent()) return

    ZoneSection(title = "SUP") {
        ZoneOptionalDetail("Titolo", sup.title)
        ZoneOptionalDetail("Riferimento", sup.reference)
        ZoneOptionalDetail("Attestato di competenza minimo richiesto", sup.requiredLicense.formatRequiredLicense())
        ZoneOptionalDetail("Autorizzazione", sup.authorizationRequired.authorizationRequiredText())
        ZoneOptionalDetail("Spiegazione", sup.explanation)
        ZoneOptionalDetail("Significato operativo", sup.operationalMeaning)
        ValiditySection(sup.validity)
        AuthorizationSection(
            authorization = sup.authorization,
            onAuthorizationRequest = {},
            allowDraftCreation = false
        )
        OfficialSection(sup.official, title = "Testo ufficiale SUP")
        if (sup.blockers.isNotEmpty()) ZoneOptionalDetail("Blocker", sup.blockers.joinIssues())
        if (sup.warnings.isNotEmpty()) ZoneOptionalDetail("Warning", sup.warnings.joinIssues())
    }
}

@Composable
private fun UasGeographicalZoneSection(uasGeographicalZone: UasGeographicalZoneInfo?) {
    if (uasGeographicalZone == null || !uasGeographicalZone.hasContent()) return

    ZoneSection(title = "Zona geografica UAS") {
        ZoneOptionalDetail("Nome", uasGeographicalZone.id)
        ZoneOptionalDetail("Orari di attività", uasGeographicalZone.schedule)
        ZoneOptionalDetail("Attestato di competenza minimo richiesto", uasGeographicalZone.requiredLicense.formatRequiredLicense())
        ZoneOptionalDetail("Autorizzazione", uasGeographicalZone.authorizationRequired.authorizationRequiredText())
    }
}

@Composable
private fun AuthorizationSection(
    authorization: AuthorizationInfo?,
    onAuthorizationRequest: () -> Unit,
    allowDraftCreation: Boolean = true
) {
    if (authorization == null || !authorization.hasContent()) return
    val manualCheck = authorization.manualCheckSummary()

    ZoneSection(title = "Autorizzazioni") {
        AuthorizationBadges(authorization)
        if (manualCheck != null) {
            ZoneOptionalDetail("Stato operativo", manualCheck.first)
            ZoneOptionalDetail("Spiegazione", manualCheck.second)
        } else {
            ZoneOptionalDetail("Richiesta", authorization.requiredText())
        }
        ZoneOptionalDetail("Requisiti aggiuntivi", authorization.additionalRequirements.formatAdditionalRequirements())
        ZoneOptionalDetail("Requisito", authorization.requirement.usableAuthorizationText())
        ZoneOptionalDetail("Operazioni", authorization.operationSummary())
        ZoneOptionalDetail("Attestato di competenza minimo richiesto", authorization.requiredLicense.formatRequiredLicense())
        if (manualCheck == null) ZoneOptionalDetail("Spiegazione", authorization.explanation)
        ZoneOptionalDetail("Blocchi", authorization.blockingReasons.mapNotNull { it.code }.formatReasonCodes())
        if (authorization.procedures.isNotEmpty() || authorization.additionalRequirements.isNotEmpty()) {
            Button(
                onClick = onAuthorizationRequest,
                enabled = allowDraftCreation && authorization.canCreateLocalDraft()
            ) {
                Text("Richiedi autorizzazione")
            }
        }
    }
}

@Composable
private fun AuthorizationBadges(authorization: AuthorizationInfo) {
    val badges = buildList {
        authorization.procedures.forEach { procedure ->
            val label = procedure.label ?: procedure.type
            if (!label.isNullOrBlank()) add(procedure.type.orEmpty() to label)
        }
        authorization.additionalRequirements.forEach { requirement ->
            val label = requirement.label ?: requirement.type
            if (!label.isNullOrBlank()) add(requirement.type.orEmpty() to label)
        }
    }
    if (badges.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        badges.forEach { (type, label) ->
            val badgeColor = authorizationBadgeColor(type)
            CompactInfoPill(
                text = label,
                containerColor = badgeColor,
                contentColor = readableContentColor(badgeColor)
            )
        }
    }
}

@Composable
private fun AuthoritySection(authority: AuthorityInfo?) {
    if (authority == null || !authority.hasContent()) return

    ZoneSection(title = "Autorita / fonte") {
        ZoneOptionalDetail("Invio richieste a", authority.formatRequestContacts())
        ZoneOptionalDetail("Fonte", authority.source)
    }
}

@Composable
private fun EnrichedSection(enriched: List<KeyValueInfo>) {
    if (enriched.isEmpty()) return

    ZoneSection(title = "Dati enriched") {
        KeyValueList(enriched)
    }
}

@Composable
private fun ZoneSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun KeyValueList(items: List<KeyValueInfo>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            ZoneDetail(label = item.key, value = item.value)
        }
    }
}

@Composable
private fun VerdictBadge(response: ZoneCheckV3Response) {
    val altitude = response.verdict.maxAltitudeMetersAgl
    val badgeColor = dscAltitudeColor(altitude)
    val contentColor = readableContentColor(badgeColor)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = badgeColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = verdictBadgeTitle(response),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$altitude m AGL",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ZoneLimitPill(limit: Int) {
    val pillColor = dscAltitudeColor(limit)
    CompactInfoPill(
        text = "$limit m AGL",
        containerColor = pillColor,
        contentColor = readableContentColor(pillColor)
    )
}

@Composable
private fun ZoneStatusPill(text: String, inactive: Boolean) {
    CompactInfoPill(
        text = text,
        containerColor = if (inactive) InactiveZonePillColor else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (inactive) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
    )
}

@Composable
private fun CompactInfoPill(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun ZoneOptionalDetail(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    ZoneDetail(label = label, value = value)
}

@Composable
private fun ZoneDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun OfficialInfo.hasContent(): Boolean =
    !sourceText.isNullOrBlank() ||
        !sourceReference.isNullOrBlank() ||
        !qLine.isNullOrBlank() ||
        fields.isNotEmpty()

private fun ValidityInfo.hasContent(): Boolean =
    activeNow != null ||
        !validFrom.isNullOrBlank() ||
        !validTo.isNullOrBlank() ||
        !schedule.isNullOrBlank() ||
        !interpretedSchedule.isNullOrBlank() ||
        !explanation.isNullOrBlank() ||
        future != null ||
        expired != null

private fun it.droneskycheck.app.data.ScheduleInfo.hasContent(): Boolean =
    !raw.isNullOrBlank() ||
        !human.isNullOrBlank() ||
        activeNow != null ||
        !explanation.isNullOrBlank()

private fun AuthorizationInfo.hasContent(): Boolean =
    required == true ||
        !applicability.isNullOrBlank() ||
        !resolutionStatus.isNullOrBlank() ||
        procedures.isNotEmpty() ||
        additionalRequirements.isNotEmpty() ||
        reasonCodes.isNotEmpty() ||
        blockingReasons.isNotEmpty() ||
        requirement.usableAuthorizationText() != null ||
        operationSummary() != null ||
        requiredLicense.formatRequiredLicense() != null ||
        !explanation.isNullOrBlank()

private fun AuthorizationInfo.canCreateLocalDraft(): Boolean =
    resolutionStatus == "RESOLVED" &&
        procedures.any {
            it.type.equals("ATM05", ignoreCase = true) ||
                it.type.equals("ATM09", ignoreCase = true)
        }

private fun AuthorityInfo.hasContent(): Boolean =
    !name.isNullOrBlank() ||
        !code.isNullOrBlank() ||
        !contact.isNullOrBlank() ||
        emails.isNotEmpty() ||
        !note.isNullOrBlank() ||
        !source.isNullOrBlank()

private fun EnrInfo.hasContent(): Boolean =
    !code.isNullOrBlank() ||
        !name.isNullOrBlank() ||
        !description.isNullOrBlank() ||
        !limitText.isNullOrBlank() ||
        !notes.isNullOrBlank() ||
        !classification.isNullOrBlank() ||
        !activationType.isNullOrBlank() ||
        !operationMode.isNullOrBlank() ||
        !operationCategory.isNullOrBlank() ||
        !requiredLicense.isNullOrBlank() ||
        authorizationRequired == true ||
        schedule?.hasContent() == true ||
        authority?.hasContent() == true ||
        official?.hasContent() == true ||
        validity?.hasContent() == true ||
        !explanation.isNullOrBlank() ||
        !operationalMeaning.isNullOrBlank()

private fun SupInfo.hasContent(): Boolean =
    !title.isNullOrBlank() ||
        !reference.isNullOrBlank() ||
        !generality.isNullOrBlank() ||
        !description.isNullOrBlank() ||
        !operationMode.isNullOrBlank() ||
        !operationCategory.isNullOrBlank() ||
        !requiredLicense.isNullOrBlank() ||
        authorizationRequired != null ||
        authority?.hasContent() == true ||
        official?.hasContent() == true ||
        validity?.hasContent() == true ||
        authorization?.hasContent() == true ||
        !explanation.isNullOrBlank() ||
        !operationalMeaning.isNullOrBlank() ||
        blockers.isNotEmpty() ||
        warnings.isNotEmpty()

private fun UasGeographicalZoneInfo.hasContent(): Boolean =
    !id.isNullOrBlank() ||
        !generality.isNullOrBlank() ||
        !description.isNullOrBlank() ||
        !schedule.isNullOrBlank() ||
        !operationMode.isNullOrBlank() ||
        !operationCategory.isNullOrBlank() ||
        !requiredLicense.isNullOrBlank() ||
        authorizationRequired != null ||
        authority?.hasContent() == true

private fun ZoneInfo.primaryDescription(): String? =
    listOf(
        enr?.description,
        sup?.generality,
        sup?.description,
        uasGeographicalZone?.generality,
        uasGeographicalZone?.description,
        description
    )
        .mapNotNull { it.usableUserText() }
        .distinctBy { it.lowercase() }
        .joinToString("\n\n")
        .takeIf { it.isNotBlank() }

private fun ValidityInfo.statusLabel(): String? =
    when {
        activeNow == true -> "Attiva ora"
        expired == true -> "Scaduta"
        future == true -> "Futura"
        activeNow == false -> "Non attiva ora"
        else -> null
    }

private fun Boolean.formatBoolean(): String =
    if (this) "Sì" else "No"

private fun AuthorizationInfo.requiredText(): String? =
    when {
        procedures.isNotEmpty() || additionalRequirements.isNotEmpty() ->
            "Negli orari di attività della zona è necessario richiedere l'autorizzazione."
        required == true -> "Sì"
        resolutionStatus.equals("MANUAL_CHECK", ignoreCase = true) -> "Verifica manuale necessaria"
        resolutionStatus.equals("BLOCKED", ignoreCase = true) -> "Non gestibile automaticamente"
        else -> null
    }

private fun String?.formatResolutionStatus(): String? =
    when (this?.uppercase()) {
        "RESOLVED" -> "Risolta"
        "MANUAL_CHECK" -> "Verifica manuale"
        "BLOCKED" -> "Bloccata"
        else -> this
    }

private fun String?.formatApplicability(): String? =
    when (this?.uppercase()) {
        "WHEN_ACTIVE" -> "Quando la zona è attiva"
        "NONE" -> "Nessuna"
        else -> this
    }

private fun List<AuthorizationProcedure>.formatProcedures(): String? =
    mapNotNull { procedure ->
        val label = procedure.label ?: procedure.type
        val reason = procedure.reasonCode
        when {
            label.isNullOrBlank() -> null
            reason.isNullOrBlank() -> label
            else -> "$label ($reason)"
        }
    }.takeIf { it.isNotEmpty() }?.joinToString(", ")

private fun List<AuthorizationAdditionalRequirement>.formatAdditionalRequirements(): String? =
    mapNotNull { requirement ->
        val label = requirement.label ?: requirement.type
        val reason = requirement.reasonCode
        when {
            label.isNullOrBlank() -> null
            reason.isNullOrBlank() -> label
            else -> "$label ($reason)"
        }
    }.takeIf { it.isNotEmpty() }?.joinToString(", ")

private fun List<String>.formatReasonCodes(): String? =
    takeIf { it.isNotEmpty() }?.joinToString(", ")

private fun authorizationBadgeColor(type: String): Color =
    when (type.uppercase()) {
        "ATM09" -> Color(0xFFC0392B)
        "ATM05" -> Color(0xFFE67E22)
        "ENTE_PARCO" -> Color(0xFF7D3C98)
        else -> Color(0xFF5B6470)
    }

private fun EnrInfo.authorizationRequiredText(): String? =
    if (authorizationRequired == true) {
        "Negli orari di attività della zona è necessario richiedere l'autorizzazione."
    } else {
        null
    }

private fun Boolean?.authorizationRequiredText(): String? =
    if (this == true) {
        "Negli orari di attività della zona è necessario richiedere l'autorizzazione."
    } else {
        null
    }

private fun EnrInfo.officialSourceText(): String? {
    val officialText = official?.sourceText.cleanOfficialSourceText()
    val descriptionText = description.cleanOfficialSourceText()
        ?.takeUnless { it.isEquivalentTo(officialText) || officialText.containsEquivalent(it) }
    val rawSchedule = (schedule?.raw ?: validity?.schedule)
        .cleanOfficialSourceText()
        ?.takeUnless { it.isEquivalentTo(schedule?.human) }
        ?.takeUnless { officialText.containsEquivalent(it) || descriptionText.containsEquivalent(it) }

    return listOfNotNull(descriptionText, officialText, rawSchedule)
        .distinctBy { it.normalizedOfficialText() }
        .joinToString(separator = "\n\n")
        .ifBlank { null }
}

private fun String?.cleanOfficialSourceText(): String? {
    if (isNullOrBlank()) return null

    val cleanedLines = mutableListOf<String>()
    var skipNextSourceValue = false
    var skipNextInterpretedValue = false

    lineSequence().forEach { originalLine ->
        val trimmed = originalLine.trim()

        when {
            trimmed.isBlank() -> {
                if (cleanedLines.lastOrNull()?.isNotBlank() == true) cleanedLines.add("")
            }
            skipNextSourceValue -> {
                skipNextSourceValue = false
            }
            skipNextInterpretedValue -> {
                skipNextInterpretedValue = false
            }
            trimmed.isSourceReferenceLabel() -> {
                skipNextSourceValue = true
            }
            trimmed.isInterpretedScheduleLabel() -> {
                skipNextInterpretedValue = true
            }
            trimmed.isRawScheduleLabel() -> Unit
            else -> {
                trimmed.withoutTechnicalOfficialLabel()?.let { value ->
                    cleanedLines.add(value)
                }
            }
        }
    }

    return cleanedLines
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
        .joinToString(separator = "\n")
        .ifBlank { null }
}

private fun String.isSourceReferenceLabel(): Boolean =
    equals("Fonte", ignoreCase = true) ||
        equals("source", ignoreCase = true) ||
        equals("sourceReference", ignoreCase = true) ||
        equals("reference", ignoreCase = true)

private fun String.isInterpretedScheduleLabel(): Boolean =
    equals("scheduleHuman", ignoreCase = true) ||
        equals("Schedule interpretata", ignoreCase = true)

private fun String.isRawScheduleLabel(): Boolean =
    equals("rawSchedule", ignoreCase = true) ||
        equals("Orari raw", ignoreCase = true)

private fun String.withoutTechnicalOfficialLabel(): String? {
    val property = Regex(
        pattern = "^\"?(source|sourceReference|reference|scheduleHuman)\"?\\s*[:=]\\s*(.*)$",
        option = RegexOption.IGNORE_CASE
    )
    if (property.matches(this)) return null

    val rawValue = Regex(
        pattern = "^\"?(rawSchedule|schedule|officialText|sourceText|description)\"?\\s*[:=]\\s*(.+)$",
        option = RegexOption.IGNORE_CASE
    ).matchEntire(this)?.groupValues?.get(2)

    val value = rawValue ?: this
    return value.cleanJsonLikeValue()
        .takeUnless { it.isBlank() || it.matches(Regex("""^[{}\[\],]+$""")) }
}

private fun String.cleanJsonLikeValue(): String =
    trim()
        .removeSuffix(",")
        .trim()
        .trim('"')
        .trim()

private fun String?.containsEquivalent(other: String?): Boolean {
    val normalizedSelf = normalizedOfficialText()
    val normalizedOther = other.normalizedOfficialText()
    return normalizedSelf.isNotBlank() &&
        normalizedOther.isNotBlank() &&
        normalizedSelf.contains(normalizedOther)
}

private fun String?.isEquivalentTo(other: String?): Boolean {
    val normalizedSelf = normalizedOfficialText()
    val normalizedOther = other.normalizedOfficialText()
    return normalizedSelf.isNotBlank() &&
        normalizedSelf == normalizedOther
}

private fun String?.normalizedOfficialText(): String =
    orEmpty()
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")

private fun List<Issue>.joinIssues(): String =
    joinToString(separator = "\n") { issue ->
        listOfNotNull(
            issue.message?.cleanUserText(),
            issue.explanation?.cleanUserText(),
            issue.operationalMeaning?.cleanUserText(),
            issue.code?.toUserText(),
            issue.severity?.toUserText()
        ).distinct().joinToString(" - ").ifBlank { "Elemento senza dettaglio" }
    }

private fun LegalTimelineSegment.currentTimelineTitle(): String =
    when (state) {
        LegalTimelineState.AVAILABLE -> "Volo consentito"
        LegalTimelineState.AVAILABLE_WITH_LIMIT -> "Volo consentito con limite"
        LegalTimelineState.AUTH_REQUIRED -> "Autorizzazione richiesta"
        LegalTimelineState.UNAVAILABLE -> "Non puoi volare"
        LegalTimelineState.UNKNOWN -> "Stato da verificare"
    }

private fun LegalTimelineSegment.currentTimelineSubtitle(zoneId: ZoneId): String =
    when (state) {
        LegalTimelineState.AVAILABLE ->
            "max ${maxAltitudeAgl ?: 120} m AGL ${untilLocalTimeText(from, to, zoneId)}"
        LegalTimelineState.AVAILABLE_WITH_LIMIT ->
            "max ${maxAltitudeAgl ?: "-"} m AGL ${untilLocalTimeText(from, to, zoneId)}"
        LegalTimelineState.AUTH_REQUIRED ->
            untilLocalTimeText(from, to, zoneId)
        LegalTimelineState.UNAVAILABLE ->
            "${maxAltitudeAgl ?: 0} m AGL ${untilLocalTimeText(from, to, zoneId)}"
        LegalTimelineState.UNKNOWN ->
            untilLocalTimeText(from, to, zoneId)
    }

private fun LegalTimelineSegment.timelineRowText(): String =
    when (state) {
        LegalTimelineState.AVAILABLE -> "Volo consentito - max ${maxAltitudeAgl ?: 120} m AGL"
        LegalTimelineState.AVAILABLE_WITH_LIMIT -> "Volo consentito con limite - max ${maxAltitudeAgl ?: "-"} m AGL"
        LegalTimelineState.AUTH_REQUIRED -> "Autorizzazione richiesta"
        LegalTimelineState.UNAVAILABLE -> "Volo non consentito"
        LegalTimelineState.UNKNOWN -> "Da verificare"
    }

@Composable
private fun LegalTimelineSegment.timelineColor(): Color =
    when (state) {
        LegalTimelineState.AVAILABLE -> dscAltitudeColor(120)
        LegalTimelineState.AVAILABLE_WITH_LIMIT -> dscAltitudeColor(maxAltitudeAgl ?: 60)
        LegalTimelineState.AUTH_REQUIRED -> MaterialTheme.colorScheme.tertiary
        LegalTimelineState.UNAVAILABLE -> dscAltitudeColor(0)
        LegalTimelineState.UNKNOWN -> MaterialTheme.colorScheme.outline
    }

private fun untilLocalTimeText(from: Instant, to: Instant, zoneId: ZoneId): String {
    val localFrom = from.atZone(zoneId)
    val localTo = to.atZone(zoneId)
    val toText = DateTimeFormatter.ofPattern("HH:mm").format(localTo)
    val dayDelta = java.time.temporal.ChronoUnit.DAYS.between(localFrom.toLocalDate(), localTo.toLocalDate())
    return when (dayDelta) {
        0L -> "fino alle $toText"
        1L -> "fino a domani alle $toText"
        else -> "fino al ${DateTimeFormatter.ofPattern("dd/MM HH:mm").format(localTo)}"
    }
}

private fun WeatherAssessment.weatherSummary(): String =
    when (state) {
        WeatherState.FAVORABLE -> "Condizioni meteo favorevoli"
        WeatherState.CAUTION -> "Condizioni meteo da valutare"
        WeatherState.UNFAVORABLE -> "Condizioni meteo sfavorevoli"
        WeatherState.INSUFFICIENT_DATA -> "Dati meteo insufficienti"
    } + " - indice $score/100"

private data class WeatherFact(
    val icon: ImageVector,
    val label: String,
    val value: String
)

private fun WeatherForecastHour.weatherReferenceText(): String {
    val local = localDateTime?.let { DateTimeFormatter.ofPattern("HH:mm").format(it) }
        ?: offsetDateTime?.let { DateTimeFormatter.ofPattern("HH:mm").format(it) }
    return local?.let { "Valori dell'ora piu vicina: $it" } ?: "Valori dell'ora piu vicina"
}

private fun WeatherForecast.closestHour(now: Instant): WeatherForecastHour? =
    hours.minByOrNull { hour ->
        kotlin.math.abs(Duration.between(now, hour.instant).toMillis())
    }

private fun WeatherForecastHour.weatherFacts(assessment: WeatherAssessment): List<WeatherFact> {
    val currentMetrics = metrics
    return listOfNotNull(
        currentMetrics.windSpeedKmh?.let { speed ->
            WeatherFact(
                icon = Icons.Default.Air,
                label = "Vento",
                value = buildString {
                    append(speed.formatKmh())
                    currentMetrics.windDirectionDegrees?.let { append(" da ${it.roundToIntText()} gradi") }
                }
            )
        },
        currentMetrics.windGustsKmh?.let {
            WeatherFact(
                icon = Icons.Default.Speed,
                label = "Raffiche",
                value = it.formatKmh()
            )
        },
        WeatherFact(
            icon = Icons.Default.Opacity,
            label = "Pioggia",
            value = buildString {
                append(currentMetrics.precipitationMm?.formatMillimeters() ?: "dato non disponibile")
                currentMetrics.precipitationProbabilityPct?.let { append(" - probabilita ${it.formatPercent()}") }
            }
        ).takeIf { currentMetrics.precipitationMm != null || currentMetrics.precipitationProbabilityPct != null },
        currentMetrics.visibilityMeters?.let {
            WeatherFact(
                icon = Icons.Default.Visibility,
                label = "Visibilita",
                value = it.formatVisibility()
            )
        },
        currentMetrics.temperatureC?.let {
            WeatherFact(
                icon = Icons.Default.Thermostat,
                label = "Temperatura",
                value = "${it.roundToIntText()} C"
            )
        },
        currentMetrics.cloudCoverPct?.let {
            WeatherFact(
                icon = Icons.Default.Cloud,
                label = "Nuvole",
                value = it.formatPercent()
            )
        },
        WeatherFact(
            icon = Icons.Default.WbSunny,
            label = "Scenario",
            value = assessment.weatherCodeCategory.toUserText()
        )
    )
}

private fun WeatherAssessment.weatherReasonText(): String =
    if (reasons.isEmpty()) {
        "Nessun fattore meteo critico rilevato nell'ora piu vicina."
    } else {
        "Fattori rilevati: ${reasons.joinToString(", ") { it.toUserText() }}."
    }

private fun WeatherAssessment.weatherConfidenceText(): String =
    "Affidabilita dati: ${confidence.level.toUserText()} (${confidence.score}/100)"

private fun LocalDate.dayTitle(): String {
    val text = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALY).format(this)
    return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALY) else it.toString() }
}

private fun LegalDailyWindow.coversDisplayedDay(): Boolean =
    start == LocalTime.MIDNIGHT && end == LocalTime.MIDNIGHT

private fun LegalDailyWindow.formatLocalTimeRange(): String =
    "${start.formatHourMinute()} - ${end.formatHourMinute()}"

private fun LegalDailyWindow.legalWindowText(compact: Boolean = false): String =
    when (state) {
        LegalTimelineState.AVAILABLE ->
            if (compact) "Disponibile tutto il giorno - max ${maxAltitudeAgl ?: 120} m" else "Volo consentito - max ${maxAltitudeAgl ?: 120} m"
        LegalTimelineState.AVAILABLE_WITH_LIMIT ->
            if (compact) "Disponibile con limite - max ${maxAltitudeAgl ?: "-"} m" else "Volo consentito con limite - max ${maxAltitudeAgl ?: "-"} m"
        LegalTimelineState.AUTH_REQUIRED -> "Autorizzazione richiesta"
        LegalTimelineState.UNAVAILABLE -> "Volo non consentito"
        LegalTimelineState.UNKNOWN -> "Da verificare"
    }

private fun WeatherDailyTrend.conditionScoreText(): String =
    listOfNotNull(
        label.toUserText(),
        score?.let { "$it/100" }
    ).joinToString(" - ")

private fun WeatherDailyTrend.weatherTrendNote(): String? =
    when {
        availableHours <= 0 -> "Nessuna previsione disponibile"
        variable -> "Giornata variabile: non basarti solo sul punteggio"
        notes.isNotEmpty() -> "Criticita: ${notes.joinToString(", ") { it.toUserText() }}"
        else -> null
    }

private fun WeatherDailyTrendLabel.toUserText(): String =
    when (this) {
        WeatherDailyTrendLabel.FAVORABLE -> "Favorevole"
        WeatherDailyTrendLabel.CAUTION -> "Da valutare"
        WeatherDailyTrendLabel.UNFAVORABLE -> "Sfavorevole"
        WeatherDailyTrendLabel.VARIABLE -> "Variabile"
        WeatherDailyTrendLabel.INSUFFICIENT -> "Dati incompleti"
    }

private fun ForecastReliability.toUserText(): String =
    when (this) {
        ForecastReliability.HIGH -> "Alta"
        ForecastReliability.MEDIUM -> "Media"
        ForecastReliability.INDICATIVE -> "Indicativa"
    }

private fun WeatherTrendWindow.formatLocalTimeRange(): String =
    "${start.formatHourMinute()} - ${end.formatHourMinute()}"

private fun LocalTime.formatHourMinute(): String =
    DateTimeFormatter.ofPattern("HH:mm").format(this)

private fun WeatherForecast.weatherHorizonText(): String {
    val zoneId = timezone ?: ZoneId.systemDefault()
    val lastDate = hours.maxByOrNull { it.instant }?.let { hour ->
        hour.localDateTime?.toLocalDate() ?: hour.instant.atZone(zoneId).toLocalDate()
    }
    val daysText = metadata.forecastDays?.let { "$it giorni provider" }
    return listOfNotNull(
        daysText,
        lastDate?.let { "fino a ${it.dayTitle()}" }
    ).joinToString(" - ").ifBlank { "Orizzonte disponibile dalla previsione ricevuta" }
}

private fun WeatherConfidenceLevel.toUserText(): String =
    when (this) {
        WeatherConfidenceLevel.HIGH -> "alta"
        WeatherConfidenceLevel.MEDIUM -> "media"
        WeatherConfidenceLevel.LOW -> "bassa"
        WeatherConfidenceLevel.INSUFFICIENT -> "insufficiente"
    }

private fun WeatherCodeCategory.toUserText(): String =
    when (this) {
        WeatherCodeCategory.BENIGN -> "tempo stabile"
        WeatherCodeCategory.FOG -> "nebbia"
        WeatherCodeCategory.DRIZZLE -> "pioviggine"
        WeatherCodeCategory.RAIN -> "pioggia"
        WeatherCodeCategory.HEAVY_RAIN -> "pioggia intensa"
        WeatherCodeCategory.SNOW -> "neve"
        WeatherCodeCategory.SHOWERS -> "rovesci"
        WeatherCodeCategory.THUNDERSTORM -> "temporale"
        WeatherCodeCategory.THUNDERSTORM_WITH_HAIL -> "temporale con grandine"
        WeatherCodeCategory.UNKNOWN -> "codice meteo non riconosciuto"
    }

private fun WeatherReasonCode.toUserText(): String =
    when (this) {
        WeatherReasonCode.STRONG_WIND -> "vento sostenuto"
        WeatherReasonCode.HIGH_GUSTS -> "raffiche elevate"
        WeatherReasonCode.HIGH_GUST_SPREAD -> "raffiche molto variabili"
        WeatherReasonCode.HIGH_GUST_RATIO -> "raffiche proporzionalmente alte"
        WeatherReasonCode.PRECIPITATION -> "precipitazione"
        WeatherReasonCode.INTENSE_PRECIPITATION -> "precipitazione intensa"
        WeatherReasonCode.HIGH_PRECIPITATION_PROBABILITY -> "probabilita di pioggia elevata"
        WeatherReasonCode.FOG -> "nebbia"
        WeatherReasonCode.DRIZZLE -> "pioviggine"
        WeatherReasonCode.RAIN -> "pioggia"
        WeatherReasonCode.HEAVY_RAIN -> "pioggia intensa"
        WeatherReasonCode.SNOW -> "neve"
        WeatherReasonCode.SHOWERS -> "rovesci"
        WeatherReasonCode.THUNDERSTORM -> "temporale"
        WeatherReasonCode.THUNDERSTORM_WITH_HAIL -> "temporale con grandine"
        WeatherReasonCode.LOW_VISIBILITY -> "visibilita ridotta"
        WeatherReasonCode.EXTREME_TEMPERATURE -> "temperatura estrema"
        WeatherReasonCode.HIGH_CLOUD_COVER_INFO -> "copertura nuvolosa elevata"
        WeatherReasonCode.UNKNOWN_WEATHER_CODE -> "codice meteo non riconosciuto"
        WeatherReasonCode.WIND_MISSING -> "vento mancante"
        WeatherReasonCode.GUSTS_MISSING -> "raffiche mancanti"
        WeatherReasonCode.WEATHER_CODE_MISSING -> "codice meteo mancante"
        WeatherReasonCode.PRECIPITATION_MISSING -> "precipitazione mancante"
        WeatherReasonCode.PRECIPITATION_PROBABILITY_MISSING -> "probabilita precipitazione mancante"
        WeatherReasonCode.VISIBILITY_MISSING -> "visibilita mancante"
        WeatherReasonCode.TEMPERATURE_MISSING -> "temperatura mancante"
        WeatherReasonCode.CLOUD_COVER_MISSING -> "copertura nuvolosa mancante"
        WeatherReasonCode.INVALID_METRIC -> "dato fuori scala"
    }

private fun Double.formatKmh(): String =
    "${roundToIntText()} km/h"

private fun Double.formatPercent(): String =
    "${roundToIntText()}%"

private fun Double.formatMillimeters(): String =
    if (this < 0.05) "0 mm" else "${formatOneDecimal()} mm"

private fun Double.formatVisibility(): String =
    if (this >= 10_000.0) "10+ km" else "${(this / 1000.0).formatOneDecimal()} km"

private fun Double.roundToIntText(): String =
    roundToInt().toString()

private fun Double.formatOneDecimal(): String =
    String.format(Locale.ROOT, "%.1f", this)

private fun verdictBadgeTitle(response: ZoneCheckV3Response): String =
    when {
        response.verdict.maxAltitudeMetersAgl <= 0 ||
            response.verdict.status in setOf("NO_FLY", "PROHIBITED", "NOT_ALLOWED") ->
            "ORA NON PUOI VOLARE QUI"
        response.verdict.maxAltitudeMetersAgl < 120 ->
            "PUOI VOLARE FINO A"
        else ->
            "PUOI VOLARE FINO A"
    }

private fun verdictHeader(response: ZoneCheckV3Response): String =
    when (response.verdict.status) {
        "NO_FLY" -> "Volo non consentito"
        "LIMITED" -> "Volo consentito fino a ${response.verdict.maxAltitudeMetersAgl} m AGL"
        else -> "Volo consentito fino a ${response.verdict.maxAltitudeMetersAgl} m AGL"
    }

private fun ZoneCheckV3Response.sortedZones(): List<ZoneInfo> =
    zones.sortedWith(
        compareByDescending<ZoneInfo> { zone ->
            blockers.any { it.zoneName == zone.name }
        }.thenByDescending { zone ->
            warnings.any { it.zoneName == zone.name }
        }.thenBy { zone ->
            zone.limitMetersAgl ?: Int.MAX_VALUE
        }.thenBy { zone ->
            zone.name ?: ""
        }
    )

private fun zoneSubtitle(zone: ZoneInfo): String =
    zone.userCategoryTitle() ?: zone.family?.toUserText() ?: "Zona aeronautica"

@Composable
private fun zoneSeverityColor(zone: ZoneInfo, hasBlocker: Boolean, hasWarning: Boolean): Color =
    when {
        zone.isInactiveNow() -> MaterialTheme.colorScheme.outline
        hasBlocker || (zone.limitMetersAgl ?: 120) <= 0 -> dscAltitudeColor(0)
        hasWarning || (zone.limitMetersAgl ?: 120) < 60 -> dscAltitudeColor(zone.limitMetersAgl ?: 45)
        else -> MaterialTheme.colorScheme.primary
    }

@Composable
private fun dscAltitudeColor(limit: Int): Color =
    when {
        limit <= 0 -> DscZoneMapColors.noFly0m.toComposeColor()
        limit <= 25 -> DscZoneMapColors.limited25m.toComposeColor()
        limit <= 45 -> DscZoneMapColors.limited45m.toComposeColor()
        limit <= 60 -> DscZoneMapColors.limited60m.toComposeColor()
        else -> OpenVerdictColor
    }

private fun readableContentColor(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.58f) Color.Black else Color.White
}

private fun it.droneskycheck.app.map.Rgba.toComposeColor(): Color =
    Color(red / 255f, green / 255f, blue / 255f, 1f)

private fun ZoneInfo.displayName(): String =
    (name ?: "Zona senza nome").cleanZoneName()

private fun ZoneInfo.userCategoryTitle(): String? =
    MapLayerIds.categoryForFeatureType(type)?.title

private fun DemoZone.userCategoryTitle(): String? =
    MapLayerIds.categoryForFeatureType(type)?.title

private fun DemoZone.isNotamZone(): Boolean =
    type.contains("NOTAM", ignoreCase = true) ||
        name.contains("NOTAM", ignoreCase = true) ||
        userCategoryTitle()?.contains("NOTAM", ignoreCase = true) == true

private fun DemoZone.previewAltitudeText(): String {
    val isAirfield = type.contains("AVIOSUP", ignoreCase = true) ||
        name.contains("AVIOSUP", ignoreCase = true) ||
        userCategoryTitle()?.contains("aviosuperficie", ignoreCase = true) == true
    val prefix = if (isAirfield) "Aviosuperficie: " else ""

    return when {
        lowerLimit <= 0 -> "${prefix}prima indicazione: quota 0 m AGL. Confermo regole e orari con DSC."
        lowerLimit in 1..119 -> "${prefix}prima indicazione: fino a $lowerLimit m AGL. Confermo regole e orari con DSC."
        else -> "${prefix}nessun limite locale sotto 120 m AGL. Controllo comunque regole e orari con DSC."
    }
}

private fun ZoneInfo.zoneLimitLabel(): String? =
    when {
        isInactiveNow() -> null
        limitMetersAgl != null -> "${limitMetersAgl} m AGL"
        else -> null
    }

private fun ZoneInfo.userOperationalStatus(): String? =
    operationalStatus?.toUserText()
        ?: validity?.statusLabel()
        ?: activeNow?.let { if (it) "Attiva ora" else "Non attiva in questo momento" }

private fun ZoneInfo.isInactiveNow(): Boolean =
    operationalStatus in setOf("ENR_INACTIVE_NOW", "NOTAM_INACTIVE_NOW", "SUP_INACTIVE_NOW", "INACTIVE_NOW") ||
        activeNow == false

private fun NotamInfo.hasUsefulContent(): Boolean =
    !code.isNullOrBlank() &&
        (
            !summary.isNullOrBlank() ||
                !explanation.isNullOrBlank() ||
                schedule?.hasContent() == true ||
                !operationalMeaning.isNullOrBlank() ||
                official?.hasContent() == true ||
                validity?.hasContent() == true
        )

private fun String.cleanZoneName(): String =
    trim()
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .replace(Regex("^([A-Z0-9]+)\\s+(.+)$")) { match ->
            val code = match.groupValues[1]
            val rest = match.groupValues[2]
            if (code.length >= 5 && rest.any { it.isLetter() }) {
                "$code - ${rest.lowercase().replaceFirstChar { it.uppercase() }}"
            } else {
                match.value
            }
        }

private fun String.cleanUserText(): String =
    toUserText().replace(Regex("\\s+"), " ").trim()

private fun String?.usableUserText(): String? =
    this?.cleanUserText()
        ?.takeUnless { it.isBlank() || it.equals("NIL", ignoreCase = true) }

private fun String?.usableAuthorizationText(): String? =
    usableUserText()
        ?.takeUnless { it.isNoLikeValue() }
        ?.toUserText()

private fun String.isNoLikeValue(): Boolean =
    trim().trim('"').trim().uppercase() in setOf("NO", "FALSE", "N/A", "NA", "NONE", "NULL")

private fun String?.distinctFrom(other: String?): String? {
    val value = usableUserText() ?: return null
    val compare = other.usableUserText() ?: return value
    return value.takeUnless { it.equals(compare, ignoreCase = true) }
}

private fun String.toUserText(): String =
    when (trim().uppercase()) {
        "REQ_AUTHORIZATION", "AUTHORIZATION_REQUIRED" ->
            "Autorizzazione richiesta"
        "ACTIVE", "ENR_ACTIVE", "NOTAM_ACTIVE", "SUP_ACTIVE" ->
            "Attiva ora"
        "ACTIVE_LIMITED" ->
            "Attiva con limite"
        "ENR_INACTIVE_NOW", "NOTAM_INACTIVE_NOW", "SUP_INACTIVE_NOW", "INACTIVE_NOW", "SUP_INACTIVE" ->
            "Non attiva in questo momento"
        "ENR_TEMPORAL_UNKNOWN" ->
            "Orari non valutabili automaticamente"
        "CHECK_NOTAM" ->
            "Attivazione da verificare tramite NOTAM"
        "ACTIVE_ENR" ->
            "ENR attiva"
        "ACTIVE_HARD_NOTAM" ->
            "NOTAM attivo bloccante"
        "ACTIVE_SOFT_NOTAM" ->
            "NOTAM attivo da verificare"
        "ACTIVE_SUP_AUTH_REQUIRED" ->
            "SUP attivo con autorizzazione richiesta"
        "PROTECTED_AREA" ->
            "Area protetta"
        "AERONAUTICAL_INFRASTRUCTURE" ->
            "Infrastruttura aeronautica"
        "PROHIBITED" ->
            "Volo non consentito"
        "RESTRICTED" ->
            "Area regolamentata"
        "DANGER" ->
            "Area pericolosa"
        "HARD", "BLOCKER" ->
            "Bloccante"
        "SOFT", "WARNING" ->
            "Da verificare"
        "INFO", "INFORMATION" ->
            "Informativa"
        "OPEN_WITH_AUTH" ->
            "Operazioni OPEN previa autorizzazione"
        "SPECIFIC_REQUIRED" ->
            "Categoria Specific richiesta"
        "OPEN_POSSIBLE" ->
            "Operazioni OPEN possibili"
        "ATM09" ->
            "Procedura ATM09"
        "ATM05" ->
            "Autorizzazione ATM05"
        else -> this
    }

private fun List<Issue>.filterRelevantFor(zone: ZoneInfo): List<Issue> =
    filterNot { issue ->
        val code = issue.code?.trim()?.uppercase()
        code == zone.operationalStatus?.trim()?.uppercase() ||
            (zone.isInactiveNow() && code?.contains("INACTIVE") == true)
    }

private fun EnrInfo.operationSummary(): String? =
    operationSummary(operationMode, operationCategory)

private fun AuthorizationInfo.operationSummary(): String? =
    operationSummary(operationMode, operationCategory)

private fun operationSummary(mode: String?, category: String?): String? {
    val modeValue = mode.takeUnlessNoLike()
    val categoryValue = category.takeUnlessNoLike()
    val modeCode = modeValue?.trim()?.uppercase()
    val categoryCode = categoryValue?.trim()?.uppercase()

    return when {
        modeCode == null && categoryCode == null -> null
        modeCode == "OPEN_POSSIBLE" && categoryCode == "OPEN_WITH_AUTH" ->
            "Sono possibili operazioni in OPEN previa autorizzazione"
        modeCode == "SPECIFIC" || categoryCode == "SPECIFIC_REQUIRED" ->
            "Sono richieste operazioni in categoria Specific"
        categoryCode == "OPEN_WITH_AUTH" ->
            "Sono possibili operazioni in OPEN previa autorizzazione"
        modeCode == "OPEN_POSSIBLE" ->
            "Sono possibili operazioni in OPEN"
        else ->
            listOfNotNull(modeValue?.toUserText(), categoryValue?.toUserText())
                .distinct()
                .joinToString(" - ")
                .ifBlank { null }
    }
}

private fun String?.takeUnlessNoLike(): String? =
    this?.takeUnless { it.isNoLikeValue() }

private fun String?.formatRequiredLicense(): String? {
    if (isNullOrBlank()) return null
    val values = parseJsonStringList(this).ifEmpty {
        split(',', ';')
            .map { it.trim().trim('[', ']', '"') }
            .filter { it.isNotBlank() }
    }

    return values
        .map { it.replace("\\/", "/").trim() }
        .filter { it.isNotBlank() && !it.isNoLikeValue() }
        .distinct()
        .joinToString(" o ")
        .ifBlank { null }
}

private fun AuthorizationOperationData.takeoffMapPoint(): MapPoint? {
    val lat = takeoffLat
    val lon = takeoffLon
    return if (lat != null && lon != null && lat.isFinite() && lon.isFinite()) {
        MapPoint(lat, lon)
    } else {
        null
    }
}

private fun validateAreaPointSelection(
    draft: AuthorizationDraft,
    zones: List<DemoZone>
): String? {
    if (zones.isEmpty()) {
        return "Punto non aggiunto: la mappa non conferma che sia dentro la zona della richiesta."
    }

    if (zones.none { it.matchesDraftZone(draft) }) {
        return "Punto non aggiunto: deve restare dentro ${draft.zoneName.ifBlank { "la zona richiesta" }}."
    }

    val blockingZeroZone = zones.firstOrNull { zone ->
        zone.lowerLimit <= 0 && !zone.matchesDraftZone(draft)
    }
    if (blockingZeroZone != null) {
        return "Punto non aggiunto: qui c'e' anche ${blockingZeroZone.name}, altra zona 0 m."
    }

    return null
}

private fun DemoZone.toAuthorizationZoneReference(): AuthorizationZoneReference =
    AuthorizationZoneReference(
        id = id,
        name = name,
        type = type,
        lowerLimitMeters = lowerLimit,
        upperLimitMeters = upperLimit
    )

private fun DemoZone.matchesDraftZone(draft: AuthorizationDraft): Boolean {
    val zone = JSONObject(draft.zoneSnapshotJson)
    return id.matchesZoneIdentity(zone.optString("id")) ||
        name.matchesZoneIdentity(zone.optString("name"))
}

private fun String.matchesZoneIdentity(other: String): Boolean {
    val self = normalizeZoneIdentity()
    val target = other.normalizeZoneIdentity()
    return self.isNotBlank() &&
        target.isNotBlank() &&
        (
            self == target ||
                (self.length >= 4 && target.contains(self)) ||
                (target.length >= 4 && self.contains(target))
            )
}

private fun String.normalizeZoneIdentity(): String =
    trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\bli\\s+"), "li")
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun MapPoint.formatForPlanning(): String =
    "${lat.formatCoordinate()} ${lon.formatCoordinate()}"

private fun String.toWorkflowLabel(): String =
    when (this) {
        AuthorizationWorkflowSteps.Takeoff -> "seleziona decollo"
        AuthorizationWorkflowSteps.Area -> "disegna area"
        AuthorizationWorkflowSteps.Analysis -> "analisi area"
        AuthorizationWorkflowSteps.Form -> "compilazione"
        else -> this
    }

private fun AuthorityInfo.formatRequestContacts(): String? {
    val chunks = listOfNotNull(name, contact, source)
    val objects = chunks.mapNotNull { it.parseJsonObjectOrNull() }
    val note = note
        ?: objects.firstNotNullOfOrNull { it.optNullableText("note") }
        ?: name?.takeUnless { it.trim().startsWith("{") }
    val emails = emails +
        objects.flatMap { it.optStringList("emails") } +
        listOfNotNull(contact?.takeIf { "@" in it && !it.trim().startsWith("{") })

    if (note.isNullOrBlank() && emails.isEmpty()) return null

    return buildString {
        if (!note.isNullOrBlank()) append(note.cleanUserText())
        if (emails.isNotEmpty()) {
            if (isNotEmpty()) append(": ")
            append(emails.distinct().joinToString(", "))
        }
    }
}

private fun String?.formatNotamUtcDate(): String? {
    if (isNullOrBlank()) return null
    val value = trim()

    runCatching {
        return NotamUtcFormatter.format(Instant.parse(value))
    }

    val compact = Regex("""^(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})$""").matchEntire(value)
        ?: return value
    val date = LocalDateTime.of(
        2000 + compact.groupValues[1].toInt(),
        compact.groupValues[2].toInt(),
        compact.groupValues[3].toInt(),
        compact.groupValues[4].toInt(),
        compact.groupValues[5].toInt()
    )
    return NotamUtcFormatter.format(date.atOffset(ZoneOffset.UTC))
}

private fun String?.toItalianNotamSchedule(): String? {
    if (isNullOrBlank()) return null
    val value = trim().uppercase().replace(Regex("\\s+"), " ")
    val dayText = when {
        "DAILY" in value -> "Ogni giorno"
        Regex("\\bMON-FRI\\b").containsMatchIn(value) -> "Da lunedì a venerdì"
        Regex("\\bMON-THU\\b").containsMatchIn(value) -> "Da lunedì a giovedì"
        Regex("\\bSAT\\b").containsMatchIn(value) -> "Sabato"
        Regex("\\bSUN\\b").containsMatchIn(value) -> "Domenica"
        Regex("\\bFRI\\b").containsMatchIn(value) -> "Venerdì"
        Regex("\\bTHU\\b").containsMatchIn(value) -> "Giovedì"
        Regex("\\bWED\\b").containsMatchIn(value) -> "Mercoledì"
        Regex("\\bTUE\\b").containsMatchIn(value) -> "Martedì"
        Regex("\\bMON\\b").containsMatchIn(value) -> "Lunedì"
        else -> null
    }
    val time = Regex("""\b(\d{2})(\d{2})-(\d{2})(\d{2})\b""").find(value)
    val timeText = time?.let {
        "dalle ${it.groupValues[1]}:${it.groupValues[2]} alle ${it.groupValues[3]}:${it.groupValues[4]} UTC"
    } ?: if ("H24" in value) "24 ore su 24 UTC" else null

    return listOfNotNull(dayText, timeText)
        .joinToString(" ")
        .ifBlank { value }
}

private fun String.parseJsonObjectOrNull(): JSONObject? =
    runCatching { JSONObject(this) }.getOrNull()

private fun parseJsonStringList(value: String): List<String> =
    runCatching {
        val array = JSONArray(value)
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }.getOrDefault(emptyList())

private fun JSONObject.optNullableText(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun JSONObject.optStringList(name: String): List<String> {
    if (!has(name) || isNull(name)) return emptyList()
    val value = opt(name)
    return when (value) {
        is JSONArray -> (0 until value.length()).mapNotNull { index ->
            value.optString(index).takeIf { it.isNotBlank() }
        }
        is String -> listOf(value).filter { it.isNotBlank() }
        else -> emptyList()
    }
}

private fun Double.formatCoordinate(): String =
    "%.${CoordinateDecimals}f".format(this)

private fun DscLayerCategory.swatchColor(): Color =
    Color(android.graphics.Color.parseColor(swatchHex))

@Composable
private fun LocationPermissionRevocationEffect(
    enabled: Boolean,
    permissionState: LocationPermissionState,
    onPermissionRevoked: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && enabled) {
                val current = currentLocationPermissionState(context)
                if (!current.hasForegroundLocation) {
                    onPermissionRevoked()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun UserLocationUpdatesEffect(
    enabled: Boolean,
    permissionState: LocationPermissionState,
    onLocationUpdated: (UserLocation) -> Unit,
    onProviderUnavailable: () -> Unit,
    onPermissionRevoked: () -> Unit
) {
    val context = LocalContext.current

    DisposableEffect(enabled, permissionState) {
        if (!enabled) {
            return@DisposableEffect onDispose {}
        }
        if (!permissionState.hasForegroundLocation) {
            onPermissionRevoked()
            return@DisposableEffect onDispose {}
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = locationProviders(locationManager, permissionState)
        if (providers.isEmpty()) {
            onProviderUnavailable()
            return@DisposableEffect onDispose {}
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocationUpdated(location.toUserLocation(permissionState.hasFineLocation))
            }

            override fun onProviderDisabled(provider: String) {
                if (locationProviders(locationManager, permissionState).isEmpty()) {
                    onProviderUnavailable()
                }
            }

            override fun onProviderEnabled(provider: String) = Unit

            @Deprecated("Deprecated in Android platform")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        try {
            providers.forEach { provider ->
                locationManager.getLastKnownLocation(provider)?.let { location ->
                    onLocationUpdated(location.toUserLocation(permissionState.hasFineLocation))
                }
                locationManager.requestLocationUpdates(
                    provider,
                    LOCATION_MIN_TIME_MS,
                    LOCATION_MIN_DISTANCE_METERS,
                    listener,
                    Looper.getMainLooper()
                )
            }
        } catch (_: SecurityException) {
            onPermissionRevoked()
        } catch (_: IllegalArgumentException) {
            onProviderUnavailable()
        }

        onDispose {
            locationManager.removeUpdates(listener)
        }
    }
}

private fun locationProviders(
    locationManager: LocationManager,
    permissionState: LocationPermissionState
): List<String> =
    buildList {
        if (permissionState.hasFineLocation && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            add(LocationManager.GPS_PROVIDER)
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            add(LocationManager.NETWORK_PROVIDER)
        }
    }

private fun Location.toUserLocation(isPrecise: Boolean): UserLocation =
    UserLocation(
        point = MapPoint(latitude, longitude),
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        isPrecise = isPrecise
    )

private fun currentLocationPermissionState(context: Context): LocationPermissionState =
    LocationPermissionState(
        hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED,
        hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    )

private data class LocationPermissionState(
    val hasFineLocation: Boolean,
    val hasCoarseLocation: Boolean
) {
    val hasForegroundLocation: Boolean
        get() = hasFineLocation || hasCoarseLocation
}

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private class MapViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(
                zoneCheckRepository = ZoneCheckV3Repository(),
                legalTimelineRepository = LegalTimelineRepository(),
                weatherForecastRepository = WeatherForecastRepository(),
                weatherAssessmentEngine = WeatherAssessmentEngine(),
                mapPreferences = MapPreferencesRepository(context)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}

private val LocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private const val LOCATION_MIN_TIME_MS = 2_500L
private const val LOCATION_MIN_DISTANCE_METERS = 5f
private const val MaxTimelineSegments = 6
private const val MaxDailySummaryWindows = 4
private val ZoneSheetMaxHeight = 720.dp
private val NotamUtcFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC)
private val OpenVerdictColor = Color(46, 125, 50)
private val InactiveZonePillColor = Color(46, 125, 50)
private val TemporalActiveColor = Color(198, 40, 40)
private val TemporalInactiveColor = Color(46, 125, 50)
private val TemporalUnknownColor = Color(144, 164, 174)
private const val CoordinateDecimals = 5
