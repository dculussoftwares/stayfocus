package com.dculus.stayfocused.kids.monitoring

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dculus.stayfocused.core.sync.AlertPermission
import com.dculus.stayfocused.core.sync.CommandHandler
import com.dculus.stayfocused.core.sync.CommandType
import com.dculus.stayfocused.core.sync.RemoteCommand
import com.dculus.stayfocused.kids.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Shows the child a reminder to turn a permission back on. */
interface PermissionReminder {
    fun remind(
        permission: String,
        parentName: String?,
    )
}

/**
 * `REMIND_PERMISSION`: a notification on this phone that opens the matching system settings screen. Showing the same
 * reminder again replaces the first, so a repeated command (delivery is at least once) is harmless.
 */
class RemindPermissionHandler
    @Inject
    internal constructor(
        private val reminder: PermissionReminder,
        private val links: ParentLinkStore,
    ) : CommandHandler {
        override val type = CommandType.REMIND_PERMISSION

        override suspend fun handle(command: RemoteCommand) {
            val permission = (command as RemoteCommand.RemindPermission).permission
            reminder.remind(permission, links.parentName)
        }
    }

@Singleton
internal class AndroidPermissionReminder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PermissionReminder {
        @SuppressLint("MissingPermission") // checked in canNotify()
        override fun remind(
            permission: String,
            parentName: String?,
        ) {
            if (!canNotify()) return
            ensureChannel()
            val parent = parentName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.kids_parent_fallback)
            val label = context.getString(permissionLabel(permission))
            // An explicit intent to our own trampoline: a PendingIntent never carries an implicit one.
            val open =
                PendingIntent.getActivity(
                    context,
                    notificationId(permission),
                    Intent(context, PermissionSettingsActivity::class.java)
                        .putExtra(PermissionSettingsActivity.EXTRA_PERMISSION, permission),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_monitoring)
                    .setContentTitle(context.getString(R.string.kids_remind_title, parent, label))
                    .setContentText(context.getString(R.string.kids_remind_text))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(open)
                    .build()
            NotificationManagerCompat.from(context).notify(notificationId(permission), notification)
        }

        private fun canNotify(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        private fun ensureChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.kids_remind_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }

        private companion object {
            const val CHANNEL_ID = "permission_reminders"
            const val ID_BASE = 3000

            /** One notification per permission. */
            fun notificationId(permission: String): Int = ID_BASE + (permission.hashCode() and Int.MAX_VALUE) % ID_RANGE

            const val ID_RANGE = 1000
        }
    }

/**
 * Opens the system settings screen for [permission]. A notification cannot carry an implicit intent in a
 * `PendingIntent`, so the reminder opens this activity, which starts the settings screen and finishes at once.
 */
class PermissionSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permission = intent?.getStringExtra(EXTRA_PERMISSION).orEmpty()
        try {
            startActivity(settingsIntent(permission, packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (
            @Suppress("SwallowedException") e: ActivityNotFoundException,
        ) {
            // A phone without that settings screen: nothing to open.
            Timber.w("No settings screen for %s", permission)
        }
        finish()
    }

    companion object {
        const val EXTRA_PERMISSION = "permission"
    }
}

/** The settings screen where [permission] is turned back on. */
internal fun settingsIntent(
    permission: String,
    packageName: String,
): Intent =
    when (permission) {
        AlertPermission.USAGE -> {
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }

        AlertPermission.OVERLAY -> {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        }

        AlertPermission.NOTIFICATIONS -> {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }

        else -> {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
    }

/** The string naming [permission] in the reminder; an unknown one is "a permission". */
internal fun permissionLabel(permission: String): Int =
    when (permission) {
        AlertPermission.ACCESSIBILITY -> R.string.kids_remind_accessibility
        AlertPermission.USAGE -> R.string.kids_remind_usage
        AlertPermission.OVERLAY -> R.string.kids_remind_overlay
        AlertPermission.NOTIFICATIONS -> R.string.kids_remind_notifications
        else -> R.string.kids_remind_other
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PermissionReminderModule {
    @Binds
    abstract fun reminder(impl: AndroidPermissionReminder): PermissionReminder

    @Binds
    @IntoSet
    abstract fun remindPermission(impl: RemindPermissionHandler): CommandHandler
}
