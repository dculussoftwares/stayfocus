package com.dculus.stayfocused.kids.monitoring

import com.dculus.stayfocused.core.data.repository.BlockRepository
import com.dculus.stayfocused.core.data.repository.LockedAppsRepository
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.sync.DeviceAuth
import com.dculus.stayfocused.core.sync.DeviceBlocksRepository
import com.dculus.stayfocused.core.sync.DeviceTarget
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the blocks this phone enforces equal to the ones the parent set (`devices/{id}/blocks`). They are written to
 * the local database, which the blocking engine reads, so enforcement carries on offline and after a restart: the
 * engine never talks to the network. The parent's blocks always apply to this phone, so they are stored as
 * [BlockTarget.ThisPhone].
 */
class ParentRulesMirror
    @Inject
    internal constructor(
        private val remote: DeviceBlocksRepository,
        private val deviceAuth: DeviceAuth,
        private val blocks: BlockRepository,
        private val lockedApps: LockedAppsRepository,
        private val settings: SettingsRepository,
    ) {
        /** One application of rules at a time: a listener update and a `SYNC_BLOCKS` fetch never interleave. */
        private val lock = Mutex()

        /** Runs until cancelled; re-attaches the Firestore listener after a pause when it is denied or lost. */
        @Suppress("TooGenericExceptionCaught")
        suspend fun run(target: DeviceTarget) {
            while (deviceAuth.ensureSignedIn() == null) {
                Timber.w("Not mirroring blocks yet: no anonymous sign-in")
                delay(CommandCadence.RETRY_MILLIS)
            }
            while (true) {
                try {
                    remote.observeBlocks(target).collect { apply(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Block listener lost; attaching again")
                }
                delay(CommandCadence.RETRY_MILLIS)
            }
        }

        /** Asks the server for the blocks right now (`SYNC_BLOCKS`). Throws when it cannot be reached. */
        suspend fun syncNow(target: DeviceTarget) = apply(remote.fetchBlocks(target))

        /** Makes the local blocks equal to [rules]: stores new and changed ones, drops the ones the parent removed. */
        internal suspend fun apply(rules: List<Block>) = lock.withLock { applyLocked(rules) }

        private suspend fun applyLocked(rules: List<Block>) {
            val local = blocks.observeByTarget(BlockTarget.ThisPhone).first().associateBy { it.id }
            val wanted = rules.associateBy { it.id }
            for (rule in wanted.values) {
                // Unchanged rules are not written again: rewriting could disturb the "use, then rest" progress.
                if (local[rule.id] != rule) blocks.upsert(rule)
            }
            for (id in local.keys - wanted.keys) blocks.delete(id)
        }

        /** The parent link ended: stop enforcing everything the parent set. */
        suspend fun clear() =
            lock.withLock {
                applyLocked(emptyList())
                lockedApps
                    .observeByTarget(BlockTarget.ThisPhone)
                    .first()
                    .forEach { lockedApps.unlock(it.pkg, BlockTarget.ThisPhone) }
                settings.setFocusSession(null)
            }
    }

/**
 * Clears the mirrored rules when the link is reset. Runs on its own scope, because the caller (a screen or the
 * service) may be gone right after the reset. Skipped if a new link was made meanwhile: its rules are the ones to keep.
 */
@Singleton
internal class ParentRulesCleaner
    @Inject
    constructor(
        private val mirror: ParentRulesMirror,
        private val linkStore: ParentLinkStore,
    ) : RulesCleaner {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Suppress("TooGenericExceptionCaught")
        override fun clear() {
            scope.launch {
                try {
                    if (linkStore.parentUid == null) mirror.clear()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Could not clear the parent's rules")
                }
            }
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ParentRulesModule {
    @Binds
    abstract fun cleaner(impl: ParentRulesCleaner): RulesCleaner
}
