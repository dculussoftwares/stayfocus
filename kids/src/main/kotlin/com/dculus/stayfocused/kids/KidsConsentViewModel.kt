package com.dculus.stayfocused.kids

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.sync.ApproveOutcome
import com.dculus.stayfocused.core.sync.LinkFailure
import com.dculus.stayfocused.core.sync.LinkHandshakeRepository
import com.dculus.stayfocused.kids.monitoring.MonitoringControl
import com.dculus.stayfocused.kids.monitoring.ParentLinkStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** What went wrong on the Consent screen. */
enum class ConsentError {
    /** No connection; trying again can work. */
    Network,

    /** The code ran out or was used up: the child shows a new QR code. */
    Expired,
    Other,
}

/** Where the child goes when the screen is done. */
enum class ConsentOutcome {
    /** Allowed: this phone is linked. */
    Linked,

    /** Declined, or the code is no good: back to a fresh QR code. */
    ShowNewCode,
}

/** [code] is null until the parent's claim has been seen. [parentName] is null when the parent has no name. */
data class KidsConsentUiState(
    val code: String? = null,
    val parentName: String? = null,
    val working: Boolean = false,
    val error: ConsentError? = null,
    val outcome: ConsentOutcome? = null,
)

/**
 * Kids: Consent. Shows the code the server made when the parent claimed the token, and answers `approveLink` with the
 * child's choice. A linked phone remembers the parent and device so the monitoring service can start.
 */
@HiltViewModel
class KidsConsentViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val handshake: LinkHandshakeRepository,
        private val linkStore: ParentLinkStore,
        private val launcher: MonitoringControl,
    ) : ViewModel() {
        private val token: String = savedState.get<String>("token").orEmpty()
        private val _state = MutableStateFlow(KidsConsentUiState())
        val state: StateFlow<KidsConsentUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch { loadConsent() }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun loadConsent() {
            try {
                val info = handshake.observeConsent(token).first()
                _state.update { it.copy(code = info.code, parentName = info.parentName.ifBlank { null }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The token cannot be read (gone, denied): nothing to consent to.
                Timber.w(e, "Watching the claimed link token failed")
                _state.update { it.copy(error = ConsentError.Expired) }
            }
        }

        /** "Allow and link". [fallbackName] is how the parent is called when their profile has no name. */
        fun allow(fallbackName: String) {
            if (_state.value.working || _state.value.code == null) return
            _state.update { it.copy(working = true, error = null) }
            viewModelScope.launch {
                when (val result = handshake.answer(token, approve = true)) {
                    is ApproveOutcome.Linked -> {
                        // Parent name last: the monitoring service treats a non-blank name as "linked".
                        linkStore.parentUid = result.parentUid
                        linkStore.deviceId = result.deviceId
                        linkStore.parentName = result.parentName.ifBlank { fallbackName }
                        launcher.sync()
                        _state.update { it.copy(working = false, outcome = ConsentOutcome.Linked) }
                    }

                    ApproveOutcome.Declined -> {
                        _state.update { it.copy(working = false, outcome = ConsentOutcome.ShowNewCode) }
                    }

                    is ApproveOutcome.Failed -> {
                        _state.update { it.copy(working = false, error = result.reason.toConsentError()) }
                    }
                }
            }
        }

        /** "Cancel": tells the parent no, then shows a new code. Leaves even when the call cannot get through. */
        fun cancel() {
            if (_state.value.working) return
            _state.update { it.copy(working = true, error = null) }
            viewModelScope.launch {
                handshake.answer(token, approve = false)
                _state.update { it.copy(working = false, outcome = ConsentOutcome.ShowNewCode) }
            }
        }

        /** The code is no good: leave for a new QR code. */
        fun showNewCode() {
            _state.update { it.copy(outcome = ConsentOutcome.ShowNewCode) }
        }
    }

internal fun LinkFailure.toConsentError(): ConsentError =
    when (this) {
        LinkFailure.Network -> ConsentError.Network
        LinkFailure.Expired, LinkFailure.Used, LinkFailure.Invalid, LinkFailure.NotClaimed -> ConsentError.Expired
        LinkFailure.Other -> ConsentError.Other
    }
