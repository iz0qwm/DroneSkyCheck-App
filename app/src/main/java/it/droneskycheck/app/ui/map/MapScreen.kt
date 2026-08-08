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
import it.droneskycheck.app.data.Issue
import it.droneskycheck.app.data.ZoneCheckV3Response
import it.droneskycheck.app.data.ZoneInfo
import it.droneskycheck.app.map.DscLayerCategory
import it.droneskycheck.app.map.DroneSkyMapView

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
                Text(
                    text = verdictHeader(response),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = response.verdict.explanation,
                    style = MaterialTheme.typography.titleMedium,
                    color = verdictColor(response.verdict.status)
                )
                ZoneDetail(
                    label = "Limite massimo consentito",
                    value = "${response.verdict.maxAltitudeMetersAgl} m AGL"
                )
                ZoneDetail(label = "Fonte verdetto", value = response.verdict.source ?: "Baseline")
                ZoneDetail(label = "Motore", value = "${response.meta.engine} ${response.meta.version}")
                if (response.blockers.isNotEmpty()) {
                    ZoneDetail(
                        label = "Blocker",
                        value = response.blockers.joinToString { it.code ?: "BLOCKER" }
                    )
                }
                if (response.warnings.isNotEmpty()) {
                    ZoneDetail(
                        label = "Warning",
                        value = response.warnings.joinToString { it.code ?: "WARNING" }
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
                ZoneDetail(label = "Errore", value = it)
            }

            zone?.let { tappedZone ->
                ZoneDetail(label = "Feature cartografica", value = tappedZone.name)
                ZoneDetail(label = "Layer", value = tappedZone.type)
                tappedZone.restriction?.let { restriction ->
                    ZoneDetail(label = "Restriction", value = restriction)
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
    blockers: List<Issue>,
    warnings: List<Issue>
) {
    var expanded by remember(zone.name, zone.type, index) { mutableStateOf(index == 0) }
    val hasBlocker = blockers.any { it.zoneName == zone.name }
    val hasWarning = warnings.any { it.zoneName == zone.name }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                        text = zone.name ?: "Zona senza nome",
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactInfoPill(zone.limitMetersAgl?.let { "$it m AGL" } ?: "Limite N/D")
                zone.activeNow?.let { CompactInfoPill(if (it) "Attiva" else "Non attiva") }
                if (zone.authorizationRequired == true) CompactInfoPill("Autorizzazione")
            }

            if (expanded) {
                ZoneOptionalDetail("Famiglia", zone.family)
                ZoneOptionalDetail("Tipo", zone.type)
                ZoneOptionalDetail("Descrizione", zone.description)
                if (hasBlocker) {
                    ZoneOptionalDetail(
                        "Blocker zona",
                        blockers.filter { it.zoneName == zone.name }.joinToString { it.code ?: "BLOCKER" }
                    )
                }
                if (hasWarning) {
                    ZoneOptionalDetail(
                        "Warning zona",
                        warnings.filter { it.zoneName == zone.name }.joinToString { it.code ?: "WARNING" }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactInfoPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
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

@Composable
private fun statusColor(status: DemoZoneStatus) = when (status) {
    DemoZoneStatus.NoFly -> MaterialTheme.colorScheme.error
    DemoZoneStatus.Limited -> MaterialTheme.colorScheme.tertiary
    DemoZoneStatus.Open -> MaterialTheme.colorScheme.primary
}

@Composable
private fun verdictColor(status: String) = when (status) {
    "NO_FLY" -> MaterialTheme.colorScheme.error
    "LIMITED" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

private fun verdictTitle(response: ZoneCheckV3Response): String =
    when (response.verdict.status) {
        "NO_FLY" -> "NO FLY"
        "LIMITED" -> "LIMITED"
        else -> "OPEN"
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
    listOfNotNull(zone.family, zone.type)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { "Classificazione non disponibile" }

@Composable
private fun zoneSeverityColor(zone: ZoneInfo, hasBlocker: Boolean, hasWarning: Boolean): Color =
    when {
        hasBlocker || (zone.limitMetersAgl ?: 120) <= 0 -> MaterialTheme.colorScheme.error
        hasWarning || (zone.limitMetersAgl ?: 120) < 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
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
private const val CoordinateDecimals = 5
