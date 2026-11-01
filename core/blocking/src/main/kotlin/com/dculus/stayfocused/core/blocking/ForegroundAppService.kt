package com.dculus.stayfocused.core.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.dculus.stayfocused.core.blocking.engine.EngineHealthMonitor
import com.dculus.stayfocused.core.blocking.engine.EngineLifecycle
import com.dculus.stayfocused.core.blocking.engine.EngineModeArbiter
import com.dculus.stayfocused.core.blocking.screen.BlockScreenLauncher
import com.dculus.stayfocused.core.blocking.standard.StandardEngineStarter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Reports which app is in front. Listens to `TYPE_WINDOW_STATE_CHANGED` only and reads no window content. */
@AndroidEntryPoint
class ForegroundAppService : AccessibilityService() {
    @Inject lateinit var tracker: ForegroundAppTracker

    @Inject lateinit var launcher: BlockScreenLauncher

    @Inject lateinit var lifecycle: EngineLifecycle

    @Inject lateinit var arbiter: EngineModeArbiter

    @Inject lateinit var starter: StandardEngineStarter

    @Inject lateinit var health: EngineHealthMonitor

    override fun onServiceConnected() {
        super.onServiceConnected()
        BlockingLog.init(applicationContext)
        launcher.attach(this)
        // A rebind (reboot, update, the user toggling the service) must not trust a foreground app from before it.
        tracker.onScreenOff()
        health.onServiceConnected()
        // Acquire first: the standard engine stops itself when the mode turns to ACCESSIBILITY.
        lifecycle.acquire(EngineLifecycle.Owner.ACCESSIBILITY)
        arbiter.refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        health.onEvent()
        BlockingLog.log("Event", "${event.packageName}/${event.className}")
        tracker.onWindowStateChanged(event.packageName?.toString(), event.className?.toString())
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutDown()
        tracker.onScreenOff()
        launcher.detach()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        health.onServiceUnbound()
        lifecycle.release(EngineLifecycle.Owner.ACCESSIBILITY)
        arbiter.refresh()
        // Accessibility was turned off: the standard engine takes over if it can (best effort from the background).
        starter.sync()
    }
}
