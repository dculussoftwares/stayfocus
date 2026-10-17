package com.dculus.stayfocused.core.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/** Where a command is in its life. Only the child moves it from [PENDING]. */
enum class CommandStatus(
    val wire: String,
) {
    PENDING("pending"),
    DONE("done"),
    FAILED("failed"),
    EXPIRED("expired"),
    ;

    companion object {
        fun fromWire(value: String?): CommandStatus = entries.firstOrNull { it.wire == value } ?: PENDING
    }
}

/** A command as stored: raw type and payload, so an unknown or malformed one can still be acknowledged as failed. */
data class PendingCommand(
    val id: String,
    val type: String?,
    val payload: Map<String, Any?>,
    /** Server time it was created; null while the server has not stamped it yet. */
    val createdAtMillis: Long?,
)

/** The command collection of one device. Reads and writes throw on failure unless noted. */
interface CommandRepository {
    /**
     * Queues [command] and returns its id at once. Offline, Firestore keeps the write and sends it later, so this
     * does not wait for the server; follow progress with [observeStatus].
     */
    fun send(
        target: DeviceTarget,
        command: RemoteCommand,
    ): String

    /** The status of one command as it changes; [CommandStatus.PENDING] until the child acknowledges it. */
    fun observeStatus(
        target: DeviceTarget,
        id: String,
    ): Flow<CommandStatus>

    /** Every command still `pending`, on each change. The flow fails when the listener is denied or lost. */
    fun observePending(target: DeviceTarget): Flow<List<PendingCommand>>

    /** Child only: sets the final [status] and `ackAt`. */
    suspend fun acknowledge(
        target: DeviceTarget,
        id: String,
        status: CommandStatus,
    )
}

internal class FirebaseCommandRepository(
    private val firestore: FirebaseFirestore,
) : CommandRepository {
    private fun commands(target: DeviceTarget) =
        firestore
            .collection("users")
            .document(target.parentUid)
            .collection("devices")
            .document(target.deviceId)
            .collection("commands")

    override fun send(
        target: DeviceTarget,
        command: RemoteCommand,
    ): String {
        val ref = commands(target).document()
        ref
            .set(
                mapOf(
                    "type" to command.type.name,
                    "payload" to command.payload(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "status" to CommandStatus.PENDING.wire,
                ),
            ).addOnFailureListener { Timber.w(it, "Sending %s was rejected", command.type) }
        return ref.id
    }

    override fun observeStatus(
        target: DeviceTarget,
        id: String,
    ): Flow<CommandStatus> =
        callbackFlow {
            val registration =
                commands(target).document(id).addSnapshotListener { snapshot, error ->
                    if (error != null) close(error) else trySend(CommandStatus.fromWire(snapshot?.getString("status")))
                }
            awaitClose { registration.remove() }
        }

    override fun observePending(target: DeviceTarget): Flow<List<PendingCommand>> =
        callbackFlow {
            val registration =
                commands(target)
                    .whereEqualTo("status", CommandStatus.PENDING.wire)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                        } else if (snapshot != null) {
                            trySend(snapshot.documents.map { it.toPending() })
                        }
                    }
            awaitClose { registration.remove() }
        }

    override suspend fun acknowledge(
        target: DeviceTarget,
        id: String,
        status: CommandStatus,
    ) {
        commands(target)
            .document(id)
            .update(mapOf("status" to status.wire, "ackAt" to FieldValue.serverTimestamp()))
            .await()
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toPending() =
        PendingCommand(
            id = id,
            type = getString("type"),
            payload = (get("payload") as? Map<String, Any?>).orEmpty(),
            createdAtMillis =
                getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
        )
}

/** Used when the build has no Firebase configuration: commands go nowhere and never arrive. */
internal object NoCommandRepository : CommandRepository {
    override fun send(
        target: DeviceTarget,
        command: RemoteCommand,
    ) = "offline"

    override fun observeStatus(
        target: DeviceTarget,
        id: String,
    ): Flow<CommandStatus> = MutableStateFlow(CommandStatus.PENDING)

    override fun observePending(target: DeviceTarget): Flow<List<PendingCommand>> = MutableSharedFlow()

    override suspend fun acknowledge(
        target: DeviceTarget,
        id: String,
        status: CommandStatus,
    ) = Unit
}

/** In-memory repository for tests: [send] stores a pending command; [acknowledge] records the final status. */
class FakeCommandRepository : CommandRepository {
    private val pending = MutableStateFlow<List<PendingCommand>>(emptyList())
    private val statuses = MutableStateFlow<Map<String, CommandStatus>>(emptyMap())
    private var counter = 0

    val acks = mutableListOf<Pair<String, CommandStatus>>()

    /** Set to make [acknowledge] throw (simulates offline). */
    var failingAck = false

    override fun send(
        target: DeviceTarget,
        command: RemoteCommand,
    ): String {
        val id = "cmd${counter++}"
        add(PendingCommand(id, command.type.name, command.payload(), createdAtMillis = null))
        return id
    }

    /** Puts a stored command in front of the child, as the server would. */
    fun add(command: PendingCommand) {
        pending.value += command
        statuses.value += command.id to CommandStatus.PENDING
    }

    override fun observeStatus(
        target: DeviceTarget,
        id: String,
    ): Flow<CommandStatus> = statuses.map { it[id] ?: CommandStatus.PENDING }

    override fun observePending(target: DeviceTarget): Flow<List<PendingCommand>> = pending

    override suspend fun acknowledge(
        target: DeviceTarget,
        id: String,
        status: CommandStatus,
    ) {
        if (failingAck) error("offline")
        acks += id to status
        pending.value = pending.value.filterNot { it.id == id }
        statuses.value += id to status
    }
}
