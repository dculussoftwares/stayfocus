package com.dculus.stayfocused.kids.monitoring

import com.dculus.stayfocused.core.sync.ChildStatus
import com.dculus.stayfocused.core.sync.ChildStatusRepository
import com.dculus.stayfocused.core.sync.DeviceAuth
import com.dculus.stayfocused.core.sync.DeviceTarget
import com.dculus.stayfocused.core.sync.UsageAppEntry
import com.dculus.stayfocused.core.sync.UsageSummary
import com.dculus.stayfocused.core.usage.DayUsageStats
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import com.dculus.stayfocused.core.usage.UsageAccess
import com.dculus.stayfocused.core.usage.UsageStatsDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** The live phone state the heartbeat reports: battery, charging and the app in front (label). */
interface StatusSource {
    /** Emits whenever battery, charging or the foreground app changes. `online` is always true here. */
    val snapshots: Flow<ChildStatus>
}

/** Cadence of the child's reports. */
internal object ReportCadence {
    const val HEARTBEAT_MILLIS = 90_000L
    const val CHANGE_DEBOUNCE_MILLIS = 5_000L
    const val USAGE_MILLIS = 15 * 60_000L
    const val RETRY_MILLIS = 60_000L
    const val GOODBYE_TIMEOUT_MILLIS = 3_000L
}

/**
 * The writes of the device doc: every change once it has been quiet for [debounceMillis], plus a repeat of the last
 * state when nothing was written for [heartbeatMillis], so a quiet phone costs one write per 90 s and the parent can
 * tell it is still alive. The heartbeat runs apart from the debounce, so constant app switching cannot starve it.
 */
@OptIn(FlowPreview::class)
internal fun statusWrites(
    snapshots: Flow<ChildStatus>,
    debounceMillis: Long = ReportCadence.CHANGE_DEBOUNCE_MILLIS,
    heartbeatMillis: Long = ReportCadence.HEARTBEAT_MILLIS,
): Flow<ChildStatus> =
    channelFlow {
        val latest = MutableStateFlow<ChildStatus?>(null)
        val written = Channel<Unit>(Channel.CONFLATED)
        launch { snapshots.collect { latest.value = it } }
        launch {
            latest.filterNotNull().distinctUntilChanged().debounce(debounceMillis).collect {
                send(it)
                written.trySend(Unit)
            }
        }
        launch {
            latest.filterNotNull().first()
            while (true) {
                // A write restarts the wait; silence for a whole heartbeat repeats the current state.
                if (withTimeoutOrNull(heartbeatMillis) { written.receive() } == null) {
                    latest.value?.let { send(it) }
                }
            }
        }
    }.conflate()

internal fun DayUsageStats.toSummary(labelOf: (String) -> String): UsageSummary =
    UsageSummary(
        date = date.toString(),
        totalMins = totalMins,
        apps = apps.map { UsageAppEntry(it.pkg, labelOf(it.pkg), it.mins, it.opens) },
        hourly = hourlyMins,
    )

/**
 * Reports this phone to the linked parent while the monitoring service runs: status heartbeat, usage summary every
 * 15 min and the launchable apps list. Failures are logged and retried; they never stop the service.
 */
class ChildReporter
    @Inject
    internal constructor(
        private val repository: ChildStatusRepository,
        private val deviceAuth: DeviceAuth,
        private val source: StatusSource,
        private val usage: UsageStatsDataSource,
        private val usageAccess: UsageAccess,
        private val installedApps: InstalledAppsRepository,
        private val clock: Clock,
    ) {
        /** Runs the three reporters for [target] until cancelled. */
        suspend fun run(target: DeviceTarget) =
            coroutineScope {
                launch { reportStatus(target) }
                launch { reportUsage(target) }
                launch { reportApps(target) }
            }

        internal suspend fun reportStatus(target: DeviceTarget) {
            awaitSignIn()
            var last: ChildStatus? = null
            try {
                statusWrites(source.snapshots).collect { status ->
                    last = status
                    attempt("status") { repository.writeStatus(target, status) }
                }
            } finally {
                // Best effort: tell the parent this phone stopped reporting (service stopped or unlinked).
                last?.let { status ->
                    withContext(NonCancellable) {
                        withTimeoutOrNull(ReportCadence.GOODBYE_TIMEOUT_MILLIS) {
                            attempt("status") { repository.writeStatus(target, status.copy(online = false)) }
                        }
                    }
                }
            }
        }

        internal suspend fun reportUsage(target: DeviceTarget) {
            awaitSignIn()
            val uploaded = mutableMapOf<LocalDate, UsageSummary>()
            // The day for which "yesterday" was last closed with its final numbers. Starts empty, so a restart (or a
            // failed upload) closes yesterday again on the next tick instead of leaving it stale.
            var yesterdayClosedFor: LocalDate? = null
            while (true) {
                val today = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
                val labels = installedApps.observeLaunchableApps().first().associate { it.pkg to it.label }
                if (yesterdayClosedFor != today && upload(target, today.minusDays(1), labels, uploaded)) {
                    yesterdayClosedFor = today
                }
                upload(target, today, labels, uploaded)
                delay(ReportCadence.USAGE_MILLIS)
            }
        }

        /** True when [date] is now up to date remotely (also when there is nothing to say about it). */
        private suspend fun upload(
            target: DeviceTarget,
            date: LocalDate,
            labels: Map<String, String>,
            uploaded: MutableMap<LocalDate, UsageSummary>,
        ): Boolean {
            // An empty result without usage access is "unknown", not an empty day: stay pending until it is granted.
            if (!usageAccess.isGranted()) return false
            val stats = usage.dayUsage(date)
            val summary = stats.toSummary { labels[it] ?: it }
            // No usage access yet, a day without use, or nothing new: nothing to write.
            return stats.apps.isEmpty() ||
                uploaded[date] == summary ||
                attempt("usage") { repository.writeUsage(target, summary) }.also { ok ->
                    if (ok) {
                        uploaded[date] =
                            summary
                    }
                }
        }

        @OptIn(FlowPreview::class)
        internal suspend fun reportApps(target: DeviceTarget) {
            awaitSignIn()
            installedApps
                .observeLaunchableApps()
                .map { apps -> apps.associate { it.pkg to it.label } }
                .distinctUntilChanged()
                // Installing an app fires several package broadcasts in a row.
                .debounce(ReportCadence.CHANGE_DEBOUNCE_MILLIS)
                .collect { apps ->
                    while (!attempt("apps") { repository.syncApps(target, apps) }) delay(ReportCadence.RETRY_MILLIS)
                }
        }

        /** Waits for the anonymous identity; sign-in fails while offline, so keep trying instead of giving up. */
        private suspend fun awaitSignIn() {
            while (deviceAuth.ensureSignedIn() == null) {
                Timber.w("Not reporting yet: no anonymous sign-in")
                delay(ReportCadence.RETRY_MILLIS)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun attempt(
            what: String,
            block: suspend () -> Unit,
        ): Boolean =
            try {
                block()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Reporting %s failed; will retry", what)
                false
            }
    }
