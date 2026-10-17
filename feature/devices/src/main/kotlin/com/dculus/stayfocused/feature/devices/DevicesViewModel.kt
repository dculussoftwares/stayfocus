package com.dculus.stayfocused.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.data.repository.LinkedDevicesRepository
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.UnlockRequestStatus
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

/** Undismissed alerts plus pending unlock requests: the number on the card. */
internal fun LinkedDevice.badgeCount(): Int =
    alerts.count { !it.dismissed } + requests.count { it.status == UnlockRequestStatus.PENDING }

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
