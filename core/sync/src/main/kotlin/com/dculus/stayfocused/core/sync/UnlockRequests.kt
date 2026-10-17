package com.dculus.stayfocused.core.sync

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/** The minutes the child can ask for (prototype "Kids: Blocked overlay": 15, 30, 60). */
val REQUEST_MINUTES_OPTIONS: List<Int> = listOf(15, 30, 60)

/** Child side: asks the parent for more time on a blocked app (`devices/{id}/requests/{rid}`). */
interface UnlockRequestRepository {
    /**
     * Creates a pending request for [minutes] more of [app] (its label) and returns its id at once. Offline, Firestore
     * keeps the write and sends it later. [onRejected] is called if the server refuses it (the rules).
     */
    fun create(
        target: DeviceTarget,
        app: String,
        minutes: Int,
        onRejected: () -> Unit = {},
    ): String
}

/**
 * Parent side: records the decision on a request and tells the child. One batch writes both, so a decision is never
 * half-made: the request's `status` and `decidedAt` (the only fields the rules let the parent change) and the
 * `APPROVE_REQUEST` / `DENY_REQUEST` command the child applies.
 */
interface RequestDecisionRepository {
    /**
     * Approves request [reqId] for [minutes], or denies it when [minutes] is 0. Suspends until the server has it, which
     * is never while offline: the caller decides how long to wait. Throws when the server refuses the write.
     */
    suspend fun decide(
        target: DeviceTarget,
        reqId: String,
        minutes: Int,
    )
}

internal class FirebaseUnlockRequestRepository(
    private val firestore: FirebaseFirestore,
) : UnlockRequestRepository {
    override fun create(
        target: DeviceTarget,
        app: String,
        minutes: Int,
        onRejected: () -> Unit,
    ): String {
        val ref = device(firestore, target).collection("requests").document()
        ref
            .set(
                mapOf(
                    "app" to app,
                    "minutes" to minutes,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).addOnFailureListener {
                Timber.w(it, "The unlock request was rejected")
                onRejected()
            }
        return ref.id
    }
}

internal class FirebaseRequestDecisionRepository(
    private val firestore: FirebaseFirestore,
) : RequestDecisionRepository {
    override suspend fun decide(
        target: DeviceTarget,
        reqId: String,
        minutes: Int,
    ) {
        val device = device(firestore, target)
        val approved = minutes > 0
        val command: RemoteCommand =
            if (approved) RemoteCommand.ApproveRequest(reqId, minutes) else RemoteCommand.DenyRequest(reqId)
        firestore
            .batch()
            .update(
                device.collection("requests").document(reqId),
                mapOf(
                    "status" to if (approved) "approved" else "denied",
                    "decidedAt" to FieldValue.serverTimestamp(),
                ),
            ).set(
                device.collection("commands").document(),
                mapOf(
                    "type" to command.type.name,
                    "payload" to command.payload(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "status" to CommandStatus.PENDING.wire,
                ),
            ).commit()
            .await()
    }
}

private fun device(
    firestore: FirebaseFirestore,
    target: DeviceTarget,
) = firestore
    .collection("users")
    .document(target.parentUid)
    .collection("devices")
    .document(target.deviceId)

/** Used when the build has no Firebase configuration: a phone cannot be linked, so nothing is asked. */
internal object NoUnlockRequestRepository : UnlockRequestRepository {
    override fun create(
        target: DeviceTarget,
        app: String,
        minutes: Int,
        onRejected: () -> Unit,
    ): String {
        onRejected()
        return ""
    }
}

internal object NoRequestDecisionRepository : RequestDecisionRepository {
    override suspend fun decide(
        target: DeviceTarget,
        reqId: String,
        minutes: Int,
    ) = error("No Firebase configuration")
}

/** In-memory repository for tests: records every request created. */
class FakeUnlockRequestRepository : UnlockRequestRepository {
    data class Created(
        val target: DeviceTarget,
        val app: String,
        val minutes: Int,
        val id: String,
    )

    val created = mutableListOf<Created>()

    /** When true, the server "refuses" every request: [create] still returns an id and calls `onRejected`. */
    var rejecting = false

    override fun create(
        target: DeviceTarget,
        app: String,
        minutes: Int,
        onRejected: () -> Unit,
    ): String {
        val id = "req${created.size + 1}"
        created += Created(target, app, minutes, id)
        if (rejecting) onRejected()
        return id
    }
}

/** In-memory repository for tests: records every decision as (reqId, minutes). */
class FakeRequestDecisionRepository : RequestDecisionRepository {
    val decisions = mutableListOf<Pair<String, Int>>()

    /** Set to make every decision throw (the server refused it). */
    var failing = false

    override suspend fun decide(
        target: DeviceTarget,
        reqId: String,
        minutes: Int,
    ) {
        if (failing) error("refused")
        decisions += reqId to minutes
    }
}
