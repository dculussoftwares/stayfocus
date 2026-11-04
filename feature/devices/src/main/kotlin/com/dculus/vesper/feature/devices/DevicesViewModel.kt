package com.dculus.vesper.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.vesper.core.data.repository.LinkedDevicesRepository
import com.dculus.vesper.core.model.LinkedDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

internal sealed interface DevicesUiState {
    /** Nothing read yet: shown as an empty screen, not as "No linked phones". */
    data object Loading : DevicesUiState

    data class Loaded(
        val devices: List<LinkedDevice>,
    ) : DevicesUiState
}

@HiltViewModel
class DevicesViewModel
    @Inject
    constructor(
        devices: LinkedDevicesRepository,
    ) : ViewModel() {
        internal val state: StateFlow<DevicesUiState> =
            devices
                .observeAll()
                .map<List<LinkedDevice>, DevicesUiState> { DevicesUiState.Loaded(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DevicesUiState.Loading)

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
