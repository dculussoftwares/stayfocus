package com.dculus.stayfocused.core.sync

import android.os.Build
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Clock

/** A token this phone has written to `linkTokens/{token}`. */
data class LinkSession(
    val token: String,
    val code: String,
    val expiresAtMillis: Long,
)

/** Where a token is in its life, as far as the Kids phone can see. */
enum class LinkTokenStatus { OPEN, CLAIMED }

/** The Kids side of pairing: publish a one-time token and watch for the parent claiming it. */
interface LinkTokenRepository {
    /** Writes a fresh token owned by [childUid]. Null when it could not be written (offline, no Firebase). */
    suspend fun create(childUid: String): LinkSession?

    /** The token's status as it changes. */
    fun observe(token: String): Flow<LinkTokenStatus>
}

internal class FirebaseLinkTokenRepository(
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
    private val model: String = Build.MODEL.orEmpty(),
) : LinkTokenRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun create(childUid: String): LinkSession? {
        val session =
            LinkSession(
                token = LinkTokens.newToken(),
                code = LinkTokens.newCode(),
                expiresAtMillis = clock.millis() + LinkTokens.TTL_MILLIS - LinkTokens.CLOCK_SKEW_MARGIN_MILLIS,
            )
        return try {
            firestore
                .collection(COLLECTION)
                .document(session.token)
                .set(
                    mapOf(
                        "childUid" to childUid,
                        "model" to model.ifBlank { "Android" }.take(MAX_MODEL_LENGTH),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "expiresAt" to Timestamp(session.expiresAtMillis / MILLIS, 0),
                        "code" to session.code,
                        "status" to "open",
                    ),
                ).await()
            session
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "Could not publish the link token")
            null
        }
    }

    override fun observe(token: String): Flow<LinkTokenStatus> =
        callbackFlow {
            val registration =
                firestore.collection(COLLECTION).document(token).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.w(error, "Watching the link token failed")
                    } else if (snapshot != null) {
                        trySend(snapshot.toStatus())
                    }
                }
            awaitClose { registration.remove() }
        }

    private fun DocumentSnapshot.toStatus(): LinkTokenStatus {
        val open = !exists() || (getString("status") == "open" && get("claimedBy") == null)
        return if (open) LinkTokenStatus.OPEN else LinkTokenStatus.CLAIMED
    }

    private companion object {
        const val COLLECTION = "linkTokens"
        const val MAX_MODEL_LENGTH = 100
        const val MILLIS = 1000L
    }
}

/** Used when the build has no Firebase configuration: linking is not available. */
internal object NoLinkTokenRepository : LinkTokenRepository {
    override suspend fun create(childUid: String): LinkSession? = null

    override fun observe(token: String): Flow<LinkTokenStatus> = emptyFlow()
}

/** In-memory repository for tests and previews. */
class FakeLinkTokenRepository(
    private val clock: Clock = Clock.systemUTC(),
) : LinkTokenRepository {
    private val statuses = HashMap<String, MutableStateFlow<LinkTokenStatus>>()

    /** The sessions created so far, oldest first. */
    val created = mutableListOf<LinkSession>()

    /** Set to make [create] fail (simulates offline). */
    var failCreate = false

    override suspend fun create(childUid: String): LinkSession? {
        if (failCreate) return null
        val session =
            LinkSession(
                LinkTokens.newToken(),
                LinkTokens.newCode(),
                clock.millis() + LinkTokens.TTL_MILLIS - LinkTokens.CLOCK_SKEW_MARGIN_MILLIS,
            )
        created += session
        statuses[session.token] = MutableStateFlow(LinkTokenStatus.OPEN)
        return session
    }

    override fun observe(token: String): Flow<LinkTokenStatus> = flowFor(token)

    /** Simulates the parent claiming [token]. */
    fun claim(token: String) {
        flowFor(token).value = LinkTokenStatus.CLAIMED
    }

    private fun flowFor(token: String) = statuses.getOrPut(token) { MutableStateFlow(LinkTokenStatus.OPEN) }
}
