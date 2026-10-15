package com.dculus.stayfocused.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.sync.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Whether the parent can start linking: a phone is linked to the signed-in account. */
internal enum class LinkAddState {
    /** The persisted session has not been read yet. */
    Loading,
    SignedOut,
    SignedIn,
}

@HiltViewModel
class LinkAddViewModel
    @Inject
    constructor(
        auth: AuthRepository,
    ) : ViewModel() {
        internal val state: StateFlow<LinkAddState> =
            auth.currentUser
                .map { if (it == null) LinkAddState.SignedOut else LinkAddState.SignedIn }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LinkAddState.Loading)

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
