package com.dculus.vesper.core.blocking.standard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.dculus.vesper.core.blocking.BlockingLog
import com.dculus.vesper.core.blocking.ForegroundAppTracker
import com.dculus.vesper.core.blocking.R
import com.dculus.vesper.core.blocking.engine.EngineLifecycle
import com.dculus.vesper.core.blocking.engine.EngineMode
import com.dculus.vesper.core.blocking.engine.EngineModeArbiter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service of the standard engine (blocking without Accessibility): keeps the process alive and the
 * [UsageEventsForegroundSource] polling. Declared in the phone app's manifest only (`specialUse`). It stops itself
 * as soon as the mode is no longer [EngineMode.STANDARD]: Accessibility took over, or a permission was revoked.
 */
@AndroidEntryPoint
class BlockingService : Service() {
    @Inject internal lateinit var lifecycle: EngineLifecycle

    @Inject internal lateinit var arbiter: EngineModeArbiter

    @Inject internal lateinit var source: UsageEventsForegroundSource

    @Inject internal lateinit var tracker: ForegroundAppTracker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        BlockingLog.init(applicationContext)
        ensureChannel(this)
        // Always promote first: a service started with startForegroundService must call startForeground in time.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (arbiter.refresh() != EngineMode.STANDARD) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            // A foreground app from before the service started must not be trusted.
            tracker.onScreenOff()
            lifecycle.acquire(EngineLifecycle.Owner.STANDARD)
            source.start(onAccessLost = { arbiter.refresh() })
            scope.launch {
                arbiter.mode.collect { mode ->
                    if (mode != EngineMode.STANDARD) stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (running) {
            running = false
            source.stop()
            lifecycle.release(EngineLifecycle.Owner.STANDARD)
        }
        scope.cancel()
        arbiter.refresh()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "blocking_engine"
        const val NOTIFICATION_ID = 1002

        internal fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.blocking_service_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        internal fun buildNotification(context: Context): Notification {
            val open =
                context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                    PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
                }
            return NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_blocking_service)
                .setContentTitle(context.getString(R.string.blocking_service_title))
                .setContentText(context.getString(R.string.blocking_service_text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(open)
                .build()
        }
    }
}
