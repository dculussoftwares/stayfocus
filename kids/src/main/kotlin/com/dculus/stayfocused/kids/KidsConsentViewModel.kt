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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.time.Clock
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
        private val clock: Clock,
    ) : ViewModel() {
        private companion object {
            /** The parent's claim reaches this phone within moments; past this the connection is the problem. */
            const val CLAIM_WAIT_MILLIS = 15_000L

            /** This phone's clock may run a little fast; the server alone decides whether an answer is too late. */
            const val EXPIRY_GRACE_MILLIS = 10_000L
        }

        private val token: String = savedState.get<String>("token").orEmpty()
        private val _state = MutableStateFlow(KidsConsentUiState())
        val state: StateFlow<KidsConsentUiState> = _state.asStateFlow()

        private var loading: Job? = null

        /** Set when the deadline passed; a failed answer after that is "expired", not a connection problem. */
        private var pastDeadline = false

        init {
            retryConsent()
        }

        /** (Re)starts watching this token for the parent's claim; the token is kept while it is still valid. */
        fun retryConsent() {
            loading?.cancel()
            _state.update { it.copy(error = null) }
            loading = viewModelScope.launch { loadConsent() }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun loadConsent() {
            try {
                val info = withTimeoutOrNull(CLAIM_WAIT_MILLIS) { handshake.observeConsent(token).first() }
                if (info == null) {
                    _state.update { it.copy(error = ConsentError.Network) }
                    return
                }
                _state.update { it.copy(code = info.code, parentName = info.parentName.ifBlank { null }) }
                expireAt(info.expiresAtMillis)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSuchElementException) {
                // No claim to show (no Firebase in this build): nothing to consent to.
                Timber.w(e, "No claimed link token to show")
                _state.update { it.copy(error = ConsentError.Expired) }
            } catch (e: Exception) {
                // The listener died (connection lost, denied): the claim may still be valid, so a new code is offered.
                Timber.w(e, "Watching the claimed link token failed")
                _state.update { it.copy(error = ConsentError.Network) }
            }
        }

        /**
         * After the token's deadline the server refuses an answer: say so instead of leaving "Allow" on screen. Never
         * while an answer is on its way (its own result says what happened) and not before a small grace.
         */
        private suspend fun expireAt(expiresAtMillis: Long) {
            if (expiresAtMillis <= 0) return
            delay((expiresAtMillis - clock.millis()).coerceAtLeast(0) + EXPIRY_GRACE_MILLIS)
            pastDeadline = true
            _state.update { if (it.working || it.outcome != null) it else it.copy(error = ConsentError.Expired) }
        }

        /** "Allow and link". [fallbackName] is how the parent is called when their profile has no name. */
        fun allow(fallbackName: String) {
            if (_state.value.working || _state.value.code == null) return
            _state.update { it.copy(working = true, error = null) }
            viewModelScope.launch { approve(fallbackName) }
        }

        private suspend fun approve(fallbackName: String) {
            run {
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
                        _state.update {
                            it.copy(
                                working = false,
                                error = result.reason.toConsentError(),
                            )
                        }
                    }
                }
            }
        }

        /**
         * "Cancel" (and system Back): tells the parent no, then shows a new code. When the decline cannot get through
         * the screen stays, so the child is never left believing a still-open claim was cancelled.
         */
        fun cancel(fallbackName: String) {
            if (_state.value.working) return
            if (_state.value.code == null) {
                showNewCode()
                return
            }
            _state.update { it.copy(working = true, error = null) }
            viewModelScope.launch {
                val result = handshake.answer(token, approve = false)
                if (result is ApproveOutcome.Failed && result.reason == LinkFailure.Used) {
                    // "Used" means an earlier Allow did commit and only its reply was lost: finish that link instead.
                    approve(fallbackName)
                    return@launch
                }
                val error = (result as? ApproveOutcome.Failed)?.reason?.toConsentError()?.afterDeadline()
                _state.update {
                    when (error) {
                        null, ConsentError.Expired -> it.copy(working = false, outcome = ConsentOutcome.ShowNewCode)
                        else -> it.copy(working = false, error = error)
                    }
                }
            }
        }

        private fun ConsentError.afterDeadline(): ConsentError =
            if (pastDeadline && this != ConsentError.Expired) ConsentError.Expired else this

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
