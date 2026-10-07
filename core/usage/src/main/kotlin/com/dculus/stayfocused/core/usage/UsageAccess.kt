package com.dculus.stayfocused.core.usage

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the "Usage access" special permission is granted to this app. */
interface UsageAccess {
    fun isGranted(): Boolean

    /** Intent that opens the system "Usage access" settings screen. */
    fun settingsIntent(): Intent
}

@Singleton
class AppOpsUsageAccess
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : UsageAccess {
        override fun isGranted(): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val uid = Process.myUid()
            val mode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, context.packageName)
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, context.packageName)
                }
            return if (mode == AppOpsManager.MODE_DEFAULT) {
                // No explicit app-op decision yet: fall back to the manifest permission state.
                ContextCompat.checkSelfPermission(context, USAGE_STATS_PERMISSION) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                mode == AppOpsManager.MODE_ALLOWED
            }
        }

        override fun settingsIntent(): Intent =
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private companion object {
            const val USAGE_STATS_PERMISSION = "android.permission.PACKAGE_USAGE_STATS"
        }
    }
