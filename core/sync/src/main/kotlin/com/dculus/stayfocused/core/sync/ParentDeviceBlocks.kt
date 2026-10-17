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
        /** Queues [block] for the child phone [deviceId]. Throws when no parent account is signed in. */
        suspend fun push(
            deviceId: String,
            block: Block,
        ) = repository.push(target(deviceId), block)

        /** Queues the removal of [blockId] from the child phone [deviceId]. Throws when nobody is signed in. */
        suspend fun remove(
            deviceId: String,
            blockId: String,
        ) = repository.remove(target(deviceId), blockId)

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
