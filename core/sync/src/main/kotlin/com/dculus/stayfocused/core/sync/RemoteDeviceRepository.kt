package com.dculus.stayfocused.core.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/** A launchable app on the child's phone (`devices/{id}/apps/{pkg}`). */
data class DeviceApp(
    val pkg: String,
    val label: String,
)

/** A block the parent made for a child's phone, on or off. */
data class RemoteBlock(
    val rule: KidsRule,
    val enabled: Boolean,
)

/** A lock or unlock command as the Remote screen folds it into "which apps are locked". */
data class LockEvent(
    val id: String,
    val lock: Boolean,
    val pkg: String,
    /** Server time; null while the server has not stamped it yet (then it is the newest). */
    val createdAtMillis: Long?,
    val status: CommandStatus,
)

/**
 * The packages locked by [events]: replayed oldest first, so the last lock or unlock of an app wins. A command that
 * failed or expired never took effect and is skipped; pending and done ones count (the child applies them in order).
 */
fun lockedPackages(events: List<LockEvent>): Set<String> {
    val locked = linkedSetOf<String>()
    events
        .filter { it.status == CommandStatus.PENDING || it.status == CommandStatus.DONE }
        .sortedWith(compareBy<LockEvent> { it.createdAtMillis ?: Long.MAX_VALUE }.thenBy { it.id })
        .forEach { if (it.lock) locked += it.pkg else locked -= it.pkg }
    return locked
}

/** What the parent's Remote screen reads from, and the two direct writes it makes, for one child phone. */
interface RemoteDeviceRepository {
    /** The child's launchable apps. The flow fails when the listener is denied or lost. */
    fun observeApps(target: DeviceTarget): Flow<List<DeviceApp>>

    /**
     * The newest usage day the phone reported, or null when it has reported none. The child names the day in its own
     * time zone, so the caller decides whether that is "today" for the parent.
     */
    fun observeLatestUsage(target: DeviceTarget): Flow<UsageSummary?>

    /** Every block for this phone, on or off, by name. */
    fun observeBlocks(target: DeviceTarget): Flow<List<RemoteBlock>>

    /** Packages the parent has locked, derived from the lock and unlock commands sent. */
    fun observeLocked(target: DeviceTarget): Flow<Set<String>>

    /** Switches a block on or off. Offline, Firestore keeps the write. Throws when the server refuses it. */
    suspend fun setBlockEnabled(
        target: DeviceTarget,
        blockId: String,
        enabled: Boolean,
    )

    /** Marks a tamper alert as dismissed (the only field the rules let the parent change). */
    suspend fun dismissAlert(
        target: DeviceTarget,
        alertId: String,
    )
}

internal class FirebaseRemoteDeviceRepository(
    private val firestore: FirebaseFirestore,
) : RemoteDeviceRepository {
    private fun device(target: DeviceTarget) =
        firestore
            .collection("users")
            .document(target.parentUid)
            .collection("devices")
            .document(target.deviceId)

    override fun observeApps(target: DeviceTarget): Flow<List<DeviceApp>> =
        callbackFlow {
            val registration =
                device(target).collection("apps").addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                    } else if (snapshot != null) {
                        trySend(
                            snapshot.documents.map {
                                DeviceApp(it.id, it.getString("label")?.takeIf { l -> l.isNotBlank() } ?: it.id)
                            },
                        )
                    }
                }
            awaitClose { registration.remove() }
        }

    override fun observeLatestUsage(target: DeviceTarget): Flow<UsageSummary?> =
        callbackFlow {
            val registration =
                device(target)
                    .collection("usage")
                    .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                    .limit(1)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                        } else if (snapshot != null) {
                            trySend(snapshot.documents.firstOrNull()?.toUsage())
                        }
                    }
            awaitClose { registration.remove() }
        }

    override fun observeBlocks(target: DeviceTarget): Flow<List<RemoteBlock>> =
        callbackFlow {
            val registration =
                device(target).collection("blocks").addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                    } else if (snapshot != null) {
                        val blocks =
                            snapshot.documents.mapNotNull {
                                val data = it.data.orEmpty()
                                parseRuleAnyState(it.id, data)?.let { rule ->
                                    RemoteBlock(rule, data["enabled"] == true)
                                }
                            }
                        trySend(blocks.sortedBy { it.rule.name })
                    }
                }
            awaitClose { registration.remove() }
        }

    override fun observeLocked(target: DeviceTarget): Flow<Set<String>> =
        callbackFlow {
            val registration =
                device(target)
                    .collection("commands")
                    .whereIn("type", listOf(CommandType.BLOCK_APP.name, CommandType.UNBLOCK_APP.name))
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                        } else if (snapshot != null) {
                            trySend(lockedPackages(snapshot.documents.mapNotNull { it.toLockEvent() }))
                        }
                    }
            awaitClose { registration.remove() }
        }

    override suspend fun setBlockEnabled(
        target: DeviceTarget,
        blockId: String,
        enabled: Boolean,
    ) {
        device(target)
            .collection("blocks")
            .document(blockId)
            .update("enabled", enabled)
            .await()
    }

    override suspend fun dismissAlert(
        target: DeviceTarget,
        alertId: String,
    ) {
        device(target)
            .collection("alerts")
            .document(alertId)
            .update("dismissed", true)
            .await()
    }
}

private fun DocumentSnapshot.toLockEvent(): LockEvent? {
    val lock = getString("type")?.let { CommandType.entries.firstOrNull { c -> c.name == it } }
    val pkg = (get("payload") as? Map<*, *>)?.get("pkg") as? String
    val isLockCommand = lock == CommandType.BLOCK_APP || lock == CommandType.UNBLOCK_APP
    return if (!isLockCommand || pkg == null) {
        null
    } else {
        LockEvent(
            id = id,
            lock = lock == CommandType.BLOCK_APP,
            pkg = pkg,
            createdAtMillis =
                getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
            status = CommandStatus.fromWire(getString("status")),
        )
    }
}

/** A usage document read defensively: the rules only bound the size of `apps`, not what is in it. */
private fun DocumentSnapshot.toUsage(): UsageSummary {
    val apps =
        (get("apps") as? List<*>).orEmpty().mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            val pkg = m["pkg"] as? String ?: return@mapNotNull null
            UsageAppEntry(
                pkg = pkg,
                label = (m["label"] as? String)?.takeIf { it.isNotBlank() } ?: pkg,
                mins = (m["mins"] as? Number)?.toInt() ?: 0,
                opens = (m["opens"] as? Number)?.toInt() ?: 0,
            )
        }
    return UsageSummary(id, getLong("totalMins")?.toInt() ?: 0, apps)
}

/** Used when the build has no Firebase configuration: no phone is linked, so there is nothing to read or change. */
internal object NoRemoteDeviceRepository : RemoteDeviceRepository {
    override fun observeApps(target: DeviceTarget): Flow<List<DeviceApp>> = flowOf(emptyList())

    override fun observeLatestUsage(target: DeviceTarget): Flow<UsageSummary?> = flowOf(null)

    override fun observeBlocks(target: DeviceTarget): Flow<List<RemoteBlock>> = flowOf(emptyList())

    override fun observeLocked(target: DeviceTarget): Flow<Set<String>> = flowOf(emptySet())

    override suspend fun setBlockEnabled(
        target: DeviceTarget,
        blockId: String,
        enabled: Boolean,
    ) = error("No Firebase configuration")

    override suspend fun dismissAlert(
        target: DeviceTarget,
        alertId: String,
    ) = error("No Firebase configuration")
}

/** In-memory repository for tests: the test sets the flows' values and reads back the writes. */
class FakeRemoteDeviceRepository : RemoteDeviceRepository {
    val apps = MutableStateFlow<List<DeviceApp>>(emptyList())
    val usage = MutableStateFlow<UsageSummary?>(null)
    val blocks = MutableStateFlow<List<RemoteBlock>>(emptyList())
    val locked = MutableStateFlow<Set<String>>(emptySet())

    val blockWrites = mutableListOf<Pair<String, Boolean>>()
    val dismissed = mutableListOf<String>()

    /** Set to make every write throw (the server refused it). */
    var failing = false

    override fun observeApps(target: DeviceTarget): Flow<List<DeviceApp>> = apps

    override fun observeLatestUsage(target: DeviceTarget): Flow<UsageSummary?> = usage

    override fun observeBlocks(target: DeviceTarget): Flow<List<RemoteBlock>> = blocks

    override fun observeLocked(target: DeviceTarget): Flow<Set<String>> = locked

    override suspend fun setBlockEnabled(
        target: DeviceTarget,
        blockId: String,
        enabled: Boolean,
    ) {
        if (failing) error("refused")
        blockWrites += blockId to enabled
    }

    override suspend fun dismissAlert(
        target: DeviceTarget,
        alertId: String,
    ) {
        if (failing) error("refused")
        dismissed += alertId
    }
}
