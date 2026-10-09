package com.dculus.stayfocused.core.sync

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory stand-in that succeeds unless [failWith] is set. Bound until the Firebase implementations (M6-03, M6-04)
 * replace it; it keeps nothing, so no account actually exists.
 */
@Singleton
class FakeAuthRepository
    @Inject
    constructor() : AuthRepository {
        @Volatile var failWith: AuthFailure? = null

        private fun result(): AuthResult = failWith?.let { AuthResult.Failure(it) } ?: AuthResult.Success

        override suspend fun signInWithEmail(
            email: String,
            password: String,
        ) = result()

        override suspend fun createAccount(
            email: String,
            password: String,
        ) = result()

        override suspend fun signInWithGoogle() = result()

        override suspend fun sendPasswordReset(email: String) = result()
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthModule {
    @Binds
    abstract fun auth(impl: FakeAuthRepository): AuthRepository
}
