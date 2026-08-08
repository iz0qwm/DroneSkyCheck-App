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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import it.droneskycheck.app.data.AuthorizationInfo
import it.droneskycheck.app.data.AuthorityInfo
import it.droneskycheck.app.data.EnrInfo
import it.droneskycheck.app.data.Issue
import it.droneskycheck.app.data.KeyValueInfo
import it.droneskycheck.app.data.NotamInfo
import it.droneskycheck.app.data.OfficialInfo
import it.droneskycheck.app.data.SupInfo
import it.droneskycheck.app.data.ValidityInfo
import it.droneskycheck.app.data.ZoneCheckV3Response
import it.droneskycheck.app.data.ZoneInfo
import it.droneskycheck.app.map.DscLayerCategory
import it.droneskycheck.app.map.DscZoneMapColors
import it.droneskycheck.app.map.DroneSkyMapView
import it.droneskycheck.app.map.MapLayerIds
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val uiState by viewModel.uiState.collectAsState()
    val visibleLayerCategories = uiState.layerVisibility
        .filterValues { it }
        .keys
    val permissionState = currentLocationPermissionState(context)
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
            userLocation = uiState.userLocation,
            shouldCenterOnUserLocation = uiState.shouldCenterOnUserLocation,
            onUserLocationCentered = viewModel::onUserLocationCentered,
            onMapTapped = viewModel::onMapTapped,
            onCameraIdle = viewModel::onCameraIdle,
            modifier = Modifier.fillMaxSize()
        )

        MapTitlePill(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

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
                onDisable = viewModel::onLocationDisabled,
                onDismiss = viewModel::onLocationControlDismissed
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
                onRetry = viewModel::onZoneCheckRetryRequested,
                onDismiss = viewModel::onZoneSheetDismissed
            )
        }
    }
}

@Composable
private fun LayerFilterFab(
    hiddenCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = if (hiddenCount > 0) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (hiddenCount > 0) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            LayerStackIcon(modifier = Modifier.size(28.dp))
            if (hiddenCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp),
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End
    ) {
        LocationFab(
            isEnabled = isLocationEnabled,
            hasFix = hasUserLocation,
            onClick = onLocationClick
        )
        LayerFilterFab(
            hiddenCount = hiddenCount,
            onClick = onLayersClick
        )
    }
}

@Composable
private fun LocationFab(
    isEnabled: Boolean,
    hasFix: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = if (isEnabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (isEnabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            LocationTargetIcon(modifier = Modifier.size(28.dp))
            if (isEnabled && !hasFix) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(10.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary
                ) {}
            }
        }
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
                    Text("Disattiva")
                }
                Spacer(modifier = Modifier.size(10.dp))
                Button(
                    onClick = onRecenter,
                    enabled = userLocation != null
                ) {
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
private fun MapTitlePill(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(max = 280.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Drone Sky Check",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Mappa UAS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    onRetry: () -> Unit,
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
                CircularProgressIndicator()
                Text(
                    text = "Verifica operativa in corso",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
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
                            warnings = response.warnings
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
                    Text("Riprova")
                }
            }

            if (verdict == null) {
                zone?.let { tappedZone ->
                    ZoneOptionalDetail("Zona selezionata", tappedZone.name.cleanZoneName())
                    ZoneOptionalDetail("Categoria", tappedZone.userCategoryTitle())
                    ZoneOptionalDetail("Requisito", tappedZone.restriction?.toUserText())
                }
            }

            ZoneDetail(
                label = "Punto interrogato",
                value = "${point.lat.formatCoordinate()}, ${point.lon.formatCoordinate()}"
            )
                }
            }
        }
    }
}

@Composable
private fun ZoneInfoCard(
    zone: ZoneInfo,
    index: Int,
    verdict: ZoneCheckV3Response,
    blockers: List<Issue>,
    warnings: List<Issue>
) {
    var expanded by remember(zone.name, zone.type, index) { mutableStateOf(index == 0) }
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
                ZoneNarrativeSection(zone)
                OfficialSection(zone.official)
                NotamSection(zone.notams)
                EnrSection(zone.enr)
                SupSection(zone.sup)
                AuthorizationSection(zone.authorization)
                AuthoritySection(zone.authority)
                ZoneOptionalDetail("Descrizione", zone.description?.cleanUserText())
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
private fun ZoneNarrativeSection(zone: ZoneInfo) {
    val narrative = zone.info
    if (narrative?.summary.isNullOrBlank() &&
        narrative?.explanation.isNullOrBlank() &&
        narrative?.operationalMeaning.isNullOrBlank()
    ) {
        return
    }

    ZoneSection(title = "Situazione operativa") {
        ZoneOptionalDetail("Sintesi", narrative?.summary)
        ZoneOptionalDetail("Spiegazione DSC", narrative?.explanation)
        ZoneOptionalDetail("Significato operativo", narrative?.operationalMeaning)
    }
}

@Composable
private fun OfficialSection(
    official: OfficialInfo?,
    title: String = "Informazioni ufficiali"
) {
    if (official == null || !official.hasContent()) return
    var expanded by remember(title, official.sourceText, official.sourceReference) { mutableStateOf(false) }

    ZoneSection(title = title) {
        ZoneOptionalDetail("Fonte", official.sourceReference)
        if (official.fields.isNotEmpty()) {
            KeyValueList(official.fields)
        }
        official.sourceText?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Comprimi testo" else "Leggi testo completo")
            }
        }
    }
}

@Composable
private fun ValiditySection(validity: ValidityInfo?) {
    if (validity == null || !validity.hasContent()) return

    ZoneSection(title = "Validita e orari") {
        ZoneOptionalDetail("Da", validity.validFrom)
        ZoneOptionalDetail("A", validity.validTo)
        ZoneOptionalDetail("Schedule", validity.schedule)
        ZoneOptionalDetail("Schedule interpretata", validity.interpretedSchedule)
    }
}

@Composable
private fun NotamSection(notams: List<NotamInfo>) {
    val usefulNotams = notams.filter { it.hasUsefulContent() }
    if (usefulNotams.isEmpty()) return

    ZoneSection(title = "NOTAM") {
        usefulNotams.forEachIndexed { index, notam ->
            if (index > 0) HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = notam.code.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                ZoneOptionalDetail("Stato", notam.validity?.statusLabel())
                ZoneOptionalDetail("FIR", notam.fir)
                ZoneOptionalDetail("Localita", notam.location)
                ZoneOptionalDetail("Zona", notam.zoneReference)
                ZoneOptionalDetail("Attivita", notam.activityType)
                ZoneOptionalDetail("Impatto", notam.severity?.toUserText())
                ZoneOptionalDetail("Sintesi", notam.summary)
                ZoneOptionalDetail("Spiegazione", notam.explanation)
                ZoneOptionalDetail("Significato operativo", notam.operationalMeaning)
                ZoneOptionalDetail("Motivo bloccante", notam.blockingReason)
                ValiditySection(notam.validity)
                OfficialSection(notam.official, title = "Testo ufficiale NOTAM")
                if (notam.blockers.isNotEmpty()) ZoneOptionalDetail("Blocker", notam.blockers.joinIssues())
                if (notam.warnings.isNotEmpty()) ZoneOptionalDetail("Warning", notam.warnings.joinIssues())
            }
        }
    }
}

@Composable
private fun EnrSection(enr: EnrInfo?) {
    if (enr == null || !enr.hasContent()) return

    ZoneSection(title = "ENR") {
        ZoneOptionalDetail("Nome", enr.name)
        ZoneOptionalDetail("Attivazione", enr.activationType?.toUserText())
        ZoneOptionalDetail("Operazioni", enr.operationSummary())
        ZoneOptionalDetail("Attestato di competenza minimo richiesto", enr.requiredLicense.formatRequiredLicense())
        ZoneOptionalDetail("Autorizzazione", enr.authorizationRequired?.formatBoolean())
        ZoneOptionalDetail("Spiegazione", enr.explanation)
        ZoneOptionalDetail("Significato operativo", enr.operationalMeaning)
        ValiditySection(enr.validity)
        AuthoritySection(enr.authority)
        OfficialSection(enr.official, title = "Testo ufficiale ENR")
    }
}

@Composable
private fun SupSection(sup: SupInfo?) {
    if (sup == null || !sup.hasContent()) return

    ZoneSection(title = "SUP") {
        ZoneOptionalDetail("Titolo", sup.title)
        ZoneOptionalDetail("Riferimento", sup.reference)
        ZoneOptionalDetail("Generalita", sup.generality)
        ZoneOptionalDetail("Descrizione", sup.description)
        ZoneOptionalDetail("Spiegazione", sup.explanation)
        ZoneOptionalDetail("Significato operativo", sup.operationalMeaning)
        ValiditySection(sup.validity)
        AuthorizationSection(sup.authorization)
        AuthoritySection(sup.authority)
        OfficialSection(sup.official, title = "Testo ufficiale SUP")
        if (sup.blockers.isNotEmpty()) ZoneOptionalDetail("Blocker", sup.blockers.joinIssues())
        if (sup.warnings.isNotEmpty()) ZoneOptionalDetail("Warning", sup.warnings.joinIssues())
    }
}

@Composable
private fun AuthorizationSection(authorization: AuthorizationInfo?) {
    if (authorization == null || !authorization.hasContent()) return

    ZoneSection(title = "Autorizzazioni") {
        ZoneOptionalDetail("Richiesta", authorization.required?.formatBoolean())
        ZoneOptionalDetail("Requisito", authorization.requirement?.toUserText())
        ZoneOptionalDetail("Operazioni", authorization.operationSummary())
        ZoneOptionalDetail("Attestato di competenza minimo richiesto", authorization.requiredLicense.formatRequiredLicense())
        ZoneOptionalDetail("Spiegazione", authorization.explanation)
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
    val contentColor = if (altitude == 45) Color.Black else Color.White

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
    CompactInfoPill(
        text = "$limit m AGL",
        containerColor = dscAltitudeColor(limit),
        contentColor = if (limit == 45) Color.Black else Color.White
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

private fun AuthorizationInfo.hasContent(): Boolean =
    required != null ||
        !requirement.isNullOrBlank() ||
        !operationMode.isNullOrBlank() ||
        !operationCategory.isNullOrBlank() ||
        !requiredLicense.isNullOrBlank() ||
        !explanation.isNullOrBlank()

private fun AuthorityInfo.hasContent(): Boolean =
    !name.isNullOrBlank() ||
        !code.isNullOrBlank() ||
        !contact.isNullOrBlank() ||
        !source.isNullOrBlank()

private fun EnrInfo.hasContent(): Boolean =
    !code.isNullOrBlank() ||
        !name.isNullOrBlank() ||
        !classification.isNullOrBlank() ||
        !activationType.isNullOrBlank() ||
        !operationMode.isNullOrBlank() ||
        !operationCategory.isNullOrBlank() ||
        !requiredLicense.isNullOrBlank() ||
        authorizationRequired != null ||
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
        authority?.hasContent() == true ||
        official?.hasContent() == true ||
        validity?.hasContent() == true ||
        authorization?.hasContent() == true ||
        !explanation.isNullOrBlank() ||
        !operationalMeaning.isNullOrBlank() ||
        blockers.isNotEmpty() ||
        warnings.isNotEmpty()

private fun ValidityInfo.statusLabel(): String? =
    when {
        activeNow == true -> "Attiva ora"
        expired == true -> "Scaduta"
        future == true -> "Futura"
        activeNow == false -> "Non attiva ora"
        else -> null
    }

private fun Boolean.formatBoolean(): String =
    if (this) "Si" else "No"

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
        else -> MaterialTheme.colorScheme.primary
    }

private fun it.droneskycheck.app.map.Rgba.toComposeColor(): Color =
    Color(red / 255f, green / 255f, blue / 255f, 1f)

private fun ZoneInfo.displayName(): String =
    (name ?: "Zona senza nome").cleanZoneName()

private fun ZoneInfo.userCategoryTitle(): String? =
    MapLayerIds.categoryForFeatureType(type)?.title

private fun DemoZone.userCategoryTitle(): String? =
    MapLayerIds.categoryForFeatureType(type)?.title

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
    val modeCode = mode?.trim()?.uppercase()
    val categoryCode = category?.trim()?.uppercase()

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
            listOfNotNull(mode?.toUserText(), category?.toUserText())
                .distinct()
                .joinToString(" - ")
                .ifBlank { null }
    }
}

private fun String?.formatRequiredLicense(): String? {
    if (isNullOrBlank()) return null
    val values = parseJsonStringList(this).ifEmpty {
        split(',', ';')
            .map { it.trim().trim('[', ']', '"') }
            .filter { it.isNotBlank() }
    }

    return values
        .map { it.replace("\\/", "/").trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" o ")
        .ifBlank { null }
}

private fun AuthorityInfo.formatRequestContacts(): String? {
    val chunks = listOfNotNull(name, contact, source)
    val objects = chunks.mapNotNull { it.parseJsonObjectOrNull() }
    val note = objects.firstNotNullOfOrNull { it.optNullableText("note") }
        ?: name?.takeUnless { it.trim().startsWith("{") }
    val emails = objects.flatMap { it.optStringList("emails") } +
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

private val LocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private const val LOCATION_MIN_TIME_MS = 2_500L
private const val LOCATION_MIN_DISTANCE_METERS = 5f
private val ZoneSheetMaxHeight = 720.dp
private val InactiveZonePillColor = Color(46, 125, 50)
private const val CoordinateDecimals = 5
