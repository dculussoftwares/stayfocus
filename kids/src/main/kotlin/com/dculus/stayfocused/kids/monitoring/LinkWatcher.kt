package com.dculus.stayfocused.kids.monitoring

import com.dculus.stayfocused.core.sync.DeviceLinkState
import com.dculus.stayfocused.core.sync.DeviceTarget
import com.dculus.stayfocused.core.sync.KidsHomeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.transformWhile
import timber.log.Timber
import javax.inject.Inject

/**
 * Notices that the parent ended the link (unlinked this phone or deleted their account). Runs wherever the link is
 * alive: the Home screen and the monitoring service, so a phone whose app is closed still stops monitoring.
 */
class LinkWatcher
    @Inject
    internal constructor(
        private val home: KidsHomeRepository,
    ) {
        internal companion object {
            /** A refused read looks the same as a deleted device, but also like a rejected App Check token. */
            const val REFUSALS_TO_CONFIRM = 3
            const val RECHECK_MILLIS = 20_000L
            const val RETRY_MILLIS = 30_000L
        }

        /**
         * Returns when the server has confirmed the device is gone: it says so itself, or it keeps refusing the read
         * [REFUSALS_TO_CONFIRM] times in a row. Suspends while the phone is linked; connection problems are retried.
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun untilGone(target: DeviceTarget) {
            var refusals = 0
            while (true) {
                var last: DeviceLinkState? = null
                try {
                    home
                        .observeLink(target)
                        .transformWhile { state ->
                            emit(state)
                            state == DeviceLinkState.Linked
                        }.collect { state ->
                            last = state
                            if (state == DeviceLinkState.Linked) refusals = 0
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Watching the link failed; will retry")
                    // The refusals must be consecutive: a failed look in between breaks the run.
                    refusals = 0
                    delay(RETRY_MILLIS)
                    continue
                }
                when (last) {
                    DeviceLinkState.Gone -> {
                        return
                    }

                    DeviceLinkState.Unreadable -> {
                        refusals++
                        if (refusals >= REFUSALS_TO_CONFIRM) return
                        delay(RECHECK_MILLIS)
                    }

                    // Nothing to watch (no Firebase in this build): stay linked.
                    else -> {
                        awaitCancellation()
                    }
                }
            }
        }
    }

/** Ends what the parent set on this phone: its blocks, app locks and focus session. */
fun interface RulesCleaner {
    fun clear()
}

/** Forgets the parent and stops monitoring. The name goes first: the service treats it as "linked". */
class LinkReset
    @Inject
    internal constructor(
        private val linkStore: ParentLinkStore,
        private val monitoring: MonitoringControl,
        private val rules: RulesCleaner,
    ) {
        /**
         * With a [target], only resets while the stored link is still that device: a watcher that finishes late must
         * not erase a link made since. Without one, resets whatever is stored.
         */
        fun reset(target: DeviceTarget? = null) {
            if (target != null && (linkStore.parentUid != target.parentUid || linkStore.deviceId != target.deviceId)) {
                return
            }
            linkStore.parentName = null
            linkStore.parentUid = null
            linkStore.deviceId = null
            // Whoever ended the link (this phone, the parent, the service), the parent's rules end with it.
            rules.clear()
            monitoring.sync()
        }
    }
