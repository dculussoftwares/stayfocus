package com.dculus.stayfocused.core.sync

import android.content.Context
import com.dculus.stayfocused.core.data.repository.LocalDataWiper
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.IOException

internal const val DELETE_ACCOUNT_FUNCTION = "deleteAccount"
internal const val FUNCTIONS_REGION = "europe-west1"
private const val ERROR_USER_NOT_FOUND = "ERROR_USER_NOT_FOUND"

/** The `unauthenticated` message `deleteAccount` sends for a sign-in older than 5 minutes (secureCallable.ts). */
internal const val STALE_SIGN_IN_MESSAGE = "Sign in again to continue."

/** The server accepts 5 minutes (`MAX_AUTH_AGE_SECONDS`); a margin keeps a token from going stale on the way. */
private const val FRESH_SIGN_IN_SECONDS = 4 * 60L
private const val MILLIS_PER_SECOND = 1000L

internal class FirebaseAccountDeletionRepository(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
    private val wiper: LocalDataWiper,
    private val beforeDelete: suspend () -> Unit = {},
    private val afterFailedDelete: () -> Unit = {},
    private val tokenProvider: GoogleIdTokenProvider = CredentialManagerTokenProvider(),
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
) : AccountDeletionRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun isSignInFresh(): Boolean {
        val user = auth.currentUser ?: return false
        return try {
            val authTime = user.getIdToken(false).await().authTimestamp
            nowSeconds() - authTime <= FRESH_SIGN_IN_SECONDS
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not read the sign-in age")
            false
        }
    }

    override suspend fun usesPassword(): Boolean =
        auth.currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    override suspend fun reauthenticateWithPassword(password: String): AuthResult {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email == null) return AuthResult.Failure(AuthFailure.Other)
        return reauthenticate { user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await() }
    }

    override suspend fun reauthenticateWithGoogle(activityContext: Context): AuthResult {
        val user = auth.currentUser ?: return AuthResult.Failure(AuthFailure.Other)
        return when (val token = tokenProvider.getIdToken(activityContext)) {
            GoogleTokenResult.Cancelled -> {
                AuthResult.Cancelled
            }

            is GoogleTokenResult.Failed -> {
                AuthResult.Failure(token.reason)
            }

            is GoogleTokenResult.Token -> {
                reauthenticate {
                    user.reauthenticate(GoogleAuthProvider.getCredential(token.idToken, null)).await()
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun reauthenticate(block: suspend () -> Unit): AuthResult =
        try {
            block()
            // Force a new ID token so the function sees the new `auth_time`.
            auth.currentUser
                ?.getIdToken(true)
                ?.await()
            AuthResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Re-authentication failed")
            AuthResult.Failure(e.toAuthFailure())
        }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun deleteAccount(): DeleteAccountResult =
        try {
            // Stops the background profile repair, which could otherwise recreate `users/{uid}` after the delete.
            beforeDelete()
            functions
                .getHttpsCallable(DELETE_ACCOUNT_FUNCTION)
                .call(emptyMap<String, Any>())
                .await()
            finishLocally()
        } catch (e: CancellationException) {
            // Leaving the screen mid-call must not leave the profile repair paused for the rest of the session.
            afterFailedDelete()
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Account deletion failed")
            // The server may have finished but the answer got lost: if the account is gone, the deletion succeeded.
            if (accountIsGone()) {
                finishLocally()
            } else {
                afterFailedDelete()
                e.toDeleteResult()
            }
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun accountIsGone(): Boolean =
        try {
            auth.currentUser?.reload()?.await()
            false
        } catch (e: FirebaseAuthInvalidUserException) {
            // Disabled accounts and expired tokens throw the same type: only "not found" proves the deletion.
            Timber.d(e, "Could not reload the user")
            e.errorCode == ERROR_USER_NOT_FOUND
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Could not check whether the account still exists")
            false
        }

    /**
     * The Auth user is gone server-side: drop the cached session first (so nobody stays signed in), then erase this
     * device's data. A failed erase is reported as [DeleteAccountFailure.LocalWipe], not as a failed deletion.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun finishLocally(): DeleteAccountResult {
        auth.signOut()
        return try {
            wiper.wipe()
            DeleteAccountResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not erase local data after account deletion")
            DeleteAccountResult.Failure(DeleteAccountFailure.LocalWipe)
        }
    }
}

internal fun Throwable.toDeleteResult(): DeleteAccountResult =
    when {
        // Only the server's "stale sign-in" refusal means re-authenticate; a failed App Check is a different error.
        this is FirebaseFunctionsException &&
            code == FirebaseFunctionsException.Code.UNAUTHENTICATED &&
            message == STALE_SIGN_IN_MESSAGE -> {
            DeleteAccountResult.NeedsReauth
        }

        this is FirebaseFunctionsException &&
            (
                code == FirebaseFunctionsException.Code.UNAVAILABLE ||
                    code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED
            ) -> {
            DeleteAccountResult.Failure(DeleteAccountFailure.Network)
        }

        // The Functions SDK wraps a connectivity failure (no network, DNS) as INTERNAL with the IOException as cause.
        generateSequence(this) { it.cause }.any { it is FirebaseNetworkException || it is IOException } -> {
            DeleteAccountResult.Failure(DeleteAccountFailure.Network)
        }

        else -> {
            DeleteAccountResult.Failure(DeleteAccountFailure.Other)
        }
    }
