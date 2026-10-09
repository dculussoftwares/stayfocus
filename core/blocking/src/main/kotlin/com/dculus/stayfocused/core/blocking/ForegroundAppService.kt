package com.dculus.stayfocused.core.blocking

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import com.dculus.stayfocused.core.blocking.engine.BlockingEngine
import com.dculus.stayfocused.core.blocking.screen.BlockScreenLauncher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Reports which app is in front. Listens to `TYPE_WINDOW_STATE_CHANGED` only and reads no window content. */
@AndroidEntryPoint
class ForegroundAppService : AccessibilityService() {
    @Inject lateinit var tracker: ForegroundAppTracker

    @Inject lateinit var launcher: BlockScreenLauncher

    @Inject lateinit var engine: BlockingEngine

    private val screenOffReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) tracker.onScreenOff()
            }
        }
    private val clockReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                // Schedules are local-time ranges: a changed clock or zone moves every boundary.
                engine.onClockChanged()
            }
        }
    private var receiverRegistered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        launcher.attach(this)
        engine.start()
        if (!receiverRegistered) {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            registerReceiver(
                clockReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                },
            )
            receiverRegistered = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        tracker.onWindowStateChanged(event.packageName?.toString(), event.className?.toString())
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        engine.stop()
        tracker.onScreenOff()
        launcher.detach()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        engine.stop()
        if (receiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            unregisterReceiver(clockReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }
}
