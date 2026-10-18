package com.dculus.stayfocused.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dculus.stayfocused.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows the pushes as notifications, only when the notification permission is granted. A request carries buttons to
 * approve 15, 30 or 60 minutes, or to deny: [RequestActionReceiver] answers without opening the app.
 */
@Singleton
internal class PushNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @SuppressLint("MissingPermission") // checked in canNotify()
        fun show(message: PushMessage) {
            if (!canNotify()) return
            ensureChannel()
            val builder =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setAutoCancel(true)
                    .setContentIntent(openApp())
            val id: Int
            when (message) {
                is PushMessage.Request -> {
                    id = notificationId(message.reqId)
                    builder
                        .setContentTitle(context.getString(R.string.push_request_title, message.device))
                        .setContentText(context.getString(R.string.push_request_text, message.minutes, message.app))
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                    NOTIFICATION_APPROVALS.forEach { mins ->
                        builder.addAction(
                            0,
                            context.getString(R.string.push_request_approve, minutesLabel(mins)),
                            answer(id, RequestAnswer(message.deviceId, message.reqId, mins)),
                        )
                    }
                    // Android shows three buttons; where a fourth fits it is "No". Everywhere else "No" is on the
                    // Remote screen's amber card, one tap from the notification itself.
                    builder.addAction(
                        0,
                        context.getString(R.string.push_request_deny),
                        answer(id, RequestAnswer(message.deviceId, message.reqId, 0)),
                    )
                }

                is PushMessage.Unlinked -> {
                    id = notificationId("unlinked-${message.deviceId}")
                    builder
                        .setContentTitle(context.getString(R.string.push_unlinked_title, message.device))
                        .setContentText(context.getString(R.string.push_unlinked_text))
                        .setCategory(NotificationCompat.CATEGORY_STATUS)
                }

                is PushMessage.PermissionLost -> {
                    id = notificationId("alert-${message.alertId}")
                    builder
                        .setContentTitle(context.getString(alertTitle(message.permission), message.device))
                        .setContentText(context.getString(R.string.push_alert_text))
                        .setCategory(NotificationCompat.CATEGORY_STATUS)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                }
            }
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }

        private fun answer(
            notificationId: Int,
            answer: RequestAnswer,
        ): PendingIntent {
            val intent =
                Intent(context, RequestActionReceiver::class.java)
                    .setAction(RequestActionReceiver.ACTION_ANSWER)
                    .putExtra(RequestActionReceiver.EXTRA_DEVICE_ID, answer.deviceId)
                    .putExtra(RequestActionReceiver.EXTRA_REQ_ID, answer.reqId)
                    .putExtra(RequestActionReceiver.EXTRA_MINUTES, answer.minutes)
                    .putExtra(RequestActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            // One PendingIntent per request and answer: the extras differ, so the system must not merge them.
            return PendingIntent.getBroadcast(
                context,
                notificationId * REQUEST_CODE_FACTOR + answer.minutes,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun openApp(): PendingIntent? =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(
                    context,
                    0,
                    it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }

        private fun canNotify(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        private fun ensureChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.push_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }

        companion object {
            const val CHANNEL_ID = "requests"
            private const val REQUEST_CODE_FACTOR = 1000

            /** A stable positive id per request, so the same request replaces itself and can be cancelled. */
            internal fun notificationId(key: String): Int = (key.hashCode() and Int.MAX_VALUE) % ID_RANGE + ID_BASE

            private const val ID_BASE = 2000
            private const val ID_RANGE = 1_000_000
        }
    }

/** "Accessibility turned off on {name}" and its siblings. */
internal fun alertTitle(permission: String): Int =
    when (permission) {
        "accessibility" -> R.string.push_alert_accessibility
        "usage" -> R.string.push_alert_usage
        "overlay" -> R.string.push_alert_overlay
        "notifications" -> R.string.push_alert_notifications
        else -> R.string.push_alert_other
    }

/** Prototype `dlabel`: 15 -> "15m", 30 -> "30m", 60 -> "1h". */
internal fun minutesLabel(mins: Int): String =
    when {
        mins < 60 -> "${mins}m"
        mins % 60 == 0 -> "${mins / 60}h"
        else -> "${mins / 60}h${mins % 60}"
    }
