package com.dculus.stayfocused.kids

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.sync.DeviceTarget
import com.dculus.stayfocused.core.sync.DeviceUnlinkRepository
import com.dculus.stayfocused.core.sync.KidsHomeRepository
import com.dculus.stayfocused.core.sync.KidsRule
import com.dculus.stayfocused.core.sync.UnlinkResult
import com.dculus.stayfocused.core.usage.UsageStatsDataSource
import com.dculus.stayfocused.kids.monitoring.LinkReset
import com.dculus.stayfocused.kids.monitoring.LinkWatcher
import com.dculus.stayfocused.kids.monitoring.ParentLinkStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

/** Why "Unlink" did not go through. */
enum class UnlinkError { Network, Other }

/** How the Home screen ended. */
enum class HomeOutcome {
    /** The child unlinked: the screen shows "Unlinked. {parent} has been notified." and goes back to Link. */
    Unlinked,

    /** The parent unlinked this phone or deleted their account, or the link is missing: back to Link, quietly. */
    Reset,
}

/** [todayMins] is null until the first reading. [parentName] is null only when this phone is no longer linked. */
data class KidsHomeUiState(
    val parentName: String? = null,
    val todayMins: Int? = null,
    val rules: List<KidsRule> = emptyList(),
    val confirming: Boolean = false,
    val working: Boolean = false,
    val error: UnlinkError? = null,
    val outcome: HomeOutcome? = null,
)

/**
 * Kids: Home. Shows who manages this phone, today's total and the rules the parent set (read-only), and lets the child
 * unlink through `unlinkDevice`. Whenever the device document is gone (the parent unlinked, or deleted their
 * account), the link is cleared here and the screen goes back to Link.
 */
@HiltViewModel
class KidsHomeViewModel
    @Inject
    constructor(
        private val linkStore: ParentLinkStore,
        private val home: KidsHomeRepository,
        private val watcher: LinkWatcher,
        private val unlinkRepository: DeviceUnlinkRepository,
        private val usage: UsageStatsDataSource,
        private val linkReset: LinkReset,
        private val clock: Clock,
    ) : ViewModel() {
        internal companion object {
            const val TODAY_REFRESH_MILLIS = 60_000L
            const val RETRY_MILLIS = 30_000L
        }

        private val _state = MutableStateFlow(KidsHomeUiState(parentName = linkStore.parentName))
        val state: StateFlow<KidsHomeUiState> = _state.asStateFlow()

        private var finished = false

        /** The server confirmed the device is gone while an unlink call was still in flight. */
        private var goneWhileWorking = false

        init {
            val target = linkStore.parentUid?.let { parent -> linkStore.deviceId?.let { DeviceTarget(parent, it) } }
            if (target == null || linkStore.parentName == null) {
                // Not linked (anymore): nothing to show here.
                finish(HomeOutcome.Reset)
            } else {
                viewModelScope.launch {
                    watcher.untilGone(target)
                    if (_state.value.working) goneWhileWorking = true else finish(HomeOutcome.Reset)
                }
                viewModelScope.launch { retrying("rules") { watchRules(target) } }
                viewModelScope.launch { refreshToday() }
            }
        }

        private suspend fun watchRules(target: DeviceTarget) {
            home.observeRules(target).collect { rules -> _state.update { it.copy(rules = rules) } }
        }

        /** Runs [block] again after a pause whenever it fails (a lost connection); it ends when [block] returns. */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun retrying(
            what: String,
            block: suspend () -> Unit,
        ) {
            while (!finished) {
                try {
                    block()
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Watching %s failed; will retry", what)
                    delay(RETRY_MILLIS)
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun refreshToday() {
            while (!finished) {
                try {
                    val today = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
                    val mins = usage.dayUsage(today).totalMins
                    _state.update { it.copy(todayMins = mins) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Reading today's screen time failed")
                }
                delay(TODAY_REFRESH_MILLIS)
            }
        }

        /** "Unlink from {parent}": asks first. */
        fun askToUnlink() {
            if (_state.value.working) return
            _state.update { it.copy(confirming = true, error = null) }
        }

        fun dismissConfirm() {
            _state.update { it.copy(confirming = false) }
        }

        /** The child confirmed: the function deletes the device (and tells the parent); then this phone resets. */
        fun confirmUnlink() {
            val current = _state.value
            if (current.working || finished) return
            val parentUid = linkStore.parentUid
            val deviceId = linkStore.deviceId
            if (parentUid == null || deviceId == null) {
                finish(HomeOutcome.Unlinked)
                return
            }
            _state.update { it.copy(confirming = false, working = true, error = null) }
            viewModelScope.launch {
                when (val result = unlinkRepository.unlink(deviceId, parentUid)) {
                    UnlinkResult.Unlinked -> {
                        finish(HomeOutcome.Unlinked)
                    }

                    else -> {
                        if (goneWhileWorking) {
                            // The call failed on the way back, but the device is gone: the parent's side is done.
                            finish(HomeOutcome.Unlinked)
                        } else {
                            _state.update {
                                it.copy(
                                    working = false,
                                    error =
                                        if (result ==
                                            UnlinkResult.Network
                                        ) {
                                            UnlinkError.Network
                                        } else {
                                            UnlinkError.Other
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }

        /** Forgets the parent and stops monitoring, once. The name goes first: the service treats it as "linked". */
        private fun finish(outcome: HomeOutcome) {
            if (finished) return
            finished = true
            linkReset.reset()
            _state.update { it.copy(working = false, confirming = false, outcome = outcome) }
        }
    }
