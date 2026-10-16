package com.dculus.stayfocused.kids

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.sync.DeviceAuth
import com.dculus.stayfocused.core.sync.LinkSession
import com.dculus.stayfocused.core.sync.LinkTokenRepository
import com.dculus.stayfocused.core.sync.LinkTokenStatus
import com.dculus.stayfocused.core.sync.LinkTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** What the Link (QR) screen shows. */
sealed interface KidsLinkState {
    /** Publishing the first token. */
    data object Loading : KidsLinkState

    /** The QR holds [uri]; [remaining] is the countdown text (`4:52`). */
    data class Waiting(
        val uri: String,
        val remaining: String,
    ) : KidsLinkState

    /** No token could be published (offline); the screen offers a retry. */
    data object Failed : KidsLinkState

    /** The parent scanned the code: move on to Consent. */
    data object Claimed : KidsLinkState
}

private sealed interface Outcome {
    data class Tick(
        val millisLeft: Long,
    ) : Outcome

    data object Claimed : Outcome
}

/**
 * Keeps a fresh one-time token on screen: publishes it, counts down, replaces it when it expires and
 * reports [KidsLinkState.Claimed] as soon as the token doc is claimed.
 */
@HiltViewModel
class KidsLinkViewModel
    @Inject
    constructor(
        private val tokens: LinkTokenRepository,
        private val deviceAuth: DeviceAuth,
        private val clock: Clock,
    ) : ViewModel() {
        private val _state = MutableStateFlow<KidsLinkState>(KidsLinkState.Loading)
        val state: StateFlow<KidsLinkState> = _state.asStateFlow()

        private val retries = Channel<Unit>(Channel.CONFLATED)

        init {
            viewModelScope.launch {
                while (!runCycle()) {
                    // A failed cycle waits for "Try again" instead of hammering Firestore while offline.
                    _state.value = KidsLinkState.Failed
                    retries.receive()
                    _state.value = KidsLinkState.Loading
                }
            }
        }

        fun retry() {
            retries.trySend(Unit)
        }

        /** Shows tokens until one is claimed (true) or none could be published (false). */
        private suspend fun runCycle(): Boolean {
            while (true) {
                val session = publish() ?: return false
                if (watch(session)) return true
                // Expired unclaimed: loop and publish a new one.
            }
        }

        private suspend fun publish(): LinkSession? {
            val uid = deviceAuth.ensureSignedIn() ?: return null
            return tokens.create(uid)
        }

        /** True when [session] was claimed, false when it ran out. */
        private suspend fun watch(session: LinkSession): Boolean {
            val ticks: Flow<Outcome> =
                flow {
                    while (true) {
                        val left = session.expiresAtMillis - clock.millis()
                        emit(Outcome.Tick(left))
                        if (left <= 0) return@flow
                        delay(TICK_MILLIS)
                    }
                }
            val claims: Flow<Outcome> =
                tokens
                    .observe(session.token)
                    .filter { it == LinkTokenStatus.CLAIMED }
                    .map { Outcome.Claimed }
            val last =
                merge(ticks, claims)
                    .onEach { outcome ->
                        if (outcome is Outcome.Tick) {
                            _state.value =
                                KidsLinkState.Waiting(
                                    LinkTokens.uri(session.token),
                                    LinkTokens.formatRemaining(outcome.millisLeft),
                                )
                        }
                    }.first { (it is Outcome.Tick && it.millisLeft <= 0) || it is Outcome.Claimed }
            val claimed = last is Outcome.Claimed
            if (claimed) _state.value = KidsLinkState.Claimed
            return claimed
        }

        private companion object {
            const val TICK_MILLIS = 1000L
        }
    }
