package com.dculus.stayfocused.core.sync

import android.content.Context
import com.dculus.stayfocused.core.data.repository.LocalDataWiper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory stand-in used when the build has no Firebase configuration (and in tests). Every call succeeds unless
 * [staleSignIn], [reauthResult] or [deleteResult] says otherwise; a successful deletion signs the fake user out.
 */
@Singleton
class FakeAccountDeletionRepository
    @Inject
    constructor(
        private val auth: FakeAuthRepository,
        private val wiper: LocalDataWiper,
    ) : AccountDeletionRepository {
        @Volatile var staleSignIn: Boolean = false

        @Volatile var passwordAccount: Boolean = true

        @Volatile var reauthResult: AuthResult = AuthResult.Success

        @Volatile var deleteResult: DeleteAccountResult = DeleteAccountResult.Success

        @Volatile var deleteCalls: Int = 0
            private set

        override suspend fun isSignInFresh(): Boolean = !staleSignIn

        override suspend fun usesPassword(): Boolean = passwordAccount

        override suspend fun reauthenticateWithPassword(password: String): AuthResult = reauth()

        override suspend fun reauthenticateWithGoogle(activityContext: Context): AuthResult = reauth()

        private fun reauth(): AuthResult {
            val result = reauthResult
            if (result == AuthResult.Success) staleSignIn = false
            return result
        }

        override suspend fun deleteAccount(): DeleteAccountResult {
            deleteCalls++
            val result = if (staleSignIn) DeleteAccountResult.NeedsReauth else deleteResult
            if (result == DeleteAccountResult.Success) {
                auth.signOut()
                wiper.wipe()
            }
            return result
        }
    }
