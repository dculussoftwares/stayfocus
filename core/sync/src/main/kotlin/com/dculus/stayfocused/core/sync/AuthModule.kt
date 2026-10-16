package com.dculus.stayfocused.core.sync

import com.dculus.stayfocused.core.data.repository.LocalDataWiper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AuthModule {
    /** Firebase when the build has `google-services.json`; the in-memory fake otherwise (blocking works either way). */
    @Provides
    @Singleton
    fun auth(
        availability: FirebaseAvailability,
        fake: Provider<FakeAuthRepository>,
    ): AuthRepository =
        if (availability.isConfigured) {
            FirebaseAuthRepository(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance())
        } else {
            fake.get()
        }

    /** The Kids phone's anonymous identity; none without Firebase. */
    @Provides
    @Singleton
    fun deviceAuth(availability: FirebaseAvailability): DeviceAuth =
        if (availability.isConfigured) FirebaseDeviceAuth(FirebaseAuth.getInstance()) else NoDeviceAuth

    /** The Kids phone's link tokens (`linkTokens/{token}`); none without Firebase. */
    @Provides
    @Singleton
    fun linkTokens(
        availability: FirebaseAvailability,
        clock: Clock,
    ): LinkTokenRepository =
        if (availability.isConfigured) {
            FirebaseLinkTokenRepository(FirebaseFirestore.getInstance(), clock)
        } else {
            NoLinkTokenRepository
        }

    /** The Kids phone's status, usage and app-list writes; none without Firebase. */
    @Provides
    @Singleton
    fun childStatus(availability: FirebaseAvailability): ChildStatusRepository =
        if (availability.isConfigured) {
            FirebaseChildStatusRepository(FirebaseFirestore.getInstance())
        } else {
            NoChildStatusRepository
        }

    /** The pairing handshake (`claimLinkToken`, `approveLink`); none without Firebase. */
    @Provides
    @Singleton
    fun linkHandshake(availability: FirebaseAvailability): LinkHandshakeRepository =
        if (availability.isConfigured) {
            FirebaseLinkHandshakeRepository(
                FirebaseFirestore.getInstance(),
                FirebaseFunctions.getInstance(FUNCTIONS_REGION),
            )
        } else {
            NoLinkHandshakeRepository
        }

    @Provides
    @Singleton
    fun accountDeletion(
        availability: FirebaseAvailability,
        fake: Provider<FakeAccountDeletionRepository>,
        wiper: Provider<LocalDataWiper>,
        auth: Provider<AuthRepository>,
    ): AccountDeletionRepository =
        if (availability.isConfigured) {
            FirebaseAccountDeletionRepository(
                FirebaseAuth.getInstance(),
                FirebaseFunctions.getInstance(FUNCTIONS_REGION),
                wiper.get(),
                beforeDelete = { (auth.get() as? FirebaseAuthRepository)?.pauseProfileRepair() },
                afterFailedDelete = { (auth.get() as? FirebaseAuthRepository)?.resumeProfileRepair() },
            )
        } else {
            fake.get()
        }
}
