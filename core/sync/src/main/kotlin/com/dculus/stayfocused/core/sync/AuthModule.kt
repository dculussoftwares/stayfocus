package com.dculus.stayfocused.core.sync

import com.dculus.stayfocused.core.data.repository.LinkedDevicesRepository
import com.dculus.stayfocused.core.data.repository.LocalDataWiper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions") // one provider per Firebase-backed repository
internal object AuthModule {
    /** Firebase when the build has `google-services.json`; the in-memory fake otherwise (blocking works either way). */
    @Provides
    @Singleton
    fun auth(
        availability: FirebaseAvailability,
        fake: Provider<FakeAuthRepository>,
        pushTokens: Provider<PushTokenRepository>,
    ): AuthRepository =
        if (availability.isConfigured) {
            FirebaseAuthRepository(
                FirebaseAuth.getInstance(),
                FirebaseFirestore.getInstance(),
                beforeSignOut = { uid -> pushTokens.get().unregister(uid) },
            )
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

    /** Parent to child commands (`devices/{id}/commands`); none without Firebase. */
    @Provides
    @Singleton
    fun commands(availability: FirebaseAvailability): CommandRepository =
        if (availability.isConfigured) {
            FirebaseCommandRepository(
                FirebaseFirestore.getInstance(),
            )
        } else {
            NoCommandRepository
        }

    /** Blocks, focus end and app list of a linked phone (`devices/{id}/blocks`, `apps`); none without Firebase. */
    @Provides
    @Singleton
    fun deviceBlocks(availability: FirebaseAvailability): DeviceBlocksRepository =
        if (availability.isConfigured) {
            FirebaseDeviceBlocksRepository(FirebaseFirestore.getInstance())
        } else {
            NoDeviceBlocksRepository
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

    /** The Kids home's read-only view of the parent's tree (link state and rules); none without Firebase. */
    @Provides
    @Singleton
    fun kidsHome(availability: FirebaseAvailability): KidsHomeRepository =
        if (availability.isConfigured) {
            FirebaseKidsHomeRepository(
                FirebaseFirestore.getInstance(),
            )
        } else {
            NoKidsHomeRepository
        }

    /** The parent's Remote screen: a child phone's apps, usage, blocks and lock state; none without Firebase. */
    @Provides
    @Singleton
    fun remoteDevice(availability: FirebaseAvailability): RemoteDeviceRepository =
        if (availability.isConfigured) {
            FirebaseRemoteDeviceRepository(FirebaseFirestore.getInstance())
        } else {
            NoRemoteDeviceRepository
        }

    /** The Kids phone's "ask for more time" requests (`devices/{id}/requests`); none without Firebase. */
    @Provides
    @Singleton
    fun unlockRequests(availability: FirebaseAvailability): UnlockRequestRepository =
        if (availability.isConfigured) {
            FirebaseUnlockRequestRepository(FirebaseFirestore.getInstance())
        } else {
            NoUnlockRequestRepository
        }

    /** The parent's answer to a request: the decision and the command for the child; none without Firebase. */
    @Provides
    @Singleton
    fun requestDecisions(availability: FirebaseAvailability): RequestDecisionRepository =
        if (availability.isConfigured) {
            FirebaseRequestDecisionRepository(FirebaseFirestore.getInstance())
        } else {
            NoRequestDecisionRepository
        }

    /** The parent's FCM token (`users/{uid}/fcmTokens`); none without Firebase. */
    @Provides
    @Singleton
    fun pushTokens(availability: FirebaseAvailability): PushTokenRepository =
        if (availability.isConfigured) {
            FirebasePushTokenRepository(FirebaseFirestore.getInstance(), FirebaseMessaging.getInstance())
        } else {
            NoPushTokenRepository
        }

    /** `unlinkDevice`, for the Kids home and the parent's Remote screen; none without Firebase. */
    @Provides
    @Singleton
    fun deviceUnlink(availability: FirebaseAvailability): DeviceUnlinkRepository =
        if (availability.isConfigured) {
            FirebaseDeviceUnlinkRepository(FirebaseFunctions.getInstance(FUNCTIONS_REGION))
        } else {
            NoDeviceUnlinkRepository
        }

    /** The parent's linked phones (`users/{uid}/devices`); none without Firebase. */
    @Provides
    @Singleton
    fun linkedDevices(
        availability: FirebaseAvailability,
        auth: Provider<AuthRepository>,
        clock: Clock,
    ): LinkedDevicesRepository =
        if (availability.isConfigured) {
            FirebaseLinkedDevicesRepository(
                FirebaseFirestore.getInstance(),
                auth.get(),
                clock,
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        } else {
            NoLinkedDevicesRepository
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
