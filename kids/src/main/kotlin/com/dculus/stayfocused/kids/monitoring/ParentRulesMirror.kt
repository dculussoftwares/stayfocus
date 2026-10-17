package com.dculus.stayfocused.kids.monitoring

import android.content.Context
import com.dculus.stayfocused.core.blocking.engine.TemporaryAllowances
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
import dagger.hilt.android.qualifiers.ApplicationContext
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
        private val links: ParentLinkStore,
    ) {
        /**
         * Everything that changes the parent's rules on this phone runs under this lock and only while linked, so
         * an unlink ([clear]) is final: a late snapshot, sync or command cannot bring rules back.
         */
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
                    remote.observeBlocks(target).collect { apply(it, target) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Block listener lost; attaching again")
                }
                delay(CommandCadence.RETRY_MILLIS)
            }
        }

        /** Asks the server for the blocks right now (`SYNC_BLOCKS`). Throws when it cannot be reached. */
        suspend fun syncNow(target: DeviceTarget) {
            val rules = remote.fetchBlocks(target)
            whileLinked(target) { applyLocked(rules) }
        }

        /**
         * Runs [action] exclusively and only while a parent is linked; throws otherwise, so a command that arrives
         * after the unlink ends as failed instead of locking an app for nobody.
         */
        internal suspend fun <T> whileLinked(
            target: DeviceTarget? = null,
            action: suspend () -> T,
        ): T =
            lock.withLock {
                check(isLinked(target)) { "This phone is not linked" }
                action()
            }

        /** Linked at all, or, with a [target], still linked to exactly that device (not to one linked since). */
        private fun isLinked(target: DeviceTarget?): Boolean =
            links.parentUid != null &&
                (target == null || (links.parentUid == target.parentUid && links.deviceId == target.deviceId))

        /** Makes the local blocks equal to [rules]: stores new and changed ones, drops the ones the parent removed. */
        internal suspend fun apply(
            rules: List<Block>,
            target: DeviceTarget? = null,
        ) = lock.withLock { if (isLinked(target)) applyLocked(rules) }

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
                // A phone linked again since keeps (or is about to receive) the new parent's blocks: only the old
                // parent's locks and focus are still owed.
                if (links.parentUid == null) applyLocked(emptyList())
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
        private val allowances: TemporaryAllowances,
        private val requests: PendingRequestStore,
        @ApplicationContext context: Context,
    ) : RulesCleaner {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val prefs = context.getSharedPreferences("kids_rules_cleanup", Context.MODE_PRIVATE)

        /** Marks the clean-up as owed (surviving a process death) and runs it. */
        override fun clear() {
            prefs.edit().putBoolean(KEY_OWED, true).commit()
            run()
        }

        /** App start: finishes a clean-up the process died before completing. */
        fun resume() {
            if (prefs.getBoolean(KEY_OWED, false)) run()
        }

        @Suppress("TooGenericExceptionCaught")
        private fun run() {
            scope.launch {
                try {
                    mirror.clear()
                    // Approvals and open requests belong to the parent that is gone: a new parent starts clean.
                    allowances.clear()
                    requests.clear()
                    prefs.edit().putBoolean(KEY_OWED, false).apply()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Could not clear the parent's rules; will retry at the next start")
                }
            }
        }

        private companion object {
            const val KEY_OWED = "owed"
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ParentRulesModule {
    @Binds
    abstract fun cleaner(impl: ParentRulesCleaner): RulesCleaner
}
