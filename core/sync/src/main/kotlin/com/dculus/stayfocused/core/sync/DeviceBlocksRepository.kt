package com.dculus.stayfocused.core.sync

import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.model.Block
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Instant
import java.util.Date

/**
 * The blocks, focus end and app list of one linked phone (`users/{parentUid}/devices/{deviceId}`). The parent writes
 * blocks and reads the app list; the Kids phone reads blocks (also from Firestore's offline cache) and mirrors its
 * focus end. Writes never wait for the server: offline, Firestore keeps them and sends them later.
 */
interface DeviceBlocksRepository {
    /**
     * Parent: queues [block] under its id. With [preserveEnabled] the stored on/off switch is left as it is (another
     * screen may have flipped it since this copy was made). [onRejected] is called if the server refuses it.
     */
    fun push(
        target: DeviceTarget,
        block: Block,
        preserveEnabled: Boolean = false,
        onRejected: () -> Unit = {},
    )

    /** Parent: queues the removal of the block [blockId]. */
    fun remove(
        target: DeviceTarget,
        blockId: String,
        onRejected: () -> Unit = {},
    )

    /**
     * Kids: every block of this phone on each change, from the local cache first, so a restart or airplane mode still
     * has the last synced blocks. Documents that do not fit their type are left out. Fails when the listener is denied.
     */
    fun observeBlocks(target: DeviceTarget): Flow<List<Block>>

    /** Kids: the blocks as the server has them right now. Throws when the server cannot be reached. */
    suspend fun fetchBlocks(target: DeviceTarget): List<Block>

    /** Parent: the launchable apps the child phone reported, A to Z by label is up to the caller. */
    fun observeApps(target: DeviceTarget): Flow<List<AppInfo>>

    /** Kids: mirrors the end of the focus session to the device doc (null = no focus). Queued, not awaited. */
    fun setFocusEndsAt(
        target: DeviceTarget,
        endsAt: Instant?,
    )

    /** Kids: mirrors "all apps locked" to the device doc so the parent sees it. Queued, not awaited. */
    fun setAllLocked(
        target: DeviceTarget,
        locked: Boolean,
    )
}

/** Fields of a block document only some types use. */
private val BLOCK_OPTIONAL_FIELDS =
    setOf("limitMins", "period", "useMins", "restMins", "range", "durationMins", "startedAt", "cycleScope")

internal class FirebaseDeviceBlocksRepository(
    private val firestore: FirebaseFirestore,
) : DeviceBlocksRepository {
    private fun device(target: DeviceTarget) =
        firestore
            .collection("users")
            .document(target.parentUid)
            .collection("devices")
            .document(target.deviceId)

    override fun push(
        target: DeviceTarget,
        block: Block,
        preserveEnabled: Boolean,
        onRejected: () -> Unit,
    ) {
        val doc = device(target).collection("blocks").document(block.id)
        val rejected = { e: Exception ->
            Timber.w(e, "Saving block %s was rejected", block.id)
            onRejected()
        }
        if (!preserveEnabled) {
            doc.set(block.toWire()).addOnFailureListener(rejected)
            return
        }
        // An edit of a block the child already has: update everything but the stored `enabled`, and clear the fields
        // the new type does not use. A block that is gone meanwhile is written in full instead.
        val wire = block.toWire()
        val update = (BLOCK_OPTIONAL_FIELDS - wire.keys).associateWith { FieldValue.delete() } + (wire - "enabled")
        doc.update(update).addOnFailureListener { e ->
            val missing = (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.NOT_FOUND
            if (missing) doc.set(wire).addOnFailureListener(rejected) else rejected(e)
        }
    }

    override fun remove(
        target: DeviceTarget,
        blockId: String,
        onRejected: () -> Unit,
    ) {
        device(target)
            .collection("blocks")
            .document(blockId)
            .delete()
            .addOnFailureListener {
                Timber.w(it, "Removing block %s was rejected", blockId)
                onRejected()
            }
    }

    override fun observeBlocks(target: DeviceTarget): Flow<List<Block>> =
        callbackFlow {
            val registration =
                device(target).collection("blocks").addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                    } else if (snapshot != null && !(snapshot.isEmpty && snapshot.metadata.isFromCache)) {
                        // An empty answer from the cache only means "nothing cached yet", never "all blocks removed":
                        // acting on it would wipe the blocks this phone enforces offline.
                        trySend(snapshot.documents.mapNotNull { it.toBlock() })
                    }
                }
            awaitClose { registration.remove() }
        }

    override suspend fun fetchBlocks(target: DeviceTarget): List<Block> =
        device(target)
            .collection("blocks")
            .get(Source.SERVER)
            .await()
            .documents
            .mapNotNull { it.toBlock() }

    override fun observeApps(target: DeviceTarget): Flow<List<AppInfo>> =
        callbackFlow {
            val registration =
                device(target).collection("apps").addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                    } else if (snapshot != null) {
                        trySend(
                            snapshot.documents.mapNotNull { doc ->
                                doc.getString("label")?.let { AppInfo(doc.id, it) }
                            },
                        )
                    }
                }
            awaitClose { registration.remove() }
        }

    override fun setFocusEndsAt(
        target: DeviceTarget,
        endsAt: Instant?,
    ) {
        device(target)
            .update("focusEndsAt", endsAt?.let { Timestamp(Date.from(it)) })
            .addOnFailureListener { Timber.w(it, "Mirroring the focus end was rejected") }
    }

    override fun setAllLocked(
        target: DeviceTarget,
        locked: Boolean,
    ) {
        device(target)
            .update("allLocked", locked)
            .addOnFailureListener { Timber.w(it, "Mirroring the all-apps lock was rejected") }
    }

    private fun DocumentSnapshot.toBlock(): Block? =
        data?.let { parseWireBlock(id, it) }.also {
            if (it == null) Timber.w("Ignoring malformed block %s", id)
        }
}

/** Used when the build has no Firebase configuration: nothing is sent and nothing arrives. */
internal object NoDeviceBlocksRepository : DeviceBlocksRepository {
    override fun push(
        target: DeviceTarget,
        block: Block,
        preserveEnabled: Boolean,
        onRejected: () -> Unit,
    ) = onRejected()

    override fun remove(
        target: DeviceTarget,
        blockId: String,
        onRejected: () -> Unit,
    ) = onRejected()

    override fun observeBlocks(target: DeviceTarget): Flow<List<Block>> = emptyFlow()

    override suspend fun fetchBlocks(target: DeviceTarget): List<Block> = error("Firebase is not configured")

    override fun observeApps(target: DeviceTarget): Flow<List<AppInfo>> = flowOf(emptyList())

    override fun setFocusEndsAt(
        target: DeviceTarget,
        endsAt: Instant?,
    ) = Unit

    override fun setAllLocked(
        target: DeviceTarget,
        locked: Boolean,
    ) = Unit
}

/** In-memory repository for tests. */
class FakeDeviceBlocksRepository : DeviceBlocksRepository {
    val blocks = MutableStateFlow<List<Block>>(emptyList())
    val apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val pushed = mutableListOf<Pair<DeviceTarget, Block>>()
    val removed = mutableListOf<Pair<DeviceTarget, String>>()
    val focusEnds = mutableListOf<Instant?>()
    val allLockedMirrored = mutableListOf<Boolean>()

    /** Set to make [fetchBlocks] throw (offline). */
    var offline = false

    /** Whether each push asked to keep the stored on/off switch. */
    val preservedEnabled = mutableListOf<Boolean>()

    override fun push(
        target: DeviceTarget,
        block: Block,
        preserveEnabled: Boolean,
        onRejected: () -> Unit,
    ) {
        pushed += target to block
        preservedEnabled += preserveEnabled
    }

    override fun remove(
        target: DeviceTarget,
        blockId: String,
        onRejected: () -> Unit,
    ) {
        removed += target to blockId
    }

    override fun observeBlocks(target: DeviceTarget): Flow<List<Block>> = blocks

    override suspend fun fetchBlocks(target: DeviceTarget): List<Block> {
        check(!offline) { "offline" }
        return blocks.value
    }

    override fun observeApps(target: DeviceTarget): Flow<List<AppInfo>> = apps

    override fun setFocusEndsAt(
        target: DeviceTarget,
        endsAt: Instant?,
    ) {
        focusEnds += endsAt
    }

    override fun setAllLocked(
        target: DeviceTarget,
        locked: Boolean,
    ) {
        allLockedMirrored += locked
    }
}
