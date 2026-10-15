package com.dculus.stayfocused.core.sync

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * The anonymous identity of a Kids phone. Firebase persists the session itself, so [ensureSignedIn] only signs in
 * on the first launch (or after the session was lost) and the uid survives app restarts.
 */
interface DeviceAuth {
    /** The current anonymous uid, or null while signed out or when this build has no Firebase configuration. */
    val uid: Flow<String?>

    /** Returns the existing uid, or signs in anonymously. Null when that fails (offline, no Firebase). Never throws. */
    suspend fun ensureSignedIn(): String?
}

internal class FirebaseDeviceAuth(
    private val auth: FirebaseAuth,
) : DeviceAuth {
    override val uid: Flow<String?> =
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }

    override suspend fun ensureSignedIn(): String? {
        auth.currentUser?.uid?.let { return it }
        return try {
            auth
                .signInAnonymously()
                .await()
                .user
                ?.uid
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Timber.w(e, "Anonymous sign-in failed")
            null
        }
    }
}

/** Used when the build has no Firebase configuration: there is no identity, and linking is not available. */
internal object NoDeviceAuth : DeviceAuth {
    override val uid: Flow<String?> = flowOf(null)

    override suspend fun ensureSignedIn(): String? = null
}
