package com.dculus.stayfocused.core.sync

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * The parent's push address: this phone's FCM token under `users/{uid}/fcmTokens/{token}`, where the `notifyParent`
 * function finds it. Implementations never throw: a push that does not arrive must not break sign-in or sign-out.
 */
interface PushTokenRepository {
    /** Stores (or refreshes) this phone's current token for [uid]. */
    suspend fun register(uid: String)

    /** Deletes this phone's token for [uid] and the token itself, so the account stops getting pushes here. */
    suspend fun unregister(uid: String)
}

internal object PushTokenTimeouts {
    const val UNREGISTER_MILLIS = 5_000L
}

internal class FirebasePushTokenRepository(
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
) : PushTokenRepository {
    private fun tokens(uid: String) = firestore.collection("users").document(uid).collection("fcmTokens")

    @Suppress("TooGenericExceptionCaught")
    override suspend fun register(uid: String) {
        try {
            val token = messaging.token.await()
            tokens(uid)
                .document(token)
                .set(mapOf("createdAt" to FieldValue.serverTimestamp(), "platform" to "android"))
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Registering the push token failed")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun unregister(uid: String) {
        // Offline, a delete only queues locally and then would be refused after sign-out: do not wait for it forever.
        withTimeoutOrNull(PushTokenTimeouts.UNREGISTER_MILLIS) {
            try {
                val token = messaging.token.await()
                tokens(uid).document(token).delete().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Removing the push token document failed")
            }
        }
        // Even when the document is still there (offline, timed out): a deleted token is refused by FCM, and
        // notifyParent then removes the stale document, so a signed-out phone stops receiving pushes.
        try {
            messaging.deleteToken().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Deleting the push token failed")
        }
    }
}

/** Used when the build has no Firebase configuration: there is no account to notify. */
internal object NoPushTokenRepository : PushTokenRepository {
    override suspend fun register(uid: String) = Unit

    override suspend fun unregister(uid: String) = Unit
}

/** In-memory repository for tests. */
class FakePushTokenRepository : PushTokenRepository {
    val registered = mutableListOf<String>()
    val unregistered = mutableListOf<String>()

    override suspend fun register(uid: String) {
        registered += uid
    }

    override suspend fun unregister(uid: String) {
        unregistered += uid
    }
}

/**
 * Keeps the signed-in parent's token registered: every sign-in (and every app start with a restored session) stores
 * the current token, which also refreshes it. Removing it on sign-out happens in the sign-out itself, while the
 * account can still write.
 */
class PushTokenSync
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val tokens: PushTokenRepository,
    ) {
        /** Runs until cancelled. */
        suspend fun run() {
            auth.currentUser
                .map { it?.uid }
                .distinctUntilChanged()
                .collect { uid -> if (uid != null) tokens.register(uid) }
        }

        /** The token changed (`onNewToken`): store the new one for the signed-in parent, if any. */
        suspend fun refresh() {
            auth.currentUser
                .first()
                ?.uid
                ?.let { tokens.register(it) }
        }
    }
