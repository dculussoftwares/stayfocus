package com.dculus.stayfocused.core.blocking.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.dculus.stayfocused.core.blocking.ForegroundAppTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Screen-off and clock/zone broadcasts, registered on the application context while the engine runs (both engine
 * modes need them). [EngineLifecycle] registers on the first owner and unregisters on the last.
 */
@Singleton
internal class ScreenAndClockReceivers
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val tracker: ForegroundAppTracker,
        private val engine: Provider<BlockingEngine>,
    ) : ReceiverControl {
        private var registered = false

        private val screenOff =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (intent.action == Intent.ACTION_SCREEN_OFF) tracker.onScreenOff()
                }
            }
        private val clock =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    // Schedules are local-time ranges: a changed clock or zone moves every boundary.
                    engine.get().onClockChanged()
                }
            }

        @Synchronized
        override fun register() {
            if (registered) return
            context.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
            context.registerReceiver(
                clock,
                IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                },
            )
            registered = true
        }

        @Synchronized
        override fun unregister() {
            if (!registered) return
            runCatching { context.unregisterReceiver(screenOff) }
            runCatching { context.unregisterReceiver(clock) }
            registered = false
        }
    }
