package com.dculus.stayfocused.core.sync

import kotlinx.coroutines.flow.Flow

/** Why an auth call failed, in terms the UI can word. */
enum class AuthFailure {
    InvalidCredentials,
    InvalidEmail,
    EmailInUse,
    WeakPassword,
    Network,
    Other,
}

sealed interface AuthResult {
    data object Success : AuthResult

    data class Failure(
        val reason: AuthFailure,
    ) : AuthResult
}

/** The signed-in parent account. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
)

/**
 * Account operations used by the sign-in screen. [FirebaseAuthRepository] is bound when the build has a Firebase
 * configuration, [FakeAuthRepository] otherwise. Google sign-in arrives with M6-04. Implementations never throw
 * for expected failures.
 */
interface AuthRepository {
    /** The signed-in user, or null. Emits the persisted session on start, so it survives app restarts. */
    val currentUser: Flow<AuthUser?>

    suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthResult

    suspend fun createAccount(
        email: String,
        password: String,
    ): AuthResult

    suspend fun signInWithGoogle(): AuthResult

    /** Emails a reset link. An unknown address also reports success, so account existence is not revealed. */
    suspend fun sendPasswordReset(email: String): AuthResult

    suspend fun signOut()
}
