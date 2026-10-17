package com.dculus.stayfocused.core.sync

import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.model.Block
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The parent's view of a child phone's blocks and apps: finds the account the device lives under. */
@OptIn(ExperimentalCoroutinesApi::class)
class ParentDeviceBlocks
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val repository: DeviceBlocksRepository,
    ) {
        /**
         * Queues [block] for the child phone [deviceId]. With [preserveEnabled] the phone's stored on/off switch
         * stays as it is (an edit must not undo a switch flipped elsewhere). [onRejected] is called if the server
         * refuses it later (for example because the phone was unlinked meanwhile). Throws when no parent account is
         * signed in, or when the block covers more than [MAX_APPS] apps, so the save fails instead of never arriving.
         */
        suspend fun push(
            deviceId: String,
            block: Block,
            preserveEnabled: Boolean = false,
            onRejected: () -> Unit = {},
        ) {
            require(block.apps.size <= MAX_APPS) { "A block can cover at most $MAX_APPS apps" }
            repository.push(target(deviceId), block, preserveEnabled, onRejected)
        }

        /** Queues the removal of [blockId] from the child phone [deviceId]. Throws when nobody is signed in. */
        suspend fun remove(
            deviceId: String,
            blockId: String,
            onRejected: () -> Unit = {},
        ) = repository.remove(target(deviceId), blockId, onRejected)

        /** The launchable apps the child phone [deviceId] reported; empty until it has, or without an account. */
        fun observeApps(deviceId: String): Flow<List<AppInfo>> =
            auth.currentUser
                .map { it?.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    if (uid == null) flowOf(emptyList()) else repository.observeApps(DeviceTarget(uid, deviceId))
                }

        private suspend fun target(deviceId: String): DeviceTarget {
            val uid = checkNotNull(auth.currentUser.first()?.uid) { "No parent account is signed in" }
            return DeviceTarget(uid, deviceId)
        }
    }
