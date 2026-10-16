package com.dculus.stayfocused.feature.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.sync.ClaimOutcome
import com.dculus.stayfocused.core.sync.ClaimedLink
import com.dculus.stayfocused.core.sync.LinkAnswer
import com.dculus.stayfocused.core.sync.LinkFailure
import com.dculus.stayfocused.core.sync.LinkHandshakeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Clock
import javax.inject.Inject

/** Where the parent's Confirm link screen is. */
internal sealed interface LinkConfirmState {
    /** Claiming the scanned token. */
    data object Claiming : LinkConfirmState

    /** The code is up on both phones; waiting for the child to tap Allow. */
    data class Waiting(
        val code: String,
    ) : LinkConfirmState

    /** The child allowed it: [deviceId] is the new phone under this account. */
    data class Linked(
        val deviceId: String,
    ) : LinkConfirmState

    /** The child tapped Cancel. */
    data object Declined : LinkConfirmState

    /** The claim or the wait did not work out; [LinkFailure.Expired] also covers a child who never answered. */
    data class Failed(
        val reason: LinkFailure,
    ) : LinkConfirmState
}

/**
 * Claims the scanned token (`claimLinkToken`), shows the code, lets the parent name the phone, and waits for the
 * child's answer on the token document.
 */
@HiltViewModel
class LinkConfirmViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val handshake: LinkHandshakeRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val token: String = savedState.get<String>("token").orEmpty()
        private val mutableState = MutableStateFlow<LinkConfirmState>(LinkConfirmState.Claiming)
        internal val state: StateFlow<LinkConfirmState> = mutableState.asStateFlow()

        private val _name = MutableStateFlow("")

        /** The editable device name; starts as the child's phone model. */
        val name: StateFlow<String> = _name.asStateFlow()

        private val edits = MutableSharedFlow<String>(extraBufferCapacity = EDIT_BUFFER)
        private var session: Job? = null
        private var left = false

        /** True while this phone holds a claim that the child has not answered yet. */
        private var owned = false
        private var savedName: String? = null

        init {
            start()
        }

        fun retry() = start()

        fun onNameChange(value: String) {
            val limited = value.take(MAX_NAME_LENGTH)
            _name.value = limited
            edits.tryEmit(limited)
        }

        /**
         * Withdraws the claim when the parent leaves while waiting (codes do not match, or changed their mind), so the
         * child's later "Allow" is refused. Runs to the end even though the screen is going away.
         */
        fun cancel() {
            when (mutableState.value) {
                // The claim is still on its way and may commit after the screen is gone: withdraw it once it has.
                LinkConfirmState.Claiming -> left = true

                // Also after a watch failure or a timeout: the server still holds a claim this phone made.
                is LinkConfirmState.Waiting, is LinkConfirmState.Failed -> if (owned) withdraw()

                else -> Unit
            }
        }

        private fun withdraw() {
            viewModelScope.launch { withContext(NonCancellable) { handshake.cancel(token) } }
        }

        private fun start() {
            session?.cancel()
            mutableState.value = LinkConfirmState.Claiming
            session =
                viewModelScope.launch {
                    when (val outcome = handshake.claim(token, deviceName = null)) {
                        is ClaimOutcome.Claimed -> {
                            val link = outcome.link
                            when {
                                link.alreadyLinked -> {
                                    mutableState.value = LinkConfirmState.Linked(link.deviceId)
                                }

                                left -> {
                                    // The parent left before the claim came back: nobody is looking at this code.
                                    withdraw()
                                }

                                else -> {
                                    owned = true
                                    _name.value = link.model.take(MAX_NAME_LENGTH)
                                    savedName = _name.value
                                    mutableState.value = LinkConfirmState.Waiting(link.code)
                                    val ending = watch(link)
                                    // Linked or declined: the claim is over; a failed watch leaves it open.
                                    if (ending is LinkConfirmState.Linked ||
                                        ending == LinkConfirmState.Declined
                                    ) {
                                        owned = false
                                    }
                                    mutableState.value = ending
                                }
                            }
                        }

                        is ClaimOutcome.Failed -> {
                            mutableState.value = LinkConfirmState.Failed(outcome.reason)
                        }
                    }
                }
        }

        /** Runs until the child answers, the token runs out or the token can no longer be watched. */
        @OptIn(FlowPreview::class)
        private suspend fun watch(link: ClaimedLink): LinkConfirmState =
            coroutineScope {
                // The name is saved a moment after the last keystroke; the code stays the same.
                val rename =
                    launch {
                        edits.debounce(RENAME_DEBOUNCE_MS).collect { value ->
                            val trimmed = value.trim()
                            // One call per distinct name: every call uses a rate-limit slot and an App Check token.
                            if (trimmed.isNotEmpty() && trimmed != savedName) {
                                if (handshake.claim(token, trimmed) is ClaimOutcome.Claimed) savedName = trimmed
                            }
                        }
                    }
                val ending: Flow<LinkConfirmState?> =
                    merge(
                        handshake
                            .observeAnswer(token)
                            .map { answer ->
                                when (answer) {
                                    LinkAnswer.Linked -> LinkConfirmState.Linked(link.deviceId)
                                    LinkAnswer.Declined -> LinkConfirmState.Declined
                                    LinkAnswer.Pending -> null
                                }
                            }.catch { e ->
                                if (e is CancellationException) throw e
                                Timber.w(e, "Watching the claimed link token failed")
                                emit(LinkConfirmState.Failed(LinkFailure.Other))
                            },
                        flow {
                            // A little past the deadline, so an approval that committed just in time still wins.
                            delay((link.expiresAtMillis - clock.millis()).coerceAtLeast(0) + EXPIRY_GRACE_MS)
                            emit(LinkConfirmState.Failed(LinkFailure.Expired))
                        },
                    )
                val result = ending.first { it != null } ?: LinkConfirmState.Failed(LinkFailure.Other)
                rename.cancel()
                result
            }

        private companion object {
            const val MAX_NAME_LENGTH = 60
            const val RENAME_DEBOUNCE_MS = 800L
            const val EDIT_BUFFER = 8
            const val EXPIRY_GRACE_MS = 3_000L
        }
    }
