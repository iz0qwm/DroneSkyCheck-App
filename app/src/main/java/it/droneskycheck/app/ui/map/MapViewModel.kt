package it.droneskycheck.app.ui.map

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MapViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun onZoneSelected(zone: DemoZone) {
        _uiState.value = _uiState.value.copy(selectedZone = zone)
    }

    fun onZoneSheetDismissed() {
        _uiState.value = _uiState.value.copy(selectedZone = null)
    }

    fun onCameraIdle(bounds: CameraBounds) {
        _uiState.value = _uiState.value.copy(cameraBounds = bounds)
    }
}
