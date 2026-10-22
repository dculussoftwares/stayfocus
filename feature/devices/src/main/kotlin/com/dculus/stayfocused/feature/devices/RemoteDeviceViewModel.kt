package com.dculus.stayfocused.feature.devices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.data.repository.LinkedDevicesRepository
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.TamperAlert
import com.dculus.stayfocused.core.model.TamperKind
import com.dculus.stayfocused.core.model.UnlockRequest
import com.dculus.stayfocused.core.model.hasDerivedOfflineAlert
import com.dculus.stayfocused.core.sync.AuthRepository
import com.dculus.stayfocused.core.sync.CommandSender
import com.dculus.stayfocused.core.sync.DeliveryState
import com.dculus.stayfocused.core.sync.DeviceApp
import com.dculus.stayfocused.core.sync.DeviceTarget
import com.dculus.stayfocused.core.sync.DeviceUnlinkRepository
import com.dculus.stayfocused.core.sync.RemoteBlock
import com.dculus.stayfocused.core.sync.RemoteCommand
import com.dculus.stayfocused.core.sync.RemoteDeviceRepository
import com.dculus.stayfocused.core.sync.RequestDecisionRepository
import com.dculus.stayfocused.core.sync.UnlinkResult
import com.dculus.stayfocused.core.sync.UsageSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** One app on the child's phone: today's use and whether the parent has it locked. */
internal data class RemoteAppRow(
    val pkg: String,
    val label: String,
    val mins: Int,
    /** 0..1 against the busiest app today; the bar. */
    val fraction: Float,
    val locked: Boolean,
)

/** A tamper alert as the red card shows it: [time] is the local `HH:mm` it was raised. */
internal data class RemoteAlertCard(
    val id: String,
    val kind: TamperKind,
    val permission: String?,
    val time: String,
    /** Whole minutes since the alert was raised, 0 for "Just now". */
    val agoMins: Long = 0,
)

/** An unlock request as the amber card shows it. */
internal data class RemoteRequestCard(
    val id: String,
    val app: String,
    val minutes: Int,
    /** Whole minutes since the child asked, 0 for "Just now". */
    val agoMins: Long,
)

internal sealed interface RemoteUiState {
    data object Loading : RemoteUiState

    /** The phone is not (or no longer) linked to this account. */
    data object NotFound : RemoteUiState

    data class Ready(
        val device: LinkedDevice,
        val alerts: List<RemoteAlertCard>,
        val requests: List<RemoteRequestCard>,
        val blocks: List<RemoteBlock>,
        val apps: List<RemoteAppRow>,
        /** Minutes of screen time today, or null until the phone has reported any. */
        val todayMins: Int?,
        val unlinking: Boolean = false,
    ) : RemoteUiState
}

/** A one-off message for the toast; the route turns it into text. */
internal sealed interface RemoteEvent {
    data class AppLocked(
        val app: String,
        val device: String,
    ) : RemoteEvent

    data class AppUnlocked(
        val app: String,
        val device: String,
    ) : RemoteEvent

    data class FocusStarted(
        val device: String,
    ) : RemoteEvent

    data class FocusStopped(
        val device: String,
    ) : RemoteEvent

    data class AllLocked(
        val device: String,
    ) : RemoteEvent

    data object AllUnlocked : RemoteEvent

    data class Reminded(
        val device: String,
    ) : RemoteEvent

    /** The command is stored and will apply when the phone is back. */
    data class Offline(
        val device: String,
    ) : RemoteEvent

    /** A command or write was refused, or expired before the phone answered. */
    data class Failed(
        val device: String,
    ) : RemoteEvent

    /** The parent gave [device] [mins] more minutes. */
    data class RequestApproved(
        val device: String,
        val mins: Int,
    ) : RemoteEvent

    data object RequestDenied : RemoteEvent

    data class Unlinked(
        val device: String,
    ) : RemoteEvent

    data class UnlinkFailed(
        val device: String,
    ) : RemoteEvent
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private const val STOP_TIMEOUT_MS = 5_000L
private const val DATE_TICK_MS = 60_000L
private const val RETRY_BASE_MS = 1_000L
private const val RETRY_MAX_MS = 30_000L
private const val MAX_BACKOFF_SHIFT = 5
private const val DEFAULT_PERMISSION = "accessibility"

/** The id of the offline card that is derived from `lastSeen` and has no document behind it. */
internal const val DERIVED_OFFLINE_ID = "derived-offline"
private const val MINS_PER_HOUR = 60

/** True when [usageDate] (`yyyy-MM-dd`, the child's local day) is [today] or the day before or after it. */
internal fun isAroundToday(
    usageDate: String,
    today: String,
): Boolean {
    val day = runCatching { LocalDate.parse(usageDate) }.getOrNull() ?: return false
    return kotlin.math.abs(
        java.time.temporal.ChronoUnit.DAYS
            .between(day, LocalDate.parse(today)),
    ) <= 1
}

/** Whole minutes from [then] to [now], never negative. */
internal fun agoMinutes(
    then: Instant,
    now: Instant,
): Long = Duration.between(then, now).toMinutes().coerceAtLeast(0)

/** Prototype `dlabel`: 15 -> "15m", 60 -> "1h", 90 -> "1h30". */
internal fun minutesLabel(mins: Int): String =
    when {
        mins < MINS_PER_HOUR -> "${mins}m"
        mins % MINS_PER_HOUR == 0 -> "${mins / MINS_PER_HOUR}h"
        else -> "${mins / MINS_PER_HOUR}h${mins % MINS_PER_HOUR}"
    }

/** Today's rows: the phone's apps plus anything it reported using, busiest first. */
internal fun appRows(
    apps: List<DeviceApp>,
    usage: UsageSummary?,
    locked: Set<String>,
): List<RemoteAppRow> {
    val used = usage?.apps.orEmpty().associateBy { it.pkg }
    val labels = LinkedHashMap<String, String>()
    used.forEach { (pkg, entry) -> labels[pkg] = entry.label }
    apps.forEach { labels[it.pkg] = it.label }
    val busiest = used.values.maxOfOrNull { it.mins }?.coerceAtLeast(1) ?: 1
    return labels
        .map { (pkg, label) ->
            val mins = used[pkg]?.mins ?: 0
            RemoteAppRow(pkg, label, mins, (mins.toFloat() / busiest).coerceIn(0f, 1f), pkg in locked)
        }.sortedWith(compareByDescending<RemoteAppRow> { it.mins }.thenBy { it.label.lowercase() })
}

/**
 * The parent's Remote screen for one linked phone: status from the linked-devices list, the apps, usage, blocks and
 * lock state from the device's own documents, and every control as a command ([CommandSender]) or a direct write the
 * rules allow (alert dismissed, block on/off). Unlinking goes through the `unlinkDevice` function.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class RemoteDeviceViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        devices: LinkedDevicesRepository,
        private val remote: RemoteDeviceRepository,
        private val sender: CommandSender,
        private val unlinker: DeviceUnlinkRepository,
        private val decisions: RequestDecisionRepository,
        private val auth: AuthRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val deviceId: String = savedState.get<String>("deviceId").orEmpty()
        private val unlinking = MutableStateFlow(false)

        /** The `lastSeen` of the long-offline stretch the parent dismissed here; a later one shows the card again. */
        private val dismissedOffline = MutableStateFlow<Instant?>(null)
        private val events = Channel<RemoteEvent>(Channel.BUFFERED)

        /** Requests being answered, so a double tap sends one decision. */
        private val deciding = ConcurrentHashMap.newKeySet<String>()

        /** Toast messages, each delivered once. */
        internal val messages: Flow<RemoteEvent> = events.receiveAsFlow()

        internal val state: StateFlow<RemoteUiState> =
            auth.currentUser
                .map { it?.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    if (uid == null) flowOf(RemoteUiState.NotFound) else readyFlow(devices, DeviceTarget(uid, deviceId))
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RemoteUiState.Loading)

        private fun today(): Flow<String> =
            flow {
                while (true) {
                    emit(LocalDate.now(clock).toString())
                    delay(DATE_TICK_MS)
                }
            }.distinctUntilChanged()

        /** Firestore ends a listener flow on its first error: show [default] and listen again, with backoff. */
        private fun <T> Flow<T>.orDefault(
            default: T,
            what: String,
        ): Flow<T> =
            retryWhen { cause, attempt ->
                Timber.w(cause, "Remote device %s listener stopped", what)
                emit(default)
                delay(minOf(RETRY_BASE_MS shl minOf(attempt.toInt(), MAX_BACKOFF_SHIFT), RETRY_MAX_MS))
                true
            }

        private fun readyFlow(
            devices: LinkedDevicesRepository,
            target: DeviceTarget,
        ): Flow<RemoteUiState> {
            val device =
                devices
                    .observeAll()
                    .map { list ->
                        list.firstOrNull { it.id == deviceId }
                    }.distinctUntilChanged()
            val apps = remote.observeApps(target).orDefault(emptyList(), "apps")
            // The child names a usage day in its own time zone: its newest day counts as today within a day either way.
            val usage =
                combine(today(), remote.observeLatestUsage(target).orDefault(null, "usage")) { date, latest ->
                    latest?.takeIf { isAroundToday(it.date, date) }
                }
            val blocks = remote.observeBlocks(target).orDefault(emptyList(), "blocks")
            val locked = remote.observeLocked(target).orDefault(emptySet(), "locks")
            val details = combine(apps, usage, locked) { a, u, l -> Triple(appRows(a, u, l), u?.totalMins, l) }
            return combine(
                device,
                details,
                blocks,
                unlinking,
                dismissedOffline,
            ) { d, (rows, total, _), b, busy, dismissed ->
                if (d == null) {
                    RemoteUiState.NotFound
                } else {
                    val now = clock.instant()
                    RemoteUiState.Ready(
                        device = d,
                        alerts =
                            d.alerts
                                .filter { !it.dismissed }
                                .sortedByDescending { it.createdAt }
                                .map { it.toCard() }
                                .plus(derivedOfflineCard(d, dismissed)),
                        requests = d.requests.map { it.toCard(now) },
                        blocks = b,
                        apps = rows,
                        todayMins = total ?: d.todayMins,
                        unlinking = busy,
                    )
                }
            }
        }

        /** The "Offline for a while" card, derived from `lastSeen`: no function writes it, so Dismiss is local. */
        private fun derivedOfflineCard(
            device: LinkedDevice,
            dismissed: Instant?,
        ): List<RemoteAlertCard> {
            val seen = device.lastSeen
            return if (device.hasDerivedOfflineAlert && seen != null && seen != dismissed) {
                listOf(
                    RemoteAlertCard(
                        DERIVED_OFFLINE_ID,
                        TamperKind.OFFLINE,
                        null,
                        TIME_FORMAT.format(seen.atZone(clock.zone)),
                        agoMinutes(seen, clock.instant()),
                    ),
                )
            } else {
                emptyList()
            }
        }

        private fun TamperAlert.toCard() =
            RemoteAlertCard(
                id,
                kind,
                permission,
                TIME_FORMAT.format(createdAt.atZone(clock.zone)),
                agoMinutes(createdAt, clock.instant()),
            )

        private fun UnlockRequest.toCard(now: Instant) = RemoteRequestCard(id, app, minutes, agoMinutes(createdAt, now))

        private val ready: RemoteUiState.Ready? get() = state.value as? RemoteUiState.Ready

        private fun deviceName() = ready?.device?.name.orEmpty()

        private suspend fun target(): DeviceTarget? = auth.currentUser.first()?.let { DeviceTarget(it.uid, deviceId) }

        /** Sends [command]; [then] runs once it is queued. A later offline or failed delivery is reported too. */
        private fun send(
            command: RemoteCommand,
            then: () -> Unit = {},
        ) {
            val name = deviceName()
            viewModelScope.launch {
                val target = target() ?: return@launch
                then()
                sender.send(target, command).collect { delivery ->
                    when (delivery) {
                        DeliveryState.OFFLINE -> events.trySend(RemoteEvent.Offline(name))
                        DeliveryState.FAILED, DeliveryState.EXPIRED -> events.trySend(RemoteEvent.Failed(name))
                        DeliveryState.PENDING, DeliveryState.DONE -> Unit
                    }
                }
            }
        }

        fun setLocked(
            app: String,
            pkg: String,
            locked: Boolean,
        ) {
            val name = deviceName()
            send(if (locked) RemoteCommand.BlockApp(pkg) else RemoteCommand.UnblockApp(pkg)) {
                events.trySend(if (locked) RemoteEvent.AppLocked(app, name) else RemoteEvent.AppUnlocked(app, name))
            }
        }

        fun startFocus(mins: Int) {
            val name = deviceName()
            send(RemoteCommand.StartFocus(mins)) { events.trySend(RemoteEvent.FocusStarted(name)) }
        }

        fun stopFocus() {
            val name = deviceName()
            send(RemoteCommand.StopFocus) { events.trySend(RemoteEvent.FocusStopped(name)) }
        }

        /** Locks every app on the phone except the always-allowed ones, with no end. Delivery as for any command. */
        fun lockAll() {
            val name = deviceName()
            send(RemoteCommand.LockAll) { events.trySend(RemoteEvent.AllLocked(name)) }
        }

        fun unlockAll() = send(RemoteCommand.UnlockAll) { events.trySend(RemoteEvent.AllUnlocked) }

        internal fun remind(alert: RemoteAlertCard) {
            val name = deviceName()
            send(RemoteCommand.RemindPermission(alert.permission?.takeIf { it.isNotBlank() } ?: DEFAULT_PERMISSION)) {
                events.trySend(RemoteEvent.Reminded(name))
            }
        }

        /**
         * Answers an unlock request: [mins] more minutes, or 0 for No. The toast shows at once (as in the prototype);
         * the decision and the command for the phone go out in one write, which Firestore keeps while offline.
         */
        @Suppress("TooGenericExceptionCaught")
        internal fun decide(
            request: RemoteRequestCard,
            mins: Int,
        ) {
            if (!deciding.add(request.id)) return
            val name = deviceName()
            viewModelScope.launch {
                val target = target()
                if (target == null) {
                    deciding.remove(request.id)
                    return@launch
                }
                events.trySend(
                    if (mins > 0) {
                        RemoteEvent.RequestApproved(name, mins)
                    } else {
                        RemoteEvent.RequestDenied
                    },
                )
                try {
                    decisions.decide(target, request.id, mins)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Answering the request was refused")
                    events.trySend(RemoteEvent.Failed(name))
                    // Only a failure frees the request for another try; after a success the card goes away.
                    deciding.remove(request.id)
                }
            }
        }

        internal fun dismiss(alert: RemoteAlertCard) {
            if (alert.id == DERIVED_OFFLINE_ID) {
                dismissedOffline.value = ready?.device?.lastSeen
            } else {
                write { remote.dismissAlert(it, alert.id) }
            }
        }

        fun setBlockEnabled(
            blockId: String,
            enabled: Boolean,
        ) = write { remote.setBlockEnabled(it, blockId, enabled) }

        @Suppress("TooGenericExceptionCaught")
        private fun write(action: suspend (DeviceTarget) -> Unit) {
            val name = deviceName()
            viewModelScope.launch {
                val target = target() ?: return@launch
                try {
                    action(target)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Remote device write was refused")
                    events.trySend(RemoteEvent.Failed(name))
                }
            }
        }

        fun unlink() {
            if (unlinking.value) return
            val name = deviceName()
            unlinking.value = true
            viewModelScope.launch {
                val result = unlinker.unlink(deviceId)
                unlinking.value = false
                events.trySend(
                    if (result == UnlinkResult.Unlinked) RemoteEvent.Unlinked(name) else RemoteEvent.UnlinkFailed(name),
                )
            }
        }
    }
