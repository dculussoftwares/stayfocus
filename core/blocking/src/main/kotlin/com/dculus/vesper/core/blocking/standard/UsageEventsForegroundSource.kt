package com.dculus.vesper.core.blocking.standard

import com.dculus.vesper.core.blocking.BlockingLog
import com.dculus.vesper.core.blocking.ForegroundAppTracker
import com.dculus.vesper.core.blocking.engine.EngineDispatcher
import com.dculus.vesper.core.blocking.engine.EngineHealthMonitor
import com.dculus.vesper.core.usage.ResumeEvent
import com.dculus.vesper.core.usage.ResumeEventReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the engine has anything to enforce; the poller sleeps while it has not. */
interface ActiveRules {
    val hasActiveRules: StateFlow<Boolean>
}

/** Whether the screen is on (`PowerManager.isInteractive`, `ACTION_SCREEN_ON/OFF`). */
fun interface InteractiveScreen {
    fun interactive(): Flow<Boolean>
}

/**
 * Standard engine: tells [ForegroundAppTracker] which app is in front by polling Usage Stats events (no
 * Accessibility). Polls every [POLL_MS] only while the screen is on and the engine has rules; otherwise sleeps.
 *
 * Each poll reads from [OVERLAP_MS] before the end of the previous one (the system can write an event slightly
 * late) and drops what was already delivered. With split screen or picture-in-picture the newest resumed event
 * wins, the same as the accessibility source.
 */
@Singleton
class UsageEventsForegroundSource
    @Inject
    internal constructor(
        private val reader: ResumeEventReader,
        private val tracker: ForegroundAppTracker,
        private val health: EngineHealthMonitor,
        private val rules: ActiveRules,
        private val screen: InteractiveScreen,
        private val clock: Clock,
        @EngineDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        private val lock = Any()
        private var scope: CoroutineScope? = null

        /** Starts polling; [onAccessLost] runs once when Usage access turns out to be revoked (polling then stops). */
        fun start(onAccessLost: () -> Unit) {
            synchronized(lock) {
                if (scope != null) return
                val s = CoroutineScope(SupervisorJob() + dispatcher)
                scope = s
                s.launch { supervise(onAccessLost) }
            }
        }

        fun stop() {
            synchronized(lock) {
                scope?.cancel()
                scope = null
            }
        }

        private suspend fun supervise(onAccessLost: () -> Unit) {
            combine(screen.interactive(), rules.hasActiveRules) { on, ruled -> on to ruled }
                .distinctUntilChanged()
                .collectLatest { (on, ruled) ->
                    if (!on) tracker.onScreenOff()
                    if (on && ruled) {
                        try {
                            poll()
                        } catch (e: SecurityException) {
                            BlockingLog.log("UsageSource", "usage access lost: ${e.message}")
                            tracker.onScreenOff()
                            onAccessLost()
                            stop()
                        }
                    }
                }
        }

        private suspend fun poll() {
            var windowEnd = clock.millis()
            val last = Delivered()
            reader.resumedSince(windowEnd - INITIAL_LOOKBACK_MS, windowEnd).lastOrNull()?.let { last.deliver(it) }
            while (true) {
                delay(POLL_MS)
                val now = clock.millis()
                reader
                    .resumedSince(windowEnd - OVERLAP_MS, now)
                    .forEach { last.deliver(it) }
                windowEnd = now
            }
        }

        /** Hands events to the tracker once each: older than the newest delivered one, or the same one, are skipped. */
        private inner class Delivered {
            private var time = Long.MIN_VALUE
            private val atTime = HashSet<ResumeEvent>()

            fun deliver(event: ResumeEvent) {
                if (event.timeMillis < time) return
                if (event.timeMillis > time) {
                    time = event.timeMillis
                    atTime.clear()
                }
                if (!atTime.add(event)) return
                health.onEvent()
                BlockingLog.log("UsageSource", "${event.pkg}/${event.className}")
                tracker.onWindowStateChanged(event.pkg, event.className)
            }
        }

        companion object {
            const val POLL_MS = 500L
            const val OVERLAP_MS = 2_000L
            const val INITIAL_LOOKBACK_MS = 10 * 60 * 1_000L
        }
    }
