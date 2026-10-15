package com.dculus.stayfocused.core.sync

import android.content.Context

/** Why deleting the account did not finish. */
enum class DeleteAccountFailure {
    Network,
    Other,

    /** The account is deleted and signed out, but erasing this phone's data failed. Retrying cannot help. */
    LocalWipe,
}

sealed interface DeleteAccountResult {
    data object Success : DeleteAccountResult

    /** The server refused because the sign-in is older than 5 minutes: re-authenticate, then try again. */
    data object NeedsReauth : DeleteAccountResult

    data class Failure(
        val reason: DeleteAccountFailure,
    ) : DeleteAccountResult
}

/**
 * Deletes the signed-in parent's account through the `deleteAccount` Cloud Function (Play's account-deletion
 * requirement), which removes `users/{uid}` and the Auth user server-side even if the app dies halfway. Implementations
 * never throw for expected failures.
 */
interface AccountDeletionRepository {
    /** True when the current sign-in is recent enough (under 5 minutes) for the server to accept a deletion. */
    suspend fun isSignInFresh(): Boolean

    /** True when the account signs in with an email and password (so re-authentication asks for the password). */
    suspend fun usesPassword(): Boolean

    suspend fun reauthenticateWithPassword(password: String): AuthResult

    suspend fun reauthenticateWithGoogle(activityContext: Context): AuthResult

    /**
     * Calls the function. On success the session is signed out and everything stored on this device is erased
     * (blocks included: "Delete account and all data"); onboarding shows again.
     */
    suspend fun deleteAccount(): DeleteAccountResult
}
