package com.dculus.stayfocused.core.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Email and password accounts on Firebase Auth. The SDK keeps the session on disk, so [currentUser] emits the
 * signed-in user again after a restart. On sign-in the profile `users/{uid}` is created if it does not exist.
 */
internal class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {
    override val currentUser: Flow<AuthUser?> =
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthResult =
        call {
            val user = auth.signInWithEmailAndPassword(email, password).await().user
            if (user != null) ensureProfile(user)
        }

    override suspend fun createAccount(
        email: String,
        password: String,
    ): AuthResult =
        call {
            val user = auth.createUserWithEmailAndPassword(email, password).await().user
            if (user != null) ensureProfile(user)
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

    /** Creates `users/{uid}` on first sign-in. A failure here does not undo the sign-in; the next sign-in retries. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun ensureProfile(user: FirebaseUser) {
        try {
            val doc = firestore.collection("users").document(user.uid)
            if (doc.get().await().exists()) return
            val email = user.email.orEmpty()
            doc
                .set(
                    mapOf(
                        "displayName" to profileName(user.displayName, email),
                        "email" to email,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not create the profile")
        }
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, email = email, displayName = displayName)
}

/** The profile name: the account's display name, else the part of the email before the `@`. */
internal fun profileName(
    displayName: String?,
    email: String,
): String = displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore('@').ifBlank { "Parent" }
