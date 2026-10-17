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
import com.dculus.stayfocused.core.sync.DeviceTarget
import com.dculus.stayfocused.kids.MainActivity
import com.dculus.stayfocused.kids.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground service that keeps the persistent "Managed by {parent}'s phone" notification up for as long as this phone
 * is linked (Play stalkerware policy: a visible, unique-icon notification whenever monitoring runs).
 */
@AndroidEntryPoint
class MonitoringService : Service() {
    @Inject
    internal lateinit var linkStore: ParentLinkStore

    @Inject
    internal lateinit var targetStore: ReportTargetStore

    @Inject
    internal lateinit var reporter: ChildReporter

    @Inject
    internal lateinit var commandListener: CommandListener

    @Inject
    internal lateinit var parentRules: ParentRulesMirror

    @Inject
    internal lateinit var linkWatcher: LinkWatcher

    @Inject
    internal lateinit var linkReset: LinkReset

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var reporting: Job? = null
    private var runningTarget: DeviceTarget? = null

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
            reporting?.cancel()
            return START_NOT_STICKY
        }
        // (Re)start the reports: the service is started again after linking, when the target becomes known.
        val previous = reporting
        val target = targetStore.target
        reporting =
            if (target == null) {
                previous?.cancel()
                runningTarget = null
                null
            } else if (target == runningTarget && previous?.isActive == true) {
                // Started again only to repost the notification: keep the running reports.
                previous
            } else {
                runningTarget = target
                // Stop the old run right now, even if an intermediate replacement never gets to run.
                previous?.cancel()
                // ATOMIC: the body runs even if a later restart cancels this job before it starts, so the chain of
                // joins keeps every goodbye ("offline") ahead of the next run's first "online".
                scope.launch(start = CoroutineStart.ATOMIC) {
                    withContext(NonCancellable) { previous?.join() }
                    coroutineScope {
                        // The parent may end the link while the app is closed: stop reporting and monitoring then.
                        launch {
                            linkWatcher.untilGone(target)
                            // The parent's blocks, locks and focus end with the link.
                            if (targetStore.target == target) withContext(NonCancellable) { parentRules.clear() }
                            linkReset.reset(target)
                        }
                        launch { reporter.run(target) }
                        launch { commandListener.run(target) }
                        launch { parentRules.run(target) }
                    }
                }
            }
        return START_STICKY
    }

    override fun onDestroy() {
        // Cancelling lets the status reporter say "offline" on its way out.
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "managed_by_parent"
        const val NOTIFICATION_ID = 1001

        /** Sent by the notification's delete intent: Android 14+ lets the user swipe it away, so post it again. */
        const val ACTION_REPOST = "com.dculus.stayfocused.kids.action.REPOST_MONITORING"

        /** False when the user turned notifications off, or blocked this channel, so the notice would be invisible. */
        internal fun noticeVisible(context: Context): Boolean {
            val manager = NotificationManagerCompat.from(context)
            var visible = manager.areNotificationsEnabled()
            if (visible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ensureChannel(context)
                visible = manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
            }
            return visible
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
