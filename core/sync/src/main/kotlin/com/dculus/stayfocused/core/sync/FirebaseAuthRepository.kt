package com.dculus.stayfocused.core.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Email and password accounts on Firebase Auth. The SDK keeps the session on disk, so [currentUser] emits the
 * signed-in user again after a restart. The profile `users/{uid}` is created in the background if it does not exist.
 */
internal class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val retryDelaysMs: List<Long> = PROFILE_RETRY_DELAYS_MS,
) : AuthRepository {
    private val profileJobs = ConcurrentHashMap<String, Job>()

    override val currentUser: Flow<AuthUser?> =
        callbackFlow {
            val listener =
                FirebaseAuth.AuthStateListener {
                    val user = it.currentUser
                    // Also covers a session restored after a restart whose profile write never completed.
                    if (user != null) ensureProfileInBackground(user)
                    trySend(user?.toAuthUser())
                }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthResult =
        call {
            auth
                .signInWithEmailAndPassword(email, password)
                .await()
                .user
                ?.let(::ensureProfileInBackground)
        }

    override suspend fun createAccount(
        email: String,
        password: String,
    ): AuthResult =
        call {
            auth
                .createUserWithEmailAndPassword(email, password)
                .await()
                .user
                ?.let(::ensureProfileInBackground)
        }

    /** Google sign-in is M6-04. */
    override suspend fun signInWithGoogle(): AuthResult = AuthResult.Failure(AuthFailure.Other)

    @Suppress("TooGenericExceptionCaught")
    override suspend fun sendPasswordReset(email: String): AuthResult =
        try {
            auth.sendPasswordResetEmail(email).await()
            AuthResult.Success
        } catch (e: FirebaseAuthInvalidUserException) {
            // Do not reveal whether an account exists.
            Timber.d(e, "Password reset for an unknown address")
            AuthResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Password reset failed")
            AuthResult.Failure(e.toAuthFailure())
        }

    override suspend fun signOut() = auth.signOut()

    @Suppress("TooGenericExceptionCaught")
    private suspend fun call(block: suspend () -> Unit): AuthResult =
        try {
            block()
            AuthResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Auth call failed")
            AuthResult.Failure(e.toAuthFailure())
        }

    /**
     * Creates `users/{uid}` without holding up the sign-in: Firestore may be offline, and a queued write must not keep
     * the sign-in screen waiting. Retries with backoff; if it still fails, the next app start (the auth state listener
     * above) tries again. One job per uid at a time.
     */
    private fun ensureProfileInBackground(user: FirebaseUser) {
        profileJobs.compute(user.uid) { _, running ->
            if (running?.isActive == true) {
                running
            } else {
                scope.launch { createProfileWithRetry(user) }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun createProfileWithRetry(user: FirebaseUser) {
        for (attempt in 0..retryDelaysMs.size) {
            try {
                ensureProfile(auth.currentUser?.takeIf { it.uid == user.uid } ?: user)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not create the profile (attempt ${attempt + 1})")
            }
            retryDelaysMs.getOrNull(attempt)?.let { delay(it) }
        }
    }

    private suspend fun ensureProfile(user: FirebaseUser) {
        val doc = firestore.collection("users").document(user.uid)
        if (doc.get().await().exists()) return
        // The auth state listener can fire before the new user's fields are filled in; the caller retries.
        val email = checkNotNull(user.email) { "The user has no email yet" }
        doc
            .set(
                mapOf(
                    "displayName" to profileName(user.displayName, email),
                    "email" to email,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, email = email, displayName = displayName)
}

private val PROFILE_RETRY_DELAYS_MS = listOf(1_000L, 5_000L, 15_000L)

/** The profile name: the account's display name, else the part of the email before the `@`. */
internal fun profileName(
    displayName: String?,
    email: String,
): String = displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore('@').ifBlank { "Parent" }
