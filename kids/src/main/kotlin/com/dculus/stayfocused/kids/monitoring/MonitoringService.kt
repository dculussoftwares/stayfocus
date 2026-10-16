package com.dculus.stayfocused.kids.monitoring

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
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.dculus.stayfocused.kids.MainActivity
import com.dculus.stayfocused.kids.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that keeps the persistent "Managed by {parent}'s phone" notification up for as long as this phone
 * is linked (Play stalkerware policy: a visible, unique-icon notification whenever monitoring runs).
 */
@AndroidEntryPoint
class MonitoringService : Service() {
    @Inject
    internal lateinit var linkStore: ParentLinkStore

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val parent = linkStore.parentName
        ensureChannel(this)
        // Always promote first: a service started with startForegroundService must call startForeground in time.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(this, parent.orEmpty()),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (!shouldMonitor(parent, noticeVisible(this))) {
            // Not linked, or notifications are off (the notice would be invisible): stop rather than run unseen.
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    companion object {
        const val CHANNEL_ID = "managed_by_parent"
        const val NOTIFICATION_ID = 1001

        /** Sent by the notification's delete intent: Android 14+ lets the user swipe it away, so post it again. */
        const val ACTION_REPOST = "com.dculus.stayfocused.kids.action.REPOST_MONITORING"

        /** False when the user turned notifications off, or blocked this channel, so the notice would be invisible. */
        internal fun noticeVisible(context: Context): Boolean {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
            ensureChannel(context)
            val importance = manager.getNotificationChannel(CHANNEL_ID)?.importance
            return importance != NotificationManager.IMPORTANCE_NONE
        }

        internal fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.kids_monitoring_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.kids_monitoring_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        internal fun buildNotification(
            context: Context,
            parentName: String,
        ): Notification {
            val open =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val repost =
                PendingIntent.getForegroundService(
                    context,
                    1,
                    Intent(context, MonitoringService::class.java).setAction(ACTION_REPOST),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            return NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_monitoring)
                .setContentTitle(context.getString(R.string.kids_managed_by, parentName))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(open)
                .setDeleteIntent(repost)
                .build()
        }
    }
}
