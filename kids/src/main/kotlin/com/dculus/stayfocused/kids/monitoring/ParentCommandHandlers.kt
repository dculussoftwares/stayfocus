package com.dculus.stayfocused.kids.monitoring

import com.dculus.stayfocused.core.data.repository.LockedAppsRepository
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.FocusSession
import com.dculus.stayfocused.core.sync.CommandHandler
import com.dculus.stayfocused.core.sync.CommandType
import com.dculus.stayfocused.core.sync.DeviceBlocksRepository
import com.dculus.stayfocused.core.sync.DeviceTarget
import com.dculus.stayfocused.core.sync.RemoteCommand
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private fun ReportTargetStore.require(): DeviceTarget = checkNotNull(target) { "This phone is not linked" }

/** `BLOCK_APP`: locks the app until the parent unlocks it. Locking a locked app changes nothing. */
class BlockAppHandler
    @Inject
    internal constructor(
        private val lockedApps: LockedAppsRepository,
        private val mirror: ParentRulesMirror,
    ) : CommandHandler {
        override val type = CommandType.BLOCK_APP

        override suspend fun handle(command: RemoteCommand) =
            mirror.whileLinked { lockedApps.lock((command as RemoteCommand.BlockApp).pkg, BlockTarget.ThisPhone) }
    }

/** `UNBLOCK_APP`: removes the lock. */
class UnblockAppHandler
    @Inject
    internal constructor(
        private val lockedApps: LockedAppsRepository,
        private val mirror: ParentRulesMirror,
    ) : CommandHandler {
        override val type = CommandType.UNBLOCK_APP

        override suspend fun handle(command: RemoteCommand) =
            mirror.whileLinked { lockedApps.unlock((command as RemoteCommand.UnblockApp).pkg, BlockTarget.ThisPhone) }
    }

/**
 * `START_FOCUS`: everything except the always-allowed apps is blocked until the end, which is also mirrored to the
 * device doc so the parent sees it.
 */
class StartFocusHandler
    @Inject
    internal constructor(
        private val settings: SettingsRepository,
        private val remote: DeviceBlocksRepository,
        private val targets: ReportTargetStore,
        private val clock: Clock,
        private val mirror: ParentRulesMirror,
    ) : CommandHandler {
        override val type = CommandType.START_FOCUS

        override suspend fun handle(command: RemoteCommand) =
            mirror.whileLinked {
                val mins = (command as RemoteCommand.StartFocus).mins
                val target = targets.require()
                val now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
                val session = FocusSession(now, now.plus(Duration.ofMinutes(mins.toLong())), mins)
                settings.setFocusSession(session)
                remote.setFocusEndsAt(target, session.endsAt)
            }
    }

/** `STOP_FOCUS`: ends the session on the phone and on the device doc. */
class StopFocusHandler
    @Inject
    internal constructor(
        private val settings: SettingsRepository,
        private val remote: DeviceBlocksRepository,
        private val targets: ReportTargetStore,
        private val mirror: ParentRulesMirror,
    ) : CommandHandler {
        override val type = CommandType.STOP_FOCUS

        override suspend fun handle(command: RemoteCommand) =
            mirror.whileLinked {
                val target = targets.require()
                settings.setFocusSession(null)
                remote.setFocusEndsAt(target, null)
            }
    }

/** `SYNC_BLOCKS`: fetches the blocks from the server now instead of waiting for the listener. */
class SyncBlocksHandler
    @Inject
    internal constructor(
        private val mirror: ParentRulesMirror,
        private val targets: ReportTargetStore,
    ) : CommandHandler {
        override val type = CommandType.SYNC_BLOCKS

        override suspend fun handle(command: RemoteCommand) = mirror.syncNow(targets.require())
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ParentCommandHandlersModule {
    @Binds
    @IntoSet
    abstract fun blockApp(impl: BlockAppHandler): CommandHandler

    @Binds
    @IntoSet
    abstract fun unblockApp(impl: UnblockAppHandler): CommandHandler

    @Binds
    @IntoSet
    abstract fun startFocus(impl: StartFocusHandler): CommandHandler

    @Binds
    @IntoSet
    abstract fun stopFocus(impl: StopFocusHandler): CommandHandler

    @Binds
    @IntoSet
    abstract fun syncBlocks(impl: SyncBlocksHandler): CommandHandler
}
