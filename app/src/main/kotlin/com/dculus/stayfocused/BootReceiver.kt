package com.dculus.stayfocused

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dculus.stayfocused.core.blocking.standard.StandardEngineStarter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Brings the standard engine back after a reboot or an app update without opening the app. `specialUse` services may
 * be started from BOOT_COMPLETED on Android 15+ (the restriction covers dataSync, camera, mediaPlayback, phoneCall,
 * mediaProjection and microphone only).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var starter: StandardEngineStarter

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (restartsEngine(intent.action)) starter.sync()
    }

    internal companion object {
        fun restartsEngine(action: String?): Boolean =
            action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED
    }
}
