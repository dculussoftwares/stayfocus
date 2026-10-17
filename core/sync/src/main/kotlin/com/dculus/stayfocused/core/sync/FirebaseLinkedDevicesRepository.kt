package com.dculus.stayfocused.core.sync

import com.dculus.stayfocused.core.data.repository.LinkedDevicesRepository
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.TamperAlert
import com.dculus.stayfocused.core.model.TamperKind
import com.dculus.stayfocused.core.model.UnlockRequest
import com.dculus.stayfocused.core.model.UnlockRequestStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import timber.log.Timber
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** A phone is online while its last report is at most this old. */
internal val ONLINE_WINDOW: Duration = Duration.ofMinutes(3)

/** How often "online" and "today" are re-evaluated against the clock. */
private const val TICK_MILLIS = 30_000L
private const val STOP_TIMEOUT_MS = 5_000L
private const val RETRY_BASE_MS = 1_000L
private const val RETRY_MAX_MS = 30_000L
private const val RETRY_PERMANENT_MS = 60_000L
private const val MAX_BACKOFF_SHIFT = 5

/** A child's server timestamp can be a little ahead of this phone's clock; more than this is not believed. */
private val CLOCK_SKEW: Duration = Duration.ofSeconds(30)
private const val MAX_PERCENT = 100

/** Online = reported within the last 3 minutes. The child's `online` flag is ignored: a killed app can't clear it. */
internal fun isOnline(
    lastSeen: Instant?,
    now: Instant,
): Boolean {
    if (lastSeen == null) return false
    val age = Duration.between(lastSeen, now)
    return age <= ONLINE_WINDOW && age >= CLOCK_SKEW.negated()
}

/** One device and its sub-collections as last read, before the clock is applied. */
internal data class RawDevice(
    val id: String,
    val data: Map<String, Any?>,
    val alerts: List<TamperAlert>,
    val requests: List<UnlockRequest>,
    val todayMins: Int?,
)

private fun Any?.toInstant(): Instant? = (this as? Timestamp)?.toDate()?.toInstant()

private fun Any?.toIntOrNull(): Int? = (this as? Number)?.toInt()

internal fun RawDevice.toLinkedDevice(now: Instant): LinkedDevice {
    val lastSeen = data["lastSeen"].toInstant()
    return LinkedDevice(
        id = id,
        name = (data["name"] as? String)?.takeIf { it.isNotBlank() } ?: id,
        model = (data["model"] as? String).orEmpty(),
        battery = data["battery"].toIntOrNull()?.coerceIn(0, MAX_PERCENT),
        currentApp = (data["currentApp"] as? String)?.takeIf { it.isNotBlank() },
        online = isOnline(lastSeen, now),
        lastSeen = lastSeen,
        alerts = alerts,
        requests = requests,
        focusEndsAt = data["focusEndsAt"].toInstant(),
        todayMins = todayMins,
    )
}

internal fun alertFrom(
    id: String,
    data: Map<String, Any?>,
): TamperAlert? {
    val kind =
        when (data["kind"]) {
            "permission_lost" -> TamperKind.PERMISSION_LOST
            "unlinked" -> TamperKind.UNLINKED
            "offline" -> TamperKind.OFFLINE
            else -> return null
        }
    return TamperAlert(
        id = id,
        kind = kind,
        permission = data["permission"] as? String,
        createdAt = data["createdAt"].toInstant() ?: Instant.EPOCH,
        dismissed = data["dismissed"] == true,
    )
}

internal fun requestFrom(
    id: String,
    data: Map<String, Any?>,
): UnlockRequest? {
    val status =
        when (data["status"]) {
            "pending" -> UnlockRequestStatus.PENDING
            "approved" -> UnlockRequestStatus.APPROVED
            "denied" -> UnlockRequestStatus.DENIED
            else -> null
        }
    val app = data["app"] as? String
    val minutes = data["minutes"].toIntOrNull()
    return if (status == null || app == null || minutes == null) {
        null
    } else {
        UnlockRequest(id, app, minutes, status, data["createdAt"].toInstant() ?: Instant.EPOCH)
    }
}

/** Oldest link first; devices without a `linkedAt` yet (just created) go last. */
internal fun List<RawDevice>.oldestLinkFirst(): List<RawDevice> =
    sortedWith(
        compareBy<RawDevice> { it.data["linkedAt"].toInstant() ?: Instant.MAX }.thenBy { it.id },
    )

private fun Query.snapshots(): Flow<QuerySnapshot> =
    callbackFlow {
        val registration =
            addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot)
                }
            }
        awaitClose { registration.remove() }
    }

private fun DocumentReference.snapshots(): Flow<DocumentSnapshot> =
    callbackFlow {
        val registration =
            addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot)
                }
            }
        awaitClose { registration.remove() }
    }

/** Errors that retrying cannot fix: the rules deny the read, the user is signed out, or the query is invalid. */
internal fun Throwable.isPermanent(): Boolean =
    this is FirebaseFirestoreException &&
        code in
        setOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED,
            FirebaseFirestoreException.Code.INVALID_ARGUMENT,
            FirebaseFirestoreException.Code.FAILED_PRECONDITION,
            FirebaseFirestoreException.Code.NOT_FOUND,
        )

/** What a device's sub-collections last said; the defaults stand in until (or if) a listener answers. */
internal data class DeviceExtras(
    val alerts: List<TamperAlert> = emptyList(),
    val requests: List<UnlockRequest> = emptyList(),
    val todayMins: Int? = null,
)

/**
 * The parent's linked phones, from a snapshot listener on `users/{uid}/devices` plus each phone's undismissed alerts,
 * pending requests and today's usage. The last list is cached in memory per account (replay of one) and the listeners
 * stay up for a few seconds after the last collector leaves, so switching tabs does not re-read everything. Without a
 * signed-in account the list is empty. A failing listener is retried with backoff and the last good list stays; a
 * failing sub-collection only drops that part of its phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FirebaseLinkedDevicesRepository(
    private val firestore: FirebaseFirestore,
    auth: AuthRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val tickMillis: Long = TICK_MILLIS,
) : LinkedDevicesRepository {
    private val cache = ConcurrentHashMap<String, Flow<List<LinkedDevice>>>()

    // The cache is keyed by account, so one account's replayed list is never delivered under another, and the
    // replay expires with the listeners shortly after the last collector leaves.
    private val all: Flow<List<LinkedDevice>> =
        auth.currentUser
            .map { it?.uid }
            .distinctUntilChanged()
            .flatMapLatest { uid ->
                if (uid == null) flowOf(emptyList()) else sharedFor(uid)
            }

    override fun observeAll(): Flow<List<LinkedDevice>> = all

    private fun sharedFor(uid: String): Flow<List<LinkedDevice>> =
        cache.getOrPut(uid) {
            observe(uid).shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS, STOP_TIMEOUT_MS), replay = 1)
        }

    private fun ticks(): Flow<Instant> =
        flow {
            while (true) {
                emit(clock.instant())
                delay(tickMillis)
            }
        }

    /**
     * Firestore ends a snapshot flow on its first error, so a listener is re-created instead: with backoff for a
     * transient error, and only once a minute for a permanent one (denied, bad query) so access that comes back is
     * noticed without hammering the server. Before a retry [fallback] is emitted (when [emitOnRetry], or always for a
     * permanent error) so a stale value is not kept.
     */
    private fun <T> Flow<T>.retrying(
        what: String,
        fallback: T,
        emitOnRetry: Boolean = true,
    ): Flow<T> =
        retryWhen { cause, attempt ->
            Timber.w(cause, "Linked device %s listener stopped", what)
            val permanent = cause.isPermanent()
            if (emitOnRetry || permanent) emit(fallback)
            delay(
                if (permanent) {
                    RETRY_PERMANENT_MS
                } else {
                    minOf(RETRY_BASE_MS shl minOf(attempt.toInt(), MAX_BACKOFF_SHIFT), RETRY_MAX_MS)
                },
            )
            true
        }

    private fun observe(uid: String): Flow<List<LinkedDevice>> =
        flow {
            coroutineScope {
                val devices = firestore.collection("users").document(uid).collection("devices")
                // One root listener, shared by the data and the device-id streams below.
                val docs =
                    devices
                        .snapshots()
                        .map { snapshot -> snapshot.documents.associate { it.id to it.data.orEmpty() } }
                        .retrying("list", emptyMap(), emitOnRetry = false)
                        .shareIn(this, SharingStarted.Eagerly, replay = 1)
                emitAll(build(devices, docs))
            }
        }

    private fun build(
        devices: CollectionReference,
        docs: Flow<Map<String, Map<String, Any?>>>,
    ): Flow<List<LinkedDevice>> {
        // Only a change in which phones exist re-creates the sub-collection listeners, not a status heartbeat.
        val extras =
            docs
                .map { it.keys }
                .distinctUntilChanged()
                .flatMapLatest { ids ->
                    if (ids.isEmpty()) {
                        flowOf(emptyMap())
                    } else {
                        combine(ids.map { id -> extras(devices.document(id)).map { id to it } }) { it.toMap() }
                    }
                }
        return combine(docs, extras, ticks()) { data, extra, now ->
            data
                .map { (id, fields) ->
                    val e = extra[id] ?: DeviceExtras()
                    RawDevice(id, fields, e.alerts, e.requests, e.todayMins)
                }.oldestLinkFirst()
                .map { it.toLinkedDevice(now) }
        }.distinctUntilChanged()
    }

    /** Starts with [default] so one slow sub-collection never holds back its phone. */
    private fun <T> Flow<T>.orDefault(
        default: T,
        what: String,
    ): Flow<T> = onStart { emit(default) }.retrying(what, default)

    private fun extras(device: DocumentReference): Flow<DeviceExtras> {
        val alerts =
            device
                .collection("alerts")
                .whereEqualTo("dismissed", false)
                .snapshots()
                .map { s -> s.documents.mapNotNull { alertFrom(it.id, it.data.orEmpty()) } }
                .orDefault(emptyList(), "alerts")
        val requests =
            device
                .collection("requests")
                .whereEqualTo("status", "pending")
                .snapshots()
                .map { s -> s.documents.mapNotNull { requestFrom(it.id, it.data.orEmpty()) } }
                .orDefault(emptyList(), "requests")
        val today =
            ticks()
                .map { LocalDate.now(clock).toString() }
                .distinctUntilChanged()
                .flatMapLatest { date -> device.collection("usage").document(date).snapshots() }
                .map { it.getLong("totalMins")?.toInt() }
                .orDefault(null, "usage")
        return combine(alerts, requests, today) { a, r, t -> DeviceExtras(a, r, t) }
    }
}

/** Used when the build has no Firebase configuration: no phone is ever linked. */
internal object NoLinkedDevicesRepository : LinkedDevicesRepository {
    override fun observeAll(): Flow<List<LinkedDevice>> = flowOf(emptyList())
}
