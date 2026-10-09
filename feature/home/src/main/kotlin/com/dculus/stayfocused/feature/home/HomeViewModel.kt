package com.dculus.stayfocused.feature.home

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.data.repository.AccountProfileRepository
import com.dculus.stayfocused.core.data.repository.BlockRepository
import com.dculus.stayfocused.core.data.repository.BreakRepository
import com.dculus.stayfocused.core.data.repository.LinkedDevicesRepository
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.fallbackAppLabel
import com.dculus.stayfocused.core.model.ledLevels
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import com.dculus.stayfocused.core.usage.UsageAccess
import com.dculus.stayfocused.core.usage.UsageRepository
import com.dculus.stayfocused.core.usage.totalOpens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject

/** One-off results of a Home action. */
internal sealed interface HomeEvent {
    data class BreakStarted(
        val mins: Int,
    ) : HomeEvent

    /** Starting or ending the break could not be stored. */
    data object BreakFailed : HomeEvent

    /** A block's switch could not be stored. */
    data object BlockToggleFailed : HomeEvent
}

@Suppress("LongParameterList", "TooManyFunctions") // One repository per card on the dashboard.
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel
    @Inject
    constructor(
        private val breaks: BreakRepository,
        private val clock: Clock,
        private val usage: UsageRepository,
        private val usageAccess: UsageAccess,
        private val blocks: BlockRepository,
        installedApps: InstalledAppsRepository,
        linkedDevices: LinkedDevicesRepository,
        profile: AccountProfileRepository,
        settings: SettingsRepository,
    ) : ViewModel() {
        private val accessGranted = MutableStateFlow(usageAccess.isGranted())

        /** Emits now, then at the start of every minute (the header clock and the current LED column). */
        private val minuteTicks: Flow<Instant> =
            flow {
                while (true) {
                    val now = clock.instant()
                    emit(now)
                    delay(MILLIS_PER_MINUTE - now.toEpochMilli() % MILLIS_PER_MINUTE)
                }
            }

        private fun zoned(now: Instant): ZonedDateTime = ZonedDateTime.ofInstant(now, clock.zone)

        private val header: Flow<HeaderUi> =
            combine(minuteTicks, profile.observeFirstName()) { now, name -> headerFor(zoned(now), name) }

        private val gauge: Flow<GaugeUi> =
            accessGranted.flatMapLatest { granted ->
                if (!granted) {
                    flowOf(GaugeUi.NoAccess)
                } else {
                    combine(
                        usage.today(),
                        usage.averages(),
                        usage.blockedToday(),
                        minuteTicks,
                    ) { today, averages, blocked, now ->
                        val hourlyMins = today.hourlyMillis.map { (it / MILLIS_PER_MINUTE).toInt() }
                        GaugeUi.Ready(
                            totalMins = today.totalMins,
                            avgMins = averages.takeIf { it.hasData }?.avgTotalMins,
                            levels = ledLevels(hourlyMins, HOME_LED_ROWS, zoned(now).hour),
                            launches = today.totalOpens,
                            unlocks = today.unlocks,
                            blocked = blocked,
                        )
                    }
                }
            }

        private val blockRows: Flow<List<HomeBlockUi>> =
            combine(
                blocks.observeByTarget(BlockTarget.ThisPhone),
                installedApps.observeLaunchableApps(),
            ) { list, installed ->
                val labels = installed.associate { it.pkg to it.label }
                list.map { b -> HomeBlockUi(b, b.apps.map { labels[it] ?: fallbackAppLabel(it) }) }
            }

        /** The dashboard: header, gauge card, this phone's blocks and the linked phones. */
        internal val state: StateFlow<HomeUiState> =
            combine(
                header,
                gauge,
                blockRows,
                linkedDevices.observeAll(),
                settings.settings.map { it.aiEnabled },
                ::HomeUiState,
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialState())

        private fun initialState() =
            HomeUiState(
                header = headerFor(zoned(clock.instant()), null),
                gauge = if (accessGranted.value) GaugeUi.Ready(0, null, emptyList(), 0, 0, 0) else GaugeUi.NoAccess,
                blocks = emptyList(),
                devices = emptyList(),
                aiAvailable = false,
            )

        /** Re-reads the usage-access permission, e.g. when the person returns from the system settings. */
        fun refreshUsageAccess() {
            val granted = usageAccess.isGranted()
            val newlyGranted = granted && !accessGranted.value
            accessGranted.value = granted
            if (granted) usage.requestRefresh()
            if (newlyGranted) {
                // Days skipped while access was off (the startup backfill needs it) fill the average. Only on the
                // denied -> granted change, so ordinary resumes never rescan the history.
                viewModelScope.launch { backfillHistory() }
            }
        }

        private suspend fun backfillHistory() {
            try {
                usage.backfill()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Timber.w(e, "Usage backfill failed")
            }
        }

        /** The system "Usage access" settings screen. */
        fun usageSettingsIntent(): Intent = usageAccess.settingsIntent()

        fun setBlockEnabled(
            id: String,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                runStored(null, HomeEvent.BlockToggleFailed) { blocks.setEnabled(id, enabled) }
            }
        }

        /**
         * The break card. The break itself lives in DataStore, so after process death the countdown simply
         * resumes from the stored end time.
         */
        internal val breakUi: StateFlow<BreakUi> =
            breaks
                .observe()
                .flatMapLatest { session ->
                    if (session == null) {
                        flowOf(BreakUi.Idle)
                    } else {
                        val total = Duration.between(session.startedAt, session.endsAt).toMillis()
                        countdown(session.endsAt.toEpochMilli(), total)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BreakUi.Idle)

        private fun countdown(
            endsAtMillis: Long,
            totalMs: Long,
        ): Flow<BreakUi> =
            flow {
                while (true) {
                    val remaining = endsAtMillis - clock.millis()
                    if (remaining <= 0L) {
                        emit(BreakUi.Idle)
                        return@flow
                    }
                    emit(BreakUi.Active(remaining, totalMs))
                    delay(millisToNextTick(remaining))
                }
            }

        private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)

        /** One-off results for the UI (toasts). */
        internal val events: Flow<HomeEvent> = eventChannel.receiveAsFlow()

        /** Starts a break and reports [HomeEvent.BreakStarted] only once it is stored. */
        fun startBreak(mins: Int) {
            viewModelScope.launch {
                runStored(HomeEvent.BreakStarted(mins), HomeEvent.BreakFailed) { breaks.start(mins) }
            }
        }

        fun endBreak() {
            viewModelScope.launch { runStored(null, HomeEvent.BreakFailed) { breaks.end() } }
        }

        private suspend fun runStored(
            success: HomeEvent?,
            failure: HomeEvent,
            write: suspend () -> Unit,
        ) {
            try {
                write()
                success?.let { eventChannel.send(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // A failed storage write must not crash the app; tell the user instead.
                Timber.w(e, "Home update failed")
                eventChannel.send(failure)
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val MILLIS_PER_MINUTE = 60_000L
        }
    }
