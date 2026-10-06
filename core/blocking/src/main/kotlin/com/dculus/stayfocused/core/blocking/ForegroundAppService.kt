package com.dculus.stayfocused.core.blocking

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Reports which app is in front. Listens to `TYPE_WINDOW_STATE_CHANGED` only and reads no window content. */
@AndroidEntryPoint
class ForegroundAppService : AccessibilityService() {
    @Inject lateinit var tracker: ForegroundAppTracker

    private val screenOffReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) tracker.onScreenOff()
            }
        }
    private var receiverRegistered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!receiverRegistered) {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            receiverRegistered = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        tracker.onWindowStateChanged(event.packageName?.toString(), event.className?.toString())
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        tracker.onScreenOff()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }
}
