package com.dculus.stayfocused.core.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
}
