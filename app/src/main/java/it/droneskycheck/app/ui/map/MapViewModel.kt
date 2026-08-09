package it.droneskycheck.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.droneskycheck.app.data.ZoneCheckV3Repository
import it.droneskycheck.app.map.DscLayerCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel(
    private val zoneCheckRepository: ZoneCheckV3Repository = ZoneCheckV3Repository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun onMapTapped(selection: MapTapSelection) {
        requestVerdict(selection)
    }

    fun onZoneCheckRetryRequested() {
        val point = _uiState.value.selectedPoint ?: return
        requestVerdict(
            MapTapSelection(
                point = point,
                zone = _uiState.value.selectedZone
            )
        )
    }

    private fun requestVerdict(selection: MapTapSelection) {
        _uiState.value = _uiState.value.copy(
            selectedZone = selection.zone,
            selectedPoint = selection.point,
            isZoneSheetVisible = true,
            isVerdictLoading = true,
            verdict = null,
            verdictError = null
        )

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    zoneCheckRepository.check(
                        lat = selection.point.lat,
                        lon = selection.point.lon
                    )
                }
            }.onSuccess { response ->
                _uiState.value = _uiState.value.copy(
                    isVerdictLoading = false,
                    verdict = response,
                    verdictError = null,
                    mapStatusMessage = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isVerdictLoading = false,
                    verdictError = "DSC non e' raggiungibile in questo momento."
                )
            }
        }
    }

    fun onMapDataDegraded() {
        _uiState.value = _uiState.value.copy(
            mapStatusMessage = CachedMapDataMessage
        )
        viewModelScope.launch {
            delay(StatusMessageMillis)
            if (_uiState.value.mapStatusMessage == CachedMapDataMessage) {
                _uiState.value = _uiState.value.copy(mapStatusMessage = null)
            }
        }
    }

    fun onZoneSheetDismissed() {
        _uiState.value = _uiState.value.copy(
            selectedZone = null,
            isZoneSheetVisible = false,
            isVerdictLoading = false,
            verdict = null,
            verdictError = null
        )
    }

    fun onCameraIdle(bounds: CameraBounds) {
        _uiState.value = _uiState.value.copy(cameraBounds = bounds)
    }

    fun onLayerPanelRequested() {
        _uiState.value = _uiState.value.copy(isLayerSheetVisible = true)
    }

    fun onLayerPanelDismissed() {
        _uiState.value = _uiState.value.copy(isLayerSheetVisible = false)
    }

    fun onLayerCategoryVisibilityChanged(category: DscLayerCategory, isVisible: Boolean) {
        _uiState.value = _uiState.value.copy(
            layerVisibility = _uiState.value.layerVisibility + (category to isVisible)
        )
    }

    fun onShowAllLayerCategories() {
        _uiState.value = _uiState.value.copy(
            layerVisibility = DscLayerCategory.defaultVisibility
        )
    }

    fun onHideAllLayerCategories() {
        _uiState.value = _uiState.value.copy(
            layerVisibility = DscLayerCategory.entries.associateWith { false }
        )
    }

    fun onLocationPermissionExplanationRequested() {
        _uiState.value = _uiState.value.copy(
            locationPermissionSheetVisible = true,
            locationStatusMessage = null
        )
    }

    fun onLocationPermissionExplanationDismissed() {
        _uiState.value = _uiState.value.copy(locationPermissionSheetVisible = false)
    }

    fun onLocationEnabled() {
        _uiState.value = _uiState.value.copy(
            isUserLocationEnabled = true,
            shouldCenterOnUserLocation = true,
            isLocationControlSheetVisible = false,
            locationPermissionSheetVisible = false,
            locationStatusMessage = null
        )
    }

    fun onLocationDisabled() {
        _uiState.value = _uiState.value.copy(
            isUserLocationEnabled = false,
            userLocation = null,
            shouldCenterOnUserLocation = false,
            isLocationControlSheetVisible = false,
            locationStatusMessage = null
        )
    }

    fun onLocationControlRequested() {
        _uiState.value = _uiState.value.copy(isLocationControlSheetVisible = true)
    }

    fun onLocationControlDismissed() {
        _uiState.value = _uiState.value.copy(isLocationControlSheetVisible = false)
    }

    fun onLocationRecenterRequested() {
        _uiState.value = _uiState.value.copy(
            shouldCenterOnUserLocation = true,
            isLocationControlSheetVisible = false
        )
    }

    fun onUserLocationUpdated(location: UserLocation) {
        _uiState.value = _uiState.value.copy(userLocation = location)
    }

    fun onUserLocationCentered() {
        _uiState.value = _uiState.value.copy(shouldCenterOnUserLocation = false)
    }

    fun onLocationPermissionDenied(permanently: Boolean) {
        _uiState.value = _uiState.value.copy(
            isUserLocationEnabled = false,
            userLocation = null,
            shouldCenterOnUserLocation = false,
            isLocationControlSheetVisible = false,
            locationPermissionSheetVisible = false,
            locationStatusMessage = if (permanently) {
                "Permesso posizione non disponibile. Puoi continuare a usare la mappa e abilitarlo dalle impostazioni di Android."
            } else {
                "Posizione non attivata. Puoi comunque esplorare la mappa e selezionare un punto manualmente."
            }
        )
    }

    fun onLocationPermissionRevoked() {
        _uiState.value = _uiState.value.copy(
            isUserLocationEnabled = false,
            userLocation = null,
            shouldCenterOnUserLocation = false,
            isLocationControlSheetVisible = false,
            locationStatusMessage = "Permesso posizione non più disponibile."
        )
    }

    fun onLocationProviderUnavailable() {
        _uiState.value = _uiState.value.copy(
            locationStatusMessage = "Posizione non disponibile: controlla che i servizi di localizzazione siano attivi."
        )
    }

    private companion object {
        const val CachedMapDataMessage = "Dati mappa salvati"
        const val StatusMessageMillis = 8_000L
    }
}
