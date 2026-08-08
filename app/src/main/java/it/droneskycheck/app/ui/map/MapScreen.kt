package it.droneskycheck.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.droneskycheck.app.data.ZoneCheckV3Response
import it.droneskycheck.app.map.DroneSkyMapView

@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        DroneSkyMapView(
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

        uiState.selectedPoint?.let { point ->
            ZoneBottomSheet(
                point = point,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 36.dp),
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
                    text = verdictTitle(response),
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
                response.zones.firstOrNull()?.description?.let { description ->
                    ZoneDetail(label = "Descrizione", value = description)
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

private fun Double.formatCoordinate(): String =
    "%.${CoordinateDecimals}f".format(this)

private const val CoordinateDecimals = 5
