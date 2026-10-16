package com.dculus.stayfocused.kids.monitoring

import com.dculus.stayfocused.core.sync.ChildStatus
import com.dculus.stayfocused.core.sync.ChildStatusRepository
import com.dculus.stayfocused.core.sync.DeviceAuth
import com.dculus.stayfocused.core.sync.DeviceTarget
import com.dculus.stayfocused.core.sync.UsageAppEntry
import com.dculus.stayfocused.core.sync.UsageSummary
import com.dculus.stayfocused.core.usage.DayUsageStats
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import com.dculus.stayfocused.core.usage.UsageStatsDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
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
 * The writes of the device doc: every change (debounced), plus a repeat of the last state when nothing was written for
 * a heartbeat, so a quiet phone costs one write per 90 s and the parent can tell it is still alive.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal fun statusWrites(
    snapshots: Flow<ChildStatus>,
    debounceMillis: Long = ReportCadence.CHANGE_DEBOUNCE_MILLIS,
    heartbeatMillis: Long = ReportCadence.HEARTBEAT_MILLIS,
): Flow<ChildStatus> =
    snapshots
        .distinctUntilChanged()
        .debounce(debounceMillis)
        .transformLatest { status ->
            emit(status)
            while (true) {
                delay(heartbeatMillis)
                emit(status)
            }
        }

internal fun DayUsageStats.toSummary(labelOf: (String) -> String): UsageSummary =
    UsageSummary(
        date = date.toString(),
        totalMins = totalMins,
        apps = apps.map { UsageAppEntry(it.pkg, labelOf(it.pkg), it.mins, it.opens) },
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
            if (!signedIn()) return
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
            if (!signedIn()) return
            var uploaded: UsageSummary? = null
            var lastDate: LocalDate? = null
            while (true) {
                val today = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
                // After midnight, close the previous day with its final numbers first.
                val dates = listOfNotNull(lastDate?.takeIf { it != today }, today)
                for (date in dates) {
                    val stats = usage.dayUsage(date)
                    val labels = installedApps.observeLaunchableApps().first().associate { it.pkg to it.label }
                    val summary = stats.toSummary { labels[it] ?: it }
                    // Nothing to say (no usage access yet, or a day without use) or nothing new: no write.
                    val worthWriting = stats.apps.isNotEmpty() && summary != uploaded
                    if (worthWriting && attempt("usage") { repository.writeUsage(target, summary) }) {
                        uploaded = summary
                    }
                }
                lastDate = today
                delay(ReportCadence.USAGE_MILLIS)
            }
        }

        @OptIn(FlowPreview::class)
        internal suspend fun reportApps(target: DeviceTarget) {
            if (!signedIn()) return
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

        private suspend fun signedIn(): Boolean =
            (deviceAuth.ensureSignedIn() != null).also { if (!it) Timber.w("Not reporting: no anonymous sign-in") }

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
