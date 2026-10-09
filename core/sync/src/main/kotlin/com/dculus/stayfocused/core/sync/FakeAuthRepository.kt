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

        private val recorded = java.util.concurrent.CopyOnWriteArrayList<String>()

        /** Every call so far, as `method` or `method:email:password`, for tests. */
        val calls: List<String> get() = recorded.toList()

        private fun result(): AuthResult = failWith?.let { AuthResult.Failure(it) } ?: AuthResult.Success

        override suspend fun signInWithEmail(
            email: String,
            password: String,
        ): AuthResult {
            recorded += "signInWithEmail:$email:$password"
            return result()
        }

        override suspend fun createAccount(
            email: String,
            password: String,
        ): AuthResult {
            recorded += "createAccount:$email:$password"
            return result()
        }

        override suspend fun signInWithGoogle(): AuthResult {
            recorded += "signInWithGoogle"
            return result()
        }

        override suspend fun sendPasswordReset(email: String): AuthResult {
            recorded += "sendPasswordReset:$email"
            return result()
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthModule {
    @Binds
    abstract fun auth(impl: FakeAuthRepository): AuthRepository
}
