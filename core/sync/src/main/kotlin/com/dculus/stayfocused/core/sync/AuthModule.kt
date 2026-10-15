package com.dculus.stayfocused.core.sync

import com.dculus.stayfocused.core.data.repository.LocalDataWiper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
