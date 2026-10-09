package com.dculus.stayfocused.core.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory stand-in used when the build has no Firebase configuration (and in tests). It succeeds unless
 * [failWith] is set; it keeps nothing, so no account actually exists.
 */
@Singleton
class FakeAuthRepository
    @Inject
    constructor() : AuthRepository {
        @Volatile var failWith: AuthFailure? = null

        private val user = MutableStateFlow<AuthUser?>(null)
        override val currentUser: Flow<AuthUser?> = user

        private fun result(email: String? = null): AuthResult {
            val failure = failWith
            if (failure != null) return AuthResult.Failure(failure)
            user.value = AuthUser(uid = "fake", email = email, displayName = null)
            return AuthResult.Success
        }

        override suspend fun signInWithEmail(
            email: String,
            password: String,
        ): AuthResult = result(email)

        override suspend fun createAccount(
            email: String,
            password: String,
        ): AuthResult = result(email)

        override suspend fun signInWithGoogle(): AuthResult = result()

        override suspend fun sendPasswordReset(email: String): AuthResult =
            failWith?.let { AuthResult.Failure(it) } ?: AuthResult.Success

        override suspend fun signOut() {
            user.value = null
        }
    }
