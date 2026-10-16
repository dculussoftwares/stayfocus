package com.dculus.stayfocused.kids.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Brings the notification back after a reboot or an app update without opening the app. `specialUse` services may be
 * started from BOOT_COMPLETED on Android 15+ (the restriction covers dataSync, camera, mediaPlayback, phoneCall,
 * mediaProjection and microphone only).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject
    internal lateinit var launcher: MonitoringLauncher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            launcher.sync()
        }
    }
}
