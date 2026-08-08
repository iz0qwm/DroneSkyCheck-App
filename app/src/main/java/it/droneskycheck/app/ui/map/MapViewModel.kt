package it.droneskycheck.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.droneskycheck.app.data.ZoneCheckV3Repository
import kotlinx.coroutines.Dispatchers
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
        _uiState.value = _uiState.value.copy(
            selectedZone = selection.zone,
            selectedPoint = selection.point,
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
                    verdictError = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isVerdictLoading = false,
                    verdictError = error.message ?: "Errore zoneCheckV3"
                )
            }
        }
    }

    fun onZoneSheetDismissed() {
        _uiState.value = _uiState.value.copy(
            selectedZone = null,
            selectedPoint = null,
            isVerdictLoading = false,
            verdict = null,
            verdictError = null
        )
    }

    fun onCameraIdle(bounds: CameraBounds) {
        _uiState.value = _uiState.value.copy(cameraBounds = bounds)
    }
}
