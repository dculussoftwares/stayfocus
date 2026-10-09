package com.dculus.stayfocused.core.blocking.engine

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dculus.stayfocused.core.blocking.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Posts "Break over" on the `breaks` channel, only when the notification permission is granted. */
class AndroidBreakNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BreakEndNotifier {
        @SuppressLint("MissingPermission") // checked in canNotify()
        override fun breakEnded() {
            if (!canNotify()) return
            ensureChannel()
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val open =
                launch?.let {
                    PendingIntent.getActivity(
                        context,
                        0,
                        it,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                }
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_break_over)
                    .setContentTitle(context.getString(R.string.break_over_title))
                    .setContentText(context.getString(R.string.break_over_text))
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setAutoCancel(true)
                    .setContentIntent(open)
                    .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
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
                    context.getString(R.string.break_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }

        companion object {
            const val CHANNEL_ID = "breaks"
            const val NOTIFICATION_ID = 1001
        }
    }
