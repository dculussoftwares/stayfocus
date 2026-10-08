package com.dculus.stayfocused.core.blocking.permissions

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Intents (and the one runtime request) that take the user to each permission's system screen. */
object PermissionIntents {
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    /** Runtime permission for notifications; null below Android 13, where no runtime request exists. */
    fun notificationRuntimePermission(sdk: Int = Build.VERSION.SDK_INT): String? =
        if (sdk >= Build.VERSION_CODES.TIRAMISU) POST_NOTIFICATIONS else null

    fun usageAccess(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).newTask()

    fun accessibility(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).newTask()

    /**
     * "Display over other apps". The package URI opens this app's own screen on Android 10 and below; on 11+ the
     * system ignores it and shows the list of apps, so the UI must tell the user to pick Stay Focused there.
     */
    fun overlay(packageName: String): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")).newTask()

    /** Fallback when the runtime request can no longer be shown (denied twice, or below Android 13). */
    fun notificationSettings(packageName: String): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .newTask()

    /**
     * The battery optimisation list. Deliberately not `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which is
     * restricted by Play policy.
     */
    fun batteryOptimizationSettings(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).newTask()

    /** App info screen, where "Allow restricted settings" lives in the overflow menu. */
    fun appInfo(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).newTask()

    private fun Intent.newTask(): Intent = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
