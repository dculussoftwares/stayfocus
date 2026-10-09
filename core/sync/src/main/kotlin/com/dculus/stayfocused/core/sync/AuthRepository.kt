package com.dculus.stayfocused.core.sync

/** Why an auth call failed, in terms the UI can word. */
enum class AuthFailure {
    InvalidCredentials,
    EmailInUse,
    Network,
    Other,
}

sealed interface AuthResult {
    data object Success : AuthResult

    data class Failure(
        val reason: AuthFailure,
    ) : AuthResult
}

/**
 * Account operations used by the sign-in screen. The real Firebase implementations arrive with M6-03 (Google) and
 * M6-04 (email); until then [FakeAuthRepository] is bound. Implementations never throw for expected failures.
 */
interface AuthRepository {
    suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthResult

    suspend fun createAccount(
        email: String,
        password: String,
    ): AuthResult

    suspend fun signInWithGoogle(): AuthResult

    suspend fun sendPasswordReset(email: String): AuthResult
}
